package com.qcl.launcher.control.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.qcl.launcher.control.bean.ViewPosition;
import com.qcl.launcher.control.bean.button.ButtonSize;
import com.qcl.launcher.control.bean.button.ButtonStyle;
import com.qcl.launcher.control.bean.rocker.RockerSize;
import com.qcl.launcher.control.bean.rocker.RockerStyle;
import com.qcl.launcher.utils.QclColors;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 外部署名布局（FCL / Zalith Launcher 2 / Zalith Launcher / HMCL-PE）转换到
 * QCL 控键模型时共用的解析与换算工具。
 */
final class LayoutImportUtils {

    static final int SHOW_ALWAYS = 0;
    static final int SHOW_IN_GAME = 1;
    static final int SHOW_IN_MENU = 2;

    static final int SIZE_PERCENT = 0;
    static final int SIZE_ABSOLUTE = 1;

    static final int POSITION_PERCENT = 0;
    static final int POSITION_ABSOLUTE = 1;

    static final int REF_WIDTH = 0;
    static final int REF_HEIGHT = 1;

    private static final Map<String, Integer> GLFW_KEYS = new HashMap<>();
    private static final Map<Integer, Integer> FCL_KEYS = new HashMap<>();

    static {
        buildGlfwKeys();
        buildFclKeys();
    }

    private LayoutImportUtils() {
    }

    // ---------------------------------------------------------------- JSON --

    static String string(JsonObject obj, String key, String def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Throwable ignored) {
            return def;
        }
    }

    static int integer(JsonObject obj, String key, int def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Throwable ignored) {
            return def;
        }
    }

    static float decimal(JsonObject obj, String key, float def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsFloat();
        } catch (Throwable ignored) {
            return def;
        }
    }

    static long integer64(JsonObject obj, String key, long def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsLong();
        } catch (Throwable ignored) {
            return def;
        }
    }

    static boolean bool(JsonObject obj, String key, boolean def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Throwable ignored) {
            return def;
        }
    }

    static JsonObject object(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonObject()) {
            return null;
        }
        return obj.getAsJsonObject(key);
    }

    static JsonArray array(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return new JsonArray();
        }
        JsonElement element = obj.get(key);
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        if (element.isJsonObject() || element.isJsonPrimitive()) {
            JsonArray arr = new JsonArray();
            arr.add(element);
            return arr;
        }
        return new JsonArray();
    }

    /** ZL2 的文本统一是 {default, matchQueue}，另外兼容旧版纯字符串。 */
    static String textOf(JsonElement element, String def) {
        if (element == null || element.isJsonNull()) {
            return def;
        }
        if (element.isJsonPrimitive()) {
            try {
                return element.getAsString();
            } catch (Throwable ignored) {
                return def;
            }
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("default") && !obj.get("default").isJsonNull()) {
                return string(obj, "default", def);
            }
            if (obj.has("text") && !obj.get("text").isJsonNull()) {
                return string(obj, "text", def);
            }
        }
        return def;
    }

    static String textOf(JsonObject obj, String key, String def) {
        return textOf(obj == null ? null : obj.get(key), def);
    }

    /** ZL1 的 ControlInfoData 会用字符串 "null" 表示未填写。 */
    static String cleanText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? fallback : value;
    }

    static String newUuid() {
        return UUID.randomUUID().toString();
    }

    static String color(int argb) {
        return QclColors.format(argb);
    }

    /** 把 ARGB 的 alpha 乘以额外不透明度（0-1），用于还原 ZL1 的 opacity、ZL2 的 alpha。 */
    static int withAlpha(int argb, float opacity) {
        float a = ((argb >>> 24) & 0xFF) * clamp(opacity, 0f, 1f);
        int alpha = Math.round(clamp(a, 0f, 255f));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    // ------------------------------------------------------- sizes/positions --

    static ButtonSize buttonSizePercent(float percent, int reference, int absoluteFallbackDp) {
        return new ButtonSize(Math.max(1, absoluteFallbackDp), clamp(percent, 0.01f, 1f), reference);
    }

    static ButtonSize buttonSizeAbsolute(int dp, int reference) {
        return new ButtonSize(Math.max(1, dp), 0.05f, reference);
    }

    static RockerSize rockerSizePercent(float percent, int reference, int absoluteFallbackDp) {
        return new RockerSize(Math.max(1, absoluteFallbackDp), clamp(percent, 0.05f, 1f), reference);
    }

    static RockerSize rockerSizeAbsolute(int dp, int reference) {
        return new RockerSize(Math.max(1, dp), 0.2f, reference);
    }

    static ViewPosition percentPosition(float percent) {
        return new ViewPosition(0, clamp01(percent));
    }

    static ViewPosition absolutePosition(int dp) {
        return new ViewPosition(Math.max(0, dp), 0f);
    }

    /** FCL / ZL2 的百分坐标都是「相对可用空间」(屏幕 - 控件)，与 QCL 一致，直接 /1000 或 /10000。 */
    static float perMilleToPercent(int value) {
        return clamp01(value / 1000f);
    }

    static float perTenThousandToPercent(int value) {
        return clamp01(value / 10000f);
    }

    static int dp(float value) {
        return Math.max(1, Math.round(value));
    }

    // -------------------------------------------------------------- styles --

    static ButtonStyle defaultButtonStyle() {
        return new ButtonStyle();
    }

    static ButtonStyle buttonStyle(String name, int textSize, int textColor, int cornerRadius,
                                   float strokeWidth, int strokeColor, int fillColor,
                                   int textSizePress, int textColorPress, int cornerRadiusPress,
                                   float strokeWidthPress, int strokeColorPress, int fillColorPress) {
        return new ButtonStyle(
                name == null || name.isEmpty() ? "导入样式" : name,
                Math.max(1, textSize),
                color(textColor),
                Math.max(0, cornerRadius),
                Math.max(0f, strokeWidth),
                color(strokeColor),
                color(fillColor),
                Math.max(1, textSizePress),
                color(textColorPress),
                Math.max(0, cornerRadiusPress),
                Math.max(0f, strokeWidthPress),
                color(strokeColorPress),
                color(fillColorPress)
        );
    }

    static RockerStyle defaultRockerStyle() {
        return new RockerStyle();
    }

    static RockerStyle rockerStyle(String name, int cornerRadius, float strokeWidth,
                                   int strokeColor, int fillColor, int pointerColor,
                                   int cornerRadiusPress, float strokeWidthPress,
                                   int strokeColorPress, int fillColorPress, int pointerColorPress) {
        return new RockerStyle(
                name == null || name.isEmpty() ? "导入摇杆" : name,
                Math.max(0, cornerRadius),
                Math.max(0f, strokeWidth),
                color(strokeColor),
                color(fillColor),
                color(pointerColor),
                Math.max(0, cornerRadiusPress),
                Math.max(0f, strokeWidthPress),
                color(strokeColorPress),
                color(fillColorPress),
                color(pointerColorPress)
        );
    }

    // ------------------------------------------------------------- keycodes --

    /**
     * ZL2 的按键事件是 GLFW 键名（也兼容 Minecraft 的 key.keyboard.* / key.mouse.*），
     * QCL 直接保存 GLFW 整数键码；鼠标左/右/中/滚轮沿用 QCL 的 0/1/2/3/4 约定。
     */
    static int glfwKey(String rawKey) {
        if (rawKey == null) {
            return -1;
        }
        String key = rawKey.trim();
        if (key.isEmpty()) {
            return -1;
        }
        String upper = key.toUpperCase(Locale.ROOT);
        if (upper.startsWith("MOUSE_SCROLL_UP")) {
            return 3;
        }
        if (upper.startsWith("MOUSE_SCROLL_DOWN")) {
            return 4;
        }
        if (key.startsWith("key.mouse.")) {
            String suffix = key.substring("key.mouse.".length()).toLowerCase(Locale.ROOT);
            switch (suffix) {
                case "left":
                case "1":
                    return 0;
                case "right":
                case "2":
                    return 1;
                case "middle":
                case "3":
                    return 2;
                default:
                    return 3;
            }
        }
        if (key.startsWith("key.keyboard.")) {
            key = "GLFW_KEY_" + key.substring("key.keyboard.".length())
                    .replace('.', '_')
                    .toUpperCase(Locale.ROOT);
        }
        Integer direct = GLFW_KEYS.get(key.toUpperCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        // GLFW_MOUSE_BUTTON_1..8 与 QCL 的 0..4 对应
        if (upper.startsWith("GLFW_MOUSE_BUTTON_")) {
            switch (upper) {
                case "GLFW_MOUSE_BUTTON_LEFT":
                case "GLFW_MOUSE_BUTTON_1":
                    return 0;
                case "GLFW_MOUSE_BUTTON_RIGHT":
                case "GLFW_MOUSE_BUTTON_2":
                    return 1;
                case "GLFW_MOUSE_BUTTON_MIDDLE":
                case "GLFW_MOUSE_BUTTON_3":
                    return 2;
                case "GLFW_MOUSE_BUTTON_4":
                    return 3;
                case "GLFW_MOUSE_BUTTON_5":
                case "GLFW_MOUSE_BUTTON_6":
                case "GLFW_MOUSE_BUTTON_7":
                case "GLFW_MOUSE_BUTTON_8":
                    return 4;
                default:
                    return -1;
            }
        }
        return -1;
    }

    /**
     * FCL 使用 Linux/LWJGL2 风格整数键码，而 QCL 使用 GLFW 键码。
     * 返回 -1 表示无法映射（调用方丢弃该键，避免误触发其它按键）。
     */
    static int fclKey(int code) {
        if (code >= 1000 && code <= 1004) {
            switch (code) {
                case 1000:
                    return 0;
                case 1001:
                    return 2;
                case 1002:
                    return 1;
                case 1003:
                    return 3;
                case 1004:
                    return 4;
            }
        }
        Integer mapped = FCL_KEYS.get(code);
        return mapped == null ? -1 : mapped;
    }

    private static void buildGlfwKeys() {
        for (char c = 'A'; c <= 'Z'; c++) {
            GLFW_KEYS.put("GLFW_KEY_" + c, 65 + (c - 'A'));
        }
        for (int i = 0; i <= 9; i++) {
            GLFW_KEYS.put("GLFW_KEY_" + i, 48 + i);
        }
        for (int i = 1; i <= 25; i++) {
            GLFW_KEYS.put("GLFW_KEY_F" + i, 289 + i);
        }
        putKeys(32, "SPACE");
        putKeys(39, "APOSTROPHE");
        putKeys(44, "COMMA");
        putKeys(45, "MINUS");
        putKeys(46, "PERIOD");
        putKeys(47, "SLASH");
        putKeys(59, "SEMICOLON");
        putKeys(61, "EQUAL");
        putKeys(91, "LEFT_BRACKET");
        putKeys(92, "BACKSLASH");
        putKeys(93, "RIGHT_BRACKET");
        putKeys(96, "GRAVE_ACCENT");
        putKeys(161, "WORLD_1");
        putKeys(162, "WORLD_2");
        putKeys(256, "ESCAPE");
        putKeys(257, "ENTER");
        putKeys(258, "TAB");
        putKeys(259, "BACKSPACE");
        putKeys(260, "INSERT");
        putKeys(261, "DELETE");
        putKeys(262, "RIGHT");
        putKeys(263, "LEFT");
        putKeys(264, "DOWN");
        putKeys(265, "UP");
        putKeys(266, "PAGE_UP");
        putKeys(267, "PAGE_DOWN");
        putKeys(268, "HOME");
        putKeys(269, "END");
        putKeys(280, "CAPS_LOCK");
        putKeys(281, "SCROLL_LOCK");
        putKeys(282, "NUM_LOCK");
        putKeys(283, "PRINT_SCREEN");
        putKeys(284, "PAUSE");
        for (int i = 0; i <= 9; i++) {
            putKeys(320 + i, "KP_" + i);
        }
        putKeys(330, "KP_DECIMAL");
        putKeys(331, "KP_DIVIDE");
        putKeys(332, "KP_MULTIPLY");
        putKeys(333, "KP_SUBTRACT");
        putKeys(334, "KP_ADD");
        putKeys(335, "KP_ENTER");
        putKeys(336, "KP_EQUAL");
        putKeys(340, "LEFT_SHIFT");
        putKeys(341, "LEFT_CONTROL");
        putKeys(342, "LEFT_ALT");
        putKeys(343, "LEFT_SUPER");
        putKeys(344, "RIGHT_SHIFT");
        putKeys(345, "RIGHT_CONTROL");
        putKeys(346, "RIGHT_ALT");
        putKeys(347, "RIGHT_SUPER");
        putKeys(348, "MENU");
        // 旧版 ZL 常用的别名
        GLFW_KEYS.put("GLFW_KEY_LSHIFT", 340);
        GLFW_KEYS.put("GLFW_KEY_LCTRL", 341);
        GLFW_KEYS.put("GLFW_KEY_LALT", 342);
        GLFW_KEYS.put("GLFW_KEY_RSHIFT", 344);
        GLFW_KEYS.put("GLFW_KEY_RCTRL", 345);
        GLFW_KEYS.put("GLFW_KEY_RALT", 346);
        GLFW_KEYS.put("GLFW_KEY_ESCAPE", 256);
        GLFW_KEYS.put("GLFW_KEY_ENTER", 257);
        GLFW_KEYS.put("GLFW_KEY_TAB", 258);
    }

    private static void putKeys(int code, String name) {
        GLFW_KEYS.put("GLFW_KEY_" + name, code);
        GLFW_KEYS.put("GLFW_" + name, code);
    }

    private static void buildFclKeys() {
        // 数字行
        FCL_KEYS.put(1, 256);   // ESC
        for (int i = 2; i <= 10; i++) {
            FCL_KEYS.put(i, 48 + (i - 1));
        }
        FCL_KEYS.put(11, 48);   // 0
        FCL_KEYS.put(12, 45);   // -
        FCL_KEYS.put(13, 61);   // =
        FCL_KEYS.put(14, 259);  // backspace
        FCL_KEYS.put(15, 258);  // tab
        int[] qwerty = {81, 87, 69, 82, 84, 89, 85, 73, 79, 80};
        for (int i = 0; i < qwerty.length; i++) {
            FCL_KEYS.put(16 + i, qwerty[i]);
        }
        FCL_KEYS.put(26, 91);
        FCL_KEYS.put(27, 93);
        FCL_KEYS.put(28, 257);
        FCL_KEYS.put(29, 341);
        int[] asdf = {65, 83, 68, 70, 71, 72, 74, 75, 76};
        for (int i = 0; i < asdf.length; i++) {
            FCL_KEYS.put(30 + i, asdf[i]);
        }
        FCL_KEYS.put(39, 59);
        FCL_KEYS.put(40, 39);
        FCL_KEYS.put(41, 96);
        FCL_KEYS.put(42, 340);
        FCL_KEYS.put(43, 92);
        int[] zxcv = {90, 88, 67, 86, 66, 78, 77};
        for (int i = 0; i < zxcv.length; i++) {
            FCL_KEYS.put(44 + i, zxcv[i]);
        }
        FCL_KEYS.put(51, 44);
        FCL_KEYS.put(52, 46);
        FCL_KEYS.put(53, 47);
        FCL_KEYS.put(54, 344);
        FCL_KEYS.put(55, 332);
        FCL_KEYS.put(56, 342);
        FCL_KEYS.put(57, 32);
        FCL_KEYS.put(58, 280);
        for (int i = 0; i < 10; i++) {
            FCL_KEYS.put(59 + i, 290 + i);
        }
        FCL_KEYS.put(69, 282);
        FCL_KEYS.put(70, 281);
        FCL_KEYS.put(71, 327);
        FCL_KEYS.put(72, 328);
        FCL_KEYS.put(73, 329);
        FCL_KEYS.put(74, 333);
        FCL_KEYS.put(75, 324);
        FCL_KEYS.put(76, 325);
        FCL_KEYS.put(77, 326);
        FCL_KEYS.put(78, 334);
        FCL_KEYS.put(79, 321);
        FCL_KEYS.put(80, 322);
        FCL_KEYS.put(81, 323);
        FCL_KEYS.put(82, 320);
        FCL_KEYS.put(83, 330);
        FCL_KEYS.put(87, 300);
        FCL_KEYS.put(88, 301);
        FCL_KEYS.put(96, 335);
        FCL_KEYS.put(97, 345);
        FCL_KEYS.put(98, 331);
        FCL_KEYS.put(99, 283);
        FCL_KEYS.put(100, 346);
        FCL_KEYS.put(102, 268);
        FCL_KEYS.put(103, 265);
        FCL_KEYS.put(104, 266);
        FCL_KEYS.put(105, 263);
        FCL_KEYS.put(106, 262);
        FCL_KEYS.put(107, 269);
        FCL_KEYS.put(108, 264);
        FCL_KEYS.put(109, 267);
        FCL_KEYS.put(110, 260);
        FCL_KEYS.put(111, 261);
        FCL_KEYS.put(117, 336);
        FCL_KEYS.put(119, 284);
        FCL_KEYS.put(125, 343);
        FCL_KEYS.put(126, 347);
        for (int i = 0; i < 12; i++) {
            FCL_KEYS.put(183 + i, 302 + i);
        }
    }

    // -------------------------------------------------------- ZL1 expression --

    /**
     * Zalith Launcher（ZL1，Pojav 系）用 exp4j 求值动态坐标，例如
     * {@code 0.9 * ${screen_width} - ${width}}、{@code px(160.0) / 100 * ${preferred_scale}}。
     * 这里实现等价的四则运算求值器，并用一个参考屏幕把像素坐标换算成百分比。
     */
    static final class ExpressionEvaluator {
        static final int REF_SCREEN_WIDTH = 1920;
        static final int REF_SCREEN_HEIGHT = 1080;
        static final float REF_DENSITY = 2f;
        static final float REF_SCALE = 100f;
        static final float REF_MARGIN = 2 * REF_DENSITY;

        private final String source;
        private int pos;
        private final Map<String, Double> variables = new HashMap<>();

        private ExpressionEvaluator(String source) {
            this.source = source == null ? "" : source;
        }

        static double eval(String expression, float widthDp, float heightDp) {
            ExpressionEvaluator evaluator = new ExpressionEvaluator(expression);
            double widthPx = widthDp * REF_DENSITY;
            double heightPx = heightDp * REF_DENSITY;
            evaluator.variables.put("screen_width", (double) REF_SCREEN_WIDTH);
            evaluator.variables.put("screen_height", (double) REF_SCREEN_HEIGHT);
            evaluator.variables.put("width", widthPx);
            evaluator.variables.put("height", heightPx);
            evaluator.variables.put("right", REF_SCREEN_WIDTH - widthPx);
            evaluator.variables.put("bottom", REF_SCREEN_HEIGHT - heightPx);
            evaluator.variables.put("margin", (double) REF_MARGIN);
            evaluator.variables.put("preferred_scale", (double) REF_SCALE);
            try {
                double value = evaluator.parseExpression();
                return Double.isNaN(value) || Double.isInfinite(value) ? 0 : value;
            } catch (Throwable ignored) {
                return 0;
            }
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                if (peek() == '+') {
                    pos++;
                    value += parseTerm();
                } else if (peek() == '-') {
                    pos++;
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipSpaces();
                if (peek() == '*') {
                    pos++;
                    value *= parseFactor();
                } else if (peek() == '/') {
                    pos++;
                    double divisor = parseFactor();
                    value = divisor == 0 ? 0 : value / divisor;
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipSpaces();
            char c = peek();
            if (c == '+') {
                pos++;
                return parseFactor();
            }
            if (c == '-') {
                pos++;
                return -parseFactor();
            }
            if (c == '(') {
                pos++;
                double value = parseExpression();
                skipSpaces();
                if (peek() == ')') {
                    pos++;
                }
                return value;
            }
            if (c == '$' && pos + 1 < source.length() && source.charAt(pos + 1) == '{') {
                int end = source.indexOf('}', pos + 2);
                if (end < 0) {
                    pos = source.length();
                    return 0;
                }
                String name = source.substring(pos + 2, end).trim();
                pos = end + 1;
                Double value = variables.get(name);
                return value == null ? 0 : value;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                while (pos < source.length()) {
                    char ch = source.charAt(pos);
                    if (Character.isDigit(ch) || ch == '.') {
                        pos++;
                    } else {
                        break;
                    }
                }
                try {
                    return Double.parseDouble(source.substring(start, pos));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                while (pos < source.length()) {
                    char ch = source.charAt(pos);
                    if (Character.isLetterOrDigit(ch) || ch == '_') {
                        pos++;
                    } else {
                        break;
                    }
                }
                String name = source.substring(start, pos);
                skipSpaces();
                if (peek() == '(') {
                    pos++;
                    double argument = parseExpression();
                    skipSpaces();
                    if (peek() == ')') {
                        pos++;
                    }
                    if ("px".equals(name)) {
                        return argument * REF_DENSITY;
                    }
                    if ("dp".equals(name)) {
                        return argument / REF_DENSITY;
                    }
                    return argument;
                }
                Double value = variables.get(name);
                return value == null ? 0 : value;
            }
            pos++;
            return 0;
        }

        private char peek() {
            return pos < source.length() ? source.charAt(pos) : '\0';
        }

        private void skipSpaces() {
            while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
                pos++;
            }
        }
    }

    /** ZL1 的坐标表达式求值后换算成 QCL 的「相对可用空间」百分比。 */
    static float zl1PositionPercent(String expression, float widthDp, float heightDp, boolean horizontal) {
        double px = ExpressionEvaluator.eval(expression, widthDp, heightDp);
        double available = horizontal
                ? ExpressionEvaluator.REF_SCREEN_WIDTH - widthDp * ExpressionEvaluator.REF_DENSITY
                : ExpressionEvaluator.REF_SCREEN_HEIGHT - heightDp * ExpressionEvaluator.REF_DENSITY;
        if (available <= 1) {
            return 0f;
        }
        return clamp01((float) (px / available));
    }

    /** 把一个 JsonArray 的整数键码逐个交给映射函数，丢弃 -1。 */
    static java.util.ArrayList<Integer> mapKeycodes(JsonArray array, KeyMapper mapper) {
        java.util.ArrayList<Integer> result = new java.util.ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (element == null || !element.isJsonPrimitive()) {
                continue;
            }
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            int code;
            try {
                code = primitive.getAsInt();
            } catch (Throwable t) {
                continue;
            }
            int mapped = mapper.map(code);
            if (mapped >= 0 && !result.contains(mapped)) {
                result.add(mapped);
            }
        }
        return result;
    }

    interface KeyMapper {
        int map(int code);
    }
}
