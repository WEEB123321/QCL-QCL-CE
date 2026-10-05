package com.qcl.launcher.launcher.autotask;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.qcl.launcher.launcher.StartupTrace;
import com.qcl.launcher.launcher.backup.BackupRecord;
import com.qcl.launcher.launcher.backup.InstanceBackupHelper;
import com.qcl.launcher.launcher.backup.world.WorldBackupHelper;
import com.qcl.launcher.launcher.backup.world.WorldBackupRecord;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ★★★ 社区版新增：自动任务（定时备份 / 启动前备份 / 崩溃后备份 / 定时清理缓存 / 保留份数）。
 *
 * <p><b>★ 为什么不用 WorkManager / AlarmManager</b>：这是个<b>启动器</b> ——
 * 玩家每次想玩游戏都必然先打开它，所以「打开时检查是否到期」在语义上等价于定时器，
 * 而且有三个实打实的好处：
 * <ol>
 *   <li>不需要额外权限，也不会被各 ROM 的后台限制杀掉；</li>
 *   <li>不常驻、不唤醒 CPU，省电；</li>
 *   <li>执行时机永远在「玩家正要用它」的时候，备份与清理都发生在用户看得见的地方。</li>
 * </ol>
 * 代价是「到期」最多延后到下次打开 —— 对备份/清理这类任务完全可以接受，界面上如实说明。
 *
 * <p><b>★ 绝不阻塞</b>：所有任务在后台线程执行，且调用方（MainActivity.init 的后台线程）
 * 本来就不在主线程。任何异常都被吞掉 —— 自动任务失败不该影响启动器启动。
 */
public final class AutoTaskRunner {

    private static final String TAG = "QCLAutoTask";

    private static final String FILE_NAME = "auto_task.json";

    private static final Object LOCK = new Object();
    private static Setting cached;

    private AutoTaskRunner() {
    }

    /** 自动任务设置。字段全 public，Gson 直存。 */
    public static class Setting {
        /** 定时备份：每 N 天一次（0 = 关闭） */
        public int scheduledBackupIntervalDays = 0;
        /** 启动游戏前备份当前实例 */
        public boolean backupBeforeLaunch = false;
        /** 上次启动异常结束时，本次打开启动器补一份备份 */
        public boolean backupAfterCrash = false;
        /** 定时清理缓存（每次到期执行一次） */
        public boolean cleanCacheOnSchedule = false;
        /**
         * 备份保留份数。<b>0 = 不限制（默认）</b>。
         *
         * <p>★★★ 默认必须是 0：这是「删除用户数据」的开关。如果默认 5，用户只要打开一次启动器，
         * 手里第 6 份以上的备份就会被静默删掉 —— 他既没要求、也没被通知，而且不可恢复。
         * 宁可让磁盘被备份占满（用户看得见、能自己删），也绝不替用户做删除决定。
         */
        public int keepBackups = 0;

        /**
         * ★★★ 社区版新增：**退出游戏后自动备份存档**（#28）。默认关。
         *
         * <p>为什么默认关：存档可能几百 MB，每退一次游戏就复制一份，磁盘消耗很实在。
         * 这种事必须由用户自己开 —— 但也正因为它是「防丢档」的，值得做成一个开关而不是没有。
         */
        public boolean backupWorldsOnExit = false;

        /**
         * ★★★ 社区版新增：存档备份保留份数（按世界分别计算）。默认 3，0 = 不限制。
         *
         * <p>★ 与 {@link #keepBackups}（实例备份）不同，这里**默认给 3** ——
         * 因为这一项**只在用户主动打开「退出后自动备份」之后才起作用**：
         * 用户既然开了「每次退出都备份」，那必然需要一个自动回收机制，
         * 否则几天就把磁盘写满。也就是说这个「删除」是在用户明确开启的前提下才发生的，
         * 不违背「绝不替用户做删除决定」的原则。
         */
        public int keepWorldBackups = 3;

        /** 上次定时任务执行时间 */
        public long lastScheduledRunAt = 0L;
    }

    private static String path() {
        return AppManifest.SETTING_DIR + "/" + FILE_NAME;
    }

    public static Setting get(Context context) {
        synchronized (LOCK) {
            if (cached == null) {
                cached = load();
            }
            return cached;
        }
    }

    private static Setting load() {
        try {
            File f = new File(path());
            if (!f.exists()) {
                return new Setting();
            }
            InputStreamReader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
            Setting s;
            try {
                s = new Gson().fromJson(r, Setting.class);
            } finally {
                try {
                    r.close();
                } catch (Throwable ignored) {
                }
            }
            return s == null ? new Setting() : s;
        } catch (Throwable t) {
            return new Setting();
        }
    }

    public static void save(Context context, Setting s) {
        synchronized (LOCK) {
            cached = s;
            try {
                File f = new File(path());
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
                try {
                    w.write(new Gson().toJson(s));
                } finally {
                    try {
                        w.close();
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "写自动任务设置失败: " + t);
            }
        }
    }

    // ------------------------------------------------------------------ 执行入口

    /**
     * 每次打开启动器时调用（必须在后台线程）。
     *
     * @param currentVersionPath 当前选中实例的目录；为空则跳过一切与实例相关的任务
     */
    public static void onLauncherOpened(Context context, String currentVersionPath) {
        try {
            Setting s = get(context);
            long now = System.currentTimeMillis();

            // ① 崩溃后补备份：判据用 lastRunWasAbnormal（含系统侧崩溃），
            //    这正是「游戏崩了想回到崩之前」的场景，与安全模式用的判据不同（后者只看 Java 路标）。
            if (s.backupAfterCrash && StartupTrace.lastRunWasAbnormal()) {
                backupInstance(context, currentVersionPath, "crash");
            }

            // ② 定时备份 / 定时清理
            long interval = (long) s.scheduledBackupIntervalDays * 24L * 3600L * 1000L;
            boolean due = s.scheduledBackupIntervalDays > 0
                    && (s.lastScheduledRunAt <= 0L || now - s.lastScheduledRunAt >= interval);
            if (due) {
                if (s.cleanCacheOnSchedule) {
                    cleanCache(context);
                }
                backupInstance(context, currentVersionPath, "scheduled");
                s.lastScheduledRunAt = now;
                save(context, s);
            }

            // ③ 保留份数：无论上面有没有跑，都顺手裁剪一次（用户可能刚把份数调小）
            prune(context, s.keepBackups);
        } catch (Throwable t) {
            Log.w(TAG, "自动任务执行失败（已忽略）: " + t);
        }
    }

    /** 启动游戏前调用（必须在后台线程）。 */
    public static void onGameLaunch(Context context, String currentVersionPath) {
        try {
            Setting s = get(context);
            if (s.backupBeforeLaunch) {
                backupInstance(context, currentVersionPath, "prelaunch");
            }
        } catch (Throwable t) {
            Log.w(TAG, "启动前备份失败（已忽略）: " + t);
        }
    }

    /**
     * ★★★ 社区版新增：退出游戏后自动备份存档（#28）。
     *
     * <p>在 {@code PojavMinecraftActivity.onDestroy} 里调用，**不阻塞退出**（只起线程）。
     *
     * <p>★ 只备份「看起来真的是存档」的目录（必须含 {@code level.dat}）——
     * saves 目录下可能还有用户自己放的说明文件、或者别的工具的临时目录，
     * 无差别全备份既浪费空间也会让备份列表变得莫名其妙。
     */
    public static void onGameExit(Context context, String gameDir) {
        try {
            final Setting s = get(context);
            if (!s.backupWorldsOnExit) {
                return;
            }
            if (gameDir == null || gameDir.isEmpty()) {
                return;
            }
            final File gd = new File(gameDir);
            final int keep = s.keepWorldBackups;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File saves = new File(gd, "saves");
                        File[] worlds = saves.listFiles();
                        if (worlds == null) {
                            return;
                        }
                        int n = 0;
                        for (File w : worlds) {
                            if (!w.isDirectory() || !new File(w, "level.dat").isFile()) {
                                continue;
                            }
                            WorldBackupHelper.createBackup(gd, w.getName(), w.getName(), w, false, null);
                            pruneWorldBackups(gd, w.getName(), keep);
                            n++;
                        }
                        Log.i(TAG, "退出后自动备份存档：本次 " + n + " 个世界，保留 " + keep + " 份");
                    } catch (Throwable t) {
                        Log.w(TAG, "退出后自动备份存档失败（已忽略）: " + t);
                    }
                }
            }, "qcl-world-autobackup").start();
        } catch (Throwable t) {
            Log.w(TAG, "退出后自动备份存档失败（已忽略）: " + t);
        }
    }

    /** 按世界回收旧备份：保留最新的 keep 份（keep <= 0 表示不限制）。 */
    private static void pruneWorldBackups(File gameDir, String worldKey, int keep) {
        if (keep <= 0) {
            return;
        }
        try {
            List<WorldBackupRecord> list = WorldBackupHelper.listRecords(gameDir, worldKey);
            if (list == null || list.size() <= keep) {
                return;
            }
            // 按创建时间**从新到旧**排序后再删，避免依赖索引里的原始顺序
            Collections.sort(list, new Comparator<WorldBackupRecord>() {
                @Override
                public int compare(WorldBackupRecord a, WorldBackupRecord b) {
                    return Long.compare(b.createdAt, a.createdAt);
                }
            });
            for (int i = keep; i < list.size(); i++) {
                WorldBackupHelper.delete(gameDir, list.get(i));
            }
        } catch (Throwable t) {
            Log.w(TAG, "回收旧存档备份失败（已忽略）: " + t);
        }
    }

    // ------------------------------------------------------------------ 具体任务

    private static void backupInstance(Context context, String versionPath, String reason) {
        try {
            if (versionPath == null || versionPath.isEmpty()) {
                return;
            }
            File dir = new File(versionPath);
            if (!dir.isDirectory()) {
                return;
            }
            String name = dir.getName();
            BackupRecord r = InstanceBackupHelper.createBackup(name + " (" + reason + ")", dir, null);
            Log.i(TAG, "自动备份(" + reason + ") " + (r != null ? "成功" : "失败") + " : " + name);
        } catch (Throwable t) {
            Log.w(TAG, "自动备份失败(" + reason + "): " + t);
        }
    }

    /** 清理缓存目录。★ 只清 cache 下的 install/saves 等可再生数据，绝不碰游戏目录。 */
    private static void cleanCache(Context context) {
        try {
            File cache = context.getCacheDir();
            if (cache != null) {
                clearChildren(cache);
            }
            File install = new File(AppManifest.INSTALL_DIR);
            if (install.exists()) {
                clearChildren(install);
            }
            Log.i(TAG, "定时清理缓存完成");
        } catch (Throwable t) {
            Log.w(TAG, "定时清理缓存失败: " + t);
        }
    }

    private static void clearChildren(File dir) {
        try {
            File[] children = dir.listFiles();
            if (children == null) {
                return;
            }
            for (File c : children) {
                // 只删「缓存/安装临时件」；用同一个受限递归删除，作用域仅限 cache 子树
                InstanceBackupHelper.deleteRecursive(c);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 按保留份数裁剪最旧的备份。0 = 不限制。 */
    private static void prune(Context context, int keep) {
        try {
            if (keep <= 0) {
                return;
            }
            List<BackupRecord> records = InstanceBackupHelper.listRecords();  // 已按时间倒序
            for (int i = keep; i < records.size(); i++) {
                InstanceBackupHelper.delete(records.get(i));
            }
        } catch (Throwable t) {
            Log.w(TAG, "裁剪旧备份失败: " + t);
        }
    }
}
