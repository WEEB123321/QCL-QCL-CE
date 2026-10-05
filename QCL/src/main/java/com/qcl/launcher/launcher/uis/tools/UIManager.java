package com.qcl.launcher.launcher.uis.tools;

import android.content.Context;
import android.content.Intent;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.account.AccountUI;
import com.qcl.launcher.launcher.uis.game.download.DownloadUI;
import com.qcl.launcher.launcher.uis.game.download.right.game.DownloadFabricAPIUI;
import com.qcl.launcher.launcher.uis.game.download.right.game.DownloadFabricUI;
import com.qcl.launcher.launcher.uis.game.download.right.game.DownloadForgeUI;
import com.qcl.launcher.launcher.uis.game.download.right.game.DownloadNeoForgeUI;
import com.qcl.launcher.launcher.uis.game.download.right.game.DownloadLiteLoaderUI;
import com.qcl.launcher.launcher.uis.game.download.right.game.DownloadOptifineUI;
import com.qcl.launcher.launcher.uis.game.download.right.game.DownloadQuiltAPIUI;
import com.qcl.launcher.launcher.uis.game.download.right.game.DownloadQuiltUI;
import com.qcl.launcher.launcher.uis.game.download.right.game.InstallGameUI;
import com.qcl.launcher.launcher.uis.game.download.right.resource.BaseDownloadUI;
import com.qcl.launcher.launcher.uis.game.manager.GameManagerUI;
import com.qcl.launcher.launcher.uis.game.manager.universal.ExportWorldUI;
import com.qcl.launcher.launcher.uis.game.manager.universal.ModUpdateUI;
import com.qcl.launcher.launcher.uis.game.manager.universal.PackMcManagerUI;
import com.qcl.launcher.launcher.uis.game.version.VersionListUI;
import com.qcl.launcher.launcher.uis.game.version.universal.AddGameDirectoryUI;
import com.qcl.launcher.launcher.uis.game.version.universal.ExportPackageFileUI;
import com.qcl.launcher.launcher.uis.game.version.universal.ExportPackageInfoUI;
import com.qcl.launcher.launcher.uis.game.version.universal.ExportPackageTypeUI;
import com.qcl.launcher.launcher.uis.game.version.universal.InstallPackageUI;
// ★ 1.4.1 新增页面（自 1.4.0 朋友源码包合并）
import com.qcl.launcher.launcher.uis.lab.LabUI;
import com.qcl.launcher.launcher.uis.lobby.LobbyUI;
import com.qcl.launcher.launcher.uis.main.MainUI;
// ★ 社区版新增页面：插件 / 备份与恢复 / 启动诊断 / 日志中心 / 统计 / 教程 / 自动任务
import com.qcl.launcher.launcher.uis.autotask.AutoTaskUI;
import com.qcl.launcher.launcher.uis.backup.BackupUI;
import com.qcl.launcher.launcher.uis.compat.CompatUI;
import com.qcl.launcher.launcher.uis.diagnostic.DiagnosticUI;
import com.qcl.launcher.launcher.uis.log.LogCenterUI;
import com.qcl.launcher.launcher.uis.modcheck.ModCheckUI;
import com.qcl.launcher.launcher.uis.pack.ResourceConflictUI;
import com.qcl.launcher.launcher.uis.plugin.PluginUI;
import com.qcl.launcher.launcher.uis.stats.StatsUI;
import com.qcl.launcher.launcher.uis.tutorial.TutorialUI;
import com.qcl.launcher.launcher.uis.universal.setting.SettingUI;

import java.util.ArrayList;

public class UIManager {

    public MainUI mainUI;
    public AccountUI accountUI;
    public GameManagerUI gameManagerUI;
    public VersionListUI versionListUI;
    public DownloadUI downloadUI;
    public SettingUI settingUI;

    // ★ 1.4.1 新增：大厅 / 实验室
    public LobbyUI lobbyUI;
    public LabUI labUI;

    // ★ 社区版新增：插件（底部导航第 4 个 Tab）/ 备份与恢复 / 启动诊断 / 日志中心（后三者收在「我的」里）
    public PluginUI pluginUI;
    public BackupUI backupUI;
    public DiagnosticUI diagnosticUI;
    public LogCenterUI logCenterUI;
    public StatsUI statsUI;
    public TutorialUI tutorialUI;
    public AutoTaskUI autoTaskUI;
    public ResourceConflictUI resourceConflictUI;
    public CompatUI compatUI;
    public ModCheckUI modCheckUI;

    public ModUpdateUI modUpdateUI;
    public PackMcManagerUI packMcManagerUI;
    public ExportWorldUI exportWorldUI;
    public AddGameDirectoryUI addGameDirectoryUI;
    public InstallPackageUI installPackageUI;
    public ExportPackageTypeUI exportPackageTypeUI;
    public ExportPackageInfoUI exportPackageInfoUI;
    public ExportPackageFileUI exportPackageFileUI;

    public InstallGameUI installGameUI;
    public DownloadForgeUI downloadForgeUI;
    public DownloadNeoForgeUI downloadNeoForgeUI;
    public DownloadFabricUI downloadFabricUI;
    public DownloadFabricAPIUI downloadFabricAPIUI;
    public DownloadLiteLoaderUI downloadLiteLoaderUI;
    public DownloadOptifineUI downloadOptifineUI;
    public DownloadQuiltUI downloadQuiltUI;
    public DownloadQuiltAPIUI downloadQuiltAPIUI;

    public BaseUI[] mainUIs;
    public ArrayList<BaseUI> uis;
    public BaseUI currentUI;

    /** ★ 社区版新增：初始化失败的页面（页面名 → 异常摘要）。判定依据见 bootUI 注释。 */
    private final java.util.LinkedHashMap<String, String> brokenPages = new java.util.LinkedHashMap<>();
    /** 与 brokenPages 同步，用于对象级快速判定（mainUIs 过滤 / 派发守卫） */
    private final java.util.LinkedHashSet<BaseUI> brokenUIs = new java.util.LinkedHashSet<>();

    public UIManager (Context context, MainActivity activity){
        try { mainUI = new MainUI(context, activity); } catch (Throwable t) { mainUI = null; recordBootFail("MainUI", t); }
        try { accountUI = new AccountUI(context, activity); } catch (Throwable t) { accountUI = null; recordBootFail("AccountUI", t); }
        try { gameManagerUI = new GameManagerUI(context, activity); } catch (Throwable t) { gameManagerUI = null; recordBootFail("GameManagerUI", t); }
        try { versionListUI = new VersionListUI(context, activity); } catch (Throwable t) { versionListUI = null; recordBootFail("VersionListUI", t); }
        try { downloadUI = new DownloadUI(context, activity); } catch (Throwable t) { downloadUI = null; recordBootFail("DownloadUI", t); }
        try { settingUI = new SettingUI(context, activity); } catch (Throwable t) { settingUI = null; recordBootFail("SettingUI", t); }

        // ★ 1.4.1 新增页面
        try { lobbyUI = new LobbyUI(context, activity); } catch (Throwable t) { lobbyUI = null; recordBootFail("LobbyUI", t); }
        try { labUI = new LabUI(context, activity); } catch (Throwable t) { labUI = null; recordBootFail("LabUI", t); }

        // ★ 社区版新增页面
        try { pluginUI = new PluginUI(context, activity); } catch (Throwable t) { pluginUI = null; recordBootFail("PluginUI", t); }
        try { backupUI = new BackupUI(context, activity); } catch (Throwable t) { backupUI = null; recordBootFail("BackupUI", t); }
        try { diagnosticUI = new DiagnosticUI(context, activity); } catch (Throwable t) { diagnosticUI = null; recordBootFail("DiagnosticUI", t); }
        try { logCenterUI = new LogCenterUI(context, activity); } catch (Throwable t) { logCenterUI = null; recordBootFail("LogCenterUI", t); }
        try { statsUI = new StatsUI(context, activity); } catch (Throwable t) { statsUI = null; recordBootFail("StatsUI", t); }
        try { tutorialUI = new TutorialUI(context, activity); } catch (Throwable t) { tutorialUI = null; recordBootFail("TutorialUI", t); }
        try { autoTaskUI = new AutoTaskUI(context, activity); } catch (Throwable t) { autoTaskUI = null; recordBootFail("AutoTaskUI", t); }
        try { resourceConflictUI = new ResourceConflictUI(context, activity); } catch (Throwable t) { resourceConflictUI = null; recordBootFail("ResourceConflictUI", t); }
        try { compatUI = new CompatUI(context, activity); } catch (Throwable t) { compatUI = null; recordBootFail("CompatUI", t); }
        try { modCheckUI = new ModCheckUI(context, activity); } catch (Throwable t) { modCheckUI = null; recordBootFail("ModCheckUI", t); }

        try { modUpdateUI = new ModUpdateUI(context, activity); } catch (Throwable t) { modUpdateUI = null; recordBootFail("ModUpdateUI", t); }
        try { packMcManagerUI = new PackMcManagerUI(context, activity); } catch (Throwable t) { packMcManagerUI = null; recordBootFail("PackMcManagerUI", t); }
        try { exportWorldUI = new ExportWorldUI(context, activity); } catch (Throwable t) { exportWorldUI = null; recordBootFail("ExportWorldUI", t); }
        try { addGameDirectoryUI = new AddGameDirectoryUI(context, activity); } catch (Throwable t) { addGameDirectoryUI = null; recordBootFail("AddGameDirectoryUI", t); }
        try { installPackageUI = new InstallPackageUI(context, activity); } catch (Throwable t) { installPackageUI = null; recordBootFail("InstallPackageUI", t); }
        try { exportPackageTypeUI = new ExportPackageTypeUI(context, activity); } catch (Throwable t) { exportPackageTypeUI = null; recordBootFail("ExportPackageTypeUI", t); }
        try { exportPackageInfoUI = new ExportPackageInfoUI(context, activity); } catch (Throwable t) { exportPackageInfoUI = null; recordBootFail("ExportPackageInfoUI", t); }
        try { exportPackageFileUI = new ExportPackageFileUI(context, activity); } catch (Throwable t) { exportPackageFileUI = null; recordBootFail("ExportPackageFileUI", t); }

        try { installGameUI = new InstallGameUI(context, activity); } catch (Throwable t) { installGameUI = null; recordBootFail("InstallGameUI", t); }
        try { downloadForgeUI = new DownloadForgeUI(context, activity); } catch (Throwable t) { downloadForgeUI = null; recordBootFail("DownloadForgeUI", t); }
        try { downloadNeoForgeUI = new DownloadNeoForgeUI(context, activity); } catch (Throwable t) { downloadNeoForgeUI = null; recordBootFail("DownloadNeoForgeUI", t); }
        try { downloadFabricUI = new DownloadFabricUI(context, activity); } catch (Throwable t) { downloadFabricUI = null; recordBootFail("DownloadFabricUI", t); }
        try { downloadFabricAPIUI = new DownloadFabricAPIUI(context, activity); } catch (Throwable t) { downloadFabricAPIUI = null; recordBootFail("DownloadFabricAPIUI", t); }
        try { downloadLiteLoaderUI = new DownloadLiteLoaderUI(context, activity); } catch (Throwable t) { downloadLiteLoaderUI = null; recordBootFail("DownloadLiteLoaderUI", t); }
        try { downloadOptifineUI = new DownloadOptifineUI(context, activity); } catch (Throwable t) { downloadOptifineUI = null; recordBootFail("DownloadOptifineUI", t); }
        try { downloadQuiltUI = new DownloadQuiltUI(context, activity); } catch (Throwable t) { downloadQuiltUI = null; recordBootFail("DownloadQuiltUI", t); }
        try { downloadQuiltAPIUI = new DownloadQuiltAPIUI(context, activity); } catch (Throwable t) { downloadQuiltAPIUI = null; recordBootFail("DownloadQuiltAPIUI", t); }

        bootUI("MainUI", mainUI);
        bootUI("AccountUI", accountUI);
        bootUI("GameManagerUI", gameManagerUI);
        bootUI("VersionListUI", versionListUI);
        bootUI("DownloadUI", downloadUI);
        bootUI("SettingUI", settingUI);

        // ★ 1.4.1 新增页面
        bootUI("LobbyUI", lobbyUI);
        bootUI("LabUI", labUI);

        // ★ 社区版新增页面
        bootUI("PluginUI", pluginUI);
        bootUI("BackupUI", backupUI);
        bootUI("DiagnosticUI", diagnosticUI);
        bootUI("LogCenterUI", logCenterUI);
        bootUI("StatsUI", statsUI);
        bootUI("TutorialUI", tutorialUI);
        bootUI("AutoTaskUI", autoTaskUI);
        bootUI("ResourceConflictUI", resourceConflictUI);
        bootUI("CompatUI", compatUI);
        bootUI("ModCheckUI", modCheckUI);

        bootUI("ModUpdateUI", modUpdateUI);
        bootUI("PackMcManagerUI", packMcManagerUI);
        bootUI("ExportWorldUI", exportWorldUI);
        bootUI("AddGameDirectoryUI", addGameDirectoryUI);
        bootUI("InstallPackageUI", installPackageUI);
        bootUI("ExportPackageTypeUI", exportPackageTypeUI);
        bootUI("ExportPackageInfoUI", exportPackageInfoUI);
        bootUI("ExportPackageFileUI", exportPackageFileUI);

        bootUI("InstallGameUI", installGameUI);
        bootUI("DownloadForgeUI", downloadForgeUI);
        bootUI("DownloadNeoForgeUI", downloadNeoForgeUI);
        bootUI("DownloadFabricUI", downloadFabricUI);
        bootUI("DownloadFabricAPIUI", downloadFabricAPIUI);
        bootUI("DownloadLiteLoaderUI", downloadLiteLoaderUI);
        bootUI("DownloadOptifineUI", downloadOptifineUI);
        bootUI("DownloadQuiltUI", downloadQuiltUI);
        bootUI("DownloadQuiltAPIUI", downloadQuiltAPIUI);

        BaseUI[] allUIs = new BaseUI[] {
                mainUI,
                modUpdateUI,
                packMcManagerUI,
                exportWorldUI,
                addGameDirectoryUI,
                installPackageUI,
                exportPackageTypeUI,
                exportPackageInfoUI,
                exportPackageFileUI,
                accountUI,
                gameManagerUI,
                versionListUI,
                downloadUI,
                settingUI,
                lobbyUI,
                labUI,
                installGameUI,
                downloadForgeUI,
                downloadNeoForgeUI,
                downloadFabricUI,
                downloadLiteLoaderUI,
                downloadOptifineUI,
                downloadFabricAPIUI,
                downloadQuiltUI,
                downloadQuiltAPIUI,
                // ★ 社区版新增页面
                pluginUI,
                backupUI,
                diagnosticUI,
                logCenterUI,
                statsUI,
                tutorialUI,
                autoTaskUI,
                resourceConflictUI,
                compatUI,
                modCheckUI
        };
        // ★ 社区版：把初始化失败的页面剔出去。它们处于半初始化状态，
        //   绝不能再被 onPause / onResume / onActivityResult 这几个遍历 mainUIs 的循环派发到。
        java.util.ArrayList<BaseUI> alive = new java.util.ArrayList<>(allUIs.length);
        for (BaseUI u : allUIs) {
            if (u != null && !brokenUIs.contains(u)) {
                alive.add(u);
            }
        }
        mainUIs = alive.toArray(new BaseUI[0]);

        uis = new ArrayList<>();
        // ★ 社区版路标：最后这一步会跑 MainUI.onStart()（首页真正显示出来）。
        //   补上埋点，否则崩在这里时路标会停在「页面 onCreate → 最后一个页面」，看不出是 onStart。
        com.qcl.launcher.launcher.StartupTrace.mark("UIManager：switchMainUI(首页) → MainUI.onStart");
        switchMainUI(mainUI);
    }

    /**
     * ★★★ 社区版新增：逐个 boot 页面，并**在调用前**记一条启动路标。
     *
     * <p><b>为什么需要路标</b>：上面这个构造器会把 27 个页面一次性 {@code onCreate()}。
     * 只要任何一页抛异常，整个启动器就表现为「一打开就闪退」—— 而且因为发生在
     * 预加载阶段、还没进到任何页面，光看现象根本不知道是哪一页。
     * 有了路标，{@code startup_trace.log} 的**最后一行**就写着崩之前正在初始化哪个页面。
     *
     * <p><b>★★★ 为什么这里改成「隔离」而不是让异常继续抛</b>（第一版的选择，现已推翻）：
     * 第一版刻意不 try/catch，理由是「吞掉异常会让页面处于半初始化状态、问题被转移到后面」。
     * 那个理由本身没错，但它有个前提 —— <b>用户至少还能打开启动器去看崩溃页</b>。
     * 实际反馈是「打开直接闪退」，连崩溃页都没机会看，这个前提不成立：
     * 一个页面的问题把整个启动器变成了砖，用户拿不到任何可用信息，我也拿不到日志。
     *
     * <p>所以现在改成：<b>捕获、记名、隔离，但绝不静默</b>。
     * <ul>
     *   <li>异常摘要写进启动路标（{@code ★ 页面初始化失败} 那一行）；</li>
     *   <li>完整堆栈打到 logcat（tag {@code QCLStartup}）；</li>
     *   <li>失败的页面从 {@link #mainUIs} 里剔除，且 {@link #switchMainUI} /
     *       {@link #switchMainUITab} 拒绝派发 —— 半初始化对象绝不会被 {@code onStart} 碰到；</li>
     *   <li>{@code MainActivity} 构造完 UIManager 后会弹一个明确的对话框列出失败页面。</li>
     * </ul>
     * 也就是说：问题被<b>摆到台面上</b>，而不是被藏起来 —— 这是和「静默吞异常」的本质区别。
     */
    private void bootUI(String name, BaseUI ui) {
        // ★ 社区版：构造阶段就已失败的页面（见 recordBootFail）在这里是 null，
        //   不能碰 —— 它已经在 brokenPages 里登记过了。
        if (ui == null) {
            return;
        }
        com.qcl.launcher.launcher.StartupTrace.mark("页面 onCreate → " + name);
        try {
            ui.onCreate();
        } catch (Throwable t) {
            brokenUIs.add(ui);
            brokenPages.put(name, String.valueOf(t));
            com.qcl.launcher.launcher.StartupTrace.mark(
                    "★ 页面初始化失败（已隔离跳过）→ " + name + " : " + t);
            android.util.Log.e("QCLStartup", "页面 onCreate 失败，已隔离：" + name, t);
        }
    }

    /**
     * ★★★ 社区版新增：页面**构造**失败时登记。
     *
     * <p>和 {@link #bootUI} 的 onCreate 失败分开记录，是因为构造失败时**根本没有对象**
     * 可以放进 {@link #brokenUIs}，只能按名字登记。调用点在构造器里 —— 每个
     * {@code new XxxUI(context, activity)} 都被 try/catch 包住。
     *
     * <p><b>为什么必须包构造器</b>：UIManager 是在 MainActivity.onCreate 里 new 的，
     * 构造器抛异常会直接冒泡到主线程 → 全局处理器 Process.killProcess →
     * 用户看到「打开直接闪退」。只隔离 onCreate 是不够的，构造器同样是必经之路。
     */
    private void recordBootFail(String name, Throwable t) {
        brokenPages.put(name, String.valueOf(t));
        com.qcl.launcher.launcher.StartupTrace.mark(
                "★ 页面构造失败（已隔离跳过）→ " + name + " : " + t);
        android.util.Log.e("QCLStartup", "页面构造失败，已隔离：" + name, t);
    }

    /** ★ 社区版新增：有没有页面在初始化阶段失败 */
    public boolean hasBrokenPages() {
        return !brokenPages.isEmpty();
    }

    /** ★ 社区版新增：失败页面清单（给 MainActivity 弹框用；每行「页面名：异常」） */
    public String brokenPagesReport() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : brokenPages.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("· ").append(e.getKey()).append("：").append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * ★★★ 社区版新增（续 6）：安全派发一次页面生命周期调用。
     *
     * <p><b>为什么必须有它</b>：这些派发点原先**一处 try/catch 都没有**。
     * 主界面的页面是**全部预加载**的，任何一个页面的 onStart / onResume / onPause /
     * onActivityResult 抛异常，整个循环就断在那里，异常直接冒泡到主线程 ——
     * 那就是未捕获异常 = 弹崩溃页 + 杀进程。
     * 典型表现：「点某个 Tab 就闪退」「从后台切回来就闪退」「从文件选择器返回就闪退」，
     * 而且因为崩在别人的回调里，堆栈看起来跟用户的操作毫无关系，极难定位。
     *
     * <p><b>disableOnFail 的取舍</b>：只有 onStart 失败才把页面拉黑 ——
     * 它连显示都做不到，再切进去只会重复崩。onPause / onResume 这类收尾与刷新动作
     * 失败就只记录不拉黑：拉黑会让导航莫名其妙地失效，那是把一个问题换成另一个问题。
     */
    private void safeDispatch(String what, BaseUI ui, Runnable action, boolean disableOnFail) {
        if (ui == null || brokenUIs.contains(ui)) {
            return;
        }
        try {
            action.run();
        } catch (Throwable t) {
            String name = ui.getClass().getSimpleName();
            if (disableOnFail) {
                brokenUIs.add(ui);
            }
            brokenPages.put(name + "@" + what, String.valueOf(t));
            com.qcl.launcher.launcher.StartupTrace.mark(
                    "★ 页面 " + what + " 失败（已隔离跳过）→ " + name + " : " + t);
            android.util.Log.e("QCLStartup", "页面 " + what + " 失败，已隔离：" + name, t);
        }
    }

    public void switchMainUI(BaseUI ui) {
        if (ui == null) {
            return;
        }
        currentUI = ui;
        uis.add(ui);
        // ★ 社区版：初始化失败的页面不派发 onStart —— 在半初始化对象上跑生命周期，
        //   只会在别处炸，那时候更难查。栈的记账照旧，返回键行为因此不变。
        //   ★ 续 6：onStart 本身也包起来。这个方法是「压栈进页面」的公共入口，
        //     从设置页点「备份 / 启动诊断」也走这里，抛异常就是真·未捕获崩溃。
        safeDispatch("onStart", ui, new Runnable() {
            @Override
            public void run() {
                ui.onStart();
            }
        }, true);
        if (uis.size() > 1){
            // 1.0.6：加 try/catch，避免被切出去的那个页面在 onStop 里抛异常时
            // 连带打断本次切换（表现为主界面之后黑屏 / 返回栏不显示）。
            try {
                uis.get(uis.size() - 2).onStop();
            } catch (Throwable ignored) {
            }
        }
        System.out.println("-----------------------------------------------------------------------------------------------------------------------------switch to new ui");
    }

    /**
     * ★ 社区版新增：底部导航切 Tab 专用（清栈再进）。
     *
     * <p><b>为什么不能直接用 switchMainUI</b>：那个是"压栈"语义 —— 从下载页点进二级页、
     * 再点另一个 Tab，返回栈里会残留上一个 Tab 的页面，按返回键会跳到不相干的界面。
     * 切 Tab 的语义是"换一级区域"，必须把旧栈整个清掉。
     *
     * <p>对每个旧页面都调一次 onStop（页面自己的收尾逻辑照常执行，例如 MainUI 会把 3D 人物
     * 的 GLSurfaceView 藏起来），再进目标页 onStart。多调一次 onStop 是安全的：
     * 各页面的 onStop 都是幂等的收尾动作。
     */
    public void switchMainUITab(BaseUI ui) {
        if (ui == null || brokenUIs.contains(ui)) {
            return;
        }
        for (int i = uis.size() - 1; i >= 0; i--) {
            BaseUI old = uis.get(i);
            try {
                old.onStop();
            } catch (Throwable ignored) {
            }
            uis.remove(i);
        }
        currentUI = ui;
        uis.add(ui);
        // ★ 续 6：切 Tab 是「点一下就闪退」的高发路径，同样包起来
        safeDispatch("onStart", ui, new Runnable() {
            @Override
            public void run() {
                ui.onStart();
            }
        }, true);
    }

    // ★★★ 续 6：下面三个派发循环原先**完全没有保护** —— 它们由 MainActivity 的生命周期
    //   直接调用（onPause / onResume / onActivityResult），跑在主线程上，
    //   任意一个页面抛异常都会中断整个循环并冒泡成未捕获异常 = 整机闪退。
    //   典型表现：「从后台切回来就闪退」「从文件选择器 / 相册返回就闪退」——
    //   因为崩在别人的回调里，堆栈跟用户的操作看起来毫无关系，是最难查的一类。
    //   现在逐个页面独立保护：坏一个只跳过它自己，其余页面照常收到回调。
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        for (BaseUI ui : mainUIs) {
            safeDispatch("onActivityResult", ui, new Runnable() {
                @Override
                public void run() {
                    ui.onActivityResult(requestCode, resultCode, data);
                }
            }, false);
        }
        for (BaseUI ui : uis) {
            if (ui instanceof BaseDownloadUI) {
                safeDispatch("onActivityResult", ui, new Runnable() {
                    @Override
                    public void run() {
                        ui.onActivityResult(requestCode, resultCode, data);
                    }
                }, false);
            }
        }
    }

    public void onPause() {
        for (BaseUI ui : mainUIs) {
            safeDispatch("onPause", ui, new Runnable() {
                @Override
                public void run() {
                    ui.onPause();
                }
            }, false);
        }
        for (BaseUI ui : uis) {
            if (ui instanceof BaseDownloadUI) {
                safeDispatch("onPause", ui, new Runnable() {
                    @Override
                    public void run() {
                        ui.onPause();
                    }
                }, false);
            }
        }
    }

    public void onResume() {
        for (BaseUI ui : mainUIs) {
            safeDispatch("onResume", ui, new Runnable() {
                @Override
                public void run() {
                    ui.onResume();
                }
            }, false);
        }
        for (BaseUI ui : uis) {
            if (ui instanceof BaseDownloadUI) {
                safeDispatch("onResume", ui, new Runnable() {
                    @Override
                    public void run() {
                        ui.onResume();
                    }
                }, false);
            }
        }
    }

    public void removeUIIfExist(BaseUI ui) {
        for (int i = 0;i < uis.size();i++){
            if (uis.get(i) == ui){
                uis.remove(i);
            }
        }
    }
}
