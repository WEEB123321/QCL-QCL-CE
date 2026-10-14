package com.qcl.launcher.launcher.uis.plugin;

import android.widget.LinearLayout;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.layout.QclLayout;
import com.qcl.launcher.manifest.AppManifest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ★★★ 社区版：插件市场。
 *
 * <p><b>市场地址</b>：{@code https://weeb123321.github.io/QCL-Plugins/}
 * —— 网页版和这里的 App 内列表读的是**同一份** {@code index.json} ✓
 *
 * <p><b>市场里是什么</b>（照 {@code SCHEMA.md} 的契约 ✓）：
 * QCL-CE 的启动器**没有插件运行时**（无 DexClassLoader / 无沙箱）✗
 * ⇒ 市场只分发**资源型插件** ✓，落点是**启动器已有的导入流程** ✓：
 * <ul>
 *   <li>{@code layout} —— 界面排版 → 写进 {@code SETTING_DIR/qcl_layout.json} ✓
 *       （复用 {@link QclLayout#importJson} ✓）</li>
 *   <li>{@code control} —— 控键布局 → 解到 {@code CONTROLLER_DIR/<名称>/} ✓</li>
 * </ul>
 * <b>不加载代码、不改 APK、不申请新权限</b> ✓ —— 包里出现 .dex/.jar/.so 一律拒装 ✗
 *
 * <p><b>安装做的事</b>：
 * <ol>
 *   <li>下载 zip（先官方直链 ✓ 失败走 gh-proxy 镜像 ✓）</li>
 *   <li><b>校验 sha256</b> ✓ —— index 里给了 ✓ 对不上就拒装 ✗（防止半截下载 / 被替换）</li>
 *   <li>按 {@code type} 落到对应位置 ✓</li>
 * </ol>
 */
public final class PluginMarket {

    private PluginMarket() {
    }

    /** 市场索引（GitHub Pages ✓）。 */
    private static final String INDEX_URL =
            "https://weeb123321.github.io/QCL-Plugins/index.json";
    /** 索引的镜像（Pages 挂的时候用 raw + gh-proxy ✓）。 */
    private static final String INDEX_MIRROR =
            "https://gh-proxy.com/https://raw.githubusercontent.com/WEEB123321/QCL-Plugins/main/index.json";

    /** 市场网页（给「在浏览器打开」用 ✓）。 */
    private static final String WEB_URL = "https://weeb123321.github.io/QCL-Plugins/";

    /** 一条市场条目。 */
    private static final class Item {
        String id;
        String name;
        String version;
        String type;
        String author;
        String description;
        String tags;
        long size;
        String sha256;
        String url;
        String mirror;
        int minCode;

        @Override
        public String toString() {
            return name + "  " + version;
        }
    }

    // ==================== 界面 ====================

    public static void ui(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);

            col.addView(PluginUiKit.text(a, "插件市场"));
            col.addView(PluginUiKit.sub(a, "★ 市场里的插件是**资源型**的（控键布局 / 界面排版）✓"
                    + " —— 启动器没有插件运行时，所以不加载代码 ✗"));
            col.addView(PluginUiKit.sub(a, "★ 每个包都会**校验 sha256** ✓ 对不上就拒装 ✗"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "市场地址：" + WEB_URL));

            final LinearLayout list = PluginUiKit.column(a);
            PluginUiKit.row(a, "在浏览器打开市场", v -> openWeb(a));
            PluginUiKit.row(a, "刷新列表", v -> load(a, list));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "可安装的插件"));
            col.addView(list);

            PluginUiKit.sheet(a, "插件市场", PluginUiKit.scroller(a, col), "关闭", null);
            load(a, list);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    private static void openWeb(MainActivity a) {
        try {
            a.startActivity(new android.content.Intent(
                    android.content.Intent.ACTION_VIEW, android.net.Uri.parse(WEB_URL)));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "打不开浏览器：" + t);
        }
    }

    // ==================== 拉列表 ====================

    private static void load(MainActivity a, LinearLayout out) {
        out.removeAllViews();
        out.addView(PluginUiKit.sub(a, "正在读取市场索引…"));
        new Thread(() -> {
            List<Item> items = null;
            String err = null;
            try {
                String body;
                try {
                    body = get(INDEX_URL);
                } catch (Throwable t1) {
                    body = get(INDEX_MIRROR);   // Pages 挂了就走镜像 ✓
                }
                items = parse(body);
            } catch (Throwable t) {
                err = String.valueOf(t);
            }
            final List<Item> fin = items;
            final String ferr = err;
            a.runOnUiThread(() -> {
                out.removeAllViews();
                if (fin == null) {
                    out.addView(PluginUiKit.sub(a, "读不到市场索引 ✗：" + ferr));
                    out.addView(PluginUiKit.sub(a, "★ 这多半是网络问题 —— "
                            + "索引在 GitHub Pages，读不到时自动回退镜像 ✓"));
                    return;
                }
                if (fin.isEmpty()) {
                    out.addView(PluginUiKit.sub(a, "市场里还没有插件 ✓"));
                    return;
                }
                out.addView(PluginUiKit.sub(a, "共 " + fin.size() + " 个插件 ✓"));
                for (Item it : fin) {
                    renderItem(a, out, it);
                }
            });
        }, "qcl-market").start();
    }

    private static void renderItem(MainActivity a, LinearLayout out, Item it) {
        boolean tooOld = it.minCode > 0 && it.minCode > currentVersionCode(a);
        out.addView(PluginUiKit.sub(a, "· " + it.name + "  " + it.version
                + "　[" + ("layout".equals(it.type) ? "界面排版" : "控键布局") + "]"));
        if (it.description != null && !it.description.isEmpty()) {
            out.addView(PluginUiKit.sub(a, "　" + it.description));
        }
        out.addView(PluginUiKit.sub(a, "　" + (it.size / 1024) + " KB"
                + (it.author == null ? "" : "　by " + it.author)
                + (it.tags == null ? "" : "　#" + it.tags)));
        if (tooOld) {
            out.addView(PluginUiKit.sub(a, "　✗ 这个插件要求启动器版本 ≥ "
                    + it.minCode + "，当前是 " + currentVersionCode(a) + " —— 先升级启动器 ✗"));
        } else {
            PluginUiKit.row(a, "　安装「" + it.name + "」", v -> install(a, it));
        }
        out.addView(PluginUiKit.sub(a, " "));
    }

    private static int currentVersionCode(MainActivity a) {
        try {
            return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    // ==================== 安装 ====================

    private static void install(MainActivity a, Item it) {
        PluginUiKit.toast(a, "开始安装「" + it.name + "」…");
        new Thread(() -> {
            try {
                // ① 下载（官方直链 → 镜像）
                byte[] zip;
                try {
                    zip = getBytes(it.url);
                } catch (Throwable t1) {
                    zip = getBytes(it.mirror);
                }

                // ② 校验 sha256 ✓ —— 对不上就拒装 ✗
                if (it.sha256 != null && !it.sha256.isEmpty()) {
                    String got = sha256(zip);
                    if (!it.sha256.equalsIgnoreCase(got)) {
                        say(a, "✗ 「" + it.name + "」校验失败 —— 文件可能没下完整或被替换 ✗\n"
                                + "期望 " + it.sha256.substring(0, 16) + "…\n"
                                + "实际 " + got.substring(0, 16) + "…");
                        return;
                    }
                }

                // ③ 按类型落盘
                if ("layout".equals(it.type)) {
                    applyLayout(a, zip, it);
                } else if ("control".equals(it.type)) {
                    applyControl(a, zip, it);
                } else {
                    say(a, "✗ 不认识的插件类型：" + it.type);
                }
            } catch (Throwable t) {
                say(a, "✗ 安装失败：" + t);
            }
        }, "qcl-market-install").start();
    }

    /** layout 型：从 zip 里找出 qcl_layout.json，直接喂给 QclLayout ✓ */
    private static void applyLayout(MainActivity a, byte[] zip, Item it) throws Exception {
        String json = null;
        try (ZipInputStream z = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                String n = e.getName();
                if (n.endsWith("qcl_layout.json") || n.endsWith("layout.json")) {
                    json = readAll(z);
                    break;
                }
            }
        }
        if (json == null || json.trim().isEmpty()) {
            say(a, "✗ 包里没找到 qcl_layout.json ✗");
            return;
        }
        if (!QclLayout.importJson(json)) {
            say(a, "✗ 排版内容解析失败 ✗");
            return;
        }
        say(a, "✓ 已安装「" + it.name + "」\n"
                + "★ 界面排版是**进页面时应用**的 ✓ —— 退出再进主页就能看到 ✓");
    }

    /** control 型：把 zip 里的那个目录解到 CONTROLLER_DIR/<目录名>/ ✓ */
    private static void applyControl(MainActivity a, byte[] zip, Item it) throws Exception {
        File root = new File(AppManifest.CONTROLLER_DIR);
        if (!root.exists() && !root.mkdirs()) {
            say(a, "✗ 建控键目录失败：" + root.getAbsolutePath());
            return;
        }
        int files = 0;
        String topDir = null;
        try (ZipInputStream z = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                // 跳过 zip 根的 plugin.json ✓ 只解那一个目录 ✓（照 SCHEMA 的约定 ✓）
                if (n.equals("plugin.json") || n.startsWith("__MACOSX")) {
                    continue;
                }
                int slash = n.indexOf('/');
                if (slash < 0) {
                    continue;   // 根目录下的散文件不要 ✗
                }
                String dirName = n.substring(0, slash);
                if (topDir == null) {
                    topDir = dirName;
                } else if (!topDir.equals(dirName)) {
                    continue;   // SCHEMA 说只能有一个目录 ✓ 多出来的忽略 ✓
                }
                if (e.isDirectory()) {
                    continue;
                }
                File out = new File(root, n);
                File par = out.getParentFile();
                if (par != null && !par.exists()) {
                    par.mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[32 * 1024];
                    int r;
                    while ((r = z.read(buf)) > 0) {
                        fos.write(buf, 0, r);
                    }
                }
                files++;
            }
        }
        if (files == 0) {
            say(a, "✗ 包里没有可解的控键文件 ✗");
            return;
        }
        say(a, "✓ 已安装「" + it.name + "」→ " + topDir + "（" + files + " 个文件）\n"
                + "★ 到「设置 → 控键布局」里选它就能用 ✓");
    }

    // ==================== JSON ====================

    private static List<Item> parse(String body) throws Exception {
        List<Item> out = new ArrayList<>();
        JSONObject root = new JSONObject(body);
        JSONArray arr = root.optJSONArray("plugins");
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            Item it = new Item();
            it.id = o.optString("id");
            it.name = o.optString("name");
            it.version = o.optString("version");
            it.type = o.optString("type");
            it.author = o.optString("author");
            it.description = o.optString("description");
            it.size = o.optLong("size");
            it.sha256 = o.optString("sha256");
            it.minCode = o.optInt("minLauncherVersionCode");
            JSONArray tg = o.optJSONArray("tags");
            if (tg != null) {
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < tg.length(); k++) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(tg.optString(k));
                }
                it.tags = sb.toString();
            }
            JSONObject d = o.optJSONObject("download");
            if (d != null) {
                it.url = d.optString("url");
                it.mirror = d.optString("mirror");
            }
            if (it.name != null && !it.name.isEmpty()) {
                out.add(it);
            }
        }
        return out;
    }

    // ==================== 工具 ====================

    private static void say(MainActivity a, String msg) {
        a.runOnUiThread(() -> {
            try {
                PluginUiKit.log(a, msg);
            } catch (Throwable ignored) {
            }
            PluginUiKit.toast(a, msg.split("\n")[0]);
        });
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] b = new byte[16 * 1024];
        int n;
        while ((n = in.read(b)) > 0) {
            bos.write(b, 0, n);
        }
        return bos.toString("UTF-8");
    }

    private static String get(String url) throws Exception {
        return new String(getBytes(url), "UTF-8");
    }

    private static byte[] getBytes(String url) throws Exception {
        if (url == null || url.isEmpty()) {
            throw new java.io.IOException("没有下载地址");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "QCL-CE/1.10.1 (Android)");
        try (InputStream in = c.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] b = new byte[64 * 1024];
            int n;
            while ((n = in.read(b)) > 0) {
                bos.write(b, 0, n);
            }
            return bos.toByteArray();
        } finally {
            try {
                c.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : h) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
