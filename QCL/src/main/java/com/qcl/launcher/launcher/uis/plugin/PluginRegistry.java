package com.qcl.launcher.launcher.uis.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版：插件注册表。
 *
 * <p>**加一个新插件 = 在这里加一行。** 界面、分组、点击处理都由 {@link PluginUI} 统一负责。
 *
 * <p>★ 诚实原则：**只注册真正能用的**。做不了的（需要服务器 / 拿不到游戏画面 /
 * 要重写世界生成算法）不往这里放 —— 宁可列表短，也不放点了没反应的空壳。
 * 那几项的原因写在项目的交付文档里，不在这里展开。
 */
public final class PluginRegistry {

    private PluginRegistry() {
    }

    /** 分类名（顺序即界面上的显示顺序）。 */
    public static final String CAT_INSTANCE = "实例管理";
    public static final String CAT_MOD = "模组管理";
    public static final String CAT_WORLD = "存档管理";
    public static final String CAT_TOOL = "工具";

    /** 全部插件。 */
    public static List<PluginSpec> all() {
        List<PluginSpec> l = new ArrayList<>();

        // ---------------- 实例管理 ----------------
        l.add(new PluginSpec(CAT_INSTANCE, "instance_icon",
                "实例图标生成器",
                "按版本 / 加载器 / 模组数量自动给个底色和文字，也可以自己填。一键写进实例。",
                PluginActions::instanceIcon));

        l.add(new PluginSpec(CAT_INSTANCE, "instance_diff",
                "实例配置差异对比",
                "选两个实例，比模组 / 资源包 / 光影 / 配置的差异 —— 回答「为什么 A 能进 B 不能」。",
                PluginActions::instanceDiff));

        l.add(new PluginSpec(CAT_INSTANCE, "instance_health",
                "实例健康评分",
                "扫版本文件、内存分配、模组数量、崩溃报告，给出 0–100 分和问题清单。",
                PluginActions::instanceHealth));

        l.add(new PluginSpec(CAT_INSTANCE, "instance_history",
                "实例启动历史",
                "每次启动的时间、时长、按实例的启动次数，以及最近 20 次会话。",
                PluginActions::launchHistory));

        // ---------------- 模组管理 ----------------
        l.add(new PluginSpec(CAT_MOD, "mod_tag",
                "模组标签自动分类",
                "按文件名关键词把模组归成优化 / 建筑 / 科技 / 魔法 / 冒险 等，一眼看清装了什么。",
                PluginActions::modTagging));

        l.add(new PluginSpec(CAT_MOD, "mod_lock",
                "模组锁定列表",
                "锁定不想被更新的模组（🔒 标记），存在本地，按实例分开记。",
                PluginActions::modLock));


        l.add(new PluginSpec(CAT_MOD, "mod_dep",
                "模组依赖树",
                "读每个模组自己声明的依赖，列出「谁依赖谁」并标出缺失的前置。",
                PluginActions2::modDependencyTree));

        l.add(new PluginSpec(CAT_MOD, "mod_downgrade",
                "模组降级工具",
                "把某个模组换成你手动放进 mods 目录的指定版本。★ 替换前自动备份原文件。",
                PluginActions2::modDowngrade));

        l.add(new PluginSpec(CAT_MOD, "changelog",
                "更新日志聚合",
                "把 mods 里各 jar 自带的 CHANGELOG 读出来汇总。",
                PluginActions2::changelogAggregate));

        l.add(new PluginSpec(CAT_TOOL, "pack_preview",
                "资源包预览图",
                "直接看资源包里的方块/物品/界面贴图，不用进游戏。",
                PluginActions2::resourcePackPreview));

        l.add(new PluginSpec(CAT_TOOL, "shader_compat",
                "光影兼容检测",
                "读光影包的 shaders/ 目录和 GLSL 版本号，判断它需要多新的渲染器。",
                PluginActions2::shaderCompat));

        l.add(new PluginSpec(CAT_TOOL, "batch_rename",
                "批量重命名工具",
                "替换 / 前后缀 / 编号，改模组、资源包、光影、存档。★ 先预览再执行。",
                PluginActions2::batchRename));

        l.add(new PluginSpec(CAT_INSTANCE, "perf_bench",
                "启动器性能基准",
                "累计启动次数、总时长、崩溃率、各实例对比。",
                PluginActions2::perfBenchmark));


        l.add(new PluginSpec("下载", "dl_speed",
                "下载源测速",
                "测官方源和 BMCLAPI 镜像能不能连、连得多快，推荐更快的那个。",
                PluginActions3::sourceSpeedTest));

        l.add(new PluginSpec("下载", "dl_autoswitch",
                "下载失败自动换源",
                "某个源失败自动切下一个，可设重试次数。",
                PluginActions3::downloadAutoSwitch));

        l.add(new PluginSpec("下载", "dl_template",
                "下载组合模板",
                "把「这个整合包要下哪几样」存成模板，下次照单下。",
                PluginActions3::downloadTemplate));

        l.add(new PluginSpec("安全与备份", "hash_verify",
                "文件完整性校验",
                "扫实例里的 jar，查 0 字节 / 打不开 / 条目读不出来的损坏文件。只读。",
                PluginActions3::hashVerify));

        l.add(new PluginSpec("安全与备份", "vault",
                "密码保险箱",
                "记服务器密码、房间口令。★ 存在应用私有目录，不是加密存储（界面里写明了）。",
                PluginActions3::passwordVault));

        l.add(new PluginSpec("安全与备份", "action_log",
                "操作日志",
                "记录插件做过的写操作（改名、替换、合并、改色…），可回溯。",
                PluginActions3::actionLog));

        l.add(new PluginSpec("资源", "pack_merge",
                "资源包合并",
                "把两个资源包 zip 合成一个，同名文件以上层为准。不删原包。",
                PluginActions3::packMerge));

        l.add(new PluginSpec(CAT_TOOL, "theme_editor",
                "主题编辑器",
                "一键切换启动器主色（8 种），改完立刻生效。",
                PluginActions3::themeEditor));

        l.add(new PluginSpec(CAT_TOOL, "server_ping",
                "服务器延迟预检",
                "对一批服务器地址测 TCP 连接延迟，进服前先知道哪个能连。",
                PluginActions3::serverPing));

        l.add(new PluginSpec(CAT_TOOL, "glossary",
                "本地化词库",
                "维护「英文 → 统一译名」对照表，做汉化/写教程时用词统一。",
                PluginActions3::glossary));

        l.add(new PluginSpec(CAT_TOOL, "tutorials",
                "教程库",
                "6 篇本地教程：装版本、装模组、联机、开服、做整合包、排查起不来。全离线。",
                PluginActions3::tutorials));

        l.add(new PluginSpec(CAT_TOOL, "server_wizard",
                "一键开服向导",
                "生成 server.properties 和启动脚本。★ 手机上开服性能差，更适合生成配置给电脑用。",
                PluginActions3::serverWizard));


        l.add(new PluginSpec(CAT_INSTANCE, "instance_import",
                "实例导入向导",
                "从 HMCL / PCL2 / 官方启动器 / MultiMC 的目录导入实例，只复制不动原文件。",
                PluginActions4::instanceImport));

        l.add(new PluginSpec(CAT_INSTANCE, "launch_splash",
                "实例启动画面",
                "启动游戏时盖一层纯色 / 进度条画面，不用盯着黑屏。",
                PluginActions4::launchSplash));

        l.add(new PluginSpec(CAT_INSTANCE, "launch_queue",
                "启动队列",
                "把实例排个顺序记下来，依次启动时照单来。",
                PluginActions4::launchQueue));

        l.add(new PluginSpec(CAT_MOD, "mod_update",
                "模组更新预检",
                "本地预检：找出重复安装、已锁定的模组。★ 查新版本要联网，本项目不做。",
                PluginActions4::modUpdateCheck));

        l.add(new PluginSpec("资源", "shader_preset",
                "光影预设管理",
                "列出光影包并记住你用哪个。★ 真正启用要在游戏内选。",
                PluginActions4::shaderPreset));

        l.add(new PluginSpec("Java", "java_match",
                "Java 自动匹配",
                "读版本 json 的 javaVersion，比对已装的运行时，缺了会告诉你。",
                PluginActions4::javaMatch));

        l.add(new PluginSpec("Java", "java_args",
                "Java 参数预设",
                "低配 / 流畅 / 大内存 / 调试 四套 JVM 参数，一键记住。",
                PluginActions4::javaArgsPreset));

        l.add(new PluginSpec(CAT_WORLD, "world_map",
                "存档地图预览",
                "读 region 文件的区块表，画出**已探索范围**（每格 = 1 个 region）。",
                PluginActions4::worldMapPreview));

        l.add(new PluginSpec(CAT_WORLD, "world_guard",
                "存档编辑警告",
                "动存档前的检查清单 + 各存档体积一览。",
                PluginActions4::worldEditGuard));

        l.add(new PluginSpec("安全与备份", "whitelist",
                "服务器白名单管理",
                "查看 whitelist / ops / banned 四个文件的内容。",
                PluginActions4::serverWhitelist));

        l.add(new PluginSpec(CAT_TOOL, "wiki",
                "本地百科",
                "15 条原版速查：钻石、下界合金、末影珍珠、村民交易、刷怪塔…全离线。",
                PluginActions4::wiki));

        l.add(new PluginSpec("安全与备份", "migration",
                "数据迁移向导",
                "把设置 / 统计 / 笔记 / 词库打成一个 zip，换手机时带走。",
                PluginActions4::dataMigration));

        l.add(new PluginSpec(CAT_WORLD, "gamerule",
                "游戏规则预设",
                "生存 / 创造 / PVP / 红石 / 建筑 / 空岛，生成数据包写进存档。",
                PluginActions4::gamerulePreset));

        // ---------------- 存档管理 ----------------
        l.add(new PluginSpec(CAT_WORLD, "world_version",
                "存档版本转换提醒",
                "比对每个存档的 MC 版本和当前实例版本，不一致时提醒（升级不可逆，建议先备份）。",
                PluginActions::worldVersionCheck));

        l.add(new PluginSpec(CAT_WORLD, "world_players",
                "存档玩家列表",
                "列出存档里的玩家数据文件：UUID、最后修改时间、大小。只读。",
                PluginActions::worldPlayers));

        // ---------------- 工具 ----------------
        l.add(new PluginSpec(CAT_TOOL, "notes",
                "快速笔记",
                "记服务器 IP / 坐标 / 待办，支持置顶、删除。存在本地，不上传。",
                PluginActions::notes));

        l.add(new PluginSpec(CAT_TOOL, "factory_reset",
                "一键恢复出厂",
                "重置启动器设置。★ 不删实例、不删存档、不删账号，重置前会二次确认。",
                PluginActions::factoryReset));


        // ---------------- 语言 ----------------
        l.add(new PluginSpec("语言 / Language", "locale",
                "语言 / Language",
                "切换启动器界面语言（17 种，含繁體中文·日本語·한국어·Français·Deutsch·"
                        + "Español·Italiano·Русский·العربية·ไทย·Tiếng Việt·Bahasa Indonesia·Türkçe·Polski）。",
                PluginLocale::open));

        return l;
    }

    /** 按分类取（保持 {@link #all()} 的顺序）。 */
    public static List<String> categories(List<PluginSpec> list) {
        List<String> out = new ArrayList<>();
        for (PluginSpec s : list) {
            if (!out.contains(s.category)) {
                out.add(s.category);
            }
        }
        return out;
    }
}
