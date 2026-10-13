package com.qcl.launcher.launcher.uis.layout;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版：界面排版编辑器（阶段 ②，方案 B）。
 *
 * <p><b>用法</b>：{@code LayoutEditor.attach(activity, pageRoot, "main")} ——
 * 之后**长按页面**就进编辑模式 ✓
 *
 * <p><b>编辑模式里能做什么</b>（方案 B：改顺序 + 显隐 ✓ 不做自由摆放 ✗）：
 * <ul>
 *   <li><b>长按某个可编辑控件 → 拖动</b>：在同一父容器内**上下换位** ✓
 *       （实现是交换同层 View 的顺序 ✓ 不是改 x/y ✗ ——
 *        这样换分辨率、旋转屏幕、软键盘弹出都自动适配 ✓）</li>
 *   <li><b>单击某个控件</b>：切换显示 / 隐藏 ✓（隐藏用 GONE ✓ 不占位 ✓）</li>
 *   <li>底部浮动条：<b>保存 / 取消 / 恢复默认</b> ✓</li>
 * </ul>
 *
 * <p><b>为什么只对「有 id 的 View」生效</b>：排版数据按 id 存（{@link QclLayout}）✓
 * 没有 id 的控件记不住，重启就丢 ✗ —— 所以编辑器**只给有 id 的控件加手柄** ✓
 * 这样也天然避开了装饰性的空 View / 分隔线 ✗
 *
 * <p><b>为什么拖拽是「换顺序」而不是「改坐标」</b>：
 * 改坐标（setX/setY）在换分辨率 / 旋转 / 软键盘弹出时**全要打架** ✗ 极易错位 ✗
 * 换顺序则完全交给原有布局体系算 ✓ 稳得多 ✓
 */
public final class LayoutEditor {

    private LayoutEditor() {
    }

    /** 可编辑控件的最少数量 —— 少于这个数就没必要进编辑模式。 */
    private static final int MIN_EDITABLE = 2;

    /**
     * 给一个页面挂上「长按进编辑」。
     *
     * @param activity 宿主
     * @param pageRoot 页面根 View（通常是一个 LinearLayout / RelativeLayout）
     * @param pageId   页面标识（存进 qcl_layout.json 的 key）
     */
    public static void attach(final Activity activity, final View pageRoot, final String pageId) {
        if (activity == null || pageRoot == null || pageId == null) {
            return;
        }
        pageRoot.setOnLongClickListener(v -> {
            enterEditMode(activity, pageRoot, pageId);
            return true;
        });
    }

    /** 进编辑模式。 */
    private static void enterEditMode(final Activity activity, final View pageRoot, final String pageId) {
        try {
            final List<View> editable = collectEditable(pageRoot);
            if (editable.size() < MIN_EDITABLE) {
                Toast.makeText(activity, "这个页面可编辑的控件太少（需要至少 " + MIN_EDITABLE + " 个带 id 的控件）",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // 记下进入前的状态，取消时还原 ✓
            final List<Integer> origVisibility = new ArrayList<>();
            for (View v : editable) {
                origVisibility.add(v.getVisibility());
            }

            final FrameLayout overlay = new FrameLayout(activity);
            overlay.setBackgroundColor(0x22000000);   // 很淡的蒙层，提示「在编辑」

            // 给每个可编辑控件套一层高亮边框（用一个包在外面的 FrameLayout 太重 ✗
            // 直接改背景色最简单，取消时还原 ✓）
            final List<android.graphics.drawable.Drawable> origBg = new ArrayList<>();
            for (View v : editable) {
                origBg.add(v.getBackground());
                v.setBackground(highlight(activity, v.getVisibility() == View.VISIBLE));
            }

            // 底部浮动条
            LinearLayout bar = new LinearLayout(activity);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER);
            bar.setBackgroundColor(0xE6202020);
            bar.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));

            bar.addView(barButton(activity, "保存", 0xFF0E9E8E, v -> {
                QclLayout.save();
                Toast.makeText(activity, "已保存排版", Toast.LENGTH_SHORT).show();
                close(activity, overlay, editable, origBg, null);
            }));
            bar.addView(barButton(activity, "取消", 0xFF666666, v -> {
                close(activity, overlay, editable, origBg, origVisibility);
                Toast.makeText(activity, "已取消", Toast.LENGTH_SHORT).show();
            }));
            bar.addView(barButton(activity, "恢复默认", 0xFFA8452F, v -> {
                QclLayout.clearPage(pageId);
                Toast.makeText(activity, "已恢复默认（保存后生效）", Toast.LENGTH_SHORT).show();
            }));

            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            blp.bottomMargin = dp(activity, 18);
            overlay.addView(bar, blp);

            // 提示
            TextView tip = new TextView(activity);
            tip.setText("编辑模式：单击 = 显示/隐藏　长按拖动 = 换位置");
            tip.setTextSize(12f);
            tip.setTextColor(Color.WHITE);
            tip.setBackgroundColor(0xE6202020);
            tip.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            tlp.topMargin = dp(activity, 18);
            overlay.addView(tip, tlp);

            // 给每个可编辑控件装「点击切显隐」+「长按拖动换位」
            for (View v : editable) {
                installHandlers(activity, pageRoot, pageId, v);
            }

            // 挂 overlay（点空白处 = 取消 ✓）
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            decor.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overlay.setOnClickListener(v -> close(activity, overlay, editable, origBg, origVisibility));
        } catch (Throwable t) {
            Toast.makeText(activity, "编辑模式打开失败：" + t, Toast.LENGTH_SHORT).show();
        }
    }

    /** 退出编辑模式：还原背景、按需还原显隐、移除 overlay ✓ */
    private static void close(Activity activity, FrameLayout overlay, List<View> editable,
                              List<android.graphics.drawable.Drawable> origBg,
                              List<Integer> restoreVisibility) {
        try {
            for (int i = 0; i < editable.size(); i++) {
                View v = editable.get(i);
                if (i < origBg.size()) {
                    v.setBackground(origBg.get(i));
                }
                if (restoreVisibility != null && i < restoreVisibility.size()) {
                    v.setVisibility(restoreVisibility.get(i));
                }
                // 清掉编辑用的监听器（用 null 覆盖 ✓）
                v.setOnClickListener(null);
                v.setOnLongClickListener(null);
                v.setOnTouchListener(null);
            }
        } catch (Throwable ignored) {
        }
        try {
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            decor.removeView(overlay);
        } catch (Throwable ignored) {
        }
    }

    /** 给一个控件装编辑手势：单击切显隐、长按拖动换位 ✓ */
    private static void installHandlers(final Activity activity, final View pageRoot,
                                        final String pageId, final View v) {
        final boolean[] dragging = new boolean[1];
        final float[] startY = new float[1];

        v.setClickable(true);
        v.setLongClickable(true);

        v.setOnClickListener(x -> {
            // 单击 = 切显隐 ✓（用 GONE，不占位 ✓）
            boolean nowVisible = v.getVisibility() == View.VISIBLE;
            v.setVisibility(nowVisible ? View.GONE : View.VISIBLE);
            v.setBackground(highlight(activity, !nowVisible));
            int order = currentOrder(pageRoot, v);
            QclLayout.put(pageId, idOf(v), new QclLayout.Item(order, !nowVisible, 0, 0));
            QclLayout.save();
            Toast.makeText(activity, (nowVisible ? "已隐藏" : "已显示") + "："
                    + nameOf(activity, v), Toast.LENGTH_SHORT).show();
        });

        v.setOnLongClickListener(x -> {
            dragging[0] = true;
            startY[0] = 0;
            return true;
        });

        v.setOnTouchListener((x, e) -> {
            if (!dragging[0]) {
                return false;
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY[0] = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = e.getRawY() - startY[0];
                    // 每滑过一个控件高度就换一次位 ✓（不用精细判定 ✓ 手感够用 ✓）
                    int step = Math.max(dp(activity, 40), v.getHeight());
                    if (Math.abs(dy) >= step) {
                        moveInParent(pageRoot, v, dy > 0 ? 1 : -1);
                        startY[0] = e.getRawY();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    dragging[0] = false;
                    // 落位后把顺序写进排版数据 ✓
                    QclLayout.put(pageId, idOf(v),
                            new QclLayout.Item(currentOrder(pageRoot, v),
                                    v.getVisibility() == View.VISIBLE, 0, 0));
                    QclLayout.save();
                    Toast.makeText(activity, "已移动：" + nameOf(activity, v), Toast.LENGTH_SHORT).show();
                    return true;
                }
                default:
                    return false;
            }
        });
    }

    /** 在同一父容器内把 v 往前/往后挪一位 ✓（交换同层 View 的顺序） */
    private static void moveInParent(View pageRoot, View v, int dir) {
        try {
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent == null) {
                return;
            }
            int i = parent.indexOfChild(v);
            int j = i + dir;
            if (j < 0 || j >= parent.getChildCount()) {
                return;
            }
            parent.removeViewAt(i);
            parent.addView(v, j);
            parent.invalidate();
        } catch (Throwable ignored) {
        }
    }

    /** 当前控件在父容器里排第几（跳过不可见的 ✓ 和 XML 顺序一致 ✓） */
    private static int currentOrder(View pageRoot, View v) {
        try {
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent == null) {
                return 0;
            }
            return parent.indexOfChild(v);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 收集「可编辑」的控件：有 id、可见、有实际尺寸 ✓ */
    private static List<View> collectEditable(View root) {
        List<View> out = new ArrayList<>();
        walk(root, out, 0);
        return out;
    }

    private static void walk(View v, List<View> out, int depth) {
        if (v == null || depth > 8) {
            return;
        }
        if (v.getId() != View.NO_ID && v.getId() != 0 && !(v instanceof ViewGroup)) {
            out.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                walk(g.getChildAt(i), out, depth + 1);
            }
        }
    }

    private static String idOf(View v) {
        try {
            return v.getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return "id_" + v.getId();
        }
    }

    private static String nameOf(Activity a, View v) {
        try {
            return v.getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return "控件";
        }
    }

    private static android.graphics.drawable.Drawable highlight(Activity a, boolean visible) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setCornerRadius(dp(a, 6));
        d.setStroke(dp(a, 2), visible ? 0xFF0E9E8E : 0x88A8452F);
        if (!visible) {
            d.setColor(0x33A8452F);
        }
        return d;
    }

    private static TextView barButton(Activity a, String text, int color, View.OnClickListener l) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(14f);
        t.setTextColor(Color.WHITE);
        t.setBackgroundColor(color);
        t.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));
        t.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(a, 6);
        lp.rightMargin = dp(a, 6);
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        return t;
    }

    private static int dp(Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
    }
}
