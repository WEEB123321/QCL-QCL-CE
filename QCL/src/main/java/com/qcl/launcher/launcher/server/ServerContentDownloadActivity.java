package com.qcl.launcher.launcher.server;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qcl.launcher.launcher.list.download.ModIconLoader;
import com.qcl.launcher.launcher.mod.ModLoaderType;
import com.qcl.launcher.launcher.mod.RemoteMod;
import com.qcl.launcher.launcher.mod.RemoteModRepository;
import com.qcl.launcher.launcher.mod.curse.CurseForgeRemoteModRepository;
import com.qcl.launcher.launcher.mod.modrinth.ModrinthRemoteModRepository;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.task.DownloadTask;
import com.qcl.launcher.utils.LocaleUtils;
import com.qcl.launcher.utils.io.DownloadUtil;
import com.qcl.launcher.utils.io.MirrorUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** 给手机服务端快速搜索并下载插件/模组，保存到 plugins 或 mods。 */
public class ServerContentDownloadActivity extends AppCompatActivity {

    public static final String EXTRA_PROFILE = "profile";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<RemoteMod> results = new ArrayList<>();

    private File profileFile;
    private File serverDir;
    private File contentDir;
    private ServerProfile profile;
    private boolean plugins;

    private EditText searchInput;
    private Spinner sourceSpinner;
    private ListView listView;
    private ProgressBar loading;
    private TextView status;
    private TextView footer;
    private RemoteModRepository repository;
    private ModAdapter adapter;
    private boolean searching;
    private int searchToken;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppManifest.initializeManifest(getApplicationContext());
        super.onCreate(savedInstanceState);
        String path = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PROFILE);
        if (path == null || path.isEmpty()) {
            toast("缺少服务器配置");
            finish();
            return;
        }
        profileFile = new File(path);
        serverDir = profileFile.getParentFile();
        profile = ServerProfile.load(serverDir);
        if (profile == null) {
            toast("服务器配置损坏");
            finish();
            return;
        }
        if ("paper".equals(profile.type) || "purpur".equals(profile.type)) {
            plugins = true;
            contentDir = new File(serverDir, "plugins");
        } else if ("fabric".equals(profile.type) || "forge".equals(profile.type)
                || "neoforge".equals(profile.type)) {
            plugins = false;
            contentDir = new File(serverDir, "mods");
        } else {
            toast("原版服务端不支持插件或模组");
            finish();
            return;
        }
        if (!contentDir.isDirectory() && !contentDir.mkdirs()) {
            toast("无法创建目录：" + contentDir);
            finish();
            return;
        }
        setContentView(buildContent());
        repository = repositoryForSource();
        runSearch();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(0xFF111111);
        int pad = dp(18);
        root.setPadding(pad, dp(10), pad, dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(plugins ? "插件下载" : "模组下载");
        title.setTextColor(0xFFF4F0E8);
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText(profile.typeLabel() + " " + profile.version
                + " · 保存到 " + contentDir.getAbsolutePath());
        subtitle.setTextColor(0xFFBDBDBD);
        subtitle.setTextSize(11f);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(this);
        close.setText("返回");
        close.setOnClickListener(v -> finish());
        header.addView(close);
        root.addView(header);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0, dp(10), 0, dp(6));
        searchInput = new EditText(this);
        searchInput.setHint("搜索" + (plugins ? "插件" : "模组") + "名");
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(0x80FFFFFF);
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sourceSpinner = new Spinner(this);
        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Modrinth", "CurseForge"});
        sourceSpinner.setAdapter(sourceAdapter);
        sourceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                repository = repositoryForSource();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                dp(122), ViewGroup.LayoutParams.WRAP_CONTENT);
        sourceParams.leftMargin = dp(8);
        searchRow.addView(sourceSpinner, sourceParams);
        Button search = new Button(this);
        search.setText("搜索");
        search.setOnClickListener(v -> runSearch());
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.leftMargin = dp(8);
        searchRow.addView(search, searchParams);
        root.addView(searchRow);

        status = new TextView(this);
        status.setTextColor(0xFFBDBDBD);
        status.setTextSize(12f);
        status.setPadding(0, dp(2), 0, dp(4));
        root.addView(status);

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        root.addView(loading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        listView = new ListView(this);
        listView.setDividerHeight(dp(6));
        listView.setDivider(null);
        listView.setVisibility(View.GONE);
        adapter = new ModAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                showVersions(results.get(position)));
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        footer = new TextView(this);
        footer.setTextColor(0xFF9E9E9E);
        footer.setTextSize(11f);
        footer.setText("下载后重启服务端生效；前置/依赖请按说明单独下载。");
        footer.setPadding(0, dp(6), 0, 0);
        root.addView(footer);
        return root;
    }

    private RemoteModRepository repositoryForSource() {
        boolean modrinth = sourceSpinner == null || sourceSpinner.getSelectedItemPosition() == 0;
        if (plugins) {
            return modrinth ? ModrinthRemoteModRepository.PLUGINS
                    : CurseForgeRemoteModRepository.BUKKIT_PLUGINS;
        }
        return modrinth ? ModrinthRemoteModRepository.MODS
                : CurseForgeRemoteModRepository.MODS;
    }

    private void runSearch() {
        if (searching || repository == null) {
            return;
        }
        final String query = searchInput.getText() == null
                ? "" : searchInput.getText().toString().trim();
        final RemoteModRepository repo = repository;
        final int token = ++searchToken;
        searching = true;
        loading.setVisibility(View.VISIBLE);
        status.setText("正在搜索…");
        listView.setVisibility(View.GONE);
        new Thread(() -> {
            try {
                List<RemoteMod> list = repo.search(profile.version, null, 0, 50, query,
                                RemoteModRepository.SortType.TOTAL_DOWNLOADS,
                                RemoteModRepository.SortOrder.DESC)
                        .collect(Collectors.toList());
                main.post(() -> {
                    if (token != searchToken || isFinishing() || isDestroyed()) {
                        return;
                    }
                    searching = false;
                    loading.setVisibility(View.GONE);
                    results.clear();
                    results.addAll(list);
                    adapter.notifyDataSetChanged();
                    listView.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                    status.setText(list.isEmpty()
                            ? "没有找到" + (plugins ? "插件" : "模组")
                            : "共 " + list.size() + " 个结果");
                });
            } catch (Throwable t) {
                main.post(() -> {
                    if (token != searchToken || isFinishing() || isDestroyed()) {
                        return;
                    }
                    searching = false;
                    loading.setVisibility(View.GONE);
                    status.setText("搜索失败：" + shortError(t));
                });
            }
        }, "qcl-server-content-search").start();
    }

    private void showVersions(RemoteMod mod) {
        TextView loadingText = new TextView(this);
        loadingText.setText("正在读取版本…");
        loadingText.setPadding(dp(20), dp(18), dp(20), dp(18));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(mod.getTitle())
                .setView(loadingText)
                .setNegativeButton("关闭", null)
                .create();
        dialog.show();
        final RemoteModRepository repo = repository;
        new Thread(() -> {
            List<RemoteMod.Version> compatible = new ArrayList<>();
            String error = null;
            try {
                List<RemoteMod.Version> all = mod.getData().loadVersions(repo)
                        .collect(Collectors.toList());
                for (RemoteMod.Version version : all) {
                    if (version.getFile() == null || version.getFile().getUrl() == null) {
                        continue;
                    }
                    List<String> gameVersions = version.getGameVersions();
                    if (gameVersions == null || !gameVersions.contains(profile.version)) {
                        continue;
                    }
                    if (!plugins && !"neoforge".equals(profile.type)) {
                        List<ModLoaderType> loaders = version.getLoaders();
                        if (loaders != null && !loaders.isEmpty()) {
                            ModLoaderType wanted = "fabric".equals(profile.type)
                                    ? ModLoaderType.FABRIC : ModLoaderType.FORGE;
                            if (!loaders.contains(wanted)) {
                                continue;
                            }
                        }
                    }
                    compatible.add(version);
                }
                compatible.sort((a, b) -> {
                    Date da = a.getDatePublished();
                    Date db = b.getDatePublished();
                    if (da == null && db == null) return 0;
                    if (da == null) return 1;
                    if (db == null) return -1;
                    return db.compareTo(da);
                });
            } catch (Throwable t) {
                error = shortError(t);
            }
            final List<RemoteMod.Version> list = compatible;
            final String failure = error;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                if (failure != null) {
                    new AlertDialog.Builder(this)
                            .setTitle("读取版本失败")
                            .setMessage(failure)
                            .setPositiveButton("知道了", null)
                            .show();
                    return;
                }
                if (list.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setTitle("没有可用版本")
                            .setMessage("没有找到适配 Minecraft " + profile.version
                                    + " 的版本。\n请确认服务端版本，或换一个" + (plugins ? "插件" : "模组") + "。")
                            .setPositiveButton("知道了", null)
                            .show();
                    return;
                }
                String[] labels = new String[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    labels[i] = versionLabel(list.get(i));
                }
                new AlertDialog.Builder(this)
                        .setTitle("选择版本 · " + list.size() + " 个")
                        .setItems(labels, (d, which) ->
                                confirmDownload(mod, list.get(which)))
                        .setNegativeButton("取消", null)
                        .show();
            });
        }, "qcl-server-content-versions").start();
    }

    private String versionLabel(RemoteMod.Version version) {
        String fileName = fileName(version);
        String display = version.getName() == null || version.getName().trim().isEmpty()
                ? fileName : version.getName();
        List<String> gameVersions = version.getGameVersions();
        String games = gameVersions == null || gameVersions.isEmpty()
                ? profile.version : String.join(" / ", gameVersions);
        String date = version.getDatePublished() == null ? "未知日期"
                : new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(version.getDatePublished());
        return display + "\n" + fileName + "\n" + games + " · " + date;
    }

    private void confirmDownload(RemoteMod mod, RemoteMod.Version version) {
        String name = fileName(version);
        File target = new File(contentDir, name);
        if (target.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("文件已存在")
                    .setMessage(name + "\n\n覆盖会替换服务端现有的同名文件。")
                    .setPositiveButton("覆盖", (d, w) -> download(mod, version, target))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        download(mod, version, target);
    }

    private void download(RemoteMod mod, RemoteMod.Version version, File target) {
        final String name = target.getName();
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("正在下载\n" + name);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            boolean ok = false;
            String error = null;
            File part = new File(contentDir, name + ".part");
            try {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                String rawUrl = version.getFile().getUrl();
                String mirror = MirrorUtils.rewriteCdn(rawUrl);
                String sha1 = version.getFile().getHashes() == null
                        ? null : version.getFile().getHashes().get("sha1");
                DownloadTask.DownloadFeedback feedback = new DownloadTask.DownloadFeedback() {
                    @Override
                    public void updateProgress(long current, long max) {
                        int percent = max > 0
                                ? (int) Math.min(100L, current * 100L / max) : 0;
                        main.post(() -> {
                            if (progress.isShowing()) {
                                progress.setProgress(percent);
                            }
                        });
                    }

                    @Override
                    public void updateSpeed(String speed) {
                        main.post(() -> {
                            if (progress.isShowing()) {
                                progress.setMessage("正在下载\n" + name
                                        + (speed == null ? "" : "\n" + speed));
                            }
                        });
                    }
                };
                ok = downloadFile(mirror, part, sha1, feedback);
                if (!ok && !mirror.equals(rawUrl)) {
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                    ok = downloadFile(rawUrl, part, sha1, feedback);
                }
                if (!ok) {
                    throw new java.io.IOException("下载失败");
                }
                try (java.util.zip.ZipFile ignored = new java.util.zip.ZipFile(part)) {
                    // 打开一次确认不是截断的 jar；插件/模组都必须是有效 zip。
                } catch (Throwable t) {
                    throw new java.io.IOException("文件不是有效的 jar");
                }
                if (target.exists() && !target.delete()) {
                    throw new java.io.IOException("无法覆盖 " + name);
                }
                if (!part.renameTo(target)) {
                    throw new java.io.IOException("无法保存 " + name);
                }
            } catch (Throwable t) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                error = shortError(t);
            }
            final boolean success = ok && error == null;
            final String failure = error;
            main.post(() -> {
                try {
                    if (progress.isShowing()) {
                        progress.dismiss();
                    }
                } catch (Throwable ignored) {
                }
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (success) {
                    new AlertDialog.Builder(this)
                            .setTitle("下载完成")
                            .setMessage(name + "\n\n已保存到 " + contentDir.getAbsolutePath()
                                    + "\n重启服务端后生效。")
                            .setPositiveButton("继续浏览", null)
                            .setNeutralButton("打开控制台", (d, w) -> {
                                startActivity(new Intent(this, ServerConsoleActivity.class)
                                        .putExtra(ServerConsoleActivity.EXTRA_PROFILE,
                                                profileFile.getAbsolutePath()));
                            })
                            .show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("下载失败")
                            .setMessage(failure == null ? "未知错误" : failure)
                            .setPositiveButton("知道了", null)
                            .show();
                }
            });
        }, "qcl-server-content-download").start();
    }

    private static boolean downloadFile(String url, File out, String sha1,
                                        DownloadTask.DownloadFeedback feedback) {
        try {
            return DownloadUtil.downloadFile(url, out.getAbsolutePath(), sha1, feedback);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String fileName(RemoteMod.Version version) {
        String name = version.getFile() == null ? null : version.getFile().getFilename();
        if (name == null || name.trim().isEmpty()) {
            name = version.getModid() + "-" + version.getVersion() + ".jar";
        }
        name = new File(name).getName();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            name = name + ".jar";
        }
        return name;
    }

    private static String shortError(Throwable t) {
        String message = t.getMessage();
        return message == null || message.trim().isEmpty()
                ? t.getClass().getSimpleName() : message;
    }

    private final class ModAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return results.size();
        }

        @Override
        public Object getItem(int position) {
            return results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RemoteMod mod = results.get(position);
            LinearLayout row = new LinearLayout(ServerContentDownloadActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            row.setBackgroundColor(0x22FFFFFF);
            ImageView icon = new ImageView(ServerContentDownloadActivity.this);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
            ModIconLoader.load(ServerContentDownloadActivity.this, icon, mod.getIconUrl(), position);
            LinearLayout texts = new LinearLayout(ServerContentDownloadActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(10), 0, 0, 0);
            TextView name = new TextView(ServerContentDownloadActivity.this);
            name.setText(mod.getTitle());
            name.setTextColor(0xFFF4F0E8);
            name.setTextSize(15f);
            name.setTypeface(null, Typeface.BOLD);
            TextView desc = new TextView(ServerContentDownloadActivity.this);
            desc.setText(mod.getDescription());
            desc.setTextColor(0xFFBDBDBD);
            desc.setTextSize(11f);
            desc.setMaxLines(2);
            TextView meta = new TextView(ServerContentDownloadActivity.this);
            String categories = mod.getCategories() == null ? ""
                    : String.join(" · ", mod.getCategories());
            meta.setText((mod.getAuthor() == null ? "" : mod.getAuthor() + " · ") + categories);
            meta.setTextColor(0xFF9E9E9E);
            meta.setTextSize(10f);
            meta.setMaxLines(1);
            texts.addView(name);
            texts.addView(desc);
            texts.addView(meta);
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return row;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
