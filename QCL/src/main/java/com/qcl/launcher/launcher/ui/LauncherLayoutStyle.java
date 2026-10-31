package com.qcl.launcher.launcher.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;

/**
 * ★★★ 排版风格（Layout Style）—— 五套预设，运行时切换，不用改布局文件。
 *
 * <h3>★ v3.7：这一版是**照着三家真实源码重做的**，不是「把导航换个位置」</h3>
 * 上一版（v3.1~v3.6）只改了导航栏的位置 / 宽高 / 圆角，四套风格本质上是同一个界面
 * 换了点参数 —— 用户一眼看出「主题排版完全不对（完全不对！）」。
 *
 * <p>去 GitHub 把三个仓库拉下来读了布局文件之后，它们的首页结构根本不是「底部一条导航」：
 * <table border="1">
 *   <tr><th>启动器</th><th>真实结构（源码依据）</th></tr>
 *   <tr><td><b>FCL</b><br>{@code FoldCraftLauncher}</td>
 *       <td>{@code activity_main.xml}：<b>左侧窄图标栏</b>（{@code left_menu}，{@code wrap_content}，
 *           背景 {@code bg_game_menu} = {@code #40F4F4F4} + 8dp 四角，7 个 {@code FCLMenuView}
 *           图标竖排、间距 10dp）+ 中间 {@code ViewPager2} +
 *           <b>右侧 25% 宽满高面板</b>（{@code right_menu}，
 *           {@code layout_constraintWidth_percent="0.25"}，背景 {@code bg_right_menu} =
 *           {@code #40F4F4F4} 只圆<b>左上 / 左下</b> 8dp），面板里从上到下是
 *           52dp 头像 + 16sp 粗体账号名 + 版本卡片（30dp 图标）+ 启动按钮</td></tr>
 *   <tr><td><b>HMCL-PE</b><br>{@code HMCL-dev/HMCL-PE}</td>
 *       <td>{@code activity_main.xml}：<b>顶部 {@code Toolbar}</b>（{@code ?attr/actionBarSize}，
 *           背景 {@code @color/colorAccent}，白字，左边 18dp 合成台图标 + 粗体标题）+
 *           {@code main_ui_container}。<br>
 *           {@code ui_main.xml}：<b>横向双栏</b> —— 左 {@code ScrollView} <b>固定 200dp</b>
 *           （背景 {@code @color/launcher_ui_background}），条目是
 *           {@code launcher_button_parent} 底 + 30dp 图标 + 文字；
 *           右栏底部是 {@code 200dp × 55dp} 的蓝色启动按钮。
 *           <b>没有底部导航、没有侧栏图标</b></td></tr>
 *   <tr><td><b>ZL2</b><br>{@code ZalithLauncher2}</td>
 *       <td>纯 Compose，没有 XML。{@code LauncherScreen.kt}：
 *           {@code ContentWeight = 7f} / {@code ActionMenuWeight = 3f} ⇒
 *           ActionMenu 占 <b>30%</b>；{@code cardWidth = maxWidth * 3/10 - 12dp}，
 *           {@code cardHeight = maxHeight - 24dp}，{@code shape = shapes.extraLarge}
 *           （Material3 默认 <b>28dp</b> 四角），可停靠左右、离屏 12dp。
 *           <b>没有任何导航栏</b>，左侧 70% 是卡片网格</td></tr>
 *   <tr><td><b>MIUIX</b></td>
 *       <td>底部悬浮 Dock：满宽、左右与底部留 12dp、24dp 圆角；图标 + 单行文字，
 *           选中项是蓝色胶囊；液态玻璃调试 Tab 出现时自动六等分</td></tr>
 * </table>
 *
 * <h3>为什么仍然是「运行时改 LayoutParams」而不是做四套布局</h3>
 * 主界面有 28 个 {@code include}、150+ 个布局文件。做四套 = 维护 600 份布局，
 * 任何一处改动都要同步四次 —— 这个项目已经因为「要记得改多处」翻车过三次
 * （签名、版本号、docs）。
 * 但「只改参数」不等于「只改位置」：这一版把**结构差异**也补上了 ——
 * 右侧 25%/30% 操作面板（{@code view_side_panel}）、HMCL 顶部彩条、Tab 的四种形态
 * （文字 / 纯图标 / 图标+文字横排），都是靠运行时挂载和 LayoutParams 表达的。
 *
 * <h3>⚠️ 与安全区的冲突（必须处理）</h3>
 * {@link SafeAreaHelper} 在安装时**记下当时的 padding 作为基准**，之后每次
 * inset 回调都用「基准 + 安全区」重算。排版切换改了 padding 却不重装它，
 * 下一次回调就会把 padding 改回旧基准 —— 表现是「切了风格，过一会儿自己弹回去」。
 * 所以 {@link #apply} 的最后必须重新 {@code install} 并 {@code requestApplyInsets}。
 */
public final class LauncherLayoutStyle {

    private static final String TAG = "QCLLayoutStyle";

    /** QCL 原版：左侧竖排导航（默认）。 */
    public static final int STYLE_QCL = 0;
    /** FCL 风格：左侧窄图标栏 + 右侧 25% 操作面板。 */
    public static final int STYLE_FCL = 1;
    /** HMCL 电脑端风格：顶部彩条 + 左侧 200dp 菜单。 */
    public static final int STYLE_HMCL = 2;
    /** ZL2 风格：无导航栏 + 右侧 30% 浮动操作卡片。 */
    public static final int STYLE_ZL2 = 3;
    /** MIUIX 风格：底部悬浮 Dock + 图标文字 Tab + 蓝色选中胶囊。 */
    public static final int STYLE_MIUIX = 4;

    // ── FCL 的真实配色 / 尺寸（bg_game_menu.xml / bg_right_menu.xml / colors.xml）──
    /** FCL {@code @color/ui_bg_color} = {@code #40F4F4F4}。 */
    private static final int FCL_BG = 0x40F4F4F4;
    /** FCL 左侧图标栏宽度：{@code wrap_content}，实测 ≈ 56dp（48dp 图标 + 5dp×2 padding）。 */
    private static final float FCL_RAIL_DP = 56f;
    /** FCL {@code right_menu} 占屏幕宽的比例。 */
    private static final float FCL_PANEL_RATIO = 0.25f;

    // ── ZL2 的真实配色 / 尺寸（LauncherScreen.kt）──
    /** ZL2 ActionMenu 占屏幕宽的比例（{@code 3 / (7 + 3)}）。 */
    private static final float ZL2_PANEL_RATIO = 0.30f;
    /** ZL2 {@code ActionMenuOuterPadding = 12.dp}。 */
    private static final float ZL2_OUTER_DP = 12f;
    /** ZL2 {@code MaterialTheme.shapes.extraLarge} 的默认值。 */
    private static final float ZL2_RADIUS_DP = 28f;
    /** ZL2 的卡片底色（Material3 surface 近似）。 */
    private static final int ZL2_CARD = 0xF2F7F7F7;

    // ── HMCL-PE 的真实尺寸（ui_main.xml）──
    /** HMCL 左栏 {@code ScrollView} 固定宽度。 */
    private static final float HMCL_MENU_DP = 200f;
    /** HMCL {@code Toolbar} 高度（{@code ?attr/actionBarSize} 的标准值）。 */
    private static final float HMCL_BAR_DP = 56f;

    /** HMCL 顶部彩条在这个 ViewGroup 里的标记（用来找回来 / 移除）。 */
    private static final String TAG_HMCL_BAR = "qcl_hmcl_bar";

    // ── Tab 的四种形态 ──
    /** QCL：罗马数字 + 中文（无图标）。 */
    private static final int TABS_TEXT = 0;
    /** FCL：只有图标。 */
    private static final int TABS_ICON = 1;
    /** HMCL：图标 + 中文横排。 */
    private static final int TABS_ROW = 2;
    /** MIUIX：底部 Dock，图标在上、中文在下，选中态是蓝色胶囊。 */
    private static final int TABS_MIUIX = 3;

    private static final int[] TAB_IDS = {
            R.id.nav_tab_home, R.id.nav_tab_instance, R.id.nav_tab_download,
            R.id.nav_tab_plugin, R.id.nav_tab_mine};
    private static final int[] TAB_TEXT_IDS = {
            R.id.nav_text_home, R.id.nav_text_instance, R.id.nav_text_download,
            R.id.nav_text_plugin, R.id.nav_text_mine};
    private static final int[] TAB_ICON_IDS = {
            R.id.nav_icon_home, R.id.nav_icon_instance, R.id.nav_icon_download,
            R.id.nav_icon_plugin, R.id.nav_icon_mine};

    /**
     * ★★★ 当前风格（供 {@link com.qcl.launcher.launcher.uis.tools.BottomNavHelper} 查）。
     *
     * <p>为什么需要暴露：ZL2 风格下**导航栏必须整条隐藏**（ZL2 本来就没有导航）。
     * 但 {@code BottomNavHelper.show()} 每次页面切换都会 {@code setVisibility(VISIBLE)}，
     * 会把 ZL2 的隐藏状态覆盖掉。它读这个值来决定「要不要真的显示」。
     */
    private static volatile int sCurrentStyle = STYLE_QCL;

    /** 当前风格是否要求隐藏导航栏。 */
    public static boolean navHiddenByStyle() {
        return sCurrentStyle == STYLE_ZL2;
    }

    public static int currentStyle() {
        return sCurrentStyle;
    }

    private LauncherLayoutStyle() {
    }

    public static String nameOf(int style) {
        switch (style) {
            case STYLE_FCL:  return "FCL";
            case STYLE_HMCL: return "HMCL";
            case STYLE_ZL2:  return "ZL2";
            case STYLE_MIUIX: return "MIUIX";
            default:         return "QCL";
        }
    }

    /**
     * 应用排版风格。
     *
     * <p>★ 必须**主线程**调用，且要在 {@code setContentView} 之后。
     */
    public static void apply(MainActivity activity, int style) {
        if (activity == null) {
            return;
        }
        sCurrentStyle = style;
        try {
            View nav = activity.findViewById(R.id.qcl_bottom_nav);
            View content = activity.findViewById(R.id.launcher_layout);
            View fab = activity.findViewById(R.id.qcl_search_fab);
            View root = activity.findViewById(R.id.launcher_root);
            View sidePanel = activity.findViewById(R.id.qcl_side_panel);
            if (nav == null || content == null) {
                return;
            }

            switch (style) {
                case STYLE_FCL:
                    applyFcl(activity, nav, content, fab, sidePanel);
                    break;
                case STYLE_HMCL:
                    applyHmcl(activity, nav, content, fab, sidePanel);
                    break;
                case STYLE_ZL2:
                    applyZl2(activity, nav, content, fab, sidePanel);
                    break;
                case STYLE_MIUIX:
                    applyMiuix(activity, nav, content, fab, sidePanel);
                    break;
                default:
                    applyQcl(activity, nav, content, fab, sidePanel);
                    break;
            }

            // ★★★ 必须重装安全区（原因见类注释）
            if (root != null) {
                SafeAreaHelper.install(activity, root, content, nav, fab);
                root.requestApplyInsets();
            }
            Log.i(TAG, "排版风格已应用: " + nameOf(style));
        } catch (Throwable t) {
            // 排版是装饰性功能，失败绝不能影响主界面可用性
            Log.w(TAG, "排版风格应用失败（保持原样）", t);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  五套风格
    // ═══════════════════════════════════════════════════════════

    /** QCL 原版：左侧 76dp 竖栏，罗马数字 + 中文，18dp 上方两角。 */
    private static void applyQcl(Activity a, View nav, View content, View fab, View sidePanel) {
        navLayout(nav, dp(a, 76), ViewGroup.LayoutParams.MATCH_PARENT);
        navOrientation(nav, true, dp(a, 6), dp(a, 14));
        navBackground(nav, new int[]{dp(a, 18), dp(a, 18), dp(a, 18), dp(a, 18), 0, 0, 0, 0},
                navPanelColor(a));
        tabs(nav, TABS_TEXT);
        // ★★★ 2.2.7：**矮屏（横屏）下必须让出高度**。
        //   竖栏里是「品牌 + 5 个等分 Tab + 分隔线 + 版本号 + 菜单」，
        //   固定部分在横屏（可用高度常常只有 160dp 左右）会把高度吃光，
        //   5 个 weight=1 的 Tab 各自分到 0 高 ⇒ 五条文字**叠在同一处**，
        //   看起来像一堆乱码（用户截图里那块模糊的中文就是这个）。
        //   矮屏时先砍品牌和页脚，把高度全让给 Tab。
        //
        // ★★★ 3.0.1：只砍品牌还不够。横屏 499px 高时实测：
        //   上下 padding 84px + 菜单/分隔线 ≈ 120px ⇒ 5 个 Tab 每个只剩 **19dp**，
        //   而「11sp 中文竖排两行」需要约 30dp ⇒ 仍溢出 11dp，字照样叠。
        //   ⇒ 矮屏时把中文**改横排**（一行，约 17dp）并收紧 padding。
        boolean compact = screenH(a) < dp(a, 420);
        setBrandVisible(nav, !compact);
        setNavFooterVisible(nav, !compact);
        applyCompactNav(nav, compact);
        hideHmclBar(content);

        // ★ 写绝对值（top / right / bottom 全归零）——
        //   SafeAreaHelper 把「安装时的 padding」当基准，保留旧值会被当成新基准再累加。
        content.setPadding(dp(a, 76), 0, 0, 0);

        fabMargin(fab, dp(a, 66), dp(a, 14));
        showSidePanel(sidePanel, false, 0, null, 0, 0, 0, 0);
        // 返回栏维持右上角（原样）
        backBar(a, false, 0, dp(a, 6));
        Log.i(TAG, "QCL：左侧 76dp 竖栏");
    }

    /**
     * FCL：左侧窄图标栏 + 右侧 25% 操作面板。
     *
     * <p>源码依据见类注释表格。两个必须对上的细节：
     * <ul>
     *   <li>左栏背景 {@code #40F4F4F4} + <b>8dp 四角</b>（{@code bg_game_menu.xml}）；</li>
     *   <li>右面板背景同样 {@code #40F4F4F4}，但<b>只圆左上 / 左下 8dp</b>
     *       （{@code bg_right_menu.xml}）—— 因为它的右缘贴着屏幕边，圆了会露出壁纸。</li>
     * </ul>
     */
    private static void applyFcl(Activity a, View nav, View content, View fab, View sidePanel) {
        int railW = dp(a, FCL_RAIL_DP);
        int panelW = Math.round(screenW(a) * FCL_PANEL_RATIO);
        int r8 = dp(a, 8);

        navLayout(nav, railW, ViewGroup.LayoutParams.MATCH_PARENT);
        // FCL 的 left_menu padding 是 5dp 横 / 10dp 纵
        navOrientation(nav, true, dp(a, 5), dp(a, 10));
        navBackground(nav, new int[]{r8, r8, r8, r8, r8, r8, r8, r8}, FCL_BG);
        tabs(nav, TABS_ICON);
        setBrandVisible(nav, false);
        hideHmclBar(content);

        content.setPadding(railW, 0, panelW, 0);

        // 右侧操作面板：25% 宽、满高、只圆左上/左下
        showSidePanel(sidePanel, true, panelW,
                new int[]{r8, r8, 0, 0, 0, 0, r8, r8}, FCL_BG, 0, 0, 0);

        // 搜索球要躲开右面板，否则被压在下面
        fabMargin(fab, dp(a, 66), panelW + dp(a, 14));
        // ★ 返回栏挪到左侧（右面板占了它的原位置）
        backBar(a, true, railW + dp(a, 8), dp(a, 6));
        Log.i(TAG, "FCL：左 " + FCL_RAIL_DP + "dp 图标栏 + 右 " + panelW + "px(25%) 面板");
    }

    /**
     * HMCL 电脑端：顶部彩条 + 左侧 200dp 菜单。
     *
     * <p>源码依据：{@code activity_main.xml} 的 {@code Toolbar}（{@code colorAccent} 底、白字）
     * + {@code ui_main.xml} 的 {@code ScrollView layout_width="200dp"}。
     * 左栏条目是「图标 + 文字」横排。
     *
     * <p>★ 顶部彩条是**运行时挂进 {@code launcher_layout} 的第 0 个子 View**，
     * 不在任何 XML 里 —— 因为只有这一套风格需要它，为它单独改 {@code activity_main.xml}
     * 会让另外三套风格都得处理「多出来的一条」。
     * {@code launcher_layout} 是垂直 LinearLayout，插入一个固定高度的子 View 后，
     * 下面 {@code match_parent} 的 {@code main_ui_container} 会自动拿到剩余高度
     * （{@code LinearLayout.measureVertical} 对 MATCH_PARENT 子项会减掉已用高度）。
     */
    private static void applyHmcl(Activity a, View nav, View content, View fab, View sidePanel) {
        int menuW = dp(a, HMCL_MENU_DP);
        int barH = dp(a, HMCL_BAR_DP);

        navLayout(nav, menuW, ViewGroup.LayoutParams.MATCH_PARENT);
        // ★★★ 2.2.7：左菜单必须从彩条**下面**开始。
        //   彩条是满宽的，压在 y=0..56dp 这一条；菜单如果还从 y=0 开始，
        //   第一个 Tab（首页）会整个藏在彩条底下 —— 点得到但看不见。
        setTopMargin(nav, barH);
        // HMCL 的左栏是一列「条目」，横向留白靠条目自己的 padding
        navOrientation(nav, true, dp(a, 8), dp(a, 12));
        // 背景用 {@code @color/launcher_ui_background}（本项目是透明），
        // 条目自己的 {@code qcl_nav_tab_bg} 负责可见性和选中态。
        navBackground(nav, new int[]{0, 0, 0, 0, 0, 0, 0, 0}, 0x00000000);
        tabs(nav, TABS_ROW);
        setBrandVisible(nav, false);

        showSidePanel(sidePanel, false, 0, null, 0, 0, 0, 0);

        if (content instanceof ViewGroup) {
            ensureHmclBar(a, (ViewGroup) content);
        }
        // ★★★ 2.2.7：上内边距让出彩条高度。
        //   2.2.5 只写了 `setPadding(menuW, 0, 0, 0)`，而彩条是 content 的子 View ——
        //   子 View 长在 padding 里面，于是彩条从 x=menuW 才开始、右端又短 menuW，
        //   看起来就是「一条没铺满的棕色带子」。现在彩条挂到 launcher_root 上（见
        //   ensureHmclBar），content 只负责让出顶部高度。
        content.setPadding(menuW, barH, 0, 0);

        fabMargin(fab, dp(a, 66), dp(a, 14));
        // ★ 返回栏挪到左侧并**下移到彩条之下**，否则会压在彩条上
        backBar(a, true, menuW + dp(a, 8), dp(a, HMCL_BAR_DP + 6f));
        Log.i(TAG, "HMCL：顶部彩条 + 左 " + HMCL_MENU_DP + "dp 菜单");
    }

    /**
     * ZL2：无导航栏 + 右侧 30% 浮动操作卡片。
     *
     * <p>源码依据：{@code LauncherScreen.kt} 的 {@code ContentWeight=7f} /
     * {@code ActionMenuWeight=3f}（⇒ 30%）、{@code cardWidth = maxWidth*3/10 - 12dp}、
     * {@code cardHeight = maxHeight - 24dp}、{@code shape = shapes.extraLarge}（28dp）、
     * {@code ActionMenuOuterPadding = 12.dp}。
     *
     * <p>★ 卡片是「浮动」的：上下各留 12dp，所以高度 = 屏高 − 24dp。
     * 用 {@code MATCH_PARENT} + 上下 12dp 外边距表达，效果等价。
     */
    private static void applyMiuix(Activity a, View nav, View content, View fab, View sidePanel) {
        boolean compact = screenH(a) < dp(a, 420);
        int navH = dp(a, compact ? 62f : 74f);
        int margin = dp(a, 12f);

        navBottomLayout(a, nav, navH, margin);
        navOrientation(nav, false, dp(a, 6f), dp(a, 6f));
        navMiuixBackground(a, nav);
        tabs(nav, TABS_MIUIX);
        setBrandVisible(nav, false);
        setNavFooterVisible(nav, false);
        hideHmclBar(content);

        content.setPadding(0, 0, 0, navH + margin * 2);
        fabMargin(fab, navH + margin * 2 + dp(a, 6f), margin + dp(a, 4f));
        showSidePanel(sidePanel, false, 0, null, 0, 0, 0, 0);
        backBar(a, false, 0, dp(a, 6f));
        Log.i(TAG, "MIUIX：底部悬浮 Dock");
    }

    /**
     * MIUIX 的底部悬浮 Dock：满宽、左右与底部各留 12dp，圆角 24dp。
     *
     * <p>与 {@link #navLayout} 分开，是因为后者固定贴顶 + 贴左；Dock 必须贴底，
     * 而且需要保留左右外边距形成「悬浮」效果。
     */
    private static void navBottomLayout(Activity a, View nav, int heightPx, int marginPx) {
        ViewGroup.LayoutParams raw = nav.getLayoutParams();
        if (!(raw instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) raw;
        lp.removeRule(RelativeLayout.ALIGN_PARENT_START);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.removeRule(RelativeLayout.CENTER_VERTICAL);

        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = heightPx;
        lp.leftMargin = marginPx;
        lp.rightMargin = marginPx;
        lp.topMargin = 0;
        lp.bottomMargin = marginPx;
        lp.addRule(RelativeLayout.ALIGN_PARENT_START);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        nav.setLayoutParams(lp);
        nav.setVisibility(View.VISIBLE);
        nav.setElevation(dp(a, 10f));
    }

    private static void navMiuixBackground(Activity a, View nav) {
        try {
            GradientDrawable g = new GradientDrawable();
            g.setColor(isNight(a) ? 0xF21C1C1E : 0xF7FFFFFF);
            g.setCornerRadius(dp(a, 24f));
            g.setStroke(dp(a, 1f), isNight(a) ? 0x22FFFFFF : 0x0D000000);
            nav.setBackground(g);
        } catch (Throwable ignored) {
        }
    }

    private static void applyZl2(Activity a, View nav, View content, View fab, View sidePanel) {
        int outer = dp(a, ZL2_OUTER_DP);
        int panelW = Math.round(screenW(a) * ZL2_PANEL_RATIO) - outer;
        int r28 = dp(a, ZL2_RADIUS_DP);

        // ★ ZL2 没有任何导航栏 —— 整条藏掉
        nav.setVisibility(View.GONE);
        hideHmclBar(content);

        // 左侧 70% 是内容区，不留左内边距
        content.setPadding(0, 0, panelW + outer * 2, 0);

        showSidePanel(sidePanel, true, panelW,
                new int[]{r28, r28, r28, r28, r28, r28, r28, r28}, ZL2_CARD,
                outer, outer, outer);

        fabMargin(fab, dp(a, 66), panelW + outer * 2 + dp(a, 8));
        // ★ 返回栏挪到左侧（浮动卡片占了右侧）
        backBar(a, true, dp(a, 8), dp(a, 6));
        Log.i(TAG, "ZL2：无导航 + 右 " + panelW + "px(30%) 浮动卡片");
    }

    // ═══════════════════════════════════════════════════════════
    //  通用零件
    // ═══════════════════════════════════════════════════════════

    /**
     * 导航栏在父容器（{@code launcher_root}，RelativeLayout）里的位置与尺寸。
     *
     * <p>★ 先 {@code removeRule} 再 {@code addRule}：RelativeLayout 的规则是「集合」语义，
     * 只 add 不 remove 的话多条规则会同时生效。
     *
     * <p>★ 边距必须归零（写绝对值，不能保留）。原因与 content padding 同源：
     * SafeAreaHelper 把「安装时的 bottomMargin」当基准，保留旧值会每切一次风格就飘一段。
     */
    private static void navLayout(View nav, int widthPx, int heightPx) {
        ViewGroup.LayoutParams raw = nav.getLayoutParams();
        if (!(raw instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) raw;
        lp.removeRule(RelativeLayout.ALIGN_PARENT_START);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.removeRule(RelativeLayout.CENTER_VERTICAL);

        lp.topMargin = 0;
        lp.bottomMargin = 0;
        lp.leftMargin = 0;
        lp.rightMargin = 0;

        lp.width = widthPx;
        lp.height = heightPx;
        lp.addRule(RelativeLayout.ALIGN_PARENT_START);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        nav.setLayoutParams(lp);
        nav.setVisibility(View.VISIBLE);
        nav.setElevation(0);
    }

    /** 导航栏自身的方向 + 内边距。 */
    private static void navOrientation(View nav, boolean vertical, int padH, int padV) {
        if (!(nav instanceof LinearLayout)) {
            return;
        }
        LinearLayout ll = (LinearLayout) nav;
        ll.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER_VERTICAL);
        ll.setPadding(padH, padV, padH, padV);
    }

    /**
     * 导航栏 / 面板背景：动态生成圆角矩形。
     *
     * <p>★ 为什么不用 XML drawable：五套风格要五种圆角 / 颜色，而 {@code GradientDrawable}
     * 的 corner 半径在 XML 里是常量，运行时改不了。
     *
     * @param radii 8 个值，顺序 = 左上X,左上Y, 右上X,右上Y, 右下X,右下Y, 左下X,左下Y
     */
    private static void navBackground(View v, int[] radii, int color) {
        try {
            GradientDrawable g = new GradientDrawable();
            g.setColor(color);
            float[] f = new float[8];
            for (int i = 0; i < 8 && i < radii.length; i++) {
                f[i] = radii[i];
            }
            g.setCornerRadii(f);
            v.setBackground(g);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Tab 的四种形态。
     *
     * <p>★ 为什么用「显隐 + 尺寸」而不是建两套 Tab：
     * {@code view_bottom_nav.xml} 里每个 Tab 同时带了**图标、罗马数字、中文**三个子 View，
     * 由这里按风格挑一个组合显示。切换是纯属性操作，不重建视图，也不会丢监听。
     *
     * <p>★ 罗马数字那个 TextView 没有 id（XML 里本来不需要），
     * 所以用「第一个没有 id 的 TextView」来找 —— 比按下标取稳，
     * 以后在 Tab 里插别的控件也不会错位。
     */
    private static void tabs(View nav, int mode) {
        Context c = nav.getContext();
        boolean miuix = mode == TABS_MIUIX;
        for (int i = 0; i < TAB_IDS.length; i++) {
            View tab = nav.findViewById(TAB_IDS[i]);
            if (tab == null) {
                continue;
            }
            ImageView icon = nav.findViewById(TAB_ICON_IDS[i]);
            TextView label = nav.findViewById(TAB_TEXT_IDS[i]);
            TextView numeral = numeralOf(tab);

            // ① 子 View 显隐、图标尺寸与着色
            if (icon != null) {
                icon.setVisibility(mode == TABS_TEXT ? View.GONE : View.VISIBLE);
                ViewGroup.LayoutParams rawIcon = icon.getLayoutParams();
                if (rawIcon != null) {
                    int size = dp(c, miuix ? 22f : 24f);
                    rawIcon.width = size;
                    rawIcon.height = size;
                    icon.setLayoutParams(rawIcon);
                }
                if (miuix) {
                    icon.setDuplicateParentStateEnabled(true);
                    icon.setImageTintList(miuixTabColors(c));
                } else {
                    icon.setDuplicateParentStateEnabled(false);
                    icon.setImageTintList(null);
                    icon.clearColorFilter();
                }
            }
            if (numeral != null) {
                numeral.setVisibility(mode == TABS_TEXT ? View.VISIBLE : View.GONE);
            }
            if (label != null) {
                label.setVisibility(View.VISIBLE);
                label.setDuplicateParentStateEnabled(miuix);
                CharSequence orig = (CharSequence) label.getTag(TAG_NAV_TEXT);
                if (orig == null) {
                    orig = label.getText();
                    if (orig != null) {
                        label.setTag(TAG_NAV_TEXT, orig);
                    }
                }
                if (miuix) {
                    if (orig != null) {
                        String text = orig.toString().replace("\n", "");
                        if (i == 3) {
                            text = "插件";
                        }
                        label.setText(text);
                    }
                    label.setSingleLine(true);
                } else if (mode == TABS_TEXT) {
                    if (orig != null) {
                        label.setText(orig);
                    }
                    label.setSingleLine(false);
                }
                label.setTextSize(miuix ? 9.5f : (mode == TABS_ROW ? 15f : 11f));
                if (miuix) {
                    label.setTextColor(miuixTabColors(c));
                } else {
                    label.setTextColor(c.getColor(R.color.colorPureBlack));
                }
                ViewGroup.LayoutParams rawLabel = label.getLayoutParams();
                if (rawLabel instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) rawLabel;
                    llp.leftMargin = 0;
                    llp.rightMargin = 0;
                    llp.topMargin = miuix ? dp(c, 2f) : 0;
                    label.setLayoutParams(llp);
                }
            }

            // ② 尺寸与形态
            ViewGroup.LayoutParams raw = tab.getLayoutParams();
            if (!(raw instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
            lp.leftMargin = 0;
            lp.rightMargin = 0;
            if (miuix) {
                lp.width = 0;
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.weight = 1f;
                lp.topMargin = 0;
                lp.bottomMargin = 0;
                lp.leftMargin = dp(c, 2f);
                lp.rightMargin = dp(c, 2f);
                tab.setPadding(0, dp(c, 4f), 0, dp(c, 4f));
                if (tab instanceof LinearLayout) {
                    ((LinearLayout) tab).setOrientation(LinearLayout.VERTICAL);
                    ((LinearLayout) tab).setGravity(Gravity.CENTER);
                }
                tab.setBackgroundResource(R.drawable.qcl_nav_tab_bg_miuix);
            } else {
                tab.setBackgroundResource(R.drawable.qcl_nav_tab_bg);
                if (tab instanceof LinearLayout) {
                    ((LinearLayout) tab).setOrientation(LinearLayout.HORIZONTAL);
                }
                if (mode == TABS_TEXT) {
                    // QCL：竖排均分高度
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.height = 0;
                    lp.weight = 1f;
                    lp.topMargin = 0;
                    lp.bottomMargin = 0;
                    tab.setPadding(0, 0, 0, 0);
                    if (tab instanceof LinearLayout) {
                        ((LinearLayout) tab).setGravity(Gravity.CENTER);
                    }
                } else {
                    // FCL / HMCL：固定高度的条目，间距 10dp（FCL 的 layout_marginTop="10dp"）
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.height = dp(c, mode == TABS_ROW ? 56f : 48f);
                    lp.weight = 0f;
                    lp.topMargin = i == 0 ? 0 : dp(c, 10f);
                    lp.bottomMargin = 0;
                    int padStart = mode == TABS_ROW ? dp(c, 12f) : 0;
                    tab.setPadding(padStart, 0, padStart, 0);
                    if (tab instanceof LinearLayout) {
                        ((LinearLayout) tab).setGravity(mode == TABS_ROW
                                ? (Gravity.CENTER_VERTICAL | Gravity.START)
                                : Gravity.CENTER);
                    }
                }
            }
            tab.setLayoutParams(lp);
        }
        styleGlassTab(nav, mode);
    }

    /** 第 6 个「液态玻璃调试」Tab 只在开玻璃时出现，但横排 Dock 下也要按同样形态收窄。 */
    private static void styleGlassTab(View nav, int mode) {
        View tab = nav.findViewById(R.id.nav_tab_glass);
        if (tab == null) {
            return;
        }
        Context c = tab.getContext();
        boolean miuix = mode == TABS_MIUIX;
        TextView label = tab.findViewById(R.id.nav_text_glass);
        TextView numeral = numeralOf(tab);
        if (label != null) {
            label.setDuplicateParentStateEnabled(miuix);
            CharSequence orig = (CharSequence) label.getTag(TAG_NAV_TEXT);
            if (orig == null) {
                orig = label.getText();
                if (orig != null) {
                    label.setTag(TAG_NAV_TEXT, orig);
                }
            }
            if (miuix) {
                if (orig != null) {
                    label.setText("玻璃");
                }
                label.setSingleLine(true);
            } else if (mode == TABS_TEXT) {
                if (orig != null) {
                    label.setText(orig);
                }
                label.setSingleLine(false);
            }
            label.setTextSize(miuix ? 9.5f : 11f);
            if (miuix) {
                label.setTextColor(miuixTabColors(c));
            } else {
                label.setTextColor(c.getColor(R.color.colorPureBlack));
            }
            ViewGroup.LayoutParams rawLabel = label.getLayoutParams();
            if (rawLabel instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) rawLabel;
                llp.leftMargin = 0;
                llp.rightMargin = 0;
                llp.topMargin = miuix ? dp(c, 2f) : 0;
                label.setLayoutParams(llp);
            }
        }
        if (numeral != null) {
            numeral.setVisibility(mode == TABS_TEXT ? View.VISIBLE : View.GONE);
        }
        ViewGroup.LayoutParams raw = tab.getLayoutParams();
        if (!(raw instanceof LinearLayout.LayoutParams)) {
            return;
        }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
        if (miuix) {
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.weight = 1f;
            lp.topMargin = 0;
            lp.bottomMargin = 0;
            lp.leftMargin = dp(c, 2f);
            lp.rightMargin = dp(c, 2f);
            tab.setPadding(0, dp(c, 4f), 0, dp(c, 4f));
            if (tab instanceof LinearLayout) {
                ((LinearLayout) tab).setOrientation(LinearLayout.VERTICAL);
                ((LinearLayout) tab).setGravity(Gravity.CENTER);
            }
            tab.setBackgroundResource(R.drawable.qcl_nav_tab_bg_miuix);
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = 0;
            lp.weight = 1f;
            lp.topMargin = 0;
            lp.bottomMargin = 0;
            lp.leftMargin = 0;
            lp.rightMargin = 0;
            tab.setPadding(0, 0, 0, 0);
            if (tab instanceof LinearLayout) {
                ((LinearLayout) tab).setOrientation(LinearLayout.HORIZONTAL);
                ((LinearLayout) tab).setGravity(Gravity.CENTER);
            }
            tab.setBackgroundResource(R.drawable.qcl_nav_tab_bg);
        }
        tab.setLayoutParams(lp);
    }

    private static ColorStateList miuixTabColors(Context c) {
        boolean night = isNight(c);
        int accent = night ? 0xFF8AB4FF : 0xFF3482FF;
        int normal = night ? 0xFFEDE7DE : 0xFF1A1A1A;
        return new ColorStateList(
                new int[][]{ {android.R.attr.state_selected}, {} },
                new int[]{accent, normal});
    }

    /** 找 Tab 里那个「罗马数字」TextView —— 它是唯一没有 id 的 TextView。 */
    private static TextView numeralOf(View tab) {
        if (!(tab instanceof ViewGroup)) {
            return null;
        }
        ViewGroup g = (ViewGroup) tab;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof TextView && c.getId() == View.NO_ID) {
                return (TextView) c;
            }
        }
        return null;
    }

    /**
     * ★★★ 2.2.7：导航栏底色**必须跟着深浅色走**。
     *
     * <p>用户报「左侧 UI 文字颜色有些问题」：夜间模式里 {@code colorPureBlack} 被
     * {@code values-night} 换成 {@code #FFEDE7DE}（近白），所有正文变浅色；
     * 而导航栏底色是写死的 85% 白 ⇒ 浅色字落在浅色板上，等于看不见。
     *
     * <p>同一个问题在 XML 那边也用 {@code drawable-night/qcl_bottom_nav_bg.xml} 修了一份 ——
     * 两条路都要对：玻璃关掉时走 XML，玻璃开着时这里会把背景整个换掉。
     */
    private static int navPanelColor(Activity a) {
        return isNight(a) ? 0xD91A1A1A : 0xD9FFFFFF;
    }

    private static boolean isNight(Context a) {
        try {
            int mode = a.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 侧栏底部（分隔线 + 版本号）。矮屏时整组藏掉，把高度让给 Tab。 */
    /** 保存导航文字原文的 tag key（竖排靠字符串里的 {@code \n}，改横排时必须能还原）。 */
    private static final int TAG_NAV_TEXT = 0x7C1A5601;

    /**
     * ★★★ 3.0.1：矮屏（横屏）时把导航 Tab 从「罗马数字 + 中文竖排」收成「中文横排」。
     *
     * <h3>为什么必须收</h3>
     * Tab 是 {@code weight=1} 均分的，横屏 499px（166dp）高时，扣掉上下 padding、
     * 分隔线和菜单，每个 Tab 只剩约 **19dp**；而「11sp 中文两行」需要约 30dp
     * ⇒ 超出 11dp，{@code LinearLayout} 不会压缩子 View，文字直接**溢出重叠**。
     * 这就是用户截图里左侧那堆看不清的中文。
     *
     * <h3>为什么改横排而不是继续缩字号</h3>
     * 缩到 8sp 才勉强塞进 19dp，但 8sp 的中文在手机上已经难以辨认。
     * 改横排后一行只要约 17dp，配合收紧 padding（14dp→8dp）还能有富余，
     * 字号保持 10sp 可读。
     *
     * <h3>★ 必须可逆</h3>
     * 竖排是靠字符串里的 {@code \n} 实现的，{@code setText} 之后就没了。
     * 所以第一次改之前把原文存进 {@link #TAG_NAV_TEXT}，恢复时读回来 ——
     * 否则用户在「QCL / FCL / HMCL / ZL2」之间来回切一次，竖排就永久变成横排了。
     */
    private static void applyCompactNav(View nav, boolean compact) {
        for (int i = 0; i < TAB_IDS.length; i++) {
            try {
                View tab = nav.findViewById(TAB_IDS[i]);
                if (tab == null) {
                    continue;
                }
                TextView label = nav.findViewById(TAB_TEXT_IDS[i]);
                TextView numeral = numeralOf(tab);

                if (label != null) {
                    CharSequence orig = (CharSequence) label.getTag(TAG_NAV_TEXT);
                    if (orig == null) {
                        orig = label.getText();
                        if (orig != null) {
                            label.setTag(TAG_NAV_TEXT, orig);
                        }
                    }
                    if (orig != null) {
                        if (compact) {
                            label.setText(orig.toString().replace("\n", ""));
                            label.setTextSize(10f);
                            label.setSingleLine(true);
                        } else {
                            label.setText(orig);
                            label.setTextSize(11f);
                            label.setSingleLine(false);
                        }
                    }
                }
                // 矮屏收起罗马数字：横排之后它只剩装饰作用，还占横向宽度
                if (numeral != null) {
                    numeral.setVisibility(compact ? View.GONE : View.VISIBLE);
                }
            } catch (Throwable ignored) {
                // 单个 Tab 出问题不影响其它
            }
        }
        // 上下 padding：矮屏 8dp（省 12dp 总高），正常 14dp
        try {
            if (nav instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) nav;
                int h = dp(nav.getContext(), 6f);
                int v = dp(nav.getContext(), compact ? 8f : 14f);
                ll.setPadding(h, v, h, v);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setNavFooterVisible(View nav, boolean visible) {
        try {
            View divider = nav.findViewById(R.id.nav_divider);
            if (divider != null) {
                divider.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
            View version = nav.findViewById(R.id.nav_app_version);
            if (version != null) {
                version.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 品牌区（方框 + 字母 + 小字）只在 QCL 原版的侧栏里放得下。 */
    private static void setBrandVisible(View nav, boolean visible) {        try {
            View brand = nav.findViewById(R.id.nav_brand);
            if (brand != null) {
                brand.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
            View version = nav.findViewById(R.id.nav_app_version);
            if (version != null) {
                version.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 给导航栏补一个**上**外边距（写绝对值）。
     *
     * <p>★ 为什么要单独一个方法：{@link #navLayout} 会把四条外边距全清零 ——
     * 那是为了躲开 {@code SafeAreaHelper} 的「基准值累加」坑。HMCL 需要额外让出
     * 顶部彩条的高度，所以只能在 {@code navLayout} **之后**再补一次。
     */
    private static void setTopMargin(View v, int topPx) {
        if (v == null) {
            return;
        }
        try {
            ViewGroup.LayoutParams raw = v.getLayoutParams();
            if (raw instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) raw;
                lp.topMargin = topPx;
                v.setLayoutParams(lp);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 搜索悬浮球的底部 / 右侧外边距（写绝对值）。 */
    private static void fabMargin(View fab, int bottomPx, int endPx) {        if (fab == null) {
            return;
        }
        try {
            ViewGroup.LayoutParams flp = fab.getLayoutParams();
            if (flp instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) flp;
                rlp.bottomMargin = bottomPx;
                rlp.rightMargin = endPx;
                fab.setLayoutParams(rlp);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 右侧操作面板（FCL / ZL2 用）。
     *
     * <p>★ 面板的**内容**（账号行 / 版本行 / 启动按钮）全在 {@code view_side_panel.xml} 里，
     * 点击路由由 {@code MainUI} 用 {@code performClick()} 复用原有逻辑；
     * 这里只负责它的**几何与外观** —— 宽度比例、圆角、外边距。
     */
    private static void showSidePanel(View panel, boolean visible, int widthPx,
                                      int[] radii, int color,
                                      int marginTop, int marginBottom, int marginEnd) {
        if (panel == null) {
            return;
        }
        try {
            if (!visible) {
                panel.setVisibility(View.GONE);
                return;
            }
            ViewGroup.LayoutParams raw = panel.getLayoutParams();
            if (raw instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) raw;
                lp.width = widthPx;
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.topMargin = marginTop;
                lp.bottomMargin = marginBottom;
                lp.rightMargin = marginEnd;
                lp.leftMargin = 0;
                panel.setLayoutParams(lp);
            } else if (raw != null) {
                raw.width = widthPx;
                raw.height = ViewGroup.LayoutParams.MATCH_PARENT;
                panel.setLayoutParams(raw);
            }
            if (radii != null) {
                navBackground(panel, radii, color);
            }
            panel.setVisibility(View.VISIBLE);
            // ★★★ 不要 bringToFront()。
            //   v3.7 第一版写了，结果侧栏跑到 fake_dialog_background 和
            //   dialog_offline_skin 之上 —— 弹窗被侧栏盖住一角；
            //   更要命的是它盖住了右上角的 qcl_back_bar（返回 / 回首页 / 关闭），
            //   二级页面直接「退不出去」。
            //   XML 里 view_side_panel 本来就排在 launcher_layout 之后、
            //   弹窗之前，层级天然正确，不需要再动。
        } catch (Throwable ignored) {
        }
    }

    /**
     * 二级页返回栏（{@code qcl_back_bar}）的位置。
     *
     * <p>★★★ 为什么必须按风格挪：FCL / ZL2 的**右侧操作面板**正好压在它原来的位置上，
     * HMCL 的**顶部彩条**也在同一行。不挪的话返回栏会被挡掉，
     * 用户在二级页面按不到「返回 / 回首页 / 关闭」—— 这是功能性倒退，不是观感问题。
     *
     * <p>策略：有右侧面板的风格把返回栏挪到**左侧**（贴着导航栏右边）；
     * 有顶部彩条的再往下让出彩条高度；QCL 原版维持右上角。
     *
     * <p>★ 最后 {@code bringToFront()}：保证它永远在面板 / 彩条之上。
     */
    private static void backBar(Activity a, boolean atStart, int marginStartPx, int marginTopPx) {
        try {
            View bar = a.findViewById(R.id.qcl_back_bar);
            if (bar == null) {
                return;
            }
            ViewGroup.LayoutParams raw = bar.getLayoutParams();
            if (!(raw instanceof RelativeLayout.LayoutParams)) {
                return;
            }
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) raw;
            lp.removeRule(RelativeLayout.ALIGN_PARENT_START);
            lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
            if (atStart) {
                lp.addRule(RelativeLayout.ALIGN_PARENT_START);
                lp.leftMargin = marginStartPx;
                lp.rightMargin = 0;
            } else {
                lp.addRule(RelativeLayout.ALIGN_PARENT_END);
                lp.rightMargin = dp(a, 8);
                lp.leftMargin = 0;
            }
            lp.topMargin = marginTopPx;
            bar.setLayoutParams(lp);
            bar.bringToFront();
        } catch (Throwable ignored) {
        }
    }

    // ───────────────── HMCL 顶部彩条 ─────────────────

    /**
     * 确保 HMCL 的顶部彩条存在（幂等）。
     *
     * <p>★ 为什么运行时建而不是写进 XML：只有 HMCL 这一套需要它。
     * 写进 {@code activity_main.xml} 的话，另外三套风格都得额外处理「多出来的一条」，
     * 而且 {@code main_ui_container} 的高度、安全区基准都会跟着变 ——
     * 一个只为 1/4 场景服务的东西，不该污染所有场景的布局。
     */
    private static void ensureHmclBar(Activity a, ViewGroup content) {
        // ★★★ 2.2.7：彩条挂到 **launcher_root**（RelativeLayout）上，不再挂在 content 里。
        //   2.2.5 挂在 content 里的后果：content 为了给 200dp 左菜单让位，设了
        //   paddingStart=200dp —— 而子 View 是长在 padding **里面**的，于是彩条
        //   从 x=200dp 才开始、右端又短 200dp，成了一条「没铺满的棕色带子」。
        //   挂到根上才能满宽（HMCL 的 Toolbar 本来就是通栏的）。
        ViewGroup host = content;
        android.view.ViewParent p = content.getParent();
        if (p instanceof ViewGroup) {
            host = (ViewGroup) p;
        }
        if (host.findViewWithTag(TAG_HMCL_BAR) != null) {
            return;
        }
        try {
            LinearLayout bar = new LinearLayout(a);
            bar.setTag(TAG_HMCL_BAR);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            // HMCL：background=@color/colorAccent，titleTextColor=@color/colorPureWhite
            bar.setBackgroundColor(0xFFB0552F);
            int padH = dp(a, 14);
            bar.setPadding(padH, 0, padH, 0);
            if (host instanceof RelativeLayout) {
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(a, HMCL_BAR_DP));
                lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                lp.addRule(RelativeLayout.ALIGN_PARENT_START);
                bar.setLayoutParams(lp);
            } else {
                bar.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(a, HMCL_BAR_DP)));
            }

            // 左边 18dp 的方块图标（HMCL 那里是合成台，这里用 QCL 自己的方块）
            ImageView icon = new ImageView(a);
            icon.setLayoutParams(new LinearLayout.LayoutParams(dp(a, 18), dp(a, 18)));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try {
                icon.setImageResource(R.drawable.ic_qcl_gamecube_white);
            } catch (Throwable ignored) {
            }
            bar.addView(icon);

            TextView title = new TextView(a);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.leftMargin = dp(a, 10);
            title.setLayoutParams(tlp);
            title.setText(R.string.app_name);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(16f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setSingleLine(true);
            bar.addView(title);

            // 右侧弹性空间（HMCL 那里放的是窗口按钮，Android 上没有对应物）
            View spacer = new View(a);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
            bar.addView(spacer);

            // ★ 插在**第 1 个**位置：第 0 个是 launcher_layout（它有不透明壁纸背景，
            //   彩条放它后面会被整块盖住）；再往后就是返回栏 / 导航栏 / 弹窗遮罩，
            //   彩条如果 append 到最后会盖住弹窗顶部。第 1 位正好在两者之间。
            int index = host.getChildCount() >= 1 ? 1 : 0;
            host.addView(bar, index, bar.getLayoutParams());
            Log.i(TAG, "HMCL 顶部彩条已挂载（host=" + host.getClass().getSimpleName()
                    + " index=" + index + "）");
        } catch (Throwable t) {
            Log.w(TAG, "HMCL 顶部彩条挂载失败（其余排版仍然生效）", t);
        }
    }

    /** 移除 HMCL 顶部彩条（切到其它风格时）。 */
    private static void hideHmclBar(View content) {
        if (content == null) {
            return;
        }
        try {
            // ★ 2.2.7：彩条挂在 content 的父容器上，所以先从父容器找，找不到再退回 content。
            ViewGroup host = content instanceof ViewGroup ? (ViewGroup) content : null;
            android.view.ViewParent p = content.getParent();
            if (p instanceof ViewGroup) {
                host = (ViewGroup) p;
            }
            if (host == null) {
                return;
            }
            View bar = host.findViewWithTag(TAG_HMCL_BAR);
            if (bar != null) {
                host.removeView(bar);
            }
        } catch (Throwable ignored) {
        }
    }

    // ───────────────── 小工具 ─────────────────

    private static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    private static int screenW(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels;
    }

    /** 屏幕高度（像素）。横屏时这里会明显小于宽度 —— 侧栏要不要精简就看它。 */
    private static int screenH(Context c) {
        return c.getResources().getDisplayMetrics().heightPixels;
    }
}
