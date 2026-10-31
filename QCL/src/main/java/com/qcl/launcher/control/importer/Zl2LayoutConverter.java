package com.qcl.launcher.control.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qcl.launcher.control.bean.BaseButtonInfo;
import com.qcl.launcher.control.bean.BaseRockerViewInfo;
import com.qcl.launcher.control.bean.ViewPosition;
import com.qcl.launcher.control.bean.button.ButtonSize;
import com.qcl.launcher.control.bean.button.ButtonStyle;
import com.qcl.launcher.control.bean.rocker.RockerSize;
import com.qcl.launcher.control.bean.rocker.RockerStyle;
import com.qcl.launcher.launcher.list.local.controller.ChildLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zalith Launcher 2（editorVersion 12）控制布局 -> QCL 控键布局。
 *
 * <p>关键换算：
 * <ul>
 *   <li>坐标/百分比尺寸是 0~10000，与 QCL 的 percentPosition 同义，直接 /10000；</li>
 *   <li>按键事件是 GLFW 键名；鼠标事件、滚轮、启动器事件都映射到 QCL 的整数键码或开关；</li>
 *   <li>layers 转成 QCL 的子布局，switch_layer/show_layer/hide_layer 转成 visibilityControl；</li>
 *   <li>normalButtons/textBoxes/joystickButtons 分别转按钮、静态按钮与摇杆。</li>
 * </ul>
 */
final class Zl2LayoutConverter {

    private Zl2LayoutConverter() {
    }

    static ExternalLayoutImporter.Result convert(JsonObject root, String fallbackName) {
        JsonObject info = LayoutImportUtils.object(root, "info");
        String name = LayoutImportUtils.textOf(info, "name", fallbackName);
        if (name == null || name.trim().isEmpty()) {
            name = fallbackName;
        }
        String author = LayoutImportUtils.textOf(info, "author", "Zalith Launcher 2");
        String versionName = LayoutImportUtils.string(info, "versionName", "1.0");
        String description = LayoutImportUtils.textOf(info, "description", "");

        Map<String, ButtonStyle> stylesByUuid = new LinkedHashMap<>();
        Map<String, ButtonStyle> stylesByName = new LinkedHashMap<>();
        for (JsonElement element : LayoutImportUtils.array(root, "styles")) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject style = element.getAsJsonObject();
            ButtonStyle converted = convertButtonStyle(style);
            stylesByUuid.put(LayoutImportUtils.string(style, "uuid", ""), converted);
            stylesByName.put(LayoutImportUtils.string(style, "name", ""), converted);
        }

        Map<String, RockerStyle> joystickStylesByUuid = new LinkedHashMap<>();
        Map<String, RockerStyle> joystickStylesByName = new LinkedHashMap<>();
        for (JsonElement element : LayoutImportUtils.array(root, "joystickStyles")) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject style = element.getAsJsonObject();
            RockerStyle converted = convertJoystickStyle(style);
            joystickStylesByUuid.put(LayoutImportUtils.string(style, "uuid", ""), converted);
            joystickStylesByName.put(LayoutImportUtils.string(style, "name", ""), converted);
        }

        JsonArray layers = LayoutImportUtils.array(root, "layers");
        Map<String, String> layerNamesById = new LinkedHashMap<>();
        for (JsonElement element : layers) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject layer = element.getAsJsonObject();
            layerNamesById.put(LayoutImportUtils.string(layer, "uuid", ""), layerName(layer));
        }

        List<ChildLayout> children = new ArrayList<>();
        for (JsonElement element : layers) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject layer = element.getAsJsonObject();
            String childName = layerName(layer);
            int visibility = LayoutImportUtils.bool(layer, "hide", false) ? 4 : 0;
            String layerVisibility = LayoutImportUtils.string(layer, "visibilityType", "always");

            ArrayList<BaseButtonInfo> buttons = new ArrayList<>();
            ArrayList<BaseRockerViewInfo> rockers = new ArrayList<>();

            for (JsonElement normalElement : LayoutImportUtils.array(layer, "normalButtons")) {
                if (normalElement != null && normalElement.isJsonObject()) {
                    buttons.add(convertNormal(normalElement.getAsJsonObject(), stylesByUuid, stylesByName,
                            layerNamesById, childName, layerVisibility));
                }
            }
            for (JsonElement textElement : LayoutImportUtils.array(layer, "textBoxes")) {
                if (textElement != null && textElement.isJsonObject()) {
                    buttons.add(convertTextBox(textElement.getAsJsonObject(), stylesByUuid, stylesByName,
                            childName, layerVisibility));
                }
            }
            for (JsonElement joystickElement : LayoutImportUtils.array(layer, "joystickButtons")) {
                if (joystickElement != null && joystickElement.isJsonObject()) {
                    rockers.add(convertJoystick(joystickElement.getAsJsonObject(),
                            joystickStylesByUuid, joystickStylesByName, childName, layerVisibility));
                }
            }
            children.add(new ChildLayout(childName, visibility, buttons, rockers));
        }

        return new ExternalLayoutImporter.Result(
                "ZL2", name, author, versionName, description, children);
    }

    private static String layerName(JsonObject layer) {
        String name = LayoutImportUtils.string(layer, "name", "");
        return name.trim().isEmpty() ? "game_layout" : name;
    }

    private static ButtonStyle convertButtonStyle(JsonObject style) {
        JsonObject config = LayoutImportUtils.object(style, "lightStyle");
        if (config == null) {
            config = LayoutImportUtils.object(style, "darkStyle");
        }
        if (config == null) {
            return LayoutImportUtils.defaultButtonStyle();
        }
        float alpha = LayoutImportUtils.decimal(config, "alpha", 1f);
        int backgroundColor = zl2Color(LayoutImportUtils.integer64(config, "backgroundColor", 0L));
        int pressedBackground = zl2Color(LayoutImportUtils.integer64(config, "pressedBackgroundColor", 0L));
        int contentColor = zl2Color(LayoutImportUtils.integer64(config, "contentColor", 0xFFFFFFFFL));
        int pressedContent = zl2Color(LayoutImportUtils.integer64(config, "pressedContentColor", 0xFFFFFFFFL));
        int borderColor = zl2Color(LayoutImportUtils.integer64(config, "borderColor", 0xFFFFFFFFL));
        int pressedBorder = zl2Color(LayoutImportUtils.integer64(config, "pressedBorderColor", 0xFFFFFFFFL));

        int fontSize = LayoutImportUtils.integer(config, "fontSize", 14);
        int pressedFontSize = LayoutImportUtils.integer(config, "pressedFontSize", fontSize);
        int borderWidth = LayoutImportUtils.integer(config, "borderWidth", 0);
        int pressedBorderWidth = LayoutImportUtils.integer(config, "pressedBorderWidth", borderWidth);
        float pressedAlpha = LayoutImportUtils.decimal(config, "pressedAlpha", alpha);
        int cornerRadius = Math.round(maxCornerRadius(LayoutImportUtils.object(config, "borderRadius")));
        int pressedCornerRadius = Math.round(maxCornerRadius(
                LayoutImportUtils.object(config, "pressedBorderRadius")));

        return LayoutImportUtils.buttonStyle(
                LayoutImportUtils.string(style, "name", "ZL2"),
                fontSize,
                contentColor,
                cornerRadius,
                borderWidth,
                borderColor,
                LayoutImportUtils.withAlpha(backgroundColor, alpha),
                pressedFontSize,
                pressedContent,
                pressedCornerRadius,
                pressedBorderWidth,
                pressedBorder,
                LayoutImportUtils.withAlpha(pressedBackground, pressedAlpha)
        );
    }

    private static RockerStyle convertJoystickStyle(JsonObject style) {
        JsonObject config = LayoutImportUtils.object(style, "lightStyle");
        if (config == null) {
            config = LayoutImportUtils.object(style, "darkStyle");
        }
        if (config == null) {
            return LayoutImportUtils.defaultRockerStyle();
        }
        float alpha = LayoutImportUtils.decimal(config, "alpha", 1f);
        int backgroundColor = zl2Color(LayoutImportUtils.integer64(config, "backgroundColor", 0L));
        int joystickColor = zl2Color(LayoutImportUtils.integer64(config, "joystickColor", 0x80FFFFFFL));
        int borderColor = zl2Color(LayoutImportUtils.integer64(config, "borderColor", 0xFFFFFFFFL));
        int backgroundShape = LayoutImportUtils.integer(config, "backgroundShape", 50);
        int borderWidthRatio = LayoutImportUtils.integer(config, "borderWidthRatio", 0);
        int cornerRadius = backgroundShape >= 50 ? 80 : Math.round(backgroundShape / 50f * 80f);
        float strokeWidth = borderWidthRatio * 1.6f;
        return LayoutImportUtils.rockerStyle(
                LayoutImportUtils.string(style, "name", "ZL2"),
                cornerRadius,
                strokeWidth,
                borderColor,
                LayoutImportUtils.withAlpha(backgroundColor, alpha),
                LayoutImportUtils.withAlpha(joystickColor, alpha),
                cornerRadius,
                strokeWidth,
                borderColor,
                LayoutImportUtils.withAlpha(backgroundColor, alpha),
                LayoutImportUtils.withAlpha(joystickColor, alpha)
        );
    }

    private static BaseButtonInfo convertNormal(JsonObject data, Map<String, ButtonStyle> byUuid,
                                                Map<String, ButtonStyle> byName, Map<String, String> layerNamesById,
                                                String childName, String layerVisibility) {
        ButtonStyle style = resolveButtonStyle(data, byUuid, byName);
        int showType = combineVisibility(layerVisibility,
                LayoutImportUtils.string(data, "visibilityType", "always"));
        ButtonSize width = convertSize(LayoutImportUtils.object(data, "buttonSize"),
                textWidthEstimate(LayoutImportUtils.textOf(data, "text", "")), false);
        ButtonSize height = convertSize(LayoutImportUtils.object(data, "buttonSize"),
                textHeightEstimate(), true);
        JsonObject position = LayoutImportUtils.object(data, "position");
        ViewPosition x = LayoutImportUtils.percentPosition(LayoutImportUtils.perTenThousandToPercent(
                LayoutImportUtils.integer(position, "x", 0)));
        ViewPosition y = LayoutImportUtils.percentPosition(LayoutImportUtils.perTenThousandToPercent(
                LayoutImportUtils.integer(position, "y", 0)));

        ArrayList<Integer> keycodes = new ArrayList<>();
        ArrayList<String> visibilityControl = new ArrayList<>();
        boolean[] openMenu = {false};
        boolean[] showInput = {false};
        StringBuilder outputText = new StringBuilder();
        parseClickEvents(LayoutImportUtils.array(data, "clickEvents"), layerNamesById,
                keycodes, visibilityControl, openMenu, showInput, outputText);

        boolean toggle = LayoutImportUtils.bool(data, "isToggleable", false);
        return new BaseButtonInfo(
                LayoutImportUtils.newUuid(),
                "",
                childName,
                LayoutImportUtils.textOf(data, "text", ""),
                showType,
                sizeTypeOf(LayoutImportUtils.object(data, "buttonSize")),
                width,
                height,
                LayoutImportUtils.POSITION_PERCENT,
                x,
                y,
                BaseButtonInfo.FUNCTION_TYPE_TOUCH,
                false,
                toggle,
                false,
                openMenu[0],
                false,
                false,
                false,
                false,
                showInput[0],
                visibilityControl,
                outputText.toString(),
                keycodes,
                true,
                style
        );
    }

    private static BaseButtonInfo convertTextBox(JsonObject data, Map<String, ButtonStyle> byUuid,
                                                 Map<String, ButtonStyle> byName, String childName,
                                                 String layerVisibility) {
        ButtonStyle style = resolveButtonStyle(data, byUuid, byName);
        int showType = combineVisibility(layerVisibility,
                LayoutImportUtils.string(data, "visibilityType", "always"));
        ButtonSize width = convertSize(LayoutImportUtils.object(data, "buttonSize"),
                textWidthEstimate(LayoutImportUtils.textOf(data, "text", "")), false);
        ButtonSize height = convertSize(LayoutImportUtils.object(data, "buttonSize"),
                textHeightEstimate(), true);
        JsonObject position = LayoutImportUtils.object(data, "position");
        ViewPosition x = LayoutImportUtils.percentPosition(LayoutImportUtils.perTenThousandToPercent(
                LayoutImportUtils.integer(position, "x", 0)));
        ViewPosition y = LayoutImportUtils.percentPosition(LayoutImportUtils.perTenThousandToPercent(
                LayoutImportUtils.integer(position, "y", 0)));
        return new BaseButtonInfo(
                LayoutImportUtils.newUuid(), "", childName,
                LayoutImportUtils.textOf(data, "text", ""),
                showType,
                sizeTypeOf(LayoutImportUtils.object(data, "buttonSize")),
                width, height,
                LayoutImportUtils.POSITION_PERCENT, x, y,
                BaseButtonInfo.FUNCTION_TYPE_TOUCH,
                false, false, false, false, false, false, false, false, false,
                new ArrayList<String>(), "", new ArrayList<Integer>(), true, style);
    }

    private static BaseRockerViewInfo convertJoystick(JsonObject data, Map<String, RockerStyle> byUuid,
                                                      Map<String, RockerStyle> byName, String childName,
                                                      String layerVisibility) {
        // ZL2 的摇杆尺寸直接放在 JoystickData 顶层：sizeType / sizeDp / sizePercentage
        String sizeType = LayoutImportUtils.string(data, "sizeType", "percentage");
        boolean absolute = "dp".equalsIgnoreCase(sizeType);
        int dp = Math.round(LayoutImportUtils.decimal(data, "sizeDp", 200f));
        RockerSize rockerSize = absolute
                ? LayoutImportUtils.rockerSizeAbsolute(dp, LayoutImportUtils.REF_HEIGHT)
                : LayoutImportUtils.rockerSizePercent(
                        LayoutImportUtils.integer(data, "sizePercentage", 2500) / 10000f,
                        LayoutImportUtils.REF_HEIGHT, dp);

        JsonObject position = LayoutImportUtils.object(data, "position");
        ViewPosition x = LayoutImportUtils.percentPosition(LayoutImportUtils.perTenThousandToPercent(
                LayoutImportUtils.integer(position, "x", 0)));
        ViewPosition y = LayoutImportUtils.percentPosition(LayoutImportUtils.perTenThousandToPercent(
                LayoutImportUtils.integer(position, "y", 0)));

        RockerStyle style = resolveJoystickStyle(data, byUuid, byName);
        int showType = combineVisibility(layerVisibility,
                LayoutImportUtils.string(data, "visibilityType", "always"));
        return new BaseRockerViewInfo(
                LayoutImportUtils.newUuid(),
                "",
                childName,
                showType,
                absolute ? LayoutImportUtils.SIZE_ABSOLUTE : LayoutImportUtils.SIZE_PERCENT,
                rockerSize,
                LayoutImportUtils.POSITION_PERCENT,
                x,
                y,
                BaseRockerViewInfo.FUNCTION_FOLLOW_CENTER,
                false,
                true,
                style
        );
    }

    private static void parseClickEvents(JsonArray events, Map<String, String> layerNamesById,
                                         ArrayList<Integer> keycodes, ArrayList<String> visibilityControl,
                                         boolean[] openMenu, boolean[] showInput, StringBuilder outputText) {
        for (JsonElement element : events) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject event = element.getAsJsonObject();
            String type = LayoutImportUtils.string(event, "type", "key");
            String key = LayoutImportUtils.string(event, "key", "");
            if ("key".equalsIgnoreCase(type)) {
                int code = LayoutImportUtils.glfwKey(key);
                if (code >= 0 && !keycodes.contains(code)) {
                    keycodes.add(code);
                }
            } else if ("launcher_event".equalsIgnoreCase(type)) {
                String lower = key.toLowerCase();
                if (lower.contains("switch_menu")) {
                    openMenu[0] = true;
                } else if (lower.contains("switch_ime")) {
                    showInput[0] = true;
                } else if (lower.contains("scroll_up")) {
                    if (!keycodes.contains(3)) {
                        keycodes.add(3);
                    }
                } else if (lower.contains("scroll_down")) {
                    if (!keycodes.contains(4)) {
                        keycodes.add(4);
                    }
                } else if (key.startsWith("GLFW_MOUSE_") || key.startsWith("GLFW_KEY_")
                        || key.startsWith("key.mouse.") || key.startsWith("key.keyboard.")) {
                    int code = LayoutImportUtils.glfwKey(key);
                    if (code >= 0 && !keycodes.contains(code)) {
                        keycodes.add(code);
                    }
                }
            } else if ("switch_layer".equalsIgnoreCase(type)
                    || "show_layer".equalsIgnoreCase(type)
                    || "hide_layer".equalsIgnoreCase(type)) {
                String layerName = layerNamesById.get(key);
                if (layerName != null && !layerName.isEmpty() && !visibilityControl.contains(layerName)) {
                    visibilityControl.add(layerName);
                }
            } else if ("send_text".equalsIgnoreCase(type)) {
                if (outputText.length() == 0) {
                    outputText.append(key);
                }
            }
        }
    }

    private static ButtonStyle resolveButtonStyle(JsonObject data, Map<String, ButtonStyle> byUuid,
                                                  Map<String, ButtonStyle> byName) {
        String styleId = LayoutImportUtils.string(data, "buttonStyle", "");
        ButtonStyle style = byUuid.get(styleId);
        if (style == null) {
            style = byName.get(styleId);
        }
        return style == null ? LayoutImportUtils.defaultButtonStyle() : style;
    }

    private static RockerStyle resolveJoystickStyle(JsonObject data, Map<String, RockerStyle> byUuid,
                                                    Map<String, RockerStyle> byName) {
        String styleId = LayoutImportUtils.string(data, "joystickStyleId", "");
        RockerStyle style = byUuid.get(styleId);
        if (style == null) {
            style = byName.get(styleId);
        }
        return style == null ? LayoutImportUtils.defaultRockerStyle() : style;
    }

    private static int sizeTypeOf(JsonObject size) {
        String type = LayoutImportUtils.string(size, "type", "percentage");
        return "dp".equalsIgnoreCase(type) || "wrap_content".equalsIgnoreCase(type)
                ? LayoutImportUtils.SIZE_ABSOLUTE : LayoutImportUtils.SIZE_PERCENT;
    }

    private static ButtonSize convertSize(JsonObject size, int fallbackDp, boolean height) {
        String type = LayoutImportUtils.string(size, "type", "percentage");
        if ("dp".equalsIgnoreCase(type)) {
            int dp = Math.round(LayoutImportUtils.decimal(size, height ? "heightDp" : "widthDp",
                    fallbackDp));
            return LayoutImportUtils.buttonSizeAbsolute(dp, LayoutImportUtils.REF_HEIGHT);
        }
        if ("wrap_content".equalsIgnoreCase(type)) {
            return LayoutImportUtils.buttonSizeAbsolute(fallbackDp, LayoutImportUtils.REF_HEIGHT);
        }
        int percentage = LayoutImportUtils.integer(size,
                height ? "heightPercentage" : "widthPercentage", 1400);
        String reference = LayoutImportUtils.string(size,
                height ? "heightReference" : "widthReference", "screen_height");
        int ref = "screen_width".equalsIgnoreCase(reference)
                ? LayoutImportUtils.REF_WIDTH : LayoutImportUtils.REF_HEIGHT;
        return LayoutImportUtils.buttonSizePercent(percentage / 10000f, ref, 50);
    }

    private static int textWidthEstimate(String text) {
        int length = text == null ? 0 : text.length();
        return Math.max(30, Math.min(480, 16 + length * 10));
    }

    private static int textHeightEstimate() {
        return 40;
    }

    private static int combineVisibility(String layerVisibility, String widgetVisibility) {
        String visibility = widgetVisibility;
        if ("always".equalsIgnoreCase(visibility)
                && ("in_game".equalsIgnoreCase(layerVisibility)
                || "in_menu".equalsIgnoreCase(layerVisibility))) {
            visibility = layerVisibility;
        }
        if ("in_game".equalsIgnoreCase(visibility)) {
            return LayoutImportUtils.SHOW_IN_GAME;
        }
        if ("in_menu".equalsIgnoreCase(visibility)) {
            return LayoutImportUtils.SHOW_IN_MENU;
        }
        return LayoutImportUtils.SHOW_ALWAYS;
    }

    private static float maxCornerRadius(JsonObject shape) {
        if (shape == null) {
            return 0;
        }
        return Math.max(
                Math.max(LayoutImportUtils.decimal(shape, "topStart", 0f),
                        LayoutImportUtils.decimal(shape, "topEnd", 0f)),
                Math.max(LayoutImportUtils.decimal(shape, "bottomEnd", 0f),
                        LayoutImportUtils.decimal(shape, "bottomStart", 0f)));
    }

    /** ZL2 颜色：高 32 位非 0 时取高 32 位（历史格式），否则取低 32 位（新格式）。 */
    static int zl2Color(long value) {
        long high = (value >>> 32) & 0xFFFFFFFFL;
        return high != 0 ? (int) high : (int) value;
    }
}
