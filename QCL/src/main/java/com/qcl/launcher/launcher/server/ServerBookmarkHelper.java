package com.qcl.launcher.launcher.server;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ★★★ 社区版新增：服务器收藏夹 / 房间历史（#11 + #24）。
 *
 * <p>这两项本质是同一个东西 —— 一份**本地**的服务器地址清单，带备注、分组、收藏、进服次数。
 * 所以做成一份数据、一个界面。
 *
 * <p><b>★ 纯本地，不联网</b>：不做「在线状态 / 延迟」—— 那需要服务器端配合。
 * 这里只记录用户自己填的和自己进过的。界面上如实写清楚，不摆一个永远显示「离线」的假指示灯。
 *
 * <p><b>★ 记录是自动的</b>：每次带地址启动成功，就把该地址的出现次数 +1、更新时间。
 * 所以「房间历史」不需要用户手动维护 —— 进过就自动留下。
 */
public final class ServerBookmarkHelper {

    private static final String TAG = "QCLServerBook";
    private static final String FILE_NAME = "server_bookmarks.json";

    private static final Object LOCK = new Object();
    private static List<Bookmark> cached;

    /** 一条服务器记录。字段全 public，Gson 直存。 */
    public static class Bookmark {
        /** 显示名（用户起的名，或自动填的地址） */
        public String name = "";
        /** host 或 host:port —— 这是真正的唯一键 */
        public String address = "";
        public String note = "";
        /** 分组（自由文本，空 = 未分组） */
        public String group = "";
        public boolean favorite = false;
        public long lastJoinedAt = 0L;
        public int joinCount = 0;

        public Bookmark() {
        }

        public Bookmark(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    private ServerBookmarkHelper() {
    }

    private static String path() {
        return AppManifest.SETTING_DIR + "/" + FILE_NAME;
    }

    // ------------------------------------------------------------------ 读写

    public static List<Bookmark> list() {
        synchronized (LOCK) {
            if (cached == null) {
                cached = load();
            }
            return new ArrayList<Bookmark>(cached);
        }
    }

    /** 排序后的展示用列表：收藏在前，然后按最近进入时间倒序。 */
    public static List<Bookmark> listSorted() {
        List<Bookmark> l = list();
        Collections.sort(l, new Comparator<Bookmark>() {
            @Override
            public int compare(Bookmark a, Bookmark b) {
                if (a.favorite != b.favorite) {
                    return a.favorite ? -1 : 1;
                }
                return Long.compare(b.lastJoinedAt, a.lastJoinedAt);
            }
        });
        return l;
    }

    private static List<Bookmark> load() {
        List<Bookmark> out = new ArrayList<Bookmark>();
        try {
            File f = new File(path());
            if (!f.isFile() || f.length() == 0) {
                return out;
            }
            InputStreamReader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
            try {
                Type t = new TypeToken<List<Bookmark>>() {
                }.getType();
                List<Bookmark> l = new Gson().fromJson(r, t);
                if (l != null) {
                    for (Bookmark b : l) {
                        if (b != null && b.address != null && !b.address.isEmpty()) {
                            out.add(b);
                        }
                    }
                }
            } finally {
                r.close();
            }
        } catch (Throwable t) {
            Log.w(TAG, "读取服务器收藏失败（按空处理）: " + t);
        }
        return out;
    }

    public static void save(List<Bookmark> list) {
        synchronized (LOCK) {
            cached = new ArrayList<Bookmark>(list);
            OutputStreamWriter w = null;
            File tmp = null;
            try {
                File dst = new File(path());
                File dir = dst.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                // 先写临时文件再改名：中途被杀也不会留下半截 JSON 把下次读取搞坏
                tmp = new File(path() + ".tmp");
                FileOutputStream fos = new FileOutputStream(tmp);
                w = new OutputStreamWriter(fos, "UTF-8");
                w.write(new Gson().toJson(list));
                w.flush();
                w.close();
                w = null;
                if (dst.exists()) {
                    dst.delete();
                }
                if (!tmp.renameTo(dst)) {
                    Log.w(TAG, "服务器收藏改名失败");
                }
                tmp = null;
            } catch (Throwable t) {
                Log.w(TAG, "保存服务器收藏失败: " + t);
            } finally {
                if (w != null) {
                    try {
                        w.close();
                    } catch (Throwable ignored) {
                    }
                }
                if (tmp != null && tmp.exists()) {
                    tmp.delete();
                }
            }
        }
    }

    // ------------------------------------------------------------------ 增删改

    public static void upsert(Bookmark b) {
        if (b == null || b.address == null || b.address.isEmpty()) {
            return;
        }
        List<Bookmark> l = list();
        for (Bookmark x : l) {
            if (b.address.equals(x.address)) {
                x.name = b.name;
                x.note = b.note;
                x.group = b.group;
                x.favorite = b.favorite;
                save(l);
                return;
            }
        }
        l.add(b);
        save(l);
    }

    public static void remove(String address) {
        if (address == null) {
            return;
        }
        List<Bookmark> l = list();
        for (int i = l.size() - 1; i >= 0; i--) {
            if (address.equals(l.get(i).address)) {
                l.remove(i);
            }
        }
        save(l);
    }

    public static void toggleFavorite(String address) {
        List<Bookmark> l = list();
        for (Bookmark x : l) {
            if (x.address.equals(address)) {
                x.favorite = !x.favorite;
            }
        }
        save(l);
    }

    /**
     * ★ 自动记录一次「进过这个服务器」。
     *
     * <p>在启动流程里调用（带地址启动时）。已存在就 +1 并更新时间；不存在就**自动建一条** ——
     * 这样「房间历史」不用用户手动维护，进过就留下。
     */
    public static void record(String address) {
        try {
            if (address == null || address.isEmpty()) {
                return;
            }
            List<Bookmark> l = list();
            for (Bookmark x : l) {
                if (address.equals(x.address)) {
                    x.joinCount++;
                    x.lastJoinedAt = System.currentTimeMillis();
                    if (x.name == null || x.name.isEmpty()) {
                        x.name = address;
                    }
                    save(l);
                    return;
                }
            }
            Bookmark b = new Bookmark(address, address);
            b.joinCount = 1;
            b.lastJoinedAt = System.currentTimeMillis();
            l.add(b);
            save(l);
        } catch (Throwable t) {
            Log.w(TAG, "记录服务器历史失败（已忽略）: " + t);
        }
    }

    /** 所有出现过的分组名（用于编辑时的候选）。 */
    public static Set<String> groups() {
        Set<String> s = new LinkedHashSet<String>();
        for (Bookmark b : list()) {
            if (b.group != null && !b.group.isEmpty()) {
                s.add(b.group);
            }
        }
        return s;
    }
}
