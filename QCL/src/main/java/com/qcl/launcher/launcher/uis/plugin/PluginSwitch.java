package com.qcl.launcher.launcher.uis.plugin;

import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ★★★ 社区版：插件的开关状态（可开可关，持久化）。
 *
 * <p><b>为什么单独一个类</b>：42 个插件谁开谁关要**记到盘上**，重启后保持。
 * 存的是**文件**而不是 SharedPreferences —— 和 {@code ModApiMirror} 一个理由：
 * 读取点可能在任意线程，走文件少一层 Context 传递。
 *
 * <p><b>格式</b>：{@code AppManifest.SETTING_DIR/qcl_plugin_switch.txt}，
 * 一行一个 **已关闭** 的插件 id。**只存关闭的，不存开启的** ——
 * 这样新增插件默认就是开的，不用改这个文件。
 */
public final class PluginSwitch {

    private PluginSwitch() {
    }

    private static final String FILE = "qcl_plugin_switch.txt";
    private static volatile List<String> cachedOff;

    private static File file() {
        return new File(AppManifest.SETTING_DIR, FILE);
    }

    /** 读出全部已关闭的插件 id。 */
    public static List<String> disabledIds() {
        List<String> cached = cachedOff;
        if (cached != null) {
            return cached;
        }
        List<String> out = new ArrayList<>();
        try {
            String s = PluginUiKit.readText(file());
            if (s != null) {
                for (String line : s.split("\n")) {
                    String t = line.trim();
                    if (!t.isEmpty()) {
                        out.add(t);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        cachedOff = out;
        return out;
    }

    /** 某个插件是否开启（不在关闭名单里就是开）。 */
    public static boolean enabled(String id) {
        return !disabledIds().contains(id);
    }

    /** 开 / 关某个插件。 */
    public static void setEnabled(String id, boolean on) {
        List<String> off = new ArrayList<>(disabledIds());
        if (on) {
            off.remove(id);
        } else if (!off.contains(id)) {
            off.add(id);
        }
        save(off);
    }

    /** 全部打开（一键恢复）。 */
    public static void enableAll() {
        save(new ArrayList<>());
    }

    private static void save(List<String> off) {
        try {
            File f = file();
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            StringBuilder sb = new StringBuilder();
            Collections.sort(off);
            for (String id : off) {
                sb.append(id).append('\n');
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(sb.toString().getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
        cachedOff = off;
    }
}
