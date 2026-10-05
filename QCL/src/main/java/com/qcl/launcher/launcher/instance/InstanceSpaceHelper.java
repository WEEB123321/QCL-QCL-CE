package com.qcl.launcher.launcher.instance;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ★★★ 社区版新增：实例占用空间分析。
 *
 * <p>把实例目录按**用途**拆开统计（模组 / 存档 / 资源包 / 光影 / 配置 / 日志 / 其它），
 * 而不是只给一个「这个实例占 2.3 GB」—— 后者没法指导任何操作。
 *
 * <p><b>★ 清理只碰「确定是垃圾」的东西</b>：日志、崩溃报告、下载残留（{@code .tmp} / {@code .part} /
 * {@code .download}）。<b>绝不</b>把模组/存档/资源包列为「可清理」——
 * 那些是用户自己放进去的，删了就是数据丢失。宁可少清，不可错删。
 */
public final class InstanceSpaceHelper {

    private InstanceSpaceHelper() {
    }

    /** 一个分类的统计结果。 */
    public static class Part {
        public final String label;
        public final long bytes;
        public final int files;

        public Part(String label, long bytes, int files) {
            this.label = label;
            this.bytes = bytes;
            this.files = files;
        }
    }

    /** 分析结果。 */
    public static class Report {
        public final List<Part> parts = new ArrayList<Part>();
        /** 可安全清理的文件（日志 / 崩溃报告 / 下载残留） */
        public final List<File> junk = new ArrayList<File>();
        public long junkBytes;
        public long totalBytes;

        public String humanTotal() {
            return human(totalBytes);
        }

        public String humanJunk() {
            return human(junkBytes);
        }
    }

    /** 这些子目录按「用途」单独统计（存在才统计）。 */
    private static final String[][] CATEGORIES = {
            {"mods", "模组"},
            {"saves", "存档"},
            {"resourcepacks", "资源包"},
            {"shaderpacks", "光影"},
            {"config", "配置"},
            {"logs", "日志"},
            {"crash-reports", "崩溃报告"},
            {"screenshots", "截图"},
            {"datapacks", "数据包"},
    };

    public static Report analyze(File instanceDir) {
        Report r = new Report();
        if (instanceDir == null || !instanceDir.isDirectory()) {
            return r;
        }
        long counted = 0;
        for (String[] c : CATEGORIES) {
            File d = new File(instanceDir, c[0]);
            if (!d.isDirectory()) {
                continue;
            }
            long b = size(d);
            int n = count(d);
            counted += b;
            r.parts.add(new Part(c[1], b, n));
        }
        r.totalBytes = size(instanceDir);
        // 「其它」= 总量减去已分类的部分（至少为 0）
        long other = Math.max(0, r.totalBytes - counted);
        if (other > 0) {
            r.parts.add(new Part("其它（版本文件 / 库 / 缓存等）", other, -1));
        }

        collectJunk(instanceDir, r, 0);
        return r;
    }

    /**
     * 收集可安全清理的文件。
     *
     * @param depth 递归深度，限制在 3 层以内 —— 清理只需要覆盖实例根 + 几个一级子目录，
     *              全盘递归在超大实例上会很慢，而且没有必要。
     */
    private static void collectJunk(File dir, Report r, int depth) {
        if (depth > 3) {
            return;
        }
        File[] fs = dir.listFiles();
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            try {
                if (f.isDirectory()) {
                    String n = f.getName().toLowerCase();
                    // 整目录都是垃圾的：日志、崩溃报告
                    if ("logs".equals(n) || "crash-reports".equals(n)) {
                        long b = size(f);
                        r.junk.add(f);
                        r.junkBytes += b;
                        continue;
                    }
                    collectJunk(f, r, depth + 1);
                } else {
                    String n = f.getName().toLowerCase();
                    if (n.endsWith(".tmp") || n.endsWith(".part")
                            || n.endsWith(".download") || n.endsWith(".qcltmp")) {
                        r.junk.add(f);
                        r.junkBytes += f.length();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static long size(File f) {
        try {
            if (f == null) {
                return 0;
            }
            if (!f.isDirectory()) {
                return f.length();
            }
            File[] fs = f.listFiles();
            if (fs == null) {
                return 0;
            }
            long t = 0;
            for (File c : fs) {
                t += size(c);
            }
            return t;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int count(File f) {
        try {
            if (f == null) {
                return 0;
            }
            if (!f.isDirectory()) {
                return 1;
            }
            File[] fs = f.listFiles();
            if (fs == null) {
                return 0;
            }
            int n = 0;
            for (File c : fs) {
                n += count(c);
            }
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static String human(long b) {
        if (b < 1024) {
            return b + " B";
        }
        if (b < 1024L * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", b / 1024.0);
        }
        if (b < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", b / 1024.0 / 1024.0);
        }
        return String.format(java.util.Locale.US, "%.2f GB", b / 1024.0 / 1024.0 / 1024.0);
    }

    /** 供 UI 直接展示的「标签 → 大小」有序表。 */
    public static Map<String, String> asDisplayMap(Report r) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (Part p : r.parts) {
            m.put(p.label, p.files < 0 ? human(p.bytes) : human(p.bytes) + " · " + p.files + " 个文件");
        }
        return m;
    }
}
