package com.qcl.launcher.launcher.setting;

import android.app.Activity;
import android.content.Context;
import com.qcl.launcher.auth.Account;
import com.qcl.launcher.auth.authlibinjector.AuthlibInjectorServer;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.list.info.contents.ContentListBean;
import com.qcl.launcher.launcher.setting.game.PrivateGameSetting;
import com.qcl.launcher.launcher.setting.game.PublicGameSetting;
import com.qcl.launcher.launcher.setting.game.child.BoatLauncherSetting;
import com.qcl.launcher.launcher.setting.game.child.GameDirSetting;
import com.qcl.launcher.launcher.setting.game.child.JavaSetting;
import com.qcl.launcher.launcher.setting.game.child.PojavLauncherSetting;
import com.qcl.launcher.launcher.setting.game.child.RamSetting;
import com.qcl.launcher.launcher.setting.launcher.LauncherSetting;
import com.qcl.launcher.launcher.setting.launcher.child.BackgroundSetting;
import com.qcl.launcher.launcher.setting.launcher.child.SourceSetting;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.file.AssetsUtils;
import com.qcl.launcher.utils.gson.GsonUtils;
import com.qcl.launcher.utils.platform.MemoryUtils;
import java.io.File;
import java.util.ArrayList;

import com.qcl.launcher.R;
/* loaded from: classes2.dex */
public class InitializeSetting {
    static final /* synthetic */ boolean $assertionsDisabled = false;

    public static void initializeControlPattern(Activity activity, AssetsUtils.FileOperateCallback fileOperateCallback) {
        String[] list = new File(AppManifest.CONTROLLER_DIR + "/").list();
        if (new File(AppManifest.CONTROLLER_DIR + "/").exists()) {
            if (list.length == 0) {
                AssetsUtils.getInstance(activity.getApplicationContext()).copyAssetsToSD("control", AppManifest.CONTROLLER_DIR).setFileOperateCallback(fileOperateCallback);
                return;
            }
            return;
        }
        AssetsUtils.getInstance(activity.getApplicationContext()).copyAssetsToSD("control", AppManifest.CONTROLLER_DIR).setFileOperateCallback(fileOperateCallback);
    }

    public static ArrayList<Account> initializeAccounts(Context context) {
        ArrayList<Account> arrayList = new ArrayList<>();
        if (new File(AppManifest.ACCOUNT_DIR + "/accounts.json").exists() && GsonUtils.getContentListFromFile(AppManifest.ACCOUNT_DIR + "/accounts.json").size() != 0) {
            return GsonUtils.getAccountListFromFile(AppManifest.ACCOUNT_DIR + "/accounts.json");
        }
        GsonUtils.saveAccounts(arrayList, AppManifest.ACCOUNT_DIR + "/accounts.json");
        return arrayList;
    }

    public static ArrayList<AuthlibInjectorServer> initializeAuthlibInjectorServer(Context context) {
        ArrayList<AuthlibInjectorServer> arrayList = new ArrayList<>();
        if (new File(AppManifest.ACCOUNT_DIR + "/authlib_injector_server.json").exists() && GsonUtils.getContentListFromFile(AppManifest.ACCOUNT_DIR + "/authlib_injector_server.json").size() != 0) {
            return GsonUtils.getServerListFromFile(AppManifest.ACCOUNT_DIR + "/authlib_injector_server.json");
        }
        GsonUtils.saveServer(arrayList, AppManifest.ACCOUNT_DIR + "/authlib_injector_server.json");
        return arrayList;
    }

    public static ArrayList<ContentListBean> initializeContents(Context context) {
        ArrayList<ContentListBean> arrayList = new ArrayList<>();
        if (new File(AppManifest.GAME_FILE_DIRECTORY_DIR + "/game_file_directories.json").exists() && GsonUtils.getContentListFromFile(AppManifest.GAME_FILE_DIRECTORY_DIR + "/game_file_directories.json").size() != 0) {
            return GsonUtils.getContentListFromFile(AppManifest.GAME_FILE_DIRECTORY_DIR + "/game_file_directories.json");
        }
        arrayList.add(new ContentListBean(context.getString(R.string.default_game_file_directory_list_pri), AppManifest.DEFAULT_GAME_DIR, true));
        arrayList.add(new ContentListBean(context.getString(R.string.default_game_file_directory_list_sec), AppManifest.INNER_GAME_DIR, false));
        GsonUtils.saveContents(arrayList, AppManifest.GAME_FILE_DIRECTORY_DIR + "/game_file_directories.json");
        return arrayList;
    }

    public static LauncherSetting initializeLauncherSetting() {
        if (new File(AppManifest.SETTING_DIR + "/launcher_setting.json").exists()) {
            // ★★★ 社区版加固（本轮）：Gson 解析失败（文件被截断 / 内容损坏 / 字段类型不符）
            //   时返回 **null**，原写法直接把它 return 出去 —— 下游
            //   initializePublicGameSetting 里 mainActivity.launcherSetting.gameFileDirectory
            //   会立刻 NPE；而它跑在 MainActivity.init() 的后台线程里，未捕获异常 = 整机闪退。
            //   更糟的是坏文件一直在，**之后每次启动都会必崩**（覆盖安装不清数据，坏文件会留着）。
            //   现在解析失败就退回默认值分支（与「文件不存在」同一处理）。
            LauncherSetting loaded = GsonUtils.getLauncherSettingFromFile(AppManifest.SETTING_DIR + "/launcher_setting.json");
            if (loaded != null) {
                return migrateBackgroundOnce(loaded);
            }
            android.util.Log.w("jrelog", "[设置] launcher_setting.json 解析失败，改用默认值");
        }
        // ★★★ 社区版：新用户默认背景 = 「网络」= 动态轮播（type 0）。
        //   1.3.4 曾把它改成 type 1（经典单张图），现在改回 0 —— 用户明确要求用他自己那两张
        //   光影截图做轮播，而轮播只在这个模式下才跑（见 DynamicBackground）。
        LauncherSetting launcherSetting = new LauncherSetting(AppManifest.DEFAULT_GAME_DIR, new SourceSetting(true, 1, 0), 0, 64, false, true, false, false, false, "DEFAULT", "DEFAULT", new BackgroundSetting(0, "", ""), AppManifest.DEFAULT_CACHE_DIR);
        launcherSetting.bgCarouselMigrated = true;
        GsonUtils.saveLauncherSetting(launcherSetting, AppManifest.SETTING_DIR + "/launcher_setting.json");
        return launcherSetting;
    }

    /**
     * ★★★ 社区版：背景轮播的一次性迁移。
     *
     * <p>已装过启动器的人，设置里存的是老默认 {@code type = 1}（经典图片）。
     * 只改代码默认值对他无效 —— 必须动他的存档一次。
     *
     * <p><b>只跑一次</b>：跑完把 {@code bgCarouselMigrated} 置 true 并落盘，
     * 之后用户在设置里自己选什么就是什么，不会被每次启动覆盖。
     */
    private static LauncherSetting migrateBackgroundOnce(LauncherSetting s) {
        try {
            if (s == null || s.bgCarouselMigrated) {
                return s;
            }
            if (s.launcherBackground == null) {
                s.launcherBackground = new BackgroundSetting(0, "", "");
            } else if (s.launcherBackground.type == 1) {
                // 只有还停在「老默认」的才动 —— 已经自己设成自定义图片（2）的不碰
                s.launcherBackground.type = 0;
            }
            s.bgCarouselMigrated = true;
            GsonUtils.saveLauncherSetting(s, AppManifest.SETTING_DIR + "/launcher_setting.json");
            android.util.Log.i("jrelog", "[设置] 背景已迁移为动态轮播（一次性）");
        } catch (Throwable t) {
            android.util.Log.w("jrelog", "[设置] 背景迁移失败（已忽略）: " + t);
        }
        return s;
    }

    public static PublicGameSetting initializePublicGameSetting(Context context, MainActivity mainActivity) {
        if (new File(AppManifest.SETTING_DIR + "/public_game_setting.json").exists()) {
            // ★ 社区版加固：同上，解析失败返回 null 会让调用方拿到 null 设置
            PublicGameSetting loaded = GsonUtils.getPublicGameSettingFromFile(AppManifest.SETTING_DIR + "/public_game_setting.json");
            if (loaded != null) {
                return loaded;
            }
            android.util.Log.w("jrelog", "[设置] public_game_setting.json 解析失败，改用默认值");
        }
        // ★ 社区版加固：mainActivity.launcherSetting 理论上不该为 null，但这里是裸解引用
        //   （它就在上一行被赋值，一旦上游改成「解析失败返回 null」就是必崩）。
        //   加一层兜底，拿不到目录就按「没有已装版本」处理（空串），与原有 else 分支一致。
        String gameDir = null;
        if (mainActivity != null && mainActivity.launcherSetting != null) {
            gameDir = mainActivity.launcherSetting.gameFileDirectory;
        }
        String firstVersion = "";
        if (gameDir != null) {
            java.util.List<String> names = SettingUtils.getLocalVersionNames(gameDir);
            if (names != null && names.size() != 0) {
                firstVersion = gameDir + "/versions/" + names.get(0);
            }
        }
        PublicGameSetting publicGameSetting = new PublicGameSetting(new Account(0, "", "", "", "", "", "", "", "", "", "", ""), AppManifest.DEBUG_DIR, firstVersion);
        GsonUtils.savePublicGameSetting(publicGameSetting, AppManifest.SETTING_DIR + "/public_game_setting.json");
        return publicGameSetting;
    }

    public static PrivateGameSetting initializePrivateGameSetting(Context context) {
        if (new File(AppManifest.SETTING_DIR + "/private_game_setting.json").exists()) {
            // ★ 社区版加固：同上
            PrivateGameSetting loaded = GsonUtils.getPrivateGameSettingFromFile(AppManifest.SETTING_DIR + "/private_game_setting.json");
            if (loaded != null) {
                return loaded;
            }
            android.util.Log.w("jrelog", "[设置] private_game_setting.json 解析失败，改用默认值");
        }
        int findBestRAMAllocation = MemoryUtils.findBestRAMAllocation(context);
        PrivateGameSetting privateGameSetting = new PrivateGameSetting(false, true, true, false, false, false, false, new JavaSetting(true, AppManifest.JAVA_DIR + "/default"), "", "", "", new GameDirSetting(1, AppManifest.DEFAULT_GAME_DIR), new BoatLauncherSetting(false, "GL4ES115", "default"), new PojavLauncherSetting(true, "ng_gl4es", "default"), new RamSetting(findBestRAMAllocation, findBestRAMAllocation, true), "Default", 1.0f);
        GsonUtils.savePrivateGameSetting(privateGameSetting, AppManifest.SETTING_DIR + "/private_game_setting.json");
        return privateGameSetting;
    }
}
