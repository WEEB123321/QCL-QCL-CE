package com.qcl.launcher.launcher.uis.game.download.right;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.google.gson.Gson;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.download.game.LegacyVersionArchive;
import com.qcl.launcher.launcher.download.game.VersionManifest;
import com.qcl.launcher.launcher.list.download.minecraft.DownloadGameListAdapter;
import com.qcl.launcher.launcher.uis.game.download.DownloadUrlSource;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;
import com.qcl.launcher.utils.io.NetworkUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.qcl.launcher.R;
/* loaded from: classes2.dex */
public class DownloadMinecraftUI extends BaseUI implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private ArrayList<VersionManifest.Version> allList;
    private CheckBox checkOld;
    private CheckBox checkRelease;
    private CheckBox checkSnapshot;
    /** ★ v3.0.5：愚人节版（独立分类，见 {@link com.qcl.launcher.launcher.download.game.AprilFools}）。 */
    private CheckBox checkApril;
    public LinearLayout downloadMinecraftUI;
    private LinearLayout gameListLayout;
    private LinearLayout hintLayout;
    private boolean loading;
    private ProgressBar loadingProgress;
    private ListView mcList;
    private LinearLayout refresh;

    public DownloadMinecraftUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
        this.allList = new ArrayList<>();
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onCreate() {
        super.onCreate();
        this.downloadMinecraftUI = (LinearLayout) this.activity.findViewById(R.id.ui_download_minecraft);
        LinearLayout linearLayout = (LinearLayout) this.activity.findViewById(R.id.download_minecraft_hint_layout);
        this.hintLayout = linearLayout;
        linearLayout.setOnClickListener(this);
        this.gameListLayout = (LinearLayout) this.activity.findViewById(R.id.game_list_layout);
        this.checkRelease = (CheckBox) this.activity.findViewById(R.id.checkbox_release);
        this.checkSnapshot = (CheckBox) this.activity.findViewById(R.id.checkbox_snapshot);
        this.checkOld = (CheckBox) this.activity.findViewById(R.id.checkbox_old);
        // ★ v3.0.5：愚人节版筛选框。布局里它是第 4 个（见 ui_download_minecraft.xml），
        //   找不到时留 null，refresh() 里做空判断 —— 老布局文件配新代码也不能崩。
        this.checkApril = (CheckBox) this.activity.findViewById(R.id.checkbox_april);
        this.refresh = (LinearLayout) this.activity.findViewById(R.id.refresh_game_list);
        this.loadingProgress = (ProgressBar) this.activity.findViewById(R.id.loading_minecraft_list_progress);
        this.mcList = (ListView) this.activity.findViewById(R.id.download_minecraft_version_list);
        this.checkRelease.setChecked(true);
        this.checkSnapshot.setChecked(false);
        this.checkOld.setChecked(false);
        if (this.checkApril != null) {
            // ★ 默认不勾：愚人节版是彩蛋，不该出现在默认视图里。
            this.checkApril.setChecked(false);
            this.checkApril.setOnCheckedChangeListener(this);
        }
        this.checkRelease.setOnCheckedChangeListener(this);
        this.checkSnapshot.setOnCheckedChangeListener(this);
        this.checkOld.setOnCheckedChangeListener(this);
        this.refresh.setOnClickListener(this);
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onStart() {
        super.onStart();
        CustomAnimationUtils.showViewFromLeft(this.downloadMinecraftUI, this.activity, this.context, false);
        if (this.activity.isLoaded) {
            this.activity.uiManager.downloadUI.startDownloadGameUI.setBackground(this.context.getResources().getDrawable(R.drawable.launcher_button_white));
        }
        if (this.allList.isEmpty()) {
            init();
        } else {
            refresh();
        }
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.downloadMinecraftUI, this.activity, this.context, false);
        if (this.activity.isLoaded) {
            this.activity.uiManager.downloadUI.startDownloadGameUI.setBackground(this.context.getResources().getDrawable(R.drawable.launcher_button_parent));
        }
    }

    private void readManifest(String str, Map<String, VersionManifest.Version> map) throws IOException {
        VersionManifest versionManifest = (VersionManifest) new Gson().fromJson(NetworkUtils.doGet(NetworkUtils.toURL(str)), VersionManifest.class);
        if (versionManifest == null || versionManifest.versions == null) {
            throw new IOException("Empty version manifest");
        }
        for (VersionManifest.Version version : versionManifest.versions) {
            if (version != null && version.id != null && version.type != null && version.url != null && version.url.startsWith("https://") && !map.containsKey(version.id)) {
                map.put(version.id, version);
            }
        }
    }

    private void init() {
        if (this.loading) {
            return;
        }
        this.loading = true;
        this.loadingProgress.setVisibility(0);
        this.refresh.setEnabled(false);
        final String subUrl = DownloadUrlSource.getSubUrl(DownloadUrlSource.getSource(this.activity.launcherSetting.downloadUrlSource), 0);
        new Thread(new Runnable() { // from class: com.qcl.launcher.launcher.uis.game.download.right.DownloadMinecraftUI$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                DownloadMinecraftUI.this.m476x1832f767(subUrl);
            }
        }, "minecraft-manifest").start();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$init$1$com-qcl-launcher-launcher-uis-game-download-right-DownloadMinecraftUI, reason: not valid java name */
    public /* synthetic */ void m476x1832f767(String str) {
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        String subUrl = DownloadUrlSource.getSubUrl(0, 0);
        String subUrl2 = DownloadUrlSource.getSubUrl(1, 0);
        try {
            readManifest(str, linkedHashMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (str.equals(subUrl)) {
                subUrl = subUrl2;
            }
            readManifest(subUrl, linkedHashMap);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        for (VersionManifest.Version version : LegacyVersionArchive.entries(this.context)) {
            if (!linkedHashMap.containsKey(version.id)) {
                linkedHashMap.put(version.id, version);
            }
        }
        final ArrayList arrayList = new ArrayList(linkedHashMap.values());
        VersionManifest.sortNewestFirst(arrayList);
        this.activity.runOnUiThread(new Runnable() { // from class: com.qcl.launcher.launcher.uis.game.download.right.DownloadMinecraftUI$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                DownloadMinecraftUI.this.m475x35074426(arrayList);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$init$0$com-qcl-launcher-launcher-uis-game-download-right-DownloadMinecraftUI, reason: not valid java name */
    public /* synthetic */ void m475x35074426(ArrayList arrayList) {
        this.loading = false;
        this.refresh.setEnabled(true);
        this.loadingProgress.setVisibility(8);
        this.gameListLayout.setVisibility(0);
        if (arrayList.isEmpty()) {
            Toast.makeText(this.context, R.string.revival_manifest_failed, 1).show();
        } else {
            this.allList = arrayList;
        }
        refresh();
    }

    private void refresh() {
        if (this.mcList == null) {
            return;
        }
        // ★★★ v3.0.5：四个分类互斥。原来的写法是「先看 type 再看勾选」，
        //   有两个毛病，都是用户报「版本缺失」的直接原因：
        //   ① 官方清单把 1.3 / 1.4 / 1.4.1 / 1.4.3 / 1.5 标成了 snapshot ⇒
        //      勾「正式版」时这五个根本不出现（详见 VersionKind 的说明）；
        //   ② 愚人节版在清单里也是 snapshot ⇒ 想玩彩蛋的人得在七百多个快照里翻。
        //   现在分类判定统一收口到 VersionKind + AprilFools，这里只做「按勾选取舍」。
        ArrayList<VersionManifest.Version> arrayList = new ArrayList<>();
        boolean showRelease = this.checkRelease.isChecked();
        boolean showSnapshot = this.checkSnapshot.isChecked();
        boolean showOld = this.checkOld.isChecked();
        boolean showApril = this.checkApril != null && this.checkApril.isChecked();
        for (VersionManifest.Version version : this.allList) {
            if (version == null || version.id == null) {
                continue;
            }
            boolean keep;
            if (com.qcl.launcher.launcher.download.game.AprilFools.is(this.context, version.id)) {
                keep = showApril;
            } else if (com.qcl.launcher.launcher.download.game.VersionKind.isRelease(this.context, version)) {
                keep = showRelease;
            } else if (com.qcl.launcher.launcher.download.game.VersionKind.isSnapshot(this.context, version)) {
                keep = showSnapshot;
            } else {
                keep = showOld;
            }
            if (keep) {
                arrayList.add(version);
            }
        }
        this.mcList.setAdapter((ListAdapter) new DownloadGameListAdapter(this.context, this.activity, arrayList));
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View view) {
        if (view == this.hintLayout) {
            this.context.startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://bmclapidoc.bangbang93.com/")));
        }
        if (view == this.refresh) {
            init();
        }
    }

    @Override // com.qcl.launcher.launcher.uis.tools.BaseUI, com.qcl.launcher.launcher.uis.tools.UILifecycleCallbacks
    public void onLoaded() {
        this.activity.uiManager.downloadUI.startDownloadGameUI.setBackground(this.context.getResources().getDrawable(R.drawable.launcher_button_white));
    }

    @Override // android.widget.CompoundButton.OnCheckedChangeListener
    public void onCheckedChanged(CompoundButton compoundButton, boolean z) {
        refresh();
    }
}
