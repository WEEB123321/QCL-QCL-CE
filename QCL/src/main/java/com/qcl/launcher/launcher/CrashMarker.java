package com.qcl.launcher.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

/**
 * ★★★ 社区版新增：**「上次使用中崩溃」的标记与提示**。
 *
 * <h3>为什么需要它</h3>
 * 工程里已经有一个「上次启动没有走完」的提示（{@link StartupTrace#lastRunWasAbnormal()}），
 * 但它的判据是「**启动**有没有跑完」。而用户真正遇到的崩溃，很多发生在
 * **启动成功之后** —— 比如点了某个按钮、切了某个页面。这种崩溃：
 * <ul>
 *   <li>启动路标里最后一行是「启动成功」，所以那个提示**不会出现**；</li>
 *   <li>日志只躺在 {@code crash.log} 里，用户得自己去翻目录 —— 实际上没人会翻。</li>
 * </ul>
 * 结果就是「用户报崩溃 → 作者没有日志 → 只能猜」。
 *
 * <p>本类补上这一段：崩溃处理器写一个**带时间戳的标记**，下次打开启动器时
 * 如果这个标记还没被用户看过，就主动弹出来并给一个「分享日志」按钮 ——
 * <b>把「取日志」从一件要教用户做的事，变成一次点击。</b>
 *
 * <p>★ 只提示一次：用户点掉之后记下时间戳，同一个崩溃不会每次启动都烦他。
 */
public final class CrashMarker {

    private static final String TAG = "QCLCrashMarker";
    private static final String FILE_NAME = "last_crash.txt";
    private static final String PREF = "qcl_crash_marker";
    private static final String KEY_SEEN = "seen_at";

    private CrashMarker() {
    }

    /** 崩溃处理器里调用。★ 本身绝不能再抛异常 —— 否则会把原始崩溃盖掉。 */
    public static void mark(Context context, Throwable t) {
        if (context == null) {
            return;
        }
        try {
            String summary = "?";
            try {
                if (t != null) {
                    summary = t.getClass().getName()
                            + (t.getMessage() == null ? "" : (": " + t.getMessage()));
                    StackTraceElement[] st = t.getStackTrace();
                    if (st != null && st.length > 0 && st[0] != null) {
                        summary = summary + "\n    at " + st[0];
                    }
                }
            } catch (Throwable ignored) {
            }
            StringBuilder sb = new StringBuilder();
            sb.append(System.currentTimeMillis()).append('\n');
            sb.append(summary).append('\n');

            File f = new File(context.getFilesDir(), FILE_NAME);
            FileOutputStream fos = new FileOutputStream(f);
            OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
            try {
                w.write(sb.toString());
                w.flush();
            } finally {
                w.close();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 启动时调用：返回**还没被用户看过**的崩溃摘要；没有则返回 {@code null}。
     *
     * @return 形如 "java.lang.IndexOutOfBoundsException: ...\n    at xxx.yyy(Z.java:12)"
     */
    public static String pending(Context context) {
        try {
            File f = new File(context.getFilesDir(), FILE_NAME);
            if (!f.isFile() || f.length() == 0L) {
                return null;
            }
            byte[] b = new byte[(int) Math.min(f.length(), 8192)];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n;
            try {
                n = in.read(b);
            } finally {
                in.close();
            }
            if (n <= 0) {
                return null;
            }
            String text = new String(b, 0, n, "UTF-8");
            String[] lines = text.split("\n", 2);
            long ts = Long.parseLong(lines[0].trim());
            long seen = seenAt(context);
            if (ts <= seen) {
                return null;   // 已经给用户看过了
            }
            return lines.length > 1 ? lines[1].trim() : "?";
        } catch (Throwable t) {
            Log.w(TAG, "读取崩溃标记失败（已忽略）: " + t);
            return null;
        }
    }

    /** 用户点掉提示之后调用 —— 记下「已看过」，同一个崩溃不再重复提示。 */
    public static void acknowledge(Context context) {
        try {
            File f = new File(context.getFilesDir(), FILE_NAME);
            long ts = 0L;
            if (f.isFile()) {
                byte[] b = new byte[(int) Math.min(f.length(), 64)];
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                int n;
                try {
                    n = in.read(b);
                } finally {
                    in.close();
                }
                if (n > 0) {
                    String first = new String(b, 0, n, "UTF-8").split("\n", 2)[0].trim();
                    ts = Long.parseLong(first);
                }
            }
            context.getSharedPreferences(PREF, 0).edit().putLong(KEY_SEEN, ts).apply();
        } catch (Throwable ignored) {
        }
    }

    private static long seenAt(Context context) {
        try {
            return context.getSharedPreferences(PREF, 0).getLong(KEY_SEEN, 0L);
        } catch (Throwable t) {
            return 0L;
        }
    }
}
