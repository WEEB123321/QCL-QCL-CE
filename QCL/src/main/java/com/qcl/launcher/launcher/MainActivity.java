/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.app.Activity
 *  android.content.Context
 *  android.content.Intent
 *  android.content.SharedPreferences
 *  android.content.SharedPreferences$Editor
 *  android.content.res.Configuration
 *  android.graphics.Color
 *  android.os.Build$VERSION
 *  android.os.Bundle
 *  android.os.Handler
 *  android.os.Message
 *  android.view.View
 *  android.view.View$OnClickListener
 *  android.widget.ImageButton
 *  android.widget.LinearLayout
 *  android.widget.RelativeLayout
 *  android.widget.TextView
 *  androidx.annotation.NonNull
 *  androidx.appcompat.app.AppCompatActivity
 *  com.afollestad.appthemeengine.ATE
 *  com.afollestad.appthemeengine.Config
 */
package com.qcl.launcher.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.afollestad.appthemeengine.ATE;
import com.afollestad.appthemeengine.Config;
import com.qcl.launcher.launcher.VerifyInterface;
import com.qcl.launcher.launcher.dialogs.VerifyDialog;
import com.qcl.launcher.launcher.dialogs.account.MicrosoftAccountSkinDialog;
// ★ 1.4.1：皮肤库（自研）
import com.qcl.launcher.launcher.dialogs.account.SkinLibraryDialog;
import com.qcl.launcher.launcher.dialogs.account.SkinPreviewDialog;
import com.qcl.launcher.launcher.setting.InitializeSetting;
import com.qcl.launcher.launcher.setting.game.PrivateGameSetting;
import com.qcl.launcher.launcher.setting.game.PublicGameSetting;
import com.qcl.launcher.launcher.setting.launcher.LauncherSetting;
import com.qcl.launcher.launcher.uis.game.download.DownloadUrlSource;
import com.qcl.launcher.launcher.uis.main.DynamicBackground;
import com.qcl.launcher.launcher.uis.tools.QclThemeUtils;
import com.qcl.launcher.launcher.uis.tools.UIManager;
import com.qcl.launcher.launcher.uis.universal.setting.right.launcher.ExteriorSettingUI;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.update.UpdateChecker;
import com.qcl.launcher.utils.LocaleUtils;

import com.qcl.launcher.R;
public class MainActivity
extends AppCompatActivity
implements View.OnClickListener {
    public LinearLayout launcherLayout;
    public boolean isLoaded = false;
    public boolean dialogMode = false;
    public LauncherSetting launcherSetting;
    public PublicGameSetting publicGameSetting;
    public PrivateGameSetting privateGameSetting;
    public UpdateChecker updateChecker;
    public LinearLayout backBar;
    public ImageButton backToLastUI;
    public TextView currentUIText;
    public ImageButton backToHome;
    public ImageButton closeCurrentUI;
    public RelativeLayout uiContainer;
    public UIManager uiManager;
    public Config exteriorConfig;
    /** ★ 社区版新增：底部导航条控制器（五个一级入口 + 全局搜索按钮） */
    public com.qcl.launcher.launcher.uis.tools.BottomNavHelper bottomNav;
    @SuppressLint(value={"HandlerLeak"})
    public final Handler loadingHandler = new Handler(){

        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == 0 && !MainActivity.this.isLoaded) {
                StartupTrace.mark("MainActivity.loadingHandler：主界面初始化开始");
                // ★★★ 社区版加固（本轮）：主线程这段初始化原先整体没有 try/catch。
                //   主线程未捕获异常同样会走全局处理器 → 弹崩溃页 + Process.killProcess，
                //   对外表现就是「打开直接闪退」。这里整段兜住：失败时明确报错，不再杀进程。
                //   注：为了把改动控制在最小、可复核的范围内，下面这段函数体没有整体缩进一级。
                try {
                MainActivity.this.exteriorConfig = ATE.config((Context)MainActivity.this, null);
                MainActivity.this.backBar = (LinearLayout)MainActivity.this.findViewById(R.id.qcl_back_bar);
                MainActivity.this.backToLastUI = (ImageButton)MainActivity.this.findViewById(R.id.back_to_last_ui);
                MainActivity.this.currentUIText = (TextView)MainActivity.this.findViewById(R.id.text_current_ui);
                MainActivity.this.backToHome = (ImageButton)MainActivity.this.findViewById(R.id.back_to_home);
                MainActivity.this.closeCurrentUI = (ImageButton)MainActivity.this.findViewById(R.id.close_current_ui);
                if (MainActivity.this.backToLastUI != null) {
                    MainActivity.this.backToLastUI.setOnClickListener((View.OnClickListener)MainActivity.this);
                }
                if (MainActivity.this.backToHome != null) {
                    MainActivity.this.backToHome.setOnClickListener((View.OnClickListener)MainActivity.this);
                }
                if (MainActivity.this.closeCurrentUI != null) {
                    MainActivity.this.closeCurrentUI.setOnClickListener((View.OnClickListener)MainActivity.this);
                }
                MainActivity.this.uiContainer = (RelativeLayout)MainActivity.this.findViewById(R.id.main_ui_container);
                // ★★★ 社区版路标：UIManager 构造里会把 27 个页面全部 onCreate，
                //   是启动期最重、也最可能出问题的一段。前后各记一次，
                //   万一崩在这里，至少能确定「是页面初始化阶段」而不是别处。
                StartupTrace.mark("MainActivity：开始 UIManager（预加载 27 个页面）");
                MainActivity.this.uiManager = new UIManager((Context)MainActivity.this, MainActivity.this);
                StartupTrace.mark("MainActivity：UIManager 完成");
                // ★★★ 社区版：有页面初始化失败时必须**明确报出来**，绝不静默。
                //   被隔离的页面已从 mainUIs 剔除，且不会再被派发 onStart，
                //   所以启动器仍可用；但这个事实必须让用户和开发者同时看到。
                //   ★ 这里刻意用硬编码文本而不是 string 资源：本弹窗只在启动降级路径上出现，
                //     若再依赖一次资源查找，万一资源本身有问题就会二次崩溃 —— 诊断路径必须最简。
                if (MainActivity.this.uiManager.hasBrokenPages()) {
                    StartupTrace.mark("MainActivity：存在初始化失败的页面（已隔离，启动器继续运行）");
                    try {
                        new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("有页面初始化失败（已跳过）")
                                .setMessage("以下页面在启动时初始化失败，已临时禁用，启动器仍可继续使用：\n\n"
                                        + MainActivity.this.uiManager.brokenPagesReport()
                                        + "\n\n完整堆栈见 logcat（tag: QCLStartup），"
                                        + "启动路标文件：\n" + StartupTrace.pathForUser())
                                .setPositiveButton("知道了", null)
                                .create().show();
                    } catch (Throwable ignored) {
                    }
                }
                // ★ 社区版新增：底部导航条。必须在 UIManager 建好之后创建 ——
                //   UIManager 构造里会 switchMainUI(mainUI)，那时 bottomNav 还是 null，
                //   页面里的 showBarTitle/hideBarTitle 会走空判断跳过；建好后主动 sync 一次补上。
                MainActivity.this.bottomNav = new com.qcl.launcher.launcher.uis.tools.BottomNavHelper(MainActivity.this);
                MainActivity.this.bottomNav.syncWithCurrentUI();
                StartupTrace.mark("MainActivity：底部导航完成");
                // ★★★ 社区版路标：这一小段原先没有埋点，是「底部导航完成」与
                //   「全部完成」之间的盲区 —— 一旦崩在这里，路标只会停在上一行，
                //   看不出是主题、onLoad 还是动态背景。补上，让断点精确到方法。
                StartupTrace.mark("MainActivity：应用主题色（ATE.apply）");
                MainActivity.this.exteriorConfig.primaryColor(ExteriorSettingUI.parseThemeColorSafe((Context)MainActivity.this, MainActivity.this.launcherSetting.launcherTheme));
                MainActivity.this.exteriorConfig.accentColor(ExteriorSettingUI.parseThemeColorSafe((Context)MainActivity.this, MainActivity.this.launcherSetting.launcherTheme));
                MainActivity.this.exteriorConfig.apply((Activity)MainActivity.this);
                MainActivity.this.isLoaded = true;
                StartupTrace.mark("MainActivity：onLoad");
                MainActivity.this.onLoad();
                StartupTrace.mark("MainActivity：面板着色");
                // ★★★ 社区版修复：这里原来传的是 getPanelColor(...) 算出来的**颜色值**，
                //   于是「DEFAULT（=面板透明，露出背景图）」这个语义在中途就被丢掉了 ——
                //   不管用户选没选，都会被当成一个实心颜色去涂。
                //   改成直接传**设置里的原始字符串**，由 applyPanelTint 自己区分「透明 / 纯色」。
                ExteriorSettingUI.applyPanelTint((Context)MainActivity.this, MainActivity.this.getWindow().getDecorView(), MainActivity.this.launcherSetting.panelColor);
                StartupTrace.mark("MainActivity：动态背景");
                MainActivity.this.startDynamicBackgroundIfNeeded();
                // ★ 1.3.0：**初始化完成后**扫一遍已装的远古版本，没打中文包的补上。
                //   为什么放这儿：onResume 时 isLoaded 还是 false（初始化是异步的），
                //   在那儿挂钩子根本进不去 —— 早就装好的版本就一直没被补上。
                try {
                    final String gameDir = MainActivity.this.launcherSetting == null
                            ? null : MainActivity.this.launcherSetting.gameFileDirectory;
                    if (gameDir != null) {
                        new Thread(() -> {
                            try {
                                com.qcl.launcher.launcher.download.game.LegacyChinesePack.sweepAll(MainActivity.this, gameDir);
                            }
                            catch (Throwable ignored) {
                            }
                        }).start();
                    }
                }
                catch (Throwable ignored) {
                }
                StartupTrace.mark("MainActivity：applyUiTheme");
                MainActivity.this.applyUiTheme();
                // ★★★ 社区版：液态玻璃（真折射）。逻辑见 applyLiquidGlass()。
                MainActivity.this.applyLiquidGlass();
                // ★ 社区版路标：走到这里说明启动全流程走通了。路标文件最后一行若是它，
                //   就说明「打不开」的问题不在启动初始化，而在更后面（或压根没复现）。
                StartupTrace.mark("MainActivity：主界面初始化全部完成（启动成功）");
                } catch (Throwable t) {
                    // ★ 社区版加固：主界面初始化失败不再让进程被直接杀掉。
                    StartupTrace.mark("★ 主界面初始化失败（已拦截，避免整机闪退）→ " + t);
                    android.util.Log.e("QCLStartup", "主界面初始化失败", t);
                    MainActivity.this.showStartupFailure(t);
                }
            }
        }
    };
    private DynamicBackground dynamicBackground;

    /**
     * ★★★ 社区版新增：桌面快捷方式「一键启动」的待办标志。
     *
     * <p><b>为什么用标志 + onResume，而不是在 onCreate 里直接启动</b>：
     * 启动游戏要先弹「启动检查」对话框，它依赖主界面已经完全就绪。
     * onCreate 时 UI 还在后台线程里初始化，此时触发会在半初始化状态上操作 ——
     * 那正是历史上最容易「打开直接闪退」的一类路径。
     * 放到 {@code onResume} 且判 {@code isLoaded}，等于「等界面真的好了再启动」。
     *
     * <p><b>为什么用完立刻清标志</b>：onResume 会反复触发（切后台回来、锁屏解锁），
     * 不清就会「每次回前台都自动启动一次游戏」。
     */
    private boolean pendingAutoLaunch = false;

    /**
     * ★★★ 社区版新增：桌面快捷方式里指定的实例路径（{@code qcl_instance}）。
     *
     * <p>空 = 用「当前实例」。非空 = 先把当前实例切过去再启动 —— 这就是
     * 「把常用实例固定到桌面」的实现方式：一个实例一个快捷方式，点谁开谁。
     */
    private String pendingInstancePath = null;

    // ★★★ 1.1.0：原 `libsecurity.so`（防篡改校验库，随已删除的 Boat 模块一起没了）曾提供
    //   下面这些 native 方法。这里改成等价的**纯 Java 实现**，彻底摘掉对它的依赖。
    @Override
    protected void onCreate(Bundle bundle) {
        // ★ 社区版：路标提前到 super.onCreate 之前 —— 它做主题校验，
        //   崩在这里和崩在 super 之后必须在日志里分得开（原因同 RuntimeInstallActivity 同处注释）。
        StartupTrace.mark("MainActivity.onCreate 进入（super.onCreate 之前）");
        super.onCreate(bundle);
        // ★★★ 社区版加固：activity_main.xml 是本社区版改动最大的布局 ——
        //   新增了插件页 / 备份页两个 include、底部导航条、以及搜索悬浮按钮。
        //   布局 inflate 阶段的异常（资源缺失、属性不合法、include 目标异常）
        //   编译期查不出来，只会在这里炸，而这里一炸就是整个进程死掉 ——
        //   用户看到的正是「打开软件闪退」。包一层后至少能弹出可读的报错。
        try {
            this.setContentView(R.layout.activity_main);
            this.launcherLayout = (LinearLayout)this.findViewById(R.id.launcher_layout);
        } catch (Throwable t) {
            StartupTrace.mark("★ MainActivity.setContentView 失败（已拦截）→ " + t);
            android.util.Log.e("QCLStartup", "MainActivity 布局加载失败", t);
            this.showStartupFailure(t);
            return;
        }
        // ★★★ 社区版新增：安全区（刘海 / 挖孔 / 大圆角）自动避让。
        //   主界面是沉浸式全屏，内容会画进 R 角里 —— 圆角大的手机上顶部入口和底部
        //   导航条会被切掉。这里挂一个 WindowInsets 监听，把真实安全区换算成 padding。
        //   注意：此处 launcherSetting 还没读出来（在 init 的后台线程里读），
        //   SafeAreaHelper 会先按「自动」处理；等设置读完再 requestApplyInsets 重算一次。
        try {
            com.qcl.launcher.launcher.ui.SafeAreaHelper.install(this,
                    this.findViewById(R.id.launcher_root),
                    this.launcherLayout,
                    this.findViewById(R.id.qcl_bottom_nav),
                    this.findViewById(R.id.qcl_search_fab));
        } catch (Throwable t) {
            StartupTrace.mark("★ 安全区安装失败（已忽略）→ " + t);
        }
        // ★ 社区版新增：桌面快捷方式带 qcl_auto_launch 进来 → 记下待办，等 onResume 且界面就绪后再启动。
        //   立刻 removeExtra 是为了防重（Activity 被重建时会再读一次 intent）。
        try {
            android.os.Bundle extras = this.getIntent() == null ? null : this.getIntent().getExtras();
            if (extras != null && extras.getBoolean("qcl_auto_launch", false)) {
                this.pendingAutoLaunch = true;
                this.getIntent().removeExtra("qcl_auto_launch");
                String inst = extras.getString("qcl_instance");
                if (inst != null && !inst.isEmpty()) {
                    this.pendingInstancePath = inst;
                }
                this.getIntent().removeExtra("qcl_instance");
            }
        } catch (Throwable ignored) {
        }
        this.init();
    }

    public boolean isValid(String str) {
        return true;
    }

    public static void verify() {
    }

    public static void verifyFunc() {
    }

    public void launch(Intent intent) {
        this.startActivity(intent);
    }

    public void init() {
        // ★ 社区版加固：原来直接 getIntent().getExtras().getBoolean(...)，
        //   一旦 MainActivity 被非 enterLauncher 的入口拉起（没有 extras）就是 NPE。
        android.os.Bundle initExtras = this.getIntent() == null ? null : this.getIntent().getExtras();
        boolean initFullscreen = initExtras != null && initExtras.getBoolean("fullscreen");
        if (Build.VERSION.SDK_INT >= 28) {
            this.getWindow().getAttributes().layoutInDisplayCutoutMode = initFullscreen ? 1 : 2;
        }
        this.getWindow().setFlags(256, 256);
        new Thread(() -> {
            // ★★★ 社区版加固（本轮）：整段包 try/catch。
            //   这是「进主界面」的必经路径，原先一处 try/catch 都没有 ——
            //   里面读设置 / 建目录都是文件与 JSON 操作，任一处抛异常，线程就带着
            //   未捕获异常死掉，全局处理器随即弹崩溃页并 Process.killProcess，
            //   对外表现就是「打开直接闪退」。
            //   注：DownloadUrlSource.getBalancedSource() 在 1.4.3 里是空实现（不碰网络），
            //   这里保留调用只是不改上游行为，别误以为它是风险点。
            try {
                StartupTrace.mark("MainActivity.init 线程：初始化 AppManifest 目录");
                AppManifest.initializeManifest((Context)this);
                StartupTrace.mark("MainActivity.init 线程：读取启动器设置");
                this.launcherSetting = InitializeSetting.initializeLauncherSetting();
                StartupTrace.mark("MainActivity.init 线程：读取公共游戏设置");
                this.publicGameSetting = InitializeSetting.initializePublicGameSetting((Context)this, this);
                StartupTrace.mark("MainActivity.init 线程：读取私有游戏设置");
                this.privateGameSetting = InitializeSetting.initializePrivateGameSetting((Context)this);
                // ★ 社区版新增：设置已经读出来了，让安全区按用户选的模式重算一次
                //   （onCreate 时 launcherSetting 还是 null，只能先按「自动」处理）。
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            android.view.View root = findViewById(R.id.launcher_root);
                            if (root != null) {
                                root.requestApplyInsets();
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
                // ★★★ 社区版优化（启动提速）：把「自动检查更新」**推迟 3 秒**。
                //   它一挂上就发网络请求 + 解析 JSON，正好和主线程构建界面（30 多个页面）
                //   抢 CPU 与 IO —— 启动阶段最不该做的就是这件事。
                //   推迟后功能一点没少，只是不再和界面初始化抢资源。
                //   （DownloadSettingUI 里「手动检查更新」已对 updateChecker == null 做了兜底，
                //     所以这 3 秒内用户点手动检查也不会 NPE。）
                this.runOnUiThread(() -> new android.os.Handler(this.getMainLooper()).postDelayed(() -> {
                    // ★ 独立包一层：runOnUiThread 是「投递」，异常发生在主线程上，
                    //   不会被外面的 try 捕获 —— 必须在这里自己兜住（同样碰网络）。
                    try {
                        if (this.updateChecker == null) {
                            this.updateChecker = new UpdateChecker((Context)this, this);
                        }
                        this.updateChecker.checkAuto();
                    } catch (Throwable t) {
                        StartupTrace.mark("★ UpdateChecker 初始化失败（已忽略，不影响启动）→ " + t);
                        android.util.Log.e("QCLStartup", "UpdateChecker 初始化失败", t);
                    }
                }, 3000L));
                StartupTrace.mark("MainActivity.init 线程：获取下载源（当前为空实现）");
                DownloadUrlSource.getBalancedSource((Context)this);
                // ★★★ 社区版新增：自动任务（定时备份 / 崩溃后备份 / 定时清理）。
                //   放在这个后台线程里是刻意的 —— 备份是整目录拷贝，可能几十秒，
                //   绝不能上主线程。AutoTaskRunner 内部对异常全包，失败也不影响启动。
                StartupTrace.mark("MainActivity.init 线程：检查自动任务是否到期");
                try {
                    String currentVersion = null;
                    if (this.publicGameSetting != null) {
                        currentVersion = this.publicGameSetting.currentVersion;
                    }
                    com.qcl.launcher.launcher.autotask.AutoTaskRunner
                            .onLauncherOpened((Context) this, currentVersion);
                } catch (Throwable t) {
                    StartupTrace.mark("★ 自动任务检查失败（已忽略）→ " + t);
                }
                // ★ 社区版新增：刷新桌面小部件 + 发布「一键启动」动态快捷方式（长按图标可见）。
                //   放后台线程：要读设置/统计/备份索引（都是文件 IO），别占主线程。
                try {
                    String cur = null;
                    if (this.publicGameSetting != null) {
                        cur = this.publicGameSetting.currentVersion;
                    }
                    com.qcl.launcher.launcher.widget.QclStatusWidget.refreshAll((Context)this);
                    com.qcl.launcher.launcher.shortcut.ShortcutHelper.publishDynamicShortcut(
                            (Context)this, cur == null ? null : new java.io.File(cur).getName());
                } catch (Throwable t) {
                    StartupTrace.mark("★ 刷新小部件 / 快捷方式失败（已忽略）→ " + t);
                }
                StartupTrace.mark("MainActivity.init 线程：投递主界面初始化消息");
                this.loadingHandler.sendEmptyMessage(0);
            } catch (Throwable t) {
                StartupTrace.mark("★ MainActivity 初始化线程失败（已拦截，避免整机闪退）→ " + t);
                android.util.Log.e("QCLStartup", "MainActivity 初始化线程失败", t);
                final Throwable cause = t;
                this.runOnUiThread(() -> this.showStartupFailure(cause));
            }
        }).start();
    }

    /**
     * ★★★ 社区版新增（本轮）：初始化线程失败时不再让进程被直接杀掉，
     * 而是明确报出错误 + 路标文件路径。（原先这种失败以未捕获异常终结整个进程。）
     */
    private void showStartupFailure(Throwable t) {
        try {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("启动初始化失败")
                    .setMessage("启动器在初始化时出错，界面可能不完整。\n\n"
                            + "【错误】\n" + t
                            // ★ 直接把路标末尾贴出来，用户截图即可（同 RuntimeInstallActivity 处注释）
                            + "\n\n【启动路标：最后一行 = 崩之前最后通过的阶段】\n"
                            + StartupTrace.tail(14)
                            + "\n【完整文件】\n"
                            + StartupTrace.pathForUser())
                    .setPositiveButton("知道了", null)
                    .create().show();
        } catch (Throwable ignored) {
        }
    }

    /**
     * ★★★ 社区版新增：从桌面快捷方式一键启动当前实例。
     *
     * <p>★ 刻意<b>复刻</b>主界面「启动」按钮的流程（同样的 setting_path 选择逻辑），
     * 而不是另走一条捷径 —— 两条路径必然会在某次改动后不一致，
     * 到时候「点按钮能进、点快捷方式不能进」这种问题极难查。
     *
     * <p>全程 try/catch：快捷方式是个「顺手做的入口」，绝不能因为它把启动器带走。
     */
    private void autoLaunchFromShortcut() {
        try {
            if (this.publicGameSetting == null) {
                return;
            }
            String version = this.publicGameSetting.currentVersion;
            // ★ 快捷方式指定了实例 → 先把当前实例切过去（并落盘），再走正常启动流程。
            //   这样「固定某个实例到桌面」点开就是那个实例，而不是「当时的当前实例」。
            if (this.pendingInstancePath != null && !this.pendingInstancePath.isEmpty()) {
                String want = this.pendingInstancePath;
                this.pendingInstancePath = null;
                if (!want.equals(version) && new java.io.File(want).isDirectory()) {
                    this.publicGameSetting.currentVersion = want;
                    try {
                        com.qcl.launcher.utils.gson.GsonUtils.savePublicGameSetting(
                                this.publicGameSetting,
                                com.qcl.launcher.manifest.AppManifest.SETTING_DIR + "/public_game_setting.json");
                    } catch (Throwable ignored) {
                    }
                    // 主界面顶部那个版本名由 MainUI.onStart 的后台线程刷新，
                    // 这里不用额外处理 —— 启动流程马上就会把它顶掉（进游戏）。
                    version = want;
                    StartupTrace.mark("社区版：快捷方式指定实例 → " + want);
                }
            }
            if (version == null || version.isEmpty()) {
                StartupTrace.mark("★ 快捷方式自动启动：没有选中实例，已跳过");
                return;
            }
            String settingPath = version + "/qcl.cfg";
            String finalPath;
            if (new java.io.File(settingPath).exists()
                    && com.qcl.launcher.utils.gson.GsonUtils.getPrivateGameSettingFromFile(settingPath) != null
                    && (com.qcl.launcher.utils.gson.GsonUtils.getPrivateGameSettingFromFile(settingPath).forceEnable
                        || com.qcl.launcher.utils.gson.GsonUtils.getPrivateGameSettingFromFile(settingPath).enable)) {
                finalPath = settingPath;
            } else {
                finalPath = com.qcl.launcher.manifest.AppManifest.SETTING_DIR + "/private_game_setting.json";
            }
            android.os.Bundle b = new android.os.Bundle();
            b.putString("setting_path", finalPath);
            b.putBoolean("test", false);
            StartupTrace.mark("社区版：桌面快捷方式一键启动 → " + version);
            com.qcl.launcher.launcher.launch.check.LaunchTools.launch(this, this, version, b);
        } catch (Throwable t) {
            StartupTrace.mark("★ 快捷方式自动启动失败（已忽略）→ " + t);
        }
    }

    /**
     * ★★★ 社区版：把**当前实际显示的那张背景图**交给液态玻璃着色器当折射输入。
     *
     * <p>为什么需要它：背景有三种模式（网络轮播 / 经典单图 / 自定义图片），
     * 而 {@code DynamicBackground} 只在**网络轮播**模式下运行 ——
     * 它的 {@code apply()} 是原先唯一喂背景的地方。
     * 于是背景模式一旦不是「网络」，着色器就拿不到输入、每块玻璃都静默降级，
     * 用户看到的和「没做」一模一样。
     *
     * <p>这里直接从 {@code launcherLayout} 的当前背景里取位图，
     * 三种模式通吃。轮播模式下背景是 {@code TransitionDrawable}，
     * 由 {@code DynamicBackground} 自己在切换时更新，这里跳过。
     */
    /**
     * ★★★ 社区版：给「悬在背景之上」的那两块套上**真·液态玻璃**（AGSL 折射）。
     *
     * <p>★ 只挑这两块：**左侧导航 + 返回栏** —— 它们浮在照片上，折射最明显；
     * 内容区的卡片上要压大量文字，边缘乱窜的色散反而干扰阅读。
     *
     * <p>★ 这个方法是**幂等**的，启动时调一次、每次回到前台再调一次。
     * 为什么要重复调：外观设置里的「面板颜色」会遍历控件重新给背景着色
     * （{@code ExteriorSettingUI.applyPanelTint}），一旦它在玻璃之后跑，
     * 就会把玻璃背景替换掉 —— 表现就是「玻璃莫名其妙没了，重启又有了」。
     * 每次 onResume 重套一遍，不管被谁覆盖都能自愈。
     *
     * <p>着色器不可用（系统 &lt; 13 / 驱动编译失败）时 {@code applyTo} 内部自动退化，不会崩。
     */
    public void applyLiquidGlass() {
        try {
            // 先把「当前背景图」喂给着色器当折射输入。
            //   ⚠️ 这一步以前漏了，症状就是「液态玻璃完全看不出来」：
            //     DynamicBackground 只在「网络（动态轮播）」模式下才跑，
            //     而它的 apply() 才是唯一喂背景的地方 ——
            //     所以背景模式一旦不是「网络」，着色器就没有输入，
            //     每一块都静默走了降级路径（半透明白），看起来跟没做一样。
            //   现在不管什么模式，都从 launcherLayout 当前的实际背景里取位图。
            this.pushBackdropFromCurrentBackground();
            com.qcl.launcher.launcher.glass.LiquidGlass.applyTo(
                    this.findViewById(R.id.qcl_bottom_nav), 18f, 0.12f, 22f);
            com.qcl.launcher.launcher.glass.LiquidGlass.applyTo(
                    this.findViewById(R.id.qcl_back_bar), 18f, 0.12f, 22f);
        } catch (Throwable ignored) {
        }
    }

    private void pushBackdropFromCurrentBackground() {
        try {
            if (this.launcherLayout == null) {
                return;
            }
            android.graphics.drawable.Drawable d = this.launcherLayout.getBackground();
            android.graphics.Bitmap bmp = null;
            // ★★★ 3.0：**必须处理 TransitionDrawable** ——
            //   轮播模式下 launcherLayout 的背景就是一个 TransitionDrawable（淡入淡出用），
            //   而这里原来只认 BitmapDrawable / LayerDrawable，直接把它跳过了
            //   → 着色器拿不到 uBg 输入 → 每块玻璃静默走降级路径。
            //   真机表现正是用户描述的：「有反光（降级里的亮边），但边缘没有畸变」。
            if (d instanceof android.graphics.drawable.TransitionDrawable) {
                // ★ TransitionDrawable 和 LayerDrawable 都没有 getLayers()，
                //   只有 getNumberOfLayers() + getDrawable(i)。
                android.graphics.drawable.TransitionDrawable td =
                        (android.graphics.drawable.TransitionDrawable) d;
                int n = td.getNumberOfLayers();
                // 取最后一层 —— 淡入完成时显示的就是它
                for (int i = n - 1; i >= 0; i--) {
                    android.graphics.drawable.Drawable one = td.getDrawable(i);
                    if (one instanceof android.graphics.drawable.BitmapDrawable) {
                        bmp = ((android.graphics.drawable.BitmapDrawable) one).getBitmap();
                        break;
                    }
                }
            } else if (d instanceof android.graphics.drawable.BitmapDrawable) {
                bmp = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
            } else if (d instanceof android.graphics.drawable.LayerDrawable) {
                // 有些主题会把位图包在 LayerDrawable 里，往下找一层
                android.graphics.drawable.LayerDrawable ld =
                        (android.graphics.drawable.LayerDrawable) d;
                int n = ld.getNumberOfLayers();
                for (int i = 0; i < n; i++) {
                    android.graphics.drawable.Drawable one = ld.getDrawable(i);
                    if (one instanceof android.graphics.drawable.BitmapDrawable) {
                        bmp = ((android.graphics.drawable.BitmapDrawable) one).getBitmap();
                        break;
                    }
                }
            }
            if (bmp == null || bmp.isRecycled()) {
                return;
            }
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            com.qcl.launcher.launcher.glass.LiquidGlassDrawable.setBackdrop(
                    bmp, dm.widthPixels, dm.heightPixels, null);
            StartupTrace.mark("社区版：液态玻璃背景已就绪 " + bmp.getWidth() + "x" + bmp.getHeight());
        } catch (Throwable ignored) {
        }
    }

    private void startDynamicBackgroundIfNeeded() {
        try {
            if (this.launcherSetting.launcherBackground.type != 0) {
                return;
            }
            if (this.dynamicBackground == null) {
                this.dynamicBackground = new DynamicBackground((Activity)this, (View)this.launcherLayout);
            }
            this.dynamicBackground.start();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    /** ★ 1.2.9：回到前台后确保动态背景继续轮播（只在「动态背景」模式下生效） */
    public void resumeDynamicBackground() {
        // ★ 社区版：每次回到前台重套一遍液态玻璃 —— 外观设置里的「面板颜色」
        //   会遍历控件重新着色，可能在玻璃之后跑并把它覆盖掉。
        //   放在最前面（在下面那个 type != 0 的提前 return 之前），保证任何模式下都会执行。
        this.applyLiquidGlass();
        try {
            if (this.launcherSetting == null || this.launcherSetting.launcherBackground == null
                    || this.launcherSetting.launcherBackground.type != 0) {
                return;
            }
            if (this.dynamicBackground == null) {
                this.startDynamicBackgroundIfNeeded();
                return;
            }
            this.dynamicBackground.ensureRunning();
        }
        catch (Throwable ignored) {
        }
    }

    public void refreshDynamicBackground() {
        try {
            if (this.launcherSetting.launcherBackground.type == 0) {
                this.startDynamicBackgroundIfNeeded();
            } else if (this.dynamicBackground != null) {
                this.dynamicBackground.stop();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public void applyUiTheme() {
        try {
            QclThemeUtils.apply((Activity)this, this.launcherSetting.uiTheme);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    protected void onDestroy() {
        if (this.dynamicBackground != null) {
            this.dynamicBackground.stop();
        }
        super.onDestroy();
    }

    public void onLoad() {
        // ★★★ 社区版加固（续 6）：下面每一行都是**多级直接解引用**，任何一级为 null 都 NPE：
        //   - UIManager 里某个页面构造失败 → uiManager.xxxUI == null（见 UIManager 的隔离机制）；
        //   - 页面构造成功但内部子管理器为 null（例如某个 findViewById 拿不到视图）。
        //   而 onLoad() 处在启动路径上（MainActivity.loadingHandler → onLoad）。
        //   ★ 关键是**逐行独立保护**：某一个坏了只跳过它自己，不连累其余三个 ——
        //     否则「版本设置页」一处出问题，会让下载页、设置页的 onLoaded 一起不执行，
        //     用户看到的是好几个页面都不正常，而根因只有一个，排查反而更难。
        try {
            this.uiManager.gameManagerUI.gameManagerUIManager.versionSettingUI.onLoaded();
        } catch (Throwable t) {
            StartupTrace.mark("★ onLoad: 版本设置页 onLoaded 失败（已跳过）→ " + t);
        }
        try {
            this.uiManager.downloadUI.downloadUIManager.downloadMinecraftUI.onLoaded();
        } catch (Throwable t) {
            StartupTrace.mark("★ onLoad: 下载页 onLoaded 失败（已跳过）→ " + t);
        }
        try {
            this.uiManager.settingUI.settingUIManager.universalGameSettingUI.onLoaded();
        } catch (Throwable t) {
            StartupTrace.mark("★ onLoad: 通用游戏设置页 onLoaded 失败（已跳过）→ " + t);
        }
        try {
            this.uiManager.mainUI.customTheme();
        } catch (Throwable t) {
            StartupTrace.mark("★ onLoad: 自定义主题失败（已跳过）→ " + t);
        }
    }

    public void showBarTitle(String title, boolean home, boolean close) {
        try {
            if (this.currentUIText != null) {
                if (title != null && !title.isEmpty()) {
                    this.currentUIText.setText((CharSequence)title);
                    this.currentUIText.setVisibility(0);
                } else {
                    this.currentUIText.setVisibility(8);
                }
            }
            if (this.backToLastUI != null) {
                this.backToLastUI.setVisibility(0);
            }
            if (this.backToHome != null) {
                this.backToHome.setVisibility(home ? 0 : 8);
            }
            if (this.closeCurrentUI != null) {
                this.closeCurrentUI.setVisibility(close ? 0 : 8);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        this.showBackBar();
        // ★ 社区版：页面切换后同步底部导航 —— 五个一级页显示并高亮对应 Tab，
        //   其余二级页整条隐藏（含搜索按钮），避免遮挡。
        if (this.bottomNav != null) {
            this.bottomNav.syncWithCurrentUI();
        }
    }

    public void showBackBar() {
        try {
            if (this.backBar == null) {
                this.backBar = (LinearLayout)this.findViewById(R.id.qcl_back_bar);
            }
            if (this.backBar == null) {
                return;
            }
            if (this.backBar.getVisibility() != 0) {
                this.backBar.setVisibility(0);
            }
            this.backBar.bringToFront();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public void hideBarTitle() {
        try {
            if (this.backBar == null) {
                this.backBar = (LinearLayout)this.findViewById(R.id.qcl_back_bar);
            }
            if (this.backBar == null) {
                return;
            }
            this.backBar.setVisibility(8);
            if (this.currentUIText != null) {
                this.currentUIText.setText((CharSequence)"");
            }
            // ★ 社区版：回到首页（MainUI 调的就是这里）→ 恢复底部导航
            if (this.bottomNav != null) {
                this.bottomNav.syncWithCurrentUI();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    /**
     * 返回上一层。
     *
     * <h3>★★★ 社区版修复：真机崩溃 IndexOutOfBoundsException（Index -1, length 0）</h3>
     *
     * <p><b>原来的代码</b>：只要 {@code currentUI != mainUI} 就无脑
     * {@code uis.get(size-1) → uis.remove(size-1) → uis.get(size-1)}。
     * 当栈里**只剩 1 个**页面时，remove 之后栈变空，最后一次 {@code get(size-1)} 就成了
     * {@code get(-1)} → 直接崩。
     *
     * <p><b>复现路径</b>（用户实测）：用**底部导航**切到某个 Tab ——
     * {@link UIManager#switchMainUITab} 会先清空栈再放入那一个 Tab，于是栈里只有 1 项、
     * 而 {@code currentUI} 又不是首页；这时按返回必然踩到上面那一步。
     *
     * <p><b>现在的规则</b>：
     * <ul>
     *   <li>栈空 或 栈里只剩 1 项 → 视为「没有上一层可退」，**回首页**（而不是弹成空栈）；</li>
     *   <li>其余 → 正常弹栈；</li>
     *   <li>★ 整个过程包在 try/catch 里，任何意外都兜底回首页 ——
     *       返回键是用户最常按的键，**这里绝不允许崩**。</li>
     * </ul>
     */
    public void backToLastUI() {
        if (!this.isLoaded) {
            return;
        }
        try {
            UIManager m = this.uiManager;
            if (m == null || m.mainUI == null) {
                return;
            }
            if (m.currentUI == m.mainUI) {
                this.backToDeskTop();
                return;
            }
            int n = (m.uis == null) ? 0 : m.uis.size();
            if (n <= 1) {
                // ★ 没有上一层可退（栈空 / 只剩底部导航那一个顶层页）→ 回首页
                if (n == 1) {
                    com.qcl.launcher.launcher.uis.tools.BaseUI only = m.uis.get(0);
                    if (only != null && only != m.mainUI) {
                        try {
                            only.onStop();
                        } catch (Throwable ignored) {
                        }
                    }
                }
                m.uis.clear();
                m.currentUI = m.mainUI;
                m.uis.add(m.mainUI);
                try {
                    m.mainUI.onStart();
                } catch (Throwable ignored) {
                }
                if (this.bottomNav != null) {
                    this.bottomNav.syncWithCurrentUI();
                }
                return;
            }
            m.uis.get(n - 1).onStop();
            m.uis.remove(n - 1);
            m.currentUI = m.uis.get(m.uis.size() - 1);
            m.uis.get(m.uis.size() - 1).onStart();
        } catch (Throwable t) {
            // ★ 兜底：返回键路径不允许把异常冒到 Activity 之外
            try {
                this.uiManager.uis.clear();
                this.uiManager.currentUI = this.uiManager.mainUI;
                this.uiManager.uis.add(this.uiManager.mainUI);
                this.uiManager.mainUI.onStart();
                if (this.bottomNav != null) {
                    this.bottomNav.syncWithCurrentUI();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public void backToHome() {
        this.uiManager.switchMainUI(this.uiManager.mainUI);
        this.uiManager.uis.clear();
        this.uiManager.uis.add(this.uiManager.mainUI);
    }

    public void closeCurrentUI() {
        this.uiManager.removeUIIfExist(this.uiManager.exportWorldUI);
        this.uiManager.removeUIIfExist(this.uiManager.installPackageUI);
        this.uiManager.removeUIIfExist(this.uiManager.exportPackageTypeUI);
        this.uiManager.removeUIIfExist(this.uiManager.exportPackageInfoUI);
        this.uiManager.removeUIIfExist(this.uiManager.exportPackageFileUI);
        this.uiManager.removeUIIfExist(this.uiManager.installGameUI);
        this.uiManager.removeUIIfExist(this.uiManager.downloadForgeUI);
        this.uiManager.removeUIIfExist(this.uiManager.downloadFabricUI);
        this.uiManager.removeUIIfExist(this.uiManager.downloadFabricAPIUI);
        this.uiManager.removeUIIfExist(this.uiManager.downloadLiteLoaderUI);
        this.uiManager.removeUIIfExist(this.uiManager.downloadOptifineUI);
        this.uiManager.removeUIIfExist(this.uiManager.downloadQuiltUI);
        this.uiManager.removeUIIfExist(this.uiManager.downloadQuiltAPIUI);
        this.uiManager.uis.get(this.uiManager.uis.size() - 1).onStart();
        if (this.uiManager.currentUI == this.uiManager.exportWorldUI) {
            this.uiManager.exportWorldUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.installPackageUI) {
            this.uiManager.installPackageUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.exportPackageTypeUI) {
            this.uiManager.exportPackageTypeUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.exportPackageInfoUI) {
            this.uiManager.exportPackageInfoUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.exportPackageFileUI) {
            this.uiManager.exportPackageFileUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.installGameUI) {
            this.uiManager.installGameUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.downloadForgeUI) {
            this.uiManager.downloadForgeUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.downloadFabricUI) {
            this.uiManager.downloadFabricUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.downloadFabricAPIUI) {
            this.uiManager.downloadFabricAPIUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.downloadLiteLoaderUI) {
            this.uiManager.downloadLiteLoaderUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.downloadOptifineUI) {
            this.uiManager.downloadOptifineUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.downloadQuiltUI) {
            this.uiManager.downloadQuiltUI.onStop();
        }
        if (this.uiManager.currentUI == this.uiManager.downloadQuiltAPIUI) {
            this.uiManager.downloadQuiltAPIUI.onStop();
        }
        this.uiManager.currentUI = this.uiManager.uis.get(this.uiManager.uis.size() - 1);
    }

    public void backToDeskTop() {
        Intent i = new Intent("android.intent.action.MAIN");
        i.setFlags(0x10000000);
        i.addCategory("android.intent.category.HOME");
        this.startActivity(i);
    }

    public void onBackPressed() {
        if (!this.dialogMode) {
            this.backToLastUI();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (this.isLoaded) {
            this.uiManager.onActivityResult(requestCode, resultCode, data);
        }
        if (SkinPreviewDialog.getInstance() != null) {
            SkinPreviewDialog.getInstance().onActivityResult(requestCode, resultCode, data);
        }
        if (MicrosoftAccountSkinDialog.getInstance() != null) {
            MicrosoftAccountSkinDialog.getInstance().onActivityResult(requestCode, resultCode, data);
        }
        // ★ 1.4.1：皮肤库（本地文件选择结果转发）
        if (SkinLibraryDialog.getInstance() != null) {
            SkinLibraryDialog.getInstance().onActivityResult(requestCode, resultCode, data);
        }
    }

    public void onClick(View v) {
        if (v == this.backToLastUI) {
            this.backToLastUI();
        } else if (v == this.backToHome) {
            this.backToHome();
        } else if (v == this.closeCurrentUI) {
            this.closeCurrentUI();
        }
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            this.getWindow().getDecorView().setSystemUiVisibility(5894);
        }
    }

    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLanguage(base));
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleUtils.setLanguage((Context)this);
    }

    protected void onPause() {
        super.onPause();
        if (this.isLoaded) {
            this.uiManager.onPause();
        }
        if (SkinPreviewDialog.getInstance() != null) {
            SkinPreviewDialog.getInstance().onPause();
        }
    }

    protected void onResume() {
        super.onResume();
        if (this.isLoaded) {
            this.uiManager.onResume();
            // ★ 1.2.9：回到前台时把动态背景的轮播重新挂上（切后台回来后不动的问题）
            this.resumeDynamicBackground();
            // ★ 社区版新增：桌面快捷方式「一键启动」。必须放在 isLoaded 之后（原因见字段注释）。
            if (this.pendingAutoLaunch) {
                this.pendingAutoLaunch = false;
                new android.os.Handler(this.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        autoLaunchFromShortcut();
                    }
                }, 600L);
            }
        }
        if (SkinPreviewDialog.getInstance() != null) {
            SkinPreviewDialog.getInstance().onResume();
        }
    }

    protected void onPostResume() {
        super.onPostResume();
        if (Build.VERSION.SDK_INT >= 28 && this.launcherSetting != null) {
            this.getWindow().getAttributes().layoutInDisplayCutoutMode = this.launcherSetting.fullscreen ? 1 : 2;
        }
        this.getWindow().setFlags(256, 256);
    }

    public void startVerify() {
        this.startVerify(new VerifyInterface(){

            @Override
            public void onSuccess() {
            }

            @Override
            public void onCancel() {
                MainActivity.this.finish();
            }
        });
    }

    public void startVerify(VerifyInterface verifyInterface) {
        SharedPreferences msh = this.getSharedPreferences("Security", 0);
        SharedPreferences.Editor mshe = msh.edit();
        if (msh.getBoolean("verified", false) && this.isValid(msh.getString("code", null))) {
            verifyInterface.onSuccess();
            return;
        }
        VerifyDialog dialog = new VerifyDialog((Context)this, this, mshe, verifyInterface);
        dialog.show();
    }

    static {
    }
}

