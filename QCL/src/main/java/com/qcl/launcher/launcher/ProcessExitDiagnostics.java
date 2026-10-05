package com.qcl.launcher.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ★★★ 社区版：把「上一次进程究竟是怎么死的」直接问系统。
 *
 * <p><b>为什么需要它</b>：用户「一打开就闪退、什么提示都没有」时，此前所有观测手段都可能同时失效：
 * <ul>
 *   <li>崩溃弹窗（{@code Thread.UncaughtExceptionHandler}）只对 <b>Java 未捕获异常</b>生效；</li>
 *   <li>{@code crash.log} 同理；</li>
 *   <li>启动路标（{@link StartupTrace}）虽然能记到「最后通过的阶段」，但一旦崩溃发生在
 *       {@code Application.attachBaseContext} 之前，或进程被 native 信号直接杀死，
 *       它也只能证明「没走到某处」，证明不了「为什么」。</li>
 * </ul>
 *
 * <p>而 Android 11（API 30）起，系统为每个应用保留最近的进程退出记录，
 * 通过 {@code ActivityManager.getHistoricalProcessExitReasons()} 即可查询。
 * 它<b>不需要 root、不需要 adb、不需要任何运行时权限</b>（只返回调用方自己的进程），
 * 而且记的是<b>结果</b>而不是过程 —— 因此 native 崩溃也逃不掉。
 * 关键字段：
 * <ul>
 *   <li>{@code getReason()} —— 是 Java 崩溃、native 崩溃、ANR 还是被回收；</li>
 *   <li>{@code getStatus()} —— native 崩溃时是<b>信号编号</b>
 *       （SIGSEGV=11 / SIGABRT=6 / SIGILL=4 …），直接指出崩溃类型；</li>
 *   <li>{@code getTraceInputStream()} —— 系统侧的原始记录（native 崩溃即 tombstone）。</li>
 * </ul>
 *
 * <p><b>★★★ 本轮重要修正：全程用反射访问 {@code ApplicationExitInfo}，
 * 本类里不出现该类型的任何引用（字段 / 方法签名 / 局部变量 / 参数都不行）。</b>
 *
 * <p><b>为什么非这样不可</b>：{@code ApplicationExitInfo} 是 <b>API 30 才引入</b>的类。
 * 若本类的方法签名或局部变量直接引用它，那么即使方法体<b>第一行</b>就写了
 * {@code SDK_INT < 30 → return null}，ART 的验证器在解析方法签名时仍可能直接抛
 * {@code NoClassDefFoundError}（它是 {@link Error} 而<b>不是</b> {@link Exception}）。
 * 而调用方 {@link StartupTrace#checkLastRun} 的 try 块会把它一并吞掉 ——
 * 后果不是「查不到退出原因」这么轻，而是<b>它后面的判定逻辑整段被跳过</b>：
 * {@code lastAbnormal} 永不置位 → 「上次启动没有走完」弹框不弹、
 * <b>安全模式（跳过原生库加载）也永远不触发</b>。
 * 也就是说，在<b>最需要</b>这套自救机制的旧设备上，它会静默失效。
 * 改成反射后，旧版本上这些方法能正常执行，只是如实返回「查不到」。
 *
 * <p>本类所有方法都不向外抛异常（全部 try/catch 吞掉），观测手段本身绝不成为新的崩溃源。
 */
public final class ProcessExitDiagnostics {

    // ---- ApplicationExitInfo 的 REASON_* 常量 ----
    //   刻意写成字面量：它们是编译期常量，本就内联，但显式写出来可以彻底断掉
    //   「本类是否引用了 API 30 的类」这个疑问。数值取自 android.app.ApplicationExitInfo。
    private static final int REASON_EXIT_SELF = 1;
    private static final int REASON_SIGNALED = 2;
    private static final int REASON_LOW_MEMORY = 3;
    private static final int REASON_CRASH = 4;
    private static final int REASON_CRASH_NATIVE = 5;
    private static final int REASON_ANR = 6;
    private static final int REASON_INITIALIZATION_FAILURE = 7;
    private static final int REASON_PERMISSION_CHANGE = 8;
    private static final int REASON_EXCESSIVE_RESOURCE_USAGE = 9;
    private static final int REASON_USER_REQUESTED = 10;
    private static final int REASON_USER_STOPPED = 11;
    private static final int REASON_DEPENDENCY_DIED = 12;
    private static final int REASON_OTHER = 13;

    private ProcessExitDiagnostics() {
    }

    /** 上次退出是否属于「崩溃」类（区别于用户主动退出 / 被系统回收） */
    public static boolean lastExitWasCrash(Context context) {
        Object info = lastExit(context);
        return info != null && isCrashReason(intOf(info, "getReason", -1));
    }

    public static boolean isCrashReason(int reason) {
        return reason == REASON_CRASH
                || reason == REASON_CRASH_NATIVE
                || reason == REASON_ANR
                || reason == REASON_INITIALIZATION_FAILURE;
    }

    /**
     * 取最近一条「不是当前进程」的退出记录。
     *
     * <p>返回类型刻意是 {@link Object}（真实类型是 {@code ApplicationExitInfo}）——
     * 若把返回类型写成 {@code ApplicationExitInfo}，本类就会在 API 30 以下的设备上
     * 因验证器解析签名而抛 {@code NoClassDefFoundError}。见类注释。
     *
     * @return 退出记录对象；API &lt; 30、无记录或任何异常时返回 {@code null}
     */
    public static Object lastExit(Context context) {
        if (Build.VERSION.SDK_INT < 30 || context == null) {
            return null;
        }
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return null;
            }
            // ★ 注意：API 30 只有 3 参重载（没有无参版本）。packageName 传 null
            //   表示「调用方自己的包」—— 这是唯一不需要 DUMP 权限的用法。
            List<?> list = null;
            try {
                list = asList(invoke(am, "getHistoricalProcessExitReasons",
                        new Class[]{String.class, int.class, int.class},
                        new Object[]{null, 0, 0}));
            } catch (Throwable ignored) {
                // 个别 ROM 上 null 不被接受，退一步显式传自己的包名
                try {
                    list = asList(invoke(am, "getHistoricalProcessExitReasons",
                            new Class[]{String.class, int.class, int.class},
                            new Object[]{context.getPackageName(), 0, 0}));
                } catch (Throwable ignored2) {
                    list = null;
                }
            }
            if (list == null || list.isEmpty()) {
                return null;
            }
            int myPid = android.os.Process.myPid();
            for (Object i : list) {
                if (i != null && intOf(i, "getPid", -1) != myPid) {
                    return i;
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把最近一次退出整理成人类可读的多行文本（给弹框 / 诊断页用） */
    public static String describeLastExit(Context context) {
        if (Build.VERSION.SDK_INT < 30) {
            return "(系统版本低于 Android 11，查不到进程退出记录 —— 请看下方 logcat 崩溃缓冲区)";
        }
        Object info = lastExit(context);
        if (info == null) {
            return "(没有查到进程退出记录 —— 可能是首次安装，或系统未保留)";
        }
        StringBuilder sb = new StringBuilder();
        int reason = intOf(info, "getReason", -1);
        sb.append("原因  : ").append(reasonText(reason)).append('\n');
        sb.append("描述  : ").append(strOf(info, "getDescription")).append('\n');
        sb.append("时间  : ").append(fmt(longOf(info, "getTimestamp", 0L))).append('\n');
        if (reason == REASON_CRASH_NATIVE || reason == REASON_SIGNALED) {
            sb.append("信号  : ").append(signalText(intOf(info, "getStatus", -1))).append('\n');
        } else {
            sb.append("状态码: ").append(intOf(info, "getStatus", -1)).append('\n');
        }
        sb.append("进程  : ").append(strOf(info, "getProcessName")).append('\n');
        sb.append("重要性: ").append(intOf(info, "getImportance", -1)).append('\n');
        String trace = readTrace(info);
        if (trace != null && !trace.isEmpty()) {
            sb.append("---------- 系统侧原始记录（tombstone / 堆栈）----------\n");
            sb.append(trace);
        }
        return sb.toString();
    }

    /**
     * 上次退出原因的一行摘要（给「设备信息」那种一行式展示用）。
     *
     * <p>刻意放在这里而不是调用方 —— 调用方一旦自己写
     * {@code ApplicationExitInfo info = lastExit(...)}，就又引入了 API 30 的类型引用，
     * 在旧设备上会抛 {@code NoClassDefFoundError}。凡是需要按字段判断的逻辑，
     * 都必须在<b>本类内部</b>完成（这里才有 intOf/strOf 这些反射取值器）。
     */
    public static String lastExitOneLine(Context context) {
        if (Build.VERSION.SDK_INT < 30) {
            return "(系统低于 Android 11，无法查询)";
        }
        Object info = lastExit(context);
        if (info == null) {
            return "(无记录)";
        }
        int reason = intOf(info, "getReason", -1);
        String s = reasonText(reason);
        if (reason == REASON_CRASH_NATIVE || reason == REASON_SIGNALED) {
            s = s + " / " + signalText(intOf(info, "getStatus", -1));
        }
        return s;
    }

    private static String readTrace(Object info) {
        try {
            Object in = invoke(info, "getTraceInputStream", new Class[0], new Object[0]);
            if (!(in instanceof InputStream)) {
                return "(系统未提供原始记录；若为 native 崩溃，信号编号已足以定位方向)";
            }
            BufferedReader r = new BufferedReader(new InputStreamReader((InputStream) in));
            StringBuilder sb = new StringBuilder();
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n < 400) {
                sb.append(line).append('\n');
                n++;
            }
            r.close();
            return sb.toString();
        } catch (Throwable t) {
            return "(读取原始记录失败: " + t + ")";
        }
    }

    public static String reasonText(int reason) {
        switch (reason) {
            case REASON_EXIT_SELF:
                return "REASON_EXIT_SELF（进程自己退出）";
            case REASON_SIGNALED:
                return "REASON_SIGNALED（被信号杀死）";
            case REASON_LOW_MEMORY:
                return "REASON_LOW_MEMORY（内存不足被系统回收）";
            case REASON_CRASH:
                return "REASON_CRASH（★ Java 未捕获异常）";
            case REASON_CRASH_NATIVE:
                return "REASON_CRASH_NATIVE（★ 原生崩溃！Java 的 try/catch 与崩溃弹窗都抓不到）";
            case REASON_ANR:
                return "REASON_ANR（无响应）";
            case REASON_INITIALIZATION_FAILURE:
                return "REASON_INITIALIZATION_FAILURE（★ 应用初始化失败）";
            case REASON_PERMISSION_CHANGE:
                return "REASON_PERMISSION_CHANGE（权限变更）";
            case REASON_EXCESSIVE_RESOURCE_USAGE:
                return "REASON_EXCESSIVE_RESOURCE_USAGE（资源占用过高）";
            case REASON_USER_REQUESTED:
                return "REASON_USER_REQUESTED（用户主动结束）";
            case REASON_USER_STOPPED:
                return "REASON_USER_STOPPED（被用户强制停止）";
            case REASON_DEPENDENCY_DIED:
                return "REASON_DEPENDENCY_DIED（依赖进程死亡）";
            case REASON_OTHER:
                return "REASON_OTHER（其它）";
            default:
                return "UNKNOWN(" + reason + ")";
        }
    }

    public static String signalText(int sig) {
        switch (sig) {
            case 4:
                return "SIGILL(4) 非法指令 —— 通常是 ABI/CPU 指令集不匹配";
            case 6:
                return "SIGABRT(6) abort —— 常见于 C++ 异常、断言失败、libc 检测到堆损坏";
            case 7:
                return "SIGBUS(7) 总线错误";
            case 8:
                return "SIGFPE(8) 算术异常";
            case 11:
                return "SIGSEGV(11) 段错误 —— 空指针/野指针，最常见的 native 崩溃";
            case 9:
                return "SIGKILL(9) 被强杀";
            default:
                return "signal " + sig;
        }
    }

    // ==================== 反射工具（全部吞异常，返回安全默认值） ====================

    /** 调用无参 / 有参方法；任何失败都抛出去，由调用方决定默认值。 */
    private static Object invoke(Object target, String name, Class<?>[] types, Object[] args) throws Exception {
        Method m = target.getClass().getMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    @SuppressWarnings("unchecked")
    private static List<?> asList(Object o) {
        return o instanceof List ? (List<?>) o : null;
    }

    private static int intOf(Object o, String method, int def) {
        try {
            Object v = invoke(o, method, new Class[0], new Object[0]);
            return v instanceof Integer ? ((Integer) v).intValue() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static long longOf(Object o, String method, long def) {
        try {
            Object v = invoke(o, method, new Class[0], new Object[0]);
            return v instanceof Long ? ((Long) v).longValue() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static String strOf(Object o, String method) {
        try {
            Object v = invoke(o, method, new Class[0], new Object[0]);
            return String.valueOf(v);
        } catch (Throwable t) {
            return "(读取失败: " + t + ")";
        }
    }

    private static String fmt(long ms) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(ms));
        } catch (Throwable t) {
            return String.valueOf(ms);
        }
    }
}
