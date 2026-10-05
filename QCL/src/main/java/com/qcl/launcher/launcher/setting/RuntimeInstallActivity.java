/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.app.Activity
 *  android.content.Context
 *  android.content.Intent
 *  android.content.SharedPreferences
 *  android.content.res.Configuration
 *  android.net.Uri
 *  android.os.Build$VERSION
 *  android.os.Bundle
 *  android.os.Environment
 *  android.os.Handler
 *  android.os.Looper
 *  android.os.SystemClock
 *  android.util.Log
 *  android.view.View
 *  android.view.View$OnClickListener
 *  android.widget.Button
 *  android.widget.ImageView
 *  android.widget.ProgressBar
 *  android.widget.TextView
 *  androidx.annotation.Nullable
 *  androidx.appcompat.app.AlertDialog$Builder
 *  androidx.appcompat.app.AppCompatActivity
 *  androidx.core.app.ActivityCompat
 *  androidx.core.content.ContextCompat
 */
package com.qcl.launcher.launcher.setting;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.setting.InitializeSetting;
import com.qcl.launcher.launcher.setting.InstallLauncherFile;
import com.qcl.launcher.launcher.setting.RuntimeUtils;
import com.qcl.launcher.launcher.setting.launcher.LauncherSetting;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.Architecture;
import com.qcl.launcher.utils.LocaleUtils;
import com.qcl.launcher.utils.file.AssetsUtils;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.qcl.launcher.R;
public class RuntimeInstallActivity
extends AppCompatActivity
implements View.OnClickListener {
    private final Map<String, Item> items = new LinkedHashMap<String, Item>();
    private final Map<String, Spec> specs = new LinkedHashMap<String, Spec>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Button installButton;
    private String arch;
    private boolean installing = false;
    private View prepareLayout;
    public ProgressBar loadingProgress;
    public TextView loadingText;
    public TextView loadingProgressText;
    public LauncherSetting launcherSetting;
    private boolean prepared = false;
    /**
     * ★★★ 社区版新增（本轮）：是否已经把接力棒交给 {@link MainActivity}。
     *
     * <p>用途是让「上次启动有没有走完」这个判据不误报：用户在安装页按返回键正常退出时，
     * 路标里同样不会有「启动成功」，会被误判成闪退。所以 {@code onDestroy} 里
     * 只要「是本页自己结束、且没有交给主界面」就补记一次正常结束标记。
     * <p>而<b>已经交给主界面</b>的情况绝不能补记 —— 主界面可能随后才崩，
     * 那正是我们要抓的场景。
     */
    private boolean handedOffToMain = false;
    private static final String SP_RUNTIME = "qcl_runtime";
    private static final String KEY_READY = "ready";
    private static final String KEY_APP_VERSION = "app_version";
    private static final String KEY_RUNTIME_VERSION = "runtime_version";

    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLanguage(base));
    }

    @SuppressLint(value={"SetTextI18n"})
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // ★★★ 社区版：路标提到 super.onCreate **之前**。
        //   super.onCreate 会做 AppCompatDelegate 初始化与主题校验 —— 主题不匹配会直接抛
        //   "You need to use a Theme.AppCompat theme (or descendant) with this activity"。
        //   它是 onCreate 里唯一不在下面 try/catch 覆盖范围内的一行。路标提前后，
        //   「崩在 super」与「崩在 super 之后」就能在 startup_trace.log 里区分开。
        com.qcl.launcher.launcher.StartupTrace.mark("RuntimeInstallActivity.onCreate 进入（super.onCreate 之前）");
        super.onCreate(savedInstanceState);
        com.qcl.launcher.launcher.StartupTrace.mark("RuntimeInstallActivity.onCreate 开始（启动入口）");
        // ★★★ 社区版加固：整个 onCreate 主体包 try/catch。
        //   这里是清单里唯一的 LAUNCHER 入口，也是「打开 App」最先执行的一批代码：
        //   原先从 setContentView 到 requestPermission 一路裸奔，任何一处异常都会
        //   冒泡到主线程 → 全局处理器 Process.killProcess → 表现为无界面闪退。
        //   （函数体刻意不缩进，保持与上游 diff 最小。）
        try {
        this.setContentView(R.layout.activity_runtime_install);
        com.qcl.launcher.launcher.StartupTrace.mark("RuntimeInstallActivity：安装页布局已加载");
        // initializeManifest 内部已对 getExternalCacheDir()/getExternalFilesDir() 的
        // null 做了兜底（见 AppManifest），这里再包一层只是纵深防御。
        AppManifest.initializeManifest((Context)this);
        this.arch = RuntimeInstallActivity.deviceArchName();
        this.items.put("lwjgl", new Item((ImageView)this.findViewById(R.id.qcl_lwjgl_state), (ProgressBar)this.findViewById(R.id.qcl_lwjgl_progress), (TextView)this.findViewById(R.id.qcl_lwjgl_detail)));
        this.items.put("cacio", new Item((ImageView)this.findViewById(R.id.qcl_cacio_state), (ProgressBar)this.findViewById(R.id.qcl_cacio_progress), (TextView)this.findViewById(R.id.qcl_cacio_detail)));
        this.items.put("cacio17", new Item((ImageView)this.findViewById(R.id.qcl_cacio17_state), (ProgressBar)this.findViewById(R.id.qcl_cacio17_progress), (TextView)this.findViewById(R.id.qcl_cacio17_detail)));
        this.items.put("java8", new Item((ImageView)this.findViewById(R.id.qcl_java8_state), (ProgressBar)this.findViewById(R.id.qcl_java8_progress), (TextView)this.findViewById(R.id.qcl_java8_detail)));
        this.items.put("java17", new Item((ImageView)this.findViewById(R.id.qcl_java17_state), (ProgressBar)this.findViewById(R.id.qcl_java17_progress), (TextView)this.findViewById(R.id.qcl_java17_detail)));
        this.items.put("java21", new Item((ImageView)this.findViewById(R.id.qcl_java21_state), (ProgressBar)this.findViewById(R.id.qcl_java21_progress), (TextView)this.findViewById(R.id.qcl_java21_detail)));
        this.items.put("java25", new Item((ImageView)this.findViewById(R.id.qcl_java25_state), (ProgressBar)this.findViewById(R.id.qcl_java25_progress), (TextView)this.findViewById(R.id.qcl_java25_detail)));
        this.items.put("jna", new Item((ImageView)this.findViewById(R.id.qcl_jna_state), (ProgressBar)this.findViewById(R.id.qcl_jna_progress), (TextView)this.findViewById(R.id.qcl_jna_detail)));
        this.items.put("sdl", new Item((ImageView)this.findViewById(R.id.qcl_sdl_state), (ProgressBar)this.findViewById(R.id.qcl_sdl_progress), (TextView)this.findViewById(R.id.qcl_sdl_detail)));
        this.installButton = (Button)this.findViewById(R.id.qcl_runtime_install);
        this.installButton.setOnClickListener((View.OnClickListener)this);
        this.specs.put("lwjgl", new Spec("lwjgl", "app_runtime/pojav", AppManifest.POJAV_LIB_DIR, false, "lwjgl3"));
        this.specs.put("cacio", new Spec("cacio", "app_runtime/caciocavallo", AppManifest.CACIOCAVALLO_DIR, false, "cacio-shared-1.10-SNAPSHOT.jar"));
        this.specs.put("cacio17", new Spec("cacio17", "app_runtime/caciocavallo17", AppManifest.CACIOCAVALLO17_DIR, false, "cacio-agent.jar"));
        this.specs.put("java8", new Spec("java8", "app_runtime/java/jre8", AppManifest.JAVA_DIR + "/default", true, "bin/java"));
        this.specs.put("java17", new Spec("java17", "app_runtime/java/jre17", AppManifest.JAVA_DIR + "/JRE17", true, "lib/modules"));
        this.specs.put("java21", new Spec("java21", "app_runtime/java/jre21", AppManifest.JAVA_DIR + "/JRE21", true, "lib/modules"));
        this.specs.put("java25", new Spec("java25", "app_runtime/java/jre25", AppManifest.JAVA_DIR + "/JRE25", true, "lib/modules"));
        this.specs.put("jna", new Spec("jna", "app_runtime/lwjgl333/jna", AppManifest.POJAV_LIB_DIR + "/jna", false, RuntimeInstallActivity.deviceAbiDirName()));
        this.specs.put("sdl", new Spec("sdl", "app_runtime/pojav", this.getApplicationInfo().nativeLibraryDir, false, "libSDL3.so"));
        this.prepareLayout = this.findViewById(R.id.qcl_prepare_layout);
        this.loadingProgress = (ProgressBar)this.findViewById(R.id.loading_progress_bar);
        this.loadingText = (TextView)this.findViewById(R.id.loading_text);
        this.loadingProgressText = (TextView)this.findViewById(R.id.loading_progress_text);
        if (this.installButton != null) {
            this.installButton.setEnabled(false);
        }
        // ★★★ 社区版新增：「上次启动没有走完」提示。
        //   为什么需要它：如果崩溃发生在 ContentProvider 阶段（Application.onCreate 之前），
        //   崩溃处理器**还没装上** —— 弹不出崩溃页、crash.log 也是空的，用户看到的正是
        //   「打开直接闪退、什么都没留下」。那种情况下**下一次启动是唯一能拿到线索的时机**
        //   （上次的路标文件还在）。这里在进入权限/校验流程之前先把它弹给用户，
        //   用户截一张图，断点就一目了然。
        //   ★ 顺序很重要：必须先弹框、等用户点掉再走 requestPermission()。
        //     缓存命中路径会在 init() 里立刻 runOnUiThread(enterLauncher) → finish()，
        //     本页一销毁，刚弹出来的框就跟着没了，用户什么都看不到。
        if (com.qcl.launcher.launcher.StartupTrace.lastRunWasAbnormal()) {
            this.showLastRunAbnormal();
        } else {
            // ★★★ 社区版：启动没异常，但**上次使用中崩过** —— 也提示一次。
            //   这一段专门覆盖「启动成功之后在页面里崩掉」的情况（见 CrashMarker 的类注释）。
            String pending = null;
            try {
                pending = com.qcl.launcher.launcher.CrashMarker.pending(this);
            } catch (Throwable ignored) {
            }
            if (pending != null && !pending.isEmpty()) {
                this.showRuntimeCrash(pending);
            } else {
                this.requestPermission();
            }
        }
        } catch (Throwable t) {
            com.qcl.launcher.launcher.StartupTrace.mark("★ RuntimeInstallActivity.onCreate 失败（已拦截，避免整机闪退）→ " + t);
            Log.e("QCLStartup", "RuntimeInstallActivity.onCreate 失败", t);
            this.showStartupFailure(t);
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (Environment.isExternalStorageManager()) {
                this.init();
            } else {
                Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
                intent.setData(Uri.parse((String)("package:" + this.getPackageName())));
                this.startActivityForResult(intent, 1000);
            }
        } else if (ActivityCompat.checkSelfPermission((Context)this, (String)"android.permission.READ_EXTERNAL_STORAGE") == 0 && ContextCompat.checkSelfPermission((Context)this, (String)"android.permission.WRITE_EXTERNAL_STORAGE") == 0) {
            this.init();
        } else {
            ActivityCompat.requestPermissions((Activity)this, (String[])new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"}, (int)1000);
        }
    }

    private boolean isRuntimeReadyCached() {
        try {
            SharedPreferences sp = this.getSharedPreferences(SP_RUNTIME, 0);
            if (!sp.getBoolean(KEY_READY, false)) {
                return false;
            }
            if (sp.getInt(KEY_APP_VERSION, -1) != this.getCurrentAppVersionCode()) {
                return false;
            }
            return sp.getInt(KEY_RUNTIME_VERSION, -1) == this.getCurrentRuntimeVersion();
        }
        catch (Throwable t) {
            return false;
        }
    }

    private void saveRuntimeReadyCache() {
        try {
            this.getSharedPreferences(SP_RUNTIME, 0).edit().putBoolean(KEY_READY, true).putInt(KEY_APP_VERSION, this.getCurrentAppVersionCode()).putInt(KEY_RUNTIME_VERSION, this.getCurrentRuntimeVersion()).apply();
            this.println("[QCL_RUNTIME] \u7f13\u5b58\u5df2\u5199\u5165\uff08ready=true, appVersionCode=" + this.getCurrentAppVersionCode() + ", runtimeVersion=" + this.getCurrentRuntimeVersion() + "\uff09\u2193 \u4e0b\u6b21\u542f\u52a8\u5c06\u76f4\u63a5\u8fdb\u4e3b\u754c\u9762");
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private int getCurrentAppVersionCode() {
        try {
            return this.getPackageManager().getPackageInfo((String)this.getPackageName(), (int)0).versionCode;
        }
        catch (Throwable t) {
            return 0;
        }
    }

    private void println(String msg) {
        System.out.println(msg);
        Log.i((String)"QCL_RUNTIME", (String)msg);
    }

    private int getCurrentRuntimeVersion() {
        try {
            String s = AssetsUtils.readAssetsTxt((Context)this, "app_runtime/version");
            return Integer.parseInt(String.valueOf(s).trim());
        }
        catch (Throwable t) {
            return -1;
        }
    }

    private void init() {
        if (this.prepared) {
            return;
        }
        this.prepared = true;
        com.qcl.launcher.launcher.StartupTrace.mark("RuntimeInstallActivity.init()：开始运行环境校验");
        // ★ 默认控键布局同步（后台线程）：缓存命中路径会跳过 checkBaseFiles，
        //   必须在这里也执行，否则布局修正到不了已装齐的老用户；进主界面前 join，避免读到半份布局。
        final Thread controlSyncThread = new Thread(() ->
                com.qcl.launcher.launcher.setting.InstallLauncherFile.syncDefaultControl(getApplicationContext()));
        controlSyncThread.start();
        if (this.isRuntimeReadyCached()) {
            com.qcl.launcher.launcher.StartupTrace.mark("运行环境缓存命中 → 直接进主界面（enterLauncher）");
            try {
                controlSyncThread.join(3000L);
            } catch (InterruptedException ignored) {
            }
            this.println("[QCL_RUNTIME] \u7f13\u5b58\u547d\u4e2d\uff08ready + appVersion + runtimeVersion \u5168\u5bf9\uff09\u2192 \u76f4\u63a5\u8fdb\u4e3b\u754c\u9762");
            this.runOnUiThread(this::enterLauncher);
            return;
        }
        this.println("[QCL_RUNTIME] \u7f13\u5b58\u672a\u547d\u4e2d \u2192 \u8fdb\u5165 8 \u9879\u8fd0\u884c\u73af\u5883\u6821\u9a8c (appVersionCode=" + this.getCurrentAppVersionCode() + ", runtimeVersion=" + this.getCurrentRuntimeVersion() + ", \u5df2\u8bb0\u5f55app=" + this.getSharedPreferences(SP_RUNTIME, 0).getInt(KEY_APP_VERSION, -1) + ", \u5df2\u8bb0\u5f55runtime=" + this.getSharedPreferences(SP_RUNTIME, 0).getInt(KEY_RUNTIME_VERSION, -1) + ", \u5df2\u8bb0\u5f55ready=" + this.getSharedPreferences(SP_RUNTIME, 0).getBoolean(KEY_READY, false) + ")");
        new Thread(() -> {
            com.qcl.launcher.launcher.StartupTrace.mark("后台校验线程：读取启动器设置");
            // ★★★ 社区版加固（本轮）：整段包 try/catch。
            //   这是**全新安装必然要走**的路径（我们的 debug 包因签名与官方版不同，
            //   每次安装都是全新安装），而这里原先一处 try/catch 都没有 ——
            //   checkBaseFiles / initializeLauncherSetting 任何一处抛异常，线程就带着
            //   未捕获异常死掉，全局处理器随即弹崩溃页并 Process.killProcess，
            //   对外表现正是「打开直接闪退」，且完全拿不到有用的错误信息。
            //   现在改为：拦截 → 记路标 + logcat → 在安装页上明确报错，进程不再被杀死。
            try {
                this.launcherSetting = InitializeSetting.initializeLauncherSetting();
                this.runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= 28) {
                        this.getWindow().getAttributes().layoutInDisplayCutoutMode = this.launcherSetting != null && this.launcherSetting.fullscreen ? 1 : 2;
                    }
                    this.getWindow().setFlags(256, 256);
                });
                com.qcl.launcher.launcher.StartupTrace.mark("后台校验线程：checkBaseFiles 基础文件检查");
                InstallLauncherFile.checkBaseFiles(this);
                this.runOnUiThread(() -> {
                    if (this.prepareLayout != null) {
                        this.prepareLayout.setVisibility(8);
                    }
                    if (this.installButton != null) {
                        this.installButton.setEnabled(true);
                    }
                });
                com.qcl.launcher.launcher.StartupTrace.mark("后台校验线程：checkAll 八项运行环境校验");
                this.checkAll();
                com.qcl.launcher.launcher.StartupTrace.mark("后台校验线程：校验结束，准备进主界面");
                this.main.post(this::refreshDrawables);
                this.main.post(this::enterIfAllReady);
            } catch (Throwable t) {
                com.qcl.launcher.launcher.StartupTrace.mark(
                        "★ 运行环境校验线程失败（已拦截，避免整机闪退）→ " + t);
                Log.e("QCLStartup", "运行环境校验线程失败", t);
                final Throwable cause = t;
                this.main.post(() -> this.showStartupFailure(cause));
            }
        }).start();
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1000) {
            if (grantResults.length > 0 && grantResults[0] == 0) {
                this.init();
            } else if (ActivityCompat.shouldShowRequestPermissionRationale((Activity)this, (String)"android.permission.WRITE_EXTERNAL_STORAGE")) {
                new AlertDialog.Builder((Context)this).setMessage(R.string.storage_permissions_remind).setPositiveButton((CharSequence)"OK", (dialog1, which) -> ActivityCompat.requestPermissions((Activity)this, (String[])new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"}, (int)1000)).setNegativeButton((CharSequence)"Cancel", null).create().show();
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1000 && Build.VERSION.SDK_INT >= 30) {
            if (Environment.isExternalStorageManager()) {
                this.init();
            } else {
                new AlertDialog.Builder((Context)this).setMessage(R.string.storage_permissions_remind).setPositiveButton((CharSequence)"OK", (dialog1, which) -> this.requestPermission()).setNegativeButton((CharSequence)"Cancel", null).create().show();
            }
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleUtils.setLanguage((Context)this);
    }

    public void onBackPressed() {
        if (this.prepared) {
            super.onBackPressed();
        }
    }

    protected void onDestroy() {
        // ★★★ 社区版新增（本轮）：用户主动结束安装页（按返回键 / 从最近任务划掉）也算
        //   「正常结束」。否则下次启动会误报「上次启动没有走完」，把一个正常操作说成闪退 ——
        //   一个总在喊狼来了的提示，用户第三次就会直接无视它，那时它才真的没用。
        //   已交给主界面的情况不补记（见 handedOffToMain 的说明）。
        if (this.isFinishing() && !this.handedOffToMain) {
            com.qcl.launcher.launcher.StartupTrace.mark(
                    com.qcl.launcher.launcher.StartupTrace.MARK_STARTUP_OK);
        }
        this.worker.shutdownNow();
        super.onDestroy();
    }

    private void checkAll() {
        for (Map.Entry<String, Spec> e : this.specs.entrySet()) {
            Spec spec = e.getValue();
            Item item = this.items.get(e.getKey());
            if (item == null) continue;
            item.installed = this.isInstalled(spec);
        }
    }

    private boolean isInstalled(Spec spec) {
        try {
            if (spec.isJava) {
                if ("java25".equals(spec.key) && "x86".equals(this.arch)) {
                    return true;
                }
                if (!RuntimeUtils.isLatest((Context)this, spec.targetDir, spec.assetDir)) {
                    return false;
                }
                if ("java8".equals(spec.key)) {
                    return new File(spec.targetDir, "bin/java").exists();
                }
                return new File(spec.targetDir, "lib/modules").exists();
            }
            File dir = new File(spec.targetDir);
            if (!dir.isDirectory()) {
                return false;
            }
            File[] children = dir.listFiles();
            if (children == null || children.length == 0) {
                return false;
            }
            return spec.marker == null || new File(dir, spec.marker).exists();
        }
        catch (Throwable t) {
            return false;
        }
    }

    private void refreshDrawables() {
        for (Map.Entry<String, Item> e : this.items.entrySet()) {
            Item item = e.getValue();
            if (item.state == null) continue;
            this.applyStateIcon(item);
            item.state.setVisibility(0);
        }
        if (this.installButton != null) {
            this.installButton.setText(R.string.splash_runtime_install);
        }
    }

    private void enterIfAllReady() {
        for (Item item : this.items.values()) {
            if (item.installed) continue;
            return;
        }
        this.saveRuntimeReadyCache();
        this.enterLauncher();
    }

    private boolean allReady() {
        for (Item item : this.items.values()) {
            if (item.installed) continue;
            return false;
        }
        return true;
    }

    public void onClick(View v) {
        if (v == this.installButton) {
            if (this.installing) {
                return;
            }
            if (!this.isJavaArchSupported()) {
                new AlertDialog.Builder((Context)this).setTitle(R.string.splash_runtime_failed_title).setMessage((CharSequence)this.getString(R.string.splash_runtime_arch_unsupported, new Object[]{this.arch})).setPositiveButton((CharSequence)"OK", null).show();
                return;
            }
            this.installAll();
        }
    }

    private boolean isJavaArchSupported() {
        try {
            String[] javaDirs = new String[]{"jre8", "jre17", "jre21", "jre25"};
            int supported = 0;
            block2: for (String dir : javaDirs) {
                String[] files = this.getAssets().list("app_runtime/java/" + dir);
                if (files == null) continue;
                for (String f : files) {
                    if (!f.equals("bin-" + this.arch + ".tar.xz")) continue;
                    ++supported;
                    continue block2;
                }
            }
            return supported > 0;
        }
        catch (Throwable t) {
            return false;
        }
    }

    private void installAll() {
        this.installing = true;
        this.installButton.setEnabled(false);
        this.installButton.setText(R.string.splash_runtime_installing);
        this.worker.execute(() -> {
            StringBuilder failed = new StringBuilder();
            for (Map.Entry<String, Spec> e : this.specs.entrySet()) {
                Spec spec = e.getValue();
                Item item = this.items.get(e.getKey());
                if (item == null || item.installed || "sdl".equals(spec.key) || "java25".equals(spec.key) && "x86".equals(this.arch)) continue;
                this.main.post(() -> this.beginItem(item));
                try {
                    if (spec.isJava) {
                        RuntimeUtils.installJava((Context)this, spec.targetDir, spec.assetDir, this.arch, this.createListener(item));
                    } else {
                        RuntimeUtils.install((Context)this, spec.targetDir, spec.assetDir, this.createListener(item));
                    }
                    item.installed = true;
                }
                catch (Throwable t) {
                    Log.w((String)"jrelog", (String)("[\u8fd0\u884c\u73af\u5883] \u5b89\u88c5\u5931\u8d25 " + spec.key + " (" + spec.assetDir + " \u2192 " + spec.targetDir + ")"), (Throwable)t);
                    item.installed = false;
                    failed.append("\n\u00b7 ").append(spec.key).append(": ").append(t);
                }
                this.main.post(() -> this.endItem(item));
            }
            this.installing = false;
            String failMsg = failed.length() == 0 ? null : failed.toString();
            this.main.post(() -> {
                this.installButton.setEnabled(true);
                this.installButton.setText(R.string.splash_runtime_install);
                this.checkAll();
                this.refreshDrawables();
                if (failMsg != null) {
                    new AlertDialog.Builder((Context)this).setTitle(R.string.splash_runtime_failed_title).setMessage((CharSequence)failMsg).setPositiveButton((CharSequence)"OK", null).show();
                    return;
                }
                if (this.allReady()) {
                    this.enterLauncher();
                }
            });
        });
    }

    private void beginItem(Item item) {
        if (item.state != null) {
            item.state.setVisibility(8);
        }
        if (item.progress != null) {
            item.progress.setVisibility(0);
        }
    }

    private void endItem(Item item) {
        if (item.progress != null) {
            item.progress.setVisibility(8);
        }
        if (item.state != null) {
            this.applyStateIcon(item);
            item.state.setVisibility(0);
        }
        if (item.detail != null) {
            item.detail.setVisibility(8);
        }
    }

    private void applyStateIcon(Item item) {
        if (item.state == null) {
            return;
        }
        item.state.setImageResource(item.installed ? R.drawable.ic_baseline_done_black : R.drawable.ic_baseline_refresh_black);
        item.state.setColorFilter(item.installed ? -13730510 : -15374912);
    }

    private RuntimeUtils.InstallListener createListener(final Item item) {
        final long[] lastUpdate = new long[]{0L};
        return new RuntimeUtils.InstallListener(){

            @Override
            public void onUpdate(String detail) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastUpdate[0] < 50L) {
                    return;
                }
                lastUpdate[0] = now;
                RuntimeInstallActivity.this.main.post(() -> {
                    if (item.detail != null) {
                        item.detail.setText((CharSequence)detail);
                        item.detail.setVisibility(0);
                    }
                });
            }

            @Override
            public void onStage(String stageKey) {
                String text = "patching".equals(stageKey) ? RuntimeInstallActivity.this.getString(R.string.splash_runtime_patching) : RuntimeInstallActivity.this.getString(R.string.splash_runtime_installing);
                RuntimeInstallActivity.this.main.post(() -> {
                    if (item.detail != null) {
                        item.detail.setText((CharSequence)text);
                        item.detail.setVisibility(0);
                    }
                });
            }

            @Override
            public void onProgress(int percent) {
            }
        };
    }

    private void enterLauncher() {
        // ★★★ 社区版加固：这是「进入主界面」的最后一道门，而且它会在**主线程的
        //   runOnUiThread Runnable 里**执行（运行环境缓存命中路径）。那种上下文里
        //   没有任何外层 try 能接住异常，一旦抛出就直接冒泡到全局处理器 →
        //   弹崩溃页 + Process.killProcess，对外就是「打开直接闪退」。
        //   initializeLauncherSetting 本身已带兜底，这里主要兜 startActivity 可能抛的
        //   ActivityNotFoundException / SecurityException 之类。
        try {
            Intent intent = new Intent((Context)this, MainActivity.class);
            Bundle bundle = new Bundle();
            if (this.launcherSetting == null) {
                this.launcherSetting = InitializeSetting.initializeLauncherSetting();
            }
            bundle.putBoolean("fullscreen", this.launcherSetting != null && this.launcherSetting.fullscreen);
            intent.putExtras(bundle);
            this.startActivity(intent);
            // ★ 交给主界面了 —— 从这里往后 onDestroy 不再补记「正常结束」，
            //   让主界面自己的「启动成功」成为唯一判据（它崩了我们才能抓到）。
            this.handedOffToMain = true;
            this.finish();
        } catch (Throwable t) {
            com.qcl.launcher.launcher.StartupTrace.mark("★ 进入主界面失败（已拦截，避免整机闪退）→ " + t);
            Log.e("QCLStartup", "enterLauncher 失败", t);
            this.showStartupFailure(t);
        }
    }

    /**
     * ★★★ 社区版新增（本轮）：「上次启动没有走完」提示。
     *
     * <p>它专门覆盖一个此前完全无法观测的盲区：崩溃发生在
     * <b>{@code ContentProvider} 阶段</b>（即 {@code Application.onCreate} 之前）。
     * 那时 {@code installCrashLogger()} 还没执行 —— 崩溃页弹不出来、crash.log 是空的，
     * 用户看到的就是「打开直接闪退、什么都没留下」。
     * 这种情况下，<b>下一次启动是唯一能拿到线索的时机</b>：上一次的路标文件还在。
     *
     * <p>点「知道了」之后才继续正常流程 —— 不能先走 {@code requestPermission()}，
     * 因为缓存命中路径会在 {@code init()} 里立刻 {@code finish()} 掉本页，
     * 弹框还没来得及被看见就随 Activity 一起销毁了。
     */
    /**
     * ★★★ 社区版：「上次使用中崩溃了」的提示 + 一键分享日志。
     *
     * <p>与 {@link #showLastRunAbnormal()} 的分工：那个管「**启动**没跑完」，
     * 这个管「启动成功了、但在页面里崩了」—— 后者路标最后一行是「启动成功」，
     * 原来的提示不会出现，日志就没人去翻。这里主动弹出来，并给一个分享按钮，
     * 把「取日志」变成一次点击。
     */
    private void showRuntimeCrash(final String summary) {
        try {
            new AlertDialog.Builder((Context) this)
                    .setTitle("上次使用中崩溃了")
                    .setMessage("检测到上一次使用启动器的过程中崩溃了。\n"
                            + "（如果上次是你自己强退的，忽略这条即可。）\n\n"
                            + "【崩在哪】\n" + summary + "\n\n"
                            + "点「分享日志」可以直接把完整日志发给作者 —— 这是定位问题最快的方式。")
                    .setCancelable(false)
                    .setPositiveButton("分享日志", (d, w) -> {
                        com.qcl.launcher.launcher.CrashMarker.acknowledge(this);
                        this.shareCrashLog();
                        this.requestPermission();
                    })
                    .setNegativeButton("知道了", (d, w) -> {
                        com.qcl.launcher.launcher.CrashMarker.acknowledge(this);
                        this.requestPermission();
                    })
                    .show();
        } catch (Throwable t) {
            com.qcl.launcher.launcher.CrashMarker.acknowledge(this);
            this.requestPermission();
        }
    }

    /** 把 crash.log 通过系统分享面板发出去（复用 FilePicker 已声明的 FileProvider）。 */
    private void shareCrashLog() {
        try {
            File log = new File(getExternalFilesDir(null), "crash.log");
            if (!log.isFile()) {
                File[] medias = getExternalMediaDirs();
                if (medias != null && medias.length > 0 && medias[0] != null) {
                    File alt = new File(medias[0], "crash.log");
                    if (alt.isFile()) {
                        log = alt;
                    }
                }
            }
            if (!log.isFile()) {
                android.widget.Toast.makeText((Context) this, "没找到日志文件", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = androidx.core.content.FileProvider.getUriForFile((Context) this,
                    getPackageName() + ".filepicker.provider", log);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "QCL 崩溃日志");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            this.startActivity(Intent.createChooser(intent, "分享日志"));
        } catch (Throwable t) {
            android.widget.Toast.makeText((Context) this, "分享失败，日志在：" + com.qcl.launcher.launcher.StartupTrace.pathForUser(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void showLastRunAbnormal() {
        try {
            new AlertDialog.Builder((Context) this)
                    .setTitle("上次启动没有走完")
                    .setMessage("检测到上一次启动中途结束了。\n"
                            + "（如果上次是你自己退出的，忽略这条即可；如果是闪退，下面就是线索。）\n\n"
                            + "【上次最后停在哪一步 —— 最后一行就是断点】\n"
                            + com.qcl.launcher.launcher.StartupTrace.lastRunTail()
                            + (com.qcl.launcher.launcher.StartupTrace.lastRunStartupIncomplete()
                            ? "\n\n★ 已进入【安全模式】：本次跳过了原生库加载（bytehook / SDL3）。\n"
                            + "如果这次能正常打开，就说明崩溃发生在「加载这两个原生库」这一步 —— "
                            + "请务必把下面那份报告发给作者，这是定位的关键。"
                            : "")
                            + "\n\n★ 点「分享日志」就能把完整日志直接发给作者 —— "
                            + "不用自己去翻目录，这是定位问题最快的方式。")
                    .setCancelable(false)
                    // ★★★ 社区版：主按钮改成「分享日志」。
                    //   原来这里只有一个「知道了」，只把日志**路径**告诉用户 ——
                    //   要他自己去文件管理器翻目录。实际结果是：请了四次日志，一次都没收到。
                    //   把取日志变成**一次点击**，才真的拿得到。
                    .setPositiveButton("分享日志", (d, w) -> {
                        try {
                            this.shareCrashLog();
                        } catch (Throwable t) {
                            Log.e("QCLStartup", "分享日志失败", t);
                        }
                        try {
                            this.requestPermission();
                        } catch (Throwable t) {
                            Log.e("QCLStartup", "继续启动失败", t);
                            this.showStartupFailure(t);
                        }
                    })
                    .setNegativeButton("知道了", (d, w) -> {
                        // 这个回调已经不在 onCreate 的 try/catch 里了，自己兜一层，
                        // 否则继续启动时的异常又会变成无界面闪退。
                        try {
                            this.requestPermission();
                        } catch (Throwable t) {
                            Log.e("QCLStartup", "继续启动失败", t);
                            this.showStartupFailure(t);
                        }
                    })
                    .create().show();
        } catch (Throwable t) {
            // 提示弹框本身失败绝不能拦住启动
            Log.e("QCLStartup", "上次启动异常提示弹框失败", t);
            this.requestPermission();
        }
    }

    /**
     * ★★★ 社区版新增（本轮）：运行环境校验失败时，不再让进程被直接杀掉，
     * 而是在安装页上明确报出错误 + 路标文件路径 —— 让玩家和开发者都能拿到信息。
     * （原先这种失败会以未捕获异常的形式终结整个进程，对外就是「打开直接闪退」。）
     */
    private void showStartupFailure(Throwable t) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("运行环境校验失败")
                    .setMessage("启动前的运行环境检查出错，暂时无法进入主界面。\n\n"
                            + "【错误】\n" + t
                            // ★ 直接把路标末尾贴出来：用户截一张图就能拿到断点，
                            //   不必再去文件管理器里找 /sdcard/QCL/startup_trace.log
                            //   （那个路径对多数玩家来说门槛太高，反馈率会大打折扣）。
                            + "\n\n【启动路标：最后一行 = 崩之前最后通过的阶段】\n"
                            + com.qcl.launcher.launcher.StartupTrace.tail(14)
                            + "\n【完整文件】\n"
                            + com.qcl.launcher.launcher.StartupTrace.pathForUser())
                    .setPositiveButton("知道了", null)
                    .create().show();
        } catch (Throwable ignored) {
        }
    }

    private static String deviceArchName() {
        int a = Architecture.getRuntimeArchitecture();
        if (a == Architecture.ARCH_ARM) {
            return "arm";
        }
        if (a == Architecture.ARCH_ARM64) {
            return "arm64";
        }
        if (a == Architecture.ARCH_X86) {
            return "x86";
        }
        return "x86_64";
    }

    private static String deviceAbiDirName() {
        int a = Architecture.getRuntimeArchitecture();
        if (a == Architecture.ARCH_ARM) {
            return "armeabi-v7a";
        }
        if (a == Architecture.ARCH_ARM64) {
            return "arm64-v8a";
        }
        if (a == Architecture.ARCH_X86) {
            return "x86";
        }
        return "x86_64";
    }

    private static final class Item {
        final ImageView state;
        final ProgressBar progress;
        final TextView detail;
        boolean installed;

        Item(ImageView state, ProgressBar progress, TextView detail) {
            this.state = state;
            this.progress = progress;
            this.detail = detail;
        }
    }

    private static final class Spec {
        final String key;
        final String assetDir;
        final String targetDir;
        final boolean isJava;
        final String marker;

        Spec(String key, String assetDir, String targetDir, boolean isJava, String marker) {
            this.key = key;
            this.assetDir = assetDir;
            this.targetDir = targetDir;
            this.isJava = isJava;
            this.marker = marker;
        }
    }
}

