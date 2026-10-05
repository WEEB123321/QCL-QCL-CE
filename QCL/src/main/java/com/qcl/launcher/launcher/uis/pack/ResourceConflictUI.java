package com.qcl.launcher.launcher.uis.pack;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.pack.ResourcePackScanner;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * ★★★ 社区版新增：资源冲突检测页。
 *
 * <p><b>★ 只读、只报告</b>：本页<b>不</b>自动改 {@code options.txt}、也不移动文件。
 * 自动「一键修复」在这种场景下非常危险 —— 顺序一改，材质观感立刻变化，
 * 用户未必想要。这里只把事实摆出来，改不改由玩家决定。
 *
 * <p><b>★ 大包标注</b>：条目数被截断的包会明确写出「（超大包，覆盖检测为抽样）」，
 * 不假装结果是完整的。
 */
public class ResourceConflictUI extends BaseUI implements View.OnClickListener {

    /** 每个分区最多渲染多少条 —— 再多就该去改包，而不是在页面上翻 */
    private static final int MAX_ROWS_PER_SECTION = 40;

    private LinearLayout root;
    private TextView summary;
    private LinearLayout container;
    private volatile boolean scanning = false;
    private String lastReport = "";

    public ResourceConflictUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.root = this.activity.findViewById(R.id.ui_resource_conflict);
        this.summary = this.activity.findViewById(R.id.pack_conflict_summary);
        this.container = this.activity.findViewById(R.id.pack_conflict_container);
        bind(this.activity.findViewById(R.id.pack_conflict_refresh));
        bind(this.activity.findViewById(R.id.pack_conflict_share));
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
                this.context.getResources().getString(R.string.pack_conflict_title),
                canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.root, this.activity, this.context, true);
        scan();
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.root, this.activity, this.context, true);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.pack_conflict_refresh) {
            scan();
        } else if (id == R.id.pack_conflict_share) {
            share();
        }
    }

    // ------------------------------------------------------------------ 扫描

    private void scan() {
        if (this.scanning) {
            return;
        }
        final String versionPath = currentVersionPath();
        if (versionPath == null || versionPath.isEmpty()) {
            if (this.summary != null) {
                this.summary.setText(R.string.pack_conflict_no_instance);
            }
            if (this.container != null) {
                this.container.removeAllViews();
            }
            return;
        }
        this.scanning = true;
        if (this.summary != null) {
            this.summary.setText(R.string.pack_conflict_scanning);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ResourcePackScanner.Report report;
                try {
                    report = ResourcePackScanner.scan(new File(versionPath));
                } catch (Throwable t) {
                    // 扫描失败也要把 UI 从「正在扫描」里放出来
                    ResourceConflictUI.this.activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            scanning = false;
                            if (summary != null) {
                                summary.setText(String.valueOf(t));
                            }
                        }
                    });
                    return;
                }
                ResourceConflictUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        render(report);
                    }
                });
            }
        }, "qcl-packscan").start();
    }

    // ------------------------------------------------------------------ 渲染

    private void render(ResourcePackScanner.Report r) {
        if (this.container == null) {
            return;
        }
        this.container.removeAllViews();
        int packs = r.packs.size();
        int problems = r.conflictCount();
        if (this.summary != null) {
            this.summary.setText(this.context.getString(R.string.pack_conflict_summary, packs, problems));
        }
        if (problems == 0) {
            this.container.addView(card(this.context.getString(R.string.pack_conflict_clean), "", false));
            this.lastReport = this.context.getString(R.string.pack_conflict_clean);
            return;
        }

        StringBuilder report = new StringBuilder();
        report.append(this.context.getString(R.string.pack_conflict_summary, packs, problems)).append('\n');

        // ① 重复
        if (!r.duplicates.isEmpty()) {
            sectionHeader(R.string.pack_conflict_section_duplicate);
            int n = 0;
            for (Map.Entry<String, List<ResourcePackScanner.PackInfo>> e : r.duplicates.entrySet()) {
                if (n++ >= MAX_ROWS_PER_SECTION) {
                    overflow(r.duplicates.size() - MAX_ROWS_PER_SECTION);
                    break;
                }
                StringBuilder sub = new StringBuilder();
                for (ResourcePackScanner.PackInfo p : e.getValue()) {
                    if (sub.length() > 0) {
                        sub.append("  |  ");
                    }
                    sub.append(p.path);
                }
                this.container.addView(card(e.getKey(), sub.toString(), true));
                report.append("· 重复: ").append(e.getKey()).append(" → ").append(sub).append('\n');
            }
        }

        // ② 损坏 / 读不出
        if (!r.invalid.isEmpty()) {
            sectionHeader(R.string.pack_conflict_section_invalid);
            int n = 0;
            for (ResourcePackScanner.PackInfo p : r.invalid) {
                if (n++ >= MAX_ROWS_PER_SECTION) {
                    overflow(r.invalid.size() - MAX_ROWS_PER_SECTION);
                    break;
                }
                this.container.addView(card(p.path, String.valueOf(p.error), true));
                report.append("· 损坏: ").append(p.path).append(" → ").append(p.error).append('\n');
            }
        }

        // ③ 覆盖
        if (!r.overlaps.isEmpty()) {
            sectionHeader(R.string.pack_conflict_section_overlap);
            int n = 0;
            for (Map.Entry<String, List<String>> e : r.overlaps.entrySet()) {
                if (n++ >= MAX_ROWS_PER_SECTION) {
                    overflow(r.overlaps.size() - MAX_ROWS_PER_SECTION);
                    break;
                }
                StringBuilder sub = new StringBuilder();
                for (int i = 0; i < e.getValue().size(); i++) {
                    if (i > 0) {
                        sub.append("  >  ");
                    }
                    sub.append(e.getValue().get(i));
                }
                this.container.addView(card(e.getKey(), sub.toString(), false));
                report.append("· 覆盖: ").append(e.getKey()).append(" ← ").append(sub).append('\n');
            }
        }

        // ④ 被截断的包，如实说明
        boolean truncated = false;
        for (ResourcePackScanner.PackInfo p : r.packs) {
            if (p.truncated) {
                truncated = true;
                break;
            }
        }
        if (truncated) {
            this.container.addView(card(
                    this.context.getString(R.string.pack_conflict_truncated), "", false));
            report.append('\n').append(this.context.getString(R.string.pack_conflict_truncated)).append('\n');
        }
        this.lastReport = report.toString();
    }

    private void sectionHeader(int res) {
        TextView t = new TextView(this.context);
        t.setText(res);
        t.setTextSize(12f);
        t.setTextColor(Color.parseColor("#FF185FA5"));
        t.setPadding(px(10), px(10), px(10), px(2));
        this.container.addView(t);
    }

    private void overflow(int rest) {
        TextView t = new TextView(this.context);
        t.setText(this.context.getString(R.string.pack_conflict_more, rest));
        t.setTextSize(10f);
        t.setTextColor(Color.parseColor("#99000000"));
        t.setPadding(px(10), px(4), px(10), px(4));
        this.container.addView(t);
    }

    private View card(String title, String sub, boolean warn) {
        LinearLayout box = new LinearLayout(this.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.qcl_button_gray);
        box.setPadding(px(10), px(8), px(10), px(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(4);
        box.setLayoutParams(lp);

        TextView t = new TextView(this.context);
        t.setText(title);
        t.setTextSize(11f);
        t.setTextColor(warn ? Color.parseColor("#FFB00020") : Color.BLACK);
        box.addView(t);

        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(this.context);
            s.setText(sub);
            s.setTextSize(9f);
            s.setTextColor(Color.parseColor("#99000000"));
            box.addView(s);
        }
        return box;
    }

    // ------------------------------------------------------------------ 分享

    private void share() {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT,
                    this.context.getString(R.string.pack_conflict_title));
            intent.putExtra(Intent.EXTRA_TEXT,
                    this.lastReport.isEmpty() ? this.context.getString(R.string.pack_conflict_scanning)
                                              : this.lastReport);
            this.activity.startActivity(Intent.createChooser(intent,
                    this.context.getString(R.string.pack_conflict_share)));
        } catch (Throwable t) {
            toast(R.string.log_center_toast_share_failed);
        }
    }

    private String currentVersionPath() {
        try {
            return this.activity.publicGameSetting.currentVersion;
        } catch (Throwable t) {
            return null;
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
