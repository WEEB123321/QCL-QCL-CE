package com.qcl.launcher.manifest;

import android.content.Context;
import android.os.Environment;
import com.qcl.launcher.utils.file.FileUtils;

/* loaded from: classes2.dex */
public class AppManifest {
    public static String ACCOUNT_DIR;
    public static String BACKUP_DIR;
    public static String BOAT_LIB_DIR;
    public static String CACIOCAVALLO17_DIR;
    public static String CACIOCAVALLO_DIR;
    public static String CONTROLLER_DIR;
    public static String DEBUG_DIR;
    public static String DEFAULT_CACHE_DIR;
    public static String DEFAULT_RUNTIME_DIR;
    public static String EXTERNAL_DIR;
    public static String GAME_FILE_DIRECTORY_DIR;
    public static String INNER_DIR;
    public static String INNER_FILE_DIR;
    public static String INNER_GAME_DIR;
    public static String INSTALL_DIR;
    public static String JAVA_DIR;

    @Deprecated
    public static String LEGACY_AUDIO_DIR;
    public static String PLUGIN_DIR;
    public static String POJAV_LIB_DIR;
    public static String SAVES_CACHE_DIR;
    public static String SETTING_DIR;
    public static String STYLE_DIR;
    public static String LAUNCHER_DIR = Environment.getExternalStorageDirectory() + "/QCL";
    public static String DEFAULT_GAME_DIR = LAUNCHER_DIR + "/.minecraft";

    public static void initializeManifest(Context context) {
        INNER_DIR = context.getFilesDir().getParent();
        INNER_FILE_DIR = context.getFilesDir().getAbsolutePath();
        // ★★★ 社区版加固：getExternalCacheDir() / getExternalFilesDir() 在外部存储不可用时会
        //   返回 null（未挂载、被系统回收、多用户/工作资料、存储卡异常都会触发），
        //   而原先直接 .getParent() / .getAbsolutePath() 就是一次裸解引用 → NPE。
        //   致命之处在于本方法是在 QCLApplication.onCreate 和 RuntimeInstallActivity.onCreate
        //   里被调用的，也就是「打开 App」这条路径的最前端：一旦 NPE，进程当场死掉，
        //   用户看到的就是「一打开就闪退」，连界面都没有、也来不及弹崩溃页。
        //   这里统一退回内部存储下的等价目录：路径不同但不为 null，启动能继续。
        EXTERNAL_DIR = safeParent(context.getExternalCacheDir(), fallbackExternalRoot(context));
        ACCOUNT_DIR = INNER_FILE_DIR + "/accounts";
        // ★ 社区版新增：实例备份目录。放在 filesDir 下（不占外置存储、不需要额外权限），
        //   与旧的 settings/ 完全解耦 —— 卸载本功能后旧版可直接运行，不涉及数据迁移。
        BACKUP_DIR = INNER_FILE_DIR + "/backups";
        GAME_FILE_DIRECTORY_DIR = INNER_FILE_DIR + "/paths";
        SETTING_DIR = INNER_FILE_DIR + "/settings";
        CONTROLLER_DIR = INNER_FILE_DIR + "/control";
        PLUGIN_DIR = INNER_FILE_DIR + "/plugin";
        STYLE_DIR = INNER_FILE_DIR + "/style";
        // ★ 同上：getExternalFilesDir() 也可能为 null，退回内部存储（见上方 EXTERNAL_DIR 注释）。
        DEBUG_DIR = safeAbs(context.getExternalFilesDir("debug"), fallbackExternalRoot(context) + "/debug");
        INNER_GAME_DIR = safeAbs(context.getExternalFilesDir(".minecraft"), fallbackExternalRoot(context) + "/.minecraft");
        DEFAULT_CACHE_DIR = context.getCacheDir().getAbsolutePath();
        INSTALL_DIR = DEFAULT_CACHE_DIR + "/install";
        SAVES_CACHE_DIR = DEFAULT_CACHE_DIR + "/saves";
        DEFAULT_RUNTIME_DIR = context.getDir("runtime", 0).getAbsolutePath();
        JAVA_DIR = DEFAULT_RUNTIME_DIR + "/java";
        CACIOCAVALLO_DIR = DEFAULT_RUNTIME_DIR + "/caciocavallo";
        CACIOCAVALLO17_DIR = DEFAULT_RUNTIME_DIR + "/caciocavallo17";
        BOAT_LIB_DIR = DEFAULT_RUNTIME_DIR + "/boat";
        POJAV_LIB_DIR = DEFAULT_RUNTIME_DIR + "/pojav";
        FileUtils.createDirectory(LAUNCHER_DIR);
        FileUtils.createDirectory(DEFAULT_GAME_DIR);
        FileUtils.createDirectory(INNER_DIR);
        FileUtils.createDirectory(INNER_FILE_DIR);
        FileUtils.createDirectory(EXTERNAL_DIR);
        FileUtils.createDirectory(ACCOUNT_DIR);
        FileUtils.createDirectory(BACKUP_DIR);
        FileUtils.createDirectory(GAME_FILE_DIRECTORY_DIR);
        FileUtils.createDirectory(SETTING_DIR);
        FileUtils.createDirectory(CONTROLLER_DIR);
        FileUtils.createDirectory(INSTALL_DIR);
        FileUtils.createDirectory(PLUGIN_DIR);
        FileUtils.createDirectory(STYLE_DIR);
        FileUtils.createDirectory(DEBUG_DIR);
        FileUtils.createDirectory(INNER_GAME_DIR);
        FileUtils.createDirectory(DEFAULT_CACHE_DIR);
        FileUtils.createDirectory(INSTALL_DIR);
        FileUtils.createDirectory(SAVES_CACHE_DIR);
        FileUtils.createDirectory(DEFAULT_RUNTIME_DIR);
        FileUtils.createDirectory(JAVA_DIR);
        FileUtils.createDirectory(CACIOCAVALLO_DIR);
        FileUtils.createDirectory(CACIOCAVALLO17_DIR);
        FileUtils.createDirectory(BOAT_LIB_DIR);
        FileUtils.createDirectory(POJAV_LIB_DIR);
    }

    /**
     * ★★★ 社区版加固：外部存储不可用时的兜底根目录。
     * 用 getCacheDir()（内部存储，Android 保证非 null）的父目录，
     * 形如 /data/user/0/<包名>，与 EXTERNAL_DIR 原来的语义（应用私有外部根）不同，
     * 但保证「非 null、可写、启动不中断」。功能上：游戏目录另有 LAUNCHER_DIR 兜着，
     * 这里只影响 debug/ 与内嵌 .minecraft/ 这类临时用途。
     */
    private static String fallbackExternalRoot(Context context) {
        try {
            java.io.File cache = context.getCacheDir();
            if (cache != null && cache.getParent() != null) {
                return cache.getParent();
            }
            if (cache != null) {
                return cache.getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
        return "/data/local/tmp";
    }

    /** 安全取父目录：null 时退回 fallback，绝不抛异常。 */
    private static String safeParent(java.io.File file, String fallback) {
        try {
            if (file != null && file.getParent() != null) {
                return file.getParent();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    /** 安全取绝对路径：null 时退回 fallback，绝不抛异常。 */
    private static String safeAbs(java.io.File file, String fallback) {
        try {
            if (file != null) {
                return file.getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }
}
