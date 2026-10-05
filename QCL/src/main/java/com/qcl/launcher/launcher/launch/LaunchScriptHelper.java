package com.qcl.launcher.launcher.launch;

import android.content.Context;
import android.util.Log;

import com.qcl.launcher.launcher.setting.launcher.LauncherSetting;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * ★★★ 社区版新增：启动前 / 退出后自定义脚本。
 *
 * <p><b>为什么值得做</b>：整合包作者常需要在启动前后做点事（同步配置、清日志、拉起辅助进程），
 * 桌面端启动器（HMCL / MultiMC）都有这个能力，而移动端几乎没有 —— 这是差异化的一小块。
 *
 * <p><b>★ 怎么执行</b>：交给系统的 {@code sh -c}，工作目录设为<b>当前实例目录</b>
 * （这样脚本里可以直接写相对路径，例如 {@code ./config}）。
 * 不自己解析命令行 —— 引号 / 管道 / 重定向这些交给 shell 处理才是符合直觉的。
 *
 * <p><b>★ 安全边界</b>：
 * <ul>
 *   <li>脚本<b>由用户自己填写</b>，不存在远程下发；</li>
 *   <li>有<b>超时</b>（默认 60 秒），超时强杀，绝不让一个卡住的脚本把启动流程拖死；</li>
 *   <li>输出写进日志（tag {@code QCLScript}），用户能在日志中心看到执行结果；</li>
 *   <li>任何异常都被吞掉 —— 脚本失败不该影响游戏启动。</li>
 * </ul>
 *
 * <p><b>★ 诚实说明</b>：Android 上没有完整的 Linux 用户态，脚本能用的命令取决于系统自带
 * （{@code sh} 一定有，{@code busybox} 不一定）。界面上会提示这一点，不假装它等同于桌面。
 */
public final class LaunchScriptHelper {

    private static final String TAG = "QCLScript";

    /** 脚本执行超时（毫秒）。超时强杀，避免卡死启动流程。 */
    private static final long TIMEOUT_MS = 60_000L;

    private LaunchScriptHelper() {
    }

    /** 执行「启动前」脚本（若已配置）。 */
    public static void runPreLaunch(Context context, LauncherSetting setting, String gameDir) {
        try {
            if (setting == null) {
                return;
            }
            run(setting.preLaunchScript, gameDir, "pre-launch");
        } catch (Throwable t) {
            Log.w(TAG, "启动前脚本失败: " + t);
        }
    }

    /** 执行「退出后」脚本（若已配置）。 */
    public static void runPostExit(Context context, LauncherSetting setting, String gameDir) {
        try {
            if (setting == null) {
                return;
            }
            run(setting.postExitScript, gameDir, "post-exit");
        } catch (Throwable t) {
            Log.w(TAG, "退出后脚本失败: " + t);
        }
    }

    /**
     * 同步执行一段脚本。调用方必须保证在<b>后台线程</b>（会阻塞到脚本结束或超时）。
     */
    public static void run(String script, String gameDir, String phase) {
        if (script == null || script.trim().isEmpty()) {
            return;
        }
        Process proc = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", script);
            if (gameDir != null && !gameDir.isEmpty()) {
                File dir = new File(gameDir);
                if (dir.isDirectory()) {
                    pb.directory(dir);
                }
            }
            pb.redirectErrorStream(true);
            proc = pb.start();
            // 读走输出，否则管道写满会让脚本阻塞（经典陷阱）
            BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                Log.i(TAG, "[" + phase + "] " + line);
            }
            boolean finished = proc.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                Log.w(TAG, "[" + phase + "] 脚本超时，已强制结束");
                proc.destroy();
            } else {
                Log.i(TAG, "[" + phase + "] 脚本结束，退出码 " + proc.exitValue());
            }
        } catch (Throwable t) {
            Log.w(TAG, "[" + phase + "] 脚本执行失败: " + t);
        } finally {
            try {
                if (proc != null) {
                    proc.destroy();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
