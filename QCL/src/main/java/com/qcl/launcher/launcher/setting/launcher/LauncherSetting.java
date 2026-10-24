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
     * ★★★ v3.1：液态玻璃（边缘畸变）模式。**默认 0 = 关**。
     *
     * <p>取值对应 {@code GlassApplier.MODE_*}：
     * <ul>
     *   <li>0 = 关（默认）—— 完全不做任何玻璃效果</li>
     *   <li>1 = 开 —— 具体覆盖范围看 {@link #liquidGlassScope}</li>
     *   <li>2 = 开且标准（v3.4 之前的旧值，只可能出现在手改过的配置里）</li>
     * </ul>
     *
     * <p>★ 为什么默认关：这是逐像素着色效果，任何设备上都有成本。
     * 默认开等于把一个可选装饰变成所有人的负担 —— 用户看过 3.0 全开的真机表现后
     * 明确要求「在设置里开，默认是关的」。
     *
     * <p>★★★ v3.4 起这个字段只表达**开/关**，「套多少面板」交给
     * {@link #liquidGlassScope}。原因：关掉再打开时如果把「标准」丢了，
     * 用户会觉得设置没记住；而一个字段同时表示开关和范围，就必然要在关的时候
     * 把它归零 —— 那范围信息就没了。两件事交给两个字段，各自只表达一件事。
     */
    public int liquidGlassMode = 0;

    /**
     * ★★★ v3.4：液态玻璃「覆盖范围」。**0 / 1 = 轻量（默认），2 = 标准**。
     *
     * <ul>
     *   <li>轻量：只给长边 ≥ 200dp 的大面板套（导航栏 / 返回栏 / 大卡片）</li>
     *   <li>标准：长边 ≥ 110dp 的中等面板也套（小卡片 / 输入框 / 列表项）</li>
     * </ul>
     *
     * <p>★ 这是「观感」和「性能」之间的那根秤：面板越多越好看，也越费。
     * 默认轻量 —— 与 v3.1~v3.3 的行为完全一致，老用户升级后观感不变。
     *
     * <p>★ 取值直接复用 {@code GlassApplier.MODE_LIGHT / MODE_FULL}，
     * 少一套映射就少一处对不上的机会。
     */
    public int liquidGlassScope = 0;

    /**
     * ★★★ v3.3：液态玻璃「折射强度」，0~100。**0 = 未设置 → 用 50（标准）**。
     *
     * <p>为什么需要这个：带宽和偏移量是**观感的全部**，而它们合不合适取决于背景图的
     * 纹理密度 —— 纯色背景上再强也看不出来，花哨壁纸上太强会像「水波纹」。
     * 这两个数没法替所有人定，只能给一个可调的旋钮，让用户自己拧到顺眼。
     *
     * <p>★ 用 0 当「未设置」而不是把默认值写成 50：Gson 反序列化老配置时，
     * 缺失的字段会落到类型默认值 0。若默认值写 50，老用户读到的会是 0（最弱档），
     * 表现成「升级后玻璃突然变弱了」—— 这是升级兼容的经典坑。
     */
    public int liquidGlassStrength = 0;

    /**
     * ★★★ v3.0.4：液态玻璃「折射位移增益」，0~100。**0 = 未设置 → 用 44（= 2.2 倍）**。
     *
     * <h3>这个旋钮控制什么</h3>
     * 边缘那一圈的画面被**搬动多远**。位移 = 折射带宽度 × 增益，所以：
     * <ul>
     *   <li>增益太小 ⇒ 画面搬不动，看起来「和没有一样」（v3.1 的 9dp 配 14dp 就是这个）</li>
     *   <li>增益太大 ⇒ 边缘内容折叠成条纹（v4.1 按面板高度定标时，整屏面板 10.7 倍）</li>
     * </ul>
     *
     * <h3>★★★ 为什么默认值不是 0 也不是 50，而是 44</h3>
     * 44/20 = 2.2，正是 v4.2 定稿的那个值。选它的原因不是拍脑袋：
     * 位移/带宽 恒为 {@code 0.983 × 增益}，2.2 ⇒ 约 2.16 倍，落在
     * 「明显大于带宽（1.5~2.5×）」这个能看懂的区间中间。
     * ★ 仍然用 0 当「未设置」—— 老配置里没有这个字段，Gson 会给 0，
     *   读到 0 就退回 2.2，老用户升级后观感一点不变。
     *
     * <p>★ 百分比而不是直接存倍数：SeekBar 只能给整数进度，倍数带小数
     * （1.2~2.6）存进去要么丢精度要么得二次换算。百分比 20~55 ↔ 倍数 1.0~2.75，
     *   除数是 20，整数进度刚好整除。
     */
    public int liquidGlassGain = 0;

    /**
     * ★★★ v3.1：排版风格。0 = QCL 原版（左侧竖栏），1 = FCL（底部横栏），
     * 2 = HMCL 电脑端（左侧窄栏），3 = ZL2（底部大圆角）。
     *
     * <p>取值对应 {@code LauncherLayoutStyle.STYLE_*}。
     * 默认 0，老用户升级后界面不变。
     */
    public int layoutStyle = 0;

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
