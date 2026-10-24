package com.qcl.launcher.launcher.uis.tools;

import android.util.Log;

import java.util.Set;

/**
 * ★★★ v3.0.3：页面生命周期**安全派发**（三个子页容器共用）。
 *
 * <h3>为什么需要这个类</h3>
 * {@link com.qcl.launcher.launcher.uis.tools.UIManager} 早在 2.2.x 就给自己的派发循环
 * 套上了 {@code safeDispatch}，但**同一条链路上的另外三个容器一直漏着**：
 * <ul>
 *   <li>{@code SettingUIManager.switchSettingUIs}（设置页 8 个子页）</li>
 *   <li>{@code DownloadUIManager.switchDownloadUI}（下载页 6 个子页）</li>
 *   <li>{@code GameManagerUIManager.switchGameManagerUIs}（实例页子页）</li>
 * </ul>
 * 它们的循环体都是裸的 {@code arr[i].onStart() / onStop()}，**一处 try/catch 都没有**。
 * 于是「首页能开、点某个 Tab 就闪退」：异常冒泡到主线程 → 全局处理器 → 杀进程。
 * 而且崩在别人的回调里，堆栈和用户刚才的操作看起来毫无关系，是最难定位的一类。
 *
 * <h3>★ 语义与 UIManager.safeDispatch 完全一致（不要各写一套）</h3>
 * <ul>
 *   <li><b>onStart 失败 → 拉黑</b>：它连显示都做不到，再切进去只会重复崩；</li>
 *   <li><b>onStop / onResume / onPause / onActivityResult 失败 → 只记录不拉黑</b>：
 *       拉黑会让导航莫名其妙失效，那是把一个问题换成另一个问题；</li>
 *   <li><b>绝不静默</b>：路标 + logcat 两处都留，失败页面在「我的 → 启动诊断」里看得见。</li>
 * </ul>
 *
 * <p>★ 为什么抽成静态工具而不是每个 Manager 复制一份：这套判断已经被验证过是对的
 * （UIManager 用了好几版），复制三份只会得到三个逐渐跑偏的版本 ——
 * 这个项目在「同类逻辑复制多份」上已经翻过车（签名、版本号、docs 三处同步）。
 */
public final class UiDispatch {

    private static final String TAG = "QCLStartup";

    private UiDispatch() {
    }

    /**
     * 安全地派发一次页面生命周期调用。
     *
     * @param what          阶段名，写进日志/路标用（"onStart" / "onStop" / …）
     * @param ui            目标页面；null 直接跳过
     * @param action        真正要跑的动作
     * @param disableOnFail 失败后是否把该页面拉黑（只有 onStart 传 true）
     * @param brokenUIs     Manager 自己的黑名单；可为 null（等于不拉黑、也不跳过）
     */
    public static void run(String what, BaseUI ui, Runnable action, boolean disableOnFail,
                           Set<BaseUI> brokenUIs) {
        if (ui == null) {
            return;
        }
        if (brokenUIs != null && brokenUIs.contains(ui)) {
            return;
        }
        try {
            action.run();
        } catch (Throwable t) {
            String name = ui.getClass().getSimpleName();
            if (disableOnFail && brokenUIs != null) {
                brokenUIs.add(ui);
            }
            try {
                com.qcl.launcher.launcher.StartupTrace.mark(
                        "★ 页面 " + what + " 失败（已隔离跳过）→ " + name + " : " + t);
            } catch (Throwable ignored) {
                // 观测手段绝不能成为新的崩溃源
            }
            Log.e(TAG, "页面 " + what + " 失败，已隔离：" + name, t);
        }
    }
}
