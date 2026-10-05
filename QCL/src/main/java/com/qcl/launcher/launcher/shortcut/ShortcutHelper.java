package com.qcl.launcher.launcher.shortcut;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.util.Log;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;

import java.util.Collections;

/**
 * ★★★ 社区版新增：桌面快捷方式「一键启动当前实例」。
 *
 * <p><b>怎么实现的</b>：快捷方式带的 Intent 指向 {@code MainActivity} 并附
 * {@code qcl_auto_launch=true}；MainActivity 在<b>界面加载完成之后</b>（onResume 且 isLoaded）
 * 才去走启动流程。这样避免了「界面还没就绪就去启动游戏」这种半初始化操作 ——
 * 那正是历史上最容易闪退的一类路径。
 *
 * <p><b>★ 不申请任何权限</b>：用的是系统标准的 {@code requestPinShortcut}，
 * 由桌面弹出确认框，不需要 INSTALL_SHORTCUT 这类已废弃的权限。
 *
 * <p><b>★ 桌面不支持怎么办</b>：部分国产桌面不支持固定快捷方式，此时
 * {@code requestPinShortcut} 返回 false。界面上会如实提示，并告诉用户
 * 可以改从「桌面小部件」列表添加 —— 不假装成功。
 */
public final class ShortcutHelper {

    private static final String TAG = "QCLShortcut";

    /** 快捷方式 id。固定值 —— 同一个 id 重复创建只会更新，不会堆出一排。 */
    private static final String ID = "qcl_launch_current";

    private ShortcutHelper() {
    }

    /** 当前桌面是否支持「固定快捷方式」。 */
    public static boolean isSupported(Context context) {
        try {
            ShortcutManager sm = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
            return sm != null && sm.isRequestPinShortcutSupported();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 创建（或更新）「一键启动当前实例」的桌面快捷方式。
     *
     * @param instanceName 当前实例名，仅用于显示；为空则用通用文案
     * @return true = 系统已接受固定请求（用户还需在弹框上确认）；false = 桌面不支持或出错
     */
    public static boolean pinLaunchShortcut(Context context, String instanceName) {
        try {
            ShortcutManager sm = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                return false;
            }
            // ★ setIntent 必须带 action，否则 ShortcutInfo.Builder.build() 会抛异常。
            Intent intent = new Intent(context, MainActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("qcl_auto_launch", true);

            String label = instanceName == null || instanceName.isEmpty()
                    ? context.getString(R.string.widget_title)
                    : instanceName;

            ShortcutInfo info = new ShortcutInfo.Builder(context, ID)
                    .setShortLabel(label.length() > 10 ? label.substring(0, 10) : label)
                    .setLongLabel(context.getString(R.string.shortcut_row_title) + " · " + label)
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_baseline_launch_black))
                    .setIntent(intent)
                    .build();
            return sm.requestPinShortcut(info, null);
        } catch (Throwable t) {
            Log.w(TAG, "创建快捷方式失败: " + t);
            return false;
        }
    }

    /**
     * ★★★ 社区版新增：把**指定实例**固定到桌面。
     *
     * <p>与 {@link #pinLaunchShortcut} 的区别：那个固定的是「当前实例」（每次点开都是当时的当前实例）；
     * 这个是**认准一个实例**——快捷方式里带上 {@code qcl_instance=<版本目录>}，
     * 启动器收到后先把当前实例切过去、再启动。
     *
     * <p>★ 快捷方式 id 按实例名生成（而不是固定一个）：这样玩家可以把「1.20.1 生存」和
     * 「1.12.2 Forge 整合」各固定一个到桌面，互不覆盖。
     */
    public static boolean pinInstanceShortcut(Context context, String instancePath, String instanceName) {
        try {
            ShortcutManager sm = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                return false;
            }
            Intent intent = new Intent(context, MainActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("qcl_auto_launch", true);
            intent.putExtra("qcl_instance", instancePath == null ? "" : instancePath);

            String label = instanceName == null || instanceName.isEmpty()
                    ? context.getString(R.string.widget_title) : instanceName;
            ShortcutInfo info = new ShortcutInfo.Builder(context, idFor(instanceName))
                    .setShortLabel(label.length() > 10 ? label.substring(0, 10) : label)
                    .setLongLabel(label)
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_baseline_launch_black))
                    .setIntent(intent)
                    .build();
            return sm.requestPinShortcut(info, null);
        } catch (Throwable t) {
            Log.w(TAG, "固定实例快捷方式失败: " + t);
            return false;
        }
    }

    /** 每个实例一个稳定的快捷方式 id（只保留字母数字，避免非法字符）。 */
    private static String idFor(String instanceName) {
        String n = instanceName == null ? "" : instanceName.replaceAll("[^A-Za-z0-9]", "_");
        if (n.isEmpty()) {
            n = "default";
        }
        if (n.length() > 40) {
            n = n.substring(0, 40);
        }
        return "qcl_inst_" + n;
    }

    /**
     * 让系统「动态快捷方式」也带上这一项（长按应用图标时可见）。
     * 与 {@link #pinLaunchShortcut} 不同：这个是长按菜单，不需要用户手动固定。
     */
    public static void publishDynamicShortcut(Context context, String instanceName) {
        try {
            ShortcutManager sm = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
            if (sm == null) {
                return;
            }
            Intent intent = new Intent(context, MainActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.putExtra("qcl_auto_launch", true);
            String label = instanceName == null || instanceName.isEmpty()
                    ? context.getString(R.string.widget_title)
                    : instanceName;
            ShortcutInfo info = new ShortcutInfo.Builder(context, ID + "_dyn")
                    .setShortLabel(context.getString(R.string.shortcut_row_title))
                    .setLongLabel(context.getString(R.string.shortcut_row_title) + " · " + label)
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_baseline_launch_black))
                    .setIntent(intent)
                    .build();
            sm.setDynamicShortcuts(Collections.singletonList(info));
        } catch (Throwable t) {
            Log.w(TAG, "发布动态快捷方式失败: " + t);
        }
    }
}
