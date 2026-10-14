package com.qcl.launcher.launcher.uis.plugin;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.launch.RendererCompat;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

import java.io.File;

/**
 * ★ 社区版新增：插件页（底部导航第 4 个 Tab）。
 *
 * <p><b>★★★ 为什么这里没有"插件市场"</b>：
 * 需求书里的插件市场（静态 JSON 索引 / 多源 / 签名校验 / 沙箱 / 权限）本质上是
 * <b>一整套运行时架构</b>，不是能在增量里顺手塞进去的功能。QCL 一贯的原则是
 * 「不做假选项」（README 明确写过：自研渲染器"列表里暂无此项，不做假选项"），
 * 所以本页只呈现两件真实的东西：
 * <ol>
 *   <li><b>真正在生效的"插件"</b> —— 外部渲染器插件。QCL 的渲染器里
 *       {@code builtin=false} 的那几个（目前是 MobileGlues）就是插件形态：
 *       它的 {@code libmobileglues.so} 不进 APK，而是从已安装的插件 APK 的
 *       nativeLibraryDir 加载（{@link RendererCompat#resolveRendererLibDir}）。
 *       这已经是可用的插件能力，值得单独列出来给玩家看。</li>
 *   <li><b>插件运行时的真实进度</b> —— 明确写"尚未启用 + 属于第三阶段"，
 *       而不是摆一个点进去是空的假市场。</li>
 * </ol>
 *
 * <p>等第三阶段的 {@code :PluginRuntime} 模块落地后，本页直接扩成"市场 / 已安装 / 更新 / 源管理"
 * 四个 Tab，无需再新建入口 —— 这是刻意为将来留的挂载点。
 */
public class PluginUI extends BaseUI implements View.OnClickListener {

    private LinearLayout pluginUI;
    private TextView runtimeState;
    private LinearLayout rendererContainer;
    private LinearLayout openDirButton;
    private TextView dirPath;

    public PluginUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.pluginUI = this.activity.findViewById(R.id.ui_plugin);
        // ★★★ 1.10.0：界面排版 —— 插件 页接入
        try {
            com.qcl.launcher.launcher.uis.layout.LayoutEditor.apply(activity, this.pluginUI, "plugin");
            com.qcl.launcher.launcher.uis.layout.LayoutEditor.attach(activity, this.pluginUI, "plugin");
        } catch (Throwable ignored) {
        }

        this.runtimeState = this.activity.findViewById(R.id.plugin_runtime_state);
        this.rendererContainer = this.activity.findViewById(R.id.plugin_renderer_container);
        this.openDirButton = this.activity.findViewById(R.id.plugin_open_dir_button);
        this.dirPath = this.activity.findViewById(R.id.plugin_dir_path);
        if (this.openDirButton != null) {
            this.openDirButton.setOnClickListener(this);
        }
        if (this.dirPath != null) {
            this.dirPath.setText(AppManifest.PLUGIN_DIR);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.activity.showBarTitle(
                this.context.getResources().getString(R.string.nav_plugin),
                canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.pluginUI, this.activity, this.context, true);
        refresh();
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.pluginUI, this.activity, this.context, true);
    }

    /** 刷新页面内容：运行时状态 + 真实生效的外部渲染器插件 */
    private void refresh() {
        renderPlugins();
        if (runtimeState != null) {
            runtimeState.setText(R.string.plugin_runtime_none);
        }
        if (rendererContainer == null) {
            return;
        }
        rendererContainer.removeAllViews();

        String versionPath = null;
        String gameDir = null;
        try {
            versionPath = this.activity.publicGameSetting.currentVersion;
            gameDir = this.activity.launcherSetting.gameFileDirectory;
        } catch (Throwable ignored) {
        }

        int found = 0;
        for (RendererCompat.Info info : RendererCompat.ALL) {
            if (info == null || info.builtin) {
                continue;   // 内置渲染器不是插件，跳过
            }
            String libDir = null;
            try {
                libDir = RendererCompat.resolveRendererLibDir(this.context, info.id, versionPath, gameDir);
            } catch (Throwable ignored) {
            }
            if (libDir != null) {
                found++;
                rendererContainer.addView(buildRendererRow(info, libDir, true));
            }
        }
        // 一个都没检测到也要给出真实的外部渲染器清单（让玩家知道"能装什么"），
        // 但明确标注"未检测到"，不假装已安装。
        for (RendererCompat.Info info : RendererCompat.ALL) {
            if (info == null || info.builtin) {
                continue;
            }
            String libDir = null;
            try {
                libDir = RendererCompat.resolveRendererLibDir(this.context, info.id, versionPath, gameDir);
            } catch (Throwable ignored) {
            }
            if (libDir == null) {
                rendererContainer.addView(buildRendererRow(info, null, false));
            }
        }
        if (found == 0) {
            TextView hint = new TextView(this.context);
            hint.setText(R.string.plugin_renderer_empty);
            hint.setTextSize(10f);
            hint.setTextColor(Color.parseColor("#99000000"));
            hint.setPadding(px(10), px(6), px(10), 0);
            rendererContainer.addView(hint);
        }
    }

    /** 构建一条渲染器插件行：名称 + 支持范围 + 来源目录（或"未检测到"） */
    private View buildRendererRow(RendererCompat.Info info, String libDir, boolean installed) {
        LinearLayout row = new LinearLayout(this.context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.qcl_button_gray);
        row.setPadding(px(10), px(10), px(10), px(10));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = px(6);
        row.setLayoutParams(rowParams);

        TextView title = new TextView(this.context);
        title.setText(info.name + (installed ? " · 已检测到" : " · 未检测到"));
        title.setTextSize(13f);
        title.setTextColor(Color.BLACK);
        row.addView(title);

        TextView range = new TextView(this.context);
        range.setText(info.supportRangeText());
        range.setTextSize(10f);
        range.setTextColor(Color.parseColor("#99000000"));
        row.addView(range);

        TextView src = new TextView(this.context);
        src.setText(installed ? libDir : info.glName);
        src.setTextSize(10f);
        src.setTextColor(Color.parseColor("#99000000"));
        row.addView(src);

        return row;
    }

    /**
     * ★★★ 社区版：把 {@link PluginRegistry} 里的插件渲染成卡片（按分类分组）。
     *
     * <p>加一个新插件只需在 PluginRegistry 加一行 —— 这里不用改。
     * 分类名单独一行，每个插件一张卡片（名字 + 一句话价值说明）。
     */
    private void renderPlugins() {
        try {
            LinearLayout box = this.activity.findViewById(R.id.plugin_list_container);
            if (box == null) {
                return;
            }
            // ★ 先把「移除全部子视图」推到布局之后 —— 避免在布局过程中改树
            box.removeAllViewsInLayout();
            this.cardSeq = 0;

            // ★★★ 社区版：44 个插件一长条，找东西要一直滚 —— 加个搜索框。
            //   只按名字/说明过滤，不改变开关状态（关掉的插件仍能被搜到，方便重新打开）。
            if (this.pluginSearchBox == null) {
                android.widget.EditText et = new android.widget.EditText(this.context);
                et.setHint("搜索插件（名字或说明）");
                et.setSingleLine(true);
                et.setText(this.pluginSearch);
                et.addTextChangedListener(new android.text.TextWatcher() {
                    public void beforeTextChanged(CharSequence s2, int a2, int b2, int c2) { }
                    public void onTextChanged(CharSequence s2, int a2, int b2, int c2) { }
                    public void afterTextChanged(android.text.Editable e2) {
                        pluginSearch = e2 == null ? "" : e2.toString().trim();
                        // 只重建列表，不重建搜索框本身（否则输入法会掉）
                        rebuildListOnly();
                    }
                });
                this.pluginSearchBox = et;
            }
            box.addView(this.pluginSearchBox);

            fillPluginList(box);
        } catch (Throwable t) {
            // 插件列表渲染失败不能把整页带崩
        }
    }

    /**
     * 只重建列表内容（搜索框不动）。
     *
     * <p>★ 为什么不能直接调 renderPlugins：那样会把 EditText 也换掉，
     * 每敲一个字输入法就会掉、光标会跑。这里保留搜索框，只清掉它后面的内容再重填。
     */
    private void rebuildListOnly() {
        try {
            LinearLayout box = this.activity.findViewById(R.id.plugin_list_container);
            if (box == null) {
                return;
            }
            int keep = this.pluginSearchBox == null ? 0 : 1;
            while (box.getChildCount() > keep) {
                box.removeViewAt(box.getChildCount() - 1);
            }
            fillPluginList(box);
        } catch (Throwable ignored) {
        }
    }

    /** 填充插件列表（分组 + 卡片）。搜索框不在这里管。 */
    private void fillPluginList(LinearLayout box) {
        try {
            // ★★★ 按开关状态分成「开启」和「已关闭」两组。
            //   关掉的收进底部「已关闭」区，随时可以再打开 —— 而不是直接消失，
            //   否则用户找不到重新打开的入口。
            java.util.List<PluginSpec> all = PluginRegistry.all();
            java.util.List<PluginSpec> on = new java.util.ArrayList<>();
            java.util.List<PluginSpec> off = new java.util.ArrayList<>();
            String kw = this.pluginSearch == null ? "" : this.pluginSearch.toLowerCase();
            for (PluginSpec sp : all) {
                if (!kw.isEmpty()) {
                    String nm = loc("plugin_" + sp.id + "_name", sp.name).toLowerCase();
                    String ds = loc("plugin_" + sp.id + "_desc", sp.desc).toLowerCase();
                    if (!nm.contains(kw) && !ds.contains(kw)) {
                        continue;   // 搜不到就跳过（开关状态不变）
                    }
                }
                if (PluginSwitch.enabled(sp.id)) {
                    on.add(sp);
                } else {
                    off.add(sp);
                }
            }

            // ---------- 开启的插件（按分类分组） ----------
            String cur = null;
            for (final PluginSpec sp : on) {
                if (!sp.category.equals(cur)) {
                    cur = sp.category;
                    TextView catLabel = PluginUiKit.label(this.context, loc("plugin_cat_" + cur, cur));
                    PluginUiKit.fadeInUp(catLabel, Math.min(cardSeq++, 8) * 28L);
                    box.addView(catLabel);
                }
                box.addView(buildPluginCard(sp, true));
            }

            // ---------- 已关闭的插件（收在底部，随时可再开） ----------
            if (!off.isEmpty()) {
                TextView offLabel = PluginUiKit.label(this.context,
                        "已关闭 " + off.size() + " 个（点开关重新打开）");
                PluginUiKit.fadeInUp(offLabel, 0);
                box.addView(offLabel);
                for (final PluginSpec sp : off) {
                    box.addView(buildPluginCard(sp, false));
                }
                TextView reset = PluginUiKit.row(this.context, "全部打开", null);
                reset.setGravity(android.view.Gravity.CENTER);
                PluginUiKit.pressEffect(reset);
                PluginUiKit.fadeInUp(reset, 0);
                reset.setOnClickListener(v -> {
                    PluginSwitch.enableAll();
                    PluginUiKit.toast(this.context, "已全部打开");
                    reset.post(() -> renderPlugins());
                });
                box.addView(reset);
            }

            if (on.isEmpty()) {
                box.addView(PluginUiKit.sub(this.context,
                        "所有插件都已关闭。在下面的「已关闭」区把开关打开即可恢复。"));
            }
        } catch (Throwable t) {
            // 插件列表渲染失败不能把整页带崩
        }
    }

    /**
     * 生成一张插件卡片：名字 + 说明 + 开关。
     *
     * <p>★ 开关存到 {@link PluginSwitch}（本地文件），重启保持。
     * 点卡片主体执行插件；点开关只改状态并重绘列表。
     */
    /** ★ 搜索关键词（空 = 不过滤）。 */
    private String pluginSearch = "";
    /** 入场错落用的序号（每次重建列表归零）。 */
    private int cardSeq = 0;
    /** 搜索框实例（只建一次，避免每次重绘都换新控件导致输入法掉）。 */
    private android.widget.EditText pluginSearchBox;

    private View buildPluginCard(final PluginSpec sp, boolean on) {
        LinearLayout card = PluginUiKit.card(this.context);

        // 第一行：名字 + 开关
        LinearLayout head = new LinearLayout(this.context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView name = PluginUiKit.text(this.context, loc("plugin_" + sp.id + "_name", sp.name));
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(name);

        android.widget.Switch sw = new android.widget.Switch(this.context);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, checked) -> {
            PluginSwitch.setEnabled(sp.id, checked);
            // ★ 不能在开关回调里直接重绘 —— 此时正处于本卡片所在视图树的布局过程，
            //   removeAllViews + 重新 addView 会抛 IllegalStateException（用户看到的就是闪退）。
            //   post 到下一帧，等本轮布局结束再重建列表。
            sw.post(() -> renderPlugins());
        });
        head.addView(sw);
        card.addView(head);

        // 说明（查多语言，查不到回退原文）
        card.addView(PluginUiKit.sub(this.context, loc("plugin_" + sp.id + "_desc", sp.desc)));

        // 点卡片主体 = 执行插件（关着的点了也会先执行一次，方便试用）
        card.setOnClickListener(v -> {
            try {
                sp.action.run(this.activity);
            } catch (Throwable t) {
                PluginUiKit.toast(this.context, "插件出错：" + t);
            }
        });

        // ★ 按压动效（只动 scale，合成层属性，零布局成本）
        PluginUiKit.pressEffect(card);
        // ★ 入场错落：只给前 8 张加延迟 —— 44 张一起做入场必然掉帧
        PluginUiKit.fadeInUp(card, Math.min(cardSeq++, 8) * 28L);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(8);
        card.setLayoutParams(lp);
        return card;
    }

    /** 按名字查字符串资源；查不到返回 fallback。 */
    private String loc(String name, String fallback) {
        try {
            int id = this.context.getResources().getIdentifier(name, "string",
                    this.context.getPackageName());
            if (id != 0) {
                return this.context.getString(id);
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private int px(int dp) {
        return Math.round(dp * this.context.getResources().getDisplayMetrics().density);
    }

    @Override
    public void onClick(View view) {
        if (view == this.openDirButton) {
            openPluginDir();
        }
    }

    /** 打开插件目录（本地导入插件的落点）—— 设备上没有文件管理器时给一句明确提示，不静默失败 */
    private void openPluginDir() {
        try {
            File dir = new File(AppManifest.PLUGIN_DIR);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(dir), "resource/folder");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.context.startActivity(intent);
        } catch (Throwable t) {
            try {
                File dir = new File(AppManifest.PLUGIN_DIR);
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setDataAndType(Uri.fromFile(dir), "*/*");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.context.startActivity(fallback);
            } catch (Throwable t2) {
                Toast.makeText(this.context, R.string.plugin_open_dir_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
