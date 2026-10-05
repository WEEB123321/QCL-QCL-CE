package com.qcl.launcher.launcher.uis.stats;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.stats.StatsTracker;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

import java.util.Map;

/**
 * ★★★ 社区版新增：本地统计页。
 *
 * <p><b>只读 + 一个「重置」</b>：统计的价值在于「看一眼」，不在于配置。
 * 页面上没有开关、没有筛选 —— 打开就是三个数字加两张清单。
 *
 * <p><b>★ 崩溃次数的口径如实标注</b>：只算 Java 未捕获异常。native 段错误
 * （SIGSEGV 之类）走不到全局处理器，因此界面文案写的是「Java 崩溃」而不是「崩溃」，
 * 不夸大成「全部崩溃次数」。
 */
public class StatsUI extends BaseUI implements View.OnClickListener {

    private LinearLayout statsUI;
    private TextView totalTime;
    private TextView launchCount;
    private TextView crashCount;
    private LinearLayout instanceContainer;
    private TextView instanceEmpty;
    private LinearLayout sessionContainer;

    public StatsUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.statsUI = this.activity.findViewById(R.id.ui_stats);
        this.totalTime = this.activity.findViewById(R.id.stats_total_time);
        this.launchCount = this.activity.findViewById(R.id.stats_launch_count);
        this.crashCount = this.activity.findViewById(R.id.stats_crash_count);
        this.instanceContainer = this.activity.findViewById(R.id.stats_instance_container);
        this.instanceEmpty = this.activity.findViewById(R.id.stats_instance_empty);
        this.sessionContainer = this.activity.findViewById(R.id.stats_session_container);
        bind(this.activity.findViewById(R.id.stats_share));
        bind(this.activity.findViewById(R.id.stats_reset));
    }

    private void bind(View v) {
        if (v != null) {
            v.setOnClickListener(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.activity.showBarTitle(
                this.context.getResources().getString(R.string.stats_title),
                canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.statsUI, this.activity, this.context, true);
        refresh();
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.statsUI, this.activity, this.context, true);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.stats_share) {
            shareReport();
        } else if (id == R.id.stats_reset) {
            confirmReset();
        }
    }

    // ------------------------------------------------------------------ 渲染

    private void refresh() {
        StatsTracker.Stats s = StatsTracker.get(this.context);
        if (this.totalTime != null) {
            this.totalTime.setText(durationText(s.totalPlayMs));
        }
        if (this.launchCount != null) {
            this.launchCount.setText(String.valueOf(s.launchCount));
        }
        if (this.crashCount != null) {
            this.crashCount.setText(String.valueOf(s.crashCount));
        }
        renderInstances(s);
        renderSessions(s);
    }

    private void renderInstances(StatsTracker.Stats s) {
        if (this.instanceContainer == null) {
            return;
        }
        this.instanceContainer.removeAllViews();
        boolean empty = s.instanceLaunches == null || s.instanceLaunches.isEmpty();
        if (this.instanceEmpty != null) {
            this.instanceEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (empty) {
            return;
        }
        for (Map.Entry<String, Integer> e : s.instanceLaunches.entrySet()) {
            this.instanceContainer.addView(row(
                    e.getKey(),
                    this.context.getString(R.string.stats_instance_launch_times,
                            e.getValue() == null ? 0 : e.getValue())));
        }
    }

    private void renderSessions(StatsTracker.Stats s) {
        if (this.sessionContainer == null) {
            return;
        }
        this.sessionContainer.removeAllViews();
        if (s.sessions == null || s.sessions.isEmpty()) {
            this.sessionContainer.addView(row(
                    this.context.getString(R.string.stats_empty), ""));
            return;
        }
        for (StatsTracker.Session sess : s.sessions) {
            String name = sess.instance == null || sess.instance.isEmpty()
                    ? this.context.getString(R.string.stats_unknown_instance)
                    : sess.instance;
            this.sessionContainer.addView(row(
                    name + " · " + durationText(sess.durationMs),
                    StatsTracker.formatTime(sess.startAt)));
        }
    }

    /** 一行两列：左侧主文案、右侧次要信息。用等宽预算避免长实例名把右边挤没。 */
    private View row(String main, String sub) {
        LinearLayout row = new LinearLayout(this.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.qcl_button_gray);
        row.setPadding(px(10), px(8), px(10), px(8));
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = px(6);
        row.setLayoutParams(rp);

        TextView t1 = new TextView(this.context);
        t1.setText(main);
        t1.setTextSize(12f);
        t1.setTextColor(Color.BLACK);
        t1.setSingleLine(true);
        t1.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        t1.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t1);

        TextView t2 = new TextView(this.context);
        t2.setText(sub);
        t2.setTextSize(10f);
        t2.setTextColor(Color.parseColor("#99000000"));
        row.addView(t2);
        return row;
    }

    private String durationText(long ms) {
        long totalMin = ms / 60000L;
        long h = totalMin / 60L;
        long m = totalMin % 60L;
        if (h <= 0L) {
            return this.context.getString(R.string.stats_duration_min, m);
        }
        return this.context.getString(R.string.stats_duration_hm, h, m);
    }

    // ------------------------------------------------------------------ 动作

    private void shareReport() {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT,
                    this.context.getString(R.string.stats_share_subject));
            intent.putExtra(Intent.EXTRA_TEXT, buildReport());
            this.activity.startActivity(Intent.createChooser(intent,
                    this.context.getString(R.string.stats_share_chooser)));
        } catch (Throwable t) {
            toast(R.string.log_center_toast_share_failed);
        }
    }

    private String buildReport() {
        StatsTracker.Stats s = StatsTracker.get(this.context);
        StringBuilder sb = new StringBuilder();
        sb.append(this.context.getString(R.string.stats_title)).append('\n');
        sb.append(this.context.getString(R.string.stats_label_time)).append(": ")
                .append(durationText(s.totalPlayMs)).append('\n');
        sb.append(this.context.getString(R.string.stats_label_launch)).append(": ")
                .append(s.launchCount).append('\n');
        sb.append(this.context.getString(R.string.stats_label_crash)).append(": ")
                .append(s.crashCount).append('\n');
        sb.append(this.context.getString(R.string.stats_first_launch)).append(": ")
                .append(StatsTracker.formatTime(s.firstLaunchAt)).append('\n');
        sb.append(this.context.getString(R.string.stats_last_launch)).append(": ")
                .append(StatsTracker.formatTime(s.lastLaunchAt)).append('\n');
        if (s.instanceLaunches != null && !s.instanceLaunches.isEmpty()) {
            sb.append('\n').append(this.context.getString(R.string.stats_section_instance)).append('\n');
            for (Map.Entry<String, Integer> e : s.instanceLaunches.entrySet()) {
                sb.append("  ").append(e.getKey()).append(" × ").append(e.getValue()).append('\n');
            }
        }
        return sb.toString();
    }

    private void confirmReset() {
        try {
            new AlertDialog.Builder(this.activity)
                    .setMessage(R.string.stats_reset_confirm)
                    .setPositiveButton(R.string.stats_reset, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            StatsTracker.reset(StatsUI.this.context);
                            refresh();
                            toast(R.string.stats_reset_done);
                        }
                    })
                    .setNegativeButton(R.string.legacy_cn_lang_cancel, null)
                    .create().show();
        } catch (Throwable ignored) {
        }
    }

    private int px(int dp) {
        return Math.round(dp * this.context.getResources().getDisplayMetrics().density);
    }

    private void toast(int res) {
        try {
            Toast.makeText(this.context, res, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
