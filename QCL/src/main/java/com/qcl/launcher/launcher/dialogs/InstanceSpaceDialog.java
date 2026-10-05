package com.qcl.launcher.launcher.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.instance.InstanceSpaceHelper;

import java.io.File;

/**
 * ★★★ 社区版新增：实例占用空间分析（按用途拆分 + 一键清理垃圾）。
 *
 * <p>统计在后台线程跑（大实例有几千个文件），结果回主线程渲染。
 */
public final class InstanceSpaceDialog {

    private InstanceSpaceDialog() {
    }

    public static void show(final Context context, final MainActivity activity, final String instanceName) {
        final File dir = new File(activity.launcherSetting.gameFileDirectory + "/versions/" + instanceName);

        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        box.setPadding(pad, dp(context, 8), pad, dp(context, 8));
        final TextView loading = new TextView(context);
        loading.setText(R.string.space_calculating);
        loading.setTextSize(12f);
        loading.setTextColor(Color.parseColor("#99000000"));
        box.addView(loading);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(box);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.space_title)
                .setView(scroll)
                .setPositiveButton(R.string.space_close, null)
                .setNeutralButton(R.string.space_clean, null)
                .create();
        dialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final InstanceSpaceHelper.Report r = InstanceSpaceHelper.analyze(dir);
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            box.removeAllViews();
                            addRow(context, box, context.getString(R.string.space_instance),
                                    instanceName);
                            addRow(context, box, context.getString(R.string.space_total),
                                    r.humanTotal());
                            View line = new View(context);
                            line.setBackgroundColor(Color.parseColor("#33000000"));
                            box.addView(line, new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 1))));
                            for (InstanceSpaceHelper.Part p : r.parts) {
                                addRow(context, box, p.label,
                                        p.files < 0
                                                ? InstanceSpaceHelper.human(p.bytes)
                                                : InstanceSpaceHelper.human(p.bytes) + " · " + p.files);
                            }
                            TextView junk = addRow(context, box,
                                    context.getString(R.string.space_junk), r.humanJunk());
                            junk.setTextColor(Color.parseColor("#FFB3261E"));
                            TextView note = new TextView(context);
                            note.setText(R.string.space_junk_note);
                            note.setTextSize(10f);
                            note.setTextColor(Color.parseColor("#99000000"));
                            note.setPadding(0, dp(context, 8), 0, 0);
                            box.addView(note);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        }, "qcl-instance-space").start();

        // ★ 清理按钮：只清「日志 / 崩溃报告 / 下载残留」，弹窗里把将删的东西列清楚再确认。
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClean(context, activity, dir);
            }
        });
    }

    private static void confirmClean(final Context context, final MainActivity activity, final File dir) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.space_clean)
                .setMessage(R.string.space_clean_confirm)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final InstanceSpaceHelper.Report r = InstanceSpaceHelper.analyze(dir);
                                int ok = 0, fail = 0;
                                for (File f : r.junk) {
                                    if (delete(f)) {
                                        ok++;
                                    } else {
                                        fail++;
                                    }
                                }
                                final int fOk = ok, fFail = fail;
                                if (activity != null) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                Toast.makeText(context,
                                                        context.getString(R.string.space_clean_done, fOk, fFail),
                                                        Toast.LENGTH_SHORT).show();
                                            } catch (Throwable ignored) {
                                            }
                                        }
                                    });
                                }
                            }
                        }, "qcl-instance-clean").start();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static boolean delete(File f) {
        try {
            if (f.isDirectory()) {
                File[] fs = f.listFiles();
                if (fs != null) {
                    for (File c : fs) {
                        delete(c);
                    }
                }
            }
            return f.delete();
        } catch (Throwable t) {
            return false;
        }
    }

    private static TextView addRow(Context context, LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(context, 5), 0, dp(context, 5));

        TextView l = new TextView(context);
        l.setText(label);
        l.setTextSize(12f);
        l.setTextColor(Color.parseColor("#99000000"));
        l.setMinWidth(dp(context, 110));
        row.addView(l);

        TextView v = new TextView(context);
        v.setText(value == null ? "" : value);
        v.setTextSize(12f);
        v.setTextColor(Color.BLACK);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(v);

        parent.addView(row);
        return v;
    }

    private static int dp(Context context, int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
