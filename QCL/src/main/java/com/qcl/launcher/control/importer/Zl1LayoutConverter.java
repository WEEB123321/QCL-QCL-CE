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
import java.util.List;

/**
 * Zalith Launcher（ZL1，Pojav 系，version 8）控制布局 -> QCL 控键布局。
 *
 * <p>ZL1 的坐标是像素表达式（含 ${screen_width}、${width}、px()/dp()），
 * 这里用参考屏幕求值后换算成百分比；尺寸本身是 Dp，直接沿用。
 *
 * <p>ZL1 没有图层，但有抽屉（drawer）：每个抽屉转成一个默认隐藏的 QCL 子布局，
 * 抽屉把手通过 visibilityControl 切换该子布局，从而保留「点开抽屉出现一排按钮」的用法。
 */
final class Zl1LayoutConverter {

    private Zl1LayoutConverter() {
    }

    static ExternalLayoutImporter.Result convert(JsonObject root, String fallbackName) {
        JsonObject info = LayoutImportUtils.object(root, "mControlInfoDataList");
        String name = LayoutImportUtils.string(info, "name", fallbackName);
        if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name)) {
            name = fallbackName;
        }
        String author = LayoutImportUtils.cleanText(
                LayoutImportUtils.string(info, "author", ""), "Zalith Launcher");
        String versionName = LayoutImportUtils.cleanText(
                LayoutImportUtils.string(info, "version", "1.0"), "1.0");
        String description = LayoutImportUtils.cleanText(
                LayoutImportUtils.string(info, "desc", ""), "");

        ArrayList<BaseButtonInfo> mainButtons = new ArrayList<>();
        ArrayList<BaseRockerViewInfo> mainRockers = new ArrayList<>();
        List<ChildLayout> children = new ArrayList<>();

        for (JsonElement element : LayoutImportUtils.array(root, "mControlDataList")) {
            if (element != null && element.isJsonObject()) {
                mainButtons.add(convertButton(element.getAsJsonObject(), "game_layout"));
            }
        }
        for (JsonElement element : LayoutImportUtils.array(root, "mJoystickDataList")) {
            if (element != null && element.isJsonObject()) {
                BaseRockerViewInfo rocker = convertJoystick(element.getAsJsonObject(), "game_layout");
                if (rocker != null) {
                    mainRockers.add(rocker);
                }
            }
        }

        int drawerIndex = 0;
        for (JsonElement element : LayoutImportUtils.array(root, "mDrawerDataList")) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject drawer = element.getAsJsonObject();
            drawerIndex++;
            JsonObject properties = LayoutImportUtils.object(drawer, "properties");
            String drawerName = properties == null
                    ? "" : LayoutImportUtils.string(properties, "name", "");
            if (drawerName.trim().isEmpty()) {
                drawerName = "抽屉" + drawerIndex;
            }
            String childName = drawerName + "_子菜单";

            ArrayList<BaseButtonInfo> subButtons = new ArrayList<>();
            for (JsonElement subElement : LayoutImportUtils.array(drawer, "buttonProperties")) {
                if (subElement != null && subElement.isJsonObject()) {
                    subButtons.add(convertButton(subElement.getAsJsonObject(), childName));
                }
            }
            children.add(new ChildLayout(childName, 4, subButtons, new ArrayList<BaseRockerViewInfo>()));

            BaseButtonInfo handle = convertButton(
                    properties == null ? new JsonObject() : properties, "game_layout");
            handle.text = drawerName;
            handle.visibilityControl.add(childName);
            mainButtons.add(handle);
        }

        children.add(0, new ChildLayout("game_layout", 0, mainButtons, mainRockers));
        return new ExternalLayoutImporter.Result(
                "ZL1", name, author, versionName, description, children);
    }

    private static BaseButtonInfo convertButton(JsonObject data, String childName) {
        String text = LayoutImportUtils.string(data, "name", "");
        float widthDp = LayoutImportUtils.decimal(data, "width", 50f);
        float heightDp = LayoutImportUtils.decimal(data, "height", 50f);
        widthDp = Math.max(1f, widthDp);
        heightDp = Math.max(1f, heightDp);

        int showType = showType(
                LayoutImportUtils.bool(data, "displayInGame", true),
                LayoutImportUtils.bool(data, "displayInMenu", true));

        String dynamicX = LayoutImportUtils.string(data, "dynamicX", "");
        String dynamicY = LayoutImportUtils.string(data, "dynamicY", "");
        ViewPosition x = LayoutImportUtils.percentPosition(
                LayoutImportUtils.zl1PositionPercent(dynamicX, widthDp, heightDp, true));
        ViewPosition y = LayoutImportUtils.percentPosition(
                LayoutImportUtils.zl1PositionPercent(dynamicY, widthDp, heightDp, false));

        boolean openMenu = false;
        boolean showInput = false;
        ArrayList<Integer> keycodes = new ArrayList<>();
        for (JsonElement element : LayoutImportUtils.array(data, "keycodes")) {
            if (element == null || !element.isJsonPrimitive()) {
                continue;
            }
            int code;
            try {
                code = element.getAsInt();
            } catch (Throwable ignored) {
                continue;
            }
            switch (code) {
                case -1:
                    showInput = true;
                    break;
                case -2:
                case -9:
                    openMenu = true;
                    break;
                case -3:
                    addKey(keycodes, 0);
                    break;
                case -4:
                    addKey(keycodes, 1);
                    break;
                case -6:
                    addKey(keycodes, 2);
                    break;
                case -7:
                    addKey(keycodes, 3);
                    break;
                case -8:
                    addKey(keycodes, 4);
                    break;
                case -5:
                    // 虚拟鼠标开关：QCL 没有同款按键，交由「菜单」里的鼠标模式处理
                    break;
                default:
                    // ZL1 的 0 是 GLFW_KEY_UNKNOWN（空槽），而 QCL 的 0 是鼠标左键，必须丢弃
                    if (code > 0) {
                        addKey(keycodes, code);
                    }
                    break;
            }
        }

        float opacity = LayoutImportUtils.decimal(data, "opacity", 1f);
        ButtonStyle style = convertButtonStyle(data, widthDp, heightDp, opacity);
        return new BaseButtonInfo(
                LayoutImportUtils.newUuid(),
                "",
                childName,
                text,
                showType,
                LayoutImportUtils.SIZE_ABSOLUTE,
                LayoutImportUtils.buttonSizeAbsolute(Math.round(widthDp), LayoutImportUtils.REF_WIDTH),
                LayoutImportUtils.buttonSizeAbsolute(Math.round(heightDp), LayoutImportUtils.REF_HEIGHT),
                LayoutImportUtils.POSITION_PERCENT,
                x,
                y,
                BaseButtonInfo.FUNCTION_TYPE_TOUCH,
                false,
                LayoutImportUtils.bool(data, "isToggle", false),
                false,
                openMenu,
                false,
                false,
                false,
                false,
                showInput,
                new ArrayList<String>(),
                "",
                keycodes,
                true,
                style
        );
    }

    private static BaseRockerViewInfo convertJoystick(JsonObject data, String childName) {
        float widthDp = LayoutImportUtils.decimal(data, "width", 160f);
        float heightDp = LayoutImportUtils.decimal(data, "height", widthDp);
        float sizeDp = Math.max(1f, Math.min(widthDp, heightDp));

        String dynamicX = LayoutImportUtils.string(data, "dynamicX", "");
        String dynamicY = LayoutImportUtils.string(data, "dynamicY", "");
        ViewPosition x = LayoutImportUtils.percentPosition(
                LayoutImportUtils.zl1PositionPercent(dynamicX, sizeDp, sizeDp, true));
        ViewPosition y = LayoutImportUtils.percentPosition(
                LayoutImportUtils.zl1PositionPercent(dynamicY, sizeDp, sizeDp, false));

        boolean absolute = LayoutImportUtils.bool(data, "absolute", false);
        float opacity = LayoutImportUtils.decimal(data, "opacity", 1f);
        RockerStyle style = convertRockerStyle(data, opacity);
        int showType = showType(
                LayoutImportUtils.bool(data, "displayInGame", true),
                LayoutImportUtils.bool(data, "displayInMenu", true));
        return new BaseRockerViewInfo(
                LayoutImportUtils.newUuid(),
                "",
                childName,
                showType,
                LayoutImportUtils.SIZE_ABSOLUTE,
                LayoutImportUtils.rockerSizeAbsolute(Math.round(sizeDp), LayoutImportUtils.REF_HEIGHT),
                LayoutImportUtils.POSITION_PERCENT,
                x,
                y,
                absolute ? BaseRockerViewInfo.FUNCTION_FOLLOW_ALL : BaseRockerViewInfo.FUNCTION_FOLLOW_CENTER,
                LayoutImportUtils.bool(data, "forwardLock", false),
                true,
                style
        );
    }

    private static ButtonStyle convertButtonStyle(JsonObject data, float widthDp, float heightDp,
                                                  float opacity) {
        int bgColor = LayoutImportUtils.integer(data, "bgColor", 0x4D000000);
        int strokeColor = LayoutImportUtils.integer(data, "strokeColor", 0xFFFFFFFF);
        float strokeWidth = LayoutImportUtils.decimal(data, "strokeWidth", 0f);
        float cornerPercent = LayoutImportUtils.decimal(data, "cornerRadius", 0f);
        int cornerRadius = Math.round(Math.min(widthDp, heightDp) / 2f * cornerPercent / 100f);
        int textColor = 0xFFFFFFFF;
        int textColorPress = 0xFFFFFFFF;
        return LayoutImportUtils.buttonStyle(
                "ZL1",
                14,
                textColor,
                cornerRadius,
                strokeWidth,
                LayoutImportUtils.withAlpha(strokeColor, opacity),
                LayoutImportUtils.withAlpha(bgColor, opacity),
                14,
                textColorPress,
                cornerRadius,
                strokeWidth,
                LayoutImportUtils.withAlpha(strokeColor, opacity),
                LayoutImportUtils.withAlpha(bgColor, LayoutImportUtils.clamp01(opacity * 1.25f))
        );
    }

    private static RockerStyle convertRockerStyle(JsonObject data, float opacity) {
        int bgColor = LayoutImportUtils.integer(data, "bgColor", 0x4D000000);
        int strokeColor = LayoutImportUtils.integer(data, "strokeColor", 0xFFFFFFFF);
        float strokeWidth = LayoutImportUtils.decimal(data, "strokeWidth", 0f);
        return LayoutImportUtils.rockerStyle(
                "ZL1",
                80,
                strokeWidth,
                LayoutImportUtils.withAlpha(strokeColor, opacity),
                LayoutImportUtils.withAlpha(bgColor, opacity),
                LayoutImportUtils.withAlpha(0xF6F6F6, opacity),
                80,
                strokeWidth,
                LayoutImportUtils.withAlpha(strokeColor, opacity),
                LayoutImportUtils.withAlpha(bgColor, LayoutImportUtils.clamp01(opacity * 1.25f)),
                LayoutImportUtils.withAlpha(0xF6F6F6, opacity)
        );
    }

    private static int showType(boolean inGame, boolean inMenu) {
        if (inGame && inMenu) {
            return LayoutImportUtils.SHOW_ALWAYS;
        }
        if (inGame) {
            return LayoutImportUtils.SHOW_IN_GAME;
        }
        if (inMenu) {
            return LayoutImportUtils.SHOW_IN_MENU;
        }
        return LayoutImportUtils.SHOW_IN_GAME;
    }

    private static void addKey(ArrayList<Integer> keycodes, int code) {
        if (code >= 0 && !keycodes.contains(code)) {
            keycodes.add(code);
        }
    }
}
