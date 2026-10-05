package com.qcl.launcher.launcher.terracotta;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.utils.qr.QrRenderer;

/**
 * 联机二维码弹窗：把邀请码渲染成二维码，让伙伴直接扫。
 *
 * <p>★★★ 为什么只给房主用：
 * 房主的邀请码是一串 base32 字符（形如 {@code ABCD-EFGH-JKLM}），口头念或手打都容易出错；
 * 二维码把这个环节变成一次扫描。而访客拿到的「服务器地址」通常是本机回环地址，
 * 扫给第三个人没有意义 —— 所以这里只在没有邀请码时才退而展示地址，不做「访客专属二维码」。
 *
 * <p>★★★ 扫码端不需要 QCL 内置扫码器：
 * 伙伴用系统相机或微信扫，得到的就是**纯文本邀请码**，再粘进 QCL 的「加入房间」输入框即可。
 * 这样避免了引第三方扫码库（会破坏离线构建），也不需要自己写相机解码（无法在无设备环境下验证）。
 */
public final class MultiplayerQrDialog {

    private MultiplayerQrDialog() {
    }

    /** 入口：有邀请码就展示邀请码，否则展示服务器地址，两者都没有则提示先去开房。 */
    public static void show(Activity activity, Context context) {
        if (activity == null || context == null) {
            return;
        }
        String code = TerracottaHelper.getInviteCode();
        if (code != null && !code.isEmpty()) {
            showValue(activity, context, code,
                    R.string.multiplayer_qr_title_host, R.string.multiplayer_qr_hint_host);
            return;
        }
        String addr = TerracottaHelper.getServerAddress();
        if (addr != null && !addr.isEmpty()) {
            showValue(activity, context, addr,
                    R.string.multiplayer_qr_title_guest, R.string.multiplayer_qr_hint_guest);
            return;
        }
        Toast.makeText(context, R.string.multiplayer_qr_empty, Toast.LENGTH_SHORT).show();
    }

    private static void showValue(Activity activity, Context context, final String value,
                                  int titleRes, int hintRes) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_multiplayer_qr, null);
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(content).setCancelable(true).create();

        ((TextView) content.findViewById(R.id.qr_title)).setText(titleRes);
        ((TextView) content.findViewById(R.id.qr_hint)).setText(hintRes);
        ((TextView) content.findViewById(R.id.qr_value)).setText(value);

        ImageView image = (ImageView) content.findViewById(R.id.qr_image);
        TextView hint = (TextView) content.findViewById(R.id.qr_hint);

        // 目标边长取屏宽的 70%，并夹到 [240, 720]：
        // 太小扫不清，太大在弹窗里放不下。实际尺寸会被 QrRenderer 向下取整到整数倍模块。
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int target = Math.max(240, Math.min(720, (int) (screenWidth * 0.70f)));
        try {
            Bitmap bitmap = QrRenderer.render(value, target);
            image.setImageBitmap(bitmap);
        } catch (Throwable t) {
            // 内容过长或编码失败时降级：不显示图，明确告诉用户改用文字复制。
            image.setVisibility(View.GONE);
            hint.setText(R.string.multiplayer_qr_encode_failed);
        }

        content.findViewById(R.id.qr_copy).setOnClickListener(v -> {
            MultiplayerDialogHelper.copy(context, value);
            Toast.makeText(context, R.string.multiplayer_qr_copied, Toast.LENGTH_SHORT).show();
        });
        content.findViewById(R.id.qr_close).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
