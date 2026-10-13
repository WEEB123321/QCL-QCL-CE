package com.qcl.launcher.launcher.diagnostic;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.qcl.launcher.manifest.AppManifest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * ★★★ 社区版：输入链路自检。
 *
 * <p><b>为什么需要它</b>：用户报「游戏能启动、画面正常，但**一个东西都点不了**」✗。
 * 这类问题的根因**从 Java 侧看不出来** —— 光看代码只能猜 ✗
 * （我已经因此猜错一次：以为是新版用 SDL 输入后端 ✗ 实际不是）。
 *
 * <p>而 `PojavLauncher/src/main/jni/Android.mk` 里留着一条**与症状完全吻合**的历史记录：
 * <blockquote>
 * 旧桥与新桥导出**完全相同的 17 个 JNI 符号**，ART 按「已加载库」顺序解析，先加载者胜 ✗
 * —— 历史上已因此让高版本输入全落到 {@code isInputReady} 恒 0 的旧桥，
 * 表现为「**画面正常但点击毫无反应**」✗
 * </blockquote>
 *
 * <p><b>所以这个类做三件事</b>（都是「能拿到证据」的）：
 * <ol>
 *   <li><b>读 {@code /proc/self/maps}</b> —— 列出进程**实际加载**的输入相关 .so ✓
 *       （这是唯一能证明「加载到的是哪个桥」的办法 ✓ 看文件在不在没用 ✗）</li>
 *   <li><b>查 nativeLibraryDir</b> —— 磁盘上有哪些输入库 ✓ 和 maps 对照 ✓</li>
 *   <li><b>报控制布局设置</b> —— {@code controlLayout} 为空 = 没有可点的控件 ✗</li>
 * </ol>
 *
 * <p>全部只读 ✓ 不改任何状态 ✓ 不需要 root ✓（{@code /proc/self/maps} 是进程自己的 ✓）
 */
public final class InputDiagnostics {

    private InputDiagnostics() {
    }

    /** 输入相关的 .so 关键字（用来从 maps 里筛）。 */
    private static final String[] KEYS = {
            "pojavexec", "glfw", "sdl", "bytehook", "xhook", "lwjgl", "gl_bridge", "osm_bridge"};

    /** 生成一段可读的诊断文本。 */
    public static String describe(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("----- 输入链路自检 -----\n");

        // ① 进程实际加载了哪些输入相关 .so（关键证据 ✓）
        sb.append("[已加载] /proc/self/maps 里出现的输入相关库：\n");
        int loaded = 0;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            while ((line = r.readLine()) != null) {
                int slash = line.lastIndexOf('/');
                if (slash < 0) {
                    continue;
                }
                String path = line.substring(slash + 1).trim();
                if (!path.endsWith(".so")) {
                    continue;
                }
                String lower = path.toLowerCase(java.util.Locale.ROOT);
                for (String k : KEYS) {
                    if (lower.contains(k)) {
                        seen.add(path);
                        break;
                    }
                }
            }
            for (String s : seen) {
                sb.append("    ").append(s).append('\n');
                loaded++;
            }
        } catch (Throwable t) {
            sb.append("    ✗ 读 /proc/self/maps 失败：").append(t).append('\n');
        }
        if (loaded == 0) {
            sb.append("    ✗✗ 一个输入相关库都没加载！—— 这就是「点不了」的直接原因 ✗\n");
        }

        // ② 磁盘上有哪些输入库（和上面 ① 对照 ✓）
        sb.append("[磁盘上] nativeLibraryDir 里的输入库：\n");
        try {
            ApplicationInfo ai = ctx.getApplicationInfo();
            File dir = new File(ai.nativeLibraryDir);
            sb.append("    目录：").append(dir.getAbsolutePath()).append('\n');
            File[] fs = dir.listFiles();
            int n = 0;
            if (fs != null) {
                for (File f : fs) {
                    if (f == null) {
                        continue;
                    }
                    String lower = f.getName().toLowerCase(java.util.Locale.ROOT);
                    for (String k : KEYS) {
                        if (lower.contains(k)) {
                            sb.append("    ").append(f.getName())
                                    .append("  (").append(f.length() / 1024).append(" KB)\n");
                            n++;
                            break;
                        }
                    }
                }
            }
            if (n == 0) {
                sb.append("    ✗✗ 磁盘上没有输入相关库 —— 打包时就漏了 ✗\n");
            }
        } catch (Throwable t) {
            sb.append("    ✗ 查 nativeLibraryDir 失败：").append(t).append('\n');
        }

        // ③ 控制布局设置（为空 = 没有可点的控件 ✗）
        sb.append("[控制布局] \n");
        try {
            String cl = null;
            try {
                // 走反射读，避免直接依赖具体字段名（字段名在版本间可能变）
                Object st = null;
                Class<?> c = Class.forName("com.qcl.launcher.launcher.MainActivity");
                // 这里拿不到实例，只能读文件层面的线索 —— 退而求其次报目录内容 ✓
                cl = null;
            } catch (Throwable ignored) {
            }
            File cd = new File(AppManifest.CONTROLLER_DIR);
            if (cd.isDirectory()) {
                File[] ps = cd.listFiles();
                int n = 0;
                if (ps != null) {
                    for (File p : ps) {
                        if (p != null && p.isDirectory()) {
                            sb.append("    布局：").append(p.getName()).append('\n');
                            n++;
                        }
                    }
                }
                if (n == 0) {
                    sb.append("    ✗ 控制布局目录是空的 —— 游戏里没有可点的控件 ✗\n");
                }
            } else {
                sb.append("    ✗ 控制布局目录不存在：").append(cd.getAbsolutePath()).append('\n');
            }
            sb.append("    目录：").append(cd.getAbsolutePath()).append('\n');
        } catch (Throwable t) {
            sb.append("    ✗ 查控制布局失败：").append(t).append('\n');
        }

        // ④ 结论提示（把「怎么读这段」写清楚 ✓）
        sb.append("[怎么读这段]\n");
        sb.append("    · ① 里**没有** libpojavexec.so → 输入桥没加载 ✗ 看 ② 是不是磁盘上就没有\n");
        sb.append("    · ① 里有**多个**输入桥 → 可能撞符号（注释里说的「先加载者胜」）✗\n");
        sb.append("    · ① ② 都正常但游戏里点不了 → 看 ③ 控制布局是不是空的 ✗\n");
        sb.append('\n');
        return sb.toString();
    }
}
