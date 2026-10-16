package com.qcl.launcher.launcher.mod.curse;

import android.os.AsyncTask;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.list.install.DownloadTaskListAdapter;
import com.qcl.launcher.launcher.list.install.DownloadTaskListBean;
import com.qcl.launcher.launcher.mod.ModApiMirror;
import com.qcl.launcher.launcher.mod.Modpack;
import com.qcl.launcher.launcher.uis.game.download.DownloadUrlSource;
import com.qcl.launcher.task.DownloadTask;
import com.qcl.launcher.utils.file.FileStringUtils;
import com.qcl.launcher.utils.io.DownloadUtil;
import com.qcl.launcher.utils.io.MirrorUtils;
import com.qcl.launcher.utils.io.NetworkUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ★★★ 社区版：CurseForge 整合包（{@code .zip} + {@code manifest.json}）安装。
 *
 * <p><b>为什么写这个</b>：原来这个类是 <b>18 行空壳</b> ✗（{@code doInBackground} 直接 return null ✗）
 *
 * <p><b>★ 关键难点：{@code files[]} 里没有下载地址</b> ✗
 * CurseForge 的 manifest 只给 {@code projectID} + {@code fileID} ✗
 * ⇒ 必须调官方 API 换：{@code /v1/mods/<projectID>/files/<fileID>} ✓
 * <b>好消息</b>：项目里**已经内置了 API key** ✓（见 {@link CurseForgeRemoteModRepository} ✓
 * 那是 FCL/HMCL 一直在用的公开 key ✓）⇒ **不需要用户自己申请** ✓
 * <br>
 * ★ manifest 里**可能**带 {@code url} 字段 ✓ ⇒ **优先用它** ✓ 失败再走 API ✓
 *
 * <p><b>加载器</b>：只支持 <b>Fabric</b> ✓
 * （{@code modLoaders[].id} 形如 {@code fabric-0.15.0} ✓ ⇒ 取横线后那段拼 Fabric profile JSON ✓
 * 　profile JSON 本身就是完整版本 JSON ✓ **不用跑安装器** ✓）
 * Forge / NeoForge ✗ **明确抛异常说明** ✗ **不假装支持** ✗
 */
public class CurseInstallTask extends AsyncTask<Object, Integer, Exception> {

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

    /** ★ 与 {@link CurseForgeRemoteModRepository} 里那把 key 相同 ✓（那边是 private ✗ 只能重复 ✓ 改要两处一起改 ✗） */
    private static final String API_KEY =
            "$2a$10$fgjXkx00bZZ5ypMONic.Uu7/Be1KFjmMcprkzelHhjjs7FErXt7i6";

    private final MainActivity activity;
    private final File zipFile;
    private final Modpack modpack;
    private final CurseManifest manifest;
    private final String name;
    private final DownloadTaskListAdapter adapter;

    public CurseInstallTask(File file, Modpack modpack, CurseManifest curseManifest, String str) {
        this(file, modpack, curseManifest, str, null);
    }

    public CurseInstallTask(File file, Modpack modpack, CurseManifest curseManifest,
                            String str, DownloadTaskListAdapter adapter) {
        this.zipFile = file;
        this.modpack = modpack;
        this.manifest = curseManifest;
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
                return new IOException("整合包安装缺少 Activity（CurseInstallTask.setActivity 没有被调用）");
            }
            install();
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private void install() throws Exception {
        if (manifest == null || manifest.getMinecraft() == null) {
            throw new IOException("这个包没有 CurseForge 清单 ✗ 读不出要装什么 ✗");
        }
        String mcVersion = manifest.getMinecraft().getGameVersion();
        if (mcVersion == null || mcVersion.isEmpty()) {
            throw new IOException("整合包里没写 Minecraft 版本 ✗");
        }

        // 找 Fabric 加载器版本（id 形如 fabric-0.15.0 ✓）
        String fabricLoader = null;
        StringBuilder others = new StringBuilder();
        List<CurseManifestModLoader> loaders = manifest.getMinecraft().getModLoaders();
        if (loaders != null) {
            for (CurseManifestModLoader l : loaders) {
                String id = l == null ? null : l.getId();
                if (id == null) {
                    continue;
                }
                if (id.toLowerCase(java.util.Locale.ROOT).startsWith("fabric-")) {
                    fabricLoader = id.substring("fabric-".length());
                } else {
                    if (others.length() > 0) {
                        others.append(' ');
                    }
                    others.append(id);
                }
            }
        }
        if (fabricLoader == null) {
            throw new IOException("这个整合包用的加载器暂时还不支持 ✗\n\n"
                    + (others.length() > 0 ? "它需要：" + others + "\n\n" : "它没有声明 Fabric 加载器 ✗\n\n")
                    + "目前只支持 **Fabric** 的 CurseForge 整合包 ✓\n"
                    + "（Fabric 能直接拿官方 profile JSON ✓ 不用跑安装器 ✓\n"
                    + "　Forge / NeoForge 要跑官方安装器 ✗ 还没接 ✗）\n\n"
                    + "变通办法：先手动装好 Forge/NeoForge，再把包里的 mods/ 拷进游戏目录 ✓");
        }

        File gameDir = new File(activity.launcherSetting.gameFileDirectory);
        File versionDir = new File(gameDir, "versions" + File.separator + name);
        //noinspection ResultOfMethodCallIgnored
        versionDir.mkdirs();
        Charset encoding = modpack.getEncoding() != null
                ? modpack.getEncoding() : Charset.forName("UTF-8");

        // ① 版本 JSON = Fabric profile JSON ✓
        DownloadTaskListBean jsonRow = addRow("获取 Fabric " + fabricLoader + " 版本配置");
        String profileJson = null;
        try {
            profileJson = NetworkUtils.doGet(NetworkUtils.toURL(
                    "https://meta.fabricmc.net/v2/versions/loader/"
                            + mcVersion + "/" + fabricLoader + "/profile/json"));
        } catch (Throwable ignored) {
        }
        if (profileJson == null || profileJson.isEmpty()) {
            try {
                profileJson = NetworkUtils.doGet(NetworkUtils.toURL(
                        "https://bmclapi2.bangbang93.com/fabric-meta/v2/versions/loader/"
                                + mcVersion + "/" + fabricLoader + "/profile/json"));
            } catch (Throwable ignored) {
            }
        }
        if (profileJson == null || profileJson.isEmpty()) {
            throw new IOException("拿不到 Fabric " + fabricLoader + " 的版本配置 ✗\n检查网络后重试 ✓");
        }
        profileJson = profileJson.replaceFirst("\"id\"\\s*:\\s*\"[^\"]*\"", "\"id\":\"" + name + "\"");
        FileStringUtils.writeFile(new File(versionDir, name + ".json").getAbsolutePath(), profileJson);
        rowDone(jsonRow);
        reportOverall(10);

        // ② 游戏本体 jar
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

        // ③ 逐个文件（没 url 就调 CurseForge API 换 ✓）
        List<CurseManifestFile> files = manifest.getFiles();
        int total = files == null ? 0 : files.size();
        DownloadTaskListBean filesRow = addRow("下载整合包文件（共 " + total + " 个）");
        File modsDir = new File(gameDir, "mods");
        if (!modsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            modsDir.mkdirs();
        }
        int done = 0;
        int failed = 0;
        if (files != null) {
            for (CurseManifestFile f : files) {
                String url = null;
                try {
                    if (f.getUrl() != null) {
                        url = f.getUrl().toString();   // manifest 自带 → 省一次 API ✓
                    }
                } catch (Throwable ignored) {
                }
                if (url == null || url.isEmpty()) {
                    url = resolveFileUrl(f.getProjectID(), f.getFileID());
                }
                if (url == null || url.isEmpty()) {
                    failed++;
                    continue;
                }
                String fileName = f.getFileName();
                if (fileName == null || fileName.isEmpty()) {
                    int slash = url.lastIndexOf('/');
                    fileName = slash >= 0 ? url.substring(slash + 1)
                            : ("file-" + f.getFileID() + ".jar");
                }
                File out = new File(modsDir, fileName);
                if (out.isFile() && out.length() > 0) {
                    done++;   // 已有 → 跳过 ✓（Curse manifest 不带大小 ✗ 只能按「存在」判 ✓）
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

        // ④ 解 overrides → 游戏目录
        DownloadTaskListBean ovRow = addRow("解包 overrides");
        String ovName = manifest.getOverrides();
        if (ovName == null || ovName.isEmpty()) {
            ovName = "overrides";
        }
        int n = unzipOverrides(zipFile, gameDir, encoding, ovName);
        rowDone(ovRow);
        reportOverall(90);

        // ⑤ 写 modpack.json
        DownloadTaskListBean cfgRow = addRow("写入整合包配置");
        try {
            String cfg = "{\"name\":\"" + esc(manifest.getName()) + "\","
                    + "\"version\":\"" + esc(manifest.getVersion()) + "\","
                    + "\"type\":\"CurseForge\","
                    + "\"gameVersion\":\"" + esc(mcVersion) + "\","
                    + "\"fabricLoader\":\"" + esc(fabricLoader) + "\"}";
            FileStringUtils.writeFile(
                    new File(versionDir, name + ".json.modpack").getAbsolutePath(), cfg);
        } catch (Throwable ignored) {
        }
        rowDone(cfgRow);
        reportOverall(100);

        android.util.Log.i("Curse", "整合包 " + name + " 装完：成功 " + done
                + " / 失败 " + failed + "，overrides " + n + " 个");
        if (failed > 0) {
            throw new IOException("有 " + failed + " 个模组没下下来 ✗（共 " + total + " 个）\n"
                    + "多半是网络问题 —— 进游戏可能缺模组，建议重装一次 ✓");
        }
    }

    // ==================== 工具 ====================

    /** ★ 调 CurseForge API 把 projectID + fileID 换成下载地址 ✓ */
    private String resolveFileUrl(int projectId, int fileId) {
        try {
            String url = ModApiMirror.apply("https://api.curseforge.com")
                    + "/v1/mods/" + projectId + "/files/" + fileId;
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setRequestProperty("X-API-KEY", API_KEY);
            c.setRequestProperty("User-Agent", "QCL-CE/2.0.3 (Android)");
            try (InputStream in = c.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] b = new byte[16 * 1024];
                int r;
                while ((r = in.read(b)) > 0) {
                    bos.write(b, 0, r);
                }
                com.google.gson.JsonObject obj = new com.google.gson.JsonParser()
                        .parse(bos.toString("UTF-8")).getAsJsonObject();
                com.google.gson.JsonObject data = obj.has("data")
                        ? obj.getAsJsonObject("data") : null;
                if (data != null && data.has("downloadUrl")
                        && !data.get("downloadUrl").isJsonNull()) {
                    return data.get("downloadUrl").getAsString();
                }
            } finally {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 从 BMCL 版本清单取 client jar 地址 ✓ */
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

    /** 解 zip 里指定目录（manifest.overrides ✓）到目标目录 ✓ */
    private int unzipOverrides(File zip, File dest, Charset cs, String dirName) throws Exception {
        int n = 0;
        String prefix = dirName.endsWith("/") ? dirName : dirName + "/";
        try (ZipInputStream z = new ZipInputStream(new FileInputStream(zip), cs)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String nm = e.getName();
                if (!nm.startsWith(prefix)) {
                    continue;
                }
                String rel = nm.substring(prefix.length());
                if (rel.isEmpty() || e.isDirectory()) {
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
