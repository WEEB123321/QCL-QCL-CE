package com.qcl.launcher.launcher.stats;

import android.content.Context;

import com.google.gson.Gson;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ★★★ 社区版新增：本地统计（游戏时长 / 启动次数 / 崩溃次数 / 各实例启动频次）。
 *
 * <p><b>为什么要自己记</b>：QCL 没有任何统计设施，而「我到底玩了多少小时、崩了几次」
 * 是玩家最常问自己的问题，也是排查「是不是某次改动之后开始崩」的重要时间线。
 *
 * <p><b>★ 存储位置</b>：{@code <files>/settings/community_stats.json} —— 与其它设置同目录，
 * 卸载即清除，不涉及数据迁移；用独立文件而不是塞进 {@code launcher_setting.json}，
 * 是为了让统计文件损坏时**不会**连带毁掉启动器设置（后者损坏会自我延续成必崩）。
 *
 * <p><b>★ 全部写操作都吞异常</b>：统计是「锦上添花」的功能，任何一次磁盘写入失败
 * 都不允许影响启动或游戏。写失败最多丢一条统计，绝不能让玩家因此崩一次。
 *
 * <p><b>★ 崩溃次数的口径</b>：只统计 <b>Java 未捕获异常</b>（全局处理器里 +1）。
 * native 段错误（SIGSEGV）走不到这里，所以这个数字是「至少崩了这么多次」，
 * 界面上会如实标注，不假装它是全部。
 */
public final class StatsTracker {

    /** 最近保留的会话条数 —— 再多也没人看，还会把文件撑大 */
    private static final int MAX_SESSIONS = 50;

    private static final String FILE_NAME = "community_stats.json";

    private static final Object LOCK = new Object();
    private static Stats cached;

    private StatsTracker() {
    }

    /** 统计数据结构。字段全部 public + 无参构造，便于 Gson 直接序列化。 */
    public static class Stats {
        /** 累计启动游戏次数 */
        public int launchCount;
        /** 累计 Java 未捕获异常次数（native 崩溃不计入，见类注释） */
        public int crashCount;
        /** 累计游戏时长（毫秒） */
        public long totalPlayMs;
        /** 首次启动时间戳（0 = 还没有记录） */
        public long firstLaunchAt;
        /** 最近一次启动时间戳 */
        public long lastLaunchAt;
        /** 最近若干次会话 */
        public List<Session> sessions = new ArrayList<>();
        /** 各实例的启动次数（实例名 → 次数），用于「最常玩哪个版本」 */
        public Map<String, Integer> instanceLaunches = new LinkedHashMap<>();
        /** 未结束的会话开始时间（进程被杀时会残留，下次 beginSession 时结算） */
        public long openSessionStartAt;
        /** 未结束会话对应的实例名 */
        public String openSessionInstance;
    }

    /** 一次游戏会话。 */
    public static class Session {
        public String instance;
        public long startAt;
        public long durationMs;

        public Session() {
        }

        public Session(String instance, long startAt, long durationMs) {
            this.instance = instance;
            this.startAt = startAt;
            this.durationMs = durationMs;
        }
    }

    // ------------------------------------------------------------------ 读写

    private static String path() {
        return AppManifest.SETTING_DIR + "/" + FILE_NAME;
    }

    /** 取统计（带内存缓存）。任何异常都返回一份空统计，绝不抛出。 */
    public static Stats get(Context context) {
        synchronized (LOCK) {
            if (cached != null) {
                return cached;
            }
            cached = load();
            return cached;
        }
    }

    private static Stats load() {
        try {
            File f = new File(path());
            if (!f.exists()) {
                return new Stats();
            }
            InputStreamReader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
            Stats s;
            try {
                s = new Gson().fromJson(r, Stats.class);
            } finally {
                try {
                    r.close();
                } catch (Throwable ignored) {
                }
            }
            if (s == null) {
                return new Stats();
            }
            // Gson 用 Unsafe 分配对象，不会跑字段初始化器 —— 集合字段可能是 null，必须补齐
            if (s.sessions == null) {
                s.sessions = new ArrayList<>();
            }
            if (s.instanceLaunches == null) {
                s.instanceLaunches = new LinkedHashMap<>();
            }
            return s;
        } catch (Throwable t) {
            // 文件损坏就从头来。绝不把坏文件往外抛 —— 统计不该让启动器崩。
            return new Stats();
        }
    }

    private static void save(Stats s) {
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
        } catch (Throwable ignored) {
            // 写失败只丢统计，不影响任何功能
        }
    }

    // ------------------------------------------------------------------ 记录

    /**
     * 开始一次游戏会话。在游戏 Activity 的 onCreate 里调用。
     * <p>若上一次会话没有被正常结束（进程被杀 / 崩溃），这里会**就地结算**它，
     * 避免时长永久丢失。
     */
    public static void beginSession(Context context, String instance) {
        try {
            Stats s = get(context);
            synchronized (LOCK) {
                long now = System.currentTimeMillis();
                closeOpenSessionLocked(s, now);
                s.openSessionStartAt = now;
                s.openSessionInstance = instance;
                s.launchCount++;
                s.lastLaunchAt = now;
                if (s.firstLaunchAt <= 0L) {
                    s.firstLaunchAt = now;
                }
                if (instance != null && !instance.isEmpty()) {
                    Integer c = s.instanceLaunches.get(instance);
                    s.instanceLaunches.put(instance, c == null ? 1 : c + 1);
                }
                save(s);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 结束一次游戏会话（正常退出）。在游戏 Activity 的 onDestroy 里调用。 */
    public static void endSession(Context context) {
        try {
            Stats s = get(context);
            synchronized (LOCK) {
                closeOpenSessionLocked(s, System.currentTimeMillis());
                save(s);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 记一次 Java 未捕获异常（全局崩溃处理器里调用）。 */
    public static void noteCrash(Context context) {
        try {
            Stats s = get(context);
            synchronized (LOCK) {
                s.crashCount++;
                save(s);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 结算未结束的会话。
     * <p>★ 上限保护：如果开始时间距今超过 24 小时，按 24 小时计 —— 那多半是
     * 进程被系统杀掉、残留了一条几天前的会话，不设上限会把「游戏时长」变成天文数字。
     */
    private static void closeOpenSessionLocked(Stats s, long now) {
        if (s.openSessionStartAt <= 0L) {
            return;
        }
        long dur = now - s.openSessionStartAt;
        if (dur < 0L) {
            dur = 0L;
        }
        if (dur > 24L * 3600L * 1000L) {
            dur = 24L * 3600L * 1000L;
        }
        s.totalPlayMs += dur;
        s.sessions.add(0, new Session(s.openSessionInstance, s.openSessionStartAt, dur));
        while (s.sessions.size() > MAX_SESSIONS) {
            s.sessions.remove(s.sessions.size() - 1);
        }
        s.openSessionStartAt = 0L;
        s.openSessionInstance = null;
    }

    /** 清空全部统计（界面上的「重置」用）。 */
    public static void reset(Context context) {
        synchronized (LOCK) {
            cached = new Stats();
            save(cached);
        }
    }

    // ------------------------------------------------------------------ 展示辅助

    /** 毫秒 → 「X 小时 Y 分」。 */
    public static String formatDuration(long ms) {
        if (ms <= 0L) {
            return "0 " + "min";
        }
        long totalMin = ms / 60000L;
        long h = totalMin / 60L;
        long m = totalMin % 60L;
        if (h <= 0L) {
            return m + " min";
        }
        return h + " h " + m + " min";
    }

    /** 时间戳 → 「yyyy-MM-dd HH:mm」。 */
    public static String formatTime(long ts) {
        if (ts <= 0L) {
            return "-";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                .format(new java.util.Date(ts));
    }
}
