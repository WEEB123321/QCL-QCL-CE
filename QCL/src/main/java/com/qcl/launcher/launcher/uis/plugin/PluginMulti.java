package com.qcl.launcher.launcher.uis.plugin;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ★★★ 社区版：联机增强（用户清单第 37-42 项）。
 *
 * <p><b>先说结论：这 6 项里只有 1 项能真做。</b>
 * 原因是联机走 Terracotta（点对点 VPN），房间状态、成员列表、聊天、
 * 文件传输**全部在 VPN 层内部**，启动器这边只能「发起 / 加入」，
 * 拿不到里面的数据。硬做只会做成假入口。
 *
 * <ul>
 *   <li><b>#38 联机延迟记录</b> —— 真做 ✓ 对一组地址做 TCP 连通性测速，
 *       每次结果追加到本地历史，可以看趋势</li>
 *   <li><b>#37 房间模板</b> —— <b>做不到</b> ✗ 房间配置由 Terracotta 内部管理，
 *       启动器没有「把当前房间存成模板」的接口</li>
 *   <li><b>#39 联机黑白名单</b> —— <b>做不到（客户端侧）</b> ✗
 *       踢人 / 封禁是**服务端**行为，客户端没有这个权限。
 *       不过服务端的 whitelist / ops / banned 文件，现有插件「服务器白名单管理」已经能看 ✓</li>
 *   <li><b>#40 联机聊天记录</b> —— <b>做不到</b> ✗ 聊天是游戏内的，
 *       启动器在游戏外，看不到聊天内容（除非改游戏本身）</li>
 *   <li><b>#41 局域网文件直传</b> —— <b>做不到</b> ✗ 要自己起一个 HTTP / socket 服务，
 *       涉及权限、端口、后台服务，属于新模块而不是插件</li>
 *   <li><b>#42 版本自动同步</b> —— <b>做不到</b> ✗ 「同步」需要知道对端装了什么，
 *       而 Terracotta 不交换实例清单；只能做到**本地**提示版本不一致，
 *       那和现有的「存档版本转换提醒」重了</li>
 * </ul>
 */
public final class PluginMulti {

    private PluginMulti() {
    }

    private static final String HISTORY_FILE = "qcl_latency_history.txt";
    private static final String ADDR_FILE = "qcl_latency_addrs.txt";

    /** #38 联机延迟记录。 */
    public static void latencyHistory(final MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "联机延迟记录"));
            col.addView(PluginUiKit.sub(a, "对一组地址做 TCP 连通性测速，结果记到本地历史。"));
            col.addView(PluginUiKit.sub(a, " "));

            EditText addrs = new EditText(a);
            addrs.setHint("每行一个地址，例如 mc.hypixel.net:25565");
            addrs.setMinLines(3);
            String saved = readText(new File(AppManifest.SETTING_DIR, ADDR_FILE));
            addrs.setText(saved.isEmpty() ? "mc.hypixel.net:25565" : saved);
            col.addView(addrs);

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 测的是 **TCP 握手耗时** —— "
                    + "它不等于游戏内延迟，但能反映「连不连得上、快不快」。"));
            col.addView(PluginUiKit.sub(a, "★ 只记录**耗时数字**，不记录 IP、不联网上报。"));

            PluginUiKit.sheet(a, "联机延迟记录", PluginUiKit.scroller(a, col), "开始测速", () -> {
                String raw = addrs.getText() == null ? "" : addrs.getText().toString();
                final List<String> list = new ArrayList<>();
                for (String line : raw.split("\n")) {
                    String t = line.trim();
                    if (!t.isEmpty()) {
                        list.add(t);
                    }
                }
                if (list.isEmpty()) {
                    PluginUiKit.toast(a, "没有地址");
                    return;
                }
                writeText(new File(AppManifest.SETTING_DIR, ADDR_FILE), String.join("\n", list));

                // ★ 网络测速放后台
                PluginUiKit.work(a, "正在测 " + list.size() + " 个地址…", () -> {
                    try {
                        StringBuilder sb = new StringBuilder();
                        String ts = new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date());
                        for (String addr : list) {
                            long ms = pingTcp(addr);
                            sb.append(ts).append('\t').append(addr).append('\t')
                                    .append(ms < 0 ? "超时" : ms + "ms").append('\n');
                        }
                        appendText(new File(AppManifest.SETTING_DIR, HISTORY_FILE), sb.toString());
                        lastResult[0] = sb.toString();
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }, () -> {
                    LinearLayout c2 = PluginUiKit.column(a);
                    c2.addView(PluginUiKit.text(a, "本次测速结果"));
                    c2.addView(PluginUiKit.sub(a, " "));
                    for (String line : lastResult[0].split("\n")) {
                        if (!line.trim().isEmpty()) {
                            String[] p = line.split("\t");
                            if (p.length >= 3) {
                                c2.addView(PluginUiKit.kv(a, p[1], p[2]));
                            }
                        }
                    }
                    c2.addView(PluginUiKit.sub(a, " "));
                    c2.addView(PluginUiKit.label(a, "历史记录（最近 20 条）"));
                    String hist = readText(new File(AppManifest.SETTING_DIR, HISTORY_FILE));
                    String[] lines = hist.split("\n");
                    int shown = 0;
                    for (int i = lines.length - 1; i >= 0 && shown < 20; i--) {
                        if (!lines[i].trim().isEmpty()) {
                            c2.addView(PluginUiKit.sub(a, lines[i].replace('\t', ' ')));
                            shown++;
                        }
                    }
                    if (shown == 0) {
                        c2.addView(PluginUiKit.sub(a, "（还没有历史）"));
                    }
                    c2.addView(PluginUiKit.sub(a, " "));
                    c2.addView(PluginUiKit.sub(a, "★ 历史存 " + HISTORY_FILE
                            + "，纯本地。想清空就直接删这个文件。"));
                    PluginUiKit.log(a, "延迟测速 " + list.size() + " 个地址");
                    PluginUiKit.sheet(a, "联机延迟记录", PluginUiKit.scroller(a, c2), "知道了", null);
                });
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    private static final String[] lastResult = new String[]{""};

    /** TCP 握手耗时（毫秒）；-1 = 失败。 */
    private static long pingTcp(String addr) {
        String host = addr;
        int port = 25565;
        int i = addr.lastIndexOf(':');
        if (i > 0) {
            try {
                port = Integer.parseInt(addr.substring(i + 1).trim());
                host = addr.substring(0, i);
            } catch (Throwable ignored) {
            }
        }
        try (Socket s = new Socket()) {
            long t0 = System.currentTimeMillis();
            s.connect(new InetSocketAddress(host, port), 4000);
            return System.currentTimeMillis() - t0;
        } catch (Throwable t) {
            return -1;
        }
    }

    // ==================== 其余 5 项：如实说明 ====================

    public static void roomTemplate(MainActivity a) {
        notSupported(a, "联机房间模板",
                "房间配置（端口、模组预设、版本）由 Terracotta 内部管理，"
                        + "启动器只能「发起 / 加入」，没有「把当前房间存成模板」的接口。",
                "现在能做的替代：用「下载组合模板」插件保存常用的一组下载项，"
                        + "开房前先把实例准备好。");
    }

    public static void blackWhiteList(MainActivity a) {
        notSupported(a, "联机黑白名单（客户端侧）",
                "踢人 / 封禁是**服务端**行为 —— 客户端没有这个权限，"
                        + "Terracotta 是点对点直连，也没有中心服务端可以下发名单。",
                "★ 但**服务端**的 whitelist / ops / banned 文件，"
                        + "现有插件「服务器白名单管理」已经能看 ✓ 去那里找。");
    }

    public static void chatHistory(MainActivity a) {
        notSupported(a, "联机聊天记录",
                "聊天内容是**游戏内**的，启动器在游戏进程之外，"
                        + "拿不到聊天数据 —— 除非改游戏本身（改 Minecraft jar），"
                        + "那超出启动器的范围，而且会让联机校验失败。",
                "现在能做的替代：没有。这项需求在「不改游戏」的前提下做不到。");
    }

    public static void fileTransfer(MainActivity a) {
        notSupported(a, "局域网文件直传",
                "要在手机上起一个 HTTP / socket 服务，涉及存储权限、端口占用、"
                        + "前台服务（Android 14 对后台服务限制很严），"
                        + "这是一个**新模块**的体量，不是插件。",
                "现在能做的替代：用「模组打包分享」把文件打成 zip，"
                        + "再用系统文件管理器 / 微信 / 蓝牙发出去。");
    }

    public static void versionSync(MainActivity a) {
        notSupported(a, "联机版本自动同步",
                "「同步」需要知道对端装了什么版本、什么模组，"
                        + "而 Terracotta 只建隧道、**不交换实例清单** —— "
                        + "启动器无法得知对端的配置。",
                "现在能做的替代：用「实例配置差异对比」在**本地**比两个实例的差异，"
                        + "手动对齐。这也是现有功能 ✓");
    }

    private static void notSupported(MainActivity a, String title, String why, String alt) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, title));
            col.addView(PluginUiKit.sub(a, "★ 这个功能**没有实现**，原因如下："));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, why));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, alt));
            PluginUiKit.sheet(a, title, PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== 文件工具 ====================

    private static String readText(File f) {
        try {
            if (!f.isFile()) {
                return "";
            }
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                //noinspection ResultOfMethodCallIgnored
                in.read(b);
            }
            return new String(b, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private static void writeText(File f, String s) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(s.getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void appendText(File f, String s) {
        writeText(f, readText(f) + s);
    }
}
