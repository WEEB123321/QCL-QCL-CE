package com.qcl.launcher.launcher.list.download.minecraft;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.download.game.VersionManifest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import com.qcl.launcher.R;
/* loaded from: classes2.dex */
public class DownloadGameListAdapter extends BaseAdapter {
    private MainActivity activity;
    private Context context;
    private ArrayList<VersionManifest.Version> versions;

    @Override // android.widget.Adapter
    public long getItemId(int i) {
        return 0L;
    }

    /* loaded from: classes2.dex */
    private class ViewHolder {
        ImageView icon;
        LinearLayout item;
        TextView mcId;
        TextView releaseTime;
        TextView type;

        private ViewHolder() {
        }
    }

    /**
     * ★★★ v3.0.5：标签改成按「版本对象」判定，不再只看 {@code type} 字符串。
     *
     * <p>两个原因：① 愚人节版在官方清单里就是 {@code snapshot}，只看 type 会被标成「测试版」；
     * ② 官方清单把 1.3 / 1.4 / 1.4.1 / 1.4.3 / 1.5 标成了 {@code snapshot}，
     * 只看 type 会把五个正式版标成「测试版」。判定逻辑统一放在
     * {@link com.qcl.launcher.launcher.download.game.VersionKind} 与
     * {@link com.qcl.launcher.launcher.download.game.AprilFools}，这里只负责取字符串。
     */
    private String getType(VersionManifest.Version version) {
        if (com.qcl.launcher.launcher.download.game.AprilFools.is(this.context, version.id)) {
            return this.context.getString(R.string.download_minecraft_ui_april);
        }
        if (com.qcl.launcher.launcher.download.game.VersionKind.isRelease(this.context, version)) {
            return this.context.getString(R.string.download_minecraft_ui_release);
        }
        if (com.qcl.launcher.launcher.download.game.VersionKind.isSnapshot(this.context, version)) {
            return this.context.getString(R.string.download_minecraft_ui_snapshot);
        }
        if ("archive".equals(version.type)) {
            return this.context.getString(R.string.download_minecraft_ui_archive);
        }
        return this.context.getString(R.string.download_minecraft_ui_old);
    }

    private Drawable getIcon(VersionManifest.Version version) {
        if (com.qcl.launcher.launcher.download.game.AprilFools.is(this.context, version.id)) {
            // 愚人节版本质是快照，用快照图标（草方块=正式版、命令方块=快照、旧命令方块=远古/归档）
            return this.context.getDrawable(R.drawable.ic_command);
        }
        if (com.qcl.launcher.launcher.download.game.VersionKind.isRelease(this.context, version)) {
            return this.context.getDrawable(R.drawable.ic_grass);
        }
        if (com.qcl.launcher.launcher.download.game.VersionKind.isSnapshot(this.context, version)) {
            return this.context.getDrawable(R.drawable.ic_command);
        }
        return this.context.getDrawable(R.drawable.ic_command_block);
    }

    public DownloadGameListAdapter(Context context, MainActivity mainActivity, ArrayList<VersionManifest.Version> arrayList) {
        this.context = context;
        this.activity = mainActivity;
        this.versions = arrayList;
    }

    @Override // android.widget.Adapter
    public int getCount() {
        return this.versions.size();
    }

    @Override // android.widget.Adapter
    public Object getItem(int i) {
        return this.versions.get(i);
    }

    @Override // android.widget.Adapter
    public View getView(int i, View view, ViewGroup viewGroup) {
        View view2;
        ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            view2 = LayoutInflater.from(this.context).inflate(R.layout.item_download_game_list, (ViewGroup) null);
            viewHolder.item = (LinearLayout) view2.findViewById(R.id.item);
            viewHolder.icon = (ImageView) view2.findViewById(R.id.icon);
            viewHolder.mcId = (TextView) view2.findViewById(R.id.id);
            viewHolder.type = (TextView) view2.findViewById(R.id.type);
            viewHolder.releaseTime = (TextView) view2.findViewById(R.id.release_time);
            this.activity.exteriorConfig.apply(viewHolder.type);
            view2.setTag(viewHolder);
        } else {
            view2 = view;
            viewHolder = (ViewHolder) view.getTag();
        }
        final VersionManifest.Version version = this.versions.get(i);
        viewHolder.icon.setImageDrawable(getIcon(version));
        viewHolder.mcId.setText(version.id);
        viewHolder.type.setText(getType(version));
        viewHolder.releaseTime.setText(version.releaseTime == null ? "" : DateTimeFormatter.ofPattern(this.context.getString(R.string.time_pattern)).withZone(ZoneId.systemDefault()).format(version.releaseTime.toInstant()));
        viewHolder.item.setOnClickListener(new View.OnClickListener() { // from class: com.qcl.launcher.launcher.list.download.minecraft.DownloadGameListAdapter$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                DownloadGameListAdapter.this.m414x716a5e8(version, view3);
            }
        });
        return view2;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$0$com-qcl-launcher-launcher-list-download-minecraft-DownloadGameListAdapter, reason: not valid java name */
    public /* synthetic */ void m414x716a5e8(VersionManifest.Version version, View view) {
        this.activity.uiManager.installGameUI.name = version.id;
        this.activity.uiManager.installGameUI.fabricVersion = null;
        this.activity.uiManager.installGameUI.fabricAPIVersion = null;
        this.activity.uiManager.installGameUI.forgeVersion = null;
        this.activity.uiManager.installGameUI.optifineVersion = null;
        this.activity.uiManager.installGameUI.liteLoaderVersion = null;
        this.activity.uiManager.installGameUI.version = version;
        this.activity.uiManager.switchMainUI(this.activity.uiManager.installGameUI);
    }
}
