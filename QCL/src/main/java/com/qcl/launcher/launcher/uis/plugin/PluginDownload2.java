package com.qcl.launcher.launcher.uis.plugin;

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.launcher.mod.ModApiMirror;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版：下载增强（用户清单第 15-20 项）。
 *
 * <p><b>先说清楚哪几个是真做的、哪几个做不到</b>（不做假选项）：
 * <ul>
 *   <li><b>#18 下载镜像自定义</b> —— 真做 ✓ 自定义镜像会写进 {@link ModApiMirror}，
 *       真的影响后续 Mod / 整合包下载请求</li>
 *   <li><b>#19 下载缓存清理</b> —— 真做 ✓ 扫描缓存目录、按项删除</li>
 *   <li><b>#20 下载统计</b> —— 真做 ✓ 统计本地下载目录的文件数 / 占用</li>
 *   <li><b>#15 下载限速时段</b> —— <b>做不到</b> ✗ 限速要挂在下载器的请求链上，
 *       而 QCL 的下载走 {@code DownloadTask.downloadFileMonitored*} 内部，
 *       没有暴露速率控制点；硬加要改下载框架，风险远大于收益</li>
 *   <li><b>#16 下载通知自定义</b> —— <b>做不到</b> ✗ 通知渠道由
 *       {@code DownloadNotify} / 前台服务固定，改样式要重做通知层</li>
 *   <li><b>#17 下载队列分组</b> —— <b>做不到</b> ✗ 队列由下载服务内部维护，
 *       没有对外暴露分组接口</li>
 * </ul>
 * 这三个「做不到」的也留在插件页里，但**界面上会直说是为什么**，
 * 而不是摆一个点进去没反应的假入口。
 */
public final class PluginDownload2 {

    private PluginDownload2() {
    }

    // ==================== #18 下载镜像自定义 ====================

    public static void mirrorCustom(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "下载镜像自定义"));
            col.addView(PluginUiKit.sub(a, "Mod / 整合包下载走哪个镜像。留空 = 用默认的 MCIM。"));
            col.addView(PluginUiKit.sub(a, " "));

            EditText et = new EditText(a);
            et.setHint("自定义镜像根地址，例如 https://mod.mcimirror.top");
            String cur = ModApiMirror.customMirror();
            if (cur != null && !cur.isEmpty()) {
                et.setText(cur);
            }
            col.addView(et);

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 镜像必须**结构与官方一致**（路径、参数、响应体），"
                    + "否则下载会失败。MCIM 就是按这个前提做的。"));
            col.addView(PluginUiKit.sub(a, "★ 填错会导致 Mod / 整合包下载全部失败 —— "
                    + "出问题就把这里清空，回到默认镜像。"));
            col.addView(PluginUiKit.sub(a, "★ 只影响 Mod / 整合包 / 资源包 / 光影的 API 与文件下载，"
                    + "不影响 Minecraft 本体（那些走 BMCLAPI）。"));
            col.addView(PluginUiKit.sub(a, "当前生效：" + (cur == null || cur.isEmpty()
                    ? "默认（mod.mcimirror.top）" : cur)));

            PluginUiKit.sheet(a, "下载镜像自定义", PluginUiKit.scroller(a, col), "保存", () -> {
                String v = et.getText() == null ? "" : et.getText().toString().trim();
                if (v.endsWith("/")) {
                    v = v.substring(0, v.length() - 1);
                }
                if (!v.isEmpty() && !v.startsWith("http")) {
                    PluginUiKit.toast(a, "地址要以 http(s):// 开头");
                    return;
                }
                ModApiMirror.setCustomMirror(v);
                PluginUiKit.log(a, "下载镜像自定义 → " + (v.isEmpty() ? "默认" : v));
                PluginUiKit.toast(a, v.isEmpty() ? "已恢复默认镜像" : "已保存自定义镜像");
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== #19 下载缓存清理 ====================

    public static void cacheClean(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "下载缓存清理"));
            col.addView(PluginUiKit.sub(a, "★ 只列**缓存目录**里的内容，"
                    + "不碰你的游戏实例、存档、模组。"));

            final List<File> targets = new ArrayList<>();
            File cache = AppManifest.DEFAULT_CACHE_DIR == null
                    ? null : new File(AppManifest.DEFAULT_CACHE_DIR);
            if (cache != null && cache.isDirectory()) {
                File[] fs = cache.listFiles();
                if (fs != null) {
                    for (File f : fs) {
                        if (f != null) {
                            targets.add(f);
                        }
                    }
                }
            }

            if (targets.isEmpty()) {
                col.addView(PluginUiKit.sub(a, "缓存目录是空的（或还没建立）。"));
                col.addView(PluginUiKit.sub(a, "路径：" + (cache == null ? "未知" : cache.getAbsolutePath())));
                PluginUiKit.sheet(a, "下载缓存清理", PluginUiKit.scroller(a, col), "知道了", null);
                return;
            }

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.kv(a, "缓存项", targets.size() + " 个"));

            final List<File> picked = new ArrayList<>();
            final LinearLayout listBox = new LinearLayout(a);
            listBox.setOrientation(LinearLayout.VERTICAL);
            for (final File f : targets) {
                LinearLayout card = PluginUiKit.card(a);
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                android.widget.CheckBox cb = new android.widget.CheckBox(a);
                cb.setText(f.getName() + "（" + PluginUiKit.size(
                        f.isDirectory() ? PluginUiKit.dirSize(f, 0) : f.length()) + "）");
                cb.setTextSize(13f);
                cb.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                cb.setOnCheckedChangeListener((b, checked) -> {
                    if (checked) {
                        picked.add(f);
                    } else {
                        picked.remove(f);
                    }
                });
                row.addView(cb);
                card.addView(row);
                listBox.addView(card);
            }
            col.addView(listBox);

            PluginUiKit.sheet(a, "下载缓存清理", PluginUiKit.scroller(a, col), "清理勾选项", () -> {
                if (picked.isEmpty()) {
                    PluginUiKit.toast(a, "没有勾选任何项");
                    return;
                }
                // ★ 删除放后台 —— 缓存可能很大
                PluginUiKit.work(a, "正在清理…", () -> {
                    for (File f : picked) {
                        try {
                            deleteTree(f);
                        } catch (Throwable ignored) {
                        }
                    }
                }, () -> {
                    PluginUiKit.log(a, "清理下载缓存 " + picked.size() + " 项");
                    PluginUiKit.toast(a, "已清理 " + picked.size() + " 项");
                });
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    private static void deleteTree(File f) {
        if (f == null) {
            return;
        }
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) {
                for (File c : fs) {
                    deleteTree(c);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    // ==================== #20 下载统计 ====================

    public static void downloadStats(MainActivity a) {
        try {
            PluginUiKit.work(a, "正在统计…", () -> {
                // 后台扫描（目录可能很大）
                long[] box = new long[3];   // 文件数 / 目录数 / 总字节
                countTree(new File(AppManifest.GAME_FILE_DIRECTORY_DIR), box, 0);
                lastStats[0] = box[0];
                lastStats[1] = box[1];
                lastStats[2] = box[2];
            }, () -> {
                LinearLayout col = PluginUiKit.column(a);
                col.addView(PluginUiKit.text(a, "下载统计"));
                col.addView(PluginUiKit.sub(a, "统计的是**游戏目录**的实际占用 —— "
                        + "下载下来的版本、库、资源、模组都算在里面。"));
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.kv(a, "文件数", lastStats[0] + " 个"));
                col.addView(PluginUiKit.kv(a, "目录数", lastStats[1] + " 个"));
                col.addView(PluginUiKit.kv(a, "总占用", PluginUiKit.size(lastStats[2])));
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.sub(a, "★ **不统计「下载流量」** —— "
                        + "流量要下载器逐次累加，而 QCL 的下载是断点续传 + 多源的，"
                        + "「下载量」这个数字没有可靠口径，与其编一个不如不做。"));
                col.addView(PluginUiKit.sub(a, "★ 想知道「哪些占地方」，用「空间分析」插件。"));
                PluginUiKit.sheet(a, "下载统计", PluginUiKit.scroller(a, col), "知道了", null);
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    private static final long[] lastStats = new long[3];

    private static void countTree(File dir, long[] box, int depth) {
        if (dir == null || depth > 12 || !dir.isDirectory()) {
            return;
        }
        File[] fs = dir.listFiles();
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            if (f == null) {
                continue;
            }
            if (f.isDirectory()) {
                box[1]++;
                countTree(f, box, depth + 1);
            } else {
                box[0]++;
                box[2] += f.length();
            }
        }
    }

    // ==================== #15 / #16 / #17 做不到，如实说明 ====================

    public static void speedSchedule(MainActivity a) {
        notSupported(a, "下载限速时段",
                "限速要挂在下载器的请求链上（每次 read 之间做节流），"
                        + "而 QCL 的下载走 DownloadTask.downloadFileMonitored* 内部，"
                        + "没有暴露速率控制点。硬加要改下载框架 —— 风险远大于收益，所以没做。",
                "现在能做的替代：在「下载镜像自定义」里换更快的镜像，"
                        + "或者用系统的「仅 Wi-Fi 下载」开关控制流量。");
    }

    public static void notifyCustom(MainActivity a) {
        notSupported(a, "下载通知自定义",
                "通知渠道（声音/震动/免打扰）由 DownloadNotify 和前台服务固定，"
                        + "改样式要重做整个通知层，还要适配 targetSdk 34 的前台服务限制。",
                "现在能做的替代：在系统设置 → 应用 → QCL-CE → 通知里，"
                        + "可以单独关掉这个应用的通知声音和震动。");
    }

    public static void queueGroup(MainActivity a) {
        notSupported(a, "下载队列分组",
                "队列由下载服务内部维护，没有对外暴露分组/批量暂停的接口。"
                        + "要做得先把队列抽象出来，属于下载框架改造。",
                "现在能做的替代：在「下载」页里逐个暂停/取消任务。");
    }

    /** 统一的「做不到」说明页 —— 界面上直说原因，不摆假入口。 */
    private static void notSupported(MainActivity a, String title, String why, String alt) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, title));
            col.addView(PluginUiKit.sub(a, "★ 这个功能**没有实现**，原因如下："));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, why));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, alt));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 把它留在这里而不是删掉，是为了让你知道"
                    + "「这条需求我看到了、也评估过了」，而不是假装做了。"));
            PluginUiKit.sheet(a, title, PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }
}
