package com.qcl.launcher.launcher.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.auth.Account;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.setting.InitializeSetting;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.gson.GsonUtils;

import java.util.List;

/**
 * ★★★ 社区版新增：快速切换账号。
 *
 * <p><b>解决什么</b>：原来换账号要进「账户」页、找到目标账号、点它、再返回主界面。
 * 这里在主界面**长按账号按钮**就能弹出一张列表，点一下就切好了。
 *
 * <p><b>★ 只做「切换」，不碰登录</b>：已保存的账号直接切换（离线账号、已登录的正版账号都算）；
 * 需要重新登录的（会话过期）本对话框不处理 —— 那种情况必须在账户页走完整登录流程，
 * 在这里「静默重登」既做不对也容易把凭据搞坏。切过去之后主界面会如实显示账号类型，
 * 该重新登录时启动流程自己会拦。
 */
public final class QuickAccountSwitchDialog {

    private QuickAccountSwitchDialog() {
    }

    public static void show(final Context context, final MainActivity activity) {
        if (activity == null || activity.publicGameSetting == null) {
            return;
        }
        List<Account> accounts;
        try {
            accounts = InitializeSetting.initializeAccounts(context);
        } catch (Throwable t) {
            toast(context, String.valueOf(t));
            return;
        }
        if (accounts == null || accounts.isEmpty()) {
            toast(context, context.getString(R.string.quick_account_empty));
            return;
        }

        final String[] labels = new String[accounts.size()];
        int current = -1;
        for (int i = 0; i < accounts.size(); i++) {
            Account a = accounts.get(i);
            boolean isCur = same(activity.publicGameSetting.account, a);
            if (isCur) {
                current = i;
            }
            labels[i] = (isCur ? "● " : "○ ") + safe(a.auth_player_name)
                    + "   ·   " + typeName(context, a);
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.quick_account_title)
                .setSingleChoiceItems(labels, current, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            switchTo(context, activity, accounts.get(which));
                        } catch (Throwable t) {
                            toast(context, context.getString(R.string.quick_account_failed, String.valueOf(t)));
                        }
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void switchTo(Context context, MainActivity activity, Account account) {
        if (account == null) {
            return;
        }
        if (same(activity.publicGameSetting.account, account)) {
            toast(context, context.getString(R.string.quick_account_same));
            return;
        }
        // 与 LaunchTools / AccountUI 的做法保持一致：改 publicGameSetting 再落盘，
        // 最后让主界面刷新显示 —— 不另走一套存储。
        activity.publicGameSetting.account = account;
        GsonUtils.savePublicGameSetting(activity.publicGameSetting,
                AppManifest.SETTING_DIR + "/public_game_setting.json");
        try {
            activity.uiManager.mainUI.refreshAccount();
        } catch (Throwable ignored) {
        }
        toast(context, context.getString(R.string.quick_account_switched, safe(account.auth_player_name)));
    }

    /** 账号「同一个人」的判定：与 LaunchTools 里查重用的字段组合一致。 */
    private static boolean same(Account a, Account b) {
        if (a == null || b == null) {
            return false;
        }
        return eq(a.email, b.email)
                && eq(a.auth_player_name, b.auth_player_name)
                && eq(a.auth_uuid, b.auth_uuid)
                && eq(a.loginServer, b.loginServer);
    }

    private static boolean eq(String x, String y) {
        return x == null ? y == null : x.equals(y);
    }

    private static String typeName(Context context, Account a) {
        switch (a.loginType) {
            case 1:
                return context.getString(R.string.item_account_type_offline);
            case 2:
                return context.getString(R.string.item_account_type_mojang);
            case 3:
                return context.getString(R.string.item_account_type_microsoft);
            case 4:
                return context.getString(R.string.item_account_type_auth_lib);
            case 5:
                return context.getString(R.string.item_account_type_nide_8_auth);
            default:
                return "?";
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static void toast(Context context, String msg) {
        try {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
