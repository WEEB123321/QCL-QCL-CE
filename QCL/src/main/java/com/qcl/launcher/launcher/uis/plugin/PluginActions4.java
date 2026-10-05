package com.qcl.launcher.launcher.uis.plugin;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.game.World;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * ★★★ 社区版：插件实现（第四批，最后一批）。
 *
 * <p>约定同前三批。本批里有几项只能做「能做的那一半」，界面里都写明了原因。
 */
public final class PluginActions4 {

    private PluginActions4() {
    }

    // ==================================================================
    // 实例管理
    // ==================================================================

    /**
     * 实例导入向导：从别的启动器目录导入实例。
     *
     * <p>识别这几种常见布局：
     * <ul>
     *   <li><b>HMCL / PCL2 / 官方启动器</b>：{@code .minecraft/versions/<名>/}</li>
     *   <li><b>MultiMC / Prism</b>：{@code instances/<名>/.minecraft/} 或 {@code instances/<名>/minecraft/}</li>
     * </ul>
     */
    public static void instanceImport(MainActivity a) {
        try {
            EditText path = new EditText(a);
            path.setHint("填对方启动器的根目录，例如 /sdcard/HMCL 或 /sdcard/PCL");
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "实例导入向导"));
            col.addView(PluginUiKit.sub(a, "从 HMCL / PCL2 / 官方启动器 / MultiMC 的目录导入实例。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(path);
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 只**复制**版本目录（含 mods / config / saves），"
                    + "不动对方的文件。"));
            col.addView(PluginUiKit.sub(a, "★ 「自动识别账号」做不到 —— 账号数据是加密的，"
                    + "跨启动器搬不过来，需要重新登录。"));

            PluginUiKit.sheet(a, "实例导入向导", PluginUiKit.scroller(a, col), "扫描", () ->
                    scanOtherLauncher(a, path.getText() == null ? "" : path.getText().toString().trim()));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static void scanOtherLauncher(MainActivity a, String rootPath) {
        try {
            if (rootPath.isEmpty()) {
                PluginUiKit.toast(a, "先填目录");
                return;
            }
            File root = new File(rootPath);
            if (!root.isDirectory()) {
                PluginUiKit.toast(a, "目录不存在：" + rootPath);
                return;
            }

            List<File> found = new ArrayList<>();
            // 布局 1：<root>/versions/<名>
            File v = new File(root, "versions");
            if (v.isDirectory()) {
                for (File f : PluginUiKit.listFiles(v)) {
                    if (f.isDirectory()) {
                        found.add(f);
                    }
                }
            }
            // 布局 1b：<root>/.minecraft/versions/<名>
            File v2 = new File(root, ".minecraft/versions");
            if (v2.isDirectory()) {
                for (File f : PluginUiKit.listFiles(v2)) {
                    if (f.isDirectory()) {
                        found.add(f);
                    }
                }
            }
            // 布局 2：<root>/instances/<名>[/.minecraft|/minecraft]
            File inst = new File(root, "instances");
            if (inst.isDirectory()) {
                for (File f : PluginUiKit.listFiles(inst)) {
                    if (!f.isDirectory()) {
                        continue;
                    }
                    File inner = new File(f, ".minecraft");
                    if (!inner.isDirectory()) {
                        inner = new File(f, "minecraft");
                    }
                    File innerVersions = new File(inner, "versions");
                    if (innerVersions.isDirectory()) {
                        for (File g : PluginUiKit.listFiles(innerVersions)) {
                            if (g.isDirectory()) {
                                found.add(g);
                            }
                        }
                    } else if (inner.isDirectory()) {
                        // MultiMC 的实例本身就是 .minecraft
                        found.add(inner);
                    }
                }
            }

            if (found.isEmpty()) {
                PluginUiKit.info(a, "实例导入向导",
                        "在这个目录下没找到可导入的实例。\n\n"
                                + "试过的布局：\n"
                                + "• versions/<实例名>/\n"
                                + "• .minecraft/versions/<实例名>/\n"
                                + "• instances/<实例名>/[.minecraft|minecraft]/\n\n"
                                + "如果对方启动器把实例放在别处，请填它的**游戏目录**而不是安装目录。");
                return;
            }

            List<String> names = new ArrayList<>();
            for (File f : found) {
                int mods = PluginUiKit.listByExt(new File(f, "mods"), ".jar").size();
                names.add(f.getName() + "   (" + mods + " 模组)");
            }
            PluginUiKit.list(a, "找到 " + found.size() + " 个实例，导入哪个？", names,
                    idx -> copyInstance(a, found.get(idx)));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "扫描失败：" + t);
        }
    }

    private static void copyInstance(MainActivity a, File src) {
        try {
            File dstRoot = new File(gameDir(a), "versions");
            File dst = new File(dstRoot, src.getName());
            if (dst.exists()) {
                PluginUiKit.confirm(a, "目标已存在", "versions/" + src.getName()
                        + " 已经存在。继续会**覆盖**它，确定吗？", () -> doCopy(a, src, dst));
            } else {
                doCopy(a, src, dst);
            }
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static void doCopy(MainActivity a, File src, File dst) {
        try {
            copyTree(src, dst, 0);
            log(a, "导入实例 " + src.getName());
            PluginUiKit.toast(a, "已导入 " + dst.getName() + "（重新进版本列表可见）");
        } catch (Throwable t) {
            PluginUiKit.toast(a, "复制失败：" + t);
        }
    }

    private static void copyTree(File src, File dst, int depth) throws Exception {
        if (depth > 8) {
            return;
        }
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                throw new Exception("建目录失败 " + dst);
            }
            File[] kids = src.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    copyTree(k, new File(dst, k.getName()), depth + 1);
                }
            }
        } else if (src.isFile()) {
            File p = dst.getParentFile();
            if (p != null && !p.exists()) {
                p.mkdirs();
            }
            try (FileInputStream in = new FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                }
            }
        }
    }

    /** 实例启动画面：本地开关 + 样式选择。 */
    public static void launchSplash(MainActivity a) {
        try {
            boolean on = Local.bool(a, "splash_on", false);
            String style = Local.str(a, "splash_style", "纯色");

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "实例启动画面"));
            col.addView(PluginUiKit.sub(a, "启动游戏时盖一层自定义画面，不用一直盯着黑屏。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.kv(a, "当前状态", on ? "开启" : "关闭"));
            col.addView(PluginUiKit.kv(a, "样式", style));
            col.addView(PluginUiKit.sub(a, " "));

            TextView toggle = PluginUiKit.row(a, on ? "关掉启动画面" : "开启启动画面", null);
            toggle.setGravity(android.view.Gravity.CENTER);
            toggle.setOnClickListener(v -> {
                Local.put(a, "splash_on", !on);
                PluginUiKit.toast(a, !on ? "已开启" : "已关闭");
            });
            col.addView(toggle);

            for (String s : new String[]{"纯色", "进度条", "纯色 + 进度条"}) {
                TextView row = PluginUiKit.row(a, "样式：" + s, null);
                row.setOnClickListener(v -> {
                    Local.put(a, "splash_style", s);
                    PluginUiKit.toast(a, "样式已设为 " + s);
                });
                col.addView(row);
            }

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "⚠ **GIF / 视频启动动画没做** —— "
                    + "启动画面是盖在启动流程上的，要在里面解 GIF / 视频需要额外带解码器，"
                    + "而且会拖慢启动。纯色和进度条是零开销的。"));
            col.addView(PluginUiKit.sub(a, "★ 设置存在本地，不上传。"));
            PluginUiKit.sheet(a, "实例启动画面", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** 启动队列：把实例排个顺序，依次启动。 */
    public static void launchQueue(MainActivity a) {
        try {
            List<String> queue = Local.lines(a, "launch_queue");
            List<File> versions = versionDirs(a);
            if (versions.isEmpty()) {
                PluginUiKit.info(a, "启动队列", "还没有任何实例。");
                return;
            }
            List<String> names = new ArrayList<>();
            for (File f : versions) {
                names.add((queue.contains(f.getName()) ? "✓ " : "　 ") + f.getName());
            }
            PluginUiKit.list(a, "点一下加入 / 移出队列", names, idx -> {
                String n = versions.get(idx).getName();
                List<String> q = new ArrayList<>(Local.lines(a, "launch_queue"));
                if (q.contains(n)) {
                    q.remove(n);
                    PluginUiKit.toast(a, "已移出队列");
                } else {
                    q.add(n);
                    PluginUiKit.toast(a, "已加入队列（第 " + (q.size()) + " 位）");
                }
                Local.saveLines(a, "launch_queue", q);
                launchQueue(a);
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // 模组 / 资源
    // ==================================================================

    /** 模组更新预检：本地规则表比对。 */
    public static void modUpdateCheck(MainActivity a) {
        try {
            File ver = current(a);
            if (ver == null) {
                PluginUiKit.info(a, "模组更新预检", "当前没有选中实例。");
                return;
            }
            List<File> mods = PluginUiKit.listByExt(new File(ver, "mods"), ".jar");
            List<String> locked = PluginActions.ModLock.load(a, ver.getName());

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, mods.size() + " 个模组"));
            col.addView(PluginUiKit.sub(a, " "));

            LinearLayout card = PluginUiKit.card(a);
            card.addView(PluginUiKit.label(a, "本地预检"));
            card.addView(PluginUiKit.kv(a, "已锁定（更新时跳过）", locked.size() + " 个"));
            int dup = findDuplicateIds(mods);
            card.addView(PluginUiKit.kv(a, "疑似重复（同名前缀）", dup + " 个"));
            col.addView(card);

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "⚠ **「有没有新版本」查不了** —— "
                    + "那需要联网问 Modrinth / CurseForge 的 API。"
                    + "本项目不联网抓内容。"));
            col.addView(PluginUiKit.sub(a, "★ 能做的：找出**本地就能看出来的问题**"
                    + "（重复安装、已锁定），以及提示你先去「模组依赖树」看前置是否齐全。"));
            PluginUiKit.sheet(a, "模组更新预检", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static int findDuplicateIds(List<File> mods) {
        java.util.Map<String, Integer> stem = new java.util.HashMap<>();
        int dup = 0;
        for (File f : mods) {
            String s = f.getName().toLowerCase(Locale.US)
                    .replaceAll("\\.jar$", "")
                    .replaceAll("[-_ ]?\\d+(\\.\\d+)*.*$", "");
            int n = stem.getOrDefault(s, 0) + 1;
            stem.put(s, n);
            if (n == 2) {
                dup++;
            }
        }
        return dup;
    }

    /** 光影预设管理：一键切换 shaderpacks 里用哪个。 */
    public static void shaderPreset(MainActivity a) {
        try {
            File ver = current(a);
            if (ver == null) {
                PluginUiKit.info(a, "光影预设管理", "当前没有选中实例。");
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
                PluginUiKit.info(a, "光影预设管理", "shaderpacks 目录里没有光影包。");
                return;
            }
            String currentName = Local.str(a, "shader_current", "（未设置）");

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.kv(a, "当前标记使用", currentName));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 这里只是**记下「你用哪个光影」**并做备注。"
                    + "真正生效要在**游戏内**（视频设置 → 光影）选 —— "
                    + "光影的启用状态存在游戏自己的配置里，启动器改不了。"));
            col.addView(PluginUiKit.sub(a, " "));
            for (File p : packs) {
                col.addView(PluginUiKit.sub(a, "• " + p.getName()));
            }
            PluginUiKit.sheet(a, "光影预设管理", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // Java
    // ==================================================================

    /** Java 自动匹配：比对实例版本 json 需要的 Java 与已安装的运行时。 */
    public static void javaMatch(MainActivity a) {
        try {
            File ver = current(a);
            if (ver == null) {
                PluginUiKit.info(a, "Java 自动匹配", "当前没有选中实例。");
                return;
            }
            File json = new File(ver, ver.getName() + ".json");
            String txt = PluginUiKit.readText(json);

            String need = "8";
            try {
                if (txt != null) {
                    int i = txt.indexOf("\"javaVersion\"");
                    if (i > 0) {
                        int c = txt.indexOf("\"majorVersion\"", i);
                        if (c > 0) {
                            int s = txt.indexOf(':', c) + 1;
                            int e = txt.indexOf(',', s);
                            if (e < 0) {
                                e = txt.indexOf('}', s);
                            }
                            need = txt.substring(s, e).replaceAll("[^0-9]", "").trim();
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            File javaDir = new File(AppManifest.JAVA_DIR);
            List<String> installed = new ArrayList<>();
            for (File f : PluginUiKit.listFiles(javaDir)) {
                if (f.isDirectory()) {
                    installed.add(f.getName());
                }
            }

            boolean has = false;
            for (String s : installed) {
                if (s.contains(need)) {
                    has = true;
                }
            }

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "实例：" + ver.getName()));
            col.addView(PluginUiKit.kv(a, "这个版本需要 Java", need));
            col.addView(PluginUiKit.kv(a, "已安装运行时", installed.isEmpty() ? "无" : String.join(", ", installed)));
            col.addView(PluginUiKit.kv(a, "匹配结果", has ? "✔ 有对应的" : "✗ 缺 Java " + need));
            col.addView(PluginUiKit.sub(a, " "));
            if (!has) {
                col.addView(PluginUiKit.sub(a, "→ 去「安装运行环境」页补装 Java " + need
                        + "（首次装运行环境时应该已经全带上了）。"));
            }
            col.addView(PluginUiKit.sub(a, "★ 判断依据是**版本 json 里的 javaVersion 字段**"
                    + "（Mojang 自己声明的），不是猜的。"));
            col.addView(PluginUiKit.sub(a, "★ 「自动切换 Java」在启动时本来就会按这个字段选 —— "
                    + "这里只是把它**显示出来**，让你能自己核对。"));
            PluginUiKit.sheet(a, "Java 自动匹配", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** Java 参数预设：把常用 JVM 参数存成预设。 */
    public static void javaArgsPreset(MainActivity a) {
        try {
            final String[][] presets = {
                    {"低配（省内存）", "-Xmx512M -Xms256M -XX:+UseSerialGC"},
                    {"流畅（推荐）", "-Xmx1024M -Xms512M -XX:+UseG1GC -XX:MaxGCPauseMillis=50"},
                    {"大内存", "-Xmx2048M -Xms1024M -XX:+UseG1GC"},
                    {"调试（打印 GC）", "-Xmx1024M -XX:+UseG1GC -Xlog:gc"},
            };
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "Java 参数预设"));
            col.addView(PluginUiKit.sub(a, "把常用 JVM 参数存成一行，省得每次手打。"));
            col.addView(PluginUiKit.sub(a, " "));
            for (String[] p : presets) {
                LinearLayout card = PluginUiKit.card(a);
                card.addView(PluginUiKit.text(a, p[0]));
                card.addView(PluginUiKit.sub(a, p[1]));
                card.setOnClickListener(v -> {
                    Local.put(a, "jvm_args", p[1]);
                    log(a, "应用 JVM 预设 " + p[0]);
                    PluginUiKit.toast(a, "已记住预设「" + p[0] + "」");
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = PluginUiKit.dp(a, 8);
                card.setLayoutParams(lp);
                col.addView(card);
            }
            col.addView(PluginUiKit.sub(a, "点一个预设 = 把它记到本地（"
                    + Local.str(a, "jvm_args", "（还没选）") + "）。"));
            col.addView(PluginUiKit.sub(a, "★ **不会自动写进游戏启动参数** —— "
                    + "JVM 参数在游戏设置页里，由你自己填。"
                    + "这里只是帮你记住「我用的是哪套」。"));
            PluginUiKit.sheet(a, "Java 参数预设", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // 存档
    // ==================================================================

    /**
     * 存档地图预览。
     *
     * <p>解析 {@code region/*.mca} 的**区块表**（Anvil 格式：8KB 位置表 + 4KB 时间戳），
     * 画出「哪些区块已经生成过」的网格图 —— 也就是**已探索范围**。
     *
     * <p>★ 不做地形渲染（高度图/生物群系）—— 那要解区块 NBT 再自己着色，
     * 工作量大且容易出错。已探索范围是最实用、也最不容易做错的信息。
     */
    public static void worldMapPreview(MainActivity a) {
        try {
            File saves = savesDir(a);
            if (saves == null || !saves.isDirectory()) {
                PluginUiKit.info(a, "存档地图预览", "找不到 saves 目录。");
                return;
            }
            List<World> worlds = World.getWorlds(saves.toPath())
                    .collect(java.util.stream.Collectors.toList());
            if (worlds.isEmpty()) {
                PluginUiKit.info(a, "存档地图预览", "还没有任何存档。");
                return;
            }
            List<String> names = new ArrayList<>();
            for (World w : worlds) {
                names.add(w.getWorldName());
            }
            PluginUiKit.list(a, "看哪个存档？", names, idx -> showWorldMap(a, worlds.get(idx)));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static void showWorldMap(MainActivity a, World w) {
        try {
            File regionDir = new File(w.getFile().toFile(), "region");
            List<File> regions = PluginUiKit.listByExt(regionDir, ".mca");
            if (regions.isEmpty()) {
                PluginUiKit.info(a, "存档地图预览", "这个存档还没有 region 数据。");
                return;
            }

            // 统计每个 region 里已生成的区块数（32x32 = 1024 个一格）
            int totalChunks = 0;
            int maxR = 1;
            java.util.Map<String, Integer> perRegion = new java.util.LinkedHashMap<>();
            for (File r : regions) {
                int n = countChunks(r);
                perRegion.put(r.getName(), n);
                totalChunks += n;
                String[] p = r.getName().replace(".mca", "").split("\\.");
                if (p.length == 3) {
                    try {
                        maxR = Math.max(maxR, Math.max(Math.abs(Integer.parseInt(p[1])),
                                Math.abs(Integer.parseInt(p[2]))) + 1);
                    } catch (Throwable ignored) {
                    }
                }
            }

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, w.getWorldName()));
            col.addView(PluginUiKit.kv(a, "region 文件", regions.size() + " 个"));
            col.addView(PluginUiKit.kv(a, "已生成区块", totalChunks + " 个"));
            col.addView(PluginUiKit.kv(a, "大致范围", "±" + maxR + " 个 region（每个 512×512 格）"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "各 region 已生成区块数"));

            for (java.util.Map.Entry<String, Integer> e : perRegion.entrySet()) {
                col.addView(PluginUiKit.kv(a, e.getKey(), e.getValue() + " / 1024"));
            }

            // 画一张「已探索范围」网格图
            int cell = PluginUiKit.dp(a, 4);
            int size = maxR * 2 * cell;
            if (size > 0 && size < 2000) {
                Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                Paint p = new Paint();
                p.setColor(0xFFE8E4DC);
                c.drawRect(0, 0, size, size, p);
                p.setColor(0xFFA94F2C);
                for (File r : regions) {
                    String[] ps = r.getName().replace(".mca", "").split("\\.");
                    if (ps.length != 3) {
                        continue;
                    }
                    try {
                        int rx = Integer.parseInt(ps[1]);
                        int rz = Integer.parseInt(ps[2]);
                        int x = (rx + maxR) * cell;
                        int y = (rz + maxR) * cell;
                        c.drawRect(x, y, x + cell, y + cell, p);
                    } catch (Throwable ignored) {
                    }
                }
                ImageView iv = new ImageView(a);
                iv.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageBitmap(bmp);
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.label(a, "已探索范围（每格 = 1 个 region）"));
                col.addView(iv);
            }

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 画的是**哪些区块已经生成过**（读 region 文件的区块表）。"));
            col.addView(PluginUiKit.sub(a, "★ **不画地形** —— 高度图/生物群系要解区块 NBT 再自己着色，"
                    + "工作量大且容易出错。想知道地形长什么样，进游戏看最准。"));
            PluginUiKit.sheet(a, "存档地图预览", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "读取失败：" + t);
        }
    }

    /** 读 Anvil region 文件的区块表，数出有多少个区块是「已生成」的。 */
    private static int countChunks(File region) {
        int n = 0;
        try (RandomAccessFile raf = new RandomAccessFile(region, "r")) {
            byte[] header = new byte[4096];
            if (raf.read(header) != 4096) {
                return 0;
            }
            // 前 4096 字节 = 1024 个 4 字节的位置项（3 字节偏移 + 1 字节扇区数）
            for (int i = 0; i < 1024; i++) {
                int off = i * 4;
                int sectorCount = header[off + 3] & 0xFF;
                if (sectorCount > 0) {
                    n++;
                }
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    /** 存档编辑警告：编辑前的检查 + 备份提醒。 */
    public static void worldEditGuard(MainActivity a) {
        try {
            File saves = savesDir(a);
            if (saves == null || !saves.isDirectory()) {
                PluginUiKit.info(a, "存档编辑警告", "找不到 saves 目录。");
                return;
            }
            List<World> worlds = World.getWorlds(saves.toPath())
                    .collect(java.util.stream.Collectors.toList());

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "存档编辑警告"));
            col.addView(PluginUiKit.sub(a, "共 " + worlds.size() + " 个存档。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "在动存档之前，先确认这几件事："));
            col.addView(PluginUiKit.sub(a, "① 已经在「我的 → 备份与恢复」里备份过"));
            col.addView(PluginUiKit.sub(a, "② 知道这个存档是哪个版本创建的（高版本打开会就地升级，不可逆）"));
            col.addView(PluginUiKit.sub(a, "③ 如果装了模组，删模组可能让存档里的方块变成「未知方块」"));
            col.addView(PluginUiKit.sub(a, " "));
            for (World w : worlds) {
                long size = PluginUiKit.dirSize(w.getFile().toFile(), 0);
                col.addView(PluginUiKit.kv(a, w.getWorldName(), PluginUiKit.size(size)));
            }
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 「编辑前自动弹警告」在**启动器自己的编辑入口**"
                    + "（重命名 / 删除）里已经生效。"
                    + "你在文件管理器里直接改存档，启动器拦不到。"));
            PluginUiKit.sheet(a, "存档编辑警告", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // 其他
    // ==================================================================

    /** 服务器白名单管理：编辑 whitelist / ops / banned 四个 json。 */
    public static void serverWhitelist(MainActivity a) {
        try {
            File dir = new File(AppManifest.LAUNCHER_DIR, "server");
            String[] files = {"whitelist.json", "ops.json",
                    "banned-players.json", "banned-ips.json"};

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "服务器白名单管理"));
            col.addView(PluginUiKit.sub(a, "目录：" + dir.getAbsolutePath()));
            col.addView(PluginUiKit.sub(a, " "));
            for (String f : files) {
                File file = new File(dir, f);
                String content = PluginUiKit.readText(file);
                col.addView(PluginUiKit.kv(a, f, content == null ? "（无此文件）"
                        : (content.trim().isEmpty() ? "空" : content.length() + " 字符")));
            }
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 这四个文件是**服务器**用的。"
                    + "先跑一次「一键开服向导」建目录，再用服务器生成它们，"
                    + "然后可以在这里查看内容。"));
            col.addView(PluginUiKit.sub(a, "★ 「图形化增删 + 一键同步到服务端」需要服务端配合"
                    + "（要连上去执行命令），本项目不做假按钮。"));
            PluginUiKit.sheet(a, "服务器白名单管理", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** 本地百科：内置一份原版常见方块 / 物品 / 生物的速查表。 */
    public static void wiki(MainActivity a) {
        try {
            final String[][] entries = {
                    {"钻石", "方块 / 物品", "用铁镐及以上挖钻石矿获得。主世界 y=-64~16 之间最多，越深越多。"},
                    {"下界合金", "物品", "下界合金锭 + 钻石装备在锻造台升级，是目前最强装备材质。不会被岩浆烧掉。"},
                    {"末影珍珠", "物品", "打末影人获得。可投掷传送；9 个 + 烈焰粉 = 末影之眼（找要塞用）。"},
                    {"烈焰棒", "物品", "打烈焰人获得。合成烈焰粉；也可作酿造台燃料。"},
                    {"信标", "方块", "下界之星 + 5 玻璃 + 3 黑曜石。底下要垫矿物块金字塔才生效。"},
                    {"附魔台", "方块", "4 黑曜石 + 2 钻石 + 1 书。周围放书架能提高等级上限。"},
                    {"村民交易", "机制", "图书管理员卖附魔书，是拿经验换好附魔最省事的途径。"},
                    {"刷怪塔", "建筑", "利用光照 / 高度差让怪物掉下来。效率取决于刷怪范围与击杀方式。"},
                    {"红石比较器", "红石", "可比较容器装满程度，也用于检测方块状态。"},
                    {"鞘翅", "物品", "末地船里获得。配合烟花可长距离飞行。"},
                    {"僵尸", "生物", "夜间 / 洞穴生成。白天会被晒着火烧。"},
                    {"苦力怕", "生物", "靠近会自爆。可用猫 / 豹猫驱赶。"},
                    {"末影人", "生物", "看你眼睛会被激怒。打它会瞬移，建议在 2 格高空间打。"},
                    {"烈焰人", "生物", "下界要塞刷怪笼生成。掉烈焰棒。"},
                    {"村民", "生物", "可用职业方块改变职业。被僵尸打死会变僵尸村民。"},
            };

            List<String> titles = new ArrayList<>();
            for (String[] e : entries) {
                titles.add(e[0] + "   (" + e[1] + ")");
            }
            PluginUiKit.list(a, "本地百科（原版，全离线）", titles, idx -> {
                LinearLayout col = PluginUiKit.column(a);
                col.addView(PluginUiKit.text(a, entries[idx][0]));
                col.addView(PluginUiKit.kv(a, "类别", entries[idx][1]));
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.sub(a, entries[idx][2]));
                PluginUiKit.sheet(a, entries[idx][0], PluginUiKit.scroller(a, col), "知道了", null);
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** 数据迁移向导：把启动器数据导出成一个 zip。 */
    public static void dataMigration(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "数据迁移向导"));
            col.addView(PluginUiKit.sub(a, "换手机时把**启动器自己的数据**打包带走。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "会打包：" + AppManifest.SETTING_DIR + " 下的所有 json"));
            col.addView(PluginUiKit.sub(a, "输出到：" + AppManifest.LAUNCHER_DIR + "/qcl_data_export.zip"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 打包的是**设置 / 统计 / 笔记 / 词库 / 保险箱**这类小文件。"
                    + "**不含**实例、存档、账号 —— 那些几百 MB，而且账号是加密的、搬过去也用不了。"));
            col.addView(PluginUiKit.sub(a, "★ 「局域网直传 / 二维码」本轮没做 —— "
                    + "要起本地 HTTP 服务 + 生成二维码，工作量不小，先做最实用的导出。"));
            PluginUiKit.sheet(a, "数据迁移向导", PluginUiKit.scroller(a, col), "导出", () -> {
                try {
                    File dir = new File(AppManifest.SETTING_DIR);
                    File out = new File(AppManifest.LAUNCHER_DIR, "qcl_data_export.zip");
                    int n = 0;
                    try (java.util.zip.ZipOutputStream zos =
                                 new java.util.zip.ZipOutputStream(new FileOutputStream(out))) {
                        for (File f : PluginUiKit.listFiles(dir)) {
                            if (!f.isFile()) {
                                continue;
                            }
                            zos.putNextEntry(new java.util.zip.ZipEntry(f.getName()));
                            try (FileInputStream in = new FileInputStream(f)) {
                                byte[] buf = new byte[16384];
                                int r;
                                while ((r = in.read(buf)) > 0) {
                                    zos.write(buf, 0, r);
                                }
                            }
                            zos.closeEntry();
                            n++;
                        }
                    }
                    log(a, "导出启动器数据（" + n + " 个文件）");
                    PluginUiKit.toast(a, "已导出 " + n + " 个文件 → " + out.getName());
                } catch (Throwable t) {
                    PluginUiKit.toast(a, "导出失败：" + t);
                }
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** 游戏规则预设：生成一个数据包，把规则写进去。 */
    public static void gamerulePreset(MainActivity a) {
        try {
            File saves = savesDir(a);
            if (saves == null || !saves.isDirectory()) {
                PluginUiKit.info(a, "游戏规则预设", "找不到 saves 目录。");
                return;
            }
            List<World> worlds = World.getWorlds(saves.toPath())
                    .collect(java.util.stream.Collectors.toList());
            if (worlds.isEmpty()) {
                PluginUiKit.info(a, "游戏规则预设", "还没有任何存档。");
                return;
            }

            final String[][] presets = {
                    {"生存（标准）", "keepInventory=false\nmobGriefing=true\ndoDaylightCycle=true\nnaturalRegeneration=true"},
                    {"创造（自由）", "keepInventory=true\ndoDaylightCycle=false\nmobGriefing=false\ndoMobSpawning=false"},
                    {"PVP 竞技", "keepInventory=true\npvp=true\ndoImmediateRespawn=true\nshowDeathMessages=true"},
                    {"红石（稳定）", "randomTickSpeed=3\ndoFireTick=false\ndoMobSpawning=false"},
                    {"建筑（无干扰）", "doMobSpawning=false\nmobGriefing=false\ndoWeatherCycle=false\ndoDaylightCycle=false"},
                    {"空岛（严苛）", "keepInventory=false\nnaturalRegeneration=false\ndoDaylightCycle=true\nmobGriefing=true"},
            };

            List<String> names = new ArrayList<>();
            for (String[] p : presets) {
                names.add(p[0]);
            }
            PluginUiKit.list(a, "选一个规则预设", names, pi ->
                    PluginUiKit.list(a, "写进哪个存档？",
                            worlds.stream().map(World::getWorldName)
                                    .collect(java.util.stream.Collectors.toList()),
                            wi -> writeGamerulePack(a, worlds.get(wi), presets[pi][0], presets[pi][1])));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static void writeGamerulePack(MainActivity a, World w, String preset, String rules) {
        try {
            File dpRoot = new File(w.getFile().toFile(), "datapacks/qcl_gamerules");
            File fnDir = new File(dpRoot, "data/qcl/function");
            if (!fnDir.exists() && !fnDir.mkdirs()) {
                PluginUiKit.toast(a, "建目录失败");
                return;
            }
            write(new File(dpRoot, "pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":15,\"description\":\"QCL 游戏规则预设："
                            + preset + "\"}}\n");

            StringBuilder sb = new StringBuilder();
            for (String line : rules.split("\n")) {
                String l = line.trim();
                int eq = l.indexOf('=');
                if (eq > 0) {
                    sb.append("gamerule ").append(l.substring(0, eq).trim())
                            .append(' ').append(l.substring(eq + 1).trim()).append('\n');
                }
            }
            sb.append("say [QCL] 已套用规则预设：").append(preset).append('\n');
            write(new File(fnDir, "apply.mcfunction"), sb.toString());
            write(new File(dpRoot, "data/minecraft/tags/function/load.json"),
                    "{\"values\":[\"qcl:apply\"]}\n");

            log(a, "生成规则数据包 → " + w.getWorldName());
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "已生成数据包"));
            col.addView(PluginUiKit.kv(a, "存档", w.getWorldName()));
            col.addView(PluginUiKit.kv(a, "预设", preset));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "进游戏后执行 /reload 即可套用；"
                    + "或者重新进一次世界（load 标签会自动跑）。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "⚠ **「新建世界时一键套用」做不到** —— "
                    + "新建世界是**游戏内部**的流程，启动器插不进去。"
                    + "所以这里做成**数据包**：进世界后自动/手动生效。"));
            PluginUiKit.sheet(a, "游戏规则预设", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // 共用
    // ==================================================================

    private static File gameDir(MainActivity a) {
        try {
            String gd = a.launcherSetting == null ? null : a.launcherSetting.gameFileDirectory;
            if (gd == null || gd.isEmpty()) {
                gd = AppManifest.DEFAULT_GAME_DIR;
            }
            return new File(gd);
        } catch (Throwable t) {
            return new File(AppManifest.DEFAULT_GAME_DIR);
        }
    }

    private static File savesDir(MainActivity a) {
        return new File(gameDir(a), "saves");
    }

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

    private static List<File> versionDirs(MainActivity a) {
        List<File> out = new ArrayList<>();
        for (File f : PluginUiKit.listFiles(new File(gameDir(a), "versions"))) {
            if (f.isDirectory()) {
                out.add(f);
            }
        }
        Collections.sort(out, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        return out;
    }

    private static void write(File f, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes("UTF-8"));
        }
    }

    private static void log(MainActivity a, String what) {
        try {
            List<String> ls = Local.lines(a, "action_log");
            ls.add(new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.US)
                    .format(new java.util.Date()) + "  " + what);
            Local.saveLines(a, "action_log", ls);
        } catch (Throwable ignored) {
        }
    }

    /** 本地存储（与 PluginActions3.Local 同一套格式，独立一份避免可见性问题）。 */
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

        static String str(android.content.Context c, String key, String def) {
            List<String> ls = lines(c, key);
            return ls.isEmpty() ? def : ls.get(0);
        }

        static boolean bool(android.content.Context c, String key, boolean def) {
            List<String> ls = lines(c, key);
            return ls.isEmpty() ? def : "1".equals(ls.get(0));
        }

        static void put(android.content.Context c, String key, Object v) {
            saveLines(c, key, Collections.singletonList(
                    v instanceof Boolean ? (((Boolean) v) ? "1" : "0") : String.valueOf(v)));
        }
    }
}
