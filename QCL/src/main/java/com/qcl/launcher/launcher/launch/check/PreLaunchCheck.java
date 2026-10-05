package com.qcl.launcher.launcher.launch.check;

import android.app.ActivityManager;
import android.content.Context;

import com.google.gson.Gson;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.game.Argument;
import com.qcl.launcher.launcher.game.Artifact;
import com.qcl.launcher.launcher.game.RuledArgument;
import com.qcl.launcher.launcher.game.Version;
import com.qcl.launcher.launcher.setting.game.GameLaunchSetting;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.file.FileStringUtils;
import com.qcl.launcher.utils.gson.JsonUtils;
import com.qcl.launcher.utils.platform.Bits;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版新增：实例启动前检查（#21）。
 *
 * <p><b>目标</b>：把「进不去」从<b>崩溃之后</b>提前到<b>启动之前</b> —— 而且提前说的必须是
 * 「具体哪里不对、能不能修」，不是一句「可能有问题」。
 *
 * <p><b>★ 与已有检查的分工</b>：
 * <ul>
 *   <li>{@link CheckLibTask} 已经会**检测并自动下载**缺失的库/资源/jar —— 那部分不用重复做；</li>
 *   <li>本类补的是**它不查的那几项**：Java 运行时是否真的装好、内存是否超了物理内存一半、
 *       版本文件是否齐全、模组数量是否偏多。</li>
 * </ul>
 *
 * <p><b>★ 每项都要能「修」或明确说不能修</b>：能自动修的直接给一个动作（改内存、去装运行环境），
 * 不能修的就如实说清楚原因，不摆一个点了没反应的按钮。
 */
public final class PreLaunchCheck {

    private PreLaunchCheck() {
    }

    /** 可执行的修复动作。 */
    public enum Fix {
        /** 无需修复 */
        NONE,
        /** 把内存分配改成建议值 */
        RAM,
        /** 跳到「安装运行环境」页 */
        RUNTIME,
        /** 打开自动任务页（关掉开机自启之类的建议） */
        NONE_BUT_TIP
    }

    /** 一条检查结果。 */
    public static class Item {
        public String title;
        public boolean ok;
        public String detail;
        public String advice = "";
        public Fix fix = Fix.NONE;
        /** 修复动作需要的一个数值（目前只有内存用得到） */
        public int fixValue;

        public Item(String title, boolean ok, String detail) {
            this.title = title;
            this.ok = ok;
            this.detail = detail;
        }

        public Item advice(String a) {
            this.advice = a;
            return this;
        }

        public Item fix(Fix f) {
            this.fix = f;
            return this;
        }

        public Item value(int v) {
            this.fixValue = v;
            return this;
        }
    }

    /** 检查结果汇总。 */
    public static class Result {
        public final List<Item> items = new ArrayList<Item>();

        /** 是否存在「有问题且能自动修」的项 */
        public boolean hasFixable() {
            for (Item i : items) {
                if (!i.ok && i.fix != Fix.NONE && i.fix != Fix.NONE_BUT_TIP) {
                    return true;
                }
            }
            return false;
        }

        /** 是否存在任何问题（含不能自动修的） */
        public boolean hasProblem() {
            for (Item i : items) {
                if (!i.ok) {
                    return true;
                }
            }
            return false;
        }
    }

    public static Result run(Context context, MainActivity activity, String versionPath) {
        Result r = new Result();
        checkJava(context, activity, versionPath, r);
        checkRam(context, activity, r);
        checkVersionFiles(versionPath, r);
        checkMods(activity, versionPath, r);
        return r;
    }

    // ------------------------------------------------------------------ Java

    /**
     * Java 运行时检查。
     *
     * <p>★ 这里刻意只做「**装没装、版本对不对**」的判断，不重复 {@link CheckJavaTask} 的完整逻辑
     * （那个还会判 32 位 Java 25 之类）。启动前检查要在**一两百毫秒内**给出结论，
     * 不能把整套校验再跑一遍。
     */
    private static void checkJava(Context context, MainActivity activity, String versionPath, Result r) {
        try {
            int expected = 8;
            try {
                String jsonPath = versionPath + "/" + new File(versionPath).getName() + ".json";
                String json = FileStringUtils.getStringFromFile(jsonPath);
                if (json != null && !json.isEmpty()) {
                    Gson gson = JsonUtils.defaultGsonBuilder()
                            .registerTypeAdapter(Artifact.class, new Artifact.Serializer())
                            .registerTypeAdapter(Bits.class, new Bits.Serializer())
                            .registerTypeAdapter(RuledArgument.class, new RuledArgument.Serializer())
                            .registerTypeAdapter(Argument.class, new Argument.Deserializer())
                            .create();
                    Version v = gson.fromJson(json, Version.class);
                    if (v != null) {
                        expected = GameLaunchSetting.requiredJava(v);
                    }
                }
            } catch (Throwable ignored) {
            }

            String runtimeName;
            boolean auto = true;
            try {
                auto = activity.privateGameSetting.javaSetting.autoSelect;
                runtimeName = auto ? GameLaunchSetting.selectJavaRuntime(expected)
                        : activity.privateGameSetting.javaSetting.name;
            } catch (Throwable t) {
                runtimeName = GameLaunchSetting.selectJavaRuntime(expected);
            }
            if (runtimeName == null || runtimeName.isEmpty()) {
                runtimeName = GameLaunchSetting.selectJavaRuntime(expected);
            }
            File runtime = new File(AppManifest.JAVA_DIR, runtimeName);
            boolean releaseOk = new File(runtime, "release").isFile();
            String detail = "需要 Java " + expected + "，选用「" + runtimeName + "」"
                    + (auto ? "（自动）" : "（手动指定）");
            if (!releaseOk) {
                r.items.add(new Item("Java 运行时", false, detail + " —— 该运行时未安装或已损坏")
                        .advice("先到「安装运行环境」页点一次「安装 / 更新」。这一步是离线解压包内自带的 JRE，不需要联网。")
                        .fix(Fix.RUNTIME));
            } else {
                r.items.add(new Item("Java 运行时", true, detail));
            }
        } catch (Throwable t) {
            r.items.add(new Item("Java 运行时", false, "检查失败：" + t)
                    .advice("这本身不一定会让游戏起不来，可以先试着启动；如果进不去再反馈。"));
        }
    }

    // ------------------------------------------------------------------ 内存

    private static void checkRam(Context context, MainActivity activity, Result r) {
        try {
            long totalMb = 0;
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                totalMb = mi.totalMem / 1024 / 1024;
            }
            if (totalMb <= 0 || activity.privateGameSetting == null
                    || activity.privateGameSetting.ramSetting == null) {
                return;
            }
            int maxRam = activity.privateGameSetting.ramSetting.maxRam;
            long safe = totalMb / 2;
            String detail = "本机 " + totalMb + " MB，当前分配 " + maxRam + " MB";
            if (maxRam > safe) {
                r.items.add(new Item("内存分配", false, detail + " —— 超过物理内存的一半")
                        .advice("分配超过一半不会更快，反而容易在进游戏 / 切维度时被系统直接杀掉。"
                                + "建议改成 " + safe + " MB。")
                        .fix(Fix.RAM).value((int) safe));
            } else {
                r.items.add(new Item("内存分配", true, detail));
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ 版本文件

    private static void checkVersionFiles(String versionPath, Result r) {
        try {
            String name = new File(versionPath).getName();
            File json = new File(versionPath, name + ".json");
            File jar = new File(versionPath, name + ".jar");
            boolean jsonOk = json.isFile() && json.length() > 0;
            // ★ jar 不一定在版本目录里（可以有 inheritsFrom），所以缺 jar 只提示、不当错误。
            if (!jsonOk) {
                r.items.add(new Item("版本文件", false, "缺少或为空：" + json.getAbsolutePath())
                        .advice("这个实例的版本描述文件没了，启动一定失败。"
                                + "建议到「版本列表」重新下载一个同名版本，或从备份恢复。"));
            } else if (!jar.isFile()) {
                r.items.add(new Item("版本文件", true,
                        "有版本描述文件；本目录没有同名 jar（继承自父版本时属正常）"));
            } else {
                r.items.add(new Item("版本文件", true, "版本描述文件与 jar 都在"));
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ 模组

    private static void checkMods(MainActivity activity, String versionPath, Result r) {
        try {
            File mods = new File(versionPath, "mods");
            if (!mods.isDirectory()) {
                return;   // 没装模组的实例不用报这一项
            }
            File[] fs = mods.listFiles();
            if (fs == null) {
                return;
            }
            int n = 0;
            for (File f : fs) {
                String ln = f.getName().toLowerCase();
                if (f.isFile() && (ln.endsWith(".jar") || ln.endsWith(".zip") || ln.endsWith(".litemod"))) {
                    n++;
                }
            }
            if (n == 0) {
                return;
            }
            if (n > 150) {
                r.items.add(new Item("模组数量", false, n + " 个模组")
                        .advice("数量偏多，互相冲突和吃内存的概率明显上升。"
                                + "可以用「我的 → 模组体检」看看加载器与版本有没有对不上的。"));
            } else {
                r.items.add(new Item("模组数量", true, n + " 个模组"));
            }
        } catch (Throwable ignored) {
        }
    }
}
