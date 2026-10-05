package com.qcl.launcher.launcher.setting.launcher;

import com.qcl.launcher.launcher.setting.launcher.child.BackgroundSetting;
import com.qcl.launcher.launcher.setting.launcher.child.SourceSetting;

/* loaded from: classes2.dex */
public class LauncherSetting {
    /** ★ 1.2.3：界面背景是否透明（默认全透明；关掉恢复原来的灰色面板） */
    public boolean transparentBackground = true;
    public boolean autoCheckUpdate;
    public boolean autoDownloadTaskQuantity;
    public String cachePath;
    public SourceSetting downloadUrlSource;
    public boolean fullscreen;
    public String gameFileDirectory;
    public boolean getBetaVersion;
    public int language;
    public BackgroundSetting launcherBackground;
    public String launcherTheme;
    public int maxDownloadTask;
    public String panelColor;
    public boolean transBar;
    /** 1.3.7: 主界面是否显示账号人物（默认开） */
    public boolean showAccountModel = true;
    public int uiTheme;

    /**
     * ★ 社区版新增：仅 Wi-Fi 下载（默认关，保持老用户行为不变）。
     * <p>开着时，下载过程中一旦切到按流量计费的网络，会自动暂停并保留分片，
     * 回到 Wi-Fi 后自动继续（走 HTTP Range 断点续传，不重下）。
     * <p>放在 LauncherSetting 里而不是新开一个 SharedPreferences —— 它本来就是
     * 「启动器全局设置」，且已随 launcher_setting.json 一起持久化，无需新增存储层。
     */
    public boolean wifiOnlyDownload = false;

    /**
     * ★ 社区版新增：全局下载限速（KB/s，0 = 不限速）。
     * <p>用于「边下边玩」时避免下载把带宽吃光。默认 0，老用户行为不变。
     */
    public int downloadSpeedLimitKbps = 0;

    /**
     * ★ 社区版新增：启动前 / 退出后自定义脚本（{@code sh -c} 执行，工作目录 = 当前实例目录）。
     * <p>默认空串 = 不执行任何东西，老用户行为不变。
     * <p>为什么放在「全局设置」而不是「单实例」：QCL 的实例配置结构是反编译得来的多套模型，
     * 往里塞字段的迁移风险远大于收益；先用全局，等确认需求再下沉到实例级。
     */
    public String preLaunchScript = "";
    public String postExitScript = "";

    /**
     * ★★★ 社区版新增：安全区模式（刘海 / 挖孔 / 大圆角避让）。
     *
     * <p><b>为什么需要它</b>：主界面是**沉浸式全屏**（{@code LAYOUT_FULLSCREEN |
     * LAYOUT_HIDE_NAVIGATION} + 挖孔 SHORT_EDGES），内容从屏幕最顶端开始画。
     * 在圆角半径大的手机上，顶部那排入口按钮和底部导航条会**被 R 角切掉**；
     * 有刘海/挖孔的机型则会被摄像头挡掉。
     *
     * <p><b>取值</b>：
     * <ul>
     *   <li>{@link #SAFE_AREA_AUTO}（0，默认）—— 自动检测：从窗口真实的
     *       {@code WindowInsets} 读系统栏 + 刘海 + 圆角半径，只有确实需要才避让。
     *       没刘海、圆角小的机型保持原来的满屏观感，不吃亏。</li>
     *   <li>{@link #SAFE_AREA_ON}（1）—— 总是避让（自动检测失灵时的兜底）。</li>
     *   <li>{@link #SAFE_AREA_OFF}（2）—— 从不避让，恢复原来的沉浸式满屏。</li>
     * </ul>
     *
     * <p>★ 默认选 AUTO 而不是 OFF：默认值要为「大多数人」服务，
     * 而现在大多数人用的正是圆角/挖孔屏。
     */
    public int safeAreaMode = SAFE_AREA_AUTO;

    /**
     * ★★★ 社区版新增：启动前检查（#21）。默认<b>开</b>。
     *
     * <p>开着时，点「启动」会先跑一遍 {@code PreLaunchCheck}（Java 运行时 / 内存分配 /
     * 版本文件 / 模组数量）—— 但**只有查出问题才会弹面板**，一切正常时静默继续，
     * 不会给每次启动都加一层确认框。
     */
    public boolean preLaunchCheck = true;

    /**
     * ★★★ 社区版新增：背景轮播「一次性迁移」标记。
     *
     * <p>为什么需要它：用户要的是「把背景换成自己那两张光影截图做轮播」，
     * 但**已经装过启动器的人**，设置文件里存的是老默认值 {@code type = 1}（经典图片），
     * 只改代码里的默认值对他**完全无效** —— 他会说「改了但没变」。
     *
     * <p>所以做一次迁移：第一次跑到这里时，把背景切成 {@code type = 0}（动态轮播）并把这个标记置 true。
     * <b>只跑一次</b> —— 之后用户在设置里自己选什么就是什么，不会每次启动都覆盖他的选择。
     */
    public boolean bgCarouselMigrated = false;

    public static final int SAFE_AREA_AUTO = 0;
    public static final int SAFE_AREA_ON = 1;
    public static final int SAFE_AREA_OFF = 2;

    public LauncherSetting(String str, SourceSetting sourceSetting, int i, int i2, boolean z, boolean z2, boolean z3, boolean z4, boolean z5, String str2, String str3, BackgroundSetting backgroundSetting, String str4) {
        this(str, sourceSetting, i, i2, z, z2, z3, z4, z5, str2, str3, backgroundSetting, str4, 0);
    }

    public LauncherSetting(String str, SourceSetting sourceSetting, int i, int i2, boolean z, boolean z2, boolean z3, boolean z4, boolean z5, String str2, String str3, BackgroundSetting backgroundSetting, String str4, int i3) {
        this.gameFileDirectory = str;
        this.downloadUrlSource = sourceSetting;
        this.language = i;
        this.maxDownloadTask = i2;
        this.autoDownloadTaskQuantity = z;
        this.autoCheckUpdate = z2;
        this.getBetaVersion = z3;
        this.fullscreen = z4;
        this.transBar = z5;
        this.launcherTheme = str2;
        this.panelColor = str3;
        this.launcherBackground = backgroundSetting;
        this.cachePath = str4;
        this.uiTheme = i3;
    }
}
