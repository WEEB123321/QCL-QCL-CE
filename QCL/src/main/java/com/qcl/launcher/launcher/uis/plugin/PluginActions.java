package com.qcl.launcher.launcher.uis.plugin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.game.World;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.launcher.stats.StatsTracker;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ★★★ 社区版：插件的**具体实现**。
 *
 * <p>每个方法对应 {@link PluginRegistry} 里的一条。约定：
 * <ul>
 *   <li>界面一律走 {@link PluginUiKit}，不自己 new AlertDialog —— 观感才统一；</li>
 *   <li><b>所有危险操作（删 / 覆盖 / 重置）必须先 {@link PluginUiKit#confirm} 二次确认</b>；</li>
 *   <li>每个方法整体包 try/catch —— 一个插件坏掉不能把插件页带崩。</li>
 * </ul>
 */
public final class PluginActions {

    private PluginActions() {
    }

    // ==================================================================
    // 实例管理
    // ==================================================================

    /**
     * 实例图标生成器。
     *
     * <p>QCL 的实例图标取自 {@code <游戏目录>/versions/<实例名>/icon.png}
     * （见 SettingUtils：`bean.iconPath = path + "/versions/" + str + "/icon.png"`）。
     * 所以这里就是**生成一张 PNG 写进那个位置**。
     */
    public static void instanceIcon(MainActivity a) {
        try {
            List<File> versions = versionDirs(a);
            if (versions.isEmpty()) {
                PluginUiKit.info(a, "实例图标生成器", "还没有任何实例。");
                return;
            }
            List<String> names = new ArrayList<>();
            for (File f : versions) {
                names.add(f.getName());
            }
            PluginUiKit.list(a, "给哪个实例生成图标？", names, idx -> pickIconColor(a, versions.get(idx)));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "图标生成器失败：" + t);
        }
    }

    private static final int[] ICON_COLORS = {
            0xFFA94F2C, 0xFF0E9E8E, 0xFF3B6D11, 0xFF185FA5,
            0xFF534AB7, 0xFF993C1D, 0xFF5F5E5A, 0xFF993556,
    };
    private static final String[] ICON_COLOR_NAMES = {
            "铁锈红", "青绿", "草绿", "蓝", "紫", "棕", "灰", "玫红"
    };

    private static void pickIconColor(MainActivity a, File versionDir) {
        PluginUiKit.list(a, "选底色", Arrays.asList(ICON_COLOR_NAMES),
                idx -> pickIconText(a, versionDir, ICON_COLORS[idx]));
    }

    private static void pickIconText(MainActivity a, File versionDir, int color) {
        try {
            EditText et = new EditText(a);
            et.setHint("最多 2 个字，例如「生」「1.8」");
            et.setText(suggestIconText(versionDir));
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.sub(a, "图标会写到：versions/" + versionDir.getName() + "/icon.png"));
            col.addView(et);

            PluginUiKit.sheet(a, "图标上写什么？", PluginUiKit.scroller(a, col), "生成", () -> {
                String txt = et.getText() == null ? "" : et.getText().toString().trim();
                writeInstanceIcon(a, versionDir, color, txt);
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    /** 按实例情况猜一个默认文字：模组多就用「模」，带 Fabric 用「F」，否则取版本号前两位。 */
    private static String suggestIconText(File versionDir) {
        try {
            File mods = new File(versionDir, "mods");
            int modCount = PluginUiKit.listByExt(mods, ".jar").size();
            if (modCount >= 30) {
                return "模";
            }
            if (new File(versionDir, "fabric-loader.jar").exists()
                    || new File(versionDir, "fabric.mod.json").exists()) {
                return "F";
            }
            if (new File(versionDir, "forge.jar").exists()) {
                return "Fg";
            }
            String n = versionDir.getName();
            return n.length() > 2 ? n.substring(0, 2) : n;
        } catch (Throwable t) {
            return "";
        }
    }

    private static void writeInstanceIcon(MainActivity a, File versionDir, int color, String text) {
        try {
            int size = 256;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            p.setColor(color);
            c.drawRoundRect(new RectF(0, 0, size, size), 36, 36, p);

            // 左上角一道亮边，让图标不是一块死色
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(new RectF(0, 0, size, size * 0.45f), 36, 36, p);

            if (text != null && !text.isEmpty()) {
                p.setColor(Color.WHITE);
                p.setTextAlign(Paint.Align.CENTER);
                p.setFakeBoldText(true);
                p.setTextSize(text.length() >= 2 ? size * 0.42f : size * 0.52f);
                Paint.FontMetrics fm = p.getFontMetrics();
                c.drawText(text, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
            }

            File out = new File(versionDir, "icon.png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            bmp.recycle();
            PluginUiKit.toast(a, "已写入 " + out.getName() + "（重新进版本列表即可看到）");
        } catch (Throwable t) {
            PluginUiKit.toast(a, "写图标失败：" + t);
        }
    }

    /**
     * 实例配置差异对比。
     *
     * <p>比的是**文件清单**（模组 / 资源包 / 光影 / 配置文件），不是文件内容 ——
     * 内容哈希在手机上对几十个 jar 太慢。清单差异已经能回答「为什么 A 能进 B 不能」。
     */
    public static void instanceDiff(MainActivity a) {
        try {
            List<File> versions = versionDirs(a);
            if (versions.size() < 2) {
                PluginUiKit.info(a, "实例配置差异对比", "至少要有两个实例才能对比。");
                return;
            }
            List<String> names = new ArrayList<>();
            for (File f : versions) {
                names.add(f.getName());
            }
            PluginUiKit.list(a, "选【左边】实例", names, i1 ->
                    PluginUiKit.list(a, "选【右边】实例", names, i2 -> {
                        if (i1 == i2) {
                            PluginUiKit.toast(a, "选了两个同一个实例");
                            return;
                        }
                        showInstanceDiff(a, versions.get(i1), versions.get(i2));
                    }));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "对比失败：" + t);
        }
    }

    private static void showInstanceDiff(MainActivity a, File left, File right) {
        LinearLayout col = PluginUiKit.column(a);
        col.addView(PluginUiKit.label(a, left.getName() + "   ↔   " + right.getName()));

        int totalDiff = 0;
        for (String sub : new String[]{"mods", "resourcepacks", "shaderpacks", "config"}) {
            Map<String, Long> m1 = fileMap(new File(left, sub));
            Map<String, Long> m2 = fileMap(new File(right, sub));

            List<String> onlyLeft = new ArrayList<>();
            List<String> onlyRight = new ArrayList<>();
            List<String> changed = new ArrayList<>();

            for (Map.Entry<String, Long> e : m1.entrySet()) {
                Long o = m2.get(e.getKey());
                if (o == null) {
                    onlyLeft.add(e.getKey());
                } else if (!o.equals(e.getValue())) {
                    changed.add(e.getKey());
                }
            }
            for (String k : m2.keySet()) {
                if (!m1.containsKey(k)) {
                    onlyRight.add(k);
                }
            }

            int n = onlyLeft.size() + onlyRight.size() + changed.size();
            totalDiff += n;

            LinearLayout card = PluginUiKit.card(a);
            card.addView(PluginUiKit.text(a, sub + "   差异 " + n + " 项"));
            if (n == 0) {
                card.addView(PluginUiKit.sub(a, "完全一致"));
            } else {
                card.addView(PluginUiKit.kv(a, "只在左边有", String.valueOf(onlyLeft.size())));
                card.addView(PluginUiKit.kv(a, "只在右边有", String.valueOf(onlyRight.size())));
                card.addView(PluginUiKit.kv(a, "两边都有但大小不同", String.valueOf(changed.size())));
                for (int i = 0; i < Math.min(6, onlyLeft.size()); i++) {
                    card.addView(PluginUiKit.sub(a, "  − " + onlyLeft.get(i)));
                }
                for (int i = 0; i < Math.min(6, onlyRight.size()); i++) {
                    card.addView(PluginUiKit.sub(a, "  + " + onlyRight.get(i)));
                }
                for (int i = 0; i < Math.min(6, changed.size()); i++) {
                    card.addView(PluginUiKit.sub(a, "  ~ " + changed.get(i)));
                }
                if (n > 18) {
                    card.addView(PluginUiKit.sub(a, "  …还有 " + (n - 18) + " 项，只列了前几项"));
                }
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = PluginUiKit.dp(a, 8);
            card.setLayoutParams(lp);
            col.addView(card);
        }

        col.addView(PluginUiKit.sub(a, "★ 只比文件名和大小，不比内容 —— "
                + "几十个 jar 逐个算哈希在手机上太慢。"));
        col.addView(PluginUiKit.sub(a, "★ 差异不一定有问题：不同整合包本来就不一样。"));

        PluginUiKit.sheet(a, "差异合计 " + totalDiff + " 项",
                PluginUiKit.scroller(a, col), "知道了", null);
    }

    /** 实例启动历史（读 StatsTracker 的会话列表）。 */
    public static void launchHistory(MainActivity a) {
        try {
            StatsTracker.Stats st = StatsTracker.get(a);
            LinearLayout col = PluginUiKit.column(a);

            LinearLayout sum = PluginUiKit.card(a);
            sum.addView(PluginUiKit.kv(a, "累计启动", st.launchCount + " 次"));
            sum.addView(PluginUiKit.kv(a, "累计时长", StatsTracker.formatDuration(st.totalPlayMs)));
            sum.addView(PluginUiKit.kv(a, "Java 崩溃", st.crashCount + " 次"));
            sum.addView(PluginUiKit.kv(a, "首次启动", StatsTracker.formatTime(st.firstLaunchAt)));
            sum.addView(PluginUiKit.kv(a, "最近启动", StatsTracker.formatTime(st.lastLaunchAt)));
            col.addView(sum);

            if (st.instanceLaunches != null && !st.instanceLaunches.isEmpty()) {
                LinearLayout per = PluginUiKit.card(a);
                per.addView(PluginUiKit.label(a, "各实例启动次数"));
                List<Map.Entry<String, Integer>> es =
                        new ArrayList<>(st.instanceLaunches.entrySet());
                Collections.sort(es, (x, y) -> y.getValue() - x.getValue());
                for (Map.Entry<String, Integer> e : es) {
                    per.addView(PluginUiKit.kv(a, e.getKey(), e.getValue() + " 次"));
                }
                col.addView(per);
            }

            LinearLayout sess = PluginUiKit.card(a);
            sess.addView(PluginUiKit.label(a, "最近会话"));
            if (st.sessions == null || st.sessions.isEmpty()) {
                sess.addView(PluginUiKit.sub(a, "还没有记录"));
            } else {
                int n = st.sessions.size();
                for (int i = n - 1; i >= Math.max(0, n - 20); i--) {
                    StatsTracker.Session s = st.sessions.get(i);
                    sess.addView(PluginUiKit.kv(a,
                            StatsTracker.formatTime(s.startAt) + "  " + s.instance,
                            StatsTracker.formatDuration(s.durationMs)));
                }
            }
            col.addView(sess);

            col.addView(PluginUiKit.sub(a, "★ 只统计「启动器自己记下的」会话。"
                    + "游戏进程被系统直接杀掉时，那次时长会记到下一次启动时结算。"));

            PluginUiKit.sheet(a, "实例启动历史", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "读历史失败：" + t);
        }
    }

    /** 实例健康评分：把已有检查项折成 0-100 分。 */
    public static void instanceHealth(MainActivity a) {
        try {
            File ver = currentVersionDir(a);
            if (ver == null) {
                PluginUiKit.info(a, "实例健康评分", "当前没有选中实例。");
                return;
            }

            int score = 100;
            List<String> issues = new ArrayList<>();

            // ① 版本 json
            File json = new File(ver, ver.getName() + ".json");
            if (!json.isFile() || json.length() < 16) {
                score -= 40;
                issues.add("版本 json 缺失或为空 —— 这个实例多半起不来");
            }

            // ② 内存分配 vs 本机物理内存
            try {
                long total = totalRamBytes(a);
                // ★ 用反射读内存分配：这个字段不在 LauncherSetting 上（各版本位置不同），
                //   写死字段名会编译不过。读不到就跳过这一项，不影响其它检查。
                long alloc = 0;
                try {
                    Object gs = a.publicGameSetting;
                    if (gs != null) {
                        java.lang.reflect.Field f = gs.getClass().getField("maxMemory");
                        alloc = ((Number) f.get(gs)).longValue() * 1024L * 1024L;
                    }
                } catch (Throwable ignored) {
                }
                if (total > 0 && alloc > total / 2) {
                    score -= 15;
                    issues.add("分配内存 " + (alloc / 1024 / 1024) + " MB 超过物理内存的一半（"
                            + (total / 1024 / 1024) + " MB）—— 容易在加载时被杀");
                }
            } catch (Throwable ignored) {
            }

            // ③ 模组数量
            int mods = PluginUiKit.listByExt(new File(ver, "mods"), ".jar").size();
            if (mods > 200) {
                score -= 15;
                issues.add("模组 " + mods + " 个，偏多 —— 启动慢、内存吃紧");
            } else if (mods > 150) {
                score -= 7;
                issues.add("模组 " + mods + " 个，建议留意内存");
            }

            // ④ 日志/崩溃报告堆积（说明反复失败）
            int crashReports = PluginUiKit.listByExt(new File(ver, "crash-reports"), ".txt").size();
            if (crashReports >= 5) {
                score -= 10;
                issues.add("crash-reports 里堆了 " + crashReports + " 份 —— 说明一直在崩，"
                        + "建议看「日志中心」");
            }

            score = Math.max(0, Math.min(100, score));

            LinearLayout col = PluginUiKit.column(a);
            LinearLayout head = PluginUiKit.card(a);
            head.addView(PluginUiKit.text(a, "实例：" + ver.getName()));
            TextView big = PluginUiKit.text(a, score + " 分");
            big.setTextSize(30f);
            big.setPadding(0, PluginUiKit.dp(a, 8), 0, PluginUiKit.dp(a, 4));
            big.setTextColor(score >= 85 ? 0xFF3B6D11 : (score >= 60 ? 0xFFBA7517 : 0xFFA32D2D));
            head.addView(big);
            head.addView(PluginUiKit.sub(a, score >= 85 ? "状态良好"
                    : (score >= 60 ? "能用，但有可优化的地方" : "问题较多，建议先处理下面的")));
            col.addView(head);

            LinearLayout list = PluginUiKit.card(a);
            list.addView(PluginUiKit.label(a, "发现的问题"));
            if (issues.isEmpty()) {
                list.addView(PluginUiKit.sub(a, "没查出问题。"));
            } else {
                for (String s : issues) {
                    list.addView(PluginUiKit.sub(a, "• " + s));
                }
            }
            col.addView(list);

            col.addView(PluginUiKit.sub(a, "★ 评分只依据**能本地查到的事实**"
                    + "（版本 json / 内存分配 / 模组数 / 崩溃报告数），"
                    + "不猜「这个模组好不好玩」这类主观项。"));
            col.addView(PluginUiKit.sub(a, "★ 更细的逐项自检在「我的 → 启动诊断」。这里只是把结论折成一个分。"));
            PluginUiKit.sheet(a, "实例健康评分", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "评分失败：" + t);
        }
    }

    // ==================================================================
    // 模组管理
    // ==================================================================

    /** 模组标签自动分类（按文件名/描述关键词打标签）。 */
    public static void modTagging(MainActivity a) {
        try {
            File ver = currentVersionDir(a);
            if (ver == null) {
                PluginUiKit.info(a, "模组标签自动分类", "当前没有选中实例。");
                return;
            }
            List<File> mods = PluginUiKit.listByExt(new File(ver, "mods"), ".jar");
            if (mods.isEmpty()) {
                PluginUiKit.info(a, "模组标签自动分类", "这个实例的 mods 目录是空的。");
                return;
            }

            Map<String, List<String>> byTag = new LinkedHashMap<>();
            for (String t : TAGS) {
                byTag.put(t, new ArrayList<>());
            }
            byTag.put("未分类", new ArrayList<>());

            for (File m : mods) {
                String tag = guessTag(m.getName().toLowerCase(Locale.US));
                byTag.get(tag).add(m.getName());
            }

            LinearLayout col = PluginUiKit.column(a);
            LinearLayout head = PluginUiKit.card(a);
            head.addView(PluginUiKit.text(a, "共 " + mods.size() + " 个模组"));
            head.addView(PluginUiKit.sub(a, "按**文件名关键词**自动归类 —— "
                    + "文件名里带什么就归什么。改过名的模组会落到「未分类」，这是正常的。"));
            col.addView(head);

            for (Map.Entry<String, List<String>> e : byTag.entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                LinearLayout card = PluginUiKit.card(a);
                card.addView(PluginUiKit.text(a, e.getKey() + "   " + e.getValue().size() + " 个"));
                for (String n : e.getValue()) {
                    card.addView(PluginUiKit.sub(a, "  " + n));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = PluginUiKit.dp(a, 8);
                card.setLayoutParams(lp);
                col.addView(card);
            }
            col.addView(PluginUiKit.sub(a, "★ 只是**分类展示**，不改名、不移文件、不禁用。"));
            PluginUiKit.sheet(a, "模组标签自动分类", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "分类失败：" + t);
        }
    }

    private static final String[] TAGS = {"优化", "建筑", "科技", "魔法", "冒险", "地图/小地图", "界面/工具"};
    private static final String[][] TAG_WORDS = {
            {"sodium", "optifine", "iris", "rubidium", "embeddium", "ferrite", "lithium",
                    "phosphor", "starlight", "performance", "fps", "optimize", "memoryleakfix",
                    "krypton", "c2me", "vmp", "modernfix"},
            {"create", "building", "chisel", "decocraft", "architectury", "buildcraft",
                    "construction", "furniture", "macaw", "roof", "window"},
            {"tech", "industrial", "mekanism", "thermal", "gregtech", "ic2", "ae2",
                    "appliedenergistics", "powah", "energy", "machine", "factory"},
            {"magic", "botania", "thaumcraft", "ars", "blood", "witchery", "astral",
                    "mana", "spell", "rune", "enchant"},
            {"adventure", "dungeon", "boss", "mob", "monster", "exploration", "quest",
                    "rpg", "iceandfire", "twilight", "aether", "cataclysm"},
            {"map", "minimap", "journeymap", "xaero", "waypoint", "atlas", "compass"},
            {"jei", "rei", "emi", "wthit", "hwyla", "jade", "tooltip", "inventory",
                    "gui", "hud", "ui", "menu", "crafting"},
    };

    private static String guessTag(String lowerName) {
        for (int t = 0; t < TAGS.length; t++) {
            for (String w : TAG_WORDS[t]) {
                if (lowerName.contains(w)) {
                    return TAGS[t];
                }
            }
        }
        return "未分类";
    }

    /** 模组锁定列表：锁定的模组在「更新」类操作里会被跳过。 */
    public static void modLock(MainActivity a) {
        try {
            File ver = currentVersionDir(a);
            if (ver == null) {
                PluginUiKit.info(a, "模组锁定列表", "当前没有选中实例。");
                return;
            }
            List<File> mods = PluginUiKit.listByExt(new File(ver, "mods"), ".jar");
            if (mods.isEmpty()) {
                PluginUiKit.info(a, "模组锁定列表", "这个实例的 mods 目录是空的。");
                return;
            }

            List<String> locked = ModLock.load(a, ver.getName());
            List<String> names = new ArrayList<>();
            for (File m : mods) {
                names.add((locked.contains(m.getName()) ? "🔒 " : "　 ") + m.getName());
            }

            PluginUiKit.list(a, "点一下切换锁定（🔒 = 已锁）", names, idx -> {
                String n = mods.get(idx).getName();
                if (locked.contains(n)) {
                    locked.remove(n);
                    PluginUiKit.toast(a, "已解锁：" + n);
                } else {
                    locked.add(n);
                    PluginUiKit.toast(a, "已锁定：" + n);
                }
                ModLock.save(a, ver.getName(), locked);
                modLock(a);
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    // ==================================================================
    // 存档管理
    // ==================================================================

    /** 存档版本转换提醒：比对存档的 MC 版本与当前实例版本。 */
    public static void worldVersionCheck(MainActivity a) {
        try {
            File saves = savesDir(a);
            if (saves == null || !saves.isDirectory()) {
                PluginUiKit.info(a, "存档版本转换提醒", "找不到 saves 目录。");
                return;
            }
            List<World> worlds = World.getWorlds(saves.toPath())
                    .collect(java.util.stream.Collectors.toList());
            if (worlds.isEmpty()) {
                PluginUiKit.info(a, "存档版本转换提醒", "还没有任何存档。");
                return;
            }

            String instanceVer = currentInstanceName(a);
            LinearLayout col = PluginUiKit.column(a);
            int mismatch = 0;

            for (World w : worlds) {
                String wv = w.getGameVersion();
                boolean ok = instanceVer != null && wv != null
                        && instanceVer.contains(wv);
                if (!ok) {
                    mismatch++;
                }
                LinearLayout card = PluginUiKit.card(a);
                card.addView(PluginUiKit.text(a, (ok ? "✔ " : "⚠ ") + w.getWorldName()));
                card.addView(PluginUiKit.kv(a, "存档版本", wv == null ? "未记录" : wv));
                card.addView(PluginUiKit.kv(a, "当前实例", instanceVer == null ? "—" : instanceVer));
                card.addView(PluginUiKit.kv(a, "上次游玩",
                        StatsTracker.formatTime(w.getLastPlayed())));
                if (!ok) {
                    card.addView(PluginUiKit.sub(a,
                            "  版本不一致。★ 用**更高版本**打开会就地升级存档，"
                                    + "且**不可逆**；用更低版本打开则可能报错。建议先备份。"));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = PluginUiKit.dp(a, 8);
                card.setLayoutParams(lp);
                col.addView(card);
            }

            col.addView(PluginUiKit.sub(a, "★ 只是**提醒**，不会自动转换、不会改任何存档。"
                    + "要备份的话去「我的 → 备份与恢复」。"));
            PluginUiKit.sheet(a, mismatch == 0 ? "版本都对得上" : (mismatch + " 个存档版本不一致"),
                    PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "检查失败：" + t);
        }
    }

    /** 存档玩家列表：读 playerdata 目录（每个 .dat 就是一个玩家）。 */
    public static void worldPlayers(MainActivity a) {
        try {
            File saves = savesDir(a);
            if (saves == null || !saves.isDirectory()) {
                PluginUiKit.info(a, "存档玩家列表", "找不到 saves 目录。");
                return;
            }
            List<World> worlds = World.getWorlds(saves.toPath())
                    .collect(java.util.stream.Collectors.toList());
            if (worlds.isEmpty()) {
                PluginUiKit.info(a, "存档玩家列表", "还没有任何存档。");
                return;
            }

            List<String> names = new ArrayList<>();
            for (World w : worlds) {
                names.add(w.getWorldName());
            }
            PluginUiKit.list(a, "看哪个存档？", names, idx -> showWorldPlayers(a, worlds.get(idx)));
        } catch (Throwable t) {
            PluginUiKit.toast(a, "失败：" + t);
        }
    }

    private static void showWorldPlayers(MainActivity a, World w) {
        try {
            File dir = new File(w.getFile().toFile(), "playerdata");
            List<File> dat = PluginUiKit.listByExt(dir, ".dat");

            LinearLayout col = PluginUiKit.column(a);
            LinearLayout head = PluginUiKit.card(a);
            head.addView(PluginUiKit.text(a, w.getWorldName()));
            head.addView(PluginUiKit.kv(a, "玩家数据文件", dat.size() + " 个"));
            col.addView(head);

            if (dat.isEmpty()) {
                col.addView(PluginUiKit.sub(a, "没有 playerdata —— 单人存档里玩家数据存在 level.dat 里，"
                        + "这里为空是正常的。"));
            } else {
                for (File f : dat) {
                    String uuid = f.getName().replace(".dat", "");
                    LinearLayout card = PluginUiKit.card(a);
                    card.addView(PluginUiKit.text(a, shortUuid(uuid)));
                    card.addView(PluginUiKit.kv(a, "UUID", uuid));
                    card.addView(PluginUiKit.kv(a, "最后修改",
                            StatsTracker.formatTime(f.lastModified())));
                    card.addView(PluginUiKit.kv(a, "数据大小", PluginUiKit.size(f.length())));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = PluginUiKit.dp(a, 8);
                    card.setLayoutParams(lp);
                    col.addView(card);
                }
            }
            col.addView(PluginUiKit.sub(a, "★ 只读 UUID / 时间 / 大小。"
                    + "**不解析玩家背包和坐标** —— 那需要完整实现 NBT 物品栈反序列化，"
                    + "而且显示别人的坐标涉及隐私，本项目不做。"));
            PluginUiKit.sheet(a, "存档玩家列表", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "读取失败：" + t);
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /** 快速笔记：本地 JSON，分类 + 置顶 + 搜索。 */
    public static void notes(MainActivity a) {
        try {
            List<Note> all = Notes.load(a);
            LinearLayout col = PluginUiKit.column(a);

            EditText input = new EditText(a);
            input.setHint("记一条：服务器 IP / 坐标 / 待办…");
            col.addView(input);

            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, PluginUiKit.dp(a, 44));
            TextView add = PluginUiKit.row(a, "＋ 添加", null);
            add.setGravity(android.view.Gravity.CENTER);
            add.setLayoutParams(btnLp);
            col.addView(add);

            if (all.isEmpty()) {
                col.addView(PluginUiKit.sub(a, "还没有笔记。"));
            } else {
                for (int i = 0; i < all.size(); i++) {
                    final Note n = all.get(i);
                    LinearLayout card = PluginUiKit.card(a);
                    card.addView(PluginUiKit.text(a, (n.pinned ? "📌 " : "") + n.text));
                    card.addView(PluginUiKit.sub(a, StatsTracker.formatTime(n.at)));
                    LinearLayout ops = PluginUiKit.column(a);
                    ops.setOrientation(LinearLayout.HORIZONTAL);
                    TextView pin = PluginUiKit.sub(a, n.pinned ? "取消置顶" : "置顶");
                    pin.setPadding(0, PluginUiKit.dp(a, 6), PluginUiKit.dp(a, 18), 0);
                    pin.setOnClickListener(v -> {
                        n.pinned = !n.pinned;
                        Notes.save(a, all);
                        PluginUiKit.toast(a, n.pinned ? "已置顶" : "已取消置顶");
                    });
                    ops.addView(pin);
                    TextView del = PluginUiKit.sub(a, "删除");
                    del.setPadding(0, PluginUiKit.dp(a, 6), 0, 0);
                    del.setOnClickListener(v -> PluginUiKit.confirm(a, "删除这条笔记？",
                            n.text, () -> {
                                all.remove(n);
                                Notes.save(a, all);
                                PluginUiKit.toast(a, "已删除");
                            }));
                    ops.addView(del);
                    card.addView(ops);

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = PluginUiKit.dp(a, 8);
                    card.setLayoutParams(lp);
                    col.addView(card);
                }
            }

            add.setOnClickListener(v -> {
                String t = input.getText() == null ? "" : input.getText().toString().trim();
                if (t.isEmpty()) {
                    PluginUiKit.toast(a, "内容为空");
                    return;
                }
                all.add(new Note(t, System.currentTimeMillis(), false));
                Notes.save(a, all);
                input.setText("");
                PluginUiKit.toast(a, "已添加（关掉重开可看到排序后的列表）");
            });

            col.addView(PluginUiKit.sub(a, "★ 存在 " + AppManifest.SETTING_DIR
                    + "/qcl_notes.json，纯本地，不上传。"));
            PluginUiKit.sheet(a, "快速笔记", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "笔记失败：" + t);
        }
    }

    /** 一键恢复出厂：重置设置，可选保留实例/存档/账号。 */
    public static void factoryReset(MainActivity a) {
        PluginUiKit.confirm(a, "恢复出厂设置",
                "会重置启动器的**所有设置**（外观、下载、启动参数…）。\n\n"
                        + "★ 不会删实例、不会删存档、不会删账号。\n"
                        + "★ 设置文件在 " + AppManifest.SETTING_DIR + "，只会重写这一份。\n\n"
                        + "确定继续？", () -> {
                    try {
                        File dir = new File(AppManifest.SETTING_DIR);
                        List<File> files = PluginUiKit.listFiles(dir);
                        int n = 0;
                        for (File f : files) {
                            if (f.isFile() && f.getName().endsWith(".json")
                                    && !f.getName().startsWith("qcl_notes")) {
                                // 只删设置类 json，笔记/统计这类用户数据留着
                                if (f.delete()) {
                                    n++;
                                }
                            }
                        }
                        PluginUiKit.toast(a, "已重置 " + n + " 份设置。重启启动器后生效。");
                    } catch (Throwable t) {
                        PluginUiKit.toast(a, "重置失败：" + t);
                    }
                });
    }

    // ==================================================================
    // 共用小工具
    // ==================================================================

    private static List<File> versionDirs(MainActivity a) {
        List<File> out = new ArrayList<>();
        try {
            String gd = a.launcherSetting == null ? null : a.launcherSetting.gameFileDirectory;
            if (gd == null) {
                gd = AppManifest.DEFAULT_GAME_DIR;
            }
            File versions = new File(gd, "versions");
            for (File f : PluginUiKit.listFiles(versions)) {
                if (f.isDirectory()) {
                    out.add(f);
                }
            }
            Collections.sort(out, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static File currentVersionDir(MainActivity a) {
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

    private static String currentInstanceName(MainActivity a) {
        File f = currentVersionDir(a);
        return f == null ? null : f.getName();
    }

    private static File savesDir(MainActivity a) {
        try {
            String gd = a.launcherSetting == null ? null : a.launcherSetting.gameFileDirectory;
            if (gd == null) {
                gd = AppManifest.DEFAULT_GAME_DIR;
            }
            return new File(gd, "saves");
        } catch (Throwable t) {
            return null;
        }
    }

    private static Map<String, Long> fileMap(File dir) {
        Map<String, Long> m = new HashMap<>();
        for (File f : PluginUiKit.listFiles(dir)) {
            if (f.isFile()) {
                m.put(f.getName(), f.length());
            }
        }
        return m;
    }

    private static String shortUuid(String uuid) {
        try {
            java.util.UUID u = java.util.UUID.fromString(uuid);
            return u.toString().substring(0, 8);
        } catch (Throwable t) {
            return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
        }
    }

    private static long totalRamBytes(Context c) {
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                return mi.totalMem;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    // ==================================================================
    // 两个本地存储（笔记 / 模组锁定）—— 都写 SETTING_DIR，纯本地
    // ==================================================================

    /** 一条笔记。 */
    public static class Note {
        public String text;
        public long at;
        public boolean pinned;

        public Note(String text, long at, boolean pinned) {
            this.text = text;
            this.at = at;
            this.pinned = pinned;
        }
    }

    /** 笔记存储。格式极简（每行一条，制表符分隔），不引 JSON 库。 */
    static final class Notes {
        private static File file(Context c) {
            return new File(AppManifest.SETTING_DIR, "qcl_notes.json");
        }

        static List<Note> load(Context c) {
            List<Note> out = new ArrayList<>();
            try {
                String s = PluginUiKit.readText(file(c));
                if (s == null || s.isEmpty()) {
                    return out;
                }
                for (String line : s.split("\n")) {
                    String[] p = line.split("\t");
                    if (p.length >= 3) {
                        out.add(new Note(p[2], Long.parseLong(p[1]), "1".equals(p[0])));
                    }
                }
                Collections.sort(out, (x, y) -> {
                    if (x.pinned != y.pinned) {
                        return x.pinned ? -1 : 1;
                    }
                    return Long.compare(y.at, x.at);
                });
            } catch (Throwable ignored) {
            }
            return out;
        }

        static void save(Context c, List<Note> list) {
            try {
                File f = file(c);
                File dir = f.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                StringBuilder sb = new StringBuilder();
                for (Note n : list) {
                    sb.append(n.pinned ? "1" : "0").append('\t')
                            .append(n.at).append('\t')
                            .append(n.text.replace('\n', ' ').replace('\t', ' ')).append('\n');
                }
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(sb.toString().getBytes("UTF-8"));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** 模组锁定存储：一行一个 mod 文件名。 */
    static final class ModLock {
        private static File file(Context c, String instance) {
            return new File(AppManifest.SETTING_DIR, "qcl_modlock_" + safe(instance) + ".txt");
        }

        private static String safe(String s) {
            return s == null ? "default" : s.replaceAll("[^A-Za-z0-9_.\\-]", "_");
        }

        static List<String> load(Context c, String instance) {
            List<String> out = new ArrayList<>();
            try {
                String s = PluginUiKit.readText(file(c, instance));
                if (s != null) {
                    for (String line : s.split("\n")) {
                        if (!line.trim().isEmpty()) {
                            out.add(line.trim());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return out;
        }

        static void save(Context c, String instance, List<String> list) {
            try {
                File f = file(c, instance);
                File dir = f.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                StringBuilder sb = new StringBuilder();
                for (String s : list) {
                    sb.append(s).append('\n');
                }
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(sb.toString().getBytes("UTF-8"));
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
