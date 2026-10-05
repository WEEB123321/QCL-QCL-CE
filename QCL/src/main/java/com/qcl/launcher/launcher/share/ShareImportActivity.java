package com.qcl.launcher.launcher.share;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.setting.InitializeSetting;
import com.qcl.launcher.launcher.setting.launcher.LauncherSetting;
import com.qcl.launcher.utils.LocaleUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * ★★★ 社区版新增：从其它应用导入文件（系统分享 / 打开方式）。
 *
 * <p><b>为什么需要它</b>：Android 上「把整合包 / 资源包 / 皮肤从聊天软件发到启动器」
 * 是最自然的操作路径，但在此之前 QCL 的 {@code ImportControlActivity} 是个<b>空壳</b>
 * （收到 intent 后什么都不做），等于分享过来什么也不会发生。
 *
 * <p><b>★ 不猜用户想干什么，而是让他选</b>：同一个 {@code .zip} 既可能是资源包也可能是整合包，
 * 猜错会把文件丢到错的地方。所以这里先弹一个明确的选项框，把「放哪儿」交给用户。
 *
 * <p><b>★ 只做「放到位」这一步</b>：真正的「安装整合包」要走启动器里那套带校验的流程，
 * 本页不重复实现 —— 它只负责把文件落到正确的目录，然后告诉用户下一步去哪。
 * 宁可少做一步，也不做一条绕过校验的野路子安装。
 */
public class ShareImportActivity extends AppCompatActivity {

    /** 从系统分享进来的 URI */
    private Uri sourceUri;
    /** 已落盘的临时文件（扩展名决定给什么选项） */
    private File staged;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLanguage(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            this.sourceUri = resolveUri(getIntent());
            if (this.sourceUri == null) {
                toast(R.string.share_import_no_file);
                finish();
                return;
            }
            // ★ 分享过来的可能是「链接」而不是文件（例如聊天里发的是网盘地址）。
            //   对链接我们无从下载、也不该替用户猜，直接交给浏览器打开 ——
            //   比弹一句「导入失败」有用得多。
            String scheme = this.sourceUri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, this.sourceUri));
                } catch (Throwable ignored) {
                }
                finish();
                return;
            }
            // 复制到本地缓存：分享过来的 URI 常常只有一次性读权限，
            // 不先落地的话，用户点完选项再去读可能已经拿不到了。
            this.staged = stage(this.sourceUri);
            if (this.staged == null) {
                toast(R.string.share_import_failed);
                finish();
                return;
            }
            showChooser();
        } catch (Throwable t) {
            toast(R.string.share_import_failed);
            finish();
        }
    }

    // ------------------------------------------------------------------ URI 解析

    private Uri resolveUri(Intent intent) {
        if (intent == null) {
            return null;
        }
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Object extra = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (extra instanceof Uri) {
                return (Uri) extra;
            }
            // 有些应用只给文本（例如一个下载链接）
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && (text.startsWith("http://") || text.startsWith("https://"))) {
                return Uri.parse(text);
            }
            return null;
        }
        // ACTION_VIEW / ACTION_MAIN 等
        return intent.getData();
    }

    /** 把内容落到应用缓存目录，返回落地后的文件；失败返回 null。 */
    private File stage(Uri uri) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            File dir = new File(getCacheDir(), "import");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            String name = guessName(uri);
            File dst = new File(dir, name);
            out = new FileOutputStream(dst, false);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return dst;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** 猜一个安全的文件名：优先用 URI 的最后一段，否则按时间戳。 */
    private String guessName(Uri uri) {
        String last = uri.getLastPathSegment();
        String ext = extensionOf(last);
        String base = "import_" + System.currentTimeMillis();
        return base + ext;
    }

    private String extensionOf(String path) {
        if (path == null) {
            return "";
        }
        String lower = path.toLowerCase();
        for (String e : new String[]{".mrpack", ".zip", ".png", ".jpg", ".jpeg", ".jar", ".json"}) {
            if (lower.endsWith(e)) {
                return e;
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ 选项

    private void showChooser() {
        final String ext = extensionOf(this.staged.getName());
        final String[] labels;
        final String[] targets;
        if (isImage(ext)) {
            labels = new String[]{getString(R.string.share_import_as_background),
                    getString(R.string.share_import_as_resourcepack)};
            targets = new String[]{"background", "resourcepacks"};
        } else if (".mrpack".equals(ext) || ".json".equals(ext)) {
            labels = new String[]{getString(R.string.share_import_as_modpack)};
            targets = new String[]{"modpack"};
        } else {
            labels = new String[]{getString(R.string.share_import_as_resourcepack),
                    getString(R.string.share_import_as_shaderpack),
                    getString(R.string.share_import_as_modpack)};
            targets = new String[]{"resourcepacks", "shaderpacks", "modpack"};
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.share_import_title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        apply(targets[which]);
                    }
                })
                .setNegativeButton(R.string.legacy_cn_lang_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finish();
                    }
                })
                .setCancelable(false)
                .create().show();
    }

    private void apply(String target) {
        try {
            if ("background".equals(target)) {
                LauncherSetting s = InitializeSetting.initializeLauncherSetting();
                if (s != null) {
                    // 把图片复制到应用私有目录，避免用户删掉缓存后背景失效
                    File dst = new File(getFilesDir(), "imported_bg" + extensionOf(this.staged.getName()));
                    if (copyFile(this.staged, dst)) {
                        s.launcherBackground.type = 2;
                        s.launcherBackground.path = dst.getAbsolutePath();
                        com.qcl.launcher.utils.gson.GsonUtils.saveLauncherSetting(
                                s, com.qcl.launcher.manifest.AppManifest.SETTING_DIR + "/launcher_setting.json");
                        toast(R.string.share_import_background_done);
                        finish();
                        return;
                    }
                }
                toast(R.string.share_import_failed);
                finish();
                return;
            }

            LauncherSetting s = InitializeSetting.initializeLauncherSetting();
            String gameDir = s == null ? null : s.gameFileDirectory;
            if (gameDir == null || gameDir.isEmpty()) {
                toast(R.string.share_import_failed);
                finish();
                return;
            }
            File dir;
            if ("modpack".equals(target)) {
                // 整合包不直接装 —— 放到一个显眼的地方，让用户去启动器的安装流程里选它
                dir = new File(gameDir, "../imported_modpacks");
            } else {
                dir = new File(gameDir, target);
            }
            if (!dir.exists() && !dir.mkdirs()) {
                toast(R.string.share_import_failed);
                finish();
                return;
            }
            File dst = new File(dir, this.staged.getName());
            if (copyFile(this.staged, dst)) {
                toast(getString(R.string.share_import_done, dst.getAbsolutePath()));
            } else {
                toast(R.string.share_import_failed);
            }
        } catch (Throwable t) {
            toast(R.string.share_import_failed);
        }
        finish();
    }

    private boolean copyFile(File src, File dst) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new java.io.FileInputStream(src);
            out = new FileOutputStream(dst, false);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean isImage(String ext) {
        return ".png".equals(ext) || ".jpg".equals(ext) || ".jpeg".equals(ext);
    }

    private void toast(int res) {
        try {
            Toast.makeText(this, res, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }

    private void toast(String s) {
        try {
            Toast.makeText(this, s, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }
}
