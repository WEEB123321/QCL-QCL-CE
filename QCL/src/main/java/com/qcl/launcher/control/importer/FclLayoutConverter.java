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
 * Fold Craft Launcher（FCL，controllerVersion 21）控制布局 -> QCL 控键布局。
 *
 * <p>关键换算：
 * <ul>
 *   <li>坐标/百分比尺寸是 0~1000 的「相对可用空间」值，与 QCL 的 percentPosition 同义，直接 /1000；</li>
 *   <li>FCL 键码是 Linux/LWJGL2 整数，需要映射到 GLFW；</li>
 *   <li>方向控件（directionList）转成 QCL 摇杆；</li>
 *   <li>viewGroups 转成 QCL 的子布局（ChildLayout），初始可见性按 visibility 还原。</li>
 * </ul>
 */
final class FclLayoutConverter {

    private FclLayoutConverter() {
    }

    static ExternalLayoutImporter.Result convert(JsonObject root, String fallbackName) {
        String name = LayoutImportUtils.string(root, "name", fallbackName);
        if (name == null || name.trim().isEmpty()) {
            name = fallbackName;
        }
        String author = LayoutImportUtils.string(root, "author", "FCL");
        String versionName = LayoutImportUtils.string(root, "version", "1.0");
        String description = LayoutImportUtils.string(root, "description", "");

        Map<String, ButtonStyle> buttonStyles = parseButtonStyles(root);
        Map<String, JsonObject> directionStyles = new LinkedHashMap<>();
        for (JsonElement element : LayoutImportUtils.array(root, "directionStyles")) {
            if (element != null && element.isJsonObject()) {
                JsonObject style = element.getAsJsonObject();
                directionStyles.put(LayoutImportUtils.string(style, "name", ""), style);
            }
        }

        Map<String, String> groupNamesById = new LinkedHashMap<>();
        for (JsonElement element : LayoutImportUtils.array(root, "viewGroups")) {
            if (element != null && element.isJsonObject()) {
                JsonObject group = element.getAsJsonObject();
                String id = LayoutImportUtils.string(group, "id", "");
                if (!id.isEmpty()) {
                    groupNamesById.put(id, groupName(group));
                }
            }
        }

        List<ChildLayout> children = new ArrayList<>();
        for (JsonElement element : LayoutImportUtils.array(root, "viewGroups")) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject group = element.getAsJsonObject();
            String childName = groupName(group);
            int visibility = "INVISIBLE".equalsIgnoreCase(LayoutImportUtils.string(group, "visibility", "VISIBLE")) ? 4 : 0;
            ArrayList<BaseButtonInfo> buttons = new ArrayList<>();
            ArrayList<BaseRockerViewInfo> rockers = new ArrayList<>();

            JsonObject viewData = LayoutImportUtils.object(group, "viewData");
            for (JsonElement buttonElement : LayoutImportUtils.array(viewData, "buttonList")) {
                if (buttonElement != null && buttonElement.isJsonObject()) {
                    BaseButtonInfo button = convertButton(buttonElement.getAsJsonObject(), buttonStyles,
                            groupNamesById, childName);
                    if (button != null) {
                        buttons.add(button);
                    }
                }
            }
            for (JsonElement directionElement : LayoutImportUtils.array(viewData, "directionList")) {
                if (directionElement != null && directionElement.isJsonObject()) {
                    BaseRockerViewInfo rocker = convertDirection(directionElement.getAsJsonObject(),
                            directionStyles, childName);
                    if (rocker != null) {
                        rockers.add(rocker);
                    }
                }
            }
            children.add(new ChildLayout(childName, visibility, buttons, rockers));
        }

        return new ExternalLayoutImporter.Result(
                "FCL", name, author, versionName, description, children);
    }

    private static String groupName(JsonObject group) {
        String name = LayoutImportUtils.string(group, "name", "");
        return name.trim().isEmpty() ? "game_layout" : name;
    }

    private static Map<String, ButtonStyle> parseButtonStyles(JsonObject root) {
        Map<String, ButtonStyle> result = new HashMap<>();
        for (JsonElement element : LayoutImportUtils.array(root, "buttonStyles")) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject style = element.getAsJsonObject();
            String name = LayoutImportUtils.string(style, "name", "");
            result.put(name, convertButtonStyle(style));
        }
        if (!result.containsKey("")) {
            result.put("", LayoutImportUtils.defaultButtonStyle());
        }
        return result;
    }

    private static ButtonStyle convertButtonStyle(JsonObject style) {
        int textColor = LayoutImportUtils.integer(style, "textColor", 0xFFFFFFFF);
        int textSize = LayoutImportUtils.integer(style, "textSize", 14);
        int strokeColor = LayoutImportUtils.integer(style, "strokeColor", 0x33555555);
        int fillColor = LayoutImportUtils.integer(style, "fillColor", 0x666E6E6E);
        int cornerRadius = Math.round(LayoutImportUtils.decimal(style, "cornerRadius", 160f) / 10f);
        float strokeWidth = LayoutImportUtils.decimal(style, "strokeWidth", 15f) / 10f;

        int textColorPress = LayoutImportUtils.integer(style, "textColorPressed", textColor);
        int textSizePress = LayoutImportUtils.integer(style, "textSizePressed", textSize);
        int strokeColorPress = LayoutImportUtils.integer(style, "strokeColorPressed", strokeColor);
        int fillColorPress = LayoutImportUtils.integer(style, "fillColorPressed", fillColor);
        int cornerRadiusPress = Math.round(LayoutImportUtils.decimal(style, "cornerRadiusPressed",
                cornerRadius * 10f) / 10f);
        float strokeWidthPress = LayoutImportUtils.decimal(style, "strokeWidthPressed", strokeWidth * 10f) / 10f;

        return LayoutImportUtils.buttonStyle(
                LayoutImportUtils.string(style, "name", "FCL"),
                textSize, textColor, cornerRadius, strokeWidth, strokeColor, fillColor,
                textSizePress, textColorPress, cornerRadiusPress, strokeWidthPress,
                strokeColorPress, fillColorPress);
    }

    private static BaseButtonInfo convertButton(JsonObject button, Map<String, ButtonStyle> styles,
                                                Map<String, String> groupNamesById, String childName) {
        JsonObject baseInfo = LayoutImportUtils.object(button, "baseInfo");
        if (baseInfo == null) {
            baseInfo = new JsonObject();
        }
        String text = LayoutImportUtils.string(button, "text", "");
        int showType = showType(LayoutImportUtils.string(baseInfo, "visibilityType", "ALWAYS"));
        int sizeType = "ABSOLUTE".equalsIgnoreCase(LayoutImportUtils.string(baseInfo, "sizeType", "PERCENTAGE"))
                ? LayoutImportUtils.SIZE_ABSOLUTE : LayoutImportUtils.SIZE_PERCENT;

        JsonObject percentWidth = LayoutImportUtils.object(baseInfo, "percentageWidth");
        JsonObject percentHeight = LayoutImportUtils.object(baseInfo, "percentageHeight");
        int referenceWidth = "SCREEN_HEIGHT".equalsIgnoreCase(
                LayoutImportUtils.string(percentWidth, "reference", "SCREEN_WIDTH"))
                ? LayoutImportUtils.REF_HEIGHT : LayoutImportUtils.REF_WIDTH;
        int referenceHeight = "SCREEN_HEIGHT".equalsIgnoreCase(
                LayoutImportUtils.string(percentHeight, "reference", "SCREEN_WIDTH"))
                ? LayoutImportUtils.REF_HEIGHT : LayoutImportUtils.REF_WIDTH;

        int absoluteWidth = LayoutImportUtils.integer(baseInfo, "absoluteWidth", 50);
        int absoluteHeight = LayoutImportUtils.integer(baseInfo, "absoluteHeight", 50);
        ButtonSize width;
        ButtonSize height;
        if (sizeType == LayoutImportUtils.SIZE_ABSOLUTE) {
            width = LayoutImportUtils.buttonSizeAbsolute(absoluteWidth, referenceWidth);
            height = LayoutImportUtils.buttonSizeAbsolute(absoluteHeight, referenceHeight);
        } else {
            int percentWidthValue = LayoutImportUtils.integer(percentWidth, "size", 50);
            int percentHeightValue = LayoutImportUtils.integer(percentHeight, "size", 50);
            width = LayoutImportUtils.buttonSizePercent(percentWidthValue / 1000f, referenceWidth, absoluteWidth);
            height = LayoutImportUtils.buttonSizePercent(percentHeightValue / 1000f, referenceHeight, absoluteHeight);
        }

        ViewPosition x = LayoutImportUtils.percentPosition(
                LayoutImportUtils.perMilleToPercent(LayoutImportUtils.integer(baseInfo, "xPosition", 0)));
        ViewPosition y = LayoutImportUtils.percentPosition(
                LayoutImportUtils.perMilleToPercent(LayoutImportUtils.integer(baseInfo, "yPosition", 0)));

        JsonObject eventRoot = LayoutImportUtils.object(button, "event");
        JsonObject press = LayoutImportUtils.object(eventRoot, "pressEvent");
        JsonObject click = LayoutImportUtils.object(eventRoot, "clickEvent");
        JsonObject longPress = LayoutImportUtils.object(eventRoot, "longPressEvent");
        JsonObject doubleClick = LayoutImportUtils.object(eventRoot, "doubleClickEvent");

        JsonObject chosen = null;
        int functionType = BaseButtonInfo.FUNCTION_TYPE_TOUCH;
        if (hasPayload(press)) {
            chosen = press;
        } else if (hasPayload(click)) {
            chosen = click;
        } else if (hasPayload(longPress)) {
            chosen = longPress;
        } else if (hasPayload(doubleClick)) {
            chosen = doubleClick;
            functionType = BaseButtonInfo.FUNCTION_TYPE_DOUBLE_CLICK;
        }
        if (chosen == null) {
            chosen = press != null ? press : click;
        }

        ArrayList<Integer> keycodes = LayoutImportUtils.mapKeycodes(
                LayoutImportUtils.array(chosen, "outputKeycodes"), LayoutImportUtils::fclKey);
        ArrayList<String> visibilityControl = new ArrayList<>();
        collectBindGroups(press, groupNamesById, visibilityControl);
        collectBindGroups(click, groupNamesById, visibilityControl);
        collectBindGroups(longPress, groupNamesById, visibilityControl);
        collectBindGroups(doubleClick, groupNamesById, visibilityControl);

        ButtonStyle style = resolveButtonStyle(button, styles);

        return new BaseButtonInfo(
                LayoutImportUtils.newUuid(),
                "",
                childName,
                text,
                showType,
                sizeType,
                width,
                height,
                LayoutImportUtils.POSITION_PERCENT,
                x,
                y,
                functionType,
                LayoutImportUtils.bool(eventRoot, "pointerFollow", false),
                LayoutImportUtils.bool(chosen, "autoKeep", false),
                LayoutImportUtils.bool(chosen, "autoClick", false),
                LayoutImportUtils.bool(chosen, "openMenu", false),
                LayoutImportUtils.bool(eventRoot, "Movable", false),
                LayoutImportUtils.bool(chosen, "switchTouchMode", false),
                false,
                false,
                LayoutImportUtils.bool(chosen, "input", false)
                        || LayoutImportUtils.bool(chosen, "quickInput", false),
                visibilityControl,
                LayoutImportUtils.string(chosen, "outputText", ""),
                keycodes,
                true,
                style
        );
    }

    private static ButtonStyle resolveButtonStyle(JsonObject button, Map<String, ButtonStyle> styles) {
        JsonElement styleElement = button.get("style");
        if (styleElement != null && styleElement.isJsonObject()) {
            return convertButtonStyle(styleElement.getAsJsonObject());
        }
        String styleName = LayoutImportUtils.string(button, "style", "");
        ButtonStyle style = styles.get(styleName);
        if (style == null) {
            style = styles.get("");
        }
        return style == null ? LayoutImportUtils.defaultButtonStyle() : style;
    }

    private static BaseRockerViewInfo convertDirection(JsonObject direction, Map<String, JsonObject> directionStyles,
                                                       String childName) {
        JsonObject baseInfo = LayoutImportUtils.object(direction, "baseInfo");
        if (baseInfo == null) {
            baseInfo = new JsonObject();
        }
        int showType = showType(LayoutImportUtils.string(baseInfo, "visibilityType", "ALWAYS"));
        boolean absolute = "ABSOLUTE".equalsIgnoreCase(LayoutImportUtils.string(baseInfo, "sizeType", "PERCENTAGE"));

        JsonObject percentWidth = LayoutImportUtils.object(baseInfo, "percentageWidth");
        int reference = "SCREEN_HEIGHT".equalsIgnoreCase(
                LayoutImportUtils.string(percentWidth, "reference", "SCREEN_WIDTH"))
                ? LayoutImportUtils.REF_HEIGHT : LayoutImportUtils.REF_WIDTH;
        int absoluteWidth = LayoutImportUtils.integer(baseInfo, "absoluteWidth", 160);
        RockerSize size = absolute
                ? LayoutImportUtils.rockerSizeAbsolute(absoluteWidth, reference)
                : LayoutImportUtils.rockerSizePercent(
                        LayoutImportUtils.integer(percentWidth, "size", 400) / 1000f, reference, absoluteWidth);

        ViewPosition x = LayoutImportUtils.percentPosition(
                LayoutImportUtils.perMilleToPercent(LayoutImportUtils.integer(baseInfo, "xPosition", 0)));
        ViewPosition y = LayoutImportUtils.percentPosition(
                LayoutImportUtils.perMilleToPercent(LayoutImportUtils.integer(baseInfo, "yPosition", 0)));

        JsonObject event = LayoutImportUtils.object(direction, "event");
        String followOption = LayoutImportUtils.string(event, "followOption", "CENTER_FOLLOW");
        int followType;
        if ("FIXED".equalsIgnoreCase(followOption)) {
            followType = BaseRockerViewInfo.FUNCTION_FOLLOW_NONE;
        } else if ("FOLLOW".equalsIgnoreCase(followOption)) {
            followType = BaseRockerViewInfo.FUNCTION_FOLLOW_ALL;
        } else {
            followType = BaseRockerViewInfo.FUNCTION_FOLLOW_CENTER;
        }
        boolean shift = LayoutImportUtils.bool(event, "sneak", true);

        RockerStyle style = resolveRockerStyle(direction, directionStyles);
        return new BaseRockerViewInfo(
                LayoutImportUtils.newUuid(),
                "",
                childName,
                showType,
                absolute ? LayoutImportUtils.SIZE_ABSOLUTE : LayoutImportUtils.SIZE_PERCENT,
                size,
                LayoutImportUtils.POSITION_PERCENT,
                x,
                y,
                followType,
                shift,
                true,
                style
        );
    }

    private static RockerStyle resolveRockerStyle(JsonObject direction, Map<String, JsonObject> directionStyles) {
        JsonElement styleElement = direction.get("style");
        JsonObject style = null;
        if (styleElement != null && styleElement.isJsonObject()) {
            style = styleElement.getAsJsonObject();
        } else {
            String styleName = LayoutImportUtils.string(direction, "style", "");
            style = directionStyles.get(styleName);
        }
        if (style == null) {
            return LayoutImportUtils.defaultRockerStyle();
        }

        boolean rocker = "ROCKER".equalsIgnoreCase(LayoutImportUtils.string(style, "styleType", "BUTTON"));
        if (rocker) {
            JsonObject rockerStyle = LayoutImportUtils.object(style, "rockerStyle");
            if (rockerStyle == null) {
                rockerStyle = new JsonObject();
            }
            int bgCorner = LayoutImportUtils.integer(rockerStyle, "bgCornerRadius", 500);
            int cornerRadius = Math.round(bgCorner / 500f * 80f);
            float strokeWidth = LayoutImportUtils.integer(rockerStyle, "bgStrokeWidth", 20) / 10f;
            int strokeColor = LayoutImportUtils.integer(rockerStyle, "bgStrokeColor", 0x33555555);
            int fillColor = LayoutImportUtils.integer(rockerStyle, "bgFillColor", 0x666E6E6E);
            int pointerColor = LayoutImportUtils.integer(rockerStyle, "rockerFillColor", 0xF6F6F6);
            int pointerStroke = LayoutImportUtils.integer(rockerStyle, "rockerStrokeColor", 0x55555555);
            return LayoutImportUtils.rockerStyle(
                    LayoutImportUtils.string(style, "name", "FCL"),
                    cornerRadius, strokeWidth, strokeColor, fillColor, pointerColor,
                    cornerRadius, strokeWidth, strokeColor, fillColor, pointerStroke);
        }

        JsonObject buttonStyle = LayoutImportUtils.object(style, "buttonStyle");
        if (buttonStyle == null) {
            buttonStyle = new JsonObject();
        }
        int cornerRadius = Math.round(LayoutImportUtils.decimal(buttonStyle, "cornerRadius", 160f) / 10f);
        float strokeWidth = LayoutImportUtils.decimal(buttonStyle, "strokeWidth", 15f) / 10f;
        int strokeColor = LayoutImportUtils.integer(buttonStyle, "strokeColor", 0x33555555);
        int fillColor = LayoutImportUtils.integer(buttonStyle, "fillColor", 0x666E6E6E);
        int pointerColor = LayoutImportUtils.integer(buttonStyle, "textColor", 0xF6F6F6);
        return LayoutImportUtils.rockerStyle(
                LayoutImportUtils.string(style, "name", "FCL"),
                cornerRadius, strokeWidth, strokeColor, fillColor, pointerColor,
                Math.round(LayoutImportUtils.decimal(buttonStyle, "cornerRadiusPressed", cornerRadius * 10f) / 10f),
                LayoutImportUtils.decimal(buttonStyle, "strokeWidthPressed", strokeWidth * 10f) / 10f,
                LayoutImportUtils.integer(buttonStyle, "strokeColorPressed", strokeColor),
                LayoutImportUtils.integer(buttonStyle, "fillColorPressed", fillColor),
                pointerColor);
    }

    private static int showType(String visibilityType) {
        if ("IN_GAME".equalsIgnoreCase(visibilityType)) {
            return LayoutImportUtils.SHOW_IN_GAME;
        }
        if ("MENU".equalsIgnoreCase(visibilityType)) {
            return LayoutImportUtils.SHOW_IN_MENU;
        }
        return LayoutImportUtils.SHOW_ALWAYS;
    }

    private static boolean hasPayload(JsonObject event) {
        if (event == null) {
            return false;
        }
        if (LayoutImportUtils.array(event, "outputKeycodes").size() > 0) {
            return true;
        }
        if (LayoutImportUtils.bool(event, "input", false)
                || LayoutImportUtils.bool(event, "quickInput", false)
                || LayoutImportUtils.bool(event, "openMenu", false)
                || LayoutImportUtils.bool(event, "switchTouchMode", false)
                || LayoutImportUtils.bool(event, "switchMouseMode", false)) {
            return true;
        }
        if (!LayoutImportUtils.string(event, "outputText", "").isEmpty()) {
            return true;
        }
        return LayoutImportUtils.array(event, "bindViewGroup").size() > 0;
    }

    private static void collectBindGroups(JsonObject event, Map<String, String> groupNamesById,
                                          ArrayList<String> visibilityControl) {
        for (JsonElement element : LayoutImportUtils.array(event, "bindViewGroup")) {
            if (element == null || !element.isJsonPrimitive()) {
                continue;
            }
            String name = groupNamesById.get(element.getAsString());
            if (name != null && !name.isEmpty() && !visibilityControl.contains(name)) {
                visibilityControl.add(name);
            }
        }
    }
}
