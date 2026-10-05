package com.qcl.launcher.launcher.dialogs;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.instance.InstanceSpaceHelper;
import com.qcl.launcher.launcher.instance.InstanceTransferHelper;
import com.qcl.launcher.launcher.setting.SettingUtils;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ★★★ 社区版新增：实例间搬运（#7 模组搬家 / #8 存档迁移 / #9 资源包应用 / #10 配置同步）。
 *
 * <p>四步：<b>选目标实例 → 选搬什么 → 看计划（冲突与兼容性警告）→ 执行</b>。
 *
 * <p><b>★ 为什么必须先给「计划」再看确认</b>：这类操作最容易造成的伤害是
 * <b>把目标实例里同名的模组/存档覆盖掉</b>。所以计划里逐个列出「会覆盖谁」「哪几个加载器对不上」，
 * 用户看过再决定。绝不「点了按钮直接开始覆盖」。
 *
 * <p><b>★ 复制 / 移动分成两个按钮</b>：默认应该是复制（安全），
 * 但「搬家」的语义就是移动，所以两个都给，且在按钮文案上写清楚哪个会删源。
 */
public final class InstanceTransferDialog {

    private InstanceTransferDialog() {
    }

    /** 入口：以某个实例为源。 */
    public static void show(final Context context, final MainActivity activity, final String srcName) {
        final String gameDir = activity.launcherSetting.gameFileDirectory;
        List<String> all;
        try {
            all = SettingUtils.getLocalVersionNames(gameDir);
        } catch (Throwable t) {
            toast(context, String.valueOf(t));
            return;
        }
        if (all == null || all.isEmpty()) {
            toast(context, context.getString(R.string.transfer_no_instance));
            return;
        }
        final String[] others = new String[Math.max(0, all.size() - 1)];
        int n = 0;
        for (String s : all) {
            if (!s.equals(srcName)) {
                others[n++] = s;
            }
        }
        if (others.length == 0) {
            toast(context, context.getString(R.string.transfer_need_two));
            return;
        }

        // 第 1 步：选目标
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.transfer_pick_dst, srcName))
                .setItems(others, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        pickKinds(context, activity, srcName, others[which]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ 第 2 步：选内容

    private static void pickKinds(final Context context, final MainActivity activity,
                                  final String srcName, final String dstName) {
        final InstanceTransferHelper.Kind[] kinds = {
                InstanceTransferHelper.Kind.MODS,
                InstanceTransferHelper.Kind.SAVES,
                InstanceTransferHelper.Kind.RESOURCEPACKS,
                InstanceTransferHelper.Kind.SHADERPACKS,
                InstanceTransferHelper.Kind.CONFIG,
        };
        final CharSequence[] labels = {
                context.getString(R.string.transfer_kind_mods),
                context.getString(R.string.transfer_kind_saves),
                context.getString(R.string.transfer_kind_resourcepacks),
                context.getString(R.string.transfer_kind_shaderpacks),
                context.getString(R.string.transfer_kind_config),
        };
        // 默认勾模组 + 配置（最常见的诉求）；存档默认不勾 —— 覆盖存档是不可逆的
        final boolean[] checked = {true, false, false, false, true};

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.transfer_pick_kinds, srcName, dstName))
                .setMultiChoiceItems(labels, checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which, boolean isChecked) {
                                checked[which] = isChecked;
                            }
                        })
                .setPositiveButton(R.string.transfer_next, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Set<InstanceTransferHelper.Kind> set = new LinkedHashSet<InstanceTransferHelper.Kind>();
                        for (int i = 0; i < kinds.length; i++) {
                            if (checked[i]) {
                                set.add(kinds[i]);
                            }
                        }
                        if (set.isEmpty()) {
                            toast(context, context.getString(R.string.transfer_nothing_picked));
                            return;
                        }
                        buildPlan(context, activity, srcName, dstName, set);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ 第 3 步：出计划

    private static void buildPlan(final Context context, final MainActivity activity,
                                  final String srcName, final String dstName,
                                  final Set<InstanceTransferHelper.Kind> kinds) {
        final String gameDir = activity.launcherSetting.gameFileDirectory;
        final File src = new File(gameDir + "/versions/" + srcName);
        final File dst = new File(gameDir + "/versions/" + dstName);
        if (!src.isDirectory() || !dst.isDirectory()) {
            toast(context, context.getString(R.string.transfer_missing_dir));
            return;
        }
        final ProgressDialog pd = new ProgressDialog(context);
        pd.setMessage(context.getString(R.string.transfer_scanning));
        pd.setCancelable(false);
        try {
            pd.show();
        } catch (Throwable ignored) {
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final InstanceTransferHelper.Plan plan = InstanceTransferHelper.plan(src, dst, kinds);
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            pd.dismiss();
                        } catch (Throwable ignored) {
                        }
                        showPlan(context, activity, srcName, dstName, src, dst, plan);
                    }
                });
            }
        }, "qcl-transfer-plan").start();
    }

    private static void showPlan(final Context context, final MainActivity activity,
                                 final String srcName, final String dstName,
                                 final File src, final File dst,
                                 final InstanceTransferHelper.Plan plan) {
        if (plan.isEmpty()) {
            toast(context, context.getString(R.string.transfer_nothing_found));
            return;
        }
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        box.setPadding(pad, dp(context, 8), pad, dp(context, 8));

        TextView head = new TextView(context);
        head.setText(context.getString(R.string.transfer_summary,
                plan.entries.size(), InstanceSpaceHelper.human(plan.totalBytes),
                plan.conflicts, plan.warnings));
        head.setTextSize(12f);
        head.setTextColor(Color.BLACK);
        box.addView(head);

        int shown = 0;
        for (InstanceTransferHelper.Entry e : plan.entries) {
            if (shown++ >= 60) {
                break;
            }
            TextView tv = new TextView(context);
            StringBuilder sb = new StringBuilder();
            sb.append(e.conflict ? "⚠ " : "· ").append(e.name)
                    .append("  (").append(InstanceSpaceHelper.human(e.size)).append(')');
            if (!e.warning.isEmpty()) {
                sb.append('\n').append("      ").append(e.warning);
            }
            tv.setText(sb.toString());
            tv.setTextSize(11f);
            tv.setTextColor(e.conflict || !e.warning.isEmpty()
                    ? Color.parseColor("#FFB3261E") : Color.parseColor("#FF333333"));
            tv.setPadding(0, dp(context, 3), 0, dp(context, 3));
            box.addView(tv);
        }
        if (plan.entries.size() > 60) {
            TextView more = new TextView(context);
            more.setText(context.getString(R.string.transfer_more, plan.entries.size() - 60));
            more.setTextSize(10f);
            more.setTextColor(Color.parseColor("#99000000"));
            box.addView(more);
        }

        ScrollView scroll = new ScrollView(context);
        scroll.addView(box);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 380)));

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.transfer_plan_title))
                .setView(scroll)
                .setPositiveButton(R.string.transfer_copy, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        run(context, activity, plan, src, dst, false);
                    }
                })
                .setNeutralButton(R.string.transfer_move, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        // ★ 移动会删源，单独再确认一次
                        new AlertDialog.Builder(context)
                                .setTitle(R.string.transfer_move)
                                .setMessage(context.getString(R.string.transfer_move_confirm, srcName))
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d2, int w2) {
                                        run(context, activity, plan, src, dst, true);
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ 第 4 步：执行

    private static void run(final Context context, final MainActivity activity,
                            final InstanceTransferHelper.Plan plan, final File src, final File dst,
                            final boolean move) {
        final ProgressDialog pd = new ProgressDialog(context);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(plan.entries.size());
        pd.setCancelable(false);
        try {
            pd.show();
        } catch (Throwable ignored) {
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                InstanceTransferHelper.execute(plan, src, dst, move,
                        new InstanceTransferHelper.Callback() {
                            @Override
                            public void onProgress(final int done, final int total, final String name) {
                                if (activity == null) {
                                    return;
                                }
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            pd.setProgress(done);
                                            pd.setMessage(name);
                                        } catch (Throwable ignored) {
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onFinish(final int copied, final int failed, final String error) {
                                if (activity == null) {
                                    return;
                                }
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            pd.dismiss();
                                        } catch (Throwable ignored) {
                                        }
                                        toast(context, context.getString(
                                                R.string.transfer_done, copied, failed,
                                                error == null ? "" : error));
                                    }
                                });
                            }
                        });
            }
        }, "qcl-transfer").start();
    }

    private static int dp(Context context, int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private static void toast(Context context, String msg) {
        try {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }
}
