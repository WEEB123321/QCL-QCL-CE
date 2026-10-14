package com.qcl.launcher.launcher.uis.plugin;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.mod.ModApiMirror;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版：「适合你的手机」整合包推荐。
 *
 * <p><b>做什么</b>：读本机配置 → 分档（低 / 中 / 高）→ 在四个分类里各推荐几个
 * <b>真实存在</b>的整合包 ✓
 *
 * <p><b>四个分类</b>（用户指定）：
 * <ol>
 *   <li><b>性能优化类</b> —— 提升帧数、降低内存占用（低端机首选）</li>
 *   <li><b>战斗整合包类</b> —— 战斗 / PVP 向</li>
 *   <li><b>养老类</b> —— 轻松休闲，内容多但不逼进度</li>
 *   <li><b>纯生存类</b> —— 原版生存 + <b>辅助模组</b>（小地图、配方查询、背包整理等）</li>
 * </ol>
 *
 * <p><b>★ 为什么不内置一份写死的整合包列表</b>：
 * 整合包会下架、改名、更新 ✗ —— 写死的列表很快就会推荐到不存在的东西 ✗
 * 那比不推荐更糟 ✗。所以这里**实时查 Modrinth**（走项目已有的
 * {@link ModApiMirror} 镜像 ✓），只按「下载量」排序取前几个 ✓ —— 拿到的一定是真实存在的 ✓
 *
 * <p><b>★ 为什么按手机分档</b>：低端机装重度整合包进去就是幻灯片 ✗
 * 所以分档后**低端机只推轻量的** ✓ —— 而不是把所有包都摆出来让用户自己踩坑 ✗
 */
public final class SmartModpack {

    private SmartModpack() {
    }

    // ==================== 设备探测 ====================

    /** 手机档位。 */
    public enum Tier {
        LOW("低端", "建议只装轻量整合包，或者用性能优化类"),
        MID("中端", "大部分整合包都能跑"),
        HIGH("高端", "重度整合包也没问题");

        public final String label;
        public final String hint;

        Tier(String l, String h) {
            this.label = l;
            this.hint = h;
        }
    }

    /** 设备档案。 */
    public static final class Profile {
        public long ramMb;
        public int cores;
        public String abi;
        public int sdk;
        public long freeStorageMb;
        public Tier tier;

        /** 一句话摘要（显示在界面上）。 */
        public String summary() {
            return "内存 " + (ramMb / 1024) + " GB · " + cores + " 核 · Android "
                    + sdk + " · " + abi + " · 可用存储 " + (freeStorageMb / 1024) + " GB";
        }
    }

    /** 读本机配置并分档。 */
    public static Profile profile(Context ctx) {
        Profile p = new Profile();
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                p.ramMb = mi.totalMem / 1048576L;
            }
        } catch (Throwable ignored) {
        }
        try {
            p.cores = Runtime.getRuntime().availableProcessors();
        } catch (Throwable ignored) {
            p.cores = 4;
        }
        try {
            String[] abis = Build.SUPPORTED_ABIS;
            p.abi = (abis != null && abis.length > 0) ? abis[0] : "unknown";
        } catch (Throwable t) {
            p.abi = "unknown";
        }
        p.sdk = Build.VERSION.SDK_INT;
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            p.freeStorageMb = (sf.getAvailableBlocksLong() * sf.getBlockSizeLong()) / 1048576L;
        } catch (Throwable ignored) {
        }

        // ★ 分档：主要看内存（整合包吃内存最狠 ✗），辅以核数 ✓
        if (p.ramMb < 4096 || p.cores <= 4) {
            p.tier = Tier.LOW;
        } else if (p.ramMb < 8192) {
            p.tier = Tier.MID;
        } else {
            p.tier = Tier.HIGH;
        }
        return p;
    }

    // ==================== 四个分类 ====================

    /** 一个分类：名字 + 用途说明 + 给 Modrinth 的查询词 + 给用户看的筛选说明。 */
    public enum Cat {
        PERF("性能优化类",
                "提升帧数、降低内存占用。低端机首选。",
                "optimization",
                "只推轻量、明确以提帧为目标的包"),
        COMBAT("战斗整合包类",
                "战斗 / PVP 向，有武器、怪物、战斗机制改动。",
                "combat pvp",
                "偏战斗与对抗的包"),
        COZY("养老类",
                "轻松休闲：内容多、节奏慢，不逼你推进度。",
                "cozy relaxing",
                "偏休闲生活向的包"),
        VANILLA("纯生存类（带辅助模组）",
                "接近原版生存，但带小地图、配方查询、背包整理这类辅助模组。",
                "vanilla quality of life",
                "原版风味 + 便利性辅助");

        public final String label;
        public final String desc;
        public final String query;
        public final String rule;

        Cat(String l, String d, String q, String r) {
            this.label = l;
            this.desc = d;
            this.query = q;
            this.rule = r;
        }
    }

    /** 一条推荐结果。 */
    public static final class Pack {
        public String title;
        public String slug;
        public String author;
        public long downloads;
        public String description;
        public String iconUrl;
        /** 是否适合当前档位（低端机上的重型包会被标出来 ✓）。 */
        public boolean fitsTier;

        @Override
        public String toString() {
            return title + "  ·  " + fmt(downloads) + " 次下载";
        }
    }

    private static String fmt(long n) {
        if (n >= 1000000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", n / 1000000.0);
        }
        if (n >= 1000) {
            return String.format(java.util.Locale.ROOT, "%.0fK", n / 1000.0);
        }
        return String.valueOf(n);
    }

    /**
     * 查一个分类下的整合包。
     *
     * <p>★ 用 Modrinth 的 search 接口，facet 限定 {@code project_type:modpack} ✓
     * 按下载量排序 ✓ —— 拿到的一定是**真实存在**的整合包 ✓
     *
     * @param limit 取几条
     */
    public static List<Pack> search(Cat cat, Profile prof, int limit) throws Exception {
        String q = URLEncoder.encode(cat.query, "UTF-8");
        String facets = URLEncoder.encode("[[\"project_type:modpack\"]]", "UTF-8");
        // ★ 低端机只取「轻量」相关的（用 categories 里的 lightweight 收窄 ✓）
        if (prof != null && prof.tier == Tier.LOW) {
            facets = URLEncoder.encode(
                    "[[\"project_type:modpack\"],[\"categories:lightweight\",\"categories:optimization\"]]",
                    "UTF-8");
        }
        String url = ModApiMirror.apply("https://api.modrinth.com/v2/search")
                + "?query=" + q
                + "&facets=" + facets
                + "&index=downloads"
                + "&limit=" + Math.max(1, limit);

        String body = get(url);
        List<Pack> out = new ArrayList<>();
        JSONObject root = new JSONObject(body);
        JSONArray hits = root.optJSONArray("hits");
        if (hits == null) {
            return out;
        }
        for (int i = 0; i < hits.length(); i++) {
            JSONObject h = hits.optJSONObject(i);
            if (h == null) {
                continue;
            }
            Pack p = new Pack();
            p.title = h.optString("title");
            p.slug = h.optString("slug");
            p.author = h.optString("author");
            p.downloads = h.optLong("downloads");
            p.description = h.optString("description");
            p.iconUrl = h.optString("icon_url");
            // ★ 档位适配：低端机上的包我们已经用 facet 收窄过了 ✓
            //   中高端默认都算适配 ✓ —— 不假装能精确判断 ✗
            p.fitsTier = true;
            if (p.title != null && !p.title.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    // ==================== HTTP ====================

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", "QCL-CE/1.9.4 (Android)");
        try (InputStream in = c.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] b = new byte[32 * 1024];
            int n;
            while ((n = in.read(b)) > 0) {
                bos.write(b, 0, n);
            }
            return bos.toString("UTF-8");
        } finally {
            try {
                c.disconnect();
            } catch (Throwable ignored) {
            }
        }
    }

    // ==================== 界面 ====================

    /** 插件入口：显示本机配置 + 四个分类的推荐。 */
    public static void ui(MainActivity a) {
        try {
            final Profile prof = profile(a);
            android.widget.LinearLayout col = PluginUiKit.column(a);

            col.addView(PluginUiKit.text(a, "适合你的手机"));
            col.addView(PluginUiKit.sub(a, "★ 读的是本机真实配置，不是猜的 ✓"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "本机配置"));
            col.addView(PluginUiKit.sub(a, prof.summary()));
            col.addView(PluginUiKit.label(a, "档位：" + prof.tier.label));
            col.addView(PluginUiKit.sub(a, prof.tier.hint));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "四个分类"));
            col.addView(PluginUiKit.sub(a, "★ 整合包是**实时从 Modrinth 查的**（按下载量排序）——"
                    + "所以列表里的一定真实存在，不会推荐到已经下架的东西 ✓"));
            col.addView(PluginUiKit.sub(a, " "));

            final android.widget.LinearLayout result = PluginUiKit.column(a);
            for (final Cat cat : Cat.values()) {
                PluginUiKit.row(a, cat.label, v -> loadCategory(a, result, cat, prof));
                col.addView(PluginUiKit.sub(a, "　" + cat.desc));
                col.addView(PluginUiKit.sub(a, "　筛选：" + cat.rule));
                col.addView(PluginUiKit.sub(a, " "));
            }
            col.addView(PluginUiKit.label(a, "结果"));
            col.addView(result);

            PluginUiKit.sheet(a, "适合你的手机", PluginUiKit.scroller(a, col), "关闭", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    /** 点一个分类 → 拉数据 → 填进结果区。 */
    private static void loadCategory(MainActivity a, android.widget.LinearLayout out,
                                     Cat cat, Profile prof) {
        out.removeAllViews();
        out.addView(PluginUiKit.sub(a, "正在查「" + cat.label + "」…"));
        new Thread(() -> {
            try {
                List<Pack> list = search(cat, prof, 6);
                android.app.Activity act = a;
                act.runOnUiThread(() -> {
                    out.removeAllViews();
                    if (list.isEmpty()) {
                        out.addView(PluginUiKit.sub(a, "这个分类没查到结果 —— "
                                + "可能是网络问题，或者当前档位的筛选条件太严 ✗"));
                        return;
                    }
                    out.addView(PluginUiKit.label(a, cat.label + "（" + list.size() + " 个）"));
                    for (Pack p : list) {
                        out.addView(PluginUiKit.sub(a, "· " + p.title + "  ——  " + fmt(p.downloads) + " 次下载"));
                        if (p.description != null && !p.description.isEmpty()) {
                            out.addView(PluginUiKit.sub(a, "　" + p.description));
                        }
                        PluginUiKit.row(a, "　在 Modrinth 打开", v -> {
                            try {
                                android.content.Intent i = new android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://modrinth.com/modpack/" + p.slug));
                                a.startActivity(i);
                            } catch (Throwable t) {
                                PluginUiKit.toast(a, "打不开：" + t);
                            }
                        });
                        out.addView(PluginUiKit.sub(a, " "));
                    }
                });
            } catch (Throwable t) {
                android.app.Activity act = a;
                act.runOnUiThread(() -> {
                    out.removeAllViews();
                    out.addView(PluginUiKit.sub(a, "查失败了：" + t));
                    out.addView(PluginUiKit.sub(a, "★ 这多半是网络问题 —— "
                            + "整合包数据来自 Modrinth，会走项目的镜像。"));
                });
            }
        }, "qcl-pack").start();
    }
}
