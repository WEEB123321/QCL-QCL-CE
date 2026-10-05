package com.qcl.launcher.launcher.list.local.game;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.download.modloader.ModLoaderDetector;
import com.qcl.launcher.launcher.dialogs.CopyVersionDialog;
import com.qcl.launcher.launcher.dialogs.RenameVersionDialog;
import com.qcl.launcher.launcher.launch.check.LaunchTools;
import com.qcl.launcher.launcher.setting.game.PrivateGameSetting;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.file.DrawableUtils;
import com.qcl.launcher.utils.file.FileUtils;
import com.qcl.launcher.utils.gson.GsonUtils;
import com.tungsten.filepicker.FileBrowser;
import java.io.File;
import java.util.ArrayList;

import com.qcl.launcher.R;
/* loaded from: classes2.dex */
public class GameListAdapter extends BaseAdapter {
    private MainActivity activity;
    private Context context;
    private ArrayList<GameListBean> list;

    /* JADX INFO: Access modifiers changed from: package-private */
    public static /* synthetic */ void lambda$getView$9(DialogInterface dialogInterface, int i) {
    }

    @Override // android.widget.Adapter
    public long getItemId(int i) {
        return 0L;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes2.dex */
    public class ViewHolder {
        ImageView icon;
        LinearLayout item;
        ImageButton moreVert;
        TextView name;
        RadioButton radioButton;
        ImageButton startGame;
        TextView version;

        private ViewHolder() {
        }
    }

    /** ★ 1.2.3：FCL 同款 —— 版本装了哪个加载器就返回哪个的图标 */
    private Integer loaderIconFor(File versionDir) {
        // ★ 1.2.5：统一走 ModLoaderDetector.iconRes（单一来源），
        //   免得列表/设置页各写一份导致同一个版本显示两个图标。
        int li = ModLoaderDetector.iconRes(versionDir);
        return li == 0 ? null : li;
    }

    public GameListAdapter(Context context, MainActivity mainActivity, ArrayList<GameListBean> arrayList) {
        this.context = context;
        this.activity = mainActivity;
        this.list = arrayList;
    }

    private void testGame(String str) {
        String str2 = this.activity.launcherSetting.gameFileDirectory + "/versions/" + str + "/qcl.cfg";
        if (!new File(str2).exists() || GsonUtils.getPrivateGameSettingFromFile(str2) == null || (!GsonUtils.getPrivateGameSettingFromFile(str2).forceEnable && !GsonUtils.getPrivateGameSettingFromFile(str2).enable)) {
            str2 = AppManifest.SETTING_DIR + "/private_game_setting.json";
        }
        Bundle bundle = new Bundle();
        bundle.putString("setting_path", str2);
        bundle.putBoolean("test", true);
        bundle.putString("version", this.activity.launcherSetting.gameFileDirectory + "/versions/" + str);
        LaunchTools.launch(this.context, this.activity, this.activity.launcherSetting.gameFileDirectory + "/versions/" + str, bundle);
    }

    public void refreshCurrentVersion(String str) {
        for (int i = 0; i < this.list.size(); i++) {
            System.out.println(str);
            if (!str.endsWith("/") && this.list.get(i).name.equals(str.substring(str.lastIndexOf("/") + 1))) {
                this.list.get(i).isSelected = true;
            } else {
                this.list.get(i).isSelected = false;
            }
        }
        notifyDataSetChanged();
    }

    @Override // android.widget.Adapter
    public int getCount() {
        return this.list.size();
    }

    @Override // android.widget.Adapter
    public Object getItem(int i) {
        return this.list.get(i);
    }

    @Override // android.widget.Adapter
    public View getView(final int i, View view, ViewGroup viewGroup) {
        View view2;
        final ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            view2 = LayoutInflater.from(this.context).inflate(R.layout.item_local_version, (ViewGroup) null);
            viewHolder.item = (LinearLayout) view2.findViewById(R.id.local_version_item);
            viewHolder.radioButton = (RadioButton) view2.findViewById(R.id.select_version);
            viewHolder.icon = (ImageView) view2.findViewById(R.id.version_icon);
            viewHolder.name = (TextView) view2.findViewById(R.id.version_name);
            viewHolder.version = (TextView) view2.findViewById(R.id.version_id);
            viewHolder.startGame = (ImageButton) view2.findViewById(R.id.test_game);
            viewHolder.moreVert = (ImageButton) view2.findViewById(R.id.more_vert);
            this.activity.exteriorConfig.apply(viewHolder.radioButton);
            view2.setTag(viewHolder);
        } else {
            view2 = view;
            viewHolder = (ViewHolder) view.getTag();
        }
        viewHolder.item.setOnClickListener(new View.OnClickListener() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                GameListAdapter.this.m438x121fb4fc(i, view3);
            }
        });
        // ★★★ 社区版新增：**长按实例** = 直接弹出快捷菜单（启动 / 立即备份 / 导出 / 克隆 /
        //   固定到桌面 / 空间分析 / 重命名 / 删除 …），不用先点进详情页。
        //   复用同一个 PopupMenu：这样「长按」和右上角「⋮」行为完全一致，
        //   不会出现「两个入口能做的事不一样」这种最难解释的问题。
        viewHolder.item.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view3) {
                GameListAdapter.this.m441x531b52f8(viewHolder, i, view3);
                return true;
            }
        });
        if ((this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name).equals(this.activity.publicGameSetting.currentVersion)) {
            this.list.get(i).isSelected = true;
        }
        if (this.list.get(i).isSelected) {
            viewHolder.radioButton.setChecked(true);
        } else {
            viewHolder.radioButton.setChecked(false);
        }
        viewHolder.radioButton.setOnClickListener(new View.OnClickListener() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                GameListAdapter.this.m439x22d581bd(i, view3);
            }
        });
        if (!this.list.get(i).iconPath.equals("") && new File(this.list.get(i).iconPath).exists()) {
            // ★ 1.2.9：改成异步加载 + 缓存（以前是主线程读文件解码，滚动会卡）
            LocalIconLoader.loadBackground(viewHolder.icon, this.list.get(i).iconPath);
        } else {
            // ★ 1.2.3：FCL 同款 —— 装了哪个加载器就显示哪个的图标
            Integer li = loaderIconFor(new File(this.activity.launcherSetting.gameFileDirectory
                    + "/versions/" + this.list.get(i).name));
            viewHolder.icon.setBackground(this.context.getDrawable(li != null ? li : R.drawable.ic_grass));
        }
        viewHolder.name.setText(this.list.get(i).name);
        viewHolder.version.setText(this.list.get(i).version);
        viewHolder.startGame.setOnClickListener(new View.OnClickListener() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda6
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                GameListAdapter.this.m442x338b4e7e(i, view3);
            }
        });
        viewHolder.moreVert.setOnClickListener(new View.OnClickListener() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view3) {
                GameListAdapter.this.m441x531b52f8(viewHolder, i, view3);
            }
        });
        return view2;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$0$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ void m438x121fb4fc(int i, View view) {
        this.activity.uiManager.gameManagerUI.versionName = this.list.get(i).name;
        this.activity.uiManager.switchMainUI(this.activity.uiManager.gameManagerUI);
        this.activity.uiManager.gameManagerUI.gameManagerUIManager.switchGameManagerUIs(this.activity.uiManager.gameManagerUI.gameManagerUIManager.versionSettingUI);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$1$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ void m439x22d581bd(int i, View view) {
        if (this.list.get(i).isSelected) {
            return;
        }
        for (int i2 = 0; i2 < this.list.size(); i2++) {
            if (i2 == i) {
                this.list.get(i2).isSelected = true;
            } else {
                this.list.get(i2).isSelected = false;
            }
        }
        this.activity.publicGameSetting.currentVersion = this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name;
        if (this.activity.privateGameSetting.gameDirSetting.type == 1) {
            this.activity.uiManager.settingUI.settingUIManager.universalGameSettingUI.gameDirText.setText(this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name);
        }
        GsonUtils.savePublicGameSetting(this.activity.publicGameSetting, AppManifest.SETTING_DIR + "/public_game_setting.json");
        notifyDataSetChanged();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$2$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ void m442x338b4e7e(int i, View view) {
        testGame(this.list.get(i).name);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$11$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ void m441x531b52f8(ViewHolder viewHolder, final int i, View view) {
        PopupMenu popupMenu = new PopupMenu(new ContextThemeWrapper(this.context, R.style.MenuStyle), viewHolder.item, 8388613);
        popupMenu.inflate(R.menu.local_version_menu);
        popupMenu.setForceShowIcon(true);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda8
            @Override // androidx.appcompat.widget.PopupMenu.OnMenuItemClickListener
            public final boolean onMenuItemClick(MenuItem menuItem) {
                return GameListAdapter.this.m440x42658637(i, menuItem);
            }
        });
        popupMenu.show();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$10$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ boolean m440x42658637(final int i, MenuItem menuItem) {
        PrivateGameSetting privateGameSetting;
        String str;
        PrivateGameSetting privateGameSetting2;
        PrivateGameSetting privateGameSetting3;
        String str2;
        switch (menuItem.getItemId()) {
            // ★ 社区版新增：立即备份（原来要绕到「备份与恢复」页才能存一份）
            case R.id.local_version_menu_backup: {
                final String bkName = this.list.get(i).name;
                final File bkDir = new File(this.activity.launcherSetting.gameFileDirectory
                        + "/versions/" + bkName);
                final android.content.Context bkCtx = this.context;
                android.widget.Toast.makeText(bkCtx, R.string.menu_local_version_backup_start,
                        android.widget.Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean ok;
                        try {
                            ok = com.qcl.launcher.launcher.backup.InstanceBackupHelper
                                    .createBackup(bkName, bkDir, null) != null;
                        } catch (Throwable t) {
                            ok = false;
                        }
                        final boolean fOk = ok;
                        if (GameListAdapter.this.activity != null) {
                            GameListAdapter.this.activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        android.widget.Toast.makeText(bkCtx, fOk
                                                        ? R.string.menu_local_version_backup_ok
                                                        : R.string.menu_local_version_backup_fail,
                                                android.widget.Toast.LENGTH_SHORT).show();
                                    } catch (Throwable ignored) {
                                    }
                                }
                            });
                        }
                    }
                }, "qcl-manual-backup").start();
                return true;
            }
            // ★ 社区版新增：把这个实例固定到桌面（一个实例一个快捷方式，点谁开谁）
            case R.id.local_version_menu_pin: {
                String n = this.list.get(i).name;
                String path = this.activity.launcherSetting.gameFileDirectory + "/versions/" + n;
                boolean ok = com.qcl.launcher.launcher.shortcut.ShortcutHelper
                        .pinInstanceShortcut(this.context, path, n);
                android.widget.Toast.makeText(this.context,
                        ok ? R.string.menu_local_version_pin_ok : R.string.shortcut_unsupported,
                        android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
            // ★ 社区版新增：占用空间分析
            case R.id.local_version_menu_space:
                com.qcl.launcher.launcher.dialogs.InstanceSpaceDialog
                        .show(this.context, this.activity, this.list.get(i).name);
                return true;
            // ★ 社区版新增：实例间搬运（模组 / 存档 / 资源包 / 光影 / 配置）
            case R.id.local_version_menu_transfer:
                com.qcl.launcher.launcher.dialogs.InstanceTransferDialog
                        .show(this.context, this.activity, this.list.get(i).name);
                return true;
            // ★ 社区版新增：服务器收藏夹 / 房间历史
            case R.id.local_version_menu_servers:
                com.qcl.launcher.launcher.dialogs.ServerBookmarkDialog
                        .show(this.context, this.activity, this.list.get(i).name);
                return true;
            case R.id.local_version_menu_copy:
                String str3 = this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name + "/qcl.cfg";
                if (new File(str3).exists() && GsonUtils.getPrivateGameSettingFromFile(str3) != null && (GsonUtils.getPrivateGameSettingFromFile(str3).forceEnable || GsonUtils.getPrivateGameSettingFromFile(str3).enable)) {
                    privateGameSetting = GsonUtils.getPrivateGameSettingFromFile(str3);
                } else {
                    privateGameSetting = this.activity.privateGameSetting;
                }
                PrivateGameSetting privateGameSetting4 = privateGameSetting;
                if (privateGameSetting4.gameDirSetting.type == 0) {
                    str = this.activity.launcherSetting.gameFileDirectory;
                } else if (privateGameSetting4.gameDirSetting.type == 1) {
                    str = this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name;
                } else {
                    str = privateGameSetting4.gameDirSetting.path;
                }
                new CopyVersionDialog(this.context, this.list, privateGameSetting4, str, this.activity.launcherSetting.gameFileDirectory + "/versions/", this.list.get(i).name, new CopyVersionDialog.CopyVersionCallback() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda9
                    @Override // com.qcl.launcher.launcher.dialogs.CopyVersionDialog.CopyVersionCallback
                    public final void onFinish() {
                        GameListAdapter.this.m446x76628182();
                    }
                }).show();
                return true;
            case R.id.local_version_menu_delete:
                String str4 = this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name + "/qcl.cfg";
                AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
                builder.setTitle(this.context.getString(R.string.dialog_delete_version_title));
                builder.setPositiveButton(this.context.getString(R.string.dialog_delete_version_positive), new DialogInterface.OnClickListener() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda0
                    @Override // android.content.DialogInterface.OnClickListener
                    public final void onClick(DialogInterface dialogInterface, int i2) {
                        GameListAdapter.this.m448x97ce1b04(i, dialogInterface, i2);
                    }
                });
                builder.setNegativeButton(this.context.getString(R.string.dialog_delete_version_negative), new DialogInterface.OnClickListener() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda3
                    @Override // android.content.DialogInterface.OnClickListener
                    public final void onClick(DialogInterface dialogInterface, int i2) {
                        GameListAdapter.lambda$getView$9(dialogInterface, i2);
                    }
                });
                if (new File(str4).exists() && GsonUtils.getPrivateGameSettingFromFile(str4) != null && (GsonUtils.getPrivateGameSettingFromFile(str4).forceEnable || GsonUtils.getPrivateGameSettingFromFile(str4).enable)) {
                    privateGameSetting2 = GsonUtils.getPrivateGameSettingFromFile(str4);
                } else {
                    privateGameSetting2 = this.activity.privateGameSetting;
                }
                if (privateGameSetting2.gameDirSetting.type == 1) {
                    builder.setMessage(this.context.getString(R.string.dialog_delete_version_isolate_msg).replace("%s", this.list.get(i).name));
                } else {
                    builder.setMessage(this.context.getString(R.string.dialog_delete_version_msg).replace("%s", this.list.get(i).name));
                }
                builder.create().show();
                return true;
            case R.id.local_version_menu_export_pack:
                this.activity.uiManager.switchMainUI(this.activity.uiManager.exportPackageTypeUI);
                return true;
            case R.id.local_version_menu_game_folder:
                String str5 = this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name + "/qcl.cfg";
                if (new File(str5).exists() && GsonUtils.getPrivateGameSettingFromFile(str5) != null && (GsonUtils.getPrivateGameSettingFromFile(str5).forceEnable || GsonUtils.getPrivateGameSettingFromFile(str5).enable)) {
                    privateGameSetting3 = GsonUtils.getPrivateGameSettingFromFile(str5);
                } else {
                    privateGameSetting3 = this.activity.privateGameSetting;
                }
                if (privateGameSetting3.gameDirSetting.type == 0) {
                    str2 = this.activity.launcherSetting.gameFileDirectory;
                } else if (privateGameSetting3.gameDirSetting.type == 1) {
                    str2 = this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name;
                } else {
                    str2 = privateGameSetting3.gameDirSetting.path;
                }
                Intent intent = new Intent(this.context, (Class<?>) FileBrowser.class);
                intent.putExtra("INITIAL_DIRECTORY", str2);
                this.context.startActivity(intent);
                return true;
            case R.id.local_version_menu_game_manage:
                this.activity.uiManager.gameManagerUI.versionName = this.list.get(i).name;
                this.activity.uiManager.switchMainUI(this.activity.uiManager.gameManagerUI);
                this.activity.uiManager.gameManagerUI.gameManagerUIManager.switchGameManagerUIs(this.activity.uiManager.gameManagerUI.gameManagerUIManager.versionSettingUI);
                return true;
            case R.id.local_version_menu_rename:
                new RenameVersionDialog(this.context, this.list.get(i).name, new RenameVersionDialog.OnVersionRenameListener() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda10
                    @Override // com.qcl.launcher.launcher.dialogs.RenameVersionDialog.OnVersionRenameListener
                    public final void onRename(String str6) {
                        GameListAdapter.this.m444x54f6e800(i, str6);
                    }
                }).show();
            case R.id.local_version_menu_generate_launch_script:
                return true;
            case R.id.local_version_menu_test_game:
                testGame(this.list.get(i).name);
                return true;
            default:
                return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$4$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ void m444x54f6e800(final int i, final String str) {
        new Thread(new Runnable() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                GameListAdapter.this.m443x44411b3f(i, str);
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$3$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ void m443x44411b3f(int i, String str) {
        FileUtils.rename(this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name + "/" + this.list.get(i).name + ".jar", str + ".jar");
        FileUtils.rename(this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name + "/" + this.list.get(i).name + ".json", str + ".json");
        FileUtils.rename(this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name, str);
        if (this.list.get(i).isSelected) {
            this.activity.publicGameSetting.currentVersion = this.activity.launcherSetting.gameFileDirectory + "/versions/" + str;
            GsonUtils.savePublicGameSetting(this.activity.publicGameSetting, AppManifest.SETTING_DIR + "/public_game_setting.json");
        }
        this.activity.uiManager.versionListUI.refreshVersionList();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$6$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ void m446x76628182() {
        new Thread(new Runnable() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda11
            @Override // java.lang.Runnable
            public final void run() {
                GameListAdapter.this.m445x65acb4c1();
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$5$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ void m445x65acb4c1() {
        this.activity.uiManager.versionListUI.refreshVersionList();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$8$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ void m448x97ce1b04(final int i, DialogInterface dialogInterface, int i2) {
        new Thread(new Runnable() { // from class: com.qcl.launcher.launcher.list.local.game.GameListAdapter$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                GameListAdapter.this.m447x87184e43(i);
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: lambda$getView$7$com-qcl-launcher-launcher-list-local-game-GameListAdapter, reason: not valid java name */
    public /* synthetic */ void m447x87184e43(int i) {
        FileUtils.deleteDirectory(this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name);
        if (this.activity.publicGameSetting.currentVersion.equals(this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(i).name) && this.list.size() > 1) {
            this.list.remove(i);
            this.activity.publicGameSetting.currentVersion = this.activity.launcherSetting.gameFileDirectory + "/versions/" + this.list.get(0).name;
            GsonUtils.savePublicGameSetting(this.activity.publicGameSetting, AppManifest.SETTING_DIR + "/public_game_setting.json");
        }
        this.activity.uiManager.versionListUI.refreshVersionList();
    }
}
