package com.qcl.launcher.launcher.uis.plugin;

import android.widget.LinearLayout;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.mod.Modpack;
import com.qcl.launcher.launcher.mod.ModpackHelper;
import com.qcl.launcher.launcher.mod.multimc.MultiMCInstanceConfiguration;
import com.qcl.launcher.launcher.mod.multimc.MultiMCModpackInstallTask;
import com.qcl.launcher.utils.io.DownloadUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ★★★ 社区版：Cleanroom 加载器安装。
 *
 * <p><b>Cleanroom 是什么</b>：**Minecraft 1.12.2 的 Forge 延续版** ✓
 * （不是 fork，是逐步独立的现代化延续 ✓ 带 Fugue 做 Forge 模组兼容 ✓）
 * <b>只支持 1.12.2</b> ✗ —— 这点必须在界面上写清楚 ✗
 *
 * <p><b>★ 为什么做成插件而不是改「安装游戏」页</b>：
 * 加一个新加载器要改 5 处（布局 / 字符串 / InstallGameUI / UIManager / MainActivity）✗
 * 而 Cleanroom 的安装方式**正好就是「导入一个 MMC 实例包」** ✓
 * ⇒ 做成插件只要一个文件 ✓ 而且**复用项目已有的 `MultiMCModpackInstallTask`** ✓✓
 *
 * <p><b>★ 版本清单从哪来（已实测确认 ✓）</b>：
 * <pre>
 * https://repo.cleanroommc.com/releases/com/cleanroommc/cleanroom/maven-metadata.xml
 * </pre>
 * 这是**标准 Maven 元数据** ✓ 返回 68 个版本（0.3.16-alpha → 0.7.8-alpha）✓
 * ★ 用**正则**解析 {@code <version>} 就够 ✓ 不为一个 XML 引整个解析库 ✗
 * <br>
 * ★ **注意**：`maven.cleanroommc.com` 是 **404** ✗
 *   正确的是 **`repo.cleanroommc.com`** ✓（从官方下载页挖出来的 ✓）
 *
 * <p><b>安装包</b>：
 * <pre>
 * https://repo.cleanroommc.com/releases/com/cleanroommc/cleanroom/&lt;版本&gt;/cleanroom-&lt;版本&gt;.zip
 * </pre>
 * 这个 zip 就是 **MMC 实例包** ✓ ⇒ 下载完直接交给 `MultiMCModpackInstallTask` ✓
 * （不用它的 installer.jar ✗ 那个要跑 Java 安装器 ✗）
 *
 * <p><b>运行时</b>：Cleanroom 要 **Java 25** ✓ —— 项目里**已经有 jre25** ✓
 * （`assets/app_runtime/java/jre25` ✓）⇒ 不阻塞 ✓
 */
public final class PluginCleanroom {

    private PluginCleanroom() {
    }

    private static final String META_URL =
            "https://repo.cleanroommc.com/releases/com/cleanroommc/cleanroom/maven-metadata.xml";

    private static final String ARTIFACT_BASE =
            "https://repo.cleanroommc.com/releases/com/cleanroommc/cleanroom/";

    /** 从 maven-metadata.xml 里抠 {@code <version>…</version>} ✓ */
    private static final Pattern VER = Pattern.compile("<version>([^<]+)</version>");

    // ==================== 界面 ====================

    public static void ui(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);

            col.addView(PluginUiKit.text(a, "Cleanroom"));
            col.addView(PluginUiKit.sub(a, "★ **Minecraft 1.12.2 的 Forge 延续版** ✓"
                    + " —— 只能装在 1.12.2 上 ✗"));
            col.addView(PluginUiKit.sub(a, "★ 装法 = 下载官方的 MMC 实例包 → 用项目里已有的"
                    + " MultiMC 安装器装 ✓ 不需要跑它那个 installer.jar ✗"));
            col.addView(PluginUiKit.sub(a, "★ 运行时要 Java 25 ✓ 项目里已有 jre25 ✓"));
            col.addView(PluginUiKit.sub(a, " "));

            final LinearLayout list = PluginUiKit.column(a);
            PluginUiKit.row(a, "刷新版本列表", v -> load(a, list));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "可用版本（新的在前）"));
            col.addView(list);

            PluginUiKit.sheet(a, "Cleanroom", PluginUiKit.scroller(a, col), "关闭", null);
            load(a, list);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== 拉版本 ====================

    private static void load(MainActivity a, LinearLayout out) {
        out.removeAllViews();
        out.addView(PluginUiKit.sub(a, "正在读 maven-metadata.xml…"));
        new Thread(() -> {
            List<String> vers = null;
            String err = null;
            try {
                String xml = get(META_URL);
                vers = new ArrayList<>();
                Matcher m = VER.matcher(xml);
                while (m.find()) {
                    String v = m.group(1).trim();
                    if (!v.isEmpty()) {
                        vers.add(v);
                    }
                }
                // ★ 倒序：新的在前 ✓（maven 里是升序 ✓）
                Collections.reverse(vers);
            } catch (Throwable t) {
                err = String.valueOf(t);
            }
            final List<String> fin = vers;
            final String ferr = err;
            a.runOnUiThread(() -> {
                out.removeAllViews();
                if (fin == null) {
                    out.addView(PluginUiKit.sub(a, "读不到版本清单 ✗：" + ferr));
                    out.addView(PluginUiKit.sub(a, "★ 地址是 repo.cleanroommc.com ✗ 不是 maven.* ✗"));
                    out.addView(PluginUiKit.sub(a, "　如果四个网络都正常还读不到，"
                            + "多半是代理 / 域名被拦 ✓ 参考 Wiki 的排查表"));
                    return;
                }
                if (fin.isEmpty()) {
                    out.addView(PluginUiKit.sub(a, "清单里没有版本 ✗（解析格式可能变了 ✗）"));
                    return;
                }
                out.addView(PluginUiKit.sub(a, "共 " + fin.size() + " 个版本 ✓"
                        + "　最新的：" + fin.get(0)));
                for (String v : fin) {
                    PluginUiKit.row(a, "· " + v, x -> install(a, v));
                }
            });
        }, "qcl-cleanroom").start();
    }

    // ==================== 安装 ====================

    private static void install(MainActivity a, String ver) {
        PluginUiKit.toast(a, "开始安装 Cleanroom " + ver + "…");
        new Thread(() -> {
            try {
                // ① 下载 MMC 实例包
                File cache = new File(a.getCacheDir(), "cleanroom");
                if (!cache.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    cache.mkdirs();
                }
                File zip = new File(cache, "cleanroom-" + ver + ".zip");
                if (!(zip.isFile() && zip.length() > 0)) {
                    String url = ARTIFACT_BASE + ver + "/cleanroom-" + ver + ".zip";
                    boolean ok = DownloadUtil.downloadFile(url, zip.getAbsolutePath(), null, null);
                    if (!ok) {
                        //noinspection ResultOfMethodCallIgnored
                        zip.delete();
                        say(a, "✗ 下载失败 ✗\n" + url);
                        return;
                    }
                }

                // ② 解析成 Modpack（MMC 格式 ✓ 项目里已有解析器 ✓）
                Modpack modpack;
                try {
                    modpack = ModpackHelper.readModpackManifest(zip.toPath(), Charset.forName("UTF-8"));
                } catch (Throwable t) {
                    say(a, "✗ 这个 zip 不是有效的 MMC 实例包 ✗\n" + t);
                    return;
                }
                if (modpack == null || !(modpack.getManifest() instanceof MultiMCInstanceConfiguration)) {
                    say(a, "✗ 解析出来的不是 MultiMC 实例配置 ✗\n"
                            + "（Cleanroom 的包格式可能变了 ✗ 请反馈）");
                    return;
                }

                // ③ 交给项目已有的 MultiMC 安装器 ✓
                final String name = "Cleanroom " + ver;
                a.runOnUiThread(() -> {
                    try {
                        MultiMCModpackInstallTask.setActivity(a);
                        MultiMCModpackInstallTask task = new MultiMCModpackInstallTask(
                                a, zip, modpack,
                                (MultiMCInstanceConfiguration) modpack.getManifest(), name);
                        task.setListener(new MultiMCModpackInstallTask.ProgressListener() {
                            @Override
                            public void onProgress(int percent) {
                                // 没有任务列表时，进度只在结束时提示 ✓
                            }

                            @Override
                            public void onFinished(Exception error) {
                                if (error != null) {
                                    PluginUiKit.info(a, "安装失败", String.valueOf(error.getMessage()));
                                    return;
                                }
                                PluginUiKit.info(a, "安装完成",
                                        "Cleanroom " + ver + " 装好了 ✓\n\n"
                                                + "版本名：" + name + "\n"
                                                + "到「版本列表」里选它就能启动 ✓\n\n"
                                                + "★ 它只支持 **1.12.2** ✗ —— "
                                                + "模组也要用 1.12.2 的 ✓");
                            }
                        });
                        task.execute();
                    } catch (Throwable t) {
                        PluginUiKit.info(a, "启动安装失败", String.valueOf(t));
                    }
                });
            } catch (Throwable t) {
                say(a, "✗ 安装失败：" + t);
            }
        }, "qcl-cleanroom-install").start();
    }

    // ==================== 工具 ====================

    private static void say(MainActivity a, String msg) {
        a.runOnUiThread(() -> {
            try {
                PluginUiKit.log(a, msg);
            } catch (Throwable ignored) {
            }
            PluginUiKit.info(a, "Cleanroom", msg);
        });
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "QCL-CE/2.0.8 (Android)");
        try (InputStream in = c.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] b = new byte[16 * 1024];
            int n;
            while ((n = in.read(b)) > 0) {
                bos.write(b, 0, n);
            }
            return bos.toString("UTF-8");
        } finally {
            try {
                c.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }
}
