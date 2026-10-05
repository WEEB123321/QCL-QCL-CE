/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.content.Context
 *  android.content.Intent
 *  android.os.Bundle
 *  android.util.Log
 *  net.kdt.pojavlaunch.utils.Architecture
 *  org.apache.commons.io.FileUtils
 *  org.apache.commons.io.IOUtils
 */
package com.qcl.launcher.launcher.setting;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.setting.RuntimeInstallActivity;
import com.qcl.launcher.launcher.setting.RuntimeUtils;
import com.qcl.launcher.launcher.setting.SettingUtils;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.file.AssetsUtils;
import com.qcl.launcher.utils.file.FileStringUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Objects;
import net.kdt.pojavlaunch.utils.Architecture;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.qcl.launcher.R;
public class InstallLauncherFile {
    public static void checkLauncherFiles(RuntimeInstallActivity activity) {
        AssetsUtils.ProgressCallback progressCallback = progress -> activity.runOnUiThread(() -> {
            activity.loadingProgress.setProgress(progress);
            activity.loadingProgressText.setText((CharSequence)(progress + " %"));
        });
        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_plugin)));
        if (!new File(AppManifest.PLUGIN_DIR + "/installer").exists() || !new File(AppManifest.PLUGIN_DIR + "/installer/version").exists() || Integer.parseInt(Objects.requireNonNull(FileStringUtils.getStringFromFile(AppManifest.PLUGIN_DIR + "/installer/version"))) < Integer.parseInt(Objects.requireNonNull(AssetsUtils.readAssetsTxt((Context)activity, "plugin/installer/version")))) {
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.PLUGIN_DIR + "/installer");
            AssetsUtils.getInstance((Context)activity).setProgressCallback(progressCallback).copyOnMainThread("plugin/installer", AppManifest.PLUGIN_DIR + "/installer");
        }
        if (!new File(AppManifest.PLUGIN_DIR + "/touch").exists() || !new File(AppManifest.PLUGIN_DIR + "/touch/version").exists() || Integer.parseInt(Objects.requireNonNull(FileStringUtils.getStringFromFile(AppManifest.PLUGIN_DIR + "/touch/version"))) < Integer.parseInt(Objects.requireNonNull(AssetsUtils.readAssetsTxt((Context)activity, "plugin/touch/version")))) {
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.PLUGIN_DIR + "/touch");
            AssetsUtils.getInstance((Context)activity).setProgressCallback(progressCallback).copyOnMainThread("plugin/touch", AppManifest.PLUGIN_DIR + "/touch");
        }
        if (!new File(AppManifest.PLUGIN_DIR + "/login/authlib-injector").exists() || !new File(AppManifest.PLUGIN_DIR + "/login/authlib-injector/version").exists() || Integer.parseInt(Objects.requireNonNull(FileStringUtils.getStringFromFile(AppManifest.PLUGIN_DIR + "/login/authlib-injector/version"))) < Integer.parseInt(Objects.requireNonNull(AssetsUtils.readAssetsTxt((Context)activity, "plugin/login/authlib-injector/version")))) {
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.PLUGIN_DIR + "/login/authlib-injector");
            AssetsUtils.getInstance((Context)activity).setProgressCallback(progressCallback).copyOnMainThread("plugin/login/authlib-injector", AppManifest.PLUGIN_DIR + "/login/authlib-injector");
        }
        if (!new File(AppManifest.PLUGIN_DIR + "/login/nide8auth").exists() || !new File(AppManifest.PLUGIN_DIR + "/login/nide8auth/version").exists() || Integer.parseInt(Objects.requireNonNull(FileStringUtils.getStringFromFile(AppManifest.PLUGIN_DIR + "/login/nide8auth/version"))) < Integer.parseInt(Objects.requireNonNull(AssetsUtils.readAssetsTxt((Context)activity, "plugin/login/nide8auth/version")))) {
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.PLUGIN_DIR + "/login/nide8auth");
            AssetsUtils.getInstance((Context)activity).setProgressCallback(progressCallback).copyOnMainThread("plugin/login/nide8auth", AppManifest.PLUGIN_DIR + "/login/nide8auth");
        }
        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_control)));
        // ★ 2026-09-19：默认控键布局按 info.json 内容比对更新（只动 Default，玩家自建布局不受影响）。
        InstallLauncherFile.syncDefaultControl(activity);
        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_lib)));
        // ★★★ 社区版加固（本轮）：原写法是
        //     Integer.parseInt(Objects.requireNonNull(getStringFromFile(设备上的 version)))
        //       < Integer.parseInt(Objects.requireNonNull(readAssetsTxt("app_runtime/version")))
        //   两个 requireNonNull 任一为 null 就抛 NPE；parseInt 遇到空串 / 非数字还会抛
        //   NumberFormatException。而 checkBaseFiles 是在 RuntimeInstallActivity 的后台
        //   线程里**裸调用**的（调用点没有 try/catch）—— 线程里未捕获的异常会走全局
        //   处理器 → 弹崩溃页 + Process.killProcess，对外表现就是「打开直接闪退」。
        //   这里改成读不到就按 -1 处理，语义与「需要重新解包」一致，且任何情况都不抛。
        if (InstallLauncherFile.isRuntimeVersionOutdated(activity)) {
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.BOAT_LIB_DIR);
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.POJAV_LIB_DIR);
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.CACIOCAVALLO_DIR);
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.CACIOCAVALLO17_DIR);
            if (new File(AppManifest.DEFAULT_RUNTIME_DIR + "/version").exists()) {
                new File(AppManifest.DEFAULT_RUNTIME_DIR + "/version").delete();
            }
            // ★ Boat 后端已彻底移除：assets/app_runtime/ 下没有 boat 目录，
            //   原来这里会无条件拷贝一次，属确定的死路径（copyOnMainThread 对不存在的路径会静默失败），已删除。
            //   BOAT_LIB_DIR 的 deleteDirectory 仍保留，用于清理历史安装残留。
            AssetsUtils.getInstance((Context)activity).setProgressCallback(progressCallback).copyOnMainThread("app_runtime/pojav", AppManifest.POJAV_LIB_DIR);
            AssetsUtils.getInstance((Context)activity).setProgressCallback(progressCallback).copyOnMainThread("app_runtime/caciocavallo", AppManifest.CACIOCAVALLO_DIR);
            AssetsUtils.getInstance((Context)activity).setProgressCallback(progressCallback).copyOnMainThread("app_runtime/caciocavallo17", AppManifest.CACIOCAVALLO17_DIR);
            AssetsUtils.getInstance((Context)activity).setProgressCallback(progressCallback).copyOnMainThread("app_runtime/version", AppManifest.DEFAULT_RUNTIME_DIR + "/version");
        }
        InstallLauncherFile.checkJava8(activity, progressCallback);
        InstallLauncherFile.checkJava17(activity, progressCallback);
        InstallLauncherFile.checkJava21(activity, progressCallback);
        InstallLauncherFile.checkJava25(activity, progressCallback);
        activity.runOnUiThread(() -> InstallLauncherFile.enterLauncher(activity));
    }

    public static void checkBaseFiles(RuntimeInstallActivity activity) {
        AssetsUtils.ProgressCallback progressCallback = progress -> activity.runOnUiThread(() -> {
            activity.loadingProgress.setProgress(progress);
            activity.loadingProgressText.setText((CharSequence)(progress + " %"));
        });
        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_plugin)));
        InstallLauncherFile.copyPluginIfNeeded(activity, progressCallback, "plugin/installer", AppManifest.PLUGIN_DIR + "/installer");
        InstallLauncherFile.copyPluginIfNeeded(activity, progressCallback, "plugin/touch", AppManifest.PLUGIN_DIR + "/touch");
        InstallLauncherFile.copyPluginIfNeeded(activity, progressCallback, "plugin/login/authlib-injector", AppManifest.PLUGIN_DIR + "/login/authlib-injector");
        InstallLauncherFile.copyPluginIfNeeded(activity, progressCallback, "plugin/login/nide8auth", AppManifest.PLUGIN_DIR + "/login/nide8auth");
        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_control)));
        // ★ 2026-09-19：默认控键布局按 info.json 内容比对更新（只动 Default，玩家自建布局不受影响）。
        InstallLauncherFile.syncDefaultControl(activity);
        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_lib)));
        // ★★★ 社区版加固（本轮）：原写法是
        //     Integer.parseInt(Objects.requireNonNull(getStringFromFile(设备上的 version)))
        //       < Integer.parseInt(Objects.requireNonNull(readAssetsTxt("app_runtime/version")))
        //   两个 requireNonNull 任一为 null 就抛 NPE；parseInt 遇到空串 / 非数字还会抛
        //   NumberFormatException。而 checkBaseFiles 是在 RuntimeInstallActivity 的后台
        //   线程里**裸调用**的（调用点没有 try/catch）—— 线程里未捕获的异常会走全局
        //   处理器 → 弹崩溃页 + Process.killProcess，对外表现就是「打开直接闪退」。
        //   这里改成读不到就按 -1 处理，语义与「需要重新解包」一致，且任何情况都不抛。
        if (InstallLauncherFile.isRuntimeVersionOutdated(activity)) {
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.BOAT_LIB_DIR);
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.POJAV_LIB_DIR);
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.CACIOCAVALLO_DIR);
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.CACIOCAVALLO17_DIR);
            if (new File(AppManifest.DEFAULT_RUNTIME_DIR + "/version").exists()) {
                new File(AppManifest.DEFAULT_RUNTIME_DIR + "/version").delete();
            }
            // ★ Boat 后端已彻底移除：assets/app_runtime/ 下没有 boat 目录，
            //   原来这里会无条件拷贝一次，属确定的死路径（copyOnMainThread 对不存在的路径会静默失败），已删除。
            //   BOAT_LIB_DIR 的 deleteDirectory 仍保留，用于清理历史安装残留。
            AssetsUtils.getInstance((Context)activity).setProgressCallback(progressCallback).copyOnMainThread("app_runtime/version", AppManifest.DEFAULT_RUNTIME_DIR + "/version");
        }
    }

    /**
     * ★★★ 社区版新增（本轮加固）：设备上的运行环境版本是否落后于安装包内置版本。
     *
     * <p><b>为什么单独抽出来</b>：原来这段是内联在 {@link #checkBaseFiles} 里的
     * {@code Integer.parseInt(Objects.requireNonNull(...))} 长表达式，有两个隐患：
     * <ol>
     *   <li>{@code requireNonNull} 任一为 null 直接抛 {@code NullPointerException}；</li>
     *   <li>{@code parseInt} 遇到空串 / 含空白 / 非数字抛 {@code NumberFormatException}。</li>
     * </ol>
     * 而调用它的 {@code RuntimeInstallActivity.init()} 后台线程**没有 try/catch**，
     * 线程里未捕获的异常会走全局处理器 → 弹崩溃页 + {@code killProcess}，
     * 对外就是「打开直接闪退」。这里把整段包住，读不到一律按 -1（= 需要重新解包）处理，
     * 与原来的语义一致，但**任何情况下都不抛**。
     */
    private static boolean isRuntimeVersionOutdated(Context context) {
        try {
            File localFile = new File(AppManifest.DEFAULT_RUNTIME_DIR + "/version");
            if (!localFile.exists()) {
                return true;
            }
            int local = parseIntSafe(FileStringUtils.getStringFromFile(localFile.getAbsolutePath()));
            int asset = parseIntSafe(AssetsUtils.readAssetsTxt(context, "app_runtime/version"));
            return local < asset;
        } catch (Throwable t) {
            android.util.Log.w("jrelog", "[运行环境] 版本比对失败，按「需要重新解包」处理", t);
            return true;
        }
    }

    /** ★ 读不到 / 非数字一律按 -1，绝不抛 */
    private static int parseIntSafe(String s) {
        if (s == null) {
            return -1;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 同步默认控键布局：把 assets 的 control/Default 与设备上的一份按 info.json 内容比对，
     * 不一致就重新复制（修复历史版本按钮坐标 bug 的分发通道：F5 偏下、F8/F12 绝对坐标归零等）。
     * 只动 Default 一个目录，玩家自建布局不受影响。静默执行，可在任意线程调用。
     */
    public static void syncDefaultControl(Context context) {
        try {
            String assetControlInfo = AssetsUtils.readAssetsTxt(context, "control/Default/info.json");
            String localControlInfo = FileStringUtils.getStringFromFile(AppManifest.CONTROLLER_DIR + "/Default/info.json");
            if (assetControlInfo != null && !assetControlInfo.equals(localControlInfo)) {
                android.util.Log.i("jrelog", "[控键布局] 检测到默认布局版本变化，重新复制 Default");
                com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.CONTROLLER_DIR + "/Default");
                AssetsUtils.getInstance(context).copyOnMainThread("control/Default", AppManifest.CONTROLLER_DIR + "/Default");
            }
        } catch (Throwable t) {
            android.util.Log.w("jrelog", "[控键布局] 默认布局同步失败", t);
        }
    }

    private static void copyPluginIfNeeded(RuntimeInstallActivity activity, AssetsUtils.ProgressCallback callback, String assetDir, String targetDir) {
        File versionFile = new File(targetDir, "version");
        if (versionFile.exists()) {
            try {
                int installed = Integer.parseInt(Objects.requireNonNull(FileStringUtils.getStringFromFile(versionFile.getAbsolutePath())).trim());
                int expected = Integer.parseInt(Objects.requireNonNull(AssetsUtils.readAssetsTxt((Context)activity, assetDir + "/version")).trim());
                if (installed >= expected) {
                    return;
                }
            }
            catch (Throwable throwable) {}
        } else if (new File(targetDir).isDirectory()) {
            // empty if block
        }
        com.qcl.launcher.utils.file.FileUtils.deleteDirectory(targetDir);
        AssetsUtils.getInstance((Context)activity).setProgressCallback(callback).copyOnMainThread(assetDir, targetDir);
    }

    @SuppressLint(value={"SetTextI18n"})
    public static void checkJava8(RuntimeInstallActivity activity, AssetsUtils.ProgressCallback callback) {
        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_java_8)));
        InstallLauncherFile.installJava8(activity, callback);
    }

    private static void installJava8(final RuntimeInstallActivity activity, AssetsUtils.ProgressCallback callback) {
        String targetPath = AppManifest.JAVA_DIR + "/default";
        String srcDir = "app_runtime/java/jre8";
        try {
            if (RuntimeUtils.isLatest((Context)activity, targetPath, srcDir) && new File(targetPath, "bin/java").exists()) {
                return;
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
        try {
            RuntimeUtils.installJava((Context)activity, targetPath, srcDir, InstallLauncherFile.deviceArchName(), new RuntimeUtils.InstallListener(){

                @Override
                public void onUpdate(String detail) {
                }

                @Override
                public void onProgress(int percent) {
                    activity.runOnUiThread(() -> {
                        activity.loadingProgress.setProgress(percent);
                        activity.loadingProgressText.setText((CharSequence)(percent + " %"));
                    });
                }

                @Override
                public void onStage(String stageKey) {
                    if ("patching".equals(stageKey)) {
                        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_java_patching)));
                    }
                }
            });
            InstallLauncherFile.unpack200(activity.getApplicationContext().getApplicationInfo().nativeLibraryDir, targetPath);
            InstallLauncherFile.postPrepare((Context)activity, "default");
        }
        catch (IOException e) {
            Log.e((String)"MULTIRT", (String)"Unable to prepare default(Java 8)", (Throwable)e);
        }
    }

    @SuppressLint(value={"SetTextI18n"})
    public static void checkJava17(RuntimeInstallActivity activity, AssetsUtils.ProgressCallback callback) {
        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_java_17)));
        InstallLauncherFile.installModernJava(activity, callback, "JRE17", "jre17");
    }

    @SuppressLint(value={"SetTextI18n"})
    public static void checkJava21(RuntimeInstallActivity activity, AssetsUtils.ProgressCallback callback) {
        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_java_21)));
        InstallLauncherFile.installModernJava(activity, callback, "JRE21", "jre21");
    }

    @SuppressLint(value={"SetTextI18n"})
    public static void checkJava25(RuntimeInstallActivity activity, AssetsUtils.ProgressCallback callback) {
        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_java_25)));
        InstallLauncherFile.installModernJava(activity, callback, "JRE25", "jre25");
    }

    private static String deviceArchName() {
        int arch = com.qcl.launcher.utils.Architecture.getRuntimeArchitecture();
        if (arch == com.qcl.launcher.utils.Architecture.ARCH_ARM) {
            return "arm";
        }
        if (arch == com.qcl.launcher.utils.Architecture.ARCH_ARM64) {
            return "arm64";
        }
        if (arch == com.qcl.launcher.utils.Architecture.ARCH_X86) {
            return "x86";
        }
        return "x86_64";
    }

    private static void installModernJava(final RuntimeInstallActivity activity, AssetsUtils.ProgressCallback callback, String targetName, String assetName) {
        String arch = InstallLauncherFile.deviceArchName();
        if ("jre25".equals(assetName) && "x86".equals(arch)) {
            return;
        }
        String srcDir = "app_runtime/java/" + assetName;
        String targetPath = AppManifest.JAVA_DIR + "/" + targetName;
        try {
            if (RuntimeUtils.isLatest((Context)activity, targetPath, srcDir) && new File(targetPath, "lib/modules").exists()) {
                return;
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
        try {
            RuntimeUtils.installJava((Context)activity, targetPath, srcDir, arch, new RuntimeUtils.InstallListener(){

                @Override
                public void onUpdate(String detail) {
                }

                @Override
                public void onProgress(int percent) {
                    activity.runOnUiThread(() -> {
                        activity.loadingProgress.setProgress(percent);
                        activity.loadingProgressText.setText((CharSequence)(percent + " %"));
                    });
                }

                @Override
                public void onStage(String stageKey) {
                    if ("patching".equals(stageKey)) {
                        activity.runOnUiThread(() -> activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_java_patching)));
                    }
                }
            });
            InstallLauncherFile.unpack200(activity.getApplicationContext().getApplicationInfo().nativeLibraryDir, targetPath);
            InstallLauncherFile.postPrepare((Context)activity, targetName);
        }
        catch (IOException e) {
            Log.e((String)"MULTIRT", (String)("Unable to prepare " + targetName), (Throwable)e);
        }
    }

    private static void unpack200(String nativeLibraryDir, String runtimePath) {
        File basePath = new File(runtimePath);
        Collection<File> files = FileUtils.listFiles((File)basePath, (String[])new String[]{"pack"}, (boolean)true);
        File workdir = new File(nativeLibraryDir);
        ProcessBuilder processBuilder = new ProcessBuilder(new String[0]).directory(workdir);
        for (File jarFile : files) {
            try {
                Process process = processBuilder.command("./libunpack200.so", "-r", jarFile.getAbsolutePath(), jarFile.getAbsolutePath().replace(".pack", "")).start();
                process.waitFor();
            }
            catch (IOException | InterruptedException e) {
                Log.e((String)"MULTIRT", (String)"Failed to unpack the runtime !");
            }
        }
    }

    public static void postPrepare(Context context, String name) throws IOException {
        File dest = new File(AppManifest.JAVA_DIR, "/" + name);
        if (!dest.exists()) {
            return;
        }
        String libFolder = "lib";
        String arch = "";
        if (Architecture.getRuntimeArchitecture() == Architecture.ARCH_ARM) {
            arch = "aarch32";
        }
        if (Architecture.getRuntimeArchitecture() == Architecture.ARCH_ARM64) {
            arch = "aarch64";
        }
        if (Architecture.getRuntimeArchitecture() == Architecture.ARCH_X86) {
            arch = "i386";
        }
        if (Architecture.getRuntimeArchitecture() == Architecture.ARCH_X86_64) {
            arch = "amd64";
        }
        if (new File(dest, libFolder + "/" + arch).exists()) {
            libFolder = libFolder + "/" + arch;
        }
        File ftIn = new File(dest, libFolder + "/libfreetype.so.6");
        File ftOut = new File(dest, libFolder + "/libfreetype.so");
        if (ftIn.exists() && (!ftOut.exists() || ftIn.length() != ftOut.length())) {
            ftIn.renameTo(ftOut);
        }
        InstallLauncherFile.copyDummyNativeLib(context, "libawt_xawt.so", dest, libFolder);
    }

    private static void copyDummyNativeLib(Context ctx, String name, File dest, String libFolder) throws IOException {
        File fileLib = new File(dest, "/" + libFolder + "/" + name);
        fileLib.delete();
        FileInputStream is = new FileInputStream(new File(ctx.getApplicationInfo().nativeLibraryDir, name));
        FileOutputStream os = new FileOutputStream(fileLib);
        IOUtils.copy((InputStream)is, (OutputStream)os);
        is.close();
        os.close();
    }

    @SuppressLint(value={"SetTextI18n"})
    public static void enterLauncher(RuntimeInstallActivity activity) {
        activity.loadingText.setText((CharSequence)activity.getString(R.string.loading_hint_ready));
        activity.loadingProgress.setProgress(100);
        activity.loadingProgressText.setText((CharSequence)"100 %");
        Intent intent = new Intent((Context)activity, MainActivity.class);
        Bundle bundle = new Bundle();
        bundle.putBoolean("fullscreen", activity.launcherSetting.fullscreen);
        intent.putExtras(bundle);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void enterRuntimeInstall(RuntimeInstallActivity activity) {
        activity.startActivity(new Intent((Context)activity, RuntimeInstallActivity.class));
        activity.finish();
    }
}

