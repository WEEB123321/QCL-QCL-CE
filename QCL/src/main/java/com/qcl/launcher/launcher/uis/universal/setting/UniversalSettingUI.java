package com.qcl.launcher.launcher.uis.universal.setting;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.update.UpdateChecker;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;
import com.qcl.launcher.utils.gson.GsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ★★★ 社区版新增：「通用」设置页（启动器更新 / 缓存 / 语言 / 导出日志）。
 *
 * <h3>为什么会有这个类</h3>
 * {@code ui_setting_universal.xml} 一直都在，里面的四项设置也都画好了 —— 但是：
 * <ul>
 *   <li>设置页里**没有入口按钮**（没有 {@code start_universal_setting_ui}）；</li>
 *   <li>它的 {@code <include>} 被写成 {@code layout_height="0dp"} 且没有 weight，
 *       而 {@code android:layout_weight="1"} 那行还落在 include 标签**外面**
 *       （成了一段无效文本）。</li>
 * </ul>
 * 结果这一整页高度恒为 0、永远显示不出来，里面四项设置**全都够不到**。
 * 本类负责把它接活：补上入口（见 ui_setting.xml）、修正高度，并在这里实现每个控件的行为。
 *
 * <p><b>★ 语言项是真正修好了一个坏功能</b>：{@code LocaleUtils.setLanguage()} 读的是
 * SharedPreferences {@code "lang"} 的 {@code lang} 键，而全仓**没有任何地方写它** ——
 * 也就是说切换语言从来没生效过。这里按 0/1/2/3（跟随系统/英文/简体/繁体）写入，
 * 与 {@code LocaleUtils.isChinese} / {@code getMinecraftLang} 的既有约定一致。
 */
public class UniversalSettingUI extends BaseUI
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemSelectedListener {

    /** 语言取值：与 LocaleUtils 的既有约定保持一致（0 跟随系统 / 1 英文 / 2 简体 / 3 繁体） */
    private static final int LANG_SYSTEM = 0;
    private static final int LANG_EN = 1;
    private static final int LANG_ZH_CN = 2;
    private static final int LANG_ZH_TW = 3;

    private LinearLayout root;
    private LinearLayout updateSetting;
    private LinearLayout cacheSetting;
    private LinearLayout showUpdateSetting;
    private LinearLayout showCacheSetting;
    private TextView updateStateText;
    private TextView cacheContentText;
    private ImageView showUpdate;
    private ImageView showCache;
    private RadioButton updateToRec;
    private RadioButton updateToBeta;
    private RadioButton cacheDefault;
    private RadioButton cacheCustom;
    private EditText editCachePath;
    private ImageButton selectCachePath;
    private Button clearCache;
    private Button exportLog;
    private Spinner languageSpinner;

    /** 抑制「程序化设置控件 → 触发回调」的抖动 */
    private boolean suppress = false;

    public UniversalSettingUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.root = this.activity.findViewById(R.id.ui_setting_universal);
        this.updateSetting = this.activity.findViewById(R.id.update_setting);
        this.cacheSetting = this.activity.findViewById(R.id.cache_setting);
        this.showUpdateSetting = this.activity.findViewById(R.id.show_update_setting);
        this.showCacheSetting = this.activity.findViewById(R.id.show_cache_setting);
        this.updateStateText = this.activity.findViewById(R.id.update_state_text);
        this.cacheContentText = this.activity.findViewById(R.id.cache_content_text);
        this.showUpdate = this.activity.findViewById(R.id.show_update);
        this.showCache = this.activity.findViewById(R.id.show_cache);
        this.updateToRec = this.activity.findViewById(R.id.update_to_rec);
        this.updateToBeta = this.activity.findViewById(R.id.update_to_beta);
        this.cacheDefault = this.activity.findViewById(R.id.check_default_cache_path);
        this.cacheCustom = this.activity.findViewById(R.id.check_custom_cache_path);
        this.editCachePath = this.activity.findViewById(R.id.edit_cache_path);
        this.selectCachePath = this.activity.findViewById(R.id.select_cache_path);
        this.clearCache = this.activity.findViewById(R.id.clear_cache);
        this.exportLog = this.activity.findViewById(R.id.export_log);
        this.languageSpinner = this.activity.findViewById(R.id.language_spinner);
        this.pageContainer = this.activity.findViewById(R.id.universal_setting_container);
        // ★ 「一键清理」区块是**代码里搭**的，没写进布局 ——
        //   它要展示三项实时占用、又要弹勾选确认框，纯布局反而更难维护。
        buildCleanupSection();

        click(showUpdateSetting, showUpdate, updateToRec, updateToBeta, cacheDefault, cacheCustom,
                selectCachePath, clearCache, exportLog);
        check(updateToRec, updateToBeta, cacheDefault, cacheCustom);

        // ★ 语言下拉：三项固定文案 + 当前选中值。放 Adapter 而不是硬编码下标，
        //   以后要加语言只需动这个数组。
        if (this.languageSpinner != null) {
            List<String> items = new ArrayList<String>();
            items.add(this.context.getString(R.string.qcl_lang_system));
            items.add(this.context.getString(R.string.qcl_lang_en));
            items.add(this.context.getString(R.string.qcl_lang_zh_cn));
            items.add(this.context.getString(R.string.qcl_lang_zh_tw));
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this.context,
                    android.R.layout.simple_spinner_dropdown_item, items);
            this.languageSpinner.setAdapter(adapter);
            this.languageSpinner.setOnItemSelectedListener(this);
        }
    }

    private void click(View... vs) {
        for (View v : vs) {
            if (v != null) {
                v.setOnClickListener(this);
            }
        }
    }

    private void check(CompoundButton... bs) {
        for (CompoundButton b : bs) {
            if (b != null) {
                b.setOnCheckedChangeListener(this);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // ★ 与其它设置子页保持一致：**不**调 showBarTitle。
        //   这一页是 ui_setting 内部的子页，顶部栏归设置页管；自己再调一次会多出一层返回栏。
        //   动画参数也用 false（同 ExteriorSettingUI），true 是「独立整页」的用法。
        CustomAnimationUtils.showViewFromLeft(this.root, this.activity, this.context, false);
        refreshAll();
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.root, this.activity, this.context, false);
    }

    // ------------------------------------------------------------------ 刷新

    private void refreshAll() {
        this.suppress = true;
        try {
            refreshUpdate();
            refreshCache();
            refreshLanguage();
            refreshCleanSizesAsync();
        } finally {
            this.suppress = false;
        }
    }

    private void refreshUpdate() {
        if (this.updateStateText != null) {
            String ver = "";
            try {
                ver = this.context.getPackageManager()
                        .getPackageInfo(this.context.getPackageName(), 0).versionName;
            } catch (Throwable ignored) {
            }
            this.updateStateText.setText(this.context.getString(R.string.qcl_update_current, ver));
        }
        boolean beta = this.activity.launcherSetting != null && this.activity.launcherSetting.getBetaVersion;
        if (this.updateToBeta != null) {
            this.updateToBeta.setChecked(beta);
        }
        if (this.updateToRec != null) {
            this.updateToRec.setChecked(!beta);
        }
        applyExpand(this.updateSetting, this.showUpdate, false);
    }

    private void refreshCache() {
        boolean custom = this.activity.launcherSetting != null
                && !TextUtils.isEmpty(this.activity.launcherSetting.cachePath);
        if (this.cacheCustom != null) {
            this.cacheCustom.setChecked(custom);
        }
        if (this.cacheDefault != null) {
            this.cacheDefault.setChecked(!custom);
        }
        if (this.editCachePath != null) {
            this.editCachePath.setText(custom ? this.activity.launcherSetting.cachePath : "");
            this.editCachePath.setEnabled(custom);
        }
        applyExpand(this.cacheSetting, this.showCache, false);
        refreshCacheSizeAsync();
    }

    private void refreshLanguage() {
        if (this.languageSpinner == null) {
            return;
        }
        int cur = this.context.getSharedPreferences("lang", 0).getInt("lang", LANG_SYSTEM);
        if (cur < 0 || cur > 3) {
            cur = LANG_SYSTEM;
        }
        this.languageSpinner.setSelection(cur);
    }

    private void applyExpand(View container, ImageView arrow, boolean expand) {
        if (container != null) {
            container.setVisibility(expand ? View.VISIBLE : View.GONE);
        }
        if (arrow != null) {
            arrow.setBackgroundResource(expand
                    ? R.drawable.ic_baseline_keyboard_arrow_up_black
                    : R.drawable.ic_baseline_keyboard_arrow_down_black);
        }
    }

    // ------------------------------------------------------------------ 缓存

    private File cacheDir() {
        try {
            String p = this.activity.launcherSetting == null ? null : this.activity.launcherSetting.cachePath;
            if (!TextUtils.isEmpty(p)) {
                return new File(p);
            }
        } catch (Throwable ignored) {
        }
        String def = AppManifest.DEFAULT_CACHE_DIR;
        return def == null ? null : new File(def);
    }

    private void refreshCacheSizeAsync() {
        if (this.cacheContentText == null) {
            return;
        }
        final File dir = cacheDir();
        if (dir == null) {
            this.cacheContentText.setText(R.string.qcl_cache_unknown);
            return;
        }
        this.cacheContentText.setText(R.string.qcl_cache_calculating);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long size = dirSize(dir);
                final int files = countFiles(dir);
                UniversalSettingUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (cacheContentText != null) {
                            cacheContentText.setText(UniversalSettingUI.this.context
                                    .getString(R.string.qcl_cache_usage, human(size), files));
                        }
                    }
                });
            }
        }, "qcl-cache-size").start();
    }

    private static long dirSize(File dir) {
        try {
            if (dir == null || !dir.isDirectory()) {
                return 0;
            }
            File[] fs = dir.listFiles();
            if (fs == null) {
                return 0;
            }
            long total = 0;
            for (File f : fs) {
                total += f.isDirectory() ? dirSize(f) : f.length();
            }
            return total;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int countFiles(File dir) {
        try {
            if (dir == null || !dir.isDirectory()) {
                return 0;
            }
            File[] fs = dir.listFiles();
            if (fs == null) {
                return 0;
            }
            int n = 0;
            for (File f : fs) {
                n += f.isDirectory() ? countFiles(f) : 1;
            }
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String human(long b) {
        if (b < 1024) {
            return b + " B";
        }
        if (b < 1024L * 1024) {
            return String.format("%.1f KB", b / 1024.0);
        }
        if (b < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", b / 1024.0 / 1024.0);
        }
        return String.format("%.2f GB", b / 1024.0 / 1024.0 / 1024.0);
    }

    /**
     * 清理缓存。
     *
     * <p>★ 只删**内容**、不删目录本身 —— 目录可能是用户自己指定的路径，
     * 把目录删掉会让那个路径失效。也刻意不做递归删除的「批量确认」：
     * 这里的目标只是 App 自己的 cache 目录，范围明确。
     */
    private void clearCache() {
        final File dir = cacheDir();
        if (dir == null || !dir.isDirectory()) {
            toast(R.string.qcl_cache_unknown);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                int ok = 0, fail = 0;
                File[] fs = dir.listFiles();
                if (fs != null) {
                    for (File f : fs) {
                        if (deleteRecursive(f)) {
                            ok++;
                        } else {
                            fail++;
                        }
                    }
                }
                final int fOk = ok, fFail = fail;
                UniversalSettingUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toastMsg(UniversalSettingUI.this.context
                                .getString(R.string.qcl_cache_cleared, fOk, fFail));
                        refreshCacheSizeAsync();
                    }
                });
            }
        }, "qcl-cache-clear").start();
    }

    private static boolean deleteRecursive(File f) {
        try {
            if (f.isDirectory()) {
                File[] fs = f.listFiles();
                if (fs != null) {
                    for (File c : fs) {
                        deleteRecursive(c);
                    }
                }
            }
            return f.delete();
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 导出日志

    /**
     * 把已知的日志文件打成一个 zip 放到 {@code /sdcard/QCL/} 下。
     *
     * <p>★ 为什么是「打包到公共目录」而不是直接分享：日志可能有几十 MB，
     * 走分享 Intent 容易被各家 IM 拦掉；给一个明确路径，用户自己用文件管理器就能取。
     */
    private void exportLog() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String outPath = null;
                String err = null;
                try {
                    File outDir = new File(AppManifest.LAUNCHER_DIR);
                    if (!outDir.exists()) {
                        outDir.mkdirs();
                    }
                    File zip = new File(outDir, "QCL-logs-"
                            + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                            .format(new java.util.Date()) + ".zip");
                    List<File> sources = collectLogs();
                    if (sources.isEmpty()) {
                        err = UniversalSettingUI.this.context.getString(R.string.qcl_export_log_none);
                    } else {
                        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip));
                        try {
                            for (File f : sources) {
                                addToZip(zos, f);
                            }
                        } finally {
                            zos.close();
                        }
                        outPath = zip.getAbsolutePath();
                    }
                } catch (Throwable t) {
                    err = String.valueOf(t);
                }
                final String fOut = outPath, fErr = err;
                UniversalSettingUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (fOut != null) {
                            toastMsg(UniversalSettingUI.this.context.getString(R.string.qcl_export_log_done, fOut));
                        } else {
                            toastMsg(UniversalSettingUI.this.context.getString(R.string.qcl_export_log_failed,
                                    fErr == null ? "?" : fErr));
                        }
                    }
                });
            }
        }, "qcl-export-log").start();
    }

    private List<File> collectLogs() {
        List<File> out = new ArrayList<File>();
        try {
            File ext = this.context.getExternalFilesDir(null);
            if (ext != null) {
                File t = new File(ext, "startup_trace.log");
                if (t.isFile()) {
                    out.add(t);
                }
                File c = new File(ext, "crash.log");
                if (c.isFile()) {
                    out.add(c);
                }
            }
            File in = this.context.getFilesDir();
            if (in != null) {
                File t = new File(in, "startup_trace.log");
                if (t.isFile()) {
                    out.add(t);
                }
            }
            File pub = new File(Environment.getExternalStorageDirectory(), "QCL/crash.log");
            if (pub.isFile()) {
                out.add(pub);
            }
            // 引擎侧日志目录（DEBUG_DIR）
            File debug = AppManifest.DEBUG_DIR == null ? null : new File(AppManifest.DEBUG_DIR);
            if (debug != null && debug.isDirectory()) {
                File[] fs = debug.listFiles();
                if (fs != null) {
                    for (File f : fs) {
                        if (f.isFile()) {
                            out.add(f);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void addToZip(ZipOutputStream zos, File f) {
        FileInputStream in = null;
        try {
            zos.putNextEntry(new ZipEntry(f.getName()));
            in = new FileInputStream(f);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                zos.write(buf, 0, n);
            }
            zos.closeEntry();
        } catch (Throwable ignored) {
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------------ 一键清理（#16）

    private LinearLayout pageContainer;
    private TextView cleanCacheSize;
    private TextView cleanLogSize;
    private TextView cleanBackupSize;

    /**
     * ★★★ 社区版新增：一键清理（缓存 / 日志 / 备份）。
     *
     * <p><b>为什么是「勾选式确认」而不是一个按钮直接清</b>：备份是**用户自己攒出来的**、
     * 删掉不可恢复的东西。把它和「缓存」「日志」放在同一个按钮后面一键删掉，
     * 迟早会有人误删。所以默认只勾「缓存 + 日志」，「备份」默认**不勾**，
     * 并且确认框里明确写出各项占用与后果。
     */
    private void buildCleanupSection() {
        if (this.pageContainer == null) {
            return;
        }
        try {
            View line = new View(this.context);
            line.setBackgroundColor(Color.parseColor("#33000000"));
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, px(1)));
            lineLp.topMargin = px(10);
            this.pageContainer.addView(line, lineLp);

            TextView title = new TextView(this.context);
            title.setText(R.string.qcl_clean_title);
            title.setTextSize(14f);
            title.setTextColor(Color.BLACK);
            title.setPadding(px(5), px(12), px(5), px(4));
            this.pageContainer.addView(title);

            this.cleanCacheSize = addCleanRow(R.string.qcl_clean_cache);
            this.cleanLogSize = addCleanRow(R.string.qcl_clean_log);
            this.cleanBackupSize = addCleanRow(R.string.qcl_clean_backup);

            Button b = new Button(this.context);
            b.setText(R.string.qcl_clean_button);
            b.setTextSize(13f);
            b.setAllCaps(false);
            b.setBackgroundResource(R.drawable.launcher_setting_button);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, px(40));
            blp.topMargin = px(12);
            b.setLayoutParams(blp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCleanupDialog();
                }
            });
            this.pageContainer.addView(b);
        } catch (Throwable ignored) {
        }
    }

    private TextView addCleanRow(int labelRes) {
        LinearLayout row = new LinearLayout(this.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(px(5), px(5), px(5), px(5));

        TextView l = new TextView(this.context);
        l.setText(labelRes);
        l.setTextSize(12f);
        l.setTextColor(Color.BLACK);
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);

        TextView v = new TextView(this.context);
        v.setText(R.string.qcl_cache_calculating);
        v.setTextSize(12f);
        v.setTextColor(Color.parseColor("#99000000"));
        row.addView(v);

        this.pageContainer.addView(row);
        return v;
    }

    private void refreshCleanSizesAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long c = dirSize(cacheDir());
                long logTotal = 0;
                for (File f : logTargets()) {
                    logTotal += dirSize(f);
                }
                long backupTotal = 0;
                for (File f : backupTargets()) {
                    backupTotal += dirSize(f);
                }
                final long fl = logTotal, fb = backupTotal;
                UniversalSettingUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (cleanCacheSize != null) {
                            cleanCacheSize.setText(human(c));
                        }
                        if (cleanLogSize != null) {
                            cleanLogSize.setText(human(fl));
                        }
                        if (cleanBackupSize != null) {
                            cleanBackupSize.setText(human(fb));
                        }
                    }
                });
            }
        }, "qcl-clean-size").start();
    }

    /** 日志相关目标：文件或目录都行。 */
    private List<File> logTargets() {
        List<File> out = new ArrayList<File>();
        try {
            File ext = this.context.getExternalFilesDir(null);
            if (ext != null) {
                out.add(new File(ext, "startup_trace.log"));
                out.add(new File(ext, "crash.log"));
            }
            File in = this.context.getFilesDir();
            if (in != null) {
                out.add(new File(in, "startup_trace.log"));
            }
            out.add(new File(Environment.getExternalStorageDirectory(), "QCL/crash.log"));
            if (AppManifest.DEBUG_DIR != null) {
                out.add(new File(AppManifest.DEBUG_DIR));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 备份目标：实例备份（App 私有）+ 存档备份（游戏目录下的 .qcl_worldbackups）。 */
    private List<File> backupTargets() {
        List<File> out = new ArrayList<File>();
        try {
            if (AppManifest.BACKUP_DIR != null) {
                out.add(new File(AppManifest.BACKUP_DIR));
            }
            String gameDir = null;
            if (this.activity.launcherSetting != null) {
                gameDir = this.activity.launcherSetting.gameFileDirectory;
            }
            if (!TextUtils.isEmpty(gameDir)) {
                out.add(new File(gameDir, ".qcl_worldbackups"));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private void showCleanupDialog() {
        final boolean[] checked = {true, true, false};
        final CharSequence[] items = {
                this.context.getString(R.string.qcl_clean_cache),
                this.context.getString(R.string.qcl_clean_log),
                this.context.getString(R.string.qcl_clean_backup),
        };
        try {
            new android.app.AlertDialog.Builder(this.context)
                    .setTitle(R.string.qcl_clean_title)
                    .setMultiChoiceItems(items, checked,
                            new android.content.DialogInterface.OnMultiChoiceClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int which, boolean isChecked) {
                                    checked[which] = isChecked;
                                }
                            })
                    .setMessage(R.string.qcl_clean_warning)
                    .setPositiveButton(R.string.qcl_clean_confirm,
                            new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int which) {
                                    doClean(checked[0], checked[1], checked[2]);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void doClean(final boolean cache, final boolean log, final boolean backup) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int ok = 0, fail = 0;
                if (cache) {
                    File d = cacheDir();
                    File[] fs = d == null ? null : d.listFiles();
                    if (fs != null) {
                        for (File f : fs) {
                            if (deleteRecursive(f)) {
                                ok++;
                            } else {
                                fail++;
                            }
                        }
                    }
                }
                if (log) {
                    for (File f : logTargets()) {
                        // ★ 日志目录只清内容、保留目录本身（免得后续写日志时又得重建）
                        if (f.isDirectory()) {
                            File[] fs = f.listFiles();
                            if (fs != null) {
                                for (File c : fs) {
                                    if (deleteRecursive(c)) {
                                        ok++;
                                    } else {
                                        fail++;
                                    }
                                }
                            }
                        } else if (f.exists()) {
                            if (deleteRecursive(f)) {
                                ok++;
                            } else {
                                fail++;
                            }
                        }
                    }
                }
                if (backup) {
                    for (File f : backupTargets()) {
                        File[] fs = f.isDirectory() ? f.listFiles() : null;
                        if (fs != null) {
                            for (File c : fs) {
                                if (deleteRecursive(c)) {
                                    ok++;
                                } else {
                                    fail++;
                                }
                            }
                        }
                    }
                }
                final int fOk = ok, fFail = fail;
                UniversalSettingUI.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toastMsg(UniversalSettingUI.this.context
                                .getString(R.string.qcl_clean_done, fOk, fFail));
                        refreshCacheSizeAsync();
                        refreshCleanSizesAsync();
                    }
                });
            }
        }, "qcl-clean").start();
    }

    private int px(int v) {
        return Math.round(v * this.context.getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ 事件

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.show_update_setting || id == R.id.show_update) {
            boolean expand = this.updateSetting != null
                    && this.updateSetting.getVisibility() != View.VISIBLE;
            applyExpand(this.updateSetting, this.showUpdate, expand);
            return;
        }
        if (id == R.id.show_cache_setting) {
            boolean expand = this.cacheSetting != null
                    && this.cacheSetting.getVisibility() != View.VISIBLE;
            applyExpand(this.cacheSetting, this.showCache, expand);
            return;
        }
        if (id == R.id.clear_cache) {
            clearCache();
            return;
        }
        if (id == R.id.export_log) {
            exportLog();
            return;
        }
        if (id == R.id.select_cache_path) {
            // ★ 复用工程已有的文件选择器（FileChooser），不另起一套。
            try {
                Intent intent = new Intent(this.context, com.tungsten.filepicker.FileChooser.class);
                intent.putExtra("SELECTION_MODE",
                        com.tungsten.filepicker.Constants.SELECTION_MODES.SINGLE_SELECTION.ordinal());
                intent.putExtra("ALLOWED_FILE_EXTENSIONS", "");
                intent.putExtra("INITIAL_DIRECTORY",
                        Environment.getExternalStorageDirectory().getAbsolutePath());
                this.activity.startActivityForResult(intent, REQUEST_PICK_CACHE_DIR);
            } catch (Throwable t) {
                toastMsg("打开文件选择器失败：" + t);
            }
        }
    }

    private static final int REQUEST_PICK_CACHE_DIR = 4200;

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (this.suppress || !isChecked) {
            return;   // 只处理「被选中」的那一下，避免两个 radio 互相触发
        }
        int id = buttonView.getId();
        if (id == R.id.update_to_rec || id == R.id.update_to_beta) {
            boolean beta = (id == R.id.update_to_beta);
            try {
                this.activity.launcherSetting.getBetaVersion = beta;
                GsonUtils.saveLauncherSetting(this.activity.launcherSetting,
                        AppManifest.SETTING_DIR + "/launcher_setting.json");
            } catch (Throwable ignored) {
            }
            toast(beta ? R.string.qcl_update_channel_beta : R.string.qcl_update_channel_rec);
            // 立刻按新通道查一次，用户当场能看到结果
            try {
                if (this.activity.updateChecker == null) {
                    this.activity.updateChecker = new UpdateChecker(this.context, this.activity);
                }
                this.activity.updateChecker.checkManually(null);
            } catch (Throwable ignored) {
            }
            return;
        }
        if (id == R.id.check_default_cache_path || id == R.id.check_custom_cache_path) {
            boolean custom = (id == R.id.check_custom_cache_path);
            if (this.editCachePath != null) {
                this.editCachePath.setEnabled(custom);
            }
            try {
                this.activity.launcherSetting.cachePath = custom ? "" : "";
                if (!custom) {
                    // 选「默认」= 清空自定义路径，回落到 App 自己的 cache 目录
                    this.activity.launcherSetting.cachePath = "";
                }
                GsonUtils.saveLauncherSetting(this.activity.launcherSetting,
                        AppManifest.SETTING_DIR + "/launcher_setting.json");
            } catch (Throwable ignored) {
            }
            if (this.editCachePath != null && !custom) {
                this.editCachePath.setText("");
            }
            refreshCacheSizeAsync();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (this.suppress) {
            return;
        }
        try {
            SharedPreferences sp = this.context.getSharedPreferences("lang", 0);
            int cur = sp.getInt("lang", LANG_SYSTEM);
            if (cur == position) {
                return;
            }
            sp.edit().putInt("lang", position).apply();
            toast(R.string.qcl_lang_need_restart);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    /**
     * 文件选择结果（自定义缓存目录）。
     *
     * <p>★ 走的是工程既有约定：{@code data.getData()} 拿到 Uri，再用
     * {@link com.qcl.launcher.utils.file.UriUtils#getRealPathFromUri_AboveApi19} 转成真实路径
     * （外观设置选背景图就是这么做的）。不要自己拼 {@code uri.getPath()} ——
     * 对 SAF 返回的 content:// 会得到一段无意义的内部 id。
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_CACHE_DIR || data == null || resultCode != -1) {
            return;
        }
        try {
            Uri uri = data.getData();
            if (uri == null) {
                return;
            }
            String path = com.qcl.launcher.utils.file.UriUtils.getRealPathFromUri_AboveApi19(this.context, uri);
            if (TextUtils.isEmpty(path)) {
                return;
            }
            File dir = new File(path);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (!dir.isDirectory()) {
                toastMsg("选择的不是目录：" + path);
                return;
            }
            this.activity.launcherSetting.cachePath = dir.getAbsolutePath();
            GsonUtils.saveLauncherSetting(this.activity.launcherSetting,
                    AppManifest.SETTING_DIR + "/launcher_setting.json");
            if (this.editCachePath != null) {
                this.editCachePath.setText(dir.getAbsolutePath());
            }
            refreshCacheSizeAsync();
        } catch (Throwable t) {
            toastMsg("设置缓存目录失败：" + t);
        }
    }

    // ------------------------------------------------------------------ 小工具

    private void toast(int res) {
        try {
            Toast.makeText(this.context, res, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private void toastMsg(String msg) {
        try {
            Toast.makeText(this.context, msg, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }
}
