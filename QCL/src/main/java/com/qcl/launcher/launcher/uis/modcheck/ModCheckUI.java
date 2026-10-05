package com.qcl.launcher.launcher.uis.modcheck;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.mod.ModScanner;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

import java.io.File;
import java.util.List;

/**
 * ★★★ 社区版新增：模组体检页。
 *
 * <p><b>★ 它做什么、不做什么</b>：只<b>读</b>每个 jar 自己声明的元数据并与当前实例对照，
 * 把明显对不上的挑出来。它<b>不</b>自动禁用/删除模组 —— 那是最容易造成二次损失的操作，
 * 而且「哪个模组导致崩溃」这件事光看元数据判断不出来，只能靠人试。
 *
 * <p><b>★ 为什么远古版本也要支持</b>：QCL 的差异点就是全远古版本。
 * ModLoader 时代的模组没有元数据文件（就是一个普通 jar），这是<b>正常的</b>，
 * 所以这种情况只提示、不当成错误。
 */
public class ModCheckUI extends BaseUI implements View.OnClickListener {

    /** 模组列表最多渲染多少条 */
    private static final int MAX_ROWS = 120;

    private LinearLayout root;
    private TextView summary;
    private LinearLayout warnContainer;
    private LinearLayout listContainer;
    private volatile boolean scanning = false;
    private String lastReport = "";

    public ModCheckUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.root = this.activity.findViewById(R.id.ui_mod_check);
        this.summary = this.activity.findViewById(R.id.mod_check_summary);
        this.warnContainer = this.activity.findViewById(R.id.mod_check_warn_container);
        this.listContainer = this.activity.findViewById(R.id.mod_check_container);
        bind(this.activity.findViewById(R.id.mod_check_refresh));
        bind(this.activity.findViewById(R.id.mod_check_share));
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
                this.context.getResources().getString(R.string.mod_check_title),
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
        if (id == R.id.mod_check_refresh) {
            scan();
        } else if (id == R.id.mod_check_share) {
            share();
        }
    }

    // ------------------------------------------------------------------ 扫描

    private void scan() {
        if (this.scanning) {
            return;
        }
        String versionPath = null;
        try {
            versionPath = this.activity.publicGameSetting.currentVersion;
        } catch (Throwable ignored) {
        }
        if (versionPath == null || versionPath.isEmpty()) {
            if (this.summary != null) {
                this.summary.setText(R.string.mod_check_no_instance);
            }
            if (this.warnContainer != null) {
                this.warnContainer.removeAllViews();
            }
            if (this.listContainer != null) {
                this.listContainer.removeAllViews();
            }
            return;
        }
        final File dir = new File(versionPath);
        final String name = dir.getName();
        this.scanning = true;
        if (this.summary != null) {
            this.summary.setText(R.string.mod_check_scanning);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ModScanner.Report report = ModScanner.scan(dir, name);
                ModCheckUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        render(report);
                    }
                });
            }
        }, "qcl-modscan").start();
    }

    // ------------------------------------------------------------------ 渲染

    private void render(ModScanner.Report r) {
        if (this.warnContainer == null || this.listContainer == null) {
            return;
        }
        this.warnContainer.removeAllViews();
        this.listContainer.removeAllViews();

        int n = r.mods.size();
        if (this.summary != null) {
            this.summary.setText(this.context.getString(R.string.mod_check_summary,
                    n,
                    r.instanceVersion.isEmpty() ? "?" : r.instanceVersion,
                    r.instanceLoader.isEmpty()
                            ? this.context.getString(R.string.compat_loader_unknown)
                            : r.instanceLoader));
        }

        StringBuilder report = new StringBuilder();
        report.append(this.context.getString(R.string.mod_check_summary, n,
                r.instanceVersion.isEmpty() ? "?" : r.instanceVersion,
                r.instanceLoader.isEmpty() ? "?" : r.instanceLoader)).append('\n');

        // ★ 硬问题（加载器对不上等）用红字，软提示用蓝字 —— 全标红等于没标。
        for (String w : r.hardWarnings) {
            this.warnContainer.addView(note(w, true));
            report.append("· [严重] ").append(w).append('\n');
        }
        for (String w : r.warnings) {
            this.warnContainer.addView(note(w, false));
            report.append("· ").append(w).append('\n');
        }

        int shown = 0;
        for (ModScanner.ModInfo m : r.mods) {
            if (shown++ >= MAX_ROWS) {
                TextView more = new TextView(this.context);
                more.setText(this.context.getString(R.string.mod_check_more, r.mods.size() - MAX_ROWS));
                more.setTextSize(10f);
                more.setTextColor(Color.parseColor("#99000000"));
                more.setPadding(px(10), px(4), px(10), px(4));
                this.listContainer.addView(more);
                break;
            }
            this.listContainer.addView(modRow(m));
            report.append("· ").append(m.fileName)
                    .append("  [").append(m.loader).append(']')
                    .append(m.declaredMcVersion.isEmpty() ? "" : "  " + m.declaredMcVersion)
                    .append('\n');
        }
        this.lastReport = report.toString();
    }

    private View note(String text, boolean hard) {
        TextView t = new TextView(this.context);
        t.setText(text);
        t.setTextSize(11f);
        t.setTextColor(hard ? Color.parseColor("#FFB3261E") : Color.parseColor("#FF185FA5"));
        t.setPadding(px(10), px(4), px(10), px(4));
        return t;
    }

    private View modRow(ModScanner.ModInfo m) {
        LinearLayout box = new LinearLayout(this.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.qcl_button_gray);
        box.setPadding(px(10), px(6), px(10), px(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(4);
        box.setLayoutParams(lp);

        TextView title = new TextView(this.context);
        title.setText(m.fileName);
        title.setTextSize(11f);
        title.setTextColor(Color.BLACK);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        box.addView(title);

        StringBuilder meta = new StringBuilder();
        meta.append(m.loader);
        if (m.declaredMcVersion != null && !m.declaredMcVersion.isEmpty()) {
            meta.append("  ·  ").append(m.declaredMcVersion);
        }
        if (m.modName != null && !m.modName.isEmpty()) {
            meta.append("  ·  ").append(m.modName);
        }
        if (m.error != null) {
            meta.append("  ·  ").append(m.error);
        }
        TextView sub = new TextView(this.context);
        sub.setText(meta.toString());
        sub.setTextSize(9f);
        sub.setTextColor(Color.parseColor("#99000000"));
        box.addView(sub);
        return box;
    }

    // ------------------------------------------------------------------ 分享

    private void share() {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, this.context.getString(R.string.mod_check_title));
            intent.putExtra(Intent.EXTRA_TEXT,
                    this.lastReport.isEmpty() ? this.context.getString(R.string.mod_check_scanning)
                                              : this.lastReport);
            this.activity.startActivity(Intent.createChooser(intent,
                    this.context.getString(R.string.mod_check_share)));
        } catch (Throwable t) {
            toast(R.string.log_center_toast_share_failed);
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
