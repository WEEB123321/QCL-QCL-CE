package com.qcl.launcher.launcher.server;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.LocaleUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ServerModsActivity extends AppCompatActivity {

    public static final String EXTRA_PROFILE = "profile";
    private static final int REQUEST_PICK_JARS = 4201;

    private ServerProfile profile;
    private File serverDir;
    private File contentDir;
    private String kind;
    private LinearLayout listContainer;
    private Button addButton;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppManifest.initializeManifest(getApplicationContext());
        super.onCreate(savedInstanceState);
        String path = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PROFILE);
        if (path != null && !path.isEmpty()) {
            File profileFile = new File(path);
            serverDir = profileFile.getParentFile();
            profile = ServerProfile.load(serverDir);
        }
        if (profile == null) {
            Toast.makeText(this, "服务器配置不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if ("paper".equals(profile.type) || "purpur".equals(profile.type)) {
            contentDir = new File(serverDir, "plugins");
            kind = "插件";
        } else if ("fabric".equals(profile.type) || "forge".equals(profile.type)) {
            contentDir = new File(serverDir, "mods");
            kind = "模组";
        } else {
            kind = "插件 / 模组";
        }
        setContentView(buildContent());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(0xEE111111);
        int pad = dp(18);
        root.setPadding(pad, dp(10), pad, pad);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(kind + "管理 · " + profile.name);
        title.setTextColor(0xFFF4F0E8);
        title.setTextSize(21f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText(contentDir == null
                ? "原版服务端不支持插件或模组"
                : contentDir.getAbsolutePath());
        subtitle.setTextColor(0xFFBDBDBD);
        subtitle.setTextSize(11f);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        addButton = new Button(this);
        addButton.setText("导入 .jar");
        addButton.setEnabled(contentDir != null);
        addButton.setOnClickListener(v -> pickJars());
        header.addView(addButton);
        root.addView(header);

        TextView note = new TextView(this);
        note.setText(contentDir == null
                ? "请创建 Paper、Fabric 或 Forge 服务端后再使用。"
                : "文件修改后重启服务端生效；删除会先移入 server/qcl-trash，可手动恢复。");
        note.setTextColor(0xFF9E9E9E);
        note.setTextSize(11f);
        note.setPadding(0, dp(8), 0, dp(8));
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void refresh() {
        if (listContainer == null || contentDir == null) {
            return;
        }
        listContainer.removeAllViews();
        if (!contentDir.isDirectory() && !contentDir.mkdirs()) {
            addEmpty("无法创建目录：" + contentDir);
            return;
        }
        File[] files = contentDir.listFiles(file -> file.isFile()
                && (file.getName().endsWith(".jar")
                || file.getName().endsWith(".jar.disabled")));
        if (files == null || files.length == 0) {
            addEmpty("还没有" + kind + "，点右上角导入 .jar");
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> a.getName()
                .compareToIgnoreCase(b.getName()));
        for (File file : files) {
            addFileRow(file);
        }
    }

    private void addEmpty(String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(0xFFBDBDBD);
        empty.setTextSize(14f);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(12), dp(40), dp(12), dp(40));
        listContainer.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addFileRow(File file) {
        boolean enabled = file.getName().endsWith(".jar");
        String display = enabled ? file.getName()
                : file.getName().substring(0, file.getName().length() - ".disabled".length());
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(enabled ? 0x22FFFFFF : 0x14FFFFFF);
        bg.setCornerRadius(dp(12));
        row.setBackground(bg);
        int pad = dp(12);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(display);
        name.setTextColor(enabled ? 0xFFF4F0E8 : 0xFF9E9E9E);
        name.setTextSize(14f);
        name.setMaxLines(2);
        TextView detail = new TextView(this);
        detail.setText((enabled ? "已启用" : "已停用") + "  ·  " + size(file.length()));
        detail.setTextColor(0xFF9E9E9E);
        detail.setTextSize(11f);
        texts.addView(name);
        texts.addView(detail);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button toggle = new Button(this);
        toggle.setText(enabled ? "停用" : "启用");
        ViewGroup.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                dp(70), dp(40));
        toggle.setLayoutParams(toggleParams);
        toggle.setOnClickListener(v -> toggleFile(file));
        row.addView(toggle);

        Button delete = new Button(this);
        delete.setText("删除");
        delete.setLayoutParams(new LinearLayout.LayoutParams(dp(70), dp(40)));
        delete.setOnClickListener(v -> deleteFile(file));
        row.addView(delete);

        listContainer.addView(row);
    }

    private void pickJars() {
        if (contentDir == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/java-archive", "application/x-java-archive",
                    "application/zip", "application/octet-stream"
            });
            startActivityForResult(intent, REQUEST_PICK_JARS);
        } catch (Throwable t) {
            toast("无法打开文件选择器：" + t.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_JARS || resultCode != RESULT_OK || data == null) {
            return;
        }
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) {
            return;
        }
        toast("正在导入 " + uris.size() + " 个文件");
        new Thread(() -> {
            int copied = 0;
            String lastError = null;
            for (Uri uri : uris) {
                try {
                    if (copyJar(uri)) {
                        copied++;
                    }
                } catch (Throwable t) {
                    lastError = t.getMessage();
                }
            }
            final int count = copied;
            final String error = lastError;
            runOnUiThread(() -> {
                refresh();
                toast(error == null
                        ? "已导入 " + count + " 个文件"
                        : "已导入 " + count + " 个，最后错误：" + error);
            });
        }, "qcl-server-jar-import").start();
    }

    private boolean copyJar(Uri uri) throws Exception {
        String fileName = displayName(uri);
        fileName = new File(fileName).getName();
        if (!fileName.toLowerCase(Locale.US).endsWith(".jar")) {
            throw new IllegalArgumentException(fileName + " 不是 .jar");
        }
        File target = new File(contentDir, fileName);
        if (target.exists()) {
            throw new IllegalStateException(fileName + " 已存在");
        }
        File part = new File(contentDir, fileName + ".part");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(part)) {
            if (in == null) {
                throw new IllegalStateException("无法读取 " + fileName);
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
        }
        try (java.util.zip.ZipFile ignored = new java.util.zip.ZipFile(part)) {
            // Opening the zip validates that the jar is not truncated.
        } catch (Throwable t) {
            part.delete();
            throw new IllegalStateException(fileName + " 不是有效的 jar");
        }
        if (!part.renameTo(target)) {
            part.delete();
            throw new IllegalStateException("无法保存 " + fileName);
        }
        return true;
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        String path = uri.getLastPathSegment();
        return path == null ? "mod.jar" : path;
    }

    private void toggleFile(File file) {
        String name = file.getName();
        boolean enabled = name.endsWith(".jar");
        String next = enabled ? name + ".disabled"
                : name.substring(0, name.length() - ".disabled".length());
        File target = new File(file.getParentFile(), next);
        if (target.exists()) {
            toast("目标文件已存在：" + next);
            return;
        }
        if (!file.renameTo(target)) {
            toast("重命名失败，请检查存储权限");
            return;
        }
        refresh();
    }

    private void deleteFile(File file) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("移入回收目录")
                .setMessage(file.getName() + "\n\n不会直接删除，可从 qcl-trash 恢复。")
                .setPositiveButton("移入", (d, w) -> {
                    try {
                        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss",
                                Locale.US).format(new Date());
                        File trash = new File(serverDir, "qcl-trash/" + stamp);
                        if (!trash.isDirectory() && !trash.mkdirs()) {
                            throw new IllegalStateException("无法创建回收目录");
                        }
                        if (!file.renameTo(new File(trash, file.getName()))) {
                            throw new IllegalStateException("无法移动文件");
                        }
                        refresh();
                        toast("已移入 " + trash.getAbsolutePath());
                    } catch (Throwable t) {
                        toast("删除失败：" + t.getMessage());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
