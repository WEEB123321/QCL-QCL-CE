package com.qcl.launcher.launcher;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.util.Log;
import com.github.gzuliyujiang.oaid.DeviceIdentifier;
import com.qcl.launcher.manifest.AppManifest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/* loaded from: classes2.dex */
public class QCLApplication extends Application {
    private static final String SHARED_CRASH_DIR = "QCL";
    private static final String TAG = "QCLCrash";
    private static Context context;

    @Override // android.app.Application
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // ★★★ 社区版（本轮）：崩溃页/错误页跑在独立进程（:crash / :error_activity），
        //   它们同样会重跑 Application 生命周期。若不加区分，会有两个恶果：
        //     1) 启动崩在 native 或某 SDK 时，崩溃页进程跟着一起死 → 用户永远看不到弹框，
        //        表现就是「打开直接闪退、什么提示都没有」——正是当前最难查的那种现象；
        //     2) 崩溃页进程执行 StartupTrace.init，会把主进程刚写下的崩溃路标**截断清空**，
        //        等于亲手毁掉唯一证据。
        //   所以非主进程只保留最小初始化，把「能把弹框显示出来」放在第一位。
        if (isSecondaryProcess()) {
            return;
        }
        // ★★★ 社区版：路标的起点必须在这里，**不能在 onCreate**。
        //   完整时序是：
        //     进程创建 → Application 类加载 → attachBaseContext
        //     → **ContentProvider.onCreate（系统自动调用，代码里零引用也会跑）**
        //     → Application.onCreate
        //   把 init 放在 onCreate，就等于「Provider 阶段崩了，路标文件还是上一轮的旧内容」——
        //   而那恰恰是最典型的「打开直接闪退、什么都没留下」。
        //   放到这里之后，如果文件最后一行停在这条 mark 上，
        //   就**直接证明**崩在 ContentProvider 阶段（而不是我猜）。
        StartupTrace.init(this);
        StartupTrace.mark("Application.attachBaseContext 完成"
                + "（下一段是 ContentProvider，再下一段才是 onCreate）");
    }

    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        // ★★★ 社区版（本轮）：非主进程（崩溃页/错误页）不碰任何可能再次崩溃的东西 ——
        //   OAID 注册、AppManifest 建目录、加载 bytehook/SDL3 全部跳过。
        //   崩溃页的职责只有一个：把弹框显示出来。在它里面做重初始化，
        //   等于「为了报告崩溃而再崩一次」，用户最终什么都看不到。
        if (isSecondaryProcess()) {
            context = getApplicationContext();
            return;
        }
        // ★★★ 社区版：路标放在最前面 —— 它是唯一能在 native 崩溃后留下痕迹的手段
        //   （崩溃弹窗和 crash.log 都只对 Java 异常有效）。下面每个 mark 都写在
        //   有风险的操作**之前**，所以文件里最后一行 = 崩在了哪一步之前。
        //   （init 已经在 attachBaseContext 里做过了，这里是幂等的空调用，
        //     留着只是防止有人把 attachBaseContext 覆写删掉后路标静默失效。）
        StartupTrace.init(this);
        StartupTrace.mark("Application.onCreate 开始（说明 ContentProvider 阶段已全部通过）");
        StartupTrace.mark("安装崩溃处理器");
        installCrashLogger();
        StartupTrace.mark("注册 OAID 设备标识");
        // ★★★ 社区版加固：这是 Application.onCreate 里唯一一处**裸调用**的第三方 SDK。
        //   DeviceIdentifier 是 gzuliyujiang 的 OAID 库（纯 Java，走 Binder 连各厂商的
        //   设备标识服务）。在部分 ROM 上绑定服务会失败并抛异常，而这里一旦抛出，
        //   进程在「打开 App」的最前端就死了 —— 用户看到的就是「一打开就闪退」。
        //   设备标识只是用于统计/风控，拿不到完全不影响启动与游戏，所以直接降级忽略。
        try {
            DeviceIdentifier.register(this);
        } catch (Throwable t) {
            StartupTrace.mark("★ OAID 注册失败（已忽略，不影响启动）→ " + t);
            android.util.Log.e("QCLStartup", "OAID 注册失败", t);
        }
        context = getApplicationContext();
        // ★ 1.3.9：AppManifest 目录/SETTING_DIR 初始化提到 Application 级。
        //   之前只在 MainActivity/ApiService 里初始化，被 root 直接拉 PojavMinecraftActivity
        //   （绕过主界面）时 SETTING_DIR 还是 null → getGameLaunchSetting 读 public_game_setting.json
        //   路径错 → NPE 崩溃。提到这里后任何入口都会先初始化。
        StartupTrace.mark("初始化 AppManifest 目录");
        // ★★★ 社区版加固：同样不允许它把进程带走。initializeManifest 里要碰
        //   getExternalCacheDir()/getExternalFilesDir()（可能返回 null），已在内部做了兜底；
        //   这里再加一层保险：万一它仍抛异常，退化为「目录没建好但能启动」，
        //   后续用到某个目录时各自有判空，比整机闪退好得多。
        try {
            AppManifest.initializeManifest(this);
        } catch (Throwable t) {
            StartupTrace.mark("★ AppManifest 初始化失败（已忽略，继续启动）→ " + t);
            android.util.Log.e("QCLStartup", "AppManifest 初始化失败", t);
        }
        // ★★★ 1.1.1 SDL3：把 C++ 库提前到主线程加载，避免在游戏渲染线程 dlopen 时触发
        // libc++ 的 iostream/locale 静态初始化崩溃（fault addr 0x0）。
        // ★★★ 社区版（本轮）：**安全模式** —— 上一次启动初始化没走完时，本次跳过原生库加载。
        //   为什么必须要它：如果崩溃就发生在下面这两个 loadLibrary 里（native 段错误），
        //   进程会被信号直接杀掉 —— Java 的 try/catch 抓不住、崩溃弹框弹不出、
        //   连 RuntimeInstallActivity 都不会被创建（所以「上次启动没有走完」的提示也弹不出来）。
        //   结果是「打开就闪退、什么都拿不到」，而且每次如此、应用无法自救。
        //   跳过之后启动器至少能打开，用户才可能把诊断报告发出来。
        //   ⚠️ 判据必须用 lastRunStartupIncomplete()（只看 Java 路标），不能用 lastRunWasAbnormal()
        //      —— 后者把「游戏运行中崩溃」也算进来，会导致玩完一局游戏后启动器被误降级。
        //   ★ 曾经的怀疑对象：一度以为 libbytehook.so 是 ndk-build 从 jni_new/ 源码重编的，
        //      与官方预编译件不同，加载期 native 崩溃会很现实。
        //      **该假设已被证伪**：把官方 1.4.3 包里的 so 取出来逐个比对，libbytehook.so
        //      与 libSDL3.so 的 sha256 与官方**完全相同**（都是原样拷贝的预编译件）。
        //      所以安全模式的理由不再是「怀疑这两个库」，而是「**任何**在
        //      Application.onCreate 期间发生的 native 段错误都抓不住」——
        //      把它保留为通用兜底，判据仍只看 Java 路标。
        if (StartupTrace.lastRunStartupIncomplete()) {
            StartupTrace.mark("★★ 安全模式：上次启动初始化没有走完 → 本次跳过原生库加载"
                    + "（bytehook / SDL3）。若这样能正常打开，说明崩溃就在这两个库的加载里。");
            android.util.Log.w("QCLStartup", "安全模式：跳过 bytehook / SDL3 加载");
        } else {
            StartupTrace.mark("加载 bytehook（native，失败已被 catch）");
            try { System.loadLibrary("bytehook"); } catch (Throwable ignored) { }
            StartupTrace.mark("加载 SDL3（native，失败已被 catch）");
            try { System.loadLibrary("SDL3"); } catch (Throwable ignored) { }
        }
        StartupTrace.mark("Application.onCreate 完成");
    }

    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() { // from class: com.qcl.launcher.launcher.QCLApplication$$ExternalSyntheticLambda0
            @Override // java.lang.Thread.UncaughtExceptionHandler
            public final void uncaughtException(Thread thread, Throwable th) {
                QCLApplication.this.m211x63dcaab4(defaultUncaughtExceptionHandler, thread, th);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$installCrashLogger$0$com-qcl-launcher-launcher-QCLApplication, reason: not valid java name */
    public /* synthetic */ void m211x63dcaab4(Thread.UncaughtExceptionHandler uncaughtExceptionHandler, Thread thread, Throwable th) {
        // ★★★ 1.3.0：Finalizer 线程关 ZipFile 时偶发「close failed: EIO」——
        //   本质是 zip 对应的文件被删/覆盖后，GC 才回收 fd 去 close，属无害的清理噪音。
        //   以前会当成真崩溃弹崩溃页并杀进程。现在直接吞掉，不弹窗、不杀进程。
        if (isBenignZipClose(thread, th)) {
            try {
                Log.w("QCLCrash", "忽略无害的 ZipFile 清理异常（" + thread.getName() + "）", th);
            } catch (Throwable ignored) {
            }
            return;
        }
        // ★★★ 社区版新增：本地统计 —— 记一次 Java 未捕获异常。
        //   放在 saveCrashLog 之前、且独立 try/catch：统计写盘失败绝不能影响崩溃日志落盘。
        //   注意口径：native 段错误（SIGSEGV 等）根本走不到这里，所以统计页上的
        //   「崩溃次数」只代表 Java 崩溃，界面上已如实标注。
        try {
            com.qcl.launcher.launcher.stats.StatsTracker.noteCrash(this);
        } catch (Throwable unused) {
        }
        try {
            saveCrashLog(thread, th);
        } catch (Throwable unused) {
        }
        try {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            printWriter.println("time   : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            printWriter.println("thread : " + thread.getName());
            printWriter.println("android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            printWriter.println("device : " + Build.MANUFACTURER + " " + Build.MODEL + " " + Build.CPU_ABI);
            printWriter.println("package: " + getPackageName());
            printWriter.println("--------------------------------------------------");
            th.printStackTrace(printWriter);
            // ★★★ 社区版：把启动路标一起贴到崩溃页上 —— 一张截图就同时给出
            //   「堆栈」和「走到哪一步才崩的」，玩家不用再去翻文件。
            printWriter.println();
            printWriter.println("========= 启动路标（最后一行 = 崩之前最后通过的阶段）=========");
            try {
                printWriter.println(StartupTrace.readAll());
            } catch (Throwable ignored) {
            }
            printWriter.flush();
            Intent intent = new Intent(this, (Class<?>) CrashReportActivity.class);
            intent.addFlags(335544320);
            intent.putExtra("crash_text", stringWriter.toString());
            // ★★★ 社区版：把启动路标放进右侧「摘要」区 —— 它一打开崩溃页就能看到。
            //   只放左侧完整日志里是不够的：那是长文本、默认停在顶部（堆栈），
            //   路标在尾部，用户截图根本截不到，等于没给。
            intent.putExtra("crash_summary",
                    th.getClass().getName() + ": " + String.valueOf(th.getMessage())
                            + "\n\n【启动路标：最后一行 = 崩之前最后通过的阶段】\n"
                            + StartupTrace.tail(20));
            startActivity(intent);
            // ★★★ 社区版：不要紧跟 startActivity 就杀进程。
            //   startActivity 是**异步**的（只是把启动请求交给 system_server），
            //   而 killProcess 同步立即生效 —— 进程往往在 CrashReportActivity
            //   真正被创建出来之前就已经死了，结果就是「崩了但用户什么都没看到」，
            //   表现与「无提示闪退」完全一样，白白丢掉一次定位机会。
            //   延迟一下再退出，给崩溃页留出创建时间。
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Process.killProcess(Process.myPid());
                    System.exit(10);
                }
            }, 1200L);
        } catch (Throwable unused2) {
            if (uncaughtExceptionHandler != null) {
                uncaughtExceptionHandler.uncaughtException(thread, th);
            }
        }
    }

    /** ★ 1.3.0：判断是不是「守护线程上关 ZipFile 的 EIO」这种无害异常 */
    private static boolean isBenignZipClose(Thread thread, Throwable th) {
        if (thread == null || !thread.isDaemon()) {
            return false;
        }
        for (Throwable x = th; x != null; x = x.getCause()) {
            String msg = String.valueOf(x.getMessage());
            if (msg != null && msg.contains("close failed")) {
                return true;
            }
        }
        return false;
    }

    private void saveCrashLog(Thread thread, Throwable th) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        printWriter.println("time   : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        printWriter.println("thread : " + thread.getName());
        printWriter.println("android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        printWriter.println("device : " + Build.MANUFACTURER + " " + Build.MODEL + " " + Build.CPU_ABI);
        printWriter.println("package: " + getPackageName());
        printWriter.println("--------------------------------------------------");
        th.printStackTrace(printWriter);
        // ★★★ 社区版：crash.log 里也带上启动路标，省得两个文件对着看。
        printWriter.println();
        printWriter.println("========= 启动路标（最后一行 = 崩之前最后通过的阶段）=========");
        try {
            printWriter.println(StartupTrace.readAll());
        } catch (Throwable ignored) {
        }
        printWriter.flush();
        String stringWriter2 = stringWriter.toString();
        Log.e("QCLCrash", stringWriter2);
        writeTo(new File(getExternalFilesDir(null), "crash.log"), stringWriter2);
        writeTo(new File(Environment.getExternalStorageDirectory(), "QCL/crash.log"), stringWriter2);
        // ★★★ 社区版：再写一份到 Android/media/<包名>/crash.log。
        //   前两份各有一个致命短板：Android/data 在 Android 11+ 第三方文件管理器打不开；
        //   /sdcard/QCL 需要 MANAGE_EXTERNAL_STORAGE，而「崩在 Provider 阶段」时
        //   应用从没成功启动过、那个权限也从没被授予过 —— 最需要它时恰好不可用。
        //   Android/media 是分区存储里唯一对外可见、且不需要任何权限的应用目录。
        //   这样「闪退后把日志取出来发作者」第一次变成真的可行。
        try {
            File[] medias = getExternalMediaDirs();
            if (medias != null && medias.length > 0 && medias[0] != null) {
                writeTo(new File(medias[0], "crash.log"), stringWriter2);
            }
        } catch (Throwable ignored) {
        }
        // ★★★ 社区版：留一个「上次使用中崩溃过」的标记。
        //   上面那个「上次启动没有走完」的提示只看**启动**有没有跑完 ——
        //   而启动成功之后在页面里崩掉的，路标最后一行是「启动成功」，那个提示不会出现，
        //   日志就埋在 crash.log 里没人去翻。这里补上，下次打开启动器会主动弹出来。
        try {
            CrashMarker.mark(this, th);
        } catch (Throwable ignored) {
        }
    }

    private void writeTo(File file, String str) {
        try {
            File parentFile = file.getParentFile();
            if (parentFile != null && !parentFile.exists()) {
                parentFile.mkdirs();
            }
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8");
            try {
                outputStreamWriter.write(str);
                outputStreamWriter.write("\n\n");
                outputStreamWriter.close();
            } finally {
            }
        } catch (Throwable unused) {
        }
    }

    public static Context getContext() {
        return context;
    }

    public static void releaseContext() {
        context = null;
    }

    // ---------------- 进程判定 ----------------

    /**
     * ★★★ 社区版新增（本轮）：当前是否运行在「非主进程」。
     *
     * <p>本应用有两个派生进程：{@code :crash}（{@link CrashReportActivity} 崩溃页）与
     * {@code :error_activity}（CustomActivityOnCrash 的错误页）。它们由系统在崩溃后拉起，
     * 同样会完整跑一遍 {@code Application} 生命周期。派生进程里必须做减法，
     * 否则「报告崩溃的界面」本身会变成新的崩溃源。
     */
    private boolean isSecondaryProcess() {
        try {
            String proc = currentProcessName();
            return proc != null && !getPackageName().equals(proc);
        } catch (Throwable t) {
            // 判断失败一律按主进程处理：宁可多做初始化，也绝不能把主进程误判成派生进程
            // —— 那会让启动器彻底不工作，比崩溃更糟。
            return false;
        }
    }

    private String currentProcessName() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return getProcessName();
            }
        } catch (Throwable ignored) {
        }
        // API 26/27 没有 Application.getProcessName()，退回读 /proc/self/cmdline
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("/proc/self/cmdline")));
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = r.read()) > 0) {
                sb.append((char) c);
            }
            r.close();
            String s = sb.toString().trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }
}
