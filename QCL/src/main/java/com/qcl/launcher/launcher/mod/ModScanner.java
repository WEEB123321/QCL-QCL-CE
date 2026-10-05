package com.qcl.launcher.launcher.mod;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ★★★ 社区版新增：模组体检扫描器。
 *
 * <p><b>解决什么问题</b>：装了模组之后进不去游戏时，最难的一步是「哪个模组不对」。
 * 这里不猜、也不联网，只做一件确定性的事：<b>把每个 jar 自己声明的元数据读出来</b>
 * （加载器类型、声明的 MC 版本、mod id），再和当前实例的版本/加载器对照，
 * 把明显对不上的挑出来。
 *
 * <p><b>★ 只读</b>：不移动、不删除、不改名任何模组文件。
 *
 * <p><b>★ 判据是「模组自己声明的」，不是「我猜的」</b>：
 * <ul>
 *   <li>{@code fabric.mod.json} → Fabric / Quilt；</li>
 *   <li>{@code META-INF/mods.toml} → Forge / NeoForge；</li>
 *   <li>{@code mcmod.info} → 老 Forge（1.12 及以前）；</li>
 *   <li>{@code litemod.json} → LiteLoader；</li>
 *   <li>都没有 → 标为「无法识别」（远古 ModLoader 模组常常就是普通 jar，这是<b>正常的</b>，
 *       所以只提示、不报警）。</li>
 * </ul>
 */
public final class ModScanner {

    private static final String TAG = "QCLMod";

    /** 单个 jar 最多读多少字节的元数据 —— 防止畸形 jar 把内存吃光 */
    private static final int MAX_META_BYTES = 64 * 1024;

    private ModScanner() {
    }

    /** 单个模组的信息。 */
    public static class ModInfo {
        public String fileName;
        public long sizeBytes;
        /** Forge / NeoForge / Fabric / Quilt / LiteLoader / Legacy / unknown */
        public String loader = "unknown";
        /** 模组自己声明的 MC 版本（可能为空 = 没声明） */
        public String declaredMcVersion = "";
        public String modId = "";
        public String modName = "";
        /** 读取失败原因 */
        public String error;
    }

    /** 体检结果。 */
    public static class Report {
        public final List<ModInfo> mods = new ArrayList<>();
        /** 从实例名推测的加载器 */
        public String instanceLoader = "";
        /** 从实例名推测的 MC 版本 */
        public String instanceVersion = "";
        /** 提示（人话）。软提示：值得看一眼，但不一定是错误。 */
        public final List<String> warnings = new ArrayList<>();
        /**
         * ★ 硬问题：几乎可以确定会导致进不去游戏（例如加载器完全对不上）。
         * <p>与 {@link #warnings} 分开，是为了让界面能用红/蓝两种颜色区分
         * 「确定有问题」和「可能有问题」—— 全标红等于没标。
         */
        public final List<String> hardWarnings = new ArrayList<>();

        public int loaderMismatchCount;
    }

    /**
     * 扫描实例的 mods 目录。
     *
     * @param instanceDir  实例目录
     * @param instanceName 实例名（用于推测加载器与 MC 版本）
     */
    public static Report scan(File instanceDir, String instanceName) {
        Report r = new Report();
        try {
            r.instanceLoader = guessLoader(instanceName);
            r.instanceVersion = guessVersion(instanceName);

            File mods = new File(instanceDir, "mods");
            File[] files = mods.listFiles();
            if (files == null) {
                return r;
            }
            for (File f : files) {
                if (!f.isFile()) {
                    continue;
                }
                String n = f.getName().toLowerCase();
                if (!n.endsWith(".jar") && !n.endsWith(".zip") && !n.endsWith(".litemod")) {
                    continue;
                }
                r.mods.add(read(f));
            }
            analyse(r);
        } catch (Throwable t) {
            Log.w(TAG, "扫描模组失败: " + t);
        }
        return r;
    }

    // ------------------------------------------------------------------ 单个 jar

    private static ModInfo read(File f) {
        ModInfo m = new ModInfo();
        m.fileName = f.getName();
        m.sizeBytes = f.length();
        ZipFile zf = null;
        try {
            if (m.fileName.toLowerCase().endsWith(".litemod")) {
                m.loader = "LiteLoader";
            }
            zf = new ZipFile(f);

            // ① Fabric / Quilt
            String fabric = readEntry(zf, "fabric.mod.json");
            if (fabric != null) {
                m.loader = "Fabric";
                parseFabric(fabric, m);
                return m;
            }
            // ② Forge / NeoForge（1.13+）
            if (zf.getEntry("META-INF/mods.toml") != null) {
                // ★ 不解析 TOML：只判定类型。声明版本在 toml 里格式多变，硬解容易出错，
                //   而「是 Forge 系模组」这个结论已经足够支撑体检。
                m.loader = "Forge";
                String toml = readEntry(zf, "META-INF/mods.toml");
                if (toml != null) {
                    m.declaredMcVersion = findTomlVersionRange(toml);
                    m.modName = findTomlValue(toml, "displayName");
                }
                return m;
            }
            // ③ 老 Forge（1.12 及以前）
            String mcmod = readEntry(zf, "mcmod.info");
            if (mcmod != null) {
                m.loader = "Forge";
                parseMcmodInfo(mcmod, m);
                return m;
            }
            // ④ LiteLoader
            if (zf.getEntry("litemod.json") != null || m.fileName.toLowerCase().endsWith(".litemod")) {
                m.loader = "LiteLoader";
                return m;
            }
            // ⑤ 读不出类型 —— 远古 ModLoader 模组常常就是普通 jar，这是正常的
            m.loader = "unknown";
        } catch (Throwable t) {
            m.error = String.valueOf(t);
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return m;
    }

    private static void parseFabric(String json, ModInfo m) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("id")) {
                m.modId = root.get("id").getAsString();
            }
            if (root.has("name")) {
                m.modName = root.get("name").getAsString();
            }
            if (root.has("depends") && root.get("depends").isJsonObject()) {
                JsonObject dep = root.getAsJsonObject("depends");
                if (dep.has("minecraft")) {
                    m.declaredMcVersion = dep.get("minecraft").getAsString();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void parseMcmodInfo(String json, ModInfo m) {
        try {
            String s = json.trim();
            JsonElement el = JsonParser.parseString(s);
            JsonObject obj = null;
            if (el.isJsonArray() && el.getAsJsonArray().size() > 0) {
                obj = el.getAsJsonArray().get(0).getAsJsonObject();
            } else if (el.isJsonObject()) {
                obj = el.getAsJsonObject();
                if (obj.has("modList") && obj.get("modList").isJsonArray()) {
                    JsonArray arr = obj.getAsJsonArray("modList");
                    if (arr.size() > 0) {
                        obj = arr.get(0).getAsJsonObject();
                    }
                }
            }
            if (obj != null) {
                if (obj.has("modid")) {
                    m.modId = obj.get("modid").getAsString();
                }
                if (obj.has("name")) {
                    m.modName = obj.get("name").getAsString();
                }
                if (obj.has("mcversion")) {
                    m.declaredMcVersion = obj.get("mcversion").getAsString();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 从 mods.toml 里抠出 versionRange（形如 {@code versionRange="[1.12,1.13)"}）。 */
    private static String findTomlVersionRange(String toml) {
        try {
            int i = toml.indexOf("versionRange");
            if (i < 0) {
                return "";
            }
            int eq = toml.indexOf('=', i);
            if (eq < 0) {
                return "";
            }
            int end = toml.indexOf('\n', eq);
            String v = (end > 0 ? toml.substring(eq + 1, end) : toml.substring(eq + 1)).trim();
            return v.replace("\"", "").trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String findTomlValue(String toml, String key) {
        try {
            int i = toml.indexOf(key);
            if (i < 0) {
                return "";
            }
            int eq = toml.indexOf('=', i);
            if (eq < 0) {
                return "";
            }
            int end = toml.indexOf('\n', eq);
            String v = (end > 0 ? toml.substring(eq + 1, end) : toml.substring(eq + 1)).trim();
            return v.replace("\"", "").trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readEntry(ZipFile zf, String name) {
        try {
            ZipEntry e = zf.getEntry(name);
            if (e == null) {
                return null;
            }
            if (e.getSize() > MAX_META_BYTES) {
                return null;
            }
            InputStream in = zf.getInputStream(e);
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                int guard = 0;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                    if (++guard > 2000) {
                        break;
                    }
                }
                return sb.toString();
            } finally {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 分析

    private static void analyse(Report r) {
        int unknown = 0;
        Map<String, Integer> loaders = new LinkedHashMap<>();
        for (ModInfo m : r.mods) {
            loaders.put(m.loader, (loaders.containsKey(m.loader) ? loaders.get(m.loader) : 0) + 1);
            if ("unknown".equals(m.loader)) {
                unknown++;
            }
            if (m.error != null) {
                r.warnings.add("读不出这个文件（可能不是模组，或已损坏）：" + m.fileName);
            }
            // 加载器对不上：实例是 Fabric 却放了 Forge 模组（或反过来）
            if (!r.instanceLoader.isEmpty() && isKnown(m.loader)
                    && !loaderCompatible(m.loader, r.instanceLoader)) {
                r.loaderMismatchCount++;
                r.hardWarnings.add("加载器对不上：" + m.fileName + " 是 " + m.loader
                        + " 模组，但当前实例是 " + r.instanceLoader);
            }
            // 声明的 MC 版本和实例对不上
            if (!m.declaredMcVersion.isEmpty() && !r.instanceVersion.isEmpty()
                    && !versionCompatible(m.declaredMcVersion, r.instanceVersion)) {
                r.warnings.add("版本可能对不上：" + m.fileName
                        + " 声明支持 " + m.declaredMcVersion + "，当前实例是 " + r.instanceVersion);
            }
        }
        if (r.mods.isEmpty()) {
            r.warnings.add("这个实例还没有装模组。");
        }
        if (unknown > 0) {
            r.warnings.add("有 " + unknown + " 个文件没有可识别的模组元数据。"
                    + "远古版本（ModLoader 时代）的模组本来就是这样，属正常；"
                    + "但如果它们本该是 Forge/Fabric 模组，就要怀疑下载不完整。");
        }
        if (r.mods.size() > 150) {
            r.warnings.add("模组数量偏多（" + r.mods.size() + " 个），互相冲突和吃内存的概率明显上升。");
        }
    }

    private static boolean isKnown(String loader) {
        return loader != null && !"unknown".equals(loader);
    }

    /** Fabric 与 Quilt 互通；Forge 与 NeoForge 不通（1.20.2 起是两套）。 */
    private static boolean loaderCompatible(String modLoader, String instanceLoader) {
        if (modLoader.equals(instanceLoader)) {
            return true;
        }
        if (("Fabric".equals(modLoader) && "Quilt".equals(instanceLoader))
                || ("Quilt".equals(modLoader) && "Fabric".equals(instanceLoader))) {
            return true;
        }
        return false;
    }

    /** 宽松匹配：只要两串里出现同一个 1.x 版本段就算兼容；声明是区间时只看是否含当前版本。 */
    private static boolean versionCompatible(String declared, String actual) {
        try {
            String d = declared.replace(" ", "");
            if (d.contains(actual)) {
                return true;
            }
            // 形如 >=1.19 / [1.18,1.20) / 1.19.x —— 抠出所有 1.x 段逐个比前缀
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(d);
            while (m.find()) {
                String g = m.group(1);
                if (actual.startsWith(g) || g.startsWith(actual)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return true;   // 判不了就不报警，避免误报
        }
    }

    private static String guessLoader(String name) {
        if (name == null) {
            return "";
        }
        String n = name.toLowerCase();
        if (n.contains("neoforge")) {
            return "NeoForge";
        }
        if (n.contains("forge")) {
            return "Forge";
        }
        if (n.contains("quilt")) {
            return "Quilt";
        }
        if (n.contains("fabric")) {
            return "Fabric";
        }
        return "";
    }

    private static String guessVersion(String name) {
        if (name == null) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(name);
        String best = "";
        while (m.find()) {
            String g = m.group(1);
            if (g.startsWith("1.")) {
                best = g;
            }
        }
        return best;
    }
}
