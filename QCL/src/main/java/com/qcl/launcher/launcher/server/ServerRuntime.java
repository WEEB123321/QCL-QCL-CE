package com.qcl.launcher.launcher.server;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import com.qcl.launcher.launcher.setting.RuntimeUtils;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.Architecture;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 服务端 Java 运行时：按需安装、校验，以及 Android 上可用的 JVM 参数。
 *
 * 这些参数来自 MineServe-Mobile（GPL-3.0）的服务端启动实现，用于绕开
 * Android/bionic 上 JLine-JNA、Netty 原生传输和 OSHI 的已知崩溃点。
 */
public final class ServerRuntime {

    private static final String TAG = "QCLServerRuntime";

    public interface Listener {
        void onLog(String line);

        void onProgress(int percent);
    }

    public static final class Info {
        public final String name;
        public final File home;
        public final String assetDir;
        public final int major;

        Info(String name, File home, String assetDir, int major) {
            this.name = name;
            this.home = home;
            this.assetDir = assetDir;
            this.major = major;
        }
    }

    private ServerRuntime() {
    }

    public static Info resolve(int requiredJava) {
        int major = runtimeMajor(requiredJava);
        return new Info(runtimeName(requiredJava), new File(AppManifest.JAVA_DIR,
                runtimeName(requiredJava)), runtimeAsset(requiredJava), major);
    }

    public static int runtimeMajor(int requiredJava) {
        int major = requiredJava;
        if (major > 25) {
            major = 25;
        }
        if (major <= 8) {
            major = 8;
        }
        return major;
    }

    public static String runtimeName(int requiredJava) {
        int major = runtimeMajor(requiredJava);
        return major == 8 ? "default" : "JRE" + major;
    }

    public static String runtimeAsset(int requiredJava) {
        int major = runtimeMajor(requiredJava);
        return "app_runtime/java/" + (major == 8 ? "jre8" : "jre" + major);
    }

    /**
     * 确保运行时完整可用；缺文件或版本过期时直接从 APK 资源重新解压。
     * 这一步会删除目标目录，只能在服务端未运行时调用。
     */
    public static File ensure(Context context, int requiredJava, Listener listener) {
        Info info = resolve(requiredJava);
        File javaBinary = new File(info.home, "bin/java");
        File modules = new File(info.home, "lib/modules");
        boolean modern = info.major > 8;
        boolean present = javaBinary.isFile() && (!modern || modules.isFile());
        boolean latest = false;
        if (present) {
            try {
                latest = RuntimeUtils.isLatest(context, info.home.getAbsolutePath(), info.assetDir);
            } catch (Throwable ignored) {
            }
        }
        if (present && latest) {
            ensureExecutable(info.home);
            return info.home;
        }
        if (listener != null) {
            listener.onLog("正在安装 Java " + info.major + " 运行时（" + info.name + "）");
        }
        try {
            String arch = Architecture.archAsString(Architecture.getRuntimeArchitecture());
            RuntimeUtils.installJava(context, info.home.getAbsolutePath(), info.assetDir, arch,
                    new RuntimeUtils.InstallListener() {
                        @Override
                        public void onProgress(int percent) {
                            if (listener != null) {
                                listener.onProgress(percent);
                            }
                        }

                        @Override
                        public void onStage(String stage) {
                            if (listener != null) {
                                listener.onLog("Java 运行时：" + stage);
                            }
                        }

                        @Override
                        public void onUpdate(String detail) {
                        }
                    });
        } catch (Throwable t) {
            Log.e(TAG, "安装 Java 运行时失败", t);
            throw new IllegalStateException("Java " + info.major + " 运行时安装失败：" + t.getMessage());
        }
        ensureExecutable(info.home);
        if (!javaBinary.isFile() || (modern && !modules.isFile())) {
            throw new IllegalStateException("Java " + info.major
                    + " 运行时文件不完整，请检查存储空间后重试");
        }
        if (listener != null) {
            listener.onLog("Java " + info.major + " 运行时已就绪");
        }
        return info.home;
    }

    /** 服务端堆内存上限：保留用户设置，但不超过设备总内存的一半。 */
    public static int clampHeap(Context context, int requestedMb) {
        int heap = Math.max(512, Math.min(requestedMb, 4096));
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(
                    Context.ACTIVITY_SERVICE);
            if (manager != null) {
                ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(memory);
                long totalMb = memory.totalMem / (1024L * 1024L);
                int safe = (int) Math.max(512L, Math.min(4096L, totalMb / 2L));
                if (heap > safe) {
                    heap = safe;
                }
            }
        } catch (Throwable ignored) {
        }
        return heap;
    }

    /** 追加 Android 兼容的 JVM 参数；堆大小由调用方单独设置。 */
    public static void addJvmFlags(List<String> args, Context context, File javaHome, int heapMb) {
        try {
            String zone = TimeZone.getDefault().getID();
            if (zone != null && !zone.isEmpty() && !"GMT".equals(zone)) {
                args.add("-Duser.timezone=" + zone);
            }
        } catch (Throwable ignored) {
        }
        args.add("-Djava.awt.headless=true");
        args.add("-Dfile.encoding=UTF-8");
        args.add("-Dstdout.encoding=UTF-8");
        args.add("-Dstderr.encoding=UTF-8");
        args.add("-Dlog4j2.formatMsgNoLookups=true");
        args.add("-Djava.io.tmpdir=" + context.getCacheDir().getAbsolutePath());
        File jnaTmp = new File(context.getCacheDir(), "jna");
        if (!jnaTmp.isDirectory() && !jnaTmp.mkdirs()) {
            jnaTmp = context.getCacheDir();
        }
        args.add("-Djna.tmpdir=" + jnaTmp.getAbsolutePath());
        args.add("-Djna.boot.library.path=" + context.getApplicationInfo().nativeLibraryDir);
        args.add("-Dio.netty.transport.noNative=true");
        args.add("-Dio.netty.transport.epoll.enabled=false");
        args.add("-Dio.netty.transport.kqueue.enabled=false");
        // Forge/NeoForge 的 Log4j 控制台会实例化 JLine 的 JNA provider；
        // Android 上 libc.so.6 不存在，JNA 初始化必然失败，直接把 jna 从候选里去掉。
        args.add("-Dorg.jline.terminal.providers=exec,jni,ffi");
        args.add("-Doshi.os.linux.allowudev=false");
        args.add("-Doshi.os.linux.allowsystemd=false");
        args.add("-Doshi.os.linux.filesystem.checknfs=false");
        args.add("-Doshi.os.linux.procfs.logwarning=false");
        args.add("-Doshi.util.memoizer.expiration=0");
        args.add("-XX:+UseSerialGC");
        args.add("-XX:MaxMetaspaceSize=" + Math.max(768, heapMb) + "m");
        args.add("-XX:ReservedCodeCacheSize=128m");
        args.add("-Xss768k");
        args.add("-Duser.language=" + Locale.getDefault().getLanguage());
    }

    private static void ensureExecutable(File home) {
        File[] binFiles = new File(home, "bin").listFiles();
        if (binFiles != null) {
            for (File file : binFiles) {
                setExecutable(file);
            }
        }
        setExecutable(new File(home, "lib/jspawnhelper"));
    }

    private static void setExecutable(File file) {
        try {
            if (file.isFile()) {
                file.setExecutable(true, false);
            }
        } catch (Throwable ignored) {
        }
    }
}
