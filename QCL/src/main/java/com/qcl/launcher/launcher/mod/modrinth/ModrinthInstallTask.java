package com.qcl.launcher.launcher.mod.modrinth;

import android.os.AsyncTask;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.list.install.DownloadTaskListAdapter;
import com.qcl.launcher.launcher.list.install.DownloadTaskListBean;
import com.qcl.launcher.launcher.mod.Modpack;
import com.qcl.launcher.launcher.uis.game.download.DownloadUrlSource;
import com.qcl.launcher.task.DownloadTask;
import com.qcl.launcher.utils.io.DownloadUtil;
import com.qcl.launcher.utils.file.FileStringUtils;
import com.qcl.launcher.utils.io.MirrorUtils;
import com.qcl.launcher.utils.io.NetworkUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ★★★ 社区版：Modrinth 整合包（{@code .mrpack}）安装。
 *
 * <p><b>为什么写这个</b>：原来这个类是 <b>18 行空壳</b> ✗ ——
 * {@code doInBackground} 直接 {@code return null} ✗
 * ⇒ 「适合你的手机」推荐的都是 Modrinth 包 ✗ **推荐了却装不了** ✗ 闭环是断的 ✗
 *
 * <p><b>{@code .mrpack} 的结构</b>（zip）：
 * <ul>
 *   <li>{@code modrinth.index.json} —— 清单，已由 {@link ModrinthModpackProvider} 解析好 ✓
 *       （{@code files[]} 含 path / downloads / hashes / env ✓
 *        {@code dependencies} 含 minecraft / fabric-loader / forge … ✓）</li>
 *   <li>{@code overrides/} —— 覆盖到实例目录 ✓</li>
 *   <li>{@code client-overrides/} —— 只在客户端覆盖 ✓</li>
 * </ul>
 * <b>没有</b> patches / jarmods ✗ —— 那是 MultiMC 格式的东西 ✗
 *
 * <p><b>★ 本类只支持 Fabric</b>，理由：
 * Fabric 的官方 <b>profile JSON</b>（{@code meta.fabricmc.net/v2/versions/loader/<mc>/<loader>/profile/json}）
 * **本身就是一个完整的版本 JSON** ✓ 含 libraries 和 mainClass ✓
 * ⇒ 直接落盘即可，**不用跑任何安装器** ✓ 这是最干净的一条路 ✓
 * <br>
 * Forge / NeoForge / Quilt 需要跑官方安装器 ✗ 复杂得多 ✗
 * ⇒ **明确抛异常说明** ✗ **不假装支持** ✗（对应本项目「不做假选项」的原则 ✓）
 */
public class ModrinthInstallTask extends AsyncTask<Object, Integer, Exception> {

    /** ★ 照 MultiMCModpackInstallTask：提供器 new 的时候没有 Activity，靠静态注入 ✓ */
    private static MainActivity sActivity;

    public static void setActivity(MainActivity activity) {
        sActivity = activity;
    }

    public interface ProgressListener {
        void onProgress(int percent);

        void onFinished(Exception error);
    }

    private ProgressListener listener;

    public void setListener(ProgressListener listener) {
        this.listener = listener;
    }

    private final MainActivity activity;
    private final File zipFile;
    private final Modpack modpack;
    private final ModrinthManifest manifest;
    private final String name;
    private final DownloadTaskListAdapter adapter;

    public ModrinthInstallTask(File file, Modpack modpack, ModrinthManifest manifest, String str) {
        this(file, modpack, manifest, str, null);
    }

    public ModrinthInstallTask(File file, Modpack modpack, ModrinthManifest manifest,
                               String str, DownloadTaskListAdapter adapter) {
        this.zipFile = file;
        this.modpack = modpack;
        this.manifest = manifest;
        this.name = str;
        this.adapter = adapter;
        this.activity = sActivity;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        if (listener != null && values != null && values.length > 0) {
            listener.onProgress(values[0]);
        }
    }

    @Override
    protected void onPostExecute(Exception error) {
        super.onPostExecute(error);
        if (listener != null) {
            listener.onFinished(error);
        }
    }

    // ==================== 进度行 ====================

    private DownloadTaskListBean addRow(String rowName) {
        DownloadTaskListBean bean = new DownloadTaskListBean(rowName, "", "", "");
        if (adapter != null) {
            activity.runOnUiThread(() -> adapter.addDownloadTask(bean));
        }
        return bean;
    }

    private void rowDone(DownloadTaskListBean bean) {
        bean.progress = 100;
        if (adapter != null) {
            activity.runOnUiThread(() -> adapter.onProgress(bean));
        }
    }

    private void reportOverall(int percent) {
        publishProgress(percent);
    }

    // ==================== 主流程 ====================

    @Override
    protected Exception doInBackground(Object... objArr) {
        try {
            if (activity == null) {
                return new IOException("整合包安装缺少 Activity（ModrinthInstallTask.setActivity 没有被调用）");
            }
            install();
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private void install() throws Exception {
        if (manifest == null) {
            throw new IOException("这个包没有 Modrinth 清单 ✗ 读不出要装什么 ✗");
        }
        String mcVersion = manifest.getGameVersion();
        if (mcVersion == null || mcVersion.isEmpty()) {
            throw new IOException("整合包里没写 Minecraft 版本 ✗ 不知道要装哪个本体 ✗");
        }
        Map<String, String> deps = manifest.getDependencies();
        String fabricLoader = deps == null ? null : deps.get("fabric-loader");
        String forge = deps == null ? null : deps.get("forge");
        String neoForge = deps == null ? null : deps.get("neoforge");
        String quilt = deps == null ? null : deps.get("quilt-loader");

        // ★★★ 加载器支持范围：如实划清 ✗ 不假装 ✗
        if (fabricLoader == null) {
            StringBuilder sb = new StringBuilder("这个整合包用的加载器暂时还不支持 ✗\n\n");
            if (forge != null) {
                sb.append("它需要 Forge ").append(forge).append('\n');
            } else if (neoForge != null) {
                sb.append("它需要 NeoForge ").append(neoForge).append('\n');
            } else if (quilt != null) {
                sb.append("它需要 Quilt ").append(quilt).append('\n');
            } else {
                sb.append("它没有声明 Fabric 加载器 ✗\n");
            }
            sb.append("\n目前只支持 **Fabric** 的 Modrinth 整合包 ✓\n")
                    .append("（Fabric 能直接拿官方 profile JSON ✓ 不用跑安装器 ✓\n")
                    .append("　Forge / NeoForge 要跑官方安装器 ✗ 还没接 ✗）\n\n")
                    .append("变通办法：先手动装好 Forge/NeoForge，")
                    .append("再把包里的 mods/ 拷进游戏目录 ✓");
            throw new IOException(sb.toString());
        }

        File gameDir = new File(activity.launcherSetting.gameFileDirectory);
        File versionDir = new File(gameDir, "versions" + File.separator + name);
        //noinspection ResultOfMethodCallIgnored
        versionDir.mkdirs();

        Charset encoding = modpack.getEncoding() != null
                ? modpack.getEncoding() : Charset.forName("UTF-8");

        // ---- ① 版本 JSON = Fabric 的 profile JSON ✓ ----
        DownloadTaskListBean jsonRow = addRow("获取 Fabric " + fabricLoader + " 版本配置");
        String profileJson = null;
        try {
            profileJson = NetworkUtils.doGet(NetworkUtils.toURL(
                    "https://meta.fabricmc.net/v2/versions/loader/"
                            + mcVersion + "/" + fabricLoader + "/profile/json"));
        } catch (Throwable ignored) {
        }
        if (profileJson == null || profileJson.isEmpty()) {
            // 国内直连 meta.fabricmc.net 可能慢 → 走 BMCL 的 fabric-meta 镜像 ✓
            try {
                profileJson = NetworkUtils.doGet(NetworkUtils.toURL(
                        "https://bmclapi2.bangbang93.com/fabric-meta/v2/versions/loader/"
                                + mcVersion + "/" + fabricLoader + "/profile/json"));
            } catch (Throwable ignored) {
            }
        }
        if (profileJson == null || profileJson.isEmpty()) {
            throw new IOException("拿不到 Fabric " + fabricLoader + " 的版本配置 ✗\n"
                    + "检查一下网络，然后重试 ✓");
        }
        // ★ id 改成整合包名 —— 否则会和「下载 → Fabric」装的那个撞名 ✗
        profileJson = profileJson.replaceFirst("\"id\"\\s*:\\s*\"[^\"]*\"", "\"id\":\"" + name + "\"");
        FileStringUtils.writeFile(new File(versionDir, name + ".json").getAbsolutePath(), profileJson);
        rowDone(jsonRow);
        reportOverall(10);

        // ---- ② 游戏本体 jar ----
        DownloadTaskListBean jarRow = addRow("下载游戏本体 " + mcVersion);
        File jar = new File(versionDir, name + ".jar");
        if (!(jar.isFile() && jar.length() > 0)) {
            String jarUrl = resolveClientJarUrl(mcVersion);
            if (jarUrl == null) {
                throw new IOException("拿不到 Minecraft " + mcVersion + " 的下载地址 ✗\n"
                        + "请先在「下载 → 游戏」里把 " + mcVersion + " 装好，再来装整合包 ✓");
            }
            boolean ok = DownloadUtil.downloadFile(jarUrl, jar.getAbsolutePath(), null,
                    new DownloadTask.DownloadFeedback() {
                        @Override
                        public void updateProgress(long curr, long max) {
                            if (max <= 0) {
                                return;
                            }
                            jarRow.progress = (int) (100 * curr / max);
                            activity.runOnUiThread(() -> {
                                if (adapter != null) {
                                    adapter.onProgress(jarRow);
                                }
                            });
                        }

                        @Override
                        public void updateSpeed(String speed) {
                        }
                    });
            if (!ok) {
                //noinspection ResultOfMethodCallIgnored
                jar.delete();
                rowDone(jarRow);
                throw new IOException("下载游戏本体失败 ✗\n" + jarUrl);
            }
        }
        rowDone(jarRow);
        reportOverall(30);

        // ---- ③ 下载 files[] ----
        List<ModrinthManifest.File> files = manifest.getFiles();
        int total = files == null ? 0 : files.size();
        DownloadTaskListBean filesRow = addRow("下载整合包文件（共 " + total + " 个）");
        int done = 0;
        int failed = 0;
        if (files != null) {
            for (ModrinthManifest.File f : files) {
                // env 里 client=unsupported 的跳过 ✓（服务端专用文件 ✓）
                Map<String, String> env = f.getEnv();
                if (env != null && "unsupported".equals(env.get("client"))) {
                    continue;
                }
                String path = f.getPath();
                if (path == null || path.isEmpty()) {
                    continue;
                }
                File out = new File(gameDir, path);
                if (out.isFile() && out.length() > 0
                        && f.getFileSize() > 0 && out.length() == f.getFileSize()) {
                    done++;   // 已有且大小对得上 → 跳过 ✓
                    continue;
                }
                File par = out.getParentFile();
                if (par != null && !par.exists()) {
                    par.mkdirs();
                }
                String url = null;
                if (f.getDownloads() != null && !f.getDownloads().isEmpty()) {
                    url = f.getDownloads().get(0).toString();
                }
                if (url == null) {
                    failed++;
                    continue;
                }
                boolean ok = false;
                try {
                    ok = DownloadUtil.downloadFile(MirrorUtils.rewriteCdn(url),
                            out.getAbsolutePath(), null, null);
                } catch (Throwable ignored) {
                }
                if (!ok) {
                    try {
                        ok = DownloadUtil.downloadFile(url, out.getAbsolutePath(), null, null);
                    } catch (Throwable ignored) {
                    }
                }
                if (ok) {
                    done++;
                } else {
                    failed++;
                }
                filesRow.progress = total <= 0 ? 100 : (int) (100.0 * (done + failed) / total);
                activity.runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.onProgress(filesRow);
                    }
                });
                reportOverall(30 + (total <= 0 ? 0 : 40 * (done + failed) / total));
            }
        }
        rowDone(filesRow);
        reportOverall(75);

        // ---- ④ 解 overrides / client-overrides → 游戏目录 ----
        DownloadTaskListBean ovRow = addRow("解包 overrides");
        int n = unzipOverrides(zipFile, gameDir, encoding);
        rowDone(ovRow);
        reportOverall(90);

        // ---- ⑤ 写 modpack.json ----
        DownloadTaskListBean cfgRow = addRow("写入整合包配置");
        try {
            String cfg = "{\"name\":\"" + esc(manifest.getName()) + "\","
                    + "\"version\":\"" + esc(manifest.getVersionId()) + "\","
                    + "\"type\":\"Modrinth\","
                    + "\"gameVersion\":\"" + esc(mcVersion) + "\","
                    + "\"fabricLoader\":\"" + esc(fabricLoader) + "\"}";
            FileStringUtils.writeFile(
                    new File(versionDir, name + ".json.modpack").getAbsolutePath(), cfg);
        } catch (Throwable ignored) {
        }
        rowDone(cfgRow);
        reportOverall(100);

        android.util.Log.i("Modrinth", "整合包 " + name + " 装完：成功 " + done
                + " / 失败 " + failed + "，overrides " + n + " 个");
        if (failed > 0) {
            throw new IOException("有 " + failed + " 个文件没下下来 ✗（共 " + total + " 个）\n"
                    + "多半是网络问题 —— 进游戏可能缺模组，建议重装一次 ✓");
        }
    }

    // ==================== 工具 ====================

    /** 从 BMCL 版本清单里拿 client jar 地址 ✓（照 MultiMC 的做法 ✓） */
    private String resolveClientJarUrl(String mcVersion) {
        try {
            int source = DownloadUrlSource.getSource(activity.launcherSetting.downloadUrlSource);
            String versionJsonUrl = DownloadUrlSource
                    .getSubUrl(source, DownloadUrlSource.VERSION_JSON)
                    + "/version/" + mcVersion + "/json";
            String rawJson = NetworkUtils.doGet(NetworkUtils.toURL(versionJsonUrl));
            if (rawJson == null || rawJson.isEmpty()) {
                return null;
            }
            com.google.gson.JsonObject obj = new com.google.gson.JsonParser()
                    .parse(rawJson).getAsJsonObject();
            com.google.gson.JsonObject dl = obj.has("downloads")
                    ? obj.getAsJsonObject("downloads") : null;
            if (dl != null && dl.has("client")) {
                String official = dl.getAsJsonObject("client").get("url").getAsString();
                return DownloadUrlSource.replaceSubUrl(official, source,
                        DownloadUrlSource.VERSION_JAR);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 解 zip 里的 overrides/ 与 client-overrides/ 到目标目录 ✓ */
    private int unzipOverrides(File zip, File dest, Charset cs) throws Exception {
        int n = 0;
        try (ZipInputStream z = new ZipInputStream(new FileInputStream(zip), cs)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String nm = e.getName();
                String rel = null;
                if (nm.startsWith("overrides/")) {
                    rel = nm.substring("overrides/".length());
                } else if (nm.startsWith("client-overrides/")) {
                    rel = nm.substring("client-overrides/".length());
                }
                if (rel == null || rel.isEmpty() || e.isDirectory()) {
                    continue;
                }
                File out = new File(dest, rel);
                File par = out.getParentFile();
                if (par != null && !par.exists()) {
                    par.mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] b = new byte[32 * 1024];
                    int r;
                    while ((r = z.read(b)) > 0) {
                        fos.write(b, 0, r);
                    }
                }
                n++;
            }
        }
        return n;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
