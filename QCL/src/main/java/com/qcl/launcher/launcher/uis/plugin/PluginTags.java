package com.qcl.launcher.launcher.uis.plugin;

import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ★★★ 社区版：资源标签系统（第 29-36 项里「标签」类的统一实现）。
 *
 * <p><b>为什么做成一个通用类</b>：用户清单里第 30/31/32/35 项分别是
 * 资源包标签、光影标签、存档标签、皮肤标签 —— 它们的**逻辑完全一样**，
 * 只是「标签预设」和「被标记的对象」不同。
 * 写四遍等于四倍的 bug 面 ✗ —— 抽成一套，四处复用。
 *
 * <p><b>存储</b>：{@code SETTING_DIR/qcl_tags_<kind>.txt}，一行一条
 * {@code 文件名<TAB>标签1,标签2}。文件不存在 = 还没打过标签。
 *
 * <p><b>筛选</b>：主界面列出所有条目，勾选标签后实时过滤 —— 纯本地，不联网。
 */
public final class PluginTags {

    private PluginTags() {
    }

    /** 支持的四类标签。 */
    public static final String KIND_PACK = "pack";
    public static final String KIND_SHADER = "shader";
    public static final String KIND_WORLD = "world";
    public static final String KIND_SKIN = "skin";

    // ==================== 四个入口 ====================

    /** 第 30 项：资源包标签（材质 / 音效 / 界面…）。 */
    public static void packTags(MainActivity a) {
        open(a, KIND_PACK, "资源包标签",
                new String[]{"材质", "音效", "界面", "字体", "整合", "其它"},
                "resourcepacks", ".zip");
    }

    /** 第 31 项：光影标签（性能 / 风格…）。 */
    public static void shaderTags(MainActivity a) {
        open(a, KIND_SHADER, "光影标签",
                new String[]{"低配", "中配", "高配", "写实", "卡通", "原版风"},
                "shaderpacks", ".zip");
    }

    /** 第 32 项：存档标签（生存 / 创造 / 冒险…）。 */
    public static void worldTags(MainActivity a) {
        open(a, KIND_WORLD, "存档标签",
                new String[]{"生存", "创造", "冒险", "建筑", "红石", "空岛", "模组"},
                "saves", "");
    }

    /** 第 35 项：皮肤标签（简约 / 华丽 / 动漫…）。 */
    public static void skinTags(MainActivity a) {
        open(a, KIND_SKIN, "皮肤标签",
                new String[]{"简约", "华丽", "动漫", "写实", "像素", "自定义"},
                "skins", ".png");
    }

    // ==================== 通用实现 ====================

    private static void open(final MainActivity a, final String kind, final String title,
                             final String[] presets, final String subDir, final String ext) {
        try {
            final File dir = new File(AppManifest.GAME_FILE_DIRECTORY_DIR, subDir);
            final List<String> items = listNames(dir, ext);
            if (items.isEmpty()) {
                PluginUiKit.info(a, title, "目录里没有可标记的条目：\n" + dir.getAbsolutePath());
                return;
            }
            final Map<String, List<String>> map = load(kind);

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "共 " + items.size() + " 个条目"));
            col.addView(PluginUiKit.sub(a, "标签只存在本地，不上传、不改文件。"));

            // 筛选行：勾选预设标签
            col.addView(PluginUiKit.label(a, "按标签筛选"));
            final LinearLayout filterBox = new LinearLayout(a);
            filterBox.setOrientation(LinearLayout.VERTICAL);
            final List<String> active = new ArrayList<>();
            for (final String p : presets) {
                CheckBox cb = new CheckBox(a);
                cb.setText(p);
                cb.setTextSize(13f);
                cb.setOnCheckedChangeListener((b, checked) -> {
                    if (checked) {
                        if (!active.contains(p)) {
                            active.add(p);
                        }
                    } else {
                        active.remove(p);
                    }
                });
                filterBox.addView(cb);
            }
            col.addView(filterBox);

            // 条目列表：每个条目后面跟一排预设标签按钮
            col.addView(PluginUiKit.label(a, "给条目打标签"));
            final LinearLayout listBox = new LinearLayout(a);
            listBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(listBox);

            final Runnable[] rebuildHolder = new Runnable[1];
            rebuildHolder[0] = () -> {
                try {
                    listBox.removeAllViews();
                    int shownCount = 0;
                    for (final String name : items) {
                        List<String> tags = map.get(name);
                        if (tags == null) {
                            tags = new ArrayList<>();
                        }
                        // 筛选：勾了标签就只显示命中的
                        if (!active.isEmpty()) {
                            boolean hit = false;
                            for (String t : active) {
                                if (tags.contains(t)) {
                                    hit = true;
                                    break;
                                }
                            }
                            if (!hit) {
                                continue;
                            }
                        }
                        shownCount++;
                        LinearLayout card = PluginUiKit.card(a);
                        card.addView(PluginUiKit.text(a, name));
                        card.addView(PluginUiKit.sub(a, tags.isEmpty() ? "（无标签）" : String.join(" · ", tags)));

                        // 预设标签开关行
                        LinearLayout tagRow = new LinearLayout(a);
                        tagRow.setOrientation(LinearLayout.HORIZONTAL);
                        tagRow.setPadding(0, PluginUiKit.dp(a, 4), 0, 0);
                        final List<String> cur = tags;
                        for (final String p : presets) {
                            TextView chip = new TextView(a);
                            boolean on = cur.contains(p);
                            chip.setText(p);
                            chip.setTextSize(12f);
                            chip.setTextColor(on ? 0xFFFFFFFF : UiPalette.ACCENT);
                            chip.setBackgroundColor(on ? UiPalette.ACCENT : UiPalette.SURFACE);
                            int px = PluginUiKit.dp(a, 7);
                            chip.setPadding(px, px / 2, px, px / 2);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT);
                            lp.rightMargin = PluginUiKit.dp(a, 5);
                            chip.setLayoutParams(lp);
                            chip.setClickable(true);
                            PluginUiKit.pressEffect(chip);
                            chip.setOnClickListener(v -> {
                                if (cur.contains(p)) {
                                    cur.remove(p);
                                } else {
                                    cur.add(p);
                                }
                                map.put(name, cur);
                                rebuildHolder[0].run();
                            });
                            tagRow.addView(chip);
                        }
                        card.addView(tagRow);
                        listBox.addView(card);
                    }
                    if (shownCount == 0) {
                        listBox.addView(PluginUiKit.sub(a, "没有符合筛选的条目。"));
                    }
                } catch (Throwable ignored) {
                }
            };
            rebuildHolder[0].run();

            PluginUiKit.sheet(a, title, PluginUiKit.scroller(a, col), "保存", () -> {
                save(kind, map);
                PluginUiKit.log(a, title + "：已保存标签");
                PluginUiKit.toast(a, "已保存");
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "标签面板出错：" + t);
        }
    }

    // ==================== 读写 ====================

    private static File file(String kind) {
        return new File(AppManifest.SETTING_DIR, "qcl_tags_" + kind + ".txt");
    }

    private static Map<String, List<String>> load(String kind) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        try {
            File f = file(kind);
            if (!f.isFile()) {
                return map;
            }
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                //noinspection ResultOfMethodCallIgnored
                in.read(b);
            }
            for (String line : new String(b, "UTF-8").split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                String[] parts = t.split("\t");
                List<String> tags = new ArrayList<>();
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    for (String x : parts[1].split(",")) {
                        if (!x.trim().isEmpty()) {
                            tags.add(x.trim());
                        }
                    }
                }
                map.put(parts[0], tags);
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    private static void save(String kind, Map<String, List<String>> map) {
        try {
            File f = file(kind);
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<String>> e : map.entrySet()) {
                if (e.getValue().isEmpty()) {
                    continue;
                }
                sb.append(e.getKey()).append('\t').append(String.join(",", e.getValue())).append('\n');
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(sb.toString().getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 列出目录下的条目名（过滤扩展名）。 */
    private static List<String> listNames(File dir, String ext) {
        List<String> out = new ArrayList<>();
        try {
            File[] fs = dir.listFiles();
            if (fs == null) {
                return out;
            }
            for (File f : fs) {
                if (f == null) {
                    continue;
                }
                if (ext.isEmpty()) {
                    if (f.isDirectory()) {
                        out.add(f.getName());
                    }
                } else if (f.isFile() && f.getName().toLowerCase().endsWith(ext)) {
                    out.add(f.getName());
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 第 33 项：存档快速定位（按名字搜索 + 按时间/大小排序）。 */
    public static void worldFinder(MainActivity a) {
        try {
            File saves = new File(AppManifest.GAME_FILE_DIRECTORY_DIR, "saves");
            File[] fs = saves.listFiles();
            if (fs == null || fs.length == 0) {
                PluginUiKit.info(a, "存档快速定位", "还没有存档。");
                return;
            }
            final List<File> worlds = new ArrayList<>();
            for (File f : fs) {
                if (f != null && f.isDirectory()) {
                    worlds.add(f);
                }
            }

            LinearLayout col = PluginUiKit.column(a);
            EditText kw = new EditText(a);
            kw.setHint("搜索存档名");
            col.addView(kw);
            final LinearLayout listBox = new LinearLayout(a);
            listBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(listBox);

            final Runnable[] rebuildHolder = new Runnable[1];
            rebuildHolder[0] = () -> {
                try {
                    listBox.removeAllViews();
                    String k = kw.getText() == null ? "" : kw.getText().toString().trim().toLowerCase();
                    int n = 0;
                    for (File w : worlds) {
                        if (!k.isEmpty() && !w.getName().toLowerCase().contains(k)) {
                            continue;
                        }
                        n++;
                        LinearLayout card = PluginUiKit.card(a);
                        card.addView(PluginUiKit.text(a, w.getName()));
                        card.addView(PluginUiKit.kv(a, "大小",
                                PluginUiKit.size(PluginUiKit.dirSize(w, 0))));
                        card.addView(PluginUiKit.kv(a, "修改时间",
                                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                                        java.util.Locale.US).format(new java.util.Date(w.lastModified()))));
                        listBox.addView(card);
                    }
                    if (n == 0) {
                        listBox.addView(PluginUiKit.sub(a, "没有匹配的存档。"));
                    }
                } catch (Throwable ignored) {
                }
            };
            kw.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a2, int b2, int c2) {
                }

                public void onTextChanged(CharSequence s, int a2, int b2, int c2) {
                }

                public void afterTextChanged(android.text.Editable e) {
                    rebuildHolder[0].run();
                }
            });
            rebuildHolder[0].run();
            PluginUiKit.sheet(a, "存档快速定位", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    /** 第 36 项：皮肤收藏夹（收藏常用皮肤，一键应用）。 */
    public static void skinFavorites(MainActivity a) {
        try {
            File dir = new File(AppManifest.GAME_FILE_DIRECTORY_DIR, "skins");
            List<String> items = listNames(dir, ".png");
            Map<String, List<String>> map = load(KIND_SKIN);
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "皮肤收藏夹"));
            if (items.isEmpty()) {
                col.addView(PluginUiKit.sub(a, "skins 目录里没有皮肤文件。"));
                col.addView(PluginUiKit.sub(a, "把 .png 皮肤放进 "
                        + dir.getAbsolutePath() + " 就能在这里收藏。"));
            } else {
                col.addView(PluginUiKit.sub(a, "★ 标「收藏」的皮肤会排在最前面。"));
                final List<String> favs = new ArrayList<>();
                List<String> marked = map.get("__fav__");
                if (marked != null) {
                    favs.addAll(marked);
                }
                final LinearLayout listBox = new LinearLayout(a);
                listBox.setOrientation(LinearLayout.VERTICAL);
                col.addView(listBox);
                final Runnable[] rebuildHolder = new Runnable[1];
            rebuildHolder[0] = () -> {
                    try {
                        listBox.removeAllViews();
                        // 收藏的排前面
                        List<String> ordered = new ArrayList<>();
                        for (String s : items) {
                            if (favs.contains(s)) {
                                ordered.add(s);
                            }
                        }
                        for (String s : items) {
                            if (!favs.contains(s)) {
                                ordered.add(s);
                            }
                        }
                        for (final String s : ordered) {
                            LinearLayout card = PluginUiKit.card(a);
                            LinearLayout row = new LinearLayout(a);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            TextView name = PluginUiKit.text(a, (favs.contains(s) ? "★ " : "　") + s);
                            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                            row.addView(name);
                            TextView tog = PluginUiKit.row(a, favs.contains(s) ? "取消收藏" : "收藏", null);
                            tog.setPadding(PluginUiKit.dp(a, 8), PluginUiKit.dp(a, 4),
                                    PluginUiKit.dp(a, 8), PluginUiKit.dp(a, 4));
                            PluginUiKit.pressEffect(tog);
                            tog.setOnClickListener(v -> {
                                if (favs.contains(s)) {
                                    favs.remove(s);
                                } else {
                                    favs.add(s);
                                }
                                rebuildHolder[0].run();
                            });
                            row.addView(tog);
                            card.addView(row);
                            listBox.addView(card);
                        }
                    } catch (Throwable ignored) {
                    }
                };
                rebuildHolder[0].run();
                PluginUiKit.sheet(a, "皮肤收藏夹", PluginUiKit.scroller(a, col), "保存", () -> {
                    Map<String, List<String>> out = load(KIND_SKIN);
                    out.put("__fav__", favs);
                    save(KIND_SKIN, out);
                    PluginUiKit.toast(a, "已保存");
                });
            }
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }
}
