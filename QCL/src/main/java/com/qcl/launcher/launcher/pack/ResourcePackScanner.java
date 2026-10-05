package com.qcl.launcher.launcher.pack;

import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ★★★ 社区版新增：资源包冲突扫描器。
 *
 * <p><b>解决什么问题</b>：玩家堆十几个资源包后，出现「方块变紫黑」「材质不生效」时，
 * 完全不知道该怪谁 —— 因为后加载的包会**覆盖**前一个包里的同名文件，而顺序由
 * {@code options.txt} 的 {@code resourcePacks} 列表决定。本类把这件事变成一张清单：
 * <b>谁重复了、谁坏了、谁覆盖了谁</b>。
 *
 * <p><b>★ 性能边界</b>：一个 100MB 的资源包可能有几万个文件，逐个记录会吃光内存。
 * 所以每个包<b>最多记录 {@link #MAX_ENTRIES} 条路径</b>，超过后停止收集并标记
 * {@code truncated}。这不影响「重复 / 损坏」两类检测（它们只看包级信息），
 * 只让超大包的「覆盖」检测变成抽样 —— 界面上会标注出来，不假装是完整结果。
 *
 * <p><b>★ 只读</b>：不修改任何资源包，也不改 {@code options.txt}。
 */
public final class ResourcePackScanner {

    private static final String TAG = "QCLPack";

    /** 每个包最多收集的条目数（内存边界，见类注释） */
    private static final int MAX_ENTRIES = 4000;

    /** 参与重叠检测的资源包目录（相对实例根） */
    private static final String[] PACK_DIRS = {"resourcepacks", "shaderpacks"};

    private ResourcePackScanner() {
    }

    /** 单个资源包的信息。 */
    public static class PackInfo {
        public String name;
        public String path;
        public boolean zip;
        public long sizeBytes;
        /** pack.mcmeta 里的 pack_format；-1 = 没读到 */
        public int packFormat = -1;
        /** 收集到的条目数（可能被截断，见 truncated） */
        public int fileCount;
        /** 是否因超出上限而截断 */
        public boolean truncated;
        /** 读取失败原因；null = 正常 */
        public String error;
        /** 条目路径集合（用于重叠检测） */
        public final Set<String> entries = new LinkedHashSet<>();
        /** 在 options.txt 里的启用顺序；-1 = 未在列表里（即未启用） */
        public int order = -1;
    }

    /** 扫描结果。 */
    public static class Report {
        public final List<PackInfo> packs = new ArrayList<>();
        /** 同名包（按名字分组，组内 >1 即重复） */
        public final Map<String, List<PackInfo>> duplicates = new LinkedHashMap<>();
        /** 有问题的包（读不出 mcmeta / 读取失败） */
        public final List<PackInfo> invalid = new ArrayList<>();
        /** 被多个包同时提供的文件：路径 → 提供它的包名列表（按加载顺序） */
        public final Map<String, List<String>> overlaps = new LinkedHashMap<>();
        /** 已启用的包名（options.txt 里列出的） */
        public final List<String> enabled = new ArrayList<>();

        public int conflictCount() {
            int n = 0;
            for (List<PackInfo> v : duplicates.values()) {
                n += v.size() - 1;
            }
            n += invalid.size();
            n += overlaps.size();
            return n;
        }
    }

    /**
     * 扫描实例目录下的资源包 / 光影包。
     *
     * @param instanceDir 实例目录（含 resourcepacks / shaderpacks）
     */
    public static Report scan(File instanceDir) {
        Report r = new Report();
        try {
            List<String> enabled = readEnabledPacks(instanceDir);
            r.enabled.addAll(enabled);

            for (String dir : PACK_DIRS) {
                File root = new File(instanceDir, dir);
                File[] children = root.listFiles();
                if (children == null) {
                    continue;
                }
                for (File c : children) {
                    // 跳过 macOS 压出来的垃圾
                    if (c.getName().startsWith(".")) {
                        continue;
                    }
                    PackInfo p = read(c, dir);
                    p.order = indexOfPack(enabled, p.name, dir);
                    r.packs.add(p);
                }
            }
            detect(r);
        } catch (Throwable t) {
            Log.w(TAG, "扫描资源包失败: " + t);
        }
        return r;
    }

    /** 读取单个包（zip 或目录）。 */
    private static PackInfo read(File f, String kind) {
        PackInfo p = new PackInfo();
        p.name = f.getName();
        p.path = kind + "/" + f.getName();
        p.zip = f.isFile();
        try {
            if (p.zip) {
                p.sizeBytes = f.length();
                ZipFile zf = null;
                try {
                    zf = new ZipFile(f);
                    String meta = readZipEntry(zf, "pack.mcmeta");
                    p.packFormat = parsePackFormat(meta);
                    if (meta == null) {
                        p.error = "缺少 pack.mcmeta";
                    }
                    Enumeration<? extends ZipEntry> en = zf.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry e = en.nextElement();
                        if (e.isDirectory()) {
                            continue;
                        }
                        if (p.entries.size() >= MAX_ENTRIES) {
                            p.truncated = true;
                            break;
                        }
                        p.entries.add(e.getName());
                    }
                    p.fileCount = p.entries.size();
                } finally {
                    if (zf != null) {
                        try {
                            zf.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } else if (f.isDirectory()) {
                File metaFile = new File(f, "pack.mcmeta");
                if (!metaFile.isFile()) {
                    p.error = "缺少 pack.mcmeta";
                } else {
                    p.packFormat = parsePackFormat(readText(metaFile));
                }
                collectDir(f, "", p, 0);
                p.fileCount = p.entries.size();
                p.sizeBytes = dirSize(f);
            } else {
                p.error = "不是文件也不是目录";
            }
        } catch (Throwable t) {
            p.error = String.valueOf(t);
        }
        return p;
    }

    private static void collectDir(File dir, String prefix, PackInfo p, int depth) {
        if (depth > 12 || p.entries.size() >= MAX_ENTRIES) {
            if (p.entries.size() >= MAX_ENTRIES) {
                p.truncated = true;
            }
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            String path = prefix.isEmpty() ? c.getName() : prefix + "/" + c.getName();
            if (c.isDirectory()) {
                collectDir(c, path, p, depth + 1);
            } else {
                if (p.entries.size() >= MAX_ENTRIES) {
                    p.truncated = true;
                    return;
                }
                p.entries.add(path);
            }
        }
    }

    private static void detect(Report r) {
        Map<String, List<PackInfo>> byName = new LinkedHashMap<>();
        for (PackInfo p : r.packs) {
            List<PackInfo> list = byName.get(p.name);
            if (list == null) {
                list = new ArrayList<>();
                byName.put(p.name, list);
            }
            list.add(p);
            if (p.error != null) {
                r.invalid.add(p);
            }
        }
        for (Map.Entry<String, List<PackInfo>> e : byName.entrySet()) {
            if (e.getValue().size() > 1) {
                r.duplicates.put(e.getKey(), e.getValue());
            }
        }
        // 重叠：按「加载顺序」排序后，后加载的覆盖先加载的
        List<PackInfo> ordered = new ArrayList<>(r.packs);
        ordered.sort((a, b) -> Integer.compare(orderKey(a), orderKey(b)));
        Map<String, List<String>> provider = new HashMap<>();
        for (PackInfo p : ordered) {
            for (String entry : p.entries) {
                List<String> who = provider.get(entry);
                if (who == null) {
                    who = new ArrayList<>();
                    provider.put(entry, who);
                }
                if (!who.contains(p.name)) {
                    who.add(p.name);
                }
            }
        }
        for (Map.Entry<String, List<String>> e : provider.entrySet()) {
            if (e.getValue().size() > 1) {
                r.overlaps.put(e.getKey(), e.getValue());
            }
        }
    }

    private static int orderKey(PackInfo p) {
        return p.order < 0 ? Integer.MAX_VALUE : p.order;
    }

    // ------------------------------------------------------------------ options.txt

    /** 读 {@code options.txt} 的 {@code resourcePacks:[...]} —— 越靠后 = 优先级越高。 */
    private static List<String> readEnabledPacks(File instanceDir) {
        List<String> out = new ArrayList<>();
        try {
            File f = new File(instanceDir, "options.txt");
            if (!f.isFile()) {
                return out;
            }
            String text = readText(f);
            if (text == null) {
                return out;
            }
            int i = text.indexOf("resourcePacks:");
            if (i < 0) {
                return out;
            }
            int lb = text.indexOf('[', i);
            int rb = text.indexOf(']', lb + 1);
            if (lb < 0 || rb < 0) {
                return out;
            }
            String body = text.substring(lb + 1, rb);
            // 形如 "file/xxx.zip","vanilla"
            for (String part : body.split(",")) {
                String s = part.trim();
                if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                    s = s.substring(1, s.length() - 1);
                }
                if (s.startsWith("file/")) {
                    s = s.substring(5);
                }
                if (!s.isEmpty() && !"vanilla".equals(s)) {
                    out.add(s);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "读 options.txt 失败: " + t);
        }
        return out;
    }

    /** 在启用列表里找这个包的位置；匹配时忽略扩展名差异（options.txt 里带 .zip）。 */
    private static int indexOfPack(List<String> enabled, String name, String kind) {
        for (int i = 0; i < enabled.size(); i++) {
            String e = enabled.get(i);
            if (e.equals(name) || e.equals(name + ".zip") || stripExt(e).equals(stripExt(name))) {
                return i;
            }
        }
        return -1;
    }

    private static String stripExt(String s) {
        int i = s.lastIndexOf('.');
        return i > 0 ? s.substring(0, i) : s;
    }

    // ------------------------------------------------------------------ IO 工具

    private static String readZipEntry(ZipFile zf, String entryName) {
        try {
            ZipEntry e = zf.getEntry(entryName);
            if (e == null) {
                return null;
            }
            InputStream in = zf.getInputStream(e);
            try {
                return readAll(in);
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

    private static String readText(File f) {
        try {
            InputStream in = new java.io.FileInputStream(f);
            try {
                return readAll(in);
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

    private static String readAll(InputStream in) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            int guard = 0;
            while ((line = r.readLine()) != null) {
                sb.append(line);
                if (++guard > 20000) {
                    break;
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 从 pack.mcmeta 里取 {@code pack.pack_format}。取不到返回 -1。 */
    private static int parsePackFormat(String json) {
        if (json == null || json.trim().isEmpty()) {
            return -1;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("pack") && root.get("pack").isJsonObject()) {
                JsonObject pack = root.getAsJsonObject("pack");
                if (pack.has("pack_format")) {
                    return pack.get("pack_format").getAsInt();
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static long dirSize(File dir) {
        long total = 0L;
        try {
            File[] children = dir.listFiles();
            if (children == null) {
                return 0L;
            }
            for (File c : children) {
                total += c.isDirectory() ? dirSize(c) : c.length();
            }
        } catch (Throwable ignored) {
        }
        return total;
    }
}
