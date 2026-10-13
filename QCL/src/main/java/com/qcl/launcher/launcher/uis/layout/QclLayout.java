package com.qcl.launcher.launcher.uis.layout;

import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ★★★ 社区版：界面排版自定义（阶段 ① 数据层）。
 *
 * <p><b>需求</b>：用户要「更改**整个界面**排版」—— 不是只改菜单里显示哪几项，
 * 而是各页面的控件本身可以**改顺序 / 隐藏 / 改大小**（方案 B）。
 *
 * <p><b>为什么先做数据层</b>：编辑器、逐页接入、导入导出**都依赖同一份布局数据** ✓
 * 先把读写和格式定死，后面几步就只是调用它 ✓ —— 反过来先做 UI 的话，
 * 每改一次数据格式就要回头改 UI ✗
 *
 * <p><b>为什么选方案 B（顺序 + 显隐 + 大小）而不是绝对定位</b>：
 * <ul>
 *   <li>B 保持原有布局体系（LinearLayout / RelativeLayout 等）✓
 *       换分辨率、旋转屏幕、软键盘弹出**都能自动适配** ✓</li>
 *   <li>绝对定位（setX/setY）自由度高 ✗ 但这些场景全要打架 ✗ 易错位 ✗</li>
 *   <li>B 能覆盖「藏掉不想要的 + 常用的挪前面 + 调大小」这 80% 需求 ✓</li>
 * </ul>
 *
 * <p><b>存储</b>：{@code SETTING_DIR/qcl_layout.json}，形如
 * <pre>
 * { "main": { "startGameButton": {"order":0,"visible":true,"w":0,"h":0} , ... } }
 * </pre>
 * {@code order} 是**在同一父容器内的排序权重**（小的在前）✓
 * {@code w/h} 为 0 表示「不改，用 XML 里的原值」✓
 */
public final class QclLayout {

    private QclLayout() {
    }

    private static final String FILE = "qcl_layout.json";

    /** 一个控件的排版设置。 */
    public static final class Item {
        /** 排序权重（同一父容器内从小到大）。 */
        public int order;
        /** 是否显示（false = GONE，不占位）。 */
        public boolean visible = true;
        /** 宽度覆盖（px）；0 = 不改。 */
        public int w;
        /** 高度覆盖（px）；0 = 不改。 */
        public int h;

        public Item() {
        }

        public Item(int order, boolean visible, int w, int h) {
            this.order = order;
            this.visible = visible;
            this.w = w;
            this.h = h;
        }
    }

    /** pageId → (viewId → Item)。 */
    private static Map<String, Map<String, Item>> data;
    private static boolean dirty;

    // ==================== 读 ====================

    /** 取某个页面里某个控件的设置；没有就返回 null（表示用 XML 原样）。 */
    public static Item get(String pageId, String viewId) {
        ensureLoaded();
        Map<String, Item> p = data.get(pageId);
        return p == null ? null : p.get(viewId);
    }

    /** 这个页面有没有被自定义过。 */
    public static boolean has(String pageId) {
        ensureLoaded();
        Map<String, Item> p = data.get(pageId);
        return p != null && !p.isEmpty();
    }

    /** 列出某页面所有被改过的控件 id（按 order 升序）。 */
    public static List<String> ordered(String pageId) {
        ensureLoaded();
        List<String> out = new ArrayList<>();
        Map<String, Item> p = data.get(pageId);
        if (p == null) {
            return out;
        }
        List<Map.Entry<String, Item>> es = new ArrayList<>(p.entrySet());
        es.sort((a, b) -> Integer.compare(a.getValue().order, b.getValue().order));
        for (Map.Entry<String, Item> e : es) {
            out.add(e.getKey());
        }
        return out;
    }

    // ==================== 写 ====================

    /** 设置（不落盘，改完调 {@link #save()}）。 */
    public static void put(String pageId, String viewId, Item it) {
        ensureLoaded();
        data.computeIfAbsent(pageId, k -> new LinkedHashMap<>()).put(viewId, it);
        dirty = true;
    }

    /** 撤销某个控件的自定义（回到 XML 原样）。 */
    public static void remove(String pageId, String viewId) {
        ensureLoaded();
        Map<String, Item> p = data.get(pageId);
        if (p != null) {
            p.remove(viewId);
            dirty = true;
        }
    }

    /** 撤销整个页面的自定义。 */
    public static void clearPage(String pageId) {
        ensureLoaded();
        data.remove(pageId);
        dirty = true;
    }

    /** 恢复**全部**默认（= 清空布局文件）。 */
    public static void resetAll() {
        data = new LinkedHashMap<>();
        dirty = true;
        save();
    }

    // ==================== 落盘 ====================

    private static void ensureLoaded() {
        if (data != null) {
            return;
        }
        data = new LinkedHashMap<>();
        try {
            File f = file();
            if (!f.isFile()) {
                return;
            }
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                //noinspection ResultOfMethodCallIgnored
                in.read(b);
            }
            parse(new String(b, "UTF-8"));
        } catch (Throwable ignored) {
        }
    }

    public static void save() {
        ensureLoaded();
        if (!dirty) {
            return;
        }
        try {
            File f = file();
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(exportJson().getBytes("UTF-8"));
            }
            dirty = false;
        } catch (Throwable ignored) {
        }
    }

    // ==================== 导入 / 导出 ====================

    /**
     * 导出成 JSON 文本。
     *
     * <p>为什么手写 JSON 而不引 Gson：这个类要保持**零依赖** ✓ ——
     * 它可能被启动早期调用，那时第三方库还没初始化好 ✗
     * 格式很简单（两层 map），手写反而更可控 ✓
     */
    public static String exportJson() {
        ensureLoaded();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int pi = 0;
        for (Map.Entry<String, Map<String, Item>> pe : data.entrySet()) {
            sb.append("  \"").append(esc(pe.getKey())).append("\": {");
            int vi = 0;
            for (Map.Entry<String, Item> ve : pe.getValue().entrySet()) {
                Item it = ve.getValue();
                if (vi++ > 0) {
                    sb.append(',');
                }
                sb.append("\n    \"").append(esc(ve.getKey())).append("\": ")
                        .append("{\"order\":").append(it.order)
                        .append(",\"visible\":").append(it.visible)
                        .append(",\"w\":").append(it.w)
                        .append(",\"h\":").append(it.h).append('}');
            }
            sb.append("\n  }");
            if (++pi < data.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("}\n");
        return sb.toString();
    }

    /** 导入 JSON 文本；成功返回 true（会覆盖当前内存数据，需再调 save ✓）。 */
    public static boolean importJson(String json) {
        try {
            Map<String, Map<String, Item>> parsed = parseMap(json);
            if (parsed == null) {
                return false;
            }
            data = parsed;
            dirty = true;
            save();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 导出到文件（给「分享布局」用）。 */
    public static File exportTo(File out) {
        try {
            File dir = out.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(exportJson().getBytes("UTF-8"));
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 从文件导入（给「接收布局」用）。 */
    public static boolean importFrom(File in) {
        try {
            byte[] b = new byte[(int) in.length()];
            try (FileInputStream fis = new FileInputStream(in)) {
                //noinspection ResultOfMethodCallIgnored
                fis.read(b);
            }
            return importJson(new String(b, "UTF-8"));
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 极简 JSON 解析 ====================
    //   只解析本类自己写出来的那种格式（两层 map + 四个 int/bool 字段）✓
    //   不是通用 JSON 解析器 ✗ —— 但够用，且不引依赖 ✓

    private static void parse(String s) {
        Map<String, Map<String, Item>> m = parseMap(s);
        if (m != null) {
            data = m;
        }
    }

    private static Map<String, Map<String, Item>> parseMap(String s) {
        try {
            Map<String, Map<String, Item>> out = new LinkedHashMap<>();
            int i = 0;
            int n = s.length();
            // 找到第一个 '{'
            while (i < n && s.charAt(i) != '{') {
                i++;
            }
            i++;   // 跳过外层 '{'
            while (i < n) {
                // 跳过空白和逗号
                while (i < n && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) {
                    i++;
                }
                if (i >= n || s.charAt(i) == '}') {
                    break;
                }
                if (s.charAt(i) != '"') {
                    break;
                }
                int[] box = new int[2];
                String pageId = readString(s, i, box);
                i = box[0];
                if (pageId == null) {
                    break;
                }
                // 跳到这个页面的 '{'
                while (i < n && s.charAt(i) != '{') {
                    i++;
                }
                i++;
                Map<String, Item> items = new LinkedHashMap<>();
                while (i < n) {
                    while (i < n && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) {
                        i++;
                    }
                    if (i >= n || s.charAt(i) == '}') {
                        i++;
                        break;
                    }
                    if (s.charAt(i) != '"') {
                        break;
                    }
                    String viewId = readString(s, i, box);
                    i = box[0];
                    if (viewId == null) {
                        break;
                    }
                    int ob = s.indexOf('{', i);
                    int cb = s.indexOf('}', ob);
                    if (ob < 0 || cb < 0) {
                        break;
                    }
                    String body = s.substring(ob + 1, cb);
                    Item it = new Item(
                            (int) num(body, "order", 0),
                            !"false".equals(str(body, "visible", "true")),
                            (int) num(body, "w", 0),
                            (int) num(body, "h", 0));
                    items.put(viewId, it);
                    i = cb + 1;
                }
                out.put(pageId, items);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 读一个 JSON 字符串（处理 \" 和 \\ 转义）；把新位置写回 box[0]。 */
    private static String readString(String s, int start, int[] box) {
        if (start >= s.length() || s.charAt(start) != '"') {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                sb.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '"') {
                i++;
                break;
            }
            sb.append(c);
            i++;
        }
        box[0] = i;
        return sb.toString();
    }

    private static double num(String body, String key, double def) {
        int i = body.indexOf("\"" + key + "\"");
        if (i < 0) {
            return def;
        }
        int c = body.indexOf(':', i);
        if (c < 0) {
            return def;
        }
        int j = c + 1;
        StringBuilder sb = new StringBuilder();
        while (j < body.length()) {
            char ch = body.charAt(j);
            if (ch == '-' || (ch >= '0' && ch <= '9') || ch == '.') {
                sb.append(ch);
                j++;
            } else if (sb.length() == 0 && Character.isWhitespace(ch)) {
                j++;
            } else {
                break;
            }
        }
        try {
            return sb.length() == 0 ? def : Double.parseDouble(sb.toString());
        } catch (Throwable t) {
            return def;
        }
    }

    private static String str(String body, String key, String def) {
        int i = body.indexOf("\"" + key + "\"");
        if (i < 0) {
            return def;
        }
        int c = body.indexOf(':', i);
        if (c < 0) {
            return def;
        }
        String rest = body.substring(c + 1).trim();
        if (rest.startsWith("true")) {
            return "true";
        }
        if (rest.startsWith("false")) {
            return "false";
        }
        return def;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static File file() {
        return new File(AppManifest.SETTING_DIR, FILE);
    }
}
