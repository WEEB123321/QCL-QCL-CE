package com.qcl.launcher.launcher.launch.check;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.gson.GsonUtils;

/**
 * ★★★ 社区版新增：启动前检查面板（#21）+ 一键修复（#5 的「能修的那部分」）。
 *
 * <p><b>什么时候弹</b>：默认「有问题才弹」—— 一切正常时不打扰。
 * 在 {@link com.qcl.launcher.launcher.launch.check.LaunchTools} 的启动流程里先跑一次
 * {@link PreLaunchCheck}，没问题就静默继续。
 *
 * <p><b>能修什么</b>：
 * <ul>
 *   <li>内存分配超了 → 直接改成本机物理内存的一半；</li>
 *   <li>Java 运行时没装 → 跳到「安装运行环境」页（那一步是离线解压，不需要联网）。</li>
 * </ul>
 * 不能自动修的（版本文件丢了、模组太多）只给建议 —— <b>不摆点了没反应的按钮</b>。
 */
public final class PreLaunchCheckDialog {

    private PreLaunchCheckDialog() {
    }

    /**
     * 跑一次检查；有问题就弹面板。
     *
     * @param onProceed 用户选择「继续启动」（或检查通过）时回调
     */
    public static void checkThen(final Context context, final MainActivity activity,
                                 final String versionPath, final Runnable onProceed) {
        PreLaunchCheck.Result result;
        try {
            result = PreLaunchCheck.run(context, activity, versionPath);
        } catch (Throwable t) {
            // ★ 检查本身失败绝不能拦住启动
            if (onProceed != null) {
                onProceed.run();
            }
            return;
        }
        if (result == null || !result.hasProblem()) {
            if (onProceed != null) {
                onProceed.run();
            }
            return;
        }
        show(context, activity, result, onProceed);
    }

    public static void show(final Context context, final MainActivity activity,
                            final PreLaunchCheck.Result result, final Runnable onProceed) {
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        box.setPadding(pad, dp(context, 8), pad, dp(context, 8));

        boolean anyFixable = result.hasFixable();
        for (final PreLaunchCheck.Item item : result.items) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(context, 6), 0, dp(context, 6));

            TextView mark = new TextView(context);
            mark.setText("●");
            mark.setTextSize(11f);
            mark.setTextColor(item.ok ? Color.parseColor("#FF2E7D32") : Color.parseColor("#FFB3261E"));
            row.addView(mark);

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(context, 8), 0, 0, 0);
            col.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView title = new TextView(context);
            title.setText(item.title);
            title.setTextSize(12f);
            title.setTextColor(Color.BLACK);
            col.addView(title);

            TextView detail = new TextView(context);
            detail.setText(item.detail == null ? "" : item.detail);
            detail.setTextSize(10f);
            detail.setTextColor(Color.parseColor("#99000000"));
            col.addView(detail);

            if (!item.ok && item.advice != null && !item.advice.isEmpty()) {
                TextView advice = new TextView(context);
                advice.setText(item.advice);
                advice.setTextSize(10f);
                advice.setTextColor(Color.parseColor("#FF185FA5"));
                advice.setPadding(0, dp(context, 3), 0, 0);
                col.addView(advice);
            }
            row.addView(col);
            box.addView(row);
        }

        ScrollView scroll = new ScrollView(context);
        scroll.addView(box);

        AlertDialog.Builder b = new AlertDialog.Builder(context)
                .setTitle(R.string.pre_launch_title)
                .setView(scroll)
                .setPositiveButton(R.string.pre_launch_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (onProceed != null) {
                            onProceed.run();
                        }
                    }
                })
                .setNegativeButton(R.string.pre_launch_cancel, null);
        if (anyFixable) {
            b.setNeutralButton(R.string.pre_launch_fix, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    applyFixes(context, activity, result, onProceed);
                }
            });
        }
        try {
            b.show();
        } catch (Throwable t) {
            if (onProceed != null) {
                onProceed.run();
            }
        }
    }

    /** 把能自动修的修掉；修完不再自动启动，让用户自己再点一次「启动」（避免静默改配置又静默启动）。 */
    private static void applyFixes(Context context, MainActivity activity,
                                   PreLaunchCheck.Result result, final Runnable onProceed) {
        StringBuilder done = new StringBuilder();
        boolean needRuntimePage = false;
        for (PreLaunchCheck.Item item : result.items) {
            if (item.ok) {
                continue;
            }
            if (item.fix == PreLaunchCheck.Fix.RAM && item.fixValue > 0) {
                try {
                    activity.privateGameSetting.ramSetting.maxRam = item.fixValue;
                    activity.privateGameSetting.ramSetting.minRam =
                            Math.min(activity.privateGameSetting.ramSetting.minRam, item.fixValue);
                    GsonUtils.savePrivateGameSetting(activity.privateGameSetting,
                            AppManifest.SETTING_DIR + "/private_game_setting.json");
                    done.append(context.getString(R.string.pre_launch_fixed_ram, item.fixValue)).append('\n');
                } catch (Throwable t) {
                    done.append(context.getString(R.string.pre_launch_fix_failed, String.valueOf(t))).append('\n');
                }
            } else if (item.fix == PreLaunchCheck.Fix.RUNTIME) {
                needRuntimePage = true;
            }
        }
        try {
            if (done.length() > 0) {
                Toast.makeText(context, done.toString().trim(), Toast.LENGTH_LONG).show();
            }
        } catch (Throwable ignored) {
        }
        if (needRuntimePage) {
            // ★ 运行环境的安装是个完整流程（解压几百 MB），不能在对话框里做 —— 跳到那一页。
            try {
                Intent intent = new Intent(context,
                        com.qcl.launcher.launcher.setting.RuntimeInstallActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable t) {
                try {
                    Toast.makeText(context, R.string.pre_launch_go_runtime, Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static int dp(Context context, int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
