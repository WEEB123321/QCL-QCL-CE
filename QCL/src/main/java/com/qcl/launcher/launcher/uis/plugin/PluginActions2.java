package com.qcl.launcher.launcher.uis.plugin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.game.World;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.launcher.stats.StatsTracker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ★★★ 社区版：插件实现（第二批）。
 *
 * <p>拆成第二个文件只是为了避免单个文件过长；约定与 {@link PluginActions} 完全一致：
 * 界面走 {@link PluginUiKit}、危险操作先二次确认、整体 try/catch。
 */
public final class PluginActions2 {

    private PluginActions2() {
    }

    // ==================================================================
    // 模组管理
    // ==================================================================

    /**
     * 模组依赖树。
     *
     * <p>读每个 mod jar 里**自己声明的**依赖：
     * Fabric 读 {@code fabric.mod.json} 的 {@code depends}，
     * Forge/NeoForge 读 {@code META-INF/mods.toml} / {@code neoforge.mods.toml} 的 {@code [[dependencies]]}。
     *
     * <p>★ 只做「谁依赖谁」的文本树 + 缺失前置标记，**不画图** ——
     * 手机上几十个模组的图形化依赖图既看不清也没必要。
     */
    public static void modDependencyTree(MainActivity a) {
        try {
            File ver = currentVersion(a);
            if (ver == null) {
                PluginUiKit.info(a, "模组依赖树", "当前没有选中实例。");
                return;
            }
            List<File> mods = PluginUiKit.listByExt(new File(ver, "mods"), ".jar");
            if (mods.isEmpty()) {
                PluginUiKit.info(a, "模组依赖树", "这个实例的 mods 目录是空的。");
                return;
            }

            Map<String, String> idToFile = new LinkedHashMap<>();   // modId -> 文件名
            Map<String, List<String>> depends = new LinkedHashMap<>();

            for (File m : mods) {
                List<String> ids = readModIds(m);
                List<String> deps = readModDepends(m);
                String primary = ids.isEmpty() ? null : ids.get(0);
                if (primary != null) {
                    idToFile.put(primary, m.getName());
                    depends.put(primary, deps);
                }
                // 一个 jar 可能声明多个 modId，全部登记，避免误报「缺前置」
                for (int i = 1; i < ids.size(); i++) {
                    idToFile.put(ids.get(i), m.getName());
                }
            }

            LinearLayout col = PluginUiKit.column(a);
            LinearLayout head = PluginUiKit.card(a);
            head.addView(PluginUiKit.text(a, mods.size() + " 个模组，" + idToFile.size() + " 个 modId"));
            head.addView(PluginUiKit.sub(a, "读的是每个模组**自己声明的**依赖，不是猜的。"));
            col.addView(head);

            // 统计被依赖次数
            Map<String, Integer> inDegree = new HashMap<>();
            for (List<String> ds : depends.values()) {
                for (String d : ds) {
                    inDegree.put(d, inDegree.getOrDefault(d, 0) + 1);
                }
            }

            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : depends.entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                LinearLayout card = PluginUiKit.card(a);
                card.addView(PluginUiKit.text(a, e.getKey()));
                card.addView(PluginUiKit.sub(a, "  来自 " + idToFile.get(e.getKey())));
                for (String d : e.getValue()) {
                    boolean have = idToFile.containsKey(d);
                    if (!have) {
                        missing.add(e.getKey() + " → " + d);
                    }
                    card.addView(PluginUiKit.sub(a,
                            (have ? "    └ " : "    ✗ ") + d
                                    + (have ? "" : "   【缺失】")));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = PluginUiKit.dp(a, 8);
                card.setLayoutParams(lp);
                col.addView(card);
            }

            LinearLayout sum = PluginUiKit.card(a);
            sum.addView(PluginUiKit.label(a, "汇总"));
            sum.addView(PluginUiKit.kv(a, "声明了依赖的模组", depends.size() + " 个"));
            sum.addView(PluginUiKit.kv(a, "缺失的前置", missing.size() + " 个"));
            for (String s : missing) {
                sum.addView(PluginUiKit.sub(a, "✗ " + s));
            }
            col.addView(sum);

            col.addView(PluginUiKit.sub(a, "★ 缺失不一定是错：有些依赖是**可选**的，"
                    + "或者被同 jar 内的另一个 modId 提供了。真起不来时以游戏日志为准。"));
            PluginUiKit.sheet(a, "模组依赖树", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "读依赖失败：" + t);
        }
    }

    private static List<String> readModIds(File jar) {
        List<String> out = new ArrayList<>();
        try (ZipFile z = new ZipFile(jar)) {
            ZipEntry e = z.getEntry("fabric.mod.json");
            if (e != null) {
                String s = readEntry(z, e);
                String id = PluginUiKit.jsonStr(s, "id");
                if (id != null) {
                    out.add(id);
                }
                // provides 里声明的等价 id
                int p = s == null ? -1 : s.indexOf("\"provides\"");
                if (p >= 0) {
                    int q1 = s.indexOf('[', p), q2 = s.indexOf(']', q1);
                    if (q1 > 0 && q2 > q1) {
                        for (String part : s.substring(q1 + 1, q2).split(",")) {
                            String v = part.replace("\"", "").trim();
                            if (!v.isEmpty()) {
                                out.add(v);
                            }
                        }
                    }
                }
            }
            ZipEntry t = z.getEntry("META-INF/mods.toml");
            if (t == null) {
                t = z.getEntry("META-INF/neoforge.mods.toml");
            }
            if (t != null) {
                String s = readEntry(z, t);
                if (s != null) {
                    for (String line : s.split("\n")) {
                        String l = line.trim();
                        if (l.startsWith("modId")) {
                            int q1 = l.indexOf('"'), q2 = l.lastIndexOf('"');
                            if (q1 >= 0 && q2 > q1) {
                                out.add(l.substring(q1 + 1, q2));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static List<String> readModDepends(File jar) {
        List<String> out = new ArrayList<>();
        try (ZipFile z = new ZipFile(jar)) {
            ZipEntry e = z.getEntry("fabric.mod.json");
            if (e != null) {
                String s = readEntry(z, e);
                int p = s == null ? -1 : s.indexOf("\"depends\"");
                if (p >= 0) {
                    int b1 = s.indexOf('{', p), b2 = s.indexOf('}', b1);
                    if (b1 > 0 && b2 > b1) {
                        for (String part : s.substring(b1 + 1, b2).split(",")) {
                            int c = part.indexOf(':');
                            if (c > 0) {
                                String k = part.substring(0, c).replace("\"", "").trim();
                                if (!k.isEmpty() && !"minecraft".equals(k)
                                        && !"java".equals(k) && !"fabricloader".equals(k)) {
                                    out.add(k);
                                }
                            }
                        }
                    }
                }
            }
            ZipEntry t = z.getEntry("META-INF/mods.toml");
            if (t == null) {
                t = z.getEntry("META-INF/neoforge.mods.toml");
            }
            if (t != null) {
                String s = readEntry(z, t);
                if (s != null) {
                    String cur = null;
                    for (String line : s.split("\n")) {
                        String l = line.trim();
                        if (l.startsWith("modId")) {
                            int q1 = l.indexOf('"'), q2 = l.lastIndexOf('"');
                            if (q1 >= 0 && q2 > q1) {
                                cur = l.substring(q1 + 1, q2);
                            }
                        } else if (l.startsWith("mandatory") && l.contains("true") && cur != null) {
                            if (!"minecraft".equals(cur) && !"forge".equals(cur)
                                    && !"neoforge".equals(cur) && !"java".equals(cur)) {
                                out.add(cur);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String readEntry(ZipFile z, ZipEntry e) {
        try (InputStream in = z.getInputStream(e)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            int total = 0;
            while ((r = in.read(buf)) > 0 && total < 512 * 1024) {
                bos.write(buf, 0, r);
                total += r;
            }
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 模组降级工具。
     *
     * <p>把 mods 目录里的某个模组**换成你手动放进来的**指定版本文件。
     * ★ 降级前**自动备份原文件**（同目录 .bak），且必须先二次确认。
     */
    public static void modDowngrade(MainActivity a) {
        try {
            File ver = currentVersion(a);
            if (ver == null) {
                PluginUiKit.info(a, "模组降级工具", "当前没有选中实例。");
                return;
            }
            File modsDir = new File(ver, "mods");
            List<File> mods = PluginUiKit.listByExt(modsDir, ".jar");
            if (mods.isEmpty()) {
                PluginUiKit.info(a, "模组降级工具", "这个实例的 mods 目录是空的。");
                return;
            }
            List<String> names = new ArrayList<>();
            for (File f : mods) {
                names.add(f.getName());
            }
            PluginUiKit.list(a, "要替换哪个模组？", names, idx -> doDowngrade(a, mods.get(idx)));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static void doDowngrade(MainActivity a, File oldJar) {
        // 找一个候选：同目录下文件名相近的其它 jar（用户自己放的新旧版本）
        List<File> cands = new ArrayList<>();
        String base = oldJar.getName().toLowerCase(Locale.US);
        String stem = base.replaceAll("\\.jar$", "").replaceAll("[-_ ]?\\d+(\\.\\d+)*.*$", "");
        for (File f : PluginUiKit.listByExt(oldJar.getParentFile(), ".jar")) {
            if (f.equals(oldJar)) {
                continue;
            }
            if (f.getName().toLowerCase(Locale.US).startsWith(stem)) {
                cands.add(f);
            }
        }

        LinearLayout col = PluginUiKit.column(a);
        col.addView(PluginUiKit.text(a, "要替换：" + oldJar.getName()));
        col.addView(PluginUiKit.kv(a, "当前大小", PluginUiKit.size(oldJar.length())));
        col.addView(PluginUiKit.sub(a, " "));
        if (cands.isEmpty()) {
            col.addView(PluginUiKit.sub(a,
                    "没有找到候选版本。\n\n★ 请先把想降到的那个 .jar 复制进 mods 目录，"
                            + "再回来点这个插件 —— 我会自动把它认出来。"));
            PluginUiKit.sheet(a, "模组降级工具", PluginUiKit.scroller(a, col), "知道了", null);
            return;
        }
        col.addView(PluginUiKit.label(a, "找到这些候选（同名前缀）"));
        for (File f : cands) {
            col.addView(PluginUiKit.sub(a, "• " + f.getName() + "   " + PluginUiKit.size(f.length())));
        }
        col.addView(PluginUiKit.sub(a, " "));
        col.addView(PluginUiKit.sub(a,
                "★ 替换前会把原文件备份成 " + oldJar.getName() + ".bak（同目录）。"));
        col.addView(PluginUiKit.sub(a,
                "★ 「自动匹配兼容版本」做不到 —— 那需要联网查 Modrinth/CurseForge 的版本列表。"));

        PluginUiKit.sheet(a, "确认降级", PluginUiKit.scroller(a, col), "替换", () -> {
            try {
                File pick = cands.get(0);
                File bak = new File(oldJar.getParentFile(), oldJar.getName() + ".bak");
                if (!bak.exists()) {
                    copy(oldJar, bak);
                }
                // 把原文件换掉：删除原文件、把候选改名成原文件名
                File target = new File(oldJar.getParentFile(), oldJar.getName());
                if (target.delete() || !target.exists()) {
                    copy(pick, target);
                    pick.delete();
                    PluginUiKit.toast(a, "已降级。原文件备份为 " + bak.getName());
                } else {
                    PluginUiKit.toast(a, "删除原文件失败，未做改动");
                }
            } catch (Throwable t) {
                PluginUiKit.toast(a, "降级失败：" + t);
            }
        });
    }

    /** 更新日志聚合：把 mods 里各 jar 自带的 CHANGELOG 读出来汇总。 */
    public static void changelogAggregate(MainActivity a) {
        try {
            File ver = currentVersion(a);
            if (ver == null) {
                PluginUiKit.info(a, "更新日志聚合", "当前没有选中实例。");
                return;
            }
            List<File> mods = PluginUiKit.listByExt(new File(ver, "mods"), ".jar");
            if (mods.isEmpty()) {
                PluginUiKit.info(a, "更新日志聚合", "这个实例的 mods 目录是空的。");
                return;
            }

            LinearLayout col = PluginUiKit.column(a);
            int found = 0;
            for (File m : mods) {
                String log = findChangelog(m);
                if (log == null || log.trim().isEmpty()) {
                    continue;
                }
                found++;
                LinearLayout card = PluginUiKit.card(a);
                card.addView(PluginUiKit.text(a, m.getName()));
                String show = log.length() > 1500 ? log.substring(0, 1500) + "\n…（截断）" : log;
                card.addView(PluginUiKit.sub(a, show));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = PluginUiKit.dp(a, 8);
                card.setLayoutParams(lp);
                col.addView(card);
            }

            if (found == 0) {
                col.addView(PluginUiKit.text(a, "没找到任何更新日志。"));
                col.addView(PluginUiKit.sub(a, "★ 很多模组**不打包** CHANGELOG —— "
                        + "它们把更新日志放在 Modrinth/CurseForge 页面上。"
                        + "本项目不联网抓内容，所以只能读到 jar 里自带的那些。"));
            } else {
                col.addView(PluginUiKit.sub(a, "★ 共 " + found + " 个模组自带更新日志"
                        + "（其余模组把日志放在网站上，本地读不到）。"));
            }
            PluginUiKit.sheet(a, "更新日志聚合", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static String findChangelog(File jar) {
        String[] names = {"CHANGELOG.md", "CHANGELOG.txt", "changelog.md", "changelog.txt",
                "CHANGES.md", "CHANGES.txt", "META-INF/CHANGELOG.md"};
        try (ZipFile z = new ZipFile(jar)) {
            for (String n : names) {
                ZipEntry e = z.getEntry(n);
                if (e != null) {
                    return readEntry(z, e);
                }
            }
            // 大小写不敏感兜底
            Enumeration<? extends ZipEntry> es = z.entries();
            while (es.hasMoreElements()) {
                ZipEntry e = es.nextElement();
                String ln = e.getName().toLowerCase(Locale.US);
                if (ln.endsWith("changelog.md") || ln.endsWith("changelog.txt")) {
                    return readEntry(z, e);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ==================================================================
    // 资源 / 光影
    // ==================================================================

    /**
     * 资源包预览图：直接列出资源包里的贴图。
     *
     * <p>★ 只显示 **PNG 贴图**，不做 3D 模型预览 —— 那需要实现 MC 的
     * 模型 JSON + 骨骼 + 光照，工作量相当于重写半个渲染器。
     */
    public static void resourcePackPreview(MainActivity a) {
        try {
            File ver = currentVersion(a);
            if (ver == null) {
                PluginUiKit.info(a, "资源包预览图", "当前没有选中实例。");
                return;
            }
            List<File> packs = PluginUiKit.listByExt(new File(ver, "resourcepacks"), ".zip");
            if (packs.isEmpty()) {
                PluginUiKit.info(a, "资源包预览图", "resourcepacks 目录里没有 .zip 资源包。");
                return;
            }
            List<String> names = new ArrayList<>();
            for (File f : packs) {
                names.add(f.getName());
            }
            PluginUiKit.list(a, "看哪个资源包？", names, idx -> showPackTextures(a, packs.get(idx)));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static void showPackTextures(MainActivity a, File pack) {
        try {
            List<String> pngs = new ArrayList<>();
            try (ZipFile z = new ZipFile(pack)) {
                Enumeration<? extends ZipEntry> es = z.entries();
                int guard = 0;
                while (es.hasMoreElements() && guard++ < 4000) {
                    ZipEntry e = es.nextElement();
                    String n = e.getName().toLowerCase(Locale.US);
                    if (n.endsWith(".png") && !n.contains("_n.png") && !n.contains("_s.png")) {
                        pngs.add(e.getName());
                    }
                }
            }
            if (pngs.isEmpty()) {
                PluginUiKit.info(a, "资源包预览图", "这个包里没有 PNG 贴图。");
                return;
            }
            Collections.sort(pngs);

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, pack.getName()));
            col.addView(PluginUiKit.sub(a, "共 " + pngs.size() + " 张贴图，显示前 24 张"));
            col.addView(PluginUiKit.sub(a, " "));

            // 优先展示 block / item / gui 目录的，那才是用户最想看的
            List<String> prefer = new ArrayList<>();
            for (String p : pngs) {
                String l = p.toLowerCase(Locale.US);
                if (l.contains("/block/") || l.contains("/item/") || l.contains("/gui/")) {
                    prefer.add(p);
                }
            }
            List<String> show = prefer.isEmpty() ? pngs : prefer;

            LinearLayout grid = PluginUiKit.column(a);
            grid.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout rowBox = null;
            int count = 0;
            try (ZipFile z = new ZipFile(pack)) {
                for (String p : show) {
                    if (count >= 24) {
                        break;
                    }
                    ZipEntry e = z.getEntry(p);
                    if (e == null) {
                        continue;
                    }
                    Bitmap bmp = null;
                    try (InputStream in = z.getInputStream(e)) {
                        bmp = BitmapFactory.decodeStream(in);
                    } catch (Throwable ignored) {
                    }
                    if (bmp == null) {
                        continue;
                    }
                    ImageView iv = new ImageView(a);
                    int s = PluginUiKit.dp(a, 48);
                    iv.setLayoutParams(new LinearLayout.LayoutParams(s, s));
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    iv.setImageBitmap(bmp);

                    if (count % 4 == 0) {
                        rowBox = PluginUiKit.column(a);
                        rowBox.setOrientation(LinearLayout.HORIZONTAL);
                        grid.addView(rowBox);
                    }
                    rowBox.addView(iv);
                    count++;
                }
            }
            col.addView(grid);
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 只显示**贴图原图**。"
                    + "「不用进游戏就能看方块/物品长什么样」需要渲染 3D 模型，"
                    + "那要重写 MC 的模型系统，本项目不做。"));
            PluginUiKit.sheet(a, "资源包预览图", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "读取失败：" + t);
        }
    }

    /** 光影兼容检测：读光影包的 GLSL 版本 + 目录结构，给出本地规则判断。 */
    public static void shaderCompat(MainActivity a) {
        try {
            File ver = currentVersion(a);
            if (ver == null) {
                PluginUiKit.info(a, "光影兼容检测", "当前没有选中实例。");
                return;
            }
            File dir = new File(ver, "shaderpacks");
            List<File> packs = new ArrayList<>();
            packs.addAll(PluginUiKit.listByExt(dir, ".zip"));
            for (File f : PluginUiKit.listFiles(dir)) {
                if (f.isDirectory()) {
                    packs.add(f);
                }
            }
            if (packs.isEmpty()) {
                PluginUiKit.info(a, "光影兼容检测", "shaderpacks 目录里没有光影包。");
                return;
            }

            String instance = ver.getName();
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "实例：" + instance));

            for (File p : packs) {
                List<String> glsl = new ArrayList<>();
                boolean hasShaderDir = false;
                boolean hasIris = false;
                try {
                    if (p.isFile()) {
                        try (ZipFile z = new ZipFile(p)) {
                            Enumeration<? extends ZipEntry> es = z.entries();
                            int g = 0;
                            while (es.hasMoreElements() && g++ < 2000) {
                                ZipEntry e = es.nextElement();
                                String n = e.getName().toLowerCase(Locale.US);
                                if (n.startsWith("shaders/")) {
                                    hasShaderDir = true;
                                }
                                if (n.contains("iris") || n.contains("optifine")) {
                                    hasIris = true;
                                }
                                if (n.endsWith(".fsh") || n.endsWith(".vsh") || n.endsWith(".glsl")) {
                                    String s = readEntry(z, e);
                                    if (s != null) {
                                        for (String line : s.split("\n")) {
                                            if (line.contains("#version")) {
                                                String v = line.trim();
                                                if (!glsl.contains(v)) {
                                                    glsl.add(v);
                                                }
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        hasShaderDir = new File(p, "shaders").isDirectory();
                    }
                } catch (Throwable ignored) {
                }

                LinearLayout card = PluginUiKit.card(a);
                card.addView(PluginUiKit.text(a, p.getName()));
                card.addView(PluginUiKit.kv(a, "有 shaders/ 目录", hasShaderDir ? "是" : "否"));
                card.addView(PluginUiKit.kv(a, "GLSL 版本", glsl.isEmpty() ? "未读到" : String.join(" / ", glsl)));
                card.addView(PluginUiKit.kv(a, "自带 Iris/OptiFine 提示", hasIris ? "是" : "否"));

                if (!hasShaderDir) {
                    card.addView(PluginUiKit.sub(a, "⚠ 没有 shaders/ 目录 —— 多半不是光影包，"
                            + "或者压缩包里多套了一层目录（解压时要注意）。"));
                } else if (glsl.isEmpty()) {
                    card.addView(PluginUiKit.sub(a, "⚠ 没读到 #version —— 可能是二进制/加密着色器，"
                            + "或者入口文件命名不常规。"));
                } else {
                    boolean modern = false;
                    for (String g : glsl) {
                        if (g.contains("330") || g.contains("400") || g.contains("410")
                                || g.contains("430") || g.contains("460")) {
                            modern = true;
                        }
                    }
                    card.addView(PluginUiKit.sub(a, modern
                            ? "GLSL 用了较新的版本 —— 需要较新的渲染器（Iris + 较新驱动）。"
                            : "GLSL 版本较老 —— 兼容性通常更好。"));
                }

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = PluginUiKit.dp(a, 8);
                card.setLayoutParams(lp);
                col.addView(card);
            }

            col.addView(PluginUiKit.sub(a, "★ 判断依据是**光影包自己的文件**"
                    + "（有没有 shaders/、GLSL 版本号），不是「这个光影好不好」。"));
            col.addView(PluginUiKit.sub(a, "★ **不显示预览图** —— 光影是 GLSL 着色器，"
                    + "渲染它需要 GL 上下文 + 游戏场景，启动器里没有。"
                    + "如果光影包自带截图，可以直接去 shaderpacks 目录看图。"));
            PluginUiKit.sheet(a, "光影兼容检测", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /**
     * 批量重命名工具。
     *
     * <p>规则：替换 / 加前缀 / 加后缀 / 编号。**先预览再执行** —— 重命名不可撤销。
     */
    public static void batchRename(MainActivity a) {
        try {
            List<String> targets = new ArrayList<>();
            targets.add("模组（mods）");
            targets.add("资源包（resourcepacks）");
            targets.add("光影（shaderpacks）");
            targets.add("存档（saves）");
            PluginUiKit.list(a, "重命名哪一类？", targets, idx -> {
                File ver = currentVersion(a);
                if (ver == null) {
                    PluginUiKit.info(a, "批量重命名", "当前没有选中实例。");
                    return;
                }
                String sub = new String[]{"mods", "resourcepacks", "shaderpacks", "saves"}[idx];
                renameFlow(a, new File(ver, sub), sub);
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static void renameFlow(MainActivity a, File dir, String kind) {
        List<File> files = PluginUiKit.listFiles(dir);
        if (files.isEmpty()) {
            PluginUiKit.info(a, "批量重命名", kind + " 目录是空的。");
            return;
        }
        EditText from = new EditText(a);
        from.setHint("查找（留空 = 不替换）");
        EditText to = new EditText(a);
        to.setHint("替换为");
        EditText prefix = new EditText(a);
        prefix.setHint("加前缀");
        EditText suffix = new EditText(a);
        suffix.setHint("加后缀（加在扩展名前）");

        LinearLayout col = PluginUiKit.column(a);
        col.addView(PluginUiKit.text(a, kind + " 目录：" + files.size() + " 项"));
        col.addView(PluginUiKit.sub(a, " "));
        col.addView(PluginUiKit.sub(a, "查找 / 替换"));
        col.addView(from);
        col.addView(to);
        col.addView(PluginUiKit.sub(a, " "));
        col.addView(PluginUiKit.sub(a, "前后缀"));
        col.addView(prefix);
        col.addView(suffix);
        col.addView(PluginUiKit.sub(a, " "));
        col.addView(PluginUiKit.sub(a, "★ 点「预览」先看会变成什么，确认了再执行。"
                + "★ 重命名不可撤销，建议先备份。"));
        PluginUiKit.sheet(a, "批量重命名", PluginUiKit.scroller(a, col), "预览", () -> {
            String f = from.getText() == null ? "" : from.getText().toString();
            String t = to.getText() == null ? "" : to.getText().toString();
            String p = prefix.getText() == null ? "" : prefix.getText().toString();
            String s = suffix.getText() == null ? "" : suffix.getText().toString();
            showRenamePreview(a, files, f, t, p, s);
        });
    }

    private static String renameOf(String name, String from, String to, String prefix, String suffix) {
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        if (from != null && !from.isEmpty()) {
            base = base.replace(from, to == null ? "" : to);
        }
        return (prefix == null ? "" : prefix) + base + (suffix == null ? "" : suffix) + ext;
    }

    private static void showRenamePreview(MainActivity a, List<File> files,
                                          String from, String to, String prefix, String suffix) {
        LinearLayout col = PluginUiKit.column(a);
        List<String[]> plan = new ArrayList<>();
        for (File f : files) {
            String n = renameOf(f.getName(), from, to, prefix, suffix);
            if (!n.equals(f.getName())) {
                plan.add(new String[]{f.getName(), n});
            }
        }
        col.addView(PluginUiKit.text(a, "会改 " + plan.size() + " 项（共 " + files.size() + " 项）"));
        col.addView(PluginUiKit.sub(a, " "));
        for (int i = 0; i < Math.min(plan.size(), 30); i++) {
            col.addView(PluginUiKit.sub(a, plan.get(i)[0]));
            col.addView(PluginUiKit.sub(a, "   → " + plan.get(i)[1]));
        }
        if (plan.isEmpty()) {
            col.addView(PluginUiKit.sub(a, "按当前规则，没有任何文件名会变。"));
        }
        PluginUiKit.sheet(a, "预览", PluginUiKit.scroller(a, col), "执行", () -> {
            int ok = 0, fail = 0;
            for (String[] pair : plan) {
                try {
                    File src = new File(files.get(0).getParentFile(), pair[0]);
                    File dst = new File(files.get(0).getParentFile(), pair[1]);
                    if (dst.exists()) {
                        fail++;
                        continue;
                    }
                    if (src.renameTo(dst)) {
                        ok++;
                    } else {
                        fail++;
                    }
                } catch (Throwable t) {
                    fail++;
                }
            }
            PluginUiKit.toast(a, "改名成功 " + ok + " 项" + (fail > 0 ? "，失败 " + fail + " 项" : ""));
        });
    }

    /** 性能基准：把 StatsTracker 的数据折成可对比的指标。 */
    public static void perfBenchmark(MainActivity a) {
        try {
            StatsTracker.Stats st = StatsTracker.get(a);
            LinearLayout col = PluginUiKit.column(a);

            LinearLayout head = PluginUiKit.card(a);
            head.addView(PluginUiKit.text(a, "启动器性能基准"));
            head.addView(PluginUiKit.sub(a, "★ 只统计**启动器自己能量到的**数据。"));
            col.addView(head);

            LinearLayout m = PluginUiKit.card(a);
            m.addView(PluginUiKit.label(a, "累计指标"));
            m.addView(PluginUiKit.kv(a, "启动次数", st.launchCount + " 次"));
            m.addView(PluginUiKit.kv(a, "总游戏时长", StatsTracker.formatDuration(st.totalPlayMs)));
            if (st.launchCount > 0) {
                m.addView(PluginUiKit.kv(a, "平均每次时长",
                        StatsTracker.formatDuration(st.totalPlayMs / Math.max(1, st.launchCount))));
            }
            m.addView(PluginUiKit.kv(a, "Java 崩溃次数", st.crashCount + " 次"));
            if (st.launchCount > 0) {
                m.addView(PluginUiKit.kv(a, "崩溃率",
                        String.format(Locale.US, "%.1f%%", st.crashCount * 100.0 / st.launchCount)));
            }
            col.addView(m);

            if (st.instanceLaunches != null && !st.instanceLaunches.isEmpty()) {
                LinearLayout per = PluginUiKit.card(a);
                per.addView(PluginUiKit.label(a, "各实例（用于横向对比）"));
                List<Map.Entry<String, Integer>> es = new ArrayList<>(st.instanceLaunches.entrySet());
                Collections.sort(es, (x, y) -> y.getValue() - x.getValue());
                for (Map.Entry<String, Integer> e : es) {
                    per.addView(PluginUiKit.kv(a, e.getKey(), e.getValue() + " 次"));
                }
                col.addView(per);
            }

            col.addView(PluginUiKit.sub(a, "★ **没有帧率** —— QCL 全仓没有 FPS 数据源，"
                    + "启动器拿不到游戏渲染帧率。想要帧率得在游戏里装性能模组（如 Sodium）。"));
            col.addView(PluginUiKit.sub(a, "★ **没有启动耗时分解** —— "
                    + "现有统计只记「开始 / 结束」，没有分阶段计时。"));
            PluginUiKit.sheet(a, "性能基准", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // 共用
    // ==================================================================

    private static File currentVersion(MainActivity a) {
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

    private static void copy(File src, File dst) throws Exception {
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
        }
    }
}
