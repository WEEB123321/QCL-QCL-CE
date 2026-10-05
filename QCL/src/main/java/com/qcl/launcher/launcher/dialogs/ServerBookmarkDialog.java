package com.qcl.launcher.launcher.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.launch.check.LaunchTools;
import com.qcl.launcher.launcher.server.ServerBookmarkHelper;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.gson.GsonUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ★★★ 社区版新增：服务器收藏夹 / 房间历史（#11 + #24）。
 *
 * <p>列表里：<b>点一行 = 一键加入</b>（写入当前实例的服务器地址并启动）；
 * <b>长按一行</b> = 收藏 / 编辑 / 删除。
 *
 * <p><b>★ 不做假的在线状态</b>：真正的「在线 / 延迟」需要服务器端配合，本项目不建服务器。
 * 所以这里只显示**本地能知道的事实**：进过几次、上次什么时候进的。
 * 界面上不摆一个永远显示「离线」的假指示灯。
 */
public final class ServerBookmarkDialog {

    private ServerBookmarkDialog() {
    }

    public static void show(final Context context, final MainActivity activity, final String instanceName) {
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        box.setPadding(pad, dp(context, 8), pad, dp(context, 8));

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.server_book_title)
                .setPositiveButton(R.string.server_book_add, null)
                .setNegativeButton(R.string.server_book_close, null)
                .create();
        render(context, activity, instanceName, box, dialog);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(box);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 400)));
        dialog.setView(scroll);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(context, activity, instanceName, null, box, dialog);
            }
        });
    }

    private static void render(final Context context, final MainActivity activity,
                               final String instanceName, final LinearLayout box,
                               final AlertDialog dialog) {
        box.removeAllViews();
        List<ServerBookmarkHelper.Bookmark> list = ServerBookmarkHelper.listSorted();
        if (list.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.server_book_empty);
            empty.setTextSize(12f);
            empty.setTextColor(Color.parseColor("#99000000"));
            box.addView(empty);
            return;
        }
        String lastGroup = null;
        for (final ServerBookmarkHelper.Bookmark b : list) {
            // 分组标题（空分组归到「未分组」）
            String g = (b.group == null || b.group.isEmpty())
                    ? context.getString(R.string.server_book_no_group) : b.group;
            if (!g.equals(lastGroup)) {
                TextView gh = new TextView(context);
                gh.setText(g);
                gh.setTextSize(11f);
                gh.setTextColor(Color.parseColor("#FF185FA5"));
                gh.setPadding(0, dp(context, 10), 0, dp(context, 4));
                box.addView(gh);
                lastGroup = g;
            }

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundResource(R.drawable.qcl_button_gray);
            row.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(context, 4);
            row.setLayoutParams(lp);
            row.setClickable(true);
            row.setFocusable(true);

            TextView title = new TextView(context);
            title.setText((b.favorite ? "★ " : "") + (b.name == null ? b.address : b.name));
            title.setTextSize(13f);
            title.setTextColor(Color.BLACK);
            row.addView(title);

            TextView sub = new TextView(context);
            StringBuilder sb = new StringBuilder(b.address);
            if (b.note != null && !b.note.isEmpty()) {
                sb.append("  ·  ").append(b.note);
            }
            sb.append("  ·  ").append(context.getString(R.string.server_book_joined_times, b.joinCount));
            if (b.lastJoinedAt > 0) {
                sb.append("  ·  ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(new Date(b.lastJoinedAt)));
            }
            sub.setText(sb.toString());
            sub.setTextSize(10f);
            sub.setTextColor(Color.parseColor("#99000000"));
            row.addView(sub);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    join(context, activity, instanceName, b.address);
                    try {
                        dialog.dismiss();
                    } catch (Throwable ignored) {
                    }
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    rowMenu(context, activity, instanceName, b, box, dialog);
                    return true;
                }
            });
            box.addView(row);
        }

        TextView hint = new TextView(context);
        hint.setText(R.string.server_book_hint);
        hint.setTextSize(10f);
        hint.setTextColor(Color.parseColor("#99000000"));
        hint.setPadding(0, dp(context, 12), 0, 0);
        box.addView(hint);
    }

    private static void rowMenu(final Context context, final MainActivity activity,
                                final String instanceName, final ServerBookmarkHelper.Bookmark b,
                                final LinearLayout box, final AlertDialog dialog) {
        final String[] items = {
                context.getString(R.string.server_book_fav),
                context.getString(R.string.server_book_edit),
                context.getString(R.string.server_book_delete),
        };
        new AlertDialog.Builder(context)
                .setTitle(b.name == null ? b.address : b.name)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            ServerBookmarkHelper.toggleFavorite(b.address);
                            render(context, activity, instanceName, box, dialog);
                        } else if (which == 1) {
                            edit(context, activity, instanceName, b, box, dialog);
                        } else {
                            ServerBookmarkHelper.remove(b.address);
                            render(context, activity, instanceName, box, dialog);
                        }
                    }
                })
                .show();
    }

    /** 新增 / 编辑。三个字段：名字、地址、备注（+ 分组）。 */
    private static void edit(final Context context, final MainActivity activity,
                             final String instanceName, final ServerBookmarkHelper.Bookmark exist,
                             final LinearLayout box, final AlertDialog dialog) {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        form.setPadding(pad, dp(context, 8), pad, dp(context, 8));

        final EditText name = field(context, form, R.string.server_book_field_name,
                exist == null ? "" : exist.name, false);
        final EditText addr = field(context, form, R.string.server_book_field_address,
                exist == null ? "" : exist.address, false);
        final EditText note = field(context, form, R.string.server_book_field_note,
                exist == null ? "" : exist.note, false);
        final EditText group = field(context, form, R.string.server_book_field_group,
                exist == null ? "" : exist.group, false);

        new AlertDialog.Builder(context)
                .setTitle(exist == null ? R.string.server_book_add : R.string.server_book_edit)
                .setView(form)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String a = addr.getText().toString().trim();
                        if (a.isEmpty()) {
                            toast(context, context.getString(R.string.server_book_need_address));
                            return;
                        }
                        ServerBookmarkHelper.Bookmark b =
                                exist == null ? new ServerBookmarkHelper.Bookmark() : exist;
                        b.address = a;
                        String n = name.getText().toString().trim();
                        b.name = n.isEmpty() ? a : n;
                        b.note = note.getText().toString().trim();
                        b.group = group.getText().toString().trim();
                        ServerBookmarkHelper.upsert(b);
                        render(context, activity, instanceName, box, dialog);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static EditText field(Context context, LinearLayout parent, int labelRes,
                                  String value, boolean numeric) {
        TextView l = new TextView(context);
        l.setText(labelRes);
        l.setTextSize(11f);
        l.setTextColor(Color.parseColor("#99000000"));
        l.setPadding(0, dp(context, 8), 0, dp(context, 2));
        parent.addView(l);

        EditText e = new EditText(context);
        e.setText(value == null ? "" : value);
        e.setTextSize(13f);
        e.setSingleLine(true);
        if (numeric) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        parent.addView(e);
        return e;
    }

    // ------------------------------------------------------------------ 加入

    /**
     * 一键加入：把地址写进**当前实例**的服务器设置，然后走正常启动流程。
     *
     * <p>★ 与工程既有的做法一致：`privateGameSetting.server` 就是「启动后自动进的服务器」，
     * 版本设置页 / 全局游戏设置页改的也是它。这里不另开一套存储。
     */
    private static void join(Context context, MainActivity activity, String instanceName, String address) {
        try {
            activity.privateGameSetting.server = address;
            GsonUtils.savePrivateGameSetting(activity.privateGameSetting,
                    AppManifest.SETTING_DIR + "/private_game_setting.json");
            // 自动记一笔历史（进过就留下，不用用户手动维护）
            ServerBookmarkHelper.record(address);
        } catch (Throwable t) {
            toast(context, context.getString(R.string.server_book_failed, String.valueOf(t)));
            return;
        }
        toast(context, context.getString(R.string.server_book_joining, address));

        String versionPath = activity.launcherSetting.gameFileDirectory + "/versions/" + instanceName;
        String settingPath = versionPath + "/qcl.cfg";
        String finalPath;
        if (new File(settingPath).exists()
                && GsonUtils.getPrivateGameSettingFromFile(settingPath) != null
                && (GsonUtils.getPrivateGameSettingFromFile(settingPath).forceEnable
                    || GsonUtils.getPrivateGameSettingFromFile(settingPath).enable)) {
            finalPath = settingPath;
        } else {
            finalPath = AppManifest.SETTING_DIR + "/private_game_setting.json";
        }
        Bundle bundle = new Bundle();
        bundle.putString("setting_path", finalPath);
        bundle.putBoolean("test", false);
        LaunchTools.launch(context, activity, versionPath, bundle);
    }

    private static int dp(Context context, int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private static void toast(Context context, String msg) {
        try {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
