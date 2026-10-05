package com.qcl.launcher.launcher.backup;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ★★★ 社区版新增：实例快照对比（「两个实例 / 快照之间差了什么」）。
 *
 * <p><b>解决什么问题</b>：装了备份之后，玩家最常问的是「我上次改了什么？」
 * —— 备份能让他退回去，但退回去之前他得知道差异在哪。这里给出<b>逐文件的增删改清单</b>，
 * 覆盖模组 / 配置 / 整合包核心文件这几类最常被改动的目录。
 *
 * <p><b>★ 判据是「大小 + 最后修改时间」，不是内容哈希</b>：对实例目录（动辄几万文件、
 * 数 GB）做全量哈希要读完整盘，在低端手机上不可接受。大小 + mtime 对「我改过这个文件」
 * 的判定已经足够准，而且快几个数量级。代价是「改了内容但大小和 mtime 都没变」的极端情况
 * 会漏 —— 界面上如实标注判据，不假装是逐字节比对。
 *
 * <p><b>★ 只读</b>：本类不修改任何文件，只遍历与比较。
 */
public final class InstanceDiffHelper {

    private static final String TAG = "QCLDiff";

    /** 参与对比的目录（相对实例根）。都是「玩家会改、改了要能看出差异」的东西。 */
    private static final String[] TRACKED_DIRS = {
            "mods", "config", "resourcepacks", "shaderpacks", "defaultconfigs"
    };

    /** 参与对比的单个文件（相对实例根）。 */
    private static final String[] TRACKED_FILES = {
            "options.txt", "servers.dat", "pack.mcmeta"
    };

    private InstanceDiffHelper() {
    }

    /** 差异结果。 */
    public static class Diff {
        /** 仅在 A 中存在（A 有 B 没有） */
        public final List<String> onlyInA = new ArrayList<>();
        /** 仅在 B 中存在 */
        public final List<String> onlyInB = new ArrayList<>();
        /** 两边都有但大小 / mtime 不同 */
        public final List<String> changed = new ArrayList<>();

        public boolean isEmpty() {
            return onlyInA.isEmpty() && onlyInB.isEmpty() && changed.isEmpty();
        }

        public int total() {
            return onlyInA.size() + onlyInB.size() + changed.size();
        }
    }

    /**
     * 比较两个实例目录。
     *
     * @param a 基准（通常是备份快照）
     * @param b 对照（通常是当前实例）
     */
    public static Diff diff(File a, File b) {
        Diff d = new Diff();
        try {
            for (String dir : TRACKED_DIRS) {
                Map<String, long[]> ma = scan(new File(a, dir));
                Map<String, long[]> mb = scan(new File(b, dir));
                compareMaps(dir, ma, mb, d);
            }
            for (String f : TRACKED_FILES) {
                compareFile(f, new File(a, f), new File(b, f), d);
            }
        } catch (Throwable t) {
            Log.w(TAG, "对比失败: " + t);
        }
        Collections.sort(d.onlyInA);
        Collections.sort(d.onlyInB);
        Collections.sort(d.changed);
        return d;
    }

    /** 递归扫描目录，返回「相对路径 → {size, lastModified}」。路径统一用 / 分隔。 */
    private static Map<String, long[]> scan(File root) {
        Map<String, long[]> out = new LinkedHashMap<>();
        try {
            if (root == null || !root.exists()) {
                return out;
            }
            if (root.isFile()) {
                out.put(root.getName(), new long[]{root.length(), root.lastModified()});
                return out;
            }
            collect(root, "", out, 0);
        } catch (Throwable t) {
            Log.w(TAG, "扫描失败: " + root, t);
        }
        return out;
    }

    private static void collect(File dir, String prefix, Map<String, long[]> out, int depth) {
        // ★ 深度上限：防止符号链接成环或异常目录结构把递归拖死
        if (depth > 12) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            String path = prefix.isEmpty() ? c.getName() : prefix + "/" + c.getName();
            if (c.isDirectory()) {
                collect(c, path, out, depth + 1);
            } else {
                out.put(path, new long[]{c.length(), c.lastModified()});
            }
        }
    }

    private static void compareMaps(String dir, Map<String, long[]> a, Map<String, long[]> b, Diff d) {
        for (Map.Entry<String, long[]> e : a.entrySet()) {
            long[] vb = b.get(e.getKey());
            if (vb == null) {
                d.onlyInA.add(dir + "/" + e.getKey());
            } else if (vb[0] != e.getValue()[0] || vb[1] != e.getValue()[1]) {
                d.changed.add(dir + "/" + e.getKey());
            }
        }
        for (String k : b.keySet()) {
            if (!a.containsKey(k)) {
                d.onlyInB.add(dir + "/" + k);
            }
        }
    }

    private static void compareFile(String name, File fa, File fb, Diff d) {
        boolean ea = fa != null && fa.isFile();
        boolean eb = fb != null && fb.isFile();
        if (ea && !eb) {
            d.onlyInA.add(name);
        } else if (!ea && eb) {
            d.onlyInB.add(name);
        } else if (ea && eb) {
            if (fa.length() != fb.length() || fa.lastModified() != fb.lastModified()) {
                d.changed.add(name);
            }
        }
    }

    /**
     * 把差异渲染成可读文本。
     *
     * @param aLabel 基准名（例如备份的实例名）
     * @param bLabel 对照名（例如「当前实例」）
     * @param limit  每个分区最多列多少条（避免几万个文件把对话框撑爆）
     */
    public static String render(Diff d, String aLabel, String bLabel, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append(aLabel).append("  →  ").append(bLabel).append('\n');
        if (d.isEmpty()) {
            sb.append("(无差异)\n");
            return sb.toString();
        }
        section(sb, "仅在「" + aLabel + "」中存在", d.onlyInA, limit);
        section(sb, "仅在「" + bLabel + "」中存在", d.onlyInB, limit);
        section(sb, "两边都有但已改动", d.changed, limit);
        sb.append("\n判据：文件大小 + 最后修改时间（不做全量内容哈希，见类注释）");
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, List<String> items, int limit) {
        if (items.isEmpty()) {
            return;
        }
        sb.append("\n【").append(title).append("】共 ").append(items.size()).append(" 项\n");
        int n = Math.min(items.size(), Math.max(1, limit));
        for (int i = 0; i < n; i++) {
            sb.append("  · ").append(items.get(i)).append('\n');
        }
        if (items.size() > n) {
            sb.append("  … 其余 ").append(items.size() - n).append(" 项未列出\n");
        }
    }
}
