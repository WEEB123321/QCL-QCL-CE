package com.qcl.launcher.launcher.instance;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ★★★ 社区版新增：实例之间的搬运（模组 / 存档 / 资源包 / 光影 / 配置）。
 *
 * <p>一次覆盖用户清单里的四项：#7 模组一键搬家、#8 存档一键迁移、#9 资源包一键应用、
 * #10 配置文件一键同步 —— 它们本质是同一件事：<b>把某几类文件从实例 A 搬到实例 B</b>，
 * 区别只在「搬哪些」。所以做成一个对话框 + 一组勾选，而不是四个几乎一样的界面。
 *
 * <h3>★ 兼容性检查（#7 的「自动检测兼容性 / 冲突自动提示」）</h3>
 * 只做**能从文件里读出来**的判断，不猜：
 * <ul>
 *   <li><b>模组</b>：读 jar 里的 {@code fabric.mod.json} / {@code META-INF/mods.toml} /
 *       {@code mcmod.info} / {@code litemod.json} 判断加载器，和目标实例的加载器比对；</li>
 *   <li><b>资源包</b>：读 {@code pack.mcmeta} 的 {@code pack_format}，和目标实例的 MC 版本比对；</li>
 *   <li><b>同名冲突</b>：目标目录已有同名文件 —— 这是最容易把别人东西覆盖掉的场景，必须逐个标出来。</li>
 * </ul>
 * 读不出来的（远古 ModLoader 模组就是普通 jar）**只标「无法识别」不报警** —— 那是正常的。
 *
 * <h3>★ 先出计划、再执行</h3>
 * {@link #plan} 只读不写；用户看过冲突与警告列表、点确认之后才调 {@link #execute}。
 * 绝不「点了按钮直接开始覆盖」。
 */
public final class InstanceTransferHelper {

    private static final String TAG = "QCLTransfer";

    public enum Kind {
        MODS("mods"),
        SAVES("saves"),
        RESOURCEPACKS("resourcepacks"),
        SHADERPACKS("shaderpacks"),
        CONFIG("config");

        public final String dir;

        Kind(String dir) {
            this.dir = dir;
        }
    }

    /** 一条待搬运的条目。 */
    public static class Entry {
        public Kind kind;
        public String name;
        public long size;
        /** 目标已有同名（会被覆盖） */
        public boolean conflict;
        /** 兼容性警告；空 = 没发现问题 */
        public String warning = "";
    }

    public static class Plan {
        public final List<Entry> entries = new ArrayList<Entry>();
        public final Set<Kind> kinds = new LinkedHashSet<Kind>();
        public long totalBytes;
        public int conflicts;
        public int warnings;

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    public interface Callback {
        void onProgress(int done, int total, String name);

        void onFinish(int copied, int failed, String error);
    }

    private InstanceTransferHelper() {
    }

    // ------------------------------------------------------------------ 计划

    /** ★ 只读：扫描源实例、比对目标实例，产出一份「将要做什么」的清单。 */
    public static Plan plan(File srcDir, File dstDir, Set<Kind> kinds) {
        Plan p = new Plan();
        if (srcDir == null || dstDir == null || !srcDir.isDirectory() || !dstDir.isDirectory()) {
            return p;
        }
        String srcLoader = guessLoader(srcDir.getName());
        String dstLoader = guessLoader(dstDir.getName());
        String dstMcVer = guessVersion(dstDir.getName());

        for (Kind k : kinds) {
            p.kinds.add(k);
            File srcSub = new File(srcDir, k.dir);
            File dstSub = new File(dstDir, k.dir);
            if (!srcSub.isDirectory()) {
                continue;
            }
            File[] fs = srcSub.listFiles();
            if (fs == null) {
                continue;
            }
            for (File f : fs) {
                Entry e = new Entry();
                e.kind = k;
                e.name = f.getName();
                e.size = InstanceSpaceHelper.size(f);
                e.conflict = new File(dstSub, f.getName()).exists();
                if (e.conflict) {
                    p.conflicts++;
                }
                e.warning = checkCompat(k, f, dstLoader, dstMcVer, srcLoader);
                if (!e.warning.isEmpty()) {
                    p.warnings++;
                }
                p.entries.add(e);
                p.totalBytes += e.size;
            }
        }
        // 配置文件（options.txt 这类单文件）单独补上 —— 它们在实例根目录，不在 config/ 里
        if (kinds.contains(Kind.CONFIG)) {
            for (String n : new String[]{"options.txt", "optionsof.txt", "servers.dat", "usercache.json"}) {
                File f = new File(srcDir, n);
                if (!f.isFile()) {
                    continue;
                }
                Entry e = new Entry();
                e.kind = Kind.CONFIG;
                e.name = n;
                e.size = f.length();
                e.conflict = new File(dstDir, n).exists();
                if (e.conflict) {
                    p.conflicts++;
                }
                p.entries.add(e);
                p.totalBytes += e.size;
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ 兼容性

    private static String checkCompat(Kind k, File f, String dstLoader, String dstMcVer, String srcLoader) {
        try {
            if (k == Kind.MODS) {
                String loader = modLoaderOf(f);
                if ("unknown".equals(loader)) {
                    // ★ 远古 ModLoader 模组本来就没有元数据，这是正常的，不报警
                    return "";
                }
                if (!dstLoader.isEmpty() && !compatibleLoader(loader, dstLoader)) {
                    return "这是 " + loader + " 模组，但目标实例是 " + dstLoader;
                }
                if (!srcLoader.isEmpty() && !dstLoader.isEmpty()
                        && !compatibleLoader(srcLoader, dstLoader)
                        && compatibleLoader(loader, srcLoader)) {
                    return "与目标实例的加载器不同（目标 " + dstLoader + "）";
                }
                return "";
            }
            if (k == Kind.RESOURCEPACKS || k == Kind.SHADERPACKS) {
                Integer fmt = packFormatOf(f);
                if (fmt == null || dstMcVer.isEmpty()) {
                    return "";
                }
                int expect = expectedPackFormat(dstMcVer);
                if (expect > 0 && fmt != expect) {
                    return "资源包格式 " + fmt + "，目标版本 " + dstMcVer + " 需要 " + expect;
                }
                return "";
            }
            if (k == Kind.SAVES) {
                // 存档只提示一句：跨大版本进入会触发游戏自己的升级，有风险但不是错误
                if (!dstMcVer.isEmpty()) {
                    return "跨版本打开可能触发游戏升级存档（不可逆），建议先备份";
                }
                return "";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 从模组 jar 里读出加载器类型。读不出返回 unknown（不报警）。 */
    private static String modLoaderOf(File jar) {
        ZipFile zf = null;
        try {
            if (!jar.isFile() || !jar.getName().toLowerCase().endsWith(".jar")) {
                return "unknown";
            }
            zf = new ZipFile(jar);
            if (zf.getEntry("fabric.mod.json") != null) {
                return "Fabric";
            }
            if (zf.getEntry("META-INF/mods.toml") != null) {
                return "Forge";
            }
            if (zf.getEntry("mcmod.info") != null) {
                return "Forge";
            }
            if (zf.getEntry("litemod.json") != null) {
                return "LiteLoader";
            }
            return "unknown";
        } catch (Throwable t) {
            return "unknown";
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Fabric 与 Quilt 互通；Forge 与 NeoForge 不通。 */
    private static boolean compatibleLoader(String a, String b) {
        if (a == null || b == null) {
            return true;
        }
        if (a.equals(b)) {
            return true;
        }
        return ("Fabric".equals(a) && "Quilt".equals(b)) || ("Quilt".equals(a) && "Fabric".equals(b));
    }

    /** 读资源包 zip / 目录里的 pack.mcmeta → pack_format。 */
    private static Integer packFormatOf(File f) {
        ZipFile zf = null;
        try {
            if (f.isFile()) {
                zf = new ZipFile(f);
                ZipEntry e = zf.getEntry("pack.mcmeta");
                if (e == null) {
                    return null;
                }
                InputStream in = zf.getInputStream(e);
                try {
                    return parsePackFormat(readAll(in));
                } finally {
                    in.close();
                }
            }
            File meta = new File(f, "pack.mcmeta");
            if (meta.isFile()) {
                FileInputStream in = new FileInputStream(meta);
                try {
                    return parsePackFormat(readAll(in));
                } finally {
                    in.close();
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static String readAll(InputStream in) {
        try {
            byte[] b = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            int guard = 0;
            while ((n = in.read(b)) > 0 && guard++ < 64) {
                sb.append(new String(b, 0, n, "UTF-8"));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** pack.mcmeta 里 pack_format 可能出现在 pack 段里；用正则抠，不引 JSON 解析。 */
    private static Integer parsePackFormat(String json) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"pack_format\"\\s*:\\s*(\\d+)").matcher(json);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** MC 版本 → 资源包 pack_format 的粗略对照（只覆盖主流版本段）。 */
    private static int expectedPackFormat(String mcVer) {
        try {
            String v = mcVer.toLowerCase();
            // 远古版本（alpha/beta/1.6 之前）用旧格式，这里返回 0 表示「不判」
            if (!v.startsWith("1.") && !v.startsWith("2")) {
                return 0;
            }
            String[] p = v.replaceAll("[^0-9.]", "").split("\\.");
            if (p.length < 2) {
                return 0;
            }
            int minor = Integer.parseInt(p[1]);
            int patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
            if (minor <= 6) return 1;
            if (minor <= 8) return 1;
            if (minor == 9 || minor == 10) return 2;
            if (minor <= 12) return 3;
            if (minor <= 14) return 4;
            if (minor == 15) return 5;
            if (minor == 16) return patch >= 2 ? 6 : 5;
            if (minor == 17) return 7;
            if (minor == 18) return 8;
            if (minor == 19) {
                if (patch >= 4) return 13;
                if (patch == 3) return 12;
                return 9;
            }
            if (minor == 20) {
                if (patch >= 5) return 32;
                if (patch >= 3) return 22;
                if (patch == 2) return 18;
                return 15;
            }
            if (minor == 21) {
                if (patch >= 4) return 46;
                if (patch >= 2) return 42;
                return 34;
            }
            return 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ 执行

    /** 按计划执行。{@code move} = 搬运后删源；否则只复制。 */
    public static void execute(Plan plan, File srcDir, File dstDir, final boolean move,
                               final Callback cb) {
        int copied = 0, failed = 0;
        String error = null;
        int total = plan.entries.size();
        int done = 0;
        for (Entry e : plan.entries) {
            try {
                if (cb != null) {
                    cb.onProgress(done, total, e.name);
                }
                File src = e.kind == Kind.CONFIG
                        ? new File(srcDir, e.name)
                        : new File(new File(srcDir, e.kind.dir), e.name);
                File dst = e.kind == Kind.CONFIG
                        ? new File(dstDir, e.name)
                        : new File(new File(dstDir, e.kind.dir), e.name);
                if (!src.exists()) {
                    done++;
                    continue;
                }
                File dstParent = dst.getParentFile();
                if (dstParent != null && !dstParent.exists()) {
                    dstParent.mkdirs();
                }
                boolean ok;
                if (src.isDirectory()) {
                    ok = copyDir(src, dst);
                    if (ok && move) {
                        deleteRecursive(src);
                    }
                } else {
                    ok = copyFile(src, dst);
                    if (ok && move) {
                        src.delete();
                    }
                }
                if (ok) {
                    copied++;
                } else {
                    failed++;
                }
            } catch (Throwable t) {
                failed++;
                error = String.valueOf(t);
                Log.w(TAG, "搬运失败: " + e.name, t);
            }
            done++;
        }
        if (cb != null) {
            cb.onFinish(copied, failed, error);
        }
    }

    private static boolean copyFile(File src, File dst) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
            }
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean copyDir(File src, File dst) {
        try {
            if (!dst.exists() && !dst.mkdirs()) {
                return false;
            }
            File[] fs = src.listFiles();
            if (fs == null) {
                return true;
            }
            boolean ok = true;
            for (File f : fs) {
                if (f.isDirectory()) {
                    ok &= copyDir(f, new File(dst, f.getName()));
                } else {
                    ok &= copyFile(f, new File(dst, f.getName()));
                }
            }
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void deleteRecursive(File f) {
        try {
            if (f.isDirectory()) {
                File[] fs = f.listFiles();
                if (fs != null) {
                    for (File c : fs) {
                        deleteRecursive(c);
                    }
                }
            }
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ 版本/加载器推断

    /** 与 ModScanner / CompatUI 保持同一套推断：只看目录名。 */
    private static String guessLoader(String name) {
        if (name == null) {
            return "";
        }
        String n = name.toLowerCase();
        if (n.contains("neoforge")) return "NeoForge";
        if (n.contains("forge")) return "Forge";
        if (n.contains("quilt")) return "Quilt";
        if (n.contains("fabric")) return "Fabric";
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
