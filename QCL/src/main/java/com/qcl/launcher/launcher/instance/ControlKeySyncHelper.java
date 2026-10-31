package com.qcl.launcher.launcher.instance;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.qcl.launcher.launcher.setting.SettingUtils;
import com.qcl.launcher.launcher.setting.game.PrivateGameSetting;
import com.qcl.launcher.utils.gson.GsonUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * 控键布局的跨实例同步与恢复。
 *
 * <p>只记录「通过启动器改过」的实例：同步和版本设置页里的选择都会写入标记。
 * 恢复时只重置标记里仍然保持原值的实例；如果某个实例的 qcl.cfg 被外部改过，
 * 当前值和标记不一致，就跳过，避免把用户在启动器外做的修改一起抹掉。
 */
public final class ControlKeySyncHelper {

    private static final String PREFS = "qcl_control_sync";

    private ControlKeySyncHelper() {
    }

    public static final class Result {
        public int changed;
        public int skipped;
        public int total;
        public String lastError;
    }

    public static void markChanged(Context context, File cfgFile, String layout) {
        if (context == null || cfgFile == null) {
            return;
        }
        prefs(context).edit()
                .putString(cfgFile.getAbsolutePath(), layout == null ? "" : layout)
                .apply();
    }

    public static Result syncAll(Context context, String gameDir,
                                 PrivateGameSetting templateForMissing, String layout) {
        Result result = new Result();
        String value = layout == null || layout.trim().isEmpty() ? "Default" : layout;
        ArrayList<String> names = SettingUtils.getLocalVersionNames(gameDir);
        result.total = names.size();
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        File versionsDir = new File(gameDir, "versions");
        for (String name : names) {
            File cfg = new File(new File(versionsDir, name), "qcl.cfg");
            try {
                PrivateGameSetting setting =
                        GsonUtils.getPrivateGameSettingFromFile(cfg.getAbsolutePath());
                if (setting == null) {
                    if (cfg.isFile() || templateForMissing == null) {
                        result.skipped++;
                        continue;
                    }
                    setting = gson.fromJson(gson.toJson(templateForMissing),
                            PrivateGameSetting.class);
                    if (setting == null) {
                        result.skipped++;
                        continue;
                    }
                }
                setting.controlLayout = value;
                GsonUtils.savePrivateGameSetting(setting, cfg.getAbsolutePath());
                editor.putString(cfg.getAbsolutePath(), value);
                result.changed++;
            } catch (Throwable t) {
                result.skipped++;
                result.lastError = t.getMessage();
            }
        }
        editor.apply();
        return result;
    }

    public static Result restoreDefaults(Context context) {
        Result result = new Result();
        SharedPreferences prefs = prefs(context);
        Map<String, ?> marks = prefs.getAll();
        result.total = marks.size();
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> entry : marks.entrySet()) {
            File cfg = new File(entry.getKey());
            if (!cfg.isFile()) {
                editor.remove(entry.getKey());
                result.skipped++;
                continue;
            }
            try {
                PrivateGameSetting setting =
                        GsonUtils.getPrivateGameSettingFromFile(cfg.getAbsolutePath());
                String recorded = entry.getValue() == null
                        ? "" : String.valueOf(entry.getValue());
                if (setting == null) {
                    editor.remove(entry.getKey());
                    result.skipped++;
                    continue;
                }
                String current = setting.controlLayout == null ? "" : setting.controlLayout;
                if (!Objects.equals(current, recorded)) {
                    // 同步之后被启动器外的手改过：保留，不动。
                    editor.remove(entry.getKey());
                    result.skipped++;
                    continue;
                }
                setting.controlLayout = "Default";
                GsonUtils.savePrivateGameSetting(setting, cfg.getAbsolutePath());
                editor.remove(entry.getKey());
                result.changed++;
            } catch (Throwable t) {
                result.skipped++;
                result.lastError = t.getMessage();
            }
        }
        editor.apply();
        return result;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
