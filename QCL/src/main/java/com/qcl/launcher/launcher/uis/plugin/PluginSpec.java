package com.qcl.launcher.launcher.uis.plugin;

import com.qcl.launcher.launcher.MainActivity;

/**
 * ★★★ 社区版：一个「插件」的描述。
 *
 * <p><b>为什么用描述表而不是各写一个页面</b>：
 * 社区版要加几十项功能，如果每项都走「新建 layout + 在 activity_main 里 include +
 * 在 UIManager 注册 5 处 + 在 SettingUI 注册 4 处」那一套，光是样板代码就压垮维护。
 * 这里改成：**一个插件 = 一条描述 + 一个动作**，界面由 {@link PluginUI} 统一渲染，
 * 交互由 {@link PluginUiKit} 统一提供。
 *
 * <p>加一个新插件的成本因此降到「在 {@link PluginRegistry} 里加一行」。
 *
 * <p>★ 诚实原则：**做不了的插件不往这个表里放**。宁可列表短一点，也不放「点了没反应」的空壳。
 */
public class PluginSpec {

    /** 分类（用于在界面上分组显示）。 */
    public final String category;
    /** 稳定 id，用于记「最近使用」之类的本地状态。 */
    public final String id;
    /** 显示名。 */
    public final String name;
    /** 一句话说明「它解决什么问题」—— 不写实现细节，写用户看得懂的价值。 */
    public final String desc;
    /** 点击后执行的动作。 */
    public final Action action;

    public PluginSpec(String category, String id, String name, String desc, Action action) {
        this.category = category;
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.action = action;
    }

    /** 插件动作。传 MainActivity 而不是 Context —— 大部分动作要用到实例、设置、目录。 */
    public interface Action {
        void run(MainActivity activity);
    }
}
