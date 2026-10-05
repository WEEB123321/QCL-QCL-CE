package com.qcl.launcher.utils.io;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * ★ 社区版新增：网络计费属性判定，供「仅 Wi-Fi 下载」使用。
 *
 * <p><b>★★★ 为什么问「是否计费」而不是「是否 Wi-Fi」</b>：
 * 直觉写法是 {@code NetworkInfo.getType() == TYPE_WIFI}，但它有两个问题 ——
 * 一是 API 29 起 {@code NetworkInfo} 整体废弃；二是它回答不了真正要问的问题。
 * 手机热点、USB 以太网、部分 VPN 都不走流量；反过来某些公共 Wi-Fi 是按量计费的。
 * {@code NET_CAPABILITY_NOT_METERED} 才是系统给出的「这条网络会不会花你的钱」。
 *
 * <p><b>★ 判定不出来时一律「放行」</b>：宁可让下载继续，也不要因为读不到网络信息
 * 就把用户卡在「明明连着 Wi-Fi 却下不动」的状态。这是更糟的失败方式。
 */
public final class NetworkStateUtils {

    private NetworkStateUtils() {
    }

    /**
     * 当前是否处于「按流量计费」的网络（即：开了仅 Wi-Fi 就应该暂停下载）。
     *
     * <p>以下情况一律返回 {@code false}（不暂停）：
     * <ul>
     *   <li>拿不到 ConnectivityManager / NetworkCapabilities（判定失败，放行）；</li>
     *   <li>当前完全没有活动网络 —— 这种情况交给下载循环正常失败重试，
     *       用户能看到失败清单，而不是停在一个永远不动的「暂停」状态上。</li>
     * </ul>
     */
    public static boolean isMeteredNetwork(Context context) {
        if (context == null) {
            return false;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            Network active = cm.getActiveNetwork();
            if (active == null) {
                // 无网络：不因「仅 Wi-Fi」暂停（否则会永远停住），让下载自己失败并给出提示
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            if (caps == null) {
                return false;
            }
            return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        } catch (Throwable t) {
            return false;
        }
    }
}
