package com.qcl.launcher.launcher.uis.tools;

import android.view.View;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;

/**
 * ★ 社区版新增：底部导航条控制器（固定五个一级入口）。
 *
 * <p><b>为什么单独抽一个类</b>：底部导航的显隐、选中态、内容区留白是三件容易互相打架的事，
 * 散在 MainActivity 里会越改越乱。这里收口成一个控制器，MainActivity 只负责在
 * {@code showBarTitle()} / {@code hideBarTitle()} 时调一次 {@link #syncWithCurrentUI()}。
 *
 * <p><b>★ 显隐判定不靠"谁调了哪个方法"，而是靠"当前页面是不是五个一级页之一"</b>：
 * 原先想用 {@code hideBarTitle()} 当"回到首页"的信号，但二级页面（版本列表 / 下载 / 设置）
 * 同样会调 {@code showBarTitle()} —— 而它们本身就是一级 Tab，那样会被误判成二级页而藏掉导航条。
 * 所以改成用 {@code uiManager.currentUI} 的身份反查 Tab，语义唯一、不会误判。
 *
 * <p><b>★ 内容区留白</b>：导航条是悬浮在根布局上的（不进 launcher_layout 的垂直排版），
 * 所以必须给 launcher_layout 补一段底部内边距，否则会盖住首页的启动按钮、
 * 以及下载/版本列表最后一条。隐藏时还原原始内边距，保证与改造前完全一致。
 */
public class BottomNavHelper implements View.OnClickListener {

    /** 当前不在任何一个一级 Tab 上（二级页面 / 弹窗态） */
    public static final int TAB_NONE = -1;
    public static final int TAB_HOME = 0;
    public static final int TAB_INSTANCE = 1;
    public static final int TAB_DOWNLOAD = 2;
    public static final int TAB_PLUGIN = 3;
    public static final int TAB_MINE = 4;

    private final MainActivity activity;
    private final View navBar;
    private final View searchFab;
    private final View[] tabs = new View[5];

    /** 导航条**宽度**对应的像素值，用于给内容区让位（2.x 起导航在左侧竖排） */
    private final int navWidthPx;

    /** 改造前 launcher_layout 的原始内边距（隐藏时原样还原，绝不改变旧观感） */
    private int origPaddingLeft;
    private int origPaddingTop;
    private int origPaddingRight;
    private int origPaddingBottom;
    private boolean origCaptured = false;

    private int selectedTab = TAB_NONE;
    private boolean visible = false;

    public BottomNavHelper(MainActivity activity) {
        this.activity = activity;
        this.navBar = activity.findViewById(R.id.qcl_bottom_nav);
        this.searchFab = activity.findViewById(R.id.qcl_search_fab);
        this.tabs[TAB_HOME] = activity.findViewById(R.id.nav_tab_home);
        this.tabs[TAB_INSTANCE] = activity.findViewById(R.id.nav_tab_instance);
        this.tabs[TAB_DOWNLOAD] = activity.findViewById(R.id.nav_tab_download);
        this.tabs[TAB_PLUGIN] = activity.findViewById(R.id.nav_tab_plugin);
        this.tabs[TAB_MINE] = activity.findViewById(R.id.nav_tab_mine);
        this.navWidthPx = Math.round(76f * activity.getResources().getDisplayMetrics().density);
        for (View tab : tabs) {
            if (tab != null) {
                tab.setOnClickListener(this);
            }
        }
        if (searchFab != null) {
            searchFab.setOnClickListener(this);
        }
    }

    /** 五个一级页面分别是哪个 Tab；不是一级页面返回 {@link #TAB_NONE} */
    public int tabOfCurrentUI() {
        try {
            UIManager m = activity.uiManager;
            if (m == null || m.currentUI == null) {
                return TAB_NONE;
            }
            if (m.currentUI == m.mainUI) return TAB_HOME;
            if (m.currentUI == m.versionListUI) return TAB_INSTANCE;
            if (m.currentUI == m.downloadUI) return TAB_DOWNLOAD;
            if (m.currentUI == m.pluginUI) return TAB_PLUGIN;
            if (m.currentUI == m.settingUI) return TAB_MINE;
        } catch (Throwable ignored) {
        }
        return TAB_NONE;
    }

    /**
     * ★ 页面切换后由 MainActivity 调用一次：按当前页面身份决定显示还是隐藏。
     * 一级页面 → 显示导航条并高亮对应 Tab；二级页面 → 整条藏起来（含搜索按钮）。
     */
    /**
     * 按当前页面同步导航条的显隐与选中态。
     *
     * <p>★★★ 3.0：**子页面不再隐藏导航条**。
     *
     * <p>为什么改：`launcher_layout` 的左边距现在是**写死的 76dp**（见 activity_main.xml ——
     * 那是为了解决「BottomNavHelper 与 SafeAreaHelper 抢着写 padding」的问题）。
     * 既然左边距是常驻的，导航条一旦在子页面隐藏，那 76dp 就成了一条**空白带** ——
     * 子页面左边空一块、直接露出背景图，看起来像坏了。
     *
     * <p>保持常驻也更符合参考图：侧栏本来就是界面的一部分，不是「主界面专属」。
     * 子页面上保留**上次选中的 Tab** 高亮（不强行清空），用户能看出自己在哪个大类下。
     */
    public void syncWithCurrentUI() {
        int tab = tabOfCurrentUI();
        if (tab == TAB_NONE) {
            // 子页面：导航条保持可见，沿用上次选中的 Tab
            show(selectedTab == TAB_NONE ? TAB_HOME : selectedTab);
            // ★ 但**搜索悬浮按钮照旧隐藏** —— 它浮在右下角，
            //   子页面（版本列表 / 下载列表 / 设置）底部都有内容，会挡住。
            //   这个行为是原设计就有的，不要一起改掉。
            if (searchFab != null) {
                searchFab.setVisibility(View.GONE);
            }
        } else {
            show(tab);
        }
    }

    public void show(int tab) {
        if (navBar != null) {
            navBar.setVisibility(View.VISIBLE);
            navBar.bringToFront();
        }
        if (searchFab != null) {
            searchFab.setVisibility(View.VISIBLE);
            searchFab.bringToFront();
        }
        selectedTab = tab;
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != null) {
                tabs[i].setSelected(i == tab);
            }
        }
        applyContentInset(true);
        visible = true;
    }

    public void hide() {
        if (navBar != null) {
            navBar.setVisibility(View.GONE);
        }
        if (searchFab != null) {
            searchFab.setVisibility(View.GONE);
        }
        selectedTab = TAB_NONE;
        applyContentInset(false);
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public int getSelectedTab() {
        return selectedTab;
    }

    /**
     * 给 launcher_layout 让出导航条的**宽度**；隐藏时还原。
     *
     * <p>★★★ 2.x：导航从「底部横排」改成「左侧竖排」后，这里必须跟着改 ——
     * 原来是把 {@code navHeightPx}(56dp) 加到**底部**内边距，
     * 导航一旦搬到左边，那段底部留白就成了一条**多余的空带**，
     * 而左边反而没有给导航让位（内容会被导航条压住）。
     * 现在改成把导航**宽度**加到**左侧**内边距。
     *
     * <p>★ 注意：{@code launcher_layout} 的 XML 里**不要**再写 paddingStart ——
     * 这里会自己加。两处都写就是双倍留白（内容被推到屏幕中间）。
     */
    private void applyContentInset(boolean inset) {
        // ★★★ 3.0：这里**故意什么都不做**。
        //
        // 为什么：`launcher_layout` 的 padding 有**两个**组件在写 ——
        //   ① 本类（给导航条让位）
        //   ② `ui/SafeAreaHelper`（给刘海 / 挖孔 / 圆角让位）
        // 而 SafeAreaHelper 是**后**跑的，它用安装时捕获的 basePadLeft 重设 padding，
        // 会把本类刚加上的 76dp **整个覆盖掉**。真机表现就是：
        //   「导航条压住了启动按钮和左上角那个按钮」—— 内容根本没让位。
        //
        // 改法：左边距**写死在 XML**（`activity_main.xml` 里 launcher_layout 的
        // `android:paddingStart="76dp"`），SafeAreaHelper 会基于它**累加**安全区，
        // 两边不再互相覆盖。本类从此不碰 padding。
        //
        // ⚠️ 不要再把这里改回「运行时加 padding」—— 会和 SafeAreaHelper 打架。
    }

    @Override
    public void onClick(View v) {
        if (v == searchFab) {
            com.qcl.launcher.launcher.dialogs.GlobalSearchDialog.show(activity);
            return;
        }
        int tab = TAB_NONE;
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != null && v == tabs[i]) {
                tab = i;
                break;
            }
        }
        if (tab == TAB_NONE || activity.uiManager == null) {
            return;
        }
        // 重复点同一个 Tab：不重复切换，避免把返回栈洗掉（用户预期是"回到这个 Tab 的首页"）
        if (tab == selectedTab) {
            return;
        }
        BaseUI target = pageOf(tab);
        if (target == null) {
            return;
        }
        // ★ 切 Tab 走"清栈再进"：否则从下载页点进二级页、再点另一个 Tab，
        //   返回栈里会残留上一个 Tab 的页面，按返回键会跳到不相干的界面。
        activity.uiManager.switchMainUITab(target);
        show(tab);
    }

    private BaseUI pageOf(int tab) {
        UIManager m = activity.uiManager;
        switch (tab) {
            case TAB_HOME:     return m.mainUI;
            case TAB_INSTANCE: return m.versionListUI;
            case TAB_DOWNLOAD: return m.downloadUI;
            case TAB_PLUGIN:   return m.pluginUI;
            case TAB_MINE:     return m.settingUI;
            default:           return null;
        }
    }
}
