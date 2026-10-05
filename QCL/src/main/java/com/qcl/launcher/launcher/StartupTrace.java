package com.qcl.launcher.launcher;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ★★★ 社区版新增：启动路标（startup breadcrumb）。
 *
 * <p><b>为什么需要它</b>：启动器「一打开就闪退」时，现有的两条观测手段都可能失效：
 * <ul>
 *   <li>{@code CrashReportActivity}（崩溃弹窗）只对 <b>Java 未捕获异常</b>有效。
 *       若是 <b>native 崩溃</b> —— 例如 {@code System.loadLibrary} 里 .so 的静态构造段错误 ——
 *       进程会被信号直接杀掉，弹窗根本来不及出现；</li>
 *   <li>{@code crash.log} 同理，native 崩溃时它是空的。</li>
 * </ul>
 * 这种情况下唯一能回答「走到哪一步才死的」，就是<b>每一步都先落盘一次的路标</b>：
 * 文件里最后一行就是崩溃前最后通过的阶段。
 *
 * <p><b>为什么每次 mark 都重新开/关一次文件</b>：路标的价值在于
 * 「进程被强杀前最后一次写已经落盘」。若用缓冲流且不 close，崩溃时最后几条就丢了，
 * 而那几条恰恰是最关键的。单次写入只有几十字节，这点开销可以忽略。
 *
 * <p><b>★★★ 为什么要写两份（本轮修正）</b>：第一版只写了
 * {@code Android/data/<包名>/files/}。但 <b>Android 11+ 起 /Android/data 对第三方文件管理器
 * 基本不可见</b>（系统有意封堵），玩家拿着手机根本打不开这个目录 ——
 * 路标写得再准，取不到就等于没写。所以现在<b>同时镜像一份到
 * {@code /sdcard/QCL/startup_trace.log}</b>：那是用户随手就能打开的位置，
 * 应用自己的 {@code crash.log} 本来也就写在那儿。
 * <p>未授予 {@code MANAGE_EXTERNAL_STORAGE} 时镜像会写失败（静默吞掉，不影响主份）；
 * 授权后从下一次启动起镜像即生效。崩溃若是稳定复现的，第二次启动就能拿到。
 *
 * <p><b>为什么 init 时先清空</b>：只看「最近一次启动」才有意义。
 * 不清空的话，上一次启动跑完全程的路标会让本次的截断点难以辨认。
 *
 * <p><b>安全性</b>：本类所有方法都不向外抛异常（全部 try/catch 吞掉）。
 * 观测手段本身绝不能成为新的崩溃源。
 */
public final class StartupTrace {

    /** 与 crash.log 同目录，便于一起取 */
    public static final String FILE_NAME = "startup_trace.log";
    /**
     * ★★★ 社区版新增（本轮）：诊断报告文件名。
     *
     * <p>它是「一个文件包含全部信息」的交付形态：设备信息 + 系统侧进程退出原因 +
     * 上次启动断点 + 本次启动路标，全部写在一起。
     *
     * <p><b>为什么必须落盘而不只是显示在界面上</b>：如果崩溃发生在
     * {@code Application.onCreate} 的原生库加载里，连 {@code RuntimeInstallActivity}
     * 都不会被创建 —— 再好的诊断界面也打不开。而本文件写在**原生库加载之前**，
     * 所以哪怕应用永远打不开，用户也能直接把它发出来。
     */
    public static final String REPORT_NAME = "QCL诊断报告.txt";

    private static final String TAG = "QCLStartup";

    /** 主份：Android/data/&lt;包名&gt;/files/（不需要任何权限，但用户不易打开） */
    private static volatile File traceFile;
    /** 镜像份：/sdcard/QCL/（需要 MANAGE_EXTERNAL_STORAGE，但用户随手可开） */
    private static volatile File mirrorFile;
    /**
     * ★★★ 第三份（本轮新增）：/sdcard/Android/media/&lt;包名&gt;/。
     *
     * <p><b>为什么还要再加一份</b>：前两份各有一个致命短板 ——
     * 主份在 {@code Android/data} 下，<b>Android 11+ 起第三方文件管理器根本进不去</b>；
     * 镜像份要 {@code MANAGE_EXTERNAL_STORAGE}，而<b>崩在 Provider 阶段时应用从来没启动成功过，
     * 那个权限也就从没被授予过</b> —— 最需要它的时候它恰好不可用。
     * <p>{@code Android/media/&lt;包名&gt;} 是 Android 11 分区存储里<b>唯一对外可见</b>的应用目录
     * （WhatsApp / Telegram 等都靠它导出文件），<b>不需要任何权限</b>就能被文件管理器和电脑 MTP 打开。
     * 于是「闪退后把日志取出来」这件事第一次变成真的可行。
     */
    private static volatile File mediaFile;
    /** ★★★ 本轮新增：诊断报告（见 {@link #REPORT_NAME}），与路标写在同一批目录 */
    private static volatile File reportFile;

    private static volatile long startMs;
    private static volatile boolean ready;

    /**
     * ★★★ 社区版新增：上一次启动是否**没有走完**（旧路标里找不到「启动成功」）。
     *
     * <p><b>为什么需要它</b>：如果崩溃发生在 {@code ContentProvider} 阶段
     * （也就是 {@code Application.onCreate} 之前），崩溃处理器**还没装上** ——
     * 弹不出崩溃页、拿不到堆栈，用户看到的就是「打开直接闪退、什么都没留下」。
     * 那种情况下，<b>下一次启动是唯一能拿到线索的时机</b>：上一次的路标文件还在。
     * 于是每次 init 在**截断之前**先读一遍旧文件，发现没走完就把尾部留在这里，
     * 由 {@code RuntimeInstallActivity} 主动弹给用户看。
     */
    private static volatile boolean lastAbnormal;
    /** 上次中断前的路标尾部（给「上次启动未走完」弹框用） */
    private static volatile String lastTail = "";
    /** ★★★ 本轮新增：上一次的**完整**路标原文（给诊断报告用；lastTail 只留尾部 14 行） */
    private static volatile String lastRawTrace = "";
    /**
     * ★★★ 社区版新增（本轮）：上一次「启动初始化」是否没有走完 —— <b>只看 Java 侧路标文件</b>。
     *
     * <p>与 {@link #lastAbnormal} 的区别很关键：{@code lastAbnormal} 把
     * 「系统侧报告的崩溃」也算进来（例如游戏运行中崩了），那是给<b>弹框</b>用的；
     * 本字段只看路标里有没有「启动成功」，因此可以安全地用来决定
     * <b>这次要不要进安全模式</b>。
     *
     * <p>若拿 {@code lastAbnormal} 当安全模式判据，会出现
     * 「玩完一局游戏、游戏崩了 → 下次启动启动器被误降级（不加载原生库 → 游戏再也起不来）」，
     * 那是把一个观测问题变成一个新故障。
     */
    private static volatile boolean lastStartupIncomplete;

    /** 走完整个启动流程时写入的标记 —— 「上次是否走完」的判据。值必须与 MainActivity 里的一致。 */
    public static final String MARK_STARTUP_OK = "启动成功";

    private StartupTrace() {
    }

    /**
     * 在 {@code Application.attachBaseContext()} 里调用 —— <b>必须早于 onCreate</b>，
     * 因为 ContentProvider 阶段（系统自动初始化，代码零引用也会跑）夹在两者之间，
     * 那正是最难观测、也最容易「无界面闪退」的地方。
     *
     * <p>幂等：重复调用直接返回，不会把已经写下的路标截断掉。
     */
    public static synchronized void init(Context context) {
        if (ready) {
            return;
        }
        try {
            startMs = System.currentTimeMillis();

            File dir = context.getExternalFilesDir(null);
            if (dir == null) {
                // 外置存储不可用（极少见）。退回应用内部目录，至少保证有记录。
                dir = context.getFilesDir();
            }
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            traceFile = new File(dir, FILE_NAME);

            // ★ 镜像份（best-effort）：写失败就置 null，后续自动跳过
            try {
                File mdir = new File(Environment.getExternalStorageDirectory(), "QCL");
                if (!mdir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    mdir.mkdirs();
                }
                mirrorFile = new File(mdir, FILE_NAME);
            } catch (Throwable ignored) {
                mirrorFile = null;
            }

            // ★★★ 第三份：Android/media/<包名>/ —— 唯一不需要权限就能被文件管理器打开的位置
            try {
                File[] medias = context.getExternalMediaDirs();
                if (medias != null && medias.length > 0 && medias[0] != null) {
                    if (!medias[0].exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        medias[0].mkdirs();
                    }
                    mediaFile = new File(medias[0], FILE_NAME);
                    reportFile = new File(medias[0], REPORT_NAME);
                }
            } catch (Throwable ignored) {
                mediaFile = null;
                reportFile = null;
            }

            // ★★★ 社区版：**在截断之前**读一遍旧文件，判断上一次有没有走完。
            //   顺序很关键 —— 下面的 writeBoth(..., true) 一执行，旧内容就没了。
            checkLastRun(context);

            ready = true;

            StringBuilder sb = new StringBuilder();
            sb.append("=== QCL 启动路标 ===\n");
            sb.append("time   : ").append(stamp()).append('\n');
            sb.append("android: ").append(android.os.Build.VERSION.RELEASE)
                    .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
            sb.append("device : ").append(android.os.Build.MANUFACTURER).append(' ')
                    .append(android.os.Build.MODEL).append('\n');
            sb.append("abi    : ").append(join(android.os.Build.SUPPORTED_ABIS)).append('\n');
            // ★★★ 社区版：把本机实际注册的 ContentProvider 全列出来。
            //   它们是「Provider 阶段」的全部嫌疑人，由系统自动初始化 ——
            //   代码里搜不到引用也会跑，而且部分 ROM 会额外注入自家的 Provider。
            //   列在这里，就不必再靠反编译 APK 去猜这一阶段到底跑了什么。
            sb.append("providers: ").append(providersLine(context)).append('\n');
            sb.append("----------------------------------------\n");
            sb.append('[').append(elapsed()).append("] 进程启动\n");
            // 截断：只保留本次启动
            writeBoth(sb.toString(), true);

            // ★★★ 本轮新增：把「诊断报告」也落盘 —— 且必须写在原生库加载**之前**。
            //   为什么必须早：若崩溃发生在 Application.onCreate 的 loadLibrary 里，
            //   连 RuntimeInstallActivity 都不会被创建，界面上再好的诊断也看不到；
            //   只有落盘的文件能被取走。
            writeReport(context);
        } catch (Throwable t) {
            safeLog("init 失败", t);
        }
    }

    /**
     * 列出本机注册的 ContentProvider（Authority 列表）。
     * <p>失败返回 {@code "(读取失败)"} —— 它只是给排查提供便利，绝不能影响启动。
     */
    private static String providersLine(Context context) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(),
                            android.content.pm.PackageManager.GET_PROVIDERS);
            android.content.pm.ProviderInfo[] ps = info == null ? null : info.providers;
            if (ps == null || ps.length == 0) {
                return "(无)";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ps.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(ps[i].authority);
            }
            return sb.toString();
        } catch (Throwable t) {
            return "(读取失败)";
        }
    }

    /** 记一个路标。stage 写清楚「刚要做什么」，不要写「做完了什么」——崩溃点就在它之后。
     *  synchronized：启动期有多个线程会记路标（例如 RuntimeInstallActivity 的后台校验线程），
     *  不加锁会让两行内容交叉写坏，反而看不出顺序。 */
    public static synchronized void mark(String stage) {
        if (!ready) {
            // 还没 init（理论上不会发生）。仍然打到 logcat，便于 adb 场景。
            safeLog("[" + stage + "]", null);
            return;
        }
        writeBoth("[" + elapsed() + "] " + stage + "\n", false);
    }

    /** 把已经走过的路标原样读回来（给崩溃页 / 启动自检界面用；读不到就返回空串） */
    public static String readAll() {
        String s = readFrom(traceFile);
        if (s.isEmpty()) {
            s = readFrom(mediaFile);
        }
        if (s.isEmpty()) {
            s = readFrom(mirrorFile);
        }
        return s;
    }

    /**
     * 路标文件绝对路径（找不到时返回提示串），便于在界面上告诉玩家去哪拿文件。
     *
     * <p>★ 三个位置**全部列出**并按「用户越容易打开」排序：
     * {@code Android/media}（文件管理器直接可见、无需任何权限）
     * → {@code /sdcard/QCL}（需要存储权限）
     * → {@code Android/data}（Android 11+ 第三方文件管理器通常进不去）。
     * 只给一个路径的话，那条恰好走不通时用户就卡住了。
     */
    public static String pathForUser() {
        StringBuilder sb = new StringBuilder();
        appendPath(sb, mediaFile, "（文件管理器可直接打开）");
        appendPath(sb, mirrorFile, "（需要存储权限）");
        appendPath(sb, traceFile, "（Android 11+ 通常打不开）");
        return sb.length() == 0 ? "(外置存储不可用)" : sb.toString().trim();
    }

    private static void appendPath(StringBuilder sb, File f, String note) {
        if (f == null) {
            return;
        }
        sb.append(f.getAbsolutePath()).append(' ').append(note).append('\n');
    }

    /**
     * ★★★ 社区版新增：取路标的**最后 n 行**，直接显示在崩溃/失败弹框里。
     *
     * <p>为什么要在弹框里显示内容而不只是路径：让用户「去文件管理器找
     * {@code /sdcard/QCL/startup_trace.log} 再把内容发我」这件事，实际执行率很低
     * （路径难找、编码易错、很多人不知道文件管理器怎么进内部存储）。
     * 而**截一张图**几乎没有门槛 —— 堆栈和断点一次性到手。
     */
    public static String tail(int n) {
        String all = readAll();
        if (all.isEmpty()) {
            // 注意：init 现在跑在 attachBaseContext 里，所以「空」的含义比过去更极端 ——
            // 要么崩在 Application.attachBaseContext 之前，要么三个位置全都写不进去。
            return "(路标为空或读不到 —— 说明连 Application.attachBaseContext 都没走完，"
                    + "或三个落盘位置全部不可写；这本身就是重要线索)";
        }
        String[] lines = all.split("\n");
        int from = Math.max(0, lines.length - n);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                continue;
            }
            sb.append(lines[i]).append('\n');
        }
        return sb.toString();
    }

    /**
     * ★★★ 社区版新增：截断前检查上一次启动有没有走完。
     * 只在「旧路标非空、且找不到 {@link #MARK_STARTUP_OK}」时判定为未走完；
     * 旧路标为空（首次安装 / 从未成功写过）不算 —— 那时根本没有历史可查。
     */
    private static void checkLastRun(Context context) {
        try {
            String prev = readFrom(traceFile);
            if (prev.isEmpty()) {
                prev = readFrom(mediaFile);
            }
            if (prev.isEmpty()) {
                prev = readFrom(mirrorFile);
            }
            // ★★★ 本轮新增：**不只**看自己的路标，还要问系统「上次进程是怎么死的」。
            //   原因：若崩溃发生在 attachBaseContext 之前（Provider 阶段），或进程被
            //   native 信号直接杀死，路标文件可能压根没被写过（prev 为空）——
            //   此时只看 prev 会得出「首次安装，正常」的错误结论，把最关键的线索漏掉。
            //   系统侧的进程退出记录不受这些限制，而且不需要任何权限。
            //   ★ 独立 try/catch（防御纵深）：本类已改为反射实现，理论上不再抛，
            //   但这里是**判定链的关键路径** —— 一旦它抛任何东西（哪怕 Error），
            //   下面 lastStartupIncomplete / lastAbnormal 就全被跳过，
            //   弹框与安全模式会双双失效。所以再兜一层，保证判定一定执行到底。
            boolean sysCrash = false;
            try {
                sysCrash = ProcessExitDiagnostics.lastExitWasCrash(context);
            } catch (Throwable ignored) {
            }
            // ★★★ 本轮新增：安全模式的判据 —— 上一次「Java 侧启动流程」没走完（只看路标文件）。
            //   注意必须**在**下面那个 return 之前算，且不能用 sysCrash 参与 ——
            //   否则「游戏运行中崩溃」会让下次启动被误降级。
            lastStartupIncomplete = !prev.isEmpty() && !prev.contains(MARK_STARTUP_OK);
            if (!sysCrash && !lastStartupIncomplete) {
                return;
            }
            lastAbnormal = true;
            lastRawTrace = prev;
            String[] lines = prev.split("\n");
            int from = Math.max(0, lines.length - 14);
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < lines.length; i++) {
                if (!lines[i].isEmpty()) {
                    sb.append(lines[i]).append('\n');
                }
            }
            String sysDesc;
            try {
                sysDesc = ProcessExitDiagnostics.describeLastExit(context);
            } catch (Throwable t) {
                sysDesc = "(查询进程退出原因失败: " + t + ")";
            }
            lastTail = "【系统记录的退出原因】\n"
                    + sysDesc + "\n"
                    + "【Java 侧启动路标（最后一行 = 崩之前最后通过的阶段）】\n"
                    + (sb.length() == 0
                    ? "(路标为空 —— 连 Application.attachBaseContext 都没走完)\n"
                    : sb.toString());
        } catch (Throwable ignored) {
            // 判定失败就当「正常」，绝不因此弹框打扰用户
        }
    }

    /** 上一次启动是否没有走完（可能是闪退，也可能是用户中途退出） */
    public static boolean lastRunWasAbnormal() {
        return lastAbnormal;
    }

    /**
     * ★★★ 社区版新增（本轮）：上一次「启动初始化」是否没有走完。
     *
     * <p>给<b>安全模式</b>用 —— 只有它为 true 才说明崩溃发生在启动初始化里，
     * 跳过原生库加载才有意义。语义见 {@link #lastStartupIncomplete} 字段注释。
     */
    public static boolean lastRunStartupIncomplete() {
        return lastStartupIncomplete;
    }

    /** 上一次中断前的路标尾部 */
    public static String lastRunTail() {
        return lastTail;
    }

    /**
     * ★★★ 社区版新增（本轮）：写一份「一个文件包含全部信息」的诊断报告。
     *
     * <p>与 {@link #pathForUser()} 一样落在三个位置（media / sdcard / Android-data），
     * 但内容更完整：设备信息 + 系统侧进程退出原因 + 上次启动的完整路标 + 本次启动路标。
     *
     * <p>它在 {@link #init} 里被调用，也就是**原生库加载之前** ——
     * 因此即使应用此后立刻 native 崩溃、连界面都出不来，这份报告也已经落盘了。
     */
    private static void writeReport(Context context) {
        try {
            if (reportFile == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("=== QCL 诊断报告 ===\n");
            sb.append("生成时间 : ").append(stamp()).append('\n');
            sb.append("应用版本 : ").append(appVersion(context)).append('\n');
            sb.append("Android  : ").append(android.os.Build.VERSION.RELEASE)
                    .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
            sb.append("机型     : ").append(android.os.Build.MANUFACTURER).append(' ')
                    .append(android.os.Build.MODEL).append('\n');
            sb.append("ABI      : ").append(join(android.os.Build.SUPPORTED_ABIS)).append('\n');
            sb.append("Providers: ").append(providersLine(context)).append('\n');
            sb.append('\n');
            sb.append("----- 系统记录的进程退出原因（无需 root / adb）-----\n");
            // ★ 独立 try/catch：这一段若抛异常，后面「上次路标 + 本次路标 + logcat 缓冲区」
            //   会整段缺失 —— 而恰恰是那几段在崩溃时最有价值。
            try {
                sb.append(ProcessExitDiagnostics.describeLastExit(context)).append('\n');
            } catch (Throwable t) {
                sb.append("(查询进程退出原因失败: ").append(t).append(")\n");
            }
            sb.append('\n');
            sb.append("----- 上次启动的完整路标（最后一行 = 断点）-----\n");
            sb.append(lastRawTrace.isEmpty()
                    ? "(没有上一次记录 —— 首次安装，或上次正常走完)\n"
                    : lastRawTrace).append('\n');
            sb.append('\n');
            sb.append("----- 本次启动路标 -----\n");
            sb.append(readAll()).append('\n');
            sb.append('\n');
            sb.append("说明：本文件每次启动都会被重写；若应用打不开，直接把它发出来即可。\n");
            final String base = sb.toString();
            writeTo(reportFile, base, true);

            // ★★★ 本轮新增：把系统「崩溃日志缓冲区」也抓进来。
            //   为什么需要它：ProcessExitDiagnostics 依赖 API 30+；若用户设备是 Android 10 及以下，
            //   那条路完全拿不到东西，而 native 崩溃又没有 Java 堆栈 —— 就彻底瞎了。
            //   而 logcat 的 crash 缓冲区里会留下 libc 打印的「Fatal signal 11 ...」及其回溯，
            //   应用读取**自己进程**的日志是系统允许的（Android 4.1+ 起按 UID 过滤）。
            //   放在后台守护线程里做，绝不阻塞启动、也绝不参与主线程。
            final File f = reportFile;
            Thread t = new Thread(() -> {
                try {
                    String crash = readCrashLogcat();
                    if (!crash.isEmpty()) {
                        writeTo(f, base + "\n----- 系统崩溃日志缓冲区（logcat -b crash）-----\n"
                                + crash + "\n", true);
                    }
                } catch (Throwable ignored) {
                }
            }, "qcl-crashlog");
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {
            // 诊断手段本身绝不能成为崩溃源
        }
    }

    /**
     * ★★★ 本轮新增：读取 logcat 的 crash 缓冲区（只 dump 不跟随）。
     *
     * <p>这是**不依赖 adb、也不依赖 API 30** 的唯一能拿到 native 崩溃现场的手段：
     * native 段错误时 libc 会往该缓冲区打印 {@code Fatal signal 11 (SIGSEGV) ...}
     * 以及一串 {@code #00 pc ...} 回溯。应用读自己的日志是允许的。
     *
     * <p>失败（无权限、无该缓冲区、ROM 阉割了 logcat）一律返回空串，绝不影响启动。
     */
    private static String readCrashLogcat() {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "/system/bin/logcat", "-b", "crash", "-d", "-t", "300");
            pb.redirectErrorStream(true);
            p = pb.start();
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n < 300) {
                sb.append(line).append('\n');
                n++;
            }
            r.close();
            return sb.toString();
        } catch (Throwable t) {
            return "";
        } finally {
            try {
                if (p != null) {
                    p.destroy();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** 读应用版本名；失败返回 "?" */
    private static String appVersion(Context context) {
        try {
            android.content.pm.PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return pi == null || pi.versionName == null ? "?" : pi.versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 诊断报告的绝对路径（给界面提示用；找不到时返回空串） */
    public static String reportPathForUser() {
        return reportFile == null ? "" : reportFile.getAbsolutePath();
    }

    // ---------------- 内部工具 ----------------

    /** 三份一起写。truncate=true 时覆盖（init 用），false 时追加（mark 用）。 */
    private static void writeBoth(String text, boolean truncate) {
        writeTo(traceFile, text, truncate);
        if (mirrorFile != null) {
            writeTo(mirrorFile, text, truncate);
        }
        if (mediaFile != null) {
            writeTo(mediaFile, text, truncate);
        }
    }

    /** ★ 单次开/写/flush/close —— 崩溃强杀时最后一条也已落盘 */
    private static void writeTo(File f, String text, boolean truncate) {
        if (f == null) {
            return;
        }
        try {
            OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(f, !truncate), "UTF-8");
            w.write(text);
            w.flush();
            w.close();
        } catch (Throwable ignored) {
            // 观测手段本身绝不能成为崩溃源；镜像写失败也走这里
        }
    }

    private static String readFrom(File f) {
        if (f == null) {
            return "";
        }
        try {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(f), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            r.close();
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String elapsed() {
        long s = startMs == 0 ? 0 : (System.currentTimeMillis() - startMs);
        return "+" + s + "ms";
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String join(String[] arr) {
        if (arr == null || arr.length == 0) {
            return "?";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static void safeLog(String msg, Throwable t) {
        try {
            Log.e(TAG, msg, t);
        } catch (Throwable ignored) {
        }
    }
}
