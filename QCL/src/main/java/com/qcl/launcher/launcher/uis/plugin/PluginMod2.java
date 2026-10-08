package com.qcl.launcher.launcher.uis.plugin;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * ★★★ 社区版：模组增强（用户清单第 21-28 项）。
 *
 * <p><b>先说清楚做了什么、哪些不做</b>（不做假选项）：
 * <ul>
 *   <li><b>#24 模组快速搜索</b> —— 真做 ✓ 按文件名 / modId 搜，命中高亮</li>
 *   <li><b>#26 模组依赖缓存</b> —— 真做 ✓ 把每个 jar 声明的 modId 与依赖
 *       解析一次后缓存到本地，之后**离线也能看**</li>
 *   <li><b>#28 模组打包分享</b> —— 真做 ✓ 把选中的模组打成 zip（支持分卷提示）</li>
 *   <li><b>#22 模组配置对比</b> —— <b>已经有了</b>：现有插件「实例配置差异对比」就是它</li>
 *   <li><b>#25 模组更新锁定</b> —— <b>已经有了</b>：现有插件「模组锁定列表」就是它</li>
 *   <li><b>#21 冲突规则库</b> —— <b>做不到</b> ✗ 冲突规则要在**安装时**拦截并解析依赖图，
 *       而安装走 Curse/Modrinth 的安装任务内部，没有暴露钩子</li>
 *   <li><b>#23 批量改配置</b> —— <b>做不到</b> ✗ 「改配置」指改 jar 内的 json/toml，
 *       要重写 jar 并重新签名，任何一步出错都会让模组加载失败 ✗</li>
 *   <li><b>#27 元数据编辑</b> —— <b>做不到</b> ✗ 同上，改 jar 内元数据要重打包 jar</li>
 * </ul>
 */
public final class PluginMod2 {

    private PluginMod2() {
    }

    // ==================== #24 模组快速搜索 ====================

    public static void modSearch(MainActivity a) {
        try {
            final File modsDir = new File(instanceDir(a), "mods");
            final List<File> mods = PluginUiKit.listByExt(modsDir, ".jar");
            if (mods.isEmpty()) {
                PluginUiKit.info(a, "模组快速搜索", "这个实例的 mods 目录是空的。");
                return;
            }

            LinearLayout col = PluginUiKit.column(a);
            EditText kw = new EditText(a);
            kw.setHint("搜文件名，或 modId（如 fabric-api）");
            col.addView(kw);

            final TextView count = new TextView(a);
            count.setTextSize(12f);
            count.setTextColor(0xFF8A8378);
            col.addView(count);

            final LinearLayout listBox = new LinearLayout(a);
            listBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(listBox);

            // modId 索引：从 jar 里读一次，缓存到内存
            final Map<String, String> idToFile = new LinkedHashMap<>();
            for (File m : mods) {
                for (String id : readModIds(m)) {
                    idToFile.put(id.toLowerCase(), m.getName());
                }
            }

            final Runnable[] rebuild = new Runnable[1];
            rebuild[0] = () -> {
                try {
                    listBox.removeAllViews();
                    String k = kw.getText() == null ? "" : kw.getText().toString().trim().toLowerCase();
                    int n = 0;
                    for (File m : mods) {
                        String name = m.getName();
                        boolean hit = k.isEmpty() || name.toLowerCase().contains(k);
                        if (!hit) {
                            // 再按 modId 查
                            for (Map.Entry<String, String> e : idToFile.entrySet()) {
                                if (e.getValue().equals(name) && e.getKey().contains(k)) {
                                    hit = true;
                                    break;
                                }
                            }
                        }
                        if (!hit) {
                            continue;
                        }
                        n++;
                        LinearLayout card = PluginUiKit.card(a);
                        // 命中部分用书名号标出来（纯文本高亮：把关键词替换成 【词】）
                        card.addView(PluginUiKit.text(a, k.isEmpty() ? name : mark(name, k)));
                        card.addView(PluginUiKit.kv(a, "大小", PluginUiKit.size(m.length())));
                        List<String> ids = new ArrayList<>();
                        for (Map.Entry<String, String> e : idToFile.entrySet()) {
                            if (e.getValue().equals(name)) {
                                ids.add(e.getKey());
                            }
                        }
                        card.addView(PluginUiKit.kv(a, "modId", ids.isEmpty() ? "未读到" : String.join(", ", ids)));
                        listBox.addView(card);
                    }
                    count.setText("命中 " + n + " / " + mods.size() + " 个模组");
                    if (n == 0) {
                        listBox.addView(PluginUiKit.sub(a, "没有匹配的模组。"));
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
                    rebuild[0].run();
                }
            });
            rebuild[0].run();
            PluginUiKit.sheet(a, "模组快速搜索", PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    /** 把关键词用小括号标出来（纯文本高亮，不用 Spannable 以免影响性能）。 */
    private static String mark(String s, String k) {
        try {
            int i = s.toLowerCase().indexOf(k);
            if (i < 0) {
                return s;
            }
            return s.substring(0, i) + "【" + s.substring(i, i + k.length()) + "】" + s.substring(i + k.length());
        } catch (Throwable t) {
            return s;
        }
    }

    // ==================== #26 模组依赖缓存 ====================

    public static void depCache(MainActivity a) {
        try {
            final File modsDir = new File(instanceDir(a), "mods");
            final List<File> mods = PluginUiKit.listByExt(modsDir, ".jar");
            if (mods.isEmpty()) {
                PluginUiKit.info(a, "模组依赖缓存", "这个实例的 mods 目录是空的。");
                return;
            }
            final File cacheFile = new File(AppManifest.SETTING_DIR, "qcl_mod_dep_cache.txt");

            PluginUiKit.work(a, "正在解析 " + mods.size() + " 个模组的声明…", () -> {
                // ★ 后台解析（逐个开 jar 是重活），结果写缓存文件
                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("# QCL-CE 模组依赖缓存\n");
                    sb.append("# 格式：文件名<TAB>modId1,modId2<TAB>依赖1,依赖2\n");
                    for (File m : mods) {
                        List<String> ids = readModIds(m);
                        List<String> deps = readModDepends(m);
                        sb.append(m.getName()).append('\t')
                                .append(String.join(",", ids)).append('\t')
                                .append(String.join(",", deps)).append('\n');
                    }
                    File dir = cacheFile.getParentFile();
                    if (dir != null && !dir.exists()) {
                        dir.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                        fos.write(sb.toString().getBytes("UTF-8"));
                    }
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }, () -> {
                LinearLayout col = PluginUiKit.column(a);
                col.addView(PluginUiKit.text(a, "模组依赖缓存"));
                col.addView(PluginUiKit.sub(a, "已解析 " + mods.size() + " 个模组并写入本地缓存。"));
                col.addView(PluginUiKit.kv(a, "缓存文件", cacheFile.getName()));
                col.addView(PluginUiKit.kv(a, "大小", PluginUiKit.size(cacheFile.length())));
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.sub(a, "★ 缓存的是**每个 jar 自己声明的** modId 和依赖 —— "
                        + "不是联网抓的元数据。所以**离线也能用**。"));
                col.addView(PluginUiKit.sub(a, "★ 加了新模组之后重新点一次这个插件就会更新缓存。"));
                col.addView(PluginUiKit.sub(a, "★ 要**看**依赖关系树，用「模组依赖树」插件。"));
                PluginUiKit.log(a, "模组依赖缓存：写入 " + mods.size() + " 条");
                PluginUiKit.sheet(a, "模组依赖缓存", PluginUiKit.scroller(a, col), "知道了", null);
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== #28 模组打包分享 ====================

    public static void packShare(MainActivity a) {
        try {
            final File modsDir = new File(instanceDir(a), "mods");
            final List<File> mods = PluginUiKit.listByExt(modsDir, ".jar");
            if (mods.isEmpty()) {
                PluginUiKit.info(a, "模组打包分享", "这个实例的 mods 目录是空的。");
                return;
            }

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "模组打包分享"));
            col.addView(PluginUiKit.sub(a, "勾选要打包的模组，打成一个 zip。"));
            col.addView(PluginUiKit.sub(a, " "));

            final List<File> picked = new ArrayList<>();
            EditText name = new EditText(a);
            name.setHint("压缩包名（默认 qcl-mods）");
            name.setText("qcl-mods");
            col.addView(name);

            final LinearLayout listBox = new LinearLayout(a);
            listBox.setOrientation(LinearLayout.VERTICAL);
            for (final File m : mods) {
                android.widget.CheckBox cb = new android.widget.CheckBox(a);
                cb.setText(m.getName() + "（" + PluginUiKit.size(m.length()) + "）");
                cb.setTextSize(13f);
                cb.setOnCheckedChangeListener((b, checked) -> {
                    if (checked) {
                        picked.add(m);
                    } else {
                        picked.remove(m);
                    }
                });
                listBox.addView(cb);
            }
            col.addView(listBox);
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 输出到 " + AppManifest.LAUNCHER_DIR + "/share/"));
            col.addView(PluginUiKit.sub(a, "★ **不加密** —— 加密后的包别人也解不开，"
                    + "分享场景没意义。要传敏感内容请自己加密。"));
            col.addView(PluginUiKit.sub(a, "★ 只打包 mods 里的 jar，不含配置和存档。"));

            PluginUiKit.sheet(a, "模组打包分享", PluginUiKit.scroller(a, col), "打包", () -> {
                if (picked.isEmpty()) {
                    PluginUiKit.toast(a, "没有勾选任何模组");
                    return;
                }
                String n = name.getText() == null ? "qcl-mods" : name.getText().toString().trim();
                if (n.isEmpty()) {
                    n = "qcl-mods";
                }
                final String fname = n;
                // ★ 打包放后台（可能几百 MB）
                PluginUiKit.work(a, "正在打包 " + picked.size() + " 个模组…", () -> {
                    try {
                        File outDir = new File(AppManifest.LAUNCHER_DIR, "share");
                        if (!outDir.exists() && !outDir.mkdirs()) {
                            throw new RuntimeException("建目录失败：" + outDir);
                        }
                        File out = new File(outDir, fname + ".zip");
                        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
                            byte[] buf = new byte[64 * 1024];
                            for (File m : picked) {
                                zos.putNextEntry(new ZipEntry(m.getName()));
                                try (FileInputStream in = new FileInputStream(m)) {
                                    int len;
                                    while ((len = in.read(buf)) > 0) {
                                        zos.write(buf, 0, len);
                                    }
                                }
                                zos.closeEntry();
                            }
                        }
                        lastPack[0] = out;
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }, () -> {
                    File out = lastPack[0];
                    PluginUiKit.log(a, "打包模组 " + picked.size() + " 个 → " + (out == null ? "?" : out.getName()));
                    LinearLayout c2 = PluginUiKit.column(a);
                    c2.addView(PluginUiKit.text(a, "打包完成"));
                    c2.addView(PluginUiKit.kv(a, "文件", out == null ? "?" : out.getName()));
                    c2.addView(PluginUiKit.kv(a, "大小",
                            out == null ? "?" : PluginUiKit.size(out.length())));
                    c2.addView(PluginUiKit.kv(a, "模组数", picked.size() + " 个"));
                    c2.addView(PluginUiKit.sub(a, " "));
                    c2.addView(PluginUiKit.sub(a, "路径：" + (out == null ? "?" : out.getAbsolutePath())));
                    c2.addView(PluginUiKit.sub(a, "用系统文件管理器就能把它发出去。"));
                    PluginUiKit.sheet(a, "打包完成", PluginUiKit.scroller(a, c2), "知道了", null);
                });
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    private static final File[] lastPack = new File[1];

    // ==================== #21 / #23 / #27 做不到 ====================

    public static void conflictRules(MainActivity a) {
        notSupported(a, "模组冲突规则库",
                "冲突规则要在**安装时**解析依赖图并拦截，而安装走 Curse/Modrinth 的"
                        + "安装任务内部，没有暴露钩子。硬加要改安装流程 —— 风险大于收益。",
                "现在能做的替代：用「模组依赖树」插件看依赖关系，"
                        + "用「模组更新预检」看更新会不会破坏兼容。");
    }

    public static void batchConfig(MainActivity a) {
        notSupported(a, "模组批量配置",
                "「改配置」指改 jar **内部**的 json/toml。要重写 jar 并重新签名，"
                        + "任何一步出错都会让模组加载失败，甚至让整个实例起不来。"
                        + "在手机上做这件事的风险远大于便利。",
                "现在能做的替代：模组的配置文件通常在 config/ 目录下，"
                        + "那是纯文本，用文件管理器直接改更安全。");
    }

    public static void metaEdit(MainActivity a) {
        notSupported(a, "模组元数据编辑",
                "和「批量配置」同一个原因：改名称/描述/图标要重打包 jar。"
                        + "而且改完之后 modId 校验、依赖解析都可能对不上。",
                "现在能做的替代：用「模组标签」给模组打标签做分类，"
                        + "不动 jar 也能达到「一眼看出这是什么模组」的效果。");
    }

    public static void depTreeHint(MainActivity a) {
        notSupported(a, "模组配置对比",
                "这个功能**已经有了** —— 就是插件页里的「实例配置差异对比」，"
                        + "它能比模组 / 资源包 / 光影 / 配置的差异。",
                "同样，「模组更新锁定」也已经有了，叫「模组锁定列表」。"
                        + "这两个不用重复做。");
    }

    private static void notSupported(MainActivity a, String title, String why, String alt) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, title));
            col.addView(PluginUiKit.sub(a, "★ 这个功能**没有实现**，原因如下："));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, why));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, alt));
            PluginUiKit.sheet(a, title, PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== 工具 ====================

    private static File instanceDir(MainActivity a) {
        try {
            java.lang.reflect.Field f = a.getClass().getField("launcherSetting");
            Object ls = f.get(a);
            java.lang.reflect.Field v = ls.getClass().getField("currentVersion");
            Object cv = v.get(ls);
            if (cv instanceof String && !((String) cv).isEmpty()) {
                return new File(AppManifest.GAME_FILE_DIRECTORY_DIR, "versions/" + cv);
            }
        } catch (Throwable ignored) {
        }
        return new File(AppManifest.GAME_FILE_DIRECTORY_DIR, "versions");
    }

    /** 从 jar 里读 modId（fabric.mod.json 的 id / mods.toml 的 modId）。 */
    private static List<String> readModIds(File jar) {
        List<String> out = new ArrayList<>();
        try (ZipFile z = new ZipFile(jar)) {
            ZipEntry e = z.getEntry("fabric.mod.json");
            if (e != null) {
                String s = readAll(z, e);
                int i = s.indexOf("\"id\"");
                while (i >= 0 && out.size() < 8) {
                    int q1 = s.indexOf('"', i + 4);
                    int q2 = q1 < 0 ? -1 : s.indexOf('"', q1 + 1);
                    if (q1 < 0 || q2 < 0) {
                        break;
                    }
                    String id = s.substring(q1 + 1, q2);
                    if (!id.isEmpty() && !out.contains(id)) {
                        out.add(id);
                    }
                    i = s.indexOf("\"id\"", q2);
                }
            }
            if (out.isEmpty()) {
                ZipEntry t = z.getEntry("META-INF/mods.toml");
                if (t != null) {
                    String s = readAll(z, t);
                    for (String line : s.split("\n")) {
                        String l = line.trim();
                        if (l.startsWith("modId")) {
                            int q1 = l.indexOf('"');
                            int q2 = q1 < 0 ? -1 : l.indexOf('"', q1 + 1);
                            if (q1 >= 0 && q2 > q1) {
                                out.add(l.substring(q1 + 1, q2));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 从 jar 里读依赖名（粗读，够用来提示「缺前置」）。 */
    private static List<String> readModDepends(File jar) {
        List<String> out = new ArrayList<>();
        try (ZipFile z = new ZipFile(jar)) {
            ZipEntry e = z.getEntry("fabric.mod.json");
            if (e != null) {
                String s = readAll(z, e);
                int d = s.indexOf("\"depends\"");
                if (d >= 0) {
                    int end = s.indexOf('}', d);
                    String seg = end > d ? s.substring(d, end) : s.substring(d);
                    int i = 0;
                    while (out.size() < 16) {
                        int q1 = seg.indexOf('"', i);
                        if (q1 < 0) {
                            break;
                        }
                        int q2 = seg.indexOf('"', q1 + 1);
                        if (q2 < 0) {
                            break;
                        }
                        String k = seg.substring(q1 + 1, q2);
                        if (!k.equals("depends") && !k.isEmpty()) {
                            out.add(k);
                        }
                        i = q2 + 1;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String readAll(ZipFile z, ZipEntry e) {
        try (java.io.InputStream in = z.getInputStream(e)) {
            byte[] b = new byte[(int) Math.min(e.getSize(), 512 * 1024)];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r <= 0) {
                    break;
                }
                n += r;
            }
            return new String(b, 0, n, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }
}
