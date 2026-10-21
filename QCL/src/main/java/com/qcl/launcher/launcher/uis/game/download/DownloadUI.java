package com.qcl.launcher.launcher.uis.game.download;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

import com.qcl.launcher.R;
/* loaded from: classes2.dex */
public class DownloadUI extends BaseUI implements View.OnClickListener {
    /**
     * ★★★ 必须是 {@code View}，**不能**是 {@code LinearLayout}。
     *
     * <p>2.0.12 为了放「地区选择」覆盖层，把 {@code ui_download} 的根节点从
     * LinearLayout 换成了 FrameLayout（覆盖层必须是根级最后一个子节点才能盖住整页）。
     * 但字段类型一直没跟着改，构造时 {@code (LinearLayout) findViewById(...)}
     * 直接抛 {@code ClassCastException} —— 而 UIManager 的构造又是
     * {@code try { downloadUI = new DownloadUI(...) } catch (Throwable) { downloadUI = null }}，
     * 于是异常被**静默吞掉**，表现为「点下载页什么都不发生 / 地区界面永远不出现」。
     *
     * <p>★ 教训：给 View 换根类型时，必须把所有 {@code findViewById} 的强转一起改，
     * 否则错误会被上一层 try/catch 吃掉，连堆栈都看不到。
     */
    public View downloadUI;
    public DownloadUIManager downloadUIManager;
    public LinearLayout startDownloadGameUI;
    public LinearLayout startDownloadModUI;
    public LinearLayout startDownloadPackageUI;
    public LinearLayout startDownloadResourcePackUI;
    public LinearLayout startDownloadShaderUI;
    public LinearLayout startDownloadWorldUI;

    public DownloadUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onCreate() {
        super.onCreate();
        // ★ 根节点是 FrameLayout（见字段注释），**不要强转**
        this.downloadUI = this.activity.findViewById(R.id.ui_download);
        // ★★★ 1.9.8：界面排版 —— 下载 页接入
        //   ① 先 apply 已保存的排版 ✓（没自定义过就完全不动 ✓）
        //   ② 再挂「长按进编辑」✓
        //   ★ 顺序不能反：apply 会 removeView/addView，先挂手势会被搬乱 ✗
        try {
            com.qcl.launcher.launcher.uis.layout.LayoutEditor.apply(activity, downloadUI, "download");
            com.qcl.launcher.launcher.uis.layout.LayoutEditor.attach(activity, downloadUI, "download");
        } catch (Throwable ignored) {
        }

        this.startDownloadGameUI = (LinearLayout) this.activity.findViewById(R.id.start_download_game_ui);
        this.startDownloadModUI = (LinearLayout) this.activity.findViewById(R.id.start_download_mod_ui);
        this.startDownloadPackageUI = (LinearLayout) this.activity.findViewById(R.id.start_download_package_ui);
        this.startDownloadResourcePackUI = (LinearLayout) this.activity.findViewById(R.id.start_download_resource_pack_ui);
        this.startDownloadWorldUI = (LinearLayout) this.activity.findViewById(R.id.start_download_world_ui);
        this.startDownloadShaderUI = (LinearLayout) this.activity.findViewById(R.id.start_download_shader_ui);
        this.startDownloadGameUI.setOnClickListener(this);
        this.startDownloadModUI.setOnClickListener(this);
        this.startDownloadPackageUI.setOnClickListener(this);
        this.startDownloadResourcePackUI.setOnClickListener(this);
        this.startDownloadWorldUI.setOnClickListener(this);
        this.startDownloadShaderUI.setOnClickListener(this);
        this.downloadUIManager = new DownloadUIManager(this.context, this.activity);
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onStart() {
        super.onStart();
        this.activity.showBarTitle(this.context.getResources().getString(R.string.download_ui_title), canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.downloadUI, this.activity, this.context, true);
        init();
        // ★★★ 2.0.12：还没选过地区就先问地区，选完才放行页面
        showRegionGateIfNeeded();
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.downloadUI, this.activity, this.context, true);
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        this.downloadUIManager.onActivityResult(i, i2, intent);
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onPause() {
        super.onPause();
        this.downloadUIManager.onPause();
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onResume() {
        super.onResume();
        this.downloadUIManager.onResume();
    }

    private void init() {
        this.downloadUIManager.downloadModUI.refreshGameList();
        this.downloadUIManager.downloadResourcePackUI.refreshGameList();
    }

    /**
     * ★★★ 2.0.12：地区选择层。
     *
     * <p>★ 为什么放在 onStart 而不是 onCreate：这份 UI 是**预加载**的（activity_main 里 include），
     * onCreate 在 App 启动时就跑完了，那时候用户还没进下载页 —— 提前弹会盖到首页上。
     * onStart 才是「用户真的进了这一页」的时刻。
     *
     * <p>★ 从不缓存 View 引用，每次都重新 findViewById：LayoutEditor.apply 会
     * removeView/addView 重排子节点，缓存的那份可能已经不在树上了。
     */
    private void showRegionGateIfNeeded() {
        try {
            View gate = this.activity.findViewById(R.id.download_region_gate);
            if (gate == null) {
                return;
            }
            // ★ 强制置顶：LayoutEditor.apply 在「用户自定义过下载页排版」时会按保存的
            //   顺序重排子节点，可能把地区层排到中间 → 被其他页面盖住。
            //   bringToFront() 把它移到父容器最后一个，FrameLayout 按子节点顺序绘制，
            //   最后一个就在最上层。
            try {
                gate.bringToFront();
            } catch (Throwable ignored) {
            }
            RegionGate.bindOverlay(this.activity, gate, new Runnable() {
                @Override
                public void run() {
                    // 确认后：若选的是中国大陆，当前默认子页是「游戏」，
                    // 那个页面上的下载入口已由 onClick 拦下，这里不用额外处理。
                    DownloadUI.this.init();
                }
            });
        } catch (Throwable ignored) {
        }
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View view) {
        if (view == this.startDownloadGameUI) {
            DownloadUIManager downloadUIManager = this.downloadUIManager;
            downloadUIManager.switchDownloadUI(downloadUIManager.downloadMinecraftUI);
        }
        if (view == this.startDownloadModUI) {
            DownloadUIManager downloadUIManager2 = this.downloadUIManager;
            downloadUIManager2.switchDownloadUI(downloadUIManager2.downloadModUI);
        }
        if (view == this.startDownloadPackageUI) {
            DownloadUIManager downloadUIManager3 = this.downloadUIManager;
            downloadUIManager3.switchDownloadUI(downloadUIManager3.downloadPackageUI);
        }
        if (view == this.startDownloadResourcePackUI) {
            DownloadUIManager downloadUIManager4 = this.downloadUIManager;
            downloadUIManager4.switchDownloadUI(downloadUIManager4.downloadResourcePackUI);
        }
        if (view == this.startDownloadShaderUI) {
            DownloadUIManager downloadUIManager5 = this.downloadUIManager;
            downloadUIManager5.switchDownloadUI(downloadUIManager5.downloadShaderUI);
        }
        if (view == this.startDownloadWorldUI) {
            DownloadUIManager downloadUIManager6 = this.downloadUIManager;
            downloadUIManager6.switchDownloadUI(downloadUIManager6.downloadWorldUI);
        }
    }
}
