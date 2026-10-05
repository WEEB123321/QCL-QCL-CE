package com.qcl.launcher.launcher.diagnostic;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.launch.RendererCompat;
import com.qcl.launcher.utils.Architecture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版新增：智能推荐（按**当前版本** + **本机设备**给建议）。
 *
 * <p><b>不做的事</b>：不联网、不看「别人都在用什么」、不猜。
 * 所有结论都由两类**本机可验证**的输入推出来：
 * <ol>
 *   <li><b>当前实例</b>：目录名 → MC 版本号、所选渲染器、分配的内存；</li>
 *   <li><b>本机</b>：物理内存总量、真实 ABI。</li>
 * </ol>
 * 每条建议都会把**依据的数值**一起写出来，用户能自己核对，而不是只丢一句「建议调小一点」。
 *
 * <p>结果直接复用「启动诊断」的 {@link StartupDoctor.Item} 结构，
 * 在那一页里和现有检查项排在一起展示。
 */
public final class DeviceAdvisor {

    private DeviceAdvisor() {
    }

    public static List<StartupDoctor.Item> run(Context context, MainActivity activity) {
        List<StartupDoctor.Item> out = new ArrayList<StartupDoctor.Item>();
        addRamAdvice(context, activity, out);
        addRendererAdvice(activity, out);
        addAbiAdvice(context, out);
        return out;
    }

    // ------------------------------------------------------------------ 内存

    private static void addRamAdvice(Context context, MainActivity activity,
                                     List<StartupDoctor.Item> out) {
        try {
            long totalBytes = 0;
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                totalBytes = mi.totalMem;
            }
            long totalMb = totalBytes / 1024 / 1024;
            if (totalMb <= 0) {
                return;
            }
            if (activity.privateGameSetting == null || activity.privateGameSetting.ramSetting == null) {
                return;
            }
            int maxRam = activity.privateGameSetting.ramSetting.maxRam;
            boolean auto = activity.privateGameSetting.ramSetting.autoRam;

            // 经验线：分到物理内存的一半以上，进游戏时很容易被系统 OOM 杀掉
            long safeLine = totalMb / 2;
            String detail = "本机 " + totalMb + " MB，当前分配 " + maxRam + " MB"
                    + (auto ? "（自动）" : "");
            if (maxRam > safeLine) {
                out.add(new StartupDoctor.Item("内存分配建议", false, detail)
                        .advice("已经超过物理内存的一半（" + safeLine + " MB）。"
                                + "分配过多不会更快，反而会在进游戏/切维度时被系统直接杀掉。"
                                + "建议降到 " + safeLine + " MB 以内。"));
            } else {
                out.add(new StartupDoctor.Item("内存分配建议", true, detail)
                        .advice(""));
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ 渲染器

    private static void addRendererAdvice(MainActivity activity, List<StartupDoctor.Item> out) {
        try {
            if (activity.publicGameSetting == null) {
                return;
            }
            String versionPath = activity.publicGameSetting.currentVersion;
            if (versionPath == null || versionPath.isEmpty()) {
                return;
            }
            String mcVer = new File(versionPath).getName();
            String renderer = null;
            if (activity.privateGameSetting != null
                    && activity.privateGameSetting.pojavLauncherSetting != null) {
                renderer = activity.privateGameSetting.pojavLauncherSetting.renderer;
            }
            if (renderer == null || renderer.isEmpty()) {
                renderer = RendererCompat.defaultRendererId();
            }
            RendererCompat.Info info = RendererCompat.find(renderer);
            String rendererName = info == null ? renderer : info.displayName;

            boolean ok = RendererCompat.supports(renderer, mcVer);
            if (ok) {
                out.add(new StartupDoctor.Item("渲染器与版本匹配", true,
                        rendererName + " · 实例 " + mcVer));
            } else {
                out.add(new StartupDoctor.Item("渲染器与版本匹配", false,
                        rendererName + " · 实例 " + mcVer + "（超出该渲染器的支持范围）")
                        .advice(suggestRenderer(mcVer)));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 从全部渲染器里挑出支持这个 MC 版本的，列给用户。 */
    private static String suggestRenderer(String mcVer) {
        StringBuilder sb = new StringBuilder("这个版本可以用的渲染器：");
        int n = 0;
        try {
            for (RendererCompat.Info info : RendererCompat.ALL) {
                if (info == null) {
                    continue;
                }
                if (RendererCompat.supports(info.id, mcVer)) {
                    sb.append(n++ == 0 ? "\n· " : "\n· ").append(info.displayName);
                }
            }
        } catch (Throwable ignored) {
        }
        if (n == 0) {
            return "没有找到声明支持 " + mcVer + " 的渲染器。"
                    + "可以先试试默认的 " + RendererCompat.defaultRendererId()
                    + "，或到「版本设置 → 渲染器」里逐个试。";
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 架构

    private static void addAbiAdvice(Context context, List<StartupDoctor.Item> out) {
        try {
            String first = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                    ? Build.SUPPORTED_ABIS[0] : "?";
            int runtimeArch = Architecture.getRuntimeArchitecture();
            String runtimeName = Architecture.archAsString(runtimeArch);
            String detail = "设备首选 ABI " + first + "，启动器按 " + runtimeName + " 工作";
            // ★ 只在「首选 ABI 与启动器判断出的架构明显对不上」时才提醒 ——
            //   模拟器（如 MuMu）会把自己报成 arm64 却在 x86_64 上跑，这种情况值得让用户知道。
            boolean mismatch = false;
            if ("arm64-v8a".equals(first) && runtimeName != null && runtimeName.contains("x86")) {
                mismatch = true;
            } else if ("x86_64".equals(first) && runtimeName != null && runtimeName.contains("arm")) {
                mismatch = true;
            }
            if (mismatch) {
                out.add(new StartupDoctor.Item("运行架构", false, detail)
                        .advice("设备和启动器选出的架构不一致 —— 常见于模拟器（把首选 ABI 报成 arm64 "
                                + "但实际跑在 x86_64 上）。这会让游戏用错 JRE/原生库而进不去。"
                                + "如果是模拟器，请换用对应架构的安装包。"));
            } else {
                out.add(new StartupDoctor.Item("运行架构", true, detail));
            }
        } catch (Throwable ignored) {
        }
    }
}
