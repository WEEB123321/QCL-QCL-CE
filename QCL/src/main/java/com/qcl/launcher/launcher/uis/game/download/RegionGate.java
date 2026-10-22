package com.qcl.launcher.launcher.uis.game.download;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.R;

/**
 * ★★★ 2.0.12 地区选择（「你是哪一个国家的人」）。
 *
 * <p>动机：2026-10-20 PCL 2.99.0.0 公告「为遵守相关法律法规，暂不支持提供 MC 下载」，
 * HMCL 也传出同样的说法。MC 原版文件的获取由此变成一个**分地区**的问题。
 * 于是下载页进入时先问一次地区，回答之后才放行页面。
 *
 * <h3>三个必须写清楚的设计决定</h3>
 * <ol>
 *   <li><b>为什么存在 SharedPreferences 而不是 launcher_setting.json</b>
 *       —— launcher_setting 是「可同步/可备份」的用户配置，地区是合规判定依据，
 *       两者混在一起，用户一句「恢复默认设置」就会把合规判定一并重置。
 *       分开存，语义清楚，也不会被别的模块顺手覆盖。</li>
 *   <li><b>为什么「确定」要连点 5 次、且前 4 次会跑</b>
 *       —— 用户明确要求：「确定就是乱跑乱跑 5 次之后就能点了」。
 *       实现成「第 1~4 次点中的那一刻就换位置」，第 5 次点中才落地。
 *       ★ 计数器用**点中**而不是**点击**来累加：点空不算，这样跑位才有意义；
 *       同时每次跑位都把计数清零，否则乱戳空气也能「凑够 5 次」，玩笑就没意思了。</li>
 *   <li><b>为什么警告用 20sp</b> —— 普通提示 13sp。字号差本身就是语义：
 *       这一条不是提示，是「你得看清楚的限制」。</li>
 * </ol>
 *
 * <p>★ 一处 XML（{@code ui_region_gate.xml}）三处复用：下载页覆盖层、设置页对话框、
 * 「去设置修改」跳转。样式只有一份，不会改一处忘一处。
 */
public final class RegionGate {

    /** 用户明确要求的解锁次数。 */
    private static final int TAPS_TO_UNLOCK = 5;

    /**
     * ★★★ 为什么单独一个 SharedPreferences 文件：
     * 合规判定不应该和「恢复默认设置」共命运。见类注释 ①。
     */
    public static final String PREF = "qcl_region";

    /** 键名。取值见 {@link #REGION_CN} / {@link #REGION_OTHER}。 */
    public static final String KEY_REGION = "region";

    /** 中国大陆：原版 MC 下载被限制。 */
    public static final String REGION_CN = "cn";

    /** 其它地区：不限制。 */
    public static final String REGION_OTHER = "other";

    private RegionGate() {
    }

    // ───────────────────────── 读写 ─────────────────────────

    /** 读已保存的地区；没选过返回 {@code null}（注意不是空串，调用方要判 null）。 */
    public static String getRegion(Context ctx) {
        if (ctx == null) {
            return null;
        }
        try {
            String v = ctx.getSharedPreferences(PREF, 0).getString(KEY_REGION, null);
            if (REGION_CN.equals(v) || REGION_OTHER.equals(v)) {
                return v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 用户是否已经回答过地区问题。没回答过就要弹选择层。 */
    public static boolean hasChosen(Context ctx) {
        return getRegion(ctx) != null;
    }

    /** 当前是否处于「原版 MC 下载受限」状态。没选过地区时按**未限制**处理。 */
    public static boolean isDownloadRestricted(Context ctx) {
        return REGION_CN.equals(getRegion(ctx));
    }

    public static void setRegion(Context ctx, String region) {
        if (ctx == null || region == null) {
            return;
        }
        try {
            ctx.getSharedPreferences(PREF, 0).edit().putString(KEY_REGION, region).apply();
        } catch (Throwable ignored) {
        }
    }

    // ───────────────────────── 覆盖层（下载页） ─────────────────────────

    /**
     * 把 {@code ui_download.xml} 里那段 include 出来的地区层接管过来。
     *
     * <p>★ 必须在 {@code LayoutEditor.apply/attach} **之后**调 —— apply 会把带 id 的子 View
     * 摘下来再插回去，先挂监听会被搬乱。这里靠「每次显示前重新 bind」来保证绑定的是
     * 当前真正在树上的那批 View。
     *
     * @param gate        include 出来的根 FrameLayout（{@code R.id.download_region_gate}）
     * @param onCommitted 地区确认之后的回调（用来放行页面 / 刷新受限提示）
     */
    public static void bindOverlay(final Activity activity, final View gate,
                                   final Runnable onCommitted) {
        if (activity == null || gate == null) {
            return;
        }
        // 已经选过就不该看到这一层
        if (hasChosen(activity)) {
            gate.setVisibility(View.GONE);
            return;
        }
        // 盖住整页 = 底下的 tab 与列表一个都点不到（根 FrameLayout 是 match_parent，
        // 加上 clickable=true，触摸事件到不了下层）
        gate.setVisibility(View.VISIBLE);
        attachGateLogic(activity, gate, new Runnable() {
            @Override
            public void run() {
                gate.setVisibility(View.GONE);
                if (onCommitted != null) {
                    onCommitted.run();
                }
            }
        });
    }

    // ───────────────────────── 对话框（设置页 / 「去设置修改」） ─────────────────────────

    /**
     * 以对话框形式重选地区。设置页用这个；「下载已被限制」的提示也用这个跳过来。
     *
     * @param onDone 选完之后回调（参数是新地区），取消则不回调
     */
    public static void showDialog(final Activity activity, final String titleOverride,
                                  final OnRegionPicked onDone) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        View content = LayoutInflater.from(activity).inflate(R.layout.ui_region_gate, null);
        // 对话框里不需要那层 opaque 遮罩（对话框本身已经是浮层），换成浅色面板
        content.setBackgroundResource(R.drawable.launcher_view_light_gray);
        if (titleOverride != null) {
            TextView t = (TextView) content.findViewById(R.id.region_gate_title);
            if (t != null) {
                t.setText(titleOverride);
            }
        }
        // ★ 对话框是「重选」，不是首次选择：把已保存的值预选上，用户能看出当前是什么
        final String current = getRegion(activity);
        if (current != null) {
            View cn = content.findViewById(R.id.region_gate_cn);
            View other = content.findViewById(R.id.region_gate_other);
            markSelected(content, cn, other, REGION_CN.equals(current));
            showWarnIfCn(content, REGION_CN.equals(current));
        }

        final boolean[] committed = new boolean[1];
        DialogInterface.OnDismissListener dismiss = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                // 没确认就关掉 = 保持原状，不回调
            }
        };
        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity)
                .setView(content)
                .setOnDismissListener(dismiss)
                .create();
        attachGateLogic(activity, content, new Runnable() {
            @Override
            public void run() {
                committed[0] = true;
                dialog.dismiss();
                if (onDone != null) {
                    onDone.onPicked(getRegion(activity));
                }
            }
        });
        dialog.show();
    }

    /** 选完地区的回调。 */
    public interface OnRegionPicked {
        void onPicked(String region);
    }

    // ───────────────────────── 「受限」提示 ─────────────────────────

    /**
     * 中国大陆用户点「下载游戏」时给的解释。
     *
     * <p>★ 不做假选项：这里**不提供**「仍然下载」的按钮。用户选了中国大陆，
     * 就是接受了这条限制；再给个绕过入口等于把选择作废。
     */
    public static void showRestrictedDialog(final Activity activity, final Runnable goSetting) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.region_gate_locked_title)
                .setMessage(R.string.region_gate_locked_msg)
                .setPositiveButton(R.string.region_gate_locked_go_setting,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int i) {
                                if (goSetting != null) {
                                    goSetting.run();
                                }
                            }
                        })
                .setNegativeButton(R.string.legacy_cn_lang_cancel, null)
                .show();
    }

    // ───────────────────────── 内部：绑定那一整套交互 ─────────────────────────

    /**
     * 把「选地区 + 警告显示 + 5 次解锁」挂到一份已经 inflate 好的布局上。
     *
     * @param onCommitted 第 5 次真正点中之后执行
     */
    private static void attachGateLogic(final Activity activity, final View root,
                                        final Runnable onCommitted) {
        final View cn = root.findViewById(R.id.region_gate_cn);
        final View other = root.findViewById(R.id.region_gate_other);
        final TextView confirm = (TextView) root.findViewById(R.id.region_gate_confirm);
        if (cn == null || other == null || confirm == null) {
            return;
        }

        // 每次绑定都从干净的初始态开始（对话框复用同一份布局，不能带着上次的选择）
        final String[] picked = new String[]{getRegion(activity)};
        final int[] taps = new int[]{0};

        View.OnClickListener pick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                picked[0] = (v == cn) ? REGION_CN : REGION_OTHER;
                markSelected(root, cn, other, v == cn);
                showWarnIfCn(root, REGION_CN.equals(picked[0]));
                // 换选之后解锁进度作废 —— 不能「在中国大陆上戳 4 次、切到其它地区再补 1 次」
                taps[0] = 0;
                refreshConfirm(confirm, 0);
            }
        };
        cn.setOnClickListener(pick);
        other.setOnClickListener(pick);

        // 对话框场景已经预选过，这里再刷一次按钮文案，避免首屏是空白
        if (picked[0] != null) {
            markSelected(root, cn, other, REGION_CN.equals(picked[0]));
            showWarnIfCn(root, REGION_CN.equals(picked[0]));
            refreshConfirm(confirm, 0);
        }

        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (picked[0] == null) {
                    // 没选地区就点确定：不推进、不跑位，只是提示一下
                    android.widget.Toast.makeText(activity, R.string.region_gate_title,
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                taps[0]++;
                if (taps[0] >= TAPS_TO_UNLOCK) {
                    // ★ 第 5 次：落地。先写盘再回调 —— 回调里可能立刻去读地区做限制判断
                    setRegion(activity, picked[0]);
                    refreshConfirm(confirm, TAPS_TO_UNLOCK);
                    onCommitted.run();
                    return;
                }
                // ★★★ 还没够 5 次：按钮跑掉，但**计数必须保留**。
                //   ⚠️ 这里原来写的是 `taps[0] = 0;`（「跑位就清零」）—— 结果是
                //     `taps` 永远只可能是 1，**5 次永远达不到**，按钮永远点不落地。
                //   与下面 refreshConfirm 的 setEnabled(false) 叠加，就成了用户报的
                //   「按多少次都是一样」：点击根本不进这个方法，进来也永远凑不满。
                hop(confirm);
                refreshConfirm(confirm, taps[0]);
                android.widget.Toast.makeText(activity,
                        activity.getString(R.string.region_gate_confirm_hint,
                                TAPS_TO_UNLOCK - taps[0]),
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 让按钮「跑」一下。
     *
     * <p>★ v3.7 加了两个细节：**轻微旋转** + **回弹过冲**。
     * 纯位移的按钮看起来像被人拖着走；带一点旋转和过冲之后，
     * 读起来是「按钮自己弹开了」—— 这才是用户说的「乱跑」的手感。
     */
    private static void hop(View v) {
        if (v == null) {
            return;
        }
        int w = v.getWidth() > 0 ? v.getWidth() : 600;
        int h = v.getHeight() > 0 ? v.getHeight() : 140;
        // ★ 位移幅度收窄到 50% / 1.4 倍：v3.6 的 55% / 1.6 倍在窄屏上会把按钮
        //   甩到屏幕外，用户点不到就只能一直等它回来。
        float dx = (float) ((Math.random() - 0.5) * w * 0.50);
        float dy = (float) ((Math.random() - 0.5) * h * 1.40);
        float rot = (float) ((Math.random() - 0.5) * 7.0);
        v.animate()
                .translationX(dx)
                .translationY(dy)
                .rotation(rot)
                .scaleX(0.93f)
                .scaleY(0.93f)
                .setDuration(120)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.7f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        // 回弹：缩放和旋转用 Overshoot 过冲一点点，像弹簧归位
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .rotation(0f)
                                .setDuration(190)
                                .setInterpolator(
                                        new android.view.animation.OvershootInterpolator(1.2f))
                                .start();
                    }
                })
                .start();
    }

    /**
     * 刷新确定按钮的文字与状态。
     *
     * <p>★★★ 2.2.4：**绝不能 {@code setEnabled(false)}**。
     *
     * <p>用户报「这个界面安不了确定，按多少次都是一样」—— 根因就是这里原来写着
     * {@code confirm.setEnabled(unlocked)}。Android 里被禁用的 View **不接收任何点击**，
     * {@code OnClickListener} 根本不会触发 ⇒ 而 {@code taps} 只能在点击里累加
     * ⇒ 计数永远停在 0 ⇒ 永远解锁不了。**这是一个自锁**：解锁条件反过来把唯一能推进
     * 解锁的动作关掉了。
     *
     * <p>正确做法：按钮**始终可点**，只用透明度表达「还差几次」。
     * 透明度是视觉提示，不影响触摸；这样点击 → 计数 +1 → 透明度变实 → 第 5 次落地。
     *
     * <p>★ 参数 {@code taps} 现在是**已点次数**（0 ~ {@link #TAPS_TO_UNLOCK}），
     * 不再只传 0 / 5 两个哨兵值 —— 这样按钮的视觉能随进度连续变化，
     * 用户看得出「确实在推进」，而不是「点了没反应」。
     */
    private static void refreshConfirm(TextView confirm, int taps) {
        if (confirm == null) {
            return;
        }
        boolean unlocked = taps >= TAPS_TO_UNLOCK;
        // ★ 始终可点。这一行是修 bug 的核心，不要改成 setEnabled(unlocked)。
        confirm.setEnabled(true);
        confirm.setClickable(true);
        if (unlocked) {
            confirm.setText(R.string.region_gate_confirm_ready);
            confirm.setAlpha(1f);
        } else {
            confirm.setText(R.string.region_gate_confirm);
            // 0 次 → 0.55，4 次 → 0.91：每点一次都明显更实一点
            float t = Math.max(0, Math.min(taps, TAPS_TO_UNLOCK - 1))
                    / (float) (TAPS_TO_UNLOCK - 1);
            confirm.setAlpha(0.55f + 0.36f * t);
        }
    }

    /** 选项选中态：背景 selector 认 selected，右侧勾图标跟着显隐。 */
    private static void markSelected(View root, View cn, View other, boolean cnSelected) {
        if (cn != null) {
            cn.setSelected(cnSelected);
        }
        if (other != null) {
            // ★ 两态互斥，这里显式置反，避免「两个都亮」或「两个都不亮」
            other.setSelected(!cnSelected);
        }
        View cnCheck = root.findViewById(R.id.region_gate_cn_check);
        View otherCheck = root.findViewById(R.id.region_gate_other_check);
        if (cnCheck != null) {
            cnCheck.setVisibility(cnSelected ? View.VISIBLE : View.INVISIBLE);
        }
        if (otherCheck != null) {
            otherCheck.setVisibility(cnSelected ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /** 只有选中中国大陆才显示那条 20sp 的警告。 */
    private static void showWarnIfCn(View root, boolean cn) {
        View box = root.findViewById(R.id.region_gate_warn_box);
        if (box != null) {
            box.setVisibility(cn ? View.VISIBLE : View.GONE);
        }
    }
}
