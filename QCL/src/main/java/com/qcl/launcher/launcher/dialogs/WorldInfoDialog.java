package com.qcl.launcher.launcher.dialogs;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.game.World;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.io.ZipTools;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ★★★ 社区版新增：世界信息面板（读存档文件，展示种子 / 版本等）。
 *
 * <p>数据全部来自存档自己的 {@code level.dat}（gzip 过的 NBT），**不联网、不猜**。
 *
 * <p>顺带把「种子分享」做在同一处：面板上直接能复制种子、复制一段带版本信息的分享码；
 * 也能把整个存档打成 zip 分享出去。
 */
public final class WorldInfoDialog {

    private WorldInfoDialog() {
    }

    public static void show(final Context context, final MainActivity activity, final World world) {
        if (world == null) {
            return;
        }
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        box.setPadding(pad, dp(context, 8), pad, dp(context, 8));

        addRow(context, box, context.getString(R.string.world_info_name),
                safe(world.getWorldName()));
        addRow(context, box, context.getString(R.string.world_info_dir),
                safe(world.getFileName()));
        addRow(context, box, context.getString(R.string.world_info_version),
                world.getGameVersion() == null
                        ? context.getString(R.string.world_manager_ui_unknown_game_version)
                        : world.getGameVersion());
        addRow(context, box, context.getString(R.string.world_info_last_played),
                world.getLastPlayed() <= 0
                        ? context.getString(R.string.world_info_unknown)
                        : new SimpleDateFormat(context.getString(R.string.time_pattern), Locale.getDefault())
                        .format(new Date(world.getLastPlayed())));
        addRow(context, box, context.getString(R.string.world_info_mode), gameModeText(context, world));
        addRow(context, box, context.getString(R.string.world_info_difficulty), difficultyText(context, world));
        addRow(context, box, context.getString(R.string.world_info_hardcore),
                world.isHardcore() ? context.getString(R.string.world_info_yes)
                        : context.getString(R.string.world_info_no));
        addRow(context, box, context.getString(R.string.world_info_cheats),
                world.isAllowCommands() ? context.getString(R.string.world_info_yes)
                        : context.getString(R.string.world_info_no));

        // 种子单独一行，做成「可以长按选中复制」的样式 —— 玩家最常见的动作就是抄这个数
        final String seedText = world.hasSeed() ? String.valueOf(world.getSeed())
                : context.getString(R.string.world_info_no_seed);
        TextView seedValue = addRow(context, box, context.getString(R.string.world_info_seed), seedText);
        seedValue.setTextIsSelectable(true);
        seedValue.setTextColor(Color.parseColor("#FF185FA5"));

        // 目录占用（异步算，别卡住弹窗）
        final TextView sizeValue = addRow(context, box, context.getString(R.string.world_info_size),
                context.getString(R.string.world_info_calculating));
        final File dir = new File(world.getFile().toString());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long size = dirSize(dir);
                final int files = countFiles(dir);
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            sizeValue.setText(context.getString(R.string.world_info_size_value,
                                    human(size), files));
                        }
                    });
                }
            }
        }, "qcl-world-size").start();

        ScrollView scroll = new ScrollView(context);
        scroll.addView(box);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.world_info_title)
                .setView(scroll)
                .setPositiveButton(R.string.world_info_close, null)
                .setNeutralButton(R.string.world_info_copy_seed, null)
                .setNegativeButton(R.string.world_info_share_save, null)
                .create();
        dialog.show();
        // ★ 复制 / 分享按钮要「点了不关窗」—— 用户常常要连着复制种子再分享存档。
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!world.hasSeed()) {
                    toast(context, context.getString(R.string.world_info_no_seed));
                    return;
                }
                copy(context, shareText(context, world));
                toast(context, context.getString(R.string.world_info_copied));
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportAndShare(context, activity, world);
            }
        });
    }

    // ------------------------------------------------------------------ 分享

    /** 一段「人能看懂、也能直接发给朋友」的分享文本。 */
    private static String shareText(Context context, World world) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(R.string.world_info_share_header)).append('\n');
        sb.append(context.getString(R.string.world_info_name)).append('：').append(safe(world.getWorldName())).append('\n');
        sb.append(context.getString(R.string.world_info_version)).append('：')
                .append(world.getGameVersion() == null ? "?" : world.getGameVersion()).append('\n');
        sb.append(context.getString(R.string.world_info_seed)).append('：').append(world.getSeed()).append('\n');
        // 机器可读的一行：以后要做「粘贴分享码直接建世界」时靠它
        sb.append("QCL1|").append(world.getGameVersion() == null ? "?" : world.getGameVersion())
                .append('|').append(world.getSeed());
        return sb.toString();
    }

    /** 把存档目录打成 zip 放到 /sdcard/QCL/，再通过 FileProvider 分享出去。 */
    private static void exportAndShare(final Context context, final MainActivity activity, final World world) {
        toast(context, context.getString(R.string.world_info_packing));
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
                    String safeName = safe(world.getWorldName()).replaceAll("[\\\\/:*?\"<>|]", "_");
                    String zipName = safeName + "-"
                            + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                            + ".zip";
                    ZipTools.zip(world.getFile().toString(), outDir.getAbsolutePath(), zipName);
                    File zip = new File(outDir, zipName);
                    if (zip.isFile()) {
                        outPath = zip.getAbsolutePath();
                    }
                } catch (Throwable t) {
                    err = String.valueOf(t);
                }
                final String fOut = outPath, fErr = err;
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (fOut == null) {
                            toast(context, context.getString(R.string.world_info_share_failed,
                                    fErr == null ? "?" : fErr));
                            return;
                        }
                        shareFile(context, fOut);
                    }
                });
            }
        }, "qcl-world-export").start();
    }

    private static void shareFile(Context context, String path) {
        try {
            File f = new File(path);
            // ★ 复用 FilePicker 模块已经声明好的那个 FileProvider —— 它的 authority 是
            //   @string/filebrowser_provider，CE 版里就是 com.qcl.launcher.ce.filepicker.provider。
            //   这里用 getPackageName() 拼而不是直接读 R.string：既避开库模块 R 类的可见性问题，
            //   也让同一个 apk 在「CE 版 / 官方版」下都能算对。
            //   它的路径表是 external-path path="."（整个外部存储），/sdcard/QCL 在其中。
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.getPackageName() + ".filepicker.provider", f);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent,
                    context.getString(R.string.world_info_share_save)));
        } catch (Throwable t) {
            // 分享面板打不开（例如没有能收 zip 的应用）时，至少把路径告诉用户
            toast(context, context.getString(R.string.world_info_share_path, path));
        }
    }

    // ------------------------------------------------------------------ 工具

    private static TextView addRow(Context context, LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(context, 6), 0, dp(context, 6));

        TextView l = new TextView(context);
        l.setText(label);
        l.setTextSize(12f);
        l.setTextColor(Color.parseColor("#99000000"));
        l.setMinWidth(dp(context, 72));
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

    private static String gameModeText(Context context, World w) {
        switch (w.getGameType()) {
            case 0:
                return context.getString(R.string.world_info_mode_survival);
            case 1:
                return context.getString(R.string.world_info_mode_creative);
            case 2:
                return context.getString(R.string.world_info_mode_adventure);
            case 3:
                return context.getString(R.string.world_info_mode_spectator);
            default:
                return context.getString(R.string.world_info_unknown);
        }
    }

    private static String difficultyText(Context context, World w) {
        switch (w.getDifficulty()) {
            case 0:
                return context.getString(R.string.world_info_diff_peaceful);
            case 1:
                return context.getString(R.string.world_info_diff_easy);
            case 2:
                return context.getString(R.string.world_info_diff_normal);
            case 3:
                return context.getString(R.string.world_info_diff_hard);
            default:
                return context.getString(R.string.world_info_unknown);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static long dirSize(File dir) {
        try {
            if (dir == null || !dir.isDirectory()) {
                return dir == null ? 0 : dir.length();
            }
            File[] fs = dir.listFiles();
            if (fs == null) {
                return 0;
            }
            long t = 0;
            for (File f : fs) {
                t += f.isDirectory() ? dirSize(f) : f.length();
            }
            return t;
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
            return String.format(Locale.US, "%.1f KB", b / 1024.0);
        }
        if (b < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", b / 1024.0 / 1024.0);
        }
        return String.format(Locale.US, "%.2f GB", b / 1024.0 / 1024.0 / 1024.0);
    }

    private static void copy(Context context, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("QCL", text));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void toast(Context context, String msg) {
        try {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private static int dp(Context context, int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
