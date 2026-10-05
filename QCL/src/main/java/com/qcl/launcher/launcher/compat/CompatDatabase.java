package com.qcl.launcher.launcher.compat;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版新增：本地兼容性知识库。
 *
 * <p><b>它到底是什么</b>：一份<b>存在本机、可增删、可导入导出</b>的「某版本 + 某加载器 =
 * 能用 / 有坑」清单。内置若干条公认的常识（例如 OptiFine 与 Sodium 同时装必崩），
 * 其余由用户自己积累。
 *
 * <p><b>★ 为什么不叫「数据库」就完事</b>：我没有一份权威、实时更新的兼容性数据源，
 * 也<b>不建服务器</b>（需求书明确）。所以这里<b>不假装</b>它是「全网兼容性库」——
 * 内置条目在界面上标为「内置」，用户自己加的在界面上标为「自建」，
 * 用户能一眼看出哪条是自己写的。宁可少，不做假。
 *
 * <p><b>★ 匹配规则</b>：版本号用前缀匹配（{@code 1.12} 能命中 {@code 1.12.2}），
 * 加载器/版本写 {@code *} 表示「任意」。命中越具体的排越前。
 */
public final class CompatDatabase {

    private static final String TAG = "QCLCompat";

    private static final String FILE_NAME = "compat_notes.json";

    /** 一条兼容性记录。字段全 public，便于 Gson 直存与导入导出。 */
    public static class Entry {
        /** 适用 MC 版本，支持前缀匹配；"*" = 任意 */
        public String mcVersion = "*";
        /** 加载器：Forge / Fabric / NeoForge / OptiFine / Babric / * */
        public String loader = "*";
        /** 结论：ok = 可用；warn = 有坑；bad = 不兼容 */
        public String status = "warn";
        /** 说明 */
        public String note = "";
        /** true = 内置条目（不可删）；false = 用户自建 */
        public boolean builtin;

        public Entry() {
        }

        public Entry(String mcVersion, String loader, String status, String note, boolean builtin) {
            this.mcVersion = mcVersion;
            this.loader = loader;
            this.status = status;
            this.note = note;
            this.builtin = builtin;
        }
    }

    private CompatDatabase() {
    }

    /** 内置条目。只放**公认**的常识，不放我没把握的。 */
    public static List<Entry> builtin() {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry("1.12.2", "Forge", "ok",
                "最成熟的 Forge 版本，绝大多数模组都以它为基准开发。", true));
        out.add(new Entry("1.7.10", "Forge", "ok",
                "远古模组的黄金版本，模组数量极大、稳定性好。", true));
        out.add(new Entry("1.13", "Forge", "warn",
                "Forge 在这个版本重写了加载机制，模组生态薄弱、崩溃较多，不建议新手尝试。", true));
        out.add(new Entry("1.14", "Forge", "warn",
                "同 1.13：Forge 重写期，模组支持不完整。", true));
        out.add(new Entry("1.16.5", "Fabric", "ok",
                "Fabric 生态在这个版本已经成熟，性能模组选择多。", true));
        out.add(new Entry("*", "OptiFine", "warn",
                "OptiFine 与 Fabric 同时使用需要额外装 OptiFabric，且与 Sodium 等性能模组冲突。", true));
        out.add(new Entry("*", "Sodium", "bad",
                "Sodium 与 OptiFine 都会替换渲染管线，同时安装必定崩溃，只能二选一。", true));
        out.add(new Entry("1.20.2", "NeoForge", "ok",
                "NeoForge 从 1.20.2 起独立于 Forge，1.20.2 及以上请优先选它。", true));
        out.add(new Entry("*", "Babric", "warn",
                "Babric 是给远古版本（b1.7.3 一类）用的 Fabric 分支，需要专门的加载器版本，不能和现代 Fabric 混用。", true));
        out.add(new Entry("*", "Forge", "warn",
                "同一个实例里不要混装 Forge 与 Fabric 模组 —— 它们不是同一套加载机制。", true));
        return out;
    }

    // ------------------------------------------------------------------ 存取

    private static String path() {
        return AppManifest.SETTING_DIR + "/" + FILE_NAME;
    }

    /** 用户自建条目（只读这份；内置的由 {@link #builtin()} 提供）。 */
    public static List<Entry> loadUser() {
        try {
            File f = new File(path());
            if (!f.exists()) {
                return new ArrayList<>();
            }
            InputStreamReader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
            List<Entry> list;
            try {
                list = new Gson().fromJson(r, new TypeToken<List<Entry>>() { }.getType());
            } finally {
                try {
                    r.close();
                } catch (Throwable ignored) {
                }
            }
            return list == null ? new ArrayList<Entry>() : list;
        } catch (Throwable t) {
            Log.w(TAG, "读兼容性库失败: " + t);
            return new ArrayList<>();
        }
    }

    public static void saveUser(List<Entry> list) {
        try {
            File f = new File(path());
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f, false), "UTF-8");
            try {
                w.write(new GsonBuilder().setPrettyPrinting().create().toJson(list));
            } finally {
                try {
                    w.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "写兼容性库失败: " + t);
        }
    }

    public static void add(Context context, Entry e) {
        List<Entry> list = loadUser();
        e.builtin = false;
        list.add(e);
        saveUser(list);
    }

    public static void remove(Context context, Entry e) {
        List<Entry> list = loadUser();
        for (int i = list.size() - 1; i >= 0; i--) {
            Entry x = list.get(i);
            if (same(x, e)) {
                list.remove(i);
            }
        }
        saveUser(list);
    }

    private static boolean same(Entry a, Entry b) {
        return eq(a.mcVersion, b.mcVersion) && eq(a.loader, b.loader) && eq(a.note, b.note);
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    // ------------------------------------------------------------------ 查询

    /** 全部条目（内置在前，自建在后）。 */
    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>(builtin());
        out.addAll(loadUser());
        return out;
    }

    /**
     * 按「版本 + 加载器」查匹配条目，具体度高的排前面。
     *
     * @param mcVersion 当前实例的 MC 版本（可空）
     * @param loader    加载器名（可空）
     */
    public static List<Entry> query(String mcVersion, String loader) {
        List<Entry> hits = new ArrayList<>();
        for (Entry e : all()) {
            if (matches(e.mcVersion, mcVersion) && matches(e.loader, loader)) {
                hits.add(e);
            }
        }
        // 具体度：不是 "*" 的算 1 分
        hits.sort((a, b) -> Integer.compare(score(b), score(a)));
        return hits;
    }

    private static int score(Entry e) {
        int s = 0;
        if (!"*".equals(e.mcVersion)) {
            s++;
        }
        if (!"*".equals(e.loader)) {
            s++;
        }
        return s;
    }

    /** 版本前缀匹配：条目写 1.12 能命中 1.12.2；条目写 * 命中一切。 */
    private static boolean matches(String pattern, String actual) {
        if (pattern == null || "*".equals(pattern)) {
            return true;
        }
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        return actual.startsWith(pattern) || pattern.startsWith(actual);
    }

    /** 自由文本搜索（版本 / 加载器 / 说明）。 */
    public static List<Entry> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return all();
        }
        String k = keyword.trim().toLowerCase();
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) {
            if (contains(e.mcVersion, k) || contains(e.loader, k) || contains(e.note, k)) {
                out.add(e);
            }
        }
        return out;
    }

    private static boolean contains(String s, String k) {
        return s != null && s.toLowerCase().contains(k);
    }

    // ------------------------------------------------------------------ 导入导出

    public static String exportJson() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(all());
    }

    /**
     * 从 JSON 导入。★ 只接受用户条目，内置条目标记会被清掉 ——
     * 否则导入一份含内置条目的导出文件会得到重复项。
     *
     * @return 成功导入的条数；解析失败返回 -1
     */
    public static int importJson(String json) {
        try {
            List<Entry> parsed = new Gson().fromJson(json,
                    new TypeToken<List<Entry>>() { }.getType());
            if (parsed == null) {
                return -1;
            }
            List<Entry> user = loadUser();
            int added = 0;
            for (Entry e : parsed) {
                if (e == null || e.note == null) {
                    continue;
                }
                e.builtin = false;
                user.add(e);
                added++;
            }
            saveUser(user);
            return added;
        } catch (Throwable t) {
            Log.w(TAG, "导入兼容性库失败: " + t);
            return -1;
        }
    }
}
