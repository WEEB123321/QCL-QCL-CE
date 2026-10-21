package com.qcl.launcher.launcher.ui;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;

/**
 * ★★★ 排版风格（Layout Style）—— 四套预设，运行时切换，不用改布局文件。
 *
 * <h3>为什么用「运行时改 LayoutParams」而不是做四套布局</h3>
 * 主界面有 36 个 {@code include}、150+ 个布局文件。做四套 = 维护 600 份布局，
 * 任何一处改动都要同步四次 —— 这个项目已经因为「要记得改多处」翻车过三次
 * （签名、版本号、docs）。而三种风格的**结构差异其实很小**：
 * 导航栏在哪一侧、多宽、圆角多大、内容留多少边距。
 * 这些全是 {@code LayoutParams} 能表达的，所以这里只切参数，不复制布局。
 *
 * <h3>四套预设</h3>
 * <table border="1">
 *   <tr><th>风格</th><th>导航位置</th><th>尺寸</th><th>圆角</th><th>品牌区</th><th>观感</th></tr>
 *   <tr><td>{@link #STYLE_QCL}</td><td>左侧</td><td>76dp 满高</td><td>18dp</td><td>显示</td><td>QCL 原版（默认）</td></tr>
 *   <tr><td>{@link #STYLE_FCL}</td><td>底部</td><td>62dp 满宽</td><td>22dp</td><td>隐藏</td><td>FCL：底部横排、大圆角</td></tr>
 *   <tr><td>{@link #STYLE_HMCL}</td><td>左侧</td><td>62dp 满高</td><td>10dp</td><td>隐藏</td><td>HMCL 电脑端：窄侧栏、紧凑</td></tr>
 *   <tr><td>{@link #STYLE_ZL2}</td><td>底部</td><td>68dp 满宽</td><td>26dp</td><td>隐藏</td><td>ZL2：底部、更圆的 Material 观感</td></tr>
 * </table>
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
    /** FCL 风格：底部横排导航，大圆角。 */
    public static final int STYLE_FCL = 1;
    /** HMCL 电脑端风格：左侧窄侧栏，紧凑，小圆角。 */
    public static final int STYLE_HMCL = 2;
    /** ZL2 风格：底部导航，更大的圆角，Material 观感。 */
    public static final int STYLE_ZL2 = 3;

    private LauncherLayoutStyle() {
    }

    public static String nameOf(int style) {
        switch (style) {
            case STYLE_FCL:  return "FCL";
            case STYLE_HMCL: return "HMCL";
            case STYLE_ZL2:  return "ZL2";
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
        try {
            View nav = activity.findViewById(R.id.qcl_bottom_nav);
            View content = activity.findViewById(R.id.launcher_layout);
            View fab = activity.findViewById(R.id.qcl_search_fab);
            View root = activity.findViewById(R.id.launcher_root);
            if (nav == null || content == null) {
                return;
            }

            boolean bottom = isBottom(style);
            int navDp = navSizeDp(style);
            int radiusDp = navRadiusDp(style);
            float d = activity.getResources().getDisplayMetrics().density;
            int navPx = (int) (navDp * d + 0.5f);

            applyNavLayout(nav, bottom, navPx);
            applyNavOrientation(nav, bottom);
            applyNavBackground(nav, radiusDp, bottom);
            setBrandVisible(nav, style == STYLE_QCL);

            // 内容区留出导航占的位置
            //
            // ★★★ 必须写**绝对值**（top / right 也归零），不能保留当前值。
            //   原因：SafeAreaHelper 把「安装时的 padding」当作基准，之后每次
            //   inset 回调都在基准上加安全区。如果这里保留当前 padding（它已经含安全区），
            //   紧接着重装 SafeAreaHelper 就会把「含安全区的值」当成新基准
            //   ⇒ 下一次回调再加一遍 ⇒ padding 越来越大，界面被越挤越窄。
            //   布局里 launcher_layout 的原始 padding 就是 start=76dp，其余为 0。
            if (content instanceof View) {
                int padStart = bottom ? 0 : navPx;
                int padBottom = bottom ? navPx : 0;
                content.setPadding(padStart, 0, 0, padBottom);
            }

            // 搜索悬浮球：底部导航时抬高，否则会被导航压住
            if (fab != null) {
                ViewGroup.LayoutParams flp = fab.getLayoutParams();
                if (flp instanceof RelativeLayout.LayoutParams) {
                    RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) flp;
                    rlp.bottomMargin = bottom ? navPx + (int) (8 * d) : (int) (66 * d);
                    fab.setLayoutParams(rlp);
                }
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

    private static boolean isBottom(int style) {
        return style == STYLE_FCL || style == STYLE_ZL2;
    }

    private static int navSizeDp(int style) {
        switch (style) {
            case STYLE_FCL:  return 62;
            case STYLE_HMCL: return 62;
            case STYLE_ZL2:  return 68;
            default:         return 76;
        }
    }

    private static int navRadiusDp(int style) {
        switch (style) {
            case STYLE_FCL:  return 22;
            case STYLE_HMCL: return 10;
            case STYLE_ZL2:  return 26;
            default:         return 18;
        }
    }

    /**
     * 导航栏在父容器（{@code launcher_root}，RelativeLayout）里的位置与尺寸。
     *
     * <p>★ 先 {@code removeRule} 再 {@code addRule}：RelativeLayout 的规则是「集合」语义，
     * 只 add 不 remove 的话 ALIGN_PARENT_START 和 ALIGN_PARENT_BOTTOM 会同时生效 ——
     * 竖排变横排后，左对齐 + 底对齐会让导航条缩在左下角而不是铺满底部。
     */
    private static void applyNavLayout(View nav, boolean bottom, int navPx) {
        ViewGroup.LayoutParams raw = nav.getLayoutParams();
        if (!(raw instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) raw;
        lp.removeRule(RelativeLayout.ALIGN_PARENT_START);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.removeRule(RelativeLayout.CENTER_VERTICAL);

        // ★★★ 边距必须归零（写绝对值，不能保留）。
        //   原因与下面的 content padding 完全同源：SafeAreaHelper 把「安装时的
        //   bottomMargin」当基准，之后每次 inset 回调都在基准上加安全区。
        //   如果这里保留上一轮已经加过安全区的 margin，紧接着重装 SafeAreaHelper
        //   就会把它当成新基准 ⇒ 每切一次风格，导航条就往上飘一段。
        lp.topMargin = 0;
        lp.bottomMargin = 0;
        lp.leftMargin = 0;
        lp.rightMargin = 0;

        if (bottom) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = navPx;
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
        } else {
            lp.width = navPx;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        }
        nav.setLayoutParams(lp);
    }

    /**
     * 导航栏自身的方向 + 每个 Tab 的方向。
     *
     * <p>★ Tab 原来是 {@code width=match_parent, height=0dp, weight=1}（竖排均分高度）；
     * 横排必须改成 {@code width=0dp, weight=1, height=match_parent}（横向均分宽度）。
     * 不改的话横排下每个 Tab 都会撑满整宽，只看得见第一个。
     */
    private static void applyNavOrientation(View nav, boolean bottom) {
        if (!(nav instanceof LinearLayout)) {
            return;
        }
        LinearLayout ll = (LinearLayout) nav;
        ll.setOrientation(bottom ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        // ★ 底部导航要**居中**（水平+垂直），左侧竖栏只要垂直居中
        ll.setGravity(bottom ? android.view.Gravity.CENTER
                             : android.view.Gravity.CENTER_VERTICAL);

        // ★★★ padding 必须跟着方向改。
        //   XML 里写的是 top/bottom = 14dp（给竖栏留呼吸空间）。
        //   底栏只有 62dp 高，上下各 14dp 就把 28dp 吃掉了 —— 剩下 34dp 放不下
        //   「罗马数字 + 中文」两行字，Tab 会被压扁或裁掉。
        //   底栏改成上下 6dp、左右 4dp（横向空间靠 weight 均分，不需要大 padding）。
        float d = nav.getResources().getDisplayMetrics().density;
        if (bottom) {
            int v = (int) (6 * d + 0.5f);
            int h = (int) (4 * d + 0.5f);
            ll.setPadding(h, v, h, v);
        } else {
            int v = (int) (14 * d + 0.5f);
            int h = (int) (6 * d + 0.5f);
            ll.setPadding(h, v, h, v);
        }

        int[] tabIds = {R.id.nav_tab_home, R.id.nav_tab_instance,
                R.id.nav_tab_download, R.id.nav_tab_plugin, R.id.nav_tab_mine};
        for (int id : tabIds) {
            View tab = nav.findViewById(id);
            if (tab == null) {
                continue;
            }
            ViewGroup.LayoutParams raw = tab.getLayoutParams();
            if (!(raw instanceof LinearLayout.LayoutParams)) {
                continue;
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
            lp.weight = 1f;
            if (bottom) {
                lp.width = 0;
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            } else {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = 0;
            }
            lp.topMargin = 0;
            lp.bottomMargin = 0;
            lp.leftMargin = 0;
            lp.rightMargin = 0;
            tab.setLayoutParams(lp);
            // ★ Tab 内部是「罗马数字 + 中文」横排，两个方向都不用改内部结构；
            //   底栏宽度约 70dp（5 等分），罗马数字 + 两字中文刚好放得下。
        }
    }

    /**
     * 导航栏背景：动态生成圆角矩形，替换 XML 里写死的 {@code qcl_bottom_nav_bg}。
     *
     * <p>★ 为什么不用 XML drawable：三套风格要三种圆角，而 {@code GradientDrawable}
     * 的 corner 半径在 XML 里是常量，运行时改不了。动态生成一次即可，
     * 顺便把「左侧栏要四角圆、底栏只圆上方两角」的差异一并处理。
     */
    private static void applyNavBackground(View nav, int radiusDp, boolean bottom) {
        try {
            float d = nav.getResources().getDisplayMetrics().density;
            int r = (int) (radiusDp * d + 0.5f);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xD9FFFFFF);
            // ★ 两种方向都只圆**上方两角**：
            //   底栏贴屏幕底，圆下角会露出背景；左侧竖栏同理（底边贴屏幕底）。
            //   这与 XML 里 qcl_bottom_nav_bg 的原设计一致，不会因为切风格而改变观感。
            g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            nav.setBackground(g);
        } catch (Throwable ignored) {
        }
    }

    /** 品牌区（方框 + 字母 + 小字）只在左侧栏放得下；底部导航没有它的位置。 */
    private static void setBrandVisible(View nav, boolean visible) {
        try {
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
}
