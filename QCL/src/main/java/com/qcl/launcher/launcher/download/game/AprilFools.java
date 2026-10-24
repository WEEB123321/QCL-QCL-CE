package com.qcl.launcher.launcher.download.game;

import android.content.Context;

import com.google.gson.Gson;
import com.qcl.launcher.utils.file.AssetsUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ★★★ v3.0.5：愚人节版本清单（{@code april_fools_versions.json}）。
 *
 * <h3>为什么需要一份单独的清单，而不是靠 type 判断</h3>
 * 这些版本在 Mojang 官方清单里<b>全部是 {@code type=snapshot}</b>，和普通周快照长得一模一样
 * （{@code 15w14a}、{@code 23w13a_or_b}、{@code 24w14potato} 都是），{@code 2.0} 更极端 ——
 * Mojang 压根没给它发过元数据，只在民间档案里存在。
 * 也就是说：<b>「是不是愚人节版」这件事无法从清单字段推导，只能靠一份人工维护的名单。</b>
 *
 * <h3>这份名单同时承担两个职责</h3>
 * <ol>
 *   <li>给下载页提供独立的「愚人节」分类 —— 用户明确要求「增加一个愚人节版的分类」；</li>
 *   <li><b>把它们从正式版 / 测试版 / 远古版里摘出去</b>。这一步比第 1 条更重要：
 *       一个只想找 1.20 正式版的人，不该在正式版列表里翻到「24w14potato」；
 *       而想玩愚人节版的人，也不该在一千多个快照里一个个找。</li>
 * </ol>
 *
 * <p>★ 只读、缓存一次、任何异常都当作「不是愚人节版」—— 这份名单坏了不能连累整个版本列表。
 * 和 {@link LegacyVersionArchive} 同一套防御风格。
 */
public final class AprilFools {

    private static final String ASSET_PATH = "april_fools_versions.json";

    /** 缓存；null = 还没读过。读失败会缓存成空集合，避免每次列表滚动都重试一次 IO。 */
    private static Set<String> cache;

    private AprilFools() {
    }

    private static final class Entry {
        private String id;
        private String name;
        private String date;
        private String source;
        private String note;
    }

    private static final class Root {
        private String note;
        private List<Entry> entries;
    }

    /** 全部愚人节版本的 id（只读、顺序稳定）。永不返回 null。 */
    public static synchronized Set<String> ids(Context context) {
        if (cache != null) {
            return cache;
        }
        Set<String> result = new LinkedHashSet<>();
        try {
            String raw = AssetsUtils.readAssetsTxt(context, ASSET_PATH);
            Root root = new Gson().fromJson(raw, Root.class);
            if (root != null && root.entries != null) {
                for (Entry entry : root.entries) {
                    if (entry != null && entry.id != null && !entry.id.isEmpty()) {
                        result.add(entry.id);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 名单读不出来就当没有愚人节版：宁可少一个分类，也不能让版本列表崩掉
        }
        cache = Collections.unmodifiableSet(result);
        return cache;
    }

    /** 这个 id 是不是愚人节版本。 */
    public static boolean is(Context context, String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return ids(context).contains(id);
    }
}
