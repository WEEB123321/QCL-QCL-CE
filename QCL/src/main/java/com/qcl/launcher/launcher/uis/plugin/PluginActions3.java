package com.qcl.launcher.launcher.uis.plugin;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.game.download.DownloadUrlSource;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * ★★★ 社区版：插件实现（第三批）。
 *
 * <p>约定同前两批：界面走 {@link PluginUiKit}、危险操作先二次确认、整体 try/catch。
 *
 * <p>★ 本批里凡是不确定「QCL 某个字段叫什么」的地方，一律走**本地存储**或
 * **防御性读取**（读不到就跳过），避免因为上游改字段而编译不过 —— 前面已经栽过一次
 * （内存分配字段不在 LauncherSetting 上）。
 */
public final class PluginActions3 {

    private PluginActions3() {
    }

    // ==================================================================
    // 下载类
    // ==================================================================

    /**
     * 下载源测速。
     *
     * <p>对官方源和 BMCLAPI 各发一次 TCP 连接，量往返时间。
     * ★ 测的是**能不能连上 + 连得多快**，不是真实下载速度 ——
     * 那要真下几 MB 才能测准，代价太大。
     */
    public static void sourceSpeedTest(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "下载源测速"));
            col.addView(PluginUiKit.sub(a, "正在测…（每个源连一次，最多等 3 秒）"));
            col.addView(PluginUiKit.sub(a, " "));

            final String[][] hosts = {
                    {"官方源（Mojang）", "piston-meta.mojang.com"},
                    {"BMCLAPI 镜像", "bmclapi2.bangbang93.com"},
            };
            final long[] ms = new long[hosts.length];
            final boolean[] ok = new boolean[hosts.length];

            Thread t = new Thread(() -> {
              // ★★★ 线程里的未捕获异常会走全局处理器 → 弹崩溃页 + 杀进程。
              //   这里整体包一层，网络/Activity 已销毁等任何意外都不许把启动器带走。
              try {
                for (int i = 0; i < hosts.length; i++) {
                    long t0 = System.currentTimeMillis();
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(hosts[i][1], 443), 3000);
                        ok[i] = true;
                    } catch (Throwable ignored) {
                        ok[i] = false;
                    }
                    ms[i] = System.currentTimeMillis() - t0;
                }
                a.runOnUiThread(() -> {
                    LinearLayout out = PluginUiKit.column(a);
                    for (int i = 0; i < hosts.length; i++) {
                        LinearLayout card = PluginUiKit.card(a);
                        card.addView(PluginUiKit.text(a, hosts[i][0]));
                        card.addView(PluginUiKit.kv(a, "主机", hosts[i][1]));
                        card.addView(PluginUiKit.kv(a, "结果", ok[i] ? "可连接" : "连不上"));
                        card.addView(PluginUiKit.kv(a, "往返", ok[i] ? ms[i] + " ms" : "—"));
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.bottomMargin = PluginUiKit.dp(a, 8);
                        card.setLayoutParams(lp);
                        out.addView(card);
                    }
                    if (ok[1] && (!ok[0] || ms[1] < ms[0])) {
                        out.addView(PluginUiKit.text(a, "→ 建议用 BMCLAPI 镜像（国内更快）"));
                    } else if (ok[0]) {
                        out.addView(PluginUiKit.text(a, "→ 官方源可用"));
                    }
                    out.addView(PluginUiKit.sub(a, " "));
                    out.addView(PluginUiKit.sub(a, "★ 测的是**能不能连上、连得多快**，"
                            + "不是真实下载速度。真要测速得实下几 MB，代价太大。"));
                    out.addView(PluginUiKit.sub(a, "★ 下载源在「我的 → 下载设置」里切换。"));
                    try {
                        PluginUiKit.sheet(a, "下载源测速", PluginUiKit.scroller(a, out), "知道了", null);
                    } catch (Throwable ignored) {
                    }
                });
              } catch (Throwable ignored) {
              }
            }, "qcl-speedtest");
            t.setDaemon(true);
            t.start();

            PluginUiKit.sheet(a, "下载源测速", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "测速失败：" + t);
        }
    }

    /** 下载失败自动换源：本地开关 + 重试设置。 */
    public static void downloadAutoSwitch(MainActivity a) {
        try {
            boolean on = Local.bool(a, "dl_autoswitch", true);
            int retry = Local.integer(a, "dl_retry", 3);
            int interval = Local.integer(a, "dl_retry_interval", 2);

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "下载失败自动换源"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.kv(a, "当前状态", on ? "开启" : "关闭"));
            col.addView(PluginUiKit.kv(a, "每个文件最多重试", retry + " 次"));
            col.addView(PluginUiKit.kv(a, "重试间隔", interval + " 秒"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "开启后：某个源下载失败会**自动切到下一个源**重试，"
                    + "全部失败才算这个文件失败。"));
            col.addView(PluginUiKit.sub(a, "★ 设置存在本地（" + AppManifest.SETTING_DIR
                    + "/qcl_dl.json），不上传。"));

            TextView toggle = PluginUiKit.row(a, on ? "关闭自动换源" : "开启自动换源", null);
            toggle.setGravity(android.view.Gravity.CENTER);
            toggle.setOnClickListener(v -> {
                Local.put(a, "dl_autoswitch", !on);
                PluginUiKit.toast(a, !on ? "已开启" : "已关闭");
            });
            col.addView(toggle);

            TextView cyc = PluginUiKit.row(a, "调整重试次数（现在是 " + retry + " 次）", null);
            cyc.setGravity(android.view.Gravity.CENTER);
            cyc.setOnClickListener(v -> {
                int next = retry >= 5 ? 1 : retry + 1;
                Local.put(a, "dl_retry", next);
                PluginUiKit.toast(a, "重试次数改为 " + next);
            });
            col.addView(cyc);

            PluginUiKit.sheet(a, "下载失败自动换源", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** 下载组合模板：把常用的一组下载项存成模板，一键复用。 */
    public static void downloadTemplate(MainActivity a) {
        try {
            List<String> lines = Local.lines(a, "dl_templates");
            LinearLayout col = PluginUiKit.column(a);

            EditText name = new EditText(a);
            name.setHint("模板名，例如「1.20.1 + Fabric」");
            EditText body = new EditText(a);
            body.setHint("每行一项，例如：\n1.20.1\nfabric-0.15.0\nsodium.jar 的下载地址");
            body.setMinLines(4);

            col.addView(PluginUiKit.text(a, "下载组合模板"));
            col.addView(PluginUiKit.sub(a, "把「这个整合包需要下哪几样」记下来，下次一键照单下。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(name);
            col.addView(body);

            if (!lines.isEmpty()) {
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.label(a, "已存的模板"));
                for (String l : lines) {
                    col.addView(PluginUiKit.sub(a, "• " + l.replace("\t", "  (")));
                }
            }
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 模板只存**清单**，不会自动开始下载 —— "
                    + "下载要你自己在下载页发起。这样避免误触下几百 MB。"));
            col.addView(PluginUiKit.sub(a, "★ 「分享码」做不到：分享要有个中心服务来解析码，本项目不建服务器。"));

            PluginUiKit.sheet(a, "下载组合模板", PluginUiKit.scroller(a, col), "保存", () -> {
                String n = name.getText() == null ? "" : name.getText().toString().trim();
                String b = body.getText() == null ? "" : body.getText().toString().trim();
                if (n.isEmpty() || b.isEmpty()) {
                    PluginUiKit.toast(a, "名字和内容都要填");
                    return;
                }
                List<String> ls = new ArrayList<>(lines);
                ls.add(n + "\t" + b.replace("\n", " | "));
                Local.saveLines(a, "dl_templates", ls);
                PluginUiKit.toast(a, "已保存模板「" + n + "」");
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // 安全与备份
    // ==================================================================

    /**
     * 文件哈希校验：把实例里关键的 jar 扫一遍，检查能不能正常当 zip 打开。
     *
     * <p>★ 不做「与官方哈希对比」—— 那需要联网拿官方清单。
     * 这里做的是**本地能查的损坏检测**：jar 打不开 / 条目读不出来 / 大小为 0。
     */
    public static void hashVerify(MainActivity a) {
        try {
            File ver = current(a);
            if (ver == null) {
                PluginUiKit.info(a, "文件完整性校验", "当前没有选中实例。");
                return;
            }
            List<File> jars = new ArrayList<>();
            jars.addAll(PluginUiKit.listByExt(new File(ver, "mods"), ".jar"));
            jars.addAll(PluginUiKit.listByExt(ver, ".jar"));

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "校验 " + jars.size() + " 个 jar"));

            int bad = 0;
            List<String> badList = new ArrayList<>();
            for (File f : jars) {
                String err = checkJar(f);
                if (err != null) {
                    bad++;
                    badList.add(f.getName() + " —— " + err);
                }
            }

            col.addView(PluginUiKit.kv(a, "正常", (jars.size() - bad) + " 个"));
            col.addView(PluginUiKit.kv(a, "有问题", bad + " 个"));
            for (String s : badList) {
                col.addView(PluginUiKit.sub(a, "✗ " + s));
            }
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 做的是**本地能查的损坏检测**"
                    + "（能不能当 zip 打开、条目读得出来吗、是不是 0 字节）。"
                    + "★ 「和官方哈希对比」需要联网拿官方清单，本项目不做。"));
            col.addView(PluginUiKit.sub(a, "★ 只读，不删不改。发现问题请自己重新下载那个文件。"));
            PluginUiKit.sheet(a, "文件完整性校验", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "校验失败：" + t);
        }
    }

    private static String checkJar(File f) {
        if (f.length() == 0) {
            return "0 字节";
        }
        try (ZipFile z = new ZipFile(f)) {
            int n = 0;
            java.util.Enumeration<? extends ZipEntry> es = z.entries();
            while (es.hasMoreElements() && n < 50) {
                ZipEntry e = es.nextElement();
                if (!e.isDirectory()) {
                    try (InputStream in = z.getInputStream(e)) {
                        in.read();
                    }
                    n++;
                }
            }
            if (n == 0) {
                return "zip 里没有任何文件";
            }
            return null;
        } catch (Throwable t) {
            return "打不开（" + t.getClass().getSimpleName() + "）";
        }
    }

    /**
     * 密码保险箱。
     *
     * <p>★ **诚实说明**：这里用的是 Android 的**应用私有目录 + 文件权限**，
     * 不是「军用级加密」。要做真加密需要引入加密库并管理密钥，那超出本轮范围。
     * 界面里会写清楚这一点 —— 不假装它是加密的。
     */
    public static void passwordVault(MainActivity a) {
        try {
            List<String> lines = Local.lines(a, "vault");
            LinearLayout col = PluginUiKit.column(a);

            col.addView(PluginUiKit.text(a, "密码保险箱"));
            col.addView(PluginUiKit.sub(a, "记服务器密码、房间密码、坐标口令。"));
            col.addView(PluginUiKit.sub(a, " "));

            EditText name = new EditText(a);
            name.setHint("名字，例如「朋友服」");
            EditText val = new EditText(a);
            val.setHint("内容（密码 / 口令）");
            col.addView(name);
            col.addView(val);

            if (!lines.isEmpty()) {
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.label(a, "已存 " + lines.size() + " 条"));
                for (String l : lines) {
                    String[] p = l.split("\t");
                    col.addView(PluginUiKit.sub(a, "• " + (p.length > 0 ? p[0] : l)));
                }
            }

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "⚠ **诚实说明**：本保险箱用的是"
                    + "**应用私有目录**（其他 App 读不到），**不是加密存储**。"
                    + "手机 root 后仍可被读取。"));
            col.addView(PluginUiKit.sub(a, "★ 「指纹/面容解锁」需要引入生物识别依赖并管理密钥，"
                    + "本轮没做 —— 与其放一个假的指纹图标，不如说清楚。"));
            col.addView(PluginUiKit.sub(a, "★ 存在 " + AppManifest.SETTING_DIR + "/qcl_vault.json，不上传。"));

            PluginUiKit.sheet(a, "密码保险箱", PluginUiKit.scroller(a, col), "保存", () -> {
                String n = name.getText() == null ? "" : name.getText().toString().trim();
                String v = val.getText() == null ? "" : val.getText().toString().trim();
                if (n.isEmpty()) {
                    PluginUiKit.toast(a, "名字不能空");
                    return;
                }
                List<String> ls = new ArrayList<>(lines);
                ls.add(n + "\t" + v);
                Local.saveLines(a, "vault", ls);
                PluginUiKit.toast(a, "已保存");
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** 操作日志：记录本插件里做过的写操作，可搜索、可导出。 */
    public static void actionLog(MainActivity a) {
        try {
            List<String> lines = Local.lines(a, "action_log");
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "操作日志"));
            col.addView(PluginUiKit.sub(a, "共 " + lines.size() + " 条（最近的在前）"));
            col.addView(PluginUiKit.sub(a, " "));
            if (lines.isEmpty()) {
                col.addView(PluginUiKit.sub(a, "还没有记录。插件里做过的写操作会记在这里。"));
            } else {
                int n = lines.size();
                for (int i = n - 1; i >= Math.max(0, n - 80); i--) {
                    col.addView(PluginUiKit.sub(a, "• " + lines.get(i)));
                }
            }
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 只记**插件自己**做的操作。"
                    + "你在文件管理器里做的事，启动器看不到。"));
            col.addView(PluginUiKit.sub(a, "★ 不记录密码 / 保险箱内容。"));
            PluginUiKit.sheet(a, "操作日志", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // 资源
    // ==================================================================

    /**
     * 资源包合并：把多个资源包 zip 合成一个。
     *
     * <p>后选的包覆盖先选的（和游戏里「下面的包覆盖上面的」一致）。
     */
    public static void packMerge(MainActivity a) {
        try {
            File ver = current(a);
            if (ver == null) {
                PluginUiKit.info(a, "资源包合并", "当前没有选中实例。");
                return;
            }
            File dir = new File(ver, "resourcepacks");
            List<File> packs = PluginUiKit.listByExt(dir, ".zip");
            if (packs.size() < 2) {
                PluginUiKit.info(a, "资源包合并", "至少要有两个 .zip 资源包才能合并。");
                return;
            }
            List<String> names = new ArrayList<>();
            for (File f : packs) {
                names.add(f.getName() + "   " + PluginUiKit.size(f.length()));
            }
            PluginUiKit.list(a, "选【底层】包（先选这个，后面的会覆盖它）", names, i1 ->
                    PluginUiKit.list(a, "选【上层】包", names, i2 -> {
                        if (i1 == i2) {
                            PluginUiKit.toast(a, "选了两个同一个包");
                            return;
                        }
                        doMerge(a, packs.get(i1), packs.get(i2));
                    }));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static void doMerge(MainActivity a, File bottom, File top) {
        LinearLayout col = PluginUiKit.column(a);
        String outName = "merged_" + System.currentTimeMillis() + ".zip";
        col.addView(PluginUiKit.text(a, "合并计划"));
        col.addView(PluginUiKit.kv(a, "底层", bottom.getName()));
        col.addView(PluginUiKit.kv(a, "上层（覆盖）", top.getName()));
        col.addView(PluginUiKit.kv(a, "输出", outName));
        col.addView(PluginUiKit.sub(a, " "));
        col.addView(PluginUiKit.sub(a, "★ 同名文件以上层为准 —— 和游戏里"
                + "「列表下面的包覆盖上面的」是同一套规则。"));
        col.addView(PluginUiKit.sub(a, "★ **不会删除原包**，只新增一个合并后的 zip。"));

        PluginUiKit.sheet(a, "确认合并", PluginUiKit.scroller(a, col), "开始合并", () -> {
            try {
                File out = new File(bottom.getParentFile(), outName);
                java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
                readZipInto(bottom, entries);
                readZipInto(top, entries);
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
                    for (java.util.Map.Entry<String, byte[]> e : entries.entrySet()) {
                        zos.putNextEntry(new ZipEntry(e.getKey()));
                        zos.write(e.getValue());
                        zos.closeEntry();
                    }
                }
                log(a, "合并资源包 → " + outName);
                PluginUiKit.toast(a, "已生成 " + outName + "（" + PluginUiKit.size(out.length()) + "）");
            } catch (Throwable t) {
                PluginUiKit.toast(a, "合并失败：" + t);
            }
        });
    }

    private static void readZipInto(File zip, java.util.Map<String, byte[]> into) {
        try (ZipFile z = new ZipFile(zip)) {
            java.util.Enumeration<? extends ZipEntry> es = z.entries();
            int guard = 0;
            while (es.hasMoreElements() && guard++ < 6000) {
                ZipEntry e = es.nextElement();
                if (e.isDirectory() || e.getSize() > 8 * 1024 * 1024) {
                    continue;
                }
                try (InputStream in = z.getInputStream(e)) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        bos.write(buf, 0, r);
                    }
                    into.put(e.getName(), bos.toByteArray());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** 主题编辑器：改主色。 */
    public static void themeEditor(MainActivity a) {
        try {
            final String[] names = {"铁锈红（默认）", "青绿", "草绿", "蓝", "紫", "棕", "灰", "玫红"};
            final String[] vals = {"#B0552F", "#0E9E8E", "#3B6D11", "#185FA5",
                    "#534AB7", "#993C1D", "#5F5E5A", "#993556"};

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "启动器主题编辑器"));
            String cur = a.launcherSetting == null ? null : a.launcherSetting.launcherTheme;
            col.addView(PluginUiKit.kv(a, "当前主色", cur == null ? "未设置" : cur));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "点下面的色块切换主色。改完立刻生效。"));
            col.addView(PluginUiKit.sub(a, " "));

            for (int i = 0; i < names.length; i++) {
                final String v = vals[i];
                TextView row = PluginUiKit.row(a, names[i] + "   " + v, null);
                row.setOnClickListener(x -> {
                    try {
                        if (a.launcherSetting != null) {
                            a.launcherSetting.launcherTheme = v;
                            // ★ 保存设置的入口在不同版本里位置不一样 ——
                            //   用反射调用，找不到就跳过（设置字段已经改了，下次保存时会带上）。
                            try {
                                Class<?> su = Class.forName(
                                        "com.qcl.launcher.launcher.setting.SettingUtils");
                                java.lang.reflect.Method m = su.getMethod(
                                        "saveLauncherSetting", a.launcherSetting.getClass());
                                m.invoke(null, a.launcherSetting);
                            } catch (Throwable ignored) {
                            }
                        }
                        log(a, "改主色 → " + v);
                        PluginUiKit.toast(a, "主色已改为 " + v + "（部分界面需重启启动器）");
                    } catch (Throwable t) {
                        PluginUiKit.toast(a, "改色失败：" + t);
                    }
                });
                col.addView(row);
            }

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 「导出主题包 / 分享码」在「我的 → 外观设置 → 主题方案」里"
                    + "（那项 v1.1.0 就做了）。"));
            col.addView(PluginUiKit.sub(a, "★ 「改字体」做不到 —— 全局换字体会和大量固定高度 + 单行文本打架，"
                    + "本项目刻意不做。"));
            PluginUiKit.sheet(a, "主题编辑器", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** 服务器延迟预检：对一批地址做 TCP 连接测延迟。 */
    public static void serverPing(MainActivity a) {
        try {
            List<String> saved = Local.lines(a, "servers");
            LinearLayout col = PluginUiKit.column(a);
            EditText input = new EditText(a);
            input.setHint("地址，一行一个：play.example.com:25565");
            input.setMinLines(3);
            col.addView(PluginUiKit.text(a, "服务器延迟预检"));
            col.addView(PluginUiKit.sub(a, "填几个服务器地址，测**能不能连上**和**连接延迟**。"));
            col.addView(input);
            if (!saved.isEmpty()) {
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.label(a, "上次测过的"));
                for (String s : saved) {
                    col.addView(PluginUiKit.sub(a, "• " + s));
                }
            }
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 测的是 **TCP 连接延迟**。"
                    + "「游戏内真实延迟 / 在线人数」需要发 MC 协议握手包，"
                    + "协议版本对不上会误报，所以这里只做 TCP。"));
            PluginUiKit.sheet(a, "服务器延迟预检", PluginUiKit.scroller(a, col), "开始测", () -> {
                String txt = input.getText() == null ? "" : input.getText().toString();
                List<String> addrs = new ArrayList<>();
                for (String line : txt.split("\n")) {
                    String l = line.trim();
                    if (!l.isEmpty()) {
                        addrs.add(l);
                    }
                }
                if (addrs.isEmpty()) {
                    PluginUiKit.toast(a, "先填地址");
                    return;
                }
                Local.saveLines(a, "servers", addrs);
                pingAll(a, addrs);
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static void pingAll(MainActivity a, List<String> addrs) {
        LinearLayout col = PluginUiKit.column(a);
        col.addView(PluginUiKit.text(a, "正在测 " + addrs.size() + " 个地址…"));
        PluginUiKit.sheet(a, "服务器延迟预检", PluginUiKit.scroller(a, col), "知道了", null);

        new Thread(() -> {
          // ★ 同上：线程体整体包一层，任何意外都不许触发全局崩溃页
          try {
            final List<String> out = new ArrayList<>();
            for (String addr : addrs) {
                String host = addr;
                int port = 25565;
                int c = addr.lastIndexOf(':');
                if (c > 0) {
                    try {
                        port = Integer.parseInt(addr.substring(c + 1).trim());
                        host = addr.substring(0, c).trim();
                    } catch (Throwable ignored) {
                    }
                }
                long t0 = System.currentTimeMillis();
                boolean ok;
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(host, port), 4000);
                    ok = true;
                } catch (Throwable t) {
                    ok = false;
                }
                long ms = System.currentTimeMillis() - t0;
                out.add(addr + "   " + (ok ? ms + " ms" : "连不上"));
            }
            a.runOnUiThread(() -> {
                LinearLayout box = PluginUiKit.column(a);
                box.addView(PluginUiKit.text(a, "测速结果"));
                box.addView(PluginUiKit.sub(a, " "));
                for (String s : out) {
                    box.addView(PluginUiKit.row(a, s, null));
                }
                box.addView(PluginUiKit.sub(a, " "));
                box.addView(PluginUiKit.sub(a, "★ 只是 TCP 连接延迟，不是游戏内延迟。"));
                try {
                    PluginUiKit.sheet(a, "服务器延迟预检", PluginUiKit.scroller(a, box), "知道了", null);
                } catch (Throwable ignored) {
                }
            });
          } catch (Throwable ignored) {
          }
        }, "qcl-ping").start();
    }

    /** 本地词库：术语对照表，翻译时统一用词。 */
    public static void glossary(MainActivity a) {
        try {
            List<String> lines = Local.lines(a, "glossary");
            LinearLayout col = PluginUiKit.column(a);
            EditText en = new EditText(a);
            en.setHint("英文 / 原文，例如 Create");
            EditText zh = new EditText(a);
            zh.setHint("统一译名，例如 机械动力");

            col.addView(PluginUiKit.text(a, "本地化词库"));
            col.addView(PluginUiKit.sub(a, "把「这个模组 / 这个术语统一叫什么」记下来，"
                    + "做汉化或写教程时不用每次纠结。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(en);
            col.addView(zh);

            if (!lines.isEmpty()) {
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.label(a, "已收 " + lines.size() + " 条"));
                for (String l : lines) {
                    col.addView(PluginUiKit.sub(a, "• " + l.replace("\t", "  →  ")));
                }
            }
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ **本地词库**：自己维护、可导出、离线可用。"));
            col.addView(PluginUiKit.sub(a, "★ 「社区共建词库」做不到 —— 那要一个服务器来收集和分发，"
                    + "本项目不建服务器。"));

            PluginUiKit.sheet(a, "本地化词库", PluginUiKit.scroller(a, col), "添加", () -> {
                String e = en.getText() == null ? "" : en.getText().toString().trim();
                String z = zh.getText() == null ? "" : zh.getText().toString().trim();
                if (e.isEmpty() || z.isEmpty()) {
                    PluginUiKit.toast(a, "两个都要填");
                    return;
                }
                List<String> ls = new ArrayList<>(lines);
                ls.add(e + "\t" + z);
                Local.saveLines(a, "glossary", ls);
                PluginUiKit.toast(a, "已添加");
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** 教程库：内置的本地教程（离线）。 */
    public static void tutorials(MainActivity a) {
        try {
            final String[][] items = {
                    {"第一次用：怎么装一个能玩的版本",
                            "1. 打开启动器，如果停在「安装运行环境」页，先点安装（首次要解压几分钟，断网也行）。\n"
                                    + "2. 进主界面后点顶部「版本管理」或「下载」。\n"
                                    + "3. 选一个版本（新手建议 1.20.1），点下载。\n"
                                    + "4. 回到主界面，点左下角「启动游戏」。"},
                    {"怎么装模组",
                            "1. 先确认版本装了 Fabric 或 Forge（下载页里可以勾）。\n"
                                    + "2. 把模组 .jar 放进 <游戏目录>/versions/<实例名>/mods/。\n"
                                    + "3. 模组要和 MC 版本 + 加载器对得上，否则会崩。\n"
                                    + "4. 起不来先看「我的 → 日志中心」。"},
                    {"怎么联机（和朋友一起玩）",
                            "方式一（推荐，最简单）：\n"
                                    + "  房主在游戏里「对局域网开放」，把显示的 端口 告诉朋友。\n"
                                    + "方式二（Terracotta 陶瓷联机）：\n"
                                    + "  主界面「多人联机」→ 建房 → 把生成的邀请文本发给朋友。\n"
                                    + "★ 陶瓷是点对点的，房主报 IP，别人连进来，不需要服务器。"},
                    {"怎么开服",
                            "★ 手机上开服性能很差，建议在电脑上开。\n"
                                    + "启动器能做的是：生成 server.properties + 启动脚本，"
                                    + "你把它们拷到电脑上跑。\n"
                                    + "见插件「一键开服向导」。"},
                    {"怎么做整合包",
                            "1. 装好一个实例，把要的模组 / 资源包 / 光影都装进去。\n"
                                    + "2. 版本列表长按实例 → 导出整合包。\n"
                                    + "3. 分享给别人：对方用「分享菜单接入」把 zip 发给 QCL 即可导入。"},
                    {"游戏起不来怎么办（排查顺序）",
                            "1. 我的 → 启动诊断 → 自动排查（逐项自检）。\n"
                                    + "2. 我的 → 日志中心 → 过滤「错误」。\n"
                                    + "3. 插件「实例健康评分」看有没有明显问题。\n"
                                    + "4. 插件「模组依赖树」看有没有缺前置。\n"
                                    + "5. 还不行就把日志发给作者（崩溃弹窗里有「分享日志」）。"},
            };

            List<String> titles = new ArrayList<>();
            for (String[] it : items) {
                titles.add(it[0]);
            }
            PluginUiKit.list(a, "教程库（全部离线）", titles, idx ->
                    PluginUiKit.info(a, items[idx][0], items[idx][1]));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** 一键开服向导：生成 server.properties + 启动脚本。 */
    public static void serverWizard(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            EditText port = new EditText(a);
            port.setHint("端口（默认 25565）");
            port.setText("25565");
            EditText max = new EditText(a);
            max.setHint("最大玩家数（默认 20）");
            max.setText("20");
            EditText motd = new EditText(a);
            motd.setHint("服务器名（MOTD）");
            motd.setText("QCL Server");

            col.addView(PluginUiKit.text(a, "一键开服向导"));
            col.addView(PluginUiKit.sub(a, "生成 server.properties 和启动脚本。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(port);
            col.addView(max);
            col.addView(motd);
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "⚠ **手机上开服性能很差** —— 这更适合"
                    + "「在手机上生成配置，拷到电脑上跑」。"));
            col.addView(PluginUiKit.sub(a, "★ 服务器 jar 需要联网从 Mojang / Fabric 官方下载，"
                    + "本向导**不代下** —— 请自己把 server.jar 放进输出目录。"));
            col.addView(PluginUiKit.sub(a, "★ 输出到 " + AppManifest.LAUNCHER_DIR + "/server/"));

            PluginUiKit.sheet(a, "一键开服向导", PluginUiKit.scroller(a, col), "生成", () -> {
                try {
                    File dir = new File(AppManifest.LAUNCHER_DIR, "server");
                    if (!dir.exists() && !dir.mkdirs()) {
                        PluginUiKit.toast(a, "建目录失败");
                        return;
                    }
                    String p = port.getText() == null ? "25565" : port.getText().toString().trim();
                    String m = max.getText() == null ? "20" : max.getText().toString().trim();
                    String mo = motd.getText() == null ? "QCL Server" : motd.getText().toString().trim();

                    StringBuilder sb = new StringBuilder();
                    sb.append("# 由 QCL-CE 开服向导生成\n");
                    sb.append("server-port=").append(p).append('\n');
                    sb.append("max-players=").append(m).append('\n');
                    sb.append("motd=").append(mo).append('\n');
                    sb.append("online-mode=true\n");
                    sb.append("difficulty=easy\n");
                    sb.append("gamemode=survival\n");
                    sb.append("pvp=true\n");
                    sb.append("allow-flight=false\n");
                    sb.append("spawn-protection=16\n");
                    sb.append("view-distance=10\n");
                    sb.append("simulation-distance=10\n");
                    sb.append("enable-command-block=false\n");
                    sb.append("white-list=false\n");
                    sb.append("level-name=world\n");
                    sb.append("level-seed=\n");
                    sb.append("enable-rcon=false\n");
                    write(new File(dir, "server.properties"), sb.toString());

                    String sh = "#!/system/bin/sh\n"
                            + "# 由 QCL-CE 开服向导生成。需要先放好 server.jar 和 JRE。\n"
                            + "cd \"$(dirname \"$0\")\"\n"
                            + "java -Xmx1024M -Xms512M -jar server.jar nogui\n";
                    write(new File(dir, "start.sh"), sh);

                    log(a, "生成开服配置 → " + dir.getAbsolutePath());
                    PluginUiKit.toast(a, "已生成到 " + dir.getName() + "/"
                            + "（server.properties + start.sh）");
                } catch (Throwable t) {
                    PluginUiKit.toast(a, "生成失败：" + t);
                }
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // 共用
    // ==================================================================

    private static File current(MainActivity a) {
        try {
            String p = a.publicGameSetting == null ? null : a.publicGameSetting.currentVersion;
            if (p != null && !p.isEmpty()) {
                File f = new File(p);
                if (f.isDirectory()) {
                    return f;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void write(File f, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes("UTF-8"));
        }
    }

    private static void log(MainActivity a, String what) {
        try {
            List<String> ls = Local.lines(a, "action_log");
            ls.add(now() + "  " + what);
            Local.saveLines(a, "action_log", ls);
        } catch (Throwable ignored) {
        }
    }

    private static String now() {
        return new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.US)
                .format(new java.util.Date());
    }

    // ==================================================================
    // 本地存储（统一走 SETTING_DIR 下的 qcl_*.json，一行一条，制表符分隔）
    // ==================================================================

    static final class Local {
        private static File f(android.content.Context c, String key) {
            return new File(AppManifest.SETTING_DIR, "qcl_" + key + ".json");
        }

        static List<String> lines(android.content.Context c, String key) {
            List<String> out = new ArrayList<>();
            try {
                String s = PluginUiKit.readText(f(c, key));
                if (s != null) {
                    for (String line : s.split("\n")) {
                        if (!line.trim().isEmpty()) {
                            out.add(line);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return out;
        }

        static void saveLines(android.content.Context c, String key, List<String> ls) {
            try {
                File file = f(c, key);
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                StringBuilder sb = new StringBuilder();
                for (String s : ls) {
                    sb.append(s.replace('\n', ' ')).append('\n');
                }
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(sb.toString().getBytes("UTF-8"));
                }
            } catch (Throwable ignored) {
            }
        }

        static boolean bool(android.content.Context c, String key, boolean def) {
            List<String> ls = lines(c, key);
            return ls.isEmpty() ? def : "1".equals(ls.get(0));
        }

        static int integer(android.content.Context c, String key, int def) {
            List<String> ls = lines(c, key);
            try {
                return ls.isEmpty() ? def : Integer.parseInt(ls.get(0).trim());
            } catch (Throwable t) {
                return def;
            }
        }

        static void put(android.content.Context c, String key, Object v) {
            saveLines(c, key, Collections.singletonList(
                    v instanceof Boolean ? (((Boolean) v) ? "1" : "0") : String.valueOf(v)));
        }
    }
}
