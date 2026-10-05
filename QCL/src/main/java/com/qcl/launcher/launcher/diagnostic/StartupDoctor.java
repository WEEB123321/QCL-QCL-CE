package com.qcl.launcher.launcher.diagnostic;

import android.app.ActivityManager;
import android.content.Context;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.StartupTrace;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版新增：启动排查向导（诊断页的「自动排查」段）。
 *
 * <p><b>为什么要有它</b>：诊断页已经把「走到哪一步」摆出来了，但那是**给作者看的**原始数据。
 * 玩家真正想要的是「到底哪儿不对、我该怎么办」。这里把最常出问题的六个环节做成逐项自检，
 * 每一项都给出<b>结论 + 一句人话建议</b>，不需要理解任何术语。
 *
 * <p><b>★ 只读</b>：只检查、不修改、不自动修复。自动修复在这里是危险的 ——
 * 改内存、删模组这类动作一旦猜错，代价是玩家的存档或配置。
 *
 * <p><b>★ 结论要诚实</b>：检查不了的就写「无法判断」，不硬给一个「正常」。
 * 假绿比红更害人 —— 玩家会据此排除掉真正的问题源。
 */
public final class StartupDoctor {

    /** 单项检查结果。 */
    public static class Item {
        /** 检查项名称 */
        public String title;
        /** true = 通过；false = 有问题 */
        public boolean ok;
        /** 具体结论（含关键数值） */
        public String detail;
        /** 不通过时的建议；通过时为空 */
        public String advice = "";

        public Item(String title, boolean ok, String detail) {
            this.title = title;
            this.ok = ok;
            this.detail = detail;
        }

        public Item advice(String a) {
            this.advice = a;
            return this;
        }
    }

    private StartupDoctor() {
    }

    /** 跑全部检查。任何单项出错都不会中断其它项。 */
    public static List<Item> run(Context context, MainActivity activity) {
        List<Item> out = new ArrayList<>();
        out.add(checkRuntime(context));
        out.add(checkMemory(context, activity));
        out.add(checkInstance(activity));
        out.add(checkMods(activity));
        out.add(checkLastStartup());
        return out;
    }

    // ------------------------------------------------------------------ 各项

    /** ① 运行环境（Java）是否装好 */
    private static Item checkRuntime(Context context) {
        try {
            String javaDir = AppManifest.JAVA_DIR;
            if (javaDir == null) {
                return new Item("运行环境（Java）", false, "路径未初始化")
                        .advice("回到首页，在「安装运行环境」页点一次「安装 / 更新」。");
            }
            File dir = new File(javaDir);
            File[] children = dir.listFiles();
            if (!dir.isDirectory() || children == null || children.length == 0) {
                return new Item("运行环境（Java）", false, "未安装或为空：" + javaDir)
                        .advice("第一次使用必须先装运行环境：首页 → 安装运行环境 → 安装 / 更新。");
            }
            return new Item("运行环境（Java）", true, "已就绪（" + children.length + " 项）");
        } catch (Throwable t) {
            return new Item("运行环境（Java）", false, "检查失败：" + t);
        }
    }

    /** ② 内存分配是否合理 */
    private static Item checkMemory(Context context, MainActivity activity) {
        try {
            int maxRam = 0;
            try {
                // ★ 注意层级：内存值在 PrivateGameSetting.ramSetting.maxRam，不是 privateGameSetting.maxRam
                maxRam = activity.privateGameSetting.ramSetting.maxRam;
            } catch (Throwable ignored) {
            }
            long totalMb = 0L;
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    totalMb = mi.totalMem / (1024L * 1024L);
                }
            } catch (Throwable ignored) {
            }
            String detail = "已分配 " + maxRam + " MB"
                    + (totalMb > 0 ? "，本机共 " + totalMb + " MB" : "");
            if (totalMb > 0 && maxRam > totalMb / 2) {
                return new Item("内存分配", false, detail)
                        .advice("分配超过本机物理内存的一半，容易在进游戏时被系统杀掉。建议降到 "
                                + (totalMb / 2) + " MB 以下。");
            }
            if (maxRam <= 0) {
                return new Item("内存分配", false, detail)
                        .advice("还没设置内存：我的 → 全局游戏设置 → 分配内存。");
            }
            return new Item("内存分配", true, detail);
        } catch (Throwable t) {
            return new Item("内存分配", false, "检查失败：" + t);
        }
    }

    /** ③ 当前实例是否完整 */
    private static Item checkInstance(MainActivity activity) {
        try {
            String version = null;
            try {
                version = activity.publicGameSetting.currentVersion;
            } catch (Throwable ignored) {
            }
            if (version == null || version.isEmpty()) {
                return new Item("当前实例", false, "未选中任何版本")
                        .advice("先去「版本列表」选一个版本，或下载一个。");
            }
            File dir = new File(version);
            if (!dir.isDirectory()) {
                return new Item("当前实例", false, "目录不存在：" + version)
                        .advice("这个版本可能已被删除，请重新选一个。");
            }
            String name = dir.getName();
            File jar = new File(dir, name + ".jar");
            File json = new File(dir, name + ".json");
            if (!jar.isFile() || !json.isFile()) {
                return new Item("当前实例", false,
                        "缺少 " + (jar.isFile() ? "" : name + ".jar ")
                                + (json.isFile() ? "" : name + ".json"))
                        .advice("版本文件不完整，重新安装该版本即可（已下载的资源不会重下）。");
            }
            return new Item("当前实例", true, name);
        } catch (Throwable t) {
            return new Item("当前实例", false, "检查失败：" + t);
        }
    }

    /** ④ 模组数量与体积（模组过多是「进不去游戏」的常见原因） */
    private static Item checkMods(MainActivity activity) {
        try {
            String version = null;
            try {
                version = activity.publicGameSetting.currentVersion;
            } catch (Throwable ignored) {
            }
            if (version == null || version.isEmpty()) {
                return new Item("模组", true, "无实例，跳过");
            }
            File mods = new File(version, "mods");
            File[] files = mods.listFiles();
            if (files == null || files.length == 0) {
                return new Item("模组", true, "未安装模组");
            }
            int count = 0;
            long size = 0L;
            for (File f : files) {
                if (f.isFile()) {
                    count++;
                    size += f.length();
                }
            }
            String detail = count + " 个，共 " + (size / (1024L * 1024L)) + " MB";
            if (count > 200) {
                return new Item("模组", false, detail)
                        .advice("模组数量偏多，容易互相冲突或吃光内存。建议先只留必需的几个再试。");
            }
            return new Item("模组", true, detail);
        } catch (Throwable t) {
            return new Item("模组", false, "检查失败：" + t);
        }
    }

    /** ⑤ 上次启动是否走完 */
    private static Item checkLastStartup() {
        try {
            boolean incomplete = StartupTrace.lastRunStartupIncomplete();
            String trace = StartupTrace.readAll();
            boolean ok = !incomplete && trace != null && trace.contains("启动成功");
            if (incomplete) {
                return new Item("上次启动", false, "上次初始化没有走完")
                        .advice("看下面「本次启动路标」的最后一行，那里就是它停下的位置。");
            }
            if (trace == null || !trace.contains("启动成功")) {
                return new Item("上次启动", false, "路标里没有「启动成功」")
                        .advice("启动流程没有完整走完，但也没有记录到异常 —— 请把诊断信息发给作者。");
            }
            return new Item("上次启动", true, "完整走完");
        } catch (Throwable t) {
            return new Item("上次启动", false, "检查失败：" + t);
        }
    }
}
