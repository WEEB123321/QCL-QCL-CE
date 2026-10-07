package com.qcl.launcher.launcher.uis.plugin;

import android.widget.EditText;
import android.widget.LinearLayout;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.gson.JsonUtils;
import com.qcl.launcher.utils.io.HttpRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ★★★ 社区版：真·一键开服。
 *
 * <p><b>和原来那个「开服向导」的区别</b>：原来那个只生成 {@code server.properties} 和脚本，
 * 还在界面上写着「服务器 jar 请自己下载」—— 那不叫一键开服，叫「帮你写了个配置文件」。
 * 这个版本把中间那一步补上：**自动下载对应的服务端 jar**，并写好 {@code eula.txt}，
 * 最后给出启动脚本和「现在启动」。
 *
 * <p><b>服务端从哪来</b>：走 Mojang 官方清单
 * （{@code launchermeta.mojang.com/mc/game/version_manifest_v2.json}）——
 * 先按版本号找到该版本的元数据地址，再取其中的 {@code downloads.server.url}。
 * 这是官方、无需鉴权、长期稳定的地址；**不碰第三方镜像**，因为服务端 jar 必须可信。
 *
 * <p>★ 诚实说明（写在界面上，不藏）：
 * <ul>
 *   <li>手机上开服**性能很差**，这个功能更适合「在手机上把服务端配好、拷到电脑上跑」；</li>
 *   <li>Vanilla 服务端需要 Java 17+（1.18 起）或 Java 8（更早版本），
 *       启动器自带的运行时是给**客户端**用的，开服不一定能直接用；</li>
 *   <li>服务端 jar 是**几十 MB 的下载**，走的是网络，需要等。</li>
 * </ul>
 */
public final class PluginServer {

    private PluginServer() {
    }

    private static final String MANIFEST =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    /** 打开一键开服面板。 */
    public static void open(MainActivity a) {
        try {
            // 默认用当前实例的版本号 —— 省得用户自己敲
            String cur = currentVersionName(a);

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "一键开服"));
            col.addView(PluginUiKit.sub(a, "自动下载服务端 jar、写好配置和 eula、生成启动脚本。"));
            col.addView(PluginUiKit.sub(a, " "));

            EditText ver = new EditText(a);
            ver.setHint("服务端版本，例如 1.20.4");
            ver.setText(cur == null ? "" : cur);
            EditText port = new EditText(a);
            port.setHint("端口（默认 25565）");
            port.setText("25565");
            EditText max = new EditText(a);
            max.setHint("最大玩家数（默认 20）");
            max.setText("20");
            EditText motd = new EditText(a);
            motd.setHint("服务器名（MOTD）");
            motd.setText("QCL Server");

            col.addView(PluginUiKit.label(a, "版本"));
            col.addView(ver);
            col.addView(PluginUiKit.label(a, "端口"));
            col.addView(port);
            col.addView(PluginUiKit.label(a, "最大玩家数"));
            col.addView(max);
            col.addView(PluginUiKit.label(a, "服务器名"));
            col.addView(motd);

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 服务端 jar 从 **Mojang 官方清单**下载，"
                    + "不需要你手动找 —— 这是原来那个向导缺的一步。"));
            col.addView(PluginUiKit.sub(a, "★ **手机上开服性能很差**。这个功能最实用的用法是"
                    + "「在手机上把服务端配好，拷到电脑上跑」。"));
            col.addView(PluginUiKit.sub(a, "★ 服务端需要 Java 17+（1.18 起）/ Java 8（更早）。"
                    + "启动器自带的运行时是给客户端用的，开服不一定能直接用。"));
            col.addView(PluginUiKit.sub(a, "★ 输出目录：" + serverDir().getAbsolutePath()));

            PluginUiKit.sheet(a, "一键开服", PluginUiKit.scroller(a, col), "开始", () -> {
                String v = ver.getText() == null ? "" : ver.getText().toString().trim();
                String p = port.getText() == null ? "25565" : port.getText().toString().trim();
                String m = max.getText() == null ? "20" : max.getText().toString().trim();
                String mo = motd.getText() == null ? "QCL Server" : motd.getText().toString().trim();
                if (v.isEmpty()) {
                    PluginUiKit.toast(a, "请填版本号");
                    return;
                }
                start(a, v, p, m, mo);
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "开服面板出错：" + t);
        }
    }

    /** 后台执行：解析 → 下载 → 写配置 → 报告。 */
    private static void start(final MainActivity a, final String ver,
                              final String port, final String max, final String motd) {
        final File dir = serverDir();
        PluginUiKit.work(a, "正在解析 " + ver + " 的服务端地址…", () -> {
            try {
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new RuntimeException("建目录失败：" + dir);
                }
                String url = resolveServerUrl(ver);
                if (url == null) {
                    throw new RuntimeException("Mojang 清单里没有 " + ver
                            + " 的服务端 —— 可能版本号写错了，或者这个版本不提供服务端");
                }
                File jar = new File(dir, "server.jar");
                download(url, jar);

                writeText(new File(dir, "eula.txt"),
                        "# 由 QCL-CE 一键开服生成\n"
                                + "# 设为 true 表示你同意 Minecraft EULA（https://aka.ms/MinecraftEULA）\n"
                                + "eula=true\n");

                StringBuilder sb = new StringBuilder();
                sb.append("# 由 QCL-CE 一键开服生成 —— 版本 ").append(ver).append('\n');
                sb.append("server-port=").append(port).append('\n');
                sb.append("max-players=").append(max).append('\n');
                sb.append("motd=").append(motd).append('\n');
                sb.append("online-mode=true\n");
                sb.append("difficulty=easy\n");
                sb.append("gamemode=survival\n");
                sb.append("pvp=true\n");
                sb.append("level-name=world\n");
                sb.append("enable-command-block=false\n");
                sb.append("view-distance=8\n");
                sb.append("simulation-distance=6\n");
                sb.append("sync-chunk-writes=false\n");
                writeText(new File(dir, "server.properties"), sb.toString());

                // 启动脚本：交给用户手动跑（启动器不代跑服务端进程）
                writeText(new File(dir, "start.sh"),
                        "#!/bin/sh\n"
                                + "# 由 QCL-CE 一键开服生成\n"
                                + "# ★ 需要 Java 17+（1.18 起）/ Java 8（更早版本）\n"
                                + "cd \"$(dirname \"$0\")\"\n"
                                + "java -Xmx2G -jar server.jar nogui\n");
                writeText(new File(dir, "start.bat"),
                        "@echo off\r\n"
                                + "rem 由 QCL-CE 一键开服生成\r\n"
                                + "rem ★ 需要 Java 17+（1.18 起）/ Java 8（更早版本）\r\n"
                                + "cd /d %~dp0\r\n"
                                + "java -Xmx2G -jar server.jar nogui\r\n"
                                + "pause\r\n");

                PluginUiKit.log(a, "一键开服：下载 " + ver + " 服务端");
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }, () -> {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "开服文件已就绪"));
            col.addView(PluginUiKit.kv(a, "版本", ver));
            col.addView(PluginUiKit.kv(a, "端口", port));
            col.addView(PluginUiKit.kv(a, "目录", dir.getAbsolutePath()));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "已生成：server.jar、server.properties、"
                    + "eula.txt、start.sh、start.bat"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ **启动器不代跑服务端进程** —— "
                    + "手机上跑服务端既慢又容易被系统杀，而且崩了会连累启动器。"
                    + "请把整个 server 目录拷到电脑，双击 start.bat（或跑 start.sh）。"));
            col.addView(PluginUiKit.sub(a, "★ eula.txt 已经写成 true —— "
                    + "这代表你同意 Minecraft EULA（https://aka.ms/MinecraftEULA）。"
                    + "不同意的话请把 server 目录删掉。"));
            PluginUiKit.sheet(a, "一键开服", PluginUiKit.scroller(a, col), "知道了", null);
        });
    }

    /** 从 Mojang 官方清单里解析出某个版本的服务端 jar 地址。 */
    private static String resolveServerUrl(String ver) throws Exception {
        // ① 版本清单 → 找到该版本的元数据地址
        String manifest = HttpRequest.GET(MANIFEST).getString();
        String metaUrl = null;
        try {
            com.google.gson.JsonObject root = JsonUtils.fromNonNullJson(manifest,
                    com.google.gson.JsonObject.class);
            com.google.gson.JsonArray arr =
                    root.getAsJsonArray("versions");
            if (arr != null) {
                for (com.google.gson.JsonElement e : arr) {
                    com.google.gson.JsonObject o = e.getAsJsonObject();
                    if (o.has("id") && ver.equals(o.get("id").getAsString())) {
                        metaUrl = o.get("url").getAsString();
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (metaUrl == null) {
            return null;
        }
        // ② 该版本的元数据 → downloads.server.url
        String meta = HttpRequest.GET(metaUrl).getString();
        try {
            com.google.gson.JsonObject o = JsonUtils.fromNonNullJson(meta,
                    com.google.gson.JsonObject.class);
            com.google.gson.JsonObject dl = o.getAsJsonObject("downloads");
            if (dl != null && dl.has("server")) {
                return dl.getAsJsonObject("server").get("url").getAsString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 下载到目标文件（带进度提示由外层 work 负责）。 */
    private static void download(String url, File out) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        try (InputStream in = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        } finally {
            try {
                conn.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void writeText(File f, String s) {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(s.getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
    }

    private static File serverDir() {
        return new File(AppManifest.LAUNCHER_DIR, "server");
    }

    /** 当前实例的版本名（用作默认值）。 */
    private static String currentVersionName(MainActivity a) {
        try {
            java.lang.reflect.Field f = a.getClass().getField("launcherSetting");
            Object ls = f.get(a);
            java.lang.reflect.Field v = ls.getClass().getField("currentVersion");
            Object cv = v.get(ls);
            if (cv instanceof String) {
                return (String) cv;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
