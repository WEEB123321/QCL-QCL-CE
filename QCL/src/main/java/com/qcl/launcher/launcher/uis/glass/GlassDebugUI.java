package com.qcl.launcher.launcher.uis.glass;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.glass.GlassApplier;
import com.qcl.launcher.launcher.glass.GlassBackdrop;
import com.qcl.launcher.launcher.glass.GlassShader;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

/**
 * ★★★ v3.0.4：液态玻璃设置 / 调试页（左侧导航栏第 6 个入口，开启玻璃后才出现）。
 *
 * <h3>为什么单独做一页，而不是继续塞在外观设置里</h3>
 * 玻璃有 5 个可调量（开关 / 覆盖范围 / 带宽 / 位移 / 深度），外加 4 项只读状态。
 * 外观设置页本来就有主题、排版、启动器更新等一大堆行，玻璃挤在里面
 * <b>既看不清也调不准</b> —— 用户想「再强一点」的时候，得先在一堆无关选项里
 * 找到那三行，还不知道哪一行管哪件事。
 *
 * <h3>★ 每一行都配一句大白话，这是页面的核心要求</h3>
 * 历史上这项目的玻璃参数被反复调错，**一半原因是术语和实际效果对不上**：
 * <ul>
 *   <li>用户说「背景扭曲加大」，代码里叫 {@code refrGain}；</li>
 *   <li>用户说「那一圈太宽」，代码里叫 {@code bandDp}；</li>
 *   <li>用户说「有些手机不行」，实际是位移按面板高度定标（同一台手机差 15 倍）。</li>
 * </ul>
 * 所以这里的标题一律用**效果**命名（「折射带宽度」而不是 band），
 * 小字一律解释**后果**（拧小了会怎样、拧大了会怎样），不解释实现。
 *
 * <h3>★ 「位移/带宽比」为什么单独亮出来</h3>
 * 它是「全手机适配」的唯一可验证判据。v4.2 之前这个比值会随面板大小飘
 * （0.55× ~ 10.7×），现在恒定。把数字摆在用户眼前，他不需要相信我的话，
 * 自己看一眼就能确认这台手机也是对的。
 */
public class GlassDebugUI extends BaseUI implements View.OnClickListener {

    private android.view.View glassDebugUI;

    private SwitchCompat enableSwitch;
    private TextView scopeLight;
    private TextView scopeFull;
    private TextView scopeDesc;

    private SeekBar bandSeek;
    private TextView bandValue;

    private SeekBar gainSeek;
    private TextView gainValue;
    private TextView gainRatio;

    private TextView statusRender;
    private TextView statusMounted;
    private TextView statusBlur;
    private TextView statusBgver;

    private TextView actionRescan;
    private TextView actionCopy;
    private TextView actionReset;

    public GlassDebugUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.glassDebugUI = this.activity.findViewById(R.id.ui_glass_debug);
        this.enableSwitch = (SwitchCompat) this.activity.findViewById(R.id.glass_debug_enable);
        this.scopeLight = (TextView) this.activity.findViewById(R.id.glass_debug_scope_light);
        this.scopeFull = (TextView) this.activity.findViewById(R.id.glass_debug_scope_full);
        this.scopeDesc = (TextView) this.activity.findViewById(R.id.glass_debug_scope_desc);
        this.bandSeek = (SeekBar) this.activity.findViewById(R.id.glass_debug_band_seek);
        this.bandValue = (TextView) this.activity.findViewById(R.id.glass_debug_band_value);
        this.gainSeek = (SeekBar) this.activity.findViewById(R.id.glass_debug_gain_seek);
        this.gainValue = (TextView) this.activity.findViewById(R.id.glass_debug_gain_value);
        this.gainRatio = (TextView) this.activity.findViewById(R.id.glass_debug_gain_ratio);
        this.statusRender = (TextView) this.activity.findViewById(R.id.glass_debug_status_render);
        this.statusMounted = (TextView) this.activity.findViewById(R.id.glass_debug_status_mounted);
        this.statusBlur = (TextView) this.activity.findViewById(R.id.glass_debug_status_blur);
        this.statusBgver = (TextView) this.activity.findViewById(R.id.glass_debug_status_bgver);
        this.actionRescan = (TextView) this.activity.findViewById(R.id.glass_debug_rescan);
        this.actionCopy = (TextView) this.activity.findViewById(R.id.glass_debug_copy);
        this.actionReset = (TextView) this.activity.findViewById(R.id.glass_debug_reset);

        bind(this.scopeLight);
        bind(this.scopeFull);
        bind(this.actionRescan);
        bind(this.actionCopy);
        bind(this.actionReset);

        if (this.enableSwitch != null) {
            this.enableSwitch.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            GlassDebugUI.this.onEnableChanged(isChecked);
                        }
                    });
        }
        if (this.bandSeek != null) {
            this.bandSeek.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar bar, int p, boolean fromUser) {
                            // ★★★ 拖动时**只改文字**，不应用。
                            //   应用一次 = detachAll + refreshBackdrop + 整棵树重扫，
                            //   那是几百个 View 的遍历。每个像素都应用一遍，
                            //   就是这个项目反复出现的「性能很差」的典型来源。
                            //   （外观设置页的强度滑杆同样是松手才应用，照它做。）
                            GlassDebugUI.this.showBandText(p);
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar bar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar bar) {
                            GlassDebugUI.this.applyBand(bar.getProgress());
                        }
                    });
        }
        if (this.gainSeek != null) {
            this.gainSeek.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar bar, int p, boolean fromUser) {
                            GlassDebugUI.this.showGainText(p);
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar bar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar bar) {
                            GlassDebugUI.this.applyGain(bar.getProgress());
                        }
                    });
        }
    }

    // ───────────────────────── 应用（松手才做） ─────────────────────────

    /**
     * ★ 落一个设置项的三步，顺序不能变：
     * <ol>
     *   <li>写进 {@code launcherSetting}（内存对象）；</li>
     *   <li>立刻存盘 —— 不存的话改完杀掉进程就丢了，而用户完全不知道；</li>
     *   <li>调 Activity 的 setter 让它生效（那里负责 detachAll + 重扫）。</li>
     * </ol>
     * ★ 强度/增益用 {@code Math.max(1, p)}：0 是「未设置」的哨兵值，
     *   真存 0 下次读回来会被当成默认值，用户会看到「调没了又弹回去」。
     */
    private void persist(String what, int value) {
        try {
            if ("mode".equals(what)) {
                this.activity.launcherSetting.liquidGlassMode = value;
            } else if ("scope".equals(what)) {
                this.activity.launcherSetting.liquidGlassScope = value;
            } else if ("strength".equals(what)) {
                this.activity.launcherSetting.liquidGlassStrength = Math.max(1, value);
            } else if ("gain".equals(what)) {
                this.activity.launcherSetting.liquidGlassGain = Math.max(1, value);
            }
            com.qcl.launcher.utils.gson.GsonUtils.saveLauncherSetting(
                    this.activity.launcherSetting,
                    com.qcl.launcher.manifest.AppManifest.SETTING_DIR
                            + "/launcher_setting.json");
        } catch (Throwable ignored) {
            // 存盘失败不该让设置页崩；至少内存里的值已经改了，本次会话仍然生效
        }
    }

    private void applyBand(int p) {
        persist("strength", p);
        this.activity.setLiquidGlassStrength(p);
        refreshValues();
    }

    private void applyGain(int p) {
        persist("gain", p);
        this.activity.setLiquidGlassGain(p);
        refreshValues();
    }

    private void showBandText(int p) {
        if (this.bandValue != null) {
            this.bandValue.setText(this.context.getString(
                    R.string.glass_debug_band_value, bandDpFor(p)));
        }
    }

    private void showGainText(int p) {
        if (this.gainValue != null) {
            this.gainValue.setText(this.context.getString(R.string.glass_debug_gain_value,
                    p, String.format("%.2f", p / (float) GlassApplier.GAIN_PERCENT_DIV)));
        }
    }

    /** 百分比 → 带宽 dp。与 {@code GlassApplier.bandDp()} 同一套区间，只为显示。 */
    private int bandDpFor(int p) {
        return Math.round(14f + 16f * (Math.max(0, Math.min(100, p)) / 100f));
    }

    private void bind(View v) {
        if (v != null) {
            v.setOnClickListener(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.activity.showBarTitle(
                this.context.getResources().getString(R.string.glass_debug_title),
                canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.glassDebugUI, this.activity, this.context, true);
        refreshAll();
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.glassDebugUI, this.activity, this.context, true);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.glass_debug_scope_light || id == R.id.glass_debug_scope_full) {
            int scope = (id == R.id.glass_debug_scope_full)
                    ? com.qcl.launcher.launcher.glass.GlassApplier.MODE_FULL
                    : com.qcl.launcher.launcher.glass.GlassApplier.MODE_LIGHT;
            persist("scope", scope);
            this.activity.setLiquidGlassScope(scope);
            refreshAll();
        } else if (id == R.id.glass_debug_rescan) {
            this.activity.rescanLiquidGlass();
            toast(R.string.glass_debug_rescan_done);
        } else if (id == R.id.glass_debug_copy) {
            copyDiagnostics();
        } else if (id == R.id.glass_debug_reset) {
            resetAll();
        }
    }

    // ───────────────────────── 刷新 ─────────────────────────

    /** 进页面时刷一次：把设置值反映到控件上。 */
    private void refreshAll() {
        refreshValues();
        refreshScope();
        refreshStatus();
    }

    /** 只刷「当前值」文字 —— 拖动滑杆时每帧都走，必须便宜。 */
    private void refreshValues() {
        int strength = GlassApplier.getStrength();
        int gainPct = GlassApplier.getGainPercent();
        float bandDp = GlassApplier.bandDpPublic();
        float gain = GlassApplier.refrGain();

        if (this.bandSeek != null && !this.bandSeek.isPressed()) {
            this.bandSeek.setProgress(strength);
        }
        if (this.gainSeek != null && !this.gainSeek.isPressed()) {
            this.gainSeek.setProgress(gainPct);
        }
        if (this.bandValue != null) {
            this.bandValue.setText(this.context.getString(
                    R.string.glass_debug_band_value, Math.round(bandDp)));
        }
        if (this.gainValue != null) {
            this.gainValue.setText(this.context.getString(R.string.glass_debug_gain_value,
                    gainPct, String.format("%.2f", gain)));
        }
        if (this.gainRatio != null) {
            this.gainRatio.setText(this.context.getString(
                    R.string.glass_debug_gain_ratio,
                    String.format("%.2f", GlassApplier.displacementRatio())));
        }
    }

    private void refreshScope() {
        boolean full = GlassApplier.getMode()
                == com.qcl.launcher.launcher.glass.GlassApplier.MODE_FULL;
        markChoice(this.scopeLight, !full);
        markChoice(this.scopeFull, full);
        if (this.scopeDesc != null) {
            this.scopeDesc.setText(this.context.getString(full
                    ? R.string.glass_debug_scope_full_desc
                    : R.string.glass_debug_scope_light_desc));
        }
    }

    /** 选中态：选项背景 selector 认 selected。 */
    private void markChoice(TextView tv, boolean selected) {
        if (tv != null) {
            tv.setSelected(selected);
            tv.setAlpha(selected ? 1f : 0.55f);
        }
    }

    private void refreshStatus() {
        if (this.enableSwitch != null && !this.enableSwitch.isPressed()) {
            this.enableSwitch.setChecked(GlassApplier.isEnabled());
        }
        if (this.statusRender != null) {
            this.statusRender.setText(this.context.getString(
                    R.string.glass_debug_status_render) + "：" + GlassShader.pathDescription());
        }
        if (this.statusMounted != null) {
            this.statusMounted.setText(this.context.getString(
                    R.string.glass_debug_status_mounted) + "：" + GlassApplier.appliedCount());
        }
        if (this.statusBlur != null) {
            boolean ok = GlassBackdrop.blurred() != null
                    && GlassBackdrop.blurred() != GlassBackdrop.backdrop();
            this.statusBlur.setText(this.context.getString(R.string.glass_debug_status_blur)
                    + "：" + (ok ? "就绪" : "缺失（已退回原图）"));
        }
        if (this.statusBgver != null) {
            this.statusBgver.setText(this.context.getString(R.string.glass_debug_status_bgver)
                    + "：" + GlassBackdrop.version());
        }
    }

    // ───────────────────────── 动作 ─────────────────────────

    private void onEnableChanged(boolean on) {
        persist("mode", on ? 1 : 0);
        this.activity.setLiquidGlassMode(on ? 1 : 0);
        // ★ 开关会决定左侧导航栏那个「玻璃调试」入口显不显，必须同步
        if (this.activity.bottomNav != null) {
            this.activity.bottomNav.syncGlassTab();
        }
        refreshStatus();
    }

    private void resetAll() {
        persist("mode", 1);
        persist("scope", com.qcl.launcher.launcher.glass.GlassApplier.MODE_LIGHT);
        persist("strength", 50);
        persist("gain", GlassApplier.GAIN_PERCENT_DEFAULT);
        this.activity.setLiquidGlassMode(1);
        this.activity.setLiquidGlassScope(
                com.qcl.launcher.launcher.glass.GlassApplier.MODE_LIGHT);
        this.activity.setLiquidGlassStrength(50);
        this.activity.setLiquidGlassGain(GlassApplier.GAIN_PERCENT_DEFAULT);
        if (this.activity.bottomNav != null) {
            this.activity.bottomNav.syncGlassTab();
        }
        refreshAll();
        toast(R.string.glass_debug_reset_done);
    }

    /**
     * 把状态拼成一段可粘贴的文字。
     *
     * <p>★ 为什么必须做到「一键复制」：让用户去翻
     * {@code Android/media/<pkg>/} 找日志文件的执行率极低，截图才是有效反馈路径。
     * 而这个页面上要的信息本来就在眼前，拼一段文字比让他找文件容易得多。
     */
    private void copyDiagnostics() {
        try {
            StringBuilder sb = new StringBuilder(512);
            sb.append("QCL-CE 液态玻璃诊断\n");
            sb.append("模式：").append(GlassApplier.isEnabled()
                    ? (GlassApplier.getMode() == GlassApplier.MODE_FULL ? "标准" : "轻量") : "关闭")
                    .append('\n');
            sb.append("折射带宽度：").append(Math.round(GlassApplier.bandDpPublic())).append(" dp\n");
            sb.append("位移增益：").append(String.format("%.2f", GlassApplier.refrGain()))
                    .append(" 倍\n");
            sb.append("位移/带宽：").append(String.format("%.2f", GlassApplier.displacementRatio()))
                    .append(" 倍\n");
            sb.append("渲染方式：").append(GlassShader.pathDescription()).append('\n');
            sb.append("已挂载：").append(GlassApplier.appliedCount()).append(" 个控件\n");
            sb.append("背景版本：").append(GlassBackdrop.version()).append('\n');
            sb.append("整行摘要：").append(GlassApplier.debugLine()).append('\n');

            ClipboardManager cm = (ClipboardManager)
                    this.activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("QCL glass", sb.toString()));
                toast(R.string.glass_debug_copied);
            }
        } catch (Throwable ignored) {
            // 剪贴板不可用（少数 ROM 会禁）不该让这一页崩
        }
    }

    private void toast(int resId) {
        try {
            Toast.makeText(this.activity, resId, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    /** 供日志/分享用的一行摘要。 */
    public String oneLineStatus() {
        try {
            return TextUtils.isEmpty(GlassApplier.debugLine())
                    ? "" : GlassApplier.debugLine();
        } catch (Throwable t) {
            return "";
        }
    }
}
