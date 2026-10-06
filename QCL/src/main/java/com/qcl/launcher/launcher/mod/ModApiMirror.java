package com.qcl.launcher.launcher.mod;

import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileOutputStream;

/**
 * ★★★ 社区版：Mod / 整合包 API 镜像重写层。
 *
 * <p><b>为什么需要</b>：整合包与模组下载走的是 CurseForge（api.curseforge.com）和
 * Modrinth（api.modrinth.com）的**官方 API**。这两个域名在中国大陆基本连不上，
 * 所以「整合包下载不了」—— 点击搜索或下载就一直转圈然后失败。
 *
 * <p><b>方案</b>：接入 <a href="https://www.mcimirror.top">MCIM</a> 镜像
 * （为大陆用户镜像 Modrinth 与 CurseForge 的元数据和文件）。
 * 官方文档明确说明：**路径、参数、响应结构与官方完全一致，只需替换域名**：
 * <pre>
 *   api.curseforge.com  →  mod.mcimirror.top/curseforge
 *   api.modrinth.com    →  mod.mcimirror.top/modrinth
 *   cdn.modrinth.com/   →  mod.mcimirror.top/
 *   edge.forgecdn.net/  →  mod.mcimirror.top/
 *   mediafilez.forgecdn.net  →  ★ 不要替换（官方文档明确排除）
 * </pre>
 *
 * <p><b>默认开启</b> —— 因为这套功能的主要用户在大陆，直连本来就会失败。
 * 在插件页的「下载」分类里有关闭开关，境外用户可以关掉走官方源。
 *
 * <p>★ 注意：这只影响 **Mod / 整合包 / 资源包 / 光影** 的 API 与文件下载，
 * **不影响** Minecraft 本体、依赖库、资源文件（那些走 BMCLAPI / 官方源，由
 * {@code DownloadUrlSource} 单独管理，两套互不干扰）。
 *
 * <p>★ 开关存的是**文件**而不是 SharedPreferences —— 因为调用点
 * （CurseForgeRemoteModRepository / ModrinthRemoteModRepository）是纯静态工具类，
 * 手上没有 Context；走文件可以少一层参数传递，也避免在异步线程里碰 SharedPreferences。
 */
public final class ModApiMirror {

    private ModApiMirror() {
    }

    private static final String MIRROR = "https://mod.mcimirror.top";
    private static final String FLAG_FILE = "qcl_mirror_off";

    /** 开关缓存（进程内只读一次盘）。 */
    private static volatile Boolean cachedOff;

    /** 镜像是否开启。默认开启（缺省文件不存在 = 开）。 */
    public static boolean enabled() {
        Boolean off = cachedOff;
        if (off != null) {
            return !off;
        }
        try {
            File f = new File(AppManifest.SETTING_DIR, FLAG_FILE);
            cachedOff = f.isFile();
        } catch (Throwable t) {
            cachedOff = false;
        }
        return !cachedOff;
    }

    public static void setEnabled(boolean on) {
        try {
            File f = new File(AppManifest.SETTING_DIR, FLAG_FILE);
            if (on) {
                if (f.isFile()) {
                    f.delete();
                }
            } else {
                File dir = f.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write('0');
                }
            }
        } catch (Throwable ignored) {
        }
        cachedOff = !on;
    }

    /**
     * 把官方 API / 文件地址改写成镜像地址。
     *
     * <p>★ 所有「可能指向 CurseForge / Modrinth」的地址都应该过这一层 ——
     * 不在表里的地址原样返回，所以对其它下载（Minecraft 本体等）无影响。
     */
    public static String apply(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (!enabled()) {
            return url;
        }
        // ★ 顺序有讲究：先替换带路径前缀的 API 域名，再替换纯 CDN 域名。
        if (url.startsWith("https://api.curseforge.com")) {
            return MIRROR + "/curseforge" + url.substring("https://api.curseforge.com".length());
        }
        if (url.startsWith("https://api.modrinth.com")) {
            return MIRROR + "/modrinth" + url.substring("https://api.modrinth.com".length());
        }
        if (url.startsWith("https://cdn.modrinth.com/")) {
            return MIRROR + "/" + url.substring("https://cdn.modrinth.com/".length());
        }
        if (url.startsWith("https://edge.forgecdn.net/")) {
            return MIRROR + "/" + url.substring("https://edge.forgecdn.net/".length());
        }
        // mediafilez.forgecdn.net 官方明确排除，不动。
        return url;
    }
}
