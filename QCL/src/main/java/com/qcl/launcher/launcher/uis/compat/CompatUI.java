package com.qcl.launcher.launcher.uis.compat;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.compat.CompatDatabase;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

import java.io.File;
import java.util.List;

/**
 * ★★★ 社区版新增：兼容性备忘页。
 *
 * <p><b>★ 三条诚实原则</b>：
 * <ol>
 *   <li>内置条目与自建条目在界面上<b>分别标注</b>，用户一眼能看出哪条是自己写的；</li>
 *   <li>当前实例的加载器是<b>从版本名推测</b>的，界面上写明「推测」，不假装精确；</li>
 *   <li>不联网、不建服务器 —— 这份清单完全属于用户，可导出分享、可导入合并。</li>
 * </ol>
 */
public class CompatUI extends BaseUI implements View.OnClickListener {

    private LinearLayout root;
    private TextView instanceText;
    private EditText search;
    private LinearLayout container;
    private String keyword = "";
    /** 从版本名推测出来的加载器（可能为空） */
    private String detectedLoader = "";
    private String detectedVersion = "";

    public CompatUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.root = this.activity.findViewById(R.id.ui_compat);
        this.instanceText = this.activity.findViewById(R.id.compat_instance);
        this.search = this.activity.findViewById(R.id.compat_search);
        this.container = this.activity.findViewById(R.id.compat_container);
        bind(this.activity.findViewById(R.id.compat_add));
        bind(this.activity.findViewById(R.id.compat_export));
        bind(this.activity.findViewById(R.id.compat_import));
        if (this.search != null) {
            this.search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) {
                    keyword = s == null ? "" : s.toString();
                    render();
                }
            });
        }
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
                this.context.getResources().getString(R.string.compat_title),
                canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.root, this.activity, this.context, true);
        detectInstance();
        render();
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.root, this.activity, this.context, true);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.compat_add) {
            showAddDialog();
        } else if (id == R.id.compat_export) {
            exportAll();
        } else if (id == R.id.compat_import) {
            showImportDialog();
        }
    }

    // ------------------------------------------------------------------ 当前实例

    private void detectInstance() {
        try {
            String versionPath = this.activity.publicGameSetting.currentVersion;
            if (versionPath == null || versionPath.isEmpty()) {
                this.detectedVersion = "";
                this.detectedLoader = "";
                if (this.instanceText != null) {
                    this.instanceText.setText(R.string.compat_no_instance);
                }
                return;
            }
            String name = new File(versionPath).getName();
            this.detectedVersion = guessVersion(name);
            this.detectedLoader = guessLoader(name);
            if (this.instanceText != null) {
                this.instanceText.setText(this.context.getString(R.string.compat_instance_fmt,
                        name,
                        this.detectedVersion.isEmpty() ? "?" : this.detectedVersion,
                        this.detectedLoader.isEmpty()
                                ? this.context.getString(R.string.compat_loader_unknown)
                                : this.detectedLoader));
            }
        } catch (Throwable t) {
            if (this.instanceText != null) {
                this.instanceText.setText(R.string.compat_no_instance);
            }
        }
    }

    /** 从版本名里抠 MC 版本号（形如 fabric-loader-0.15.0-1.20.1 → 1.20.1）。 */
    private String guessVersion(String name) {
        if (name == null) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(name);
        String best = "";
        while (m.find()) {
            String g = m.group(1);
            // 优先取形如 1.x 的段
            if (g.startsWith("1.")) {
                best = g;
            }
        }
        return best;
    }

    /** 从版本名里认加载器。★ 这是「推测」，界面上已标注。 */
    private String guessLoader(String name) {
        if (name == null) {
            return "";
        }
        String n = name.toLowerCase();
        if (n.contains("neoforge")) {
            return "NeoForge";
        }
        if (n.contains("forge")) {
            return "Forge";
        }
        if (n.contains("quilt")) {
            return "Quilt";
        }
        if (n.contains("fabric")) {
            return "Fabric";
        }
        if (n.contains("optifine")) {
            return "OptiFine";
        }
        if (n.contains("babric")) {
            return "Babric";
        }
        return "";
    }

    // ------------------------------------------------------------------ 渲染

    private void render() {
        if (this.container == null) {
            return;
        }
        this.container.removeAllViews();
        List<CompatDatabase.Entry> list;
        if (this.keyword == null || this.keyword.trim().isEmpty()) {
            // 默认：先按当前实例查，没有匹配就列全部
            list = CompatDatabase.query(this.detectedVersion, this.detectedLoader);
            if (list.isEmpty()) {
                list = CompatDatabase.all();
            }
        } else {
            list = CompatDatabase.search(this.keyword);
        }
        if (list.isEmpty()) {
            this.container.addView(card(this.context.getString(R.string.compat_empty), "", "warn", null));
            return;
        }
        for (CompatDatabase.Entry e : list) {
            this.container.addView(card(titleOf(e), e.note, e.status, e));
        }
    }

    private String titleOf(CompatDatabase.Entry e) {
        String tag = e.builtin ? this.context.getString(R.string.compat_tag_builtin)
                               : this.context.getString(R.string.compat_tag_user);
        return "[" + tag + "] " + (e.mcVersion == null ? "*" : e.mcVersion)
                + " + " + (e.loader == null ? "*" : e.loader);
    }

    private View card(String title, String note, String status, final CompatDatabase.Entry entry) {
        LinearLayout box = new LinearLayout(this.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.qcl_button_gray);
        box.setPadding(px(10), px(8), px(10), px(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        box.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this.context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(this.context);
        t.setText(title);
        t.setTextSize(12f);
        t.setTextColor(statusColor(status));
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(t);
        box.addView(head);

        if (note != null && !note.isEmpty()) {
            TextView n = new TextView(this.context);
            n.setText(note);
            n.setTextSize(10f);
            n.setTextColor(Color.parseColor("#CC000000"));
            box.addView(n);
        }

        // 自建条目可删；内置条目不给删（避免用户误删掉常识后无法恢复）
        if (entry != null && !entry.builtin) {
            TextView del = new TextView(this.context);
            del.setText(this.context.getString(R.string.compat_delete));
            del.setTextSize(10f);
            del.setTextColor(Color.parseColor("#FFB3261E"));
            del.setPadding(0, px(4), 0, 0);
            del.setClickable(true);
            del.setFocusable(true);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CompatDatabase.remove(CompatUI.this.context, entry);
                    render();
                }
            });
            box.addView(del);
        }
        return box;
    }

    private int statusColor(String status) {
        if ("ok".equals(status)) {
            return Color.parseColor("#FF2E7D32");
        }
        if ("bad".equals(status)) {
            return Color.parseColor("#FFB3261E");
        }
        return Color.parseColor("#FF8A6D00");
    }

    // ------------------------------------------------------------------ 增 / 导入 / 导出

    private void showAddDialog() {
        try {
            final EditText mc = new EditText(this.context);
            mc.setHint(R.string.compat_hint_version);
            final EditText loader = new EditText(this.context);
            loader.setHint(R.string.compat_hint_loader);
            final EditText note = new EditText(this.context);
            note.setHint(R.string.compat_hint_note);
            note.setMinLines(2);

            LinearLayout box = new LinearLayout(this.context);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(px(12), px(8), px(12), px(8));
            box.addView(mc);
            box.addView(loader);
            box.addView(note);

            new AlertDialog.Builder(this.activity)
                    .setTitle(R.string.compat_add)
                    .setView(box)
                    .setPositiveButton(R.string.auto_task_script_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            CompatDatabase.Entry e = new CompatDatabase.Entry(
                                    text(mc, "*"), text(loader, "*"), "warn", text(note, ""), false);
                            if (e.note.isEmpty()) {
                                toast(R.string.compat_note_required);
                                return;
                            }
                            CompatDatabase.add(CompatUI.this.context, e);
                            render();
                        }
                    })
                    .setNegativeButton(R.string.legacy_cn_lang_cancel, null)
                    .create().show();
        } catch (Throwable ignored) {
        }
    }

    private String text(EditText e, String def) {
        String s = e == null || e.getText() == null ? "" : e.getText().toString().trim();
        return s.isEmpty() ? def : s;
    }

    private void exportAll() {
        final String json = CompatDatabase.exportJson();
        try {
            ClipboardManager cm = (ClipboardManager)
                    this.context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(
                        this.context.getString(R.string.compat_title), json));
            }
        } catch (Throwable ignored) {
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, this.context.getString(R.string.compat_title));
            intent.putExtra(Intent.EXTRA_TEXT, json);
            this.activity.startActivity(Intent.createChooser(intent,
                    this.context.getString(R.string.compat_export)));
        } catch (Throwable t) {
            toast(R.string.compat_export_copied);
        }
    }

    private void showImportDialog() {
        try {
            final EditText input = new EditText(this.context);
            input.setHint(R.string.compat_import_hint);
            input.setMinLines(4);

            new AlertDialog.Builder(this.activity)
                    .setTitle(R.string.compat_import)
                    .setView(input)
                    .setPositiveButton(R.string.auto_task_script_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            String json = input.getText() == null ? "" : input.getText().toString();
                            int n = CompatDatabase.importJson(json);
                            if (n < 0) {
                                toast(R.string.compat_import_failed);
                            } else {
                                toast(getString(R.string.compat_import_done, n));
                                render();
                            }
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

    private void toast(String s) {
        try {
            Toast.makeText(this.context, s, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private String getString(int res, Object... args) {
        return this.context.getString(res, args);
    }
}
