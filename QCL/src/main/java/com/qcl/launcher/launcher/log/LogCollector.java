package com.qcl.launcher.launcher.log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * ★★★ 社区版新增：运行期日志采集器（日志中心的数据源）。
 *
 * <p><b>为什么不用自建日志层</b>：全仓有上百处直接调 {@code android.util.Log}，
 * 想把它们全部改成走一层自己的门面，等于把整个工程翻一遍，风险远大于收益。
 * 这里改成<b>从外部把 logcat 读回来</b>：既拿到了全部既有日志（包括 native 层
 * 打出来的），又不侵入任何一处既有代码。
 *
 * <p><b>★ 权限现实</b>：Android 4.1 起，应用只能读到<b>自己 UID</b> 的日志
 * （内核 logger 在读取侧按 UID 过滤），所以不需要 {@code READ_LOGS} 权限。
 * 这也正好是我们想要的 —— 只关心本启动器的日志。
 *
 * <p><b>★ 为什么用 {@code --pid}</b>：按 PID 过滤比按 tag 过滤完整 ——
 * native（SDL/JVM）打出来的日志 tag 五花八门，按 tag 过滤必然漏。
 * {@code --pid} 需要 API 24，本项目 minSdk 26，安全。
 *
 * <p><b>线程安全</b>：写入在读取线程，读取在主线程；用 {@code synchronized(buffer)}
 * 保护环形缓冲，读时先复制一份快照再交给界面，绝不让界面持有缓冲的活引用。
 */
public final class LogCollector {

    /** 环形缓冲上限。3000 行足够覆盖一次完整启动 + 一段游戏会话，又不至于把内存吃满。 */
    private static final int MAX_LINES = 3000;

    private static final Deque<String> buffer = new ArrayDeque<>();

    private static volatile boolean live = false;
    private static Thread reader;
    private static Process proc;
    /** 采集不可用（exec 失败 / 被 ROM 限制）时置位，界面据此给出提示而不是一片空白 */
    private static volatile String unavailableReason = null;

    private LogCollector() {
    }

    /** 采集是否已经跑起来 */
    public static boolean isLive() {
        return live;
    }

    /** 采集不可用的原因；null 表示正常。 */
    public static String unavailableReason() {
        return unavailableReason;
    }

    /**
     * 启动实时采集（幂等）。重复调用不会起第二个线程。
     * <p>刻意不在 {@code Application.onCreate} 里自动调 —— 那会在每次启动都 fork 一个
     * logcat 进程，白白增加启动开销与崩溃面。日志中心是低频功能，等用户打开它再起。
     */
    public static synchronized void startLive() {
        if (live) {
            return;
        }
        live = true;
        unavailableReason = null;
        reader = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        }, "qcl-logcat");
        reader.setDaemon(true);
        try {
            reader.start();
        } catch (Throwable t) {
            live = false;
            unavailableReason = String.valueOf(t);
        }
    }

    /** 停止实时采集，并回收 logcat 进程（离开日志中心页时调用，别让它一直挂着）。 */
    public static synchronized void stopLive() {
        live = false;
        try {
            if (proc != null) {
                proc.destroy();
            }
        } catch (Throwable ignored) {
        }
        proc = null;
        reader = null;
    }

    /** 复制一份当前日志快照。界面只应持有这份副本。 */
    public static List<String> snapshot() {
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    /** 清空缓冲（只影响界面显示，不影响系统 logcat）。 */
    public static void clear() {
        synchronized (buffer) {
            buffer.clear();
        }
    }

    /**
     * 一次性把「本次进程最近的历史日志」拉进来 —— 用户刚打开日志中心时，
     * 缓冲是空的，先补一段历史才有东西看（包括启动期那些日志）。
     */
    public static void seedFromHistory(int lines) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "logcat", "-d", "-v", "time", "-t", String.valueOf(lines),
                    "--pid=" + android.os.Process.myPid()});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                append(line);
            }
            br.close();
            try {
                p.destroy();
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            if (unavailableReason == null) {
                unavailableReason = String.valueOf(t);
            }
        }
    }

    private static void readLoop() {
        try {
            proc = Runtime.getRuntime().exec(new String[]{
                    "logcat", "-v", "time", "--pid=" + android.os.Process.myPid()});
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            // 外层再套一层 live 判断：stopLive() 里已经 destroy 了进程，
            // 这里 readLine 会立刻返回 null 从而退出循环，不会卡住。
            while (live && (line = br.readLine()) != null) {
                append(line);
            }
            try {
                br.close();
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            if (unavailableReason == null) {
                unavailableReason = String.valueOf(t);
            }
        } finally {
            live = false;
        }
    }

    private static void append(String line) {
        if (line == null) {
            return;
        }
        synchronized (buffer) {
            buffer.addLast(line);
            while (buffer.size() > MAX_LINES) {
                buffer.removeFirst();
            }
        }
    }

    // ------------------------------------------------------------------ 解析辅助（给界面用）

    /** 从一行 logcat（{@code MM-DD HH:MM:SS.mmm L/TAG( PID): msg}）里取级别字母。取不到返回 ' '。 */
    public static char levelOf(String line) {
        try {
            int i = line.indexOf(' ');
            // 时间戳后紧跟一个空格，再是级别字母
            while (i >= 0 && i + 1 < line.length() && line.charAt(i) == ' ') {
                i++;
            }
            if (i >= 0 && i < line.length()) {
                char c = line.charAt(i);
                if (c == 'V' || c == 'D' || c == 'I' || c == 'W' || c == 'E' || c == 'F') {
                    return c;
                }
            }
        } catch (Throwable ignored) {
        }
        return ' ';
    }

    /** 级别字母 → 可读名（界面过滤器用）。 */
    public static String levelName(char c) {
        switch (c) {
            case 'V': return "Verbose";
            case 'D': return "Debug";
            case 'I': return "Info";
            case 'W': return "Warn";
            case 'E': return "Error";
            case 'F': return "Fatal";
            default:  return "Other";
        }
    }
}
