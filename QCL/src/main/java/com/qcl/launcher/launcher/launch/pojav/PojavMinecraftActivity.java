/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.Activity
 *  android.content.Context
 *  android.content.Intent
 *  android.content.res.Configuration
 *  android.graphics.SurfaceTexture
 *  android.os.Build$VERSION
 *  android.os.Bundle
 *  android.util.DisplayMetrics
 *  android.util.Log
 *  android.view.InputDevice
 *  android.view.KeyEvent
 *  android.view.Surface
 *  android.view.View
 *  android.view.ViewGroup
 *  android.view.ViewGroup$LayoutParams
 *  android.widget.FrameLayout
 *  android.widget.FrameLayout$LayoutParams
 *  android.widget.Toast
 *  androidx.annotation.Nullable
 *  androidx.appcompat.app.AppCompatActivity
 *  net.kdt.pojavlaunch.BaseMainActivity
 *  net.kdt.pojavlaunch.function.PojavCallback
 *  net.kdt.pojavlaunch.utils.JREUtils
 *  org.lwjgl.glfw.CallbackBridge
 */
package com.qcl.launcher.launcher.launch.pojav;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.qcl.launcher.control.InputBridge;
import com.qcl.launcher.control.MenuHelper;
import com.qcl.launcher.control.view.LayoutPanel;
import com.qcl.launcher.launcher.launch.GameFrameProbe;
import com.qcl.launcher.launcher.launch.LaunchLogWindow;
import com.qcl.launcher.launcher.launch.MCOptionUtils;
import com.qcl.launcher.launcher.launch.pojav.PojavLauncher;
import com.qcl.launcher.launcher.setting.game.GameLaunchSetting;
import com.qcl.launcher.launcher.terracotta.TerracottaHelper;
import com.qcl.launcher.utils.LocaleUtils;
import java.util.Vector;
import net.kdt.pojavlaunch.BaseMainActivity;
import net.kdt.pojavlaunch.function.PojavCallback;
import net.kdt.pojavlaunch.utils.JREUtils;
import org.lwjgl.glfw.CallbackBridge;

import com.qcl.launcher.R;
public class PojavMinecraftActivity
extends BaseMainActivity {
    private GameLaunchSetting gameLaunchSetting;
    private FrameLayout drawerLayout;
    private LayoutPanel baseLayout;
    private GameFrameProbe frameProbe;
    public MenuHelper menuHelper;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ★★★ 1.1.4：自动开启「持续性能模式」（等价于 vivo/iQOO 的"游戏魔盒"性能优化，照搬 FCL）。
        //   默认开启；玩家可在游戏菜单里点一下关 / 再点一下开（存于 qcl_perf 的 performanceMode）。
        try {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                boolean qclPerf = getSharedPreferences("qcl_perf", 0).getBoolean("performanceMode", true);
                getWindow().setSustainedPerformanceMode(qclPerf);
            }
        }
        catch (Throwable ignored) {
            // 某些窗口/设备不支持也不影响启动
        }
        this.gameLaunchSetting = GameLaunchSetting.getGameLaunchSetting(this.getIntent().getExtras().getString("setting_path"), this.getIntent().getExtras().getString("version"));
        if (this.getIntent().getExtras().getBoolean("test") || this.gameLaunchSetting.log) {
            // empty if block
        }
        if (Build.VERSION.SDK_INT >= 28) {
            this.getWindow().getAttributes().layoutInDisplayCutoutMode = this.gameLaunchSetting.fullscreen ? 1 : 2;
        }
        this.getWindow().setFlags(256, 256);
        this.setContentView(R.layout.activity_pojav);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1);
        this.drawerLayout = (FrameLayout)this.getLayoutInflater().inflate(R.layout.activity_control_pattern, null);
        this.addContentView((View)this.drawerLayout, (ViewGroup.LayoutParams)params);
        this.baseLayout = (LayoutPanel)this.findViewById(R.id.base_layout);
        this.scaleFactor = this.gameLaunchSetting.scaleFactor;
        this.handleCallback();
        this.init(this.gameLaunchSetting.game_directory, GameLaunchSetting.isHighVersion(this.gameLaunchSetting));
        this.menuHelper = new MenuHelper((Context)this, (AppCompatActivity)this, this.gameLaunchSetting.fullscreen, this.gameLaunchSetting.game_directory, this.drawerLayout, this.baseLayout, false, this.gameLaunchSetting.controlLayout, 2, this.scaleFactor);
        // ★ 1.4.3：给「无标题界面版本」开护栏 —— 它们进游戏先加载、不经标题界面，加载完游戏才自己
        //   报 grab（实测首帧后 ~2.7s）；这期间 QCL 仍是光标模式，滑动被当绝对光标投给游戏 → 开局大偏。
        org.lwjgl.glfw.CallbackBridge.setSuppressPointerUntilFirstGrab(
                isNoTitleScreenVersion(this.gameLaunchSetting.currentVersion));
        LaunchLogWindow.GameLaunchSettingInfo info = new LaunchLogWindow.GameLaunchSettingInfo();
        info.backend = "Pojav";
        info.version = this.gameLaunchSetting.currentVersion;
        info.javaRuntime = this.gameLaunchSetting.javaPath;
        info.renderer = this.gameLaunchSetting.pojavRenderer;
        info.ramMb = this.gameLaunchSetting.maxRam;
        LaunchLogWindow.setBasics(info);
        new LaunchLogWindow((Activity)this, (ViewGroup)this.drawerLayout).show(info);
        // ★★★ 社区版新增：自定义「启动前脚本」。
        //   ★ 放在后台线程：脚本内容由用户填写、耗时不可预知，绝不能让主线程等它（ANR）。
        //   ★ 诚实说明：它与游戏初始化**并行**执行，不保证严格早于游戏加载 ——
        //     要做到严格先后就得阻塞启动流程，那对绝大多数「同步配置/清日志」的用途得不偿失。
        try {
            final android.content.Context appCtx = getApplicationContext();
            final String gameDir = this.gameLaunchSetting.game_directory;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        com.qcl.launcher.launcher.setting.launcher.LauncherSetting ls =
                                com.qcl.launcher.launcher.setting.InitializeSetting.initializeLauncherSetting();
                        com.qcl.launcher.launcher.launch.LaunchScriptHelper.runPreLaunch(appCtx, ls, gameDir);
                    } catch (Throwable ignored) {
                    }
                }
            }, "qcl-script-pre").start();
        } catch (Throwable ignored) {
        }
        // ★★★ 社区版新增：自动任务 —— 「启动游戏前备份」。
        //   ★ 必须放后台线程：这是整目录拷贝，可能几十秒，放主线程必然 ANR；
        //     也刻意不阻塞游戏启动 —— 备份与启动并行，用户感知不到。
        try {
            final String launchVersion = this.gameLaunchSetting.currentVersion;
            final android.content.Context appCtx = getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    com.qcl.launcher.launcher.autotask.AutoTaskRunner.onGameLaunch(appCtx, launchVersion);
                }
            }, "qcl-autotask-prelaunch").start();
        } catch (Throwable ignored) {
        }
        // ★★★ 社区版新增：本地统计 —— 记一次「启动游戏」（时长在 onDestroy 结算）。
        //   整段吞异常：统计只是锦上添花，绝不允许它影响游戏启动。
        try {
            com.qcl.launcher.launcher.stats.StatsTracker.beginSession(this,
                    new java.io.File(this.gameLaunchSetting.currentVersion).getName());
        } catch (Throwable ignored) {
        }
    }

    /**
     * ★★★ 社区版新增：本地统计 —— 结算本次游戏时长。
     *
     * <p>{@code BaseMainActivity} 没有覆写 onDestroy，这里新增一个不会与父类冲突；
     * 必须调用 {@code super.onDestroy()}，否则会破坏父类的收尾逻辑。
     *
     * <p>若进程被系统直接杀掉（没有走 onDestroy），本次会话不会在这里结算 ——
     * 但 {@code StatsTracker.beginSession} 在下次启动时会就地补结算，时长不会永久丢失。
     */
    @Override
    protected void onDestroy() {
        try {
            com.qcl.launcher.launcher.stats.StatsTracker.endSession(this);
        } catch (Throwable ignored) {
        }
        // ★ 社区版新增：自动记录「进过这个服务器」（#11 房间历史）。
        //   放在 onDestroy 而不是启动时：真正进过服才算一次，中途失败的不该计入历史。
        try {
            String srv = this.gameLaunchSetting == null ? null : this.gameLaunchSetting.server;
            if (srv != null && !srv.isEmpty()) {
                com.qcl.launcher.launcher.server.ServerBookmarkHelper.record(srv);
            }
        } catch (Throwable ignored) {
        }
        // ★ 社区版新增：退出后自动备份存档（#28）。默认关，在「我的 → 自动任务」里开。
        //   内部只起线程、立即返回，不阻塞退出。
        try {
            String gd = this.gameLaunchSetting == null ? null : this.gameLaunchSetting.game_directory;
            com.qcl.launcher.launcher.autotask.AutoTaskRunner
                    .onGameExit(getApplicationContext(), gd);
        } catch (Throwable ignored) {
        }
        // ★★★ 社区版新增：自定义「退出后脚本」。
        //   ★ 不 join：onDestroy 在主线程，等脚本会把退出卡住。这里只负责把它启动起来 ——
        //     退出游戏后会回到启动器主界面，进程仍然存活，脚本有足够时间跑完。
        try {
            final android.content.Context appCtx = getApplicationContext();
            final String gameDir = this.gameLaunchSetting == null ? null : this.gameLaunchSetting.game_directory;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        com.qcl.launcher.launcher.setting.launcher.LauncherSetting ls =
                                com.qcl.launcher.launcher.setting.InitializeSetting.initializeLauncherSetting();
                        com.qcl.launcher.launcher.launch.LaunchScriptHelper.runPostExit(appCtx, ls, gameDir);
                    } catch (Throwable ignored) {
                    }
                }
            }, "qcl-script-post").start();
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    /**
     * 该版本是否「没有标题界面」（= 进游戏先加载一段时间、不经标题界面就直接在世界里）。
     *
     * 依据 Minecraft Wiki「Title Screen」历史章节：**标题界面是 Indev 0.31 的 20100131 构建加入的**
     * （20100206 背景不再滚动；Infdev 20100327 只是把按钮换成 Singleplayer/Multiplayer —— 界面本身早在
     * Indev 20100131 就有了）。所以属于此类只有：
     *   · Classic 全系（`c*`）—— 经典客户端本体没有标题界面，菜单在网页外壳里；
     *   · Indev 0.31 中早于 20100131 的构建（`in-YYYYMMDD-*`）。
     * infdev 起（含 Alpha / Beta / 现代版本）一律 false，不去动它们。
     */
    private static boolean isNoTitleScreenVersion(String currentVersionPath) {
        if (currentVersionPath == null) {
            return false;
        }
        String name = new java.io.File(currentVersionPath).getName().trim().toLowerCase();
        // Classic 全系：版本名是 c0.30-c-1900 / c0.0.13a / c0.28 这种「c + 数字」。
        // ★ 必须限定「c 后面跟数字」—— 曾用 startsWith("c") 误命中 "Cursed-Fabric-MultiMCnew"
        //   （现代 Fabric 整合包，**有**标题界面），会把它的标题界面光标也冻住。
        // pre-classic（RubyDung，rd-*）比 Classic 还早，同样没有标题界面。
        if (java.util.regex.Pattern.compile("^c\\d").matcher(name).find() || name.startsWith("rd-")) {
            return true;
        }
        if (!name.startsWith("in-")) {
            return false;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^in-(\\d{8})").matcher(name);
        return matcher.find() && matcher.group(1).compareTo("20100131") < 0;
    }

    private void startFrameProbe() {
        if (this.frameProbe == null) {
            this.frameProbe = new GameFrameProbe(this.minecraftGLView, () -> this.baseLayout.hideBackground());
        }
        this.frameProbe.start();
    }

    private void stopFrameProbe() {
        if (this.frameProbe != null) {
            this.frameProbe.stop();
        }
    }

    public void handleCallback() {
        this.pojavCallback = new PojavCallback(){

            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                int usableWidth = width;
                int usableHeight = height;
                if (usableWidth < 64 || usableHeight < 64) {
                    DisplayMetrics dm = PojavMinecraftActivity.this.getResources().getDisplayMetrics();
                    usableWidth = dm.widthPixels;
                    usableHeight = dm.heightPixels;
                }
                CallbackBridge.windowWidth = (int)((float)usableWidth * PojavMinecraftActivity.this.scaleFactor);
                CallbackBridge.windowHeight = (int)((float)usableHeight * PojavMinecraftActivity.this.scaleFactor);
                surface.setDefaultBufferSize(CallbackBridge.windowWidth, CallbackBridge.windowHeight);
                CallbackBridge.sendUpdateWindowSize((int)CallbackBridge.windowWidth, (int)CallbackBridge.windowHeight);
                MCOptionUtils.load(((PojavMinecraftActivity)PojavMinecraftActivity.this).gameLaunchSetting.game_directory);
                // ★★★ 2026-09-19 照 FCL FCLGameLauncher.generateOptionsTxt()：
                //   玩家新下载的游戏版本首次启动时没有 options.txt（或其中没有 lang 项），
                //   MC 会回落到英文。这里按「启动器语言 / 系统语言」补上中文，
                //   并按 MC 版本规范化语言码大小写（<1.11 用 zh_CN，≥1.11 用 zh_cn）。
                //   注意：只在缺失时补 —— 玩家在游戏里改过语言的话不会被覆盖。
                try {
                    String qclLang = MCOptionUtils.get("lang");
                    if (qclLang == null || qclLang.trim().isEmpty()) {
                        String ver = new java.io.File(
                                ((PojavMinecraftActivity)PojavMinecraftActivity.this).gameLaunchSetting.currentVersion).getName();
                        MCOptionUtils.set("lang", com.qcl.launcher.utils.LocaleUtils.normalizeMinecraftLang(
                                ver, com.qcl.launcher.utils.LocaleUtils.getMinecraftLang((Context)PojavMinecraftActivity.this)));
                    }
                } catch (Throwable t) {
                    android.util.Log.w("jrelog", "[默认语言] 写入 lang 失败", t);
                }
                MCOptionUtils.set("overrideWidth", String.valueOf(CallbackBridge.windowWidth));
                MCOptionUtils.set("overrideHeight", String.valueOf(CallbackBridge.windowHeight));
                if (GameLaunchSetting.isHighVersion(PojavMinecraftActivity.this.gameLaunchSetting)) {
                    MCOptionUtils.set("fullscreen", "false");
                }
                MCOptionUtils.save(((PojavMinecraftActivity)PojavMinecraftActivity.this).gameLaunchSetting.game_directory);
                // ★★★ 1.3.8：启动前按渲染器改 mod 配置文件（照 FCL 的 modifyIfConfigDetected）——
                //   GL4ES / VGPU 渲染器下关掉 Sodium / Rubidium 的区块渲染 mixin，避免画面闪烁。
                try {
                    com.qcl.launcher.launcher.launch.ModCompatPatcher.patchBeforeLaunch(
                            (Context)PojavMinecraftActivity.this,
                            ((PojavMinecraftActivity)PojavMinecraftActivity.this).gameLaunchSetting);
                } catch (Throwable ignored) {
                }
                int argWidth = usableWidth;
                int argHeight = usableHeight;
                new Thread(() -> {
                    // ★★★ 社区版加固（低版本闪退）：这里原来是**裸调用** getMcArgs。
                    //   它内部要解析版本 json、拼 classpath、遍历库文件 —— 任一处抛异常，
                    //   这个裸 Thread 就会带着未捕获异常终结**整个进程**：
                    //   对外表现正是「点启动直接闪退」，而且用户什么提示都看不到。
                    //   现在兜住，并把原因写进启动日志 + 弹可读提示。
                    Vector<String> args;
                    try {
                        args = PojavLauncher.getMcArgs(PojavMinecraftActivity.this.gameLaunchSetting, (Context)PojavMinecraftActivity.this, (int)((float)argWidth * PojavMinecraftActivity.this.scaleFactor), (int)((float)argHeight * PojavMinecraftActivity.this.scaleFactor), ((PojavMinecraftActivity)PojavMinecraftActivity.this).gameLaunchSetting.server);
                    } catch (Throwable t) {
                        PojavMinecraftActivity.this.reportLaunchFailure("构建启动参数", t);
                        return;
                    }
                    if (args == null) {
                        PojavMinecraftActivity.this.runOnUiThread(() -> {
                            try {
                                Toast.makeText((Context)PojavMinecraftActivity.this, (CharSequence)"\u542f\u52a8\u5931\u8d25\uff1a\u8fd0\u884c\u5e93\u6216\u7248\u672c\u6587\u4ef6\u4e0d\u5b8c\u6574\uff0c\u8be6\u60c5\u89c1\u542f\u52a8\u65e5\u5fd7", (int)1).show();
                            }
                            catch (Throwable throwable) {
                                // empty catch block
                            }
                            PojavMinecraftActivity.this.finish();
                        });
                        return;
                    }
                    PojavMinecraftActivity.this.runOnUiThread(() -> {
                        // ★ 同理：这一段跑在主线程，抛出去就是整个进程死。
                        try {
                            Surface nativeSurface = new Surface(surface);
                            // ★★★ 1.1.1 SDL3 集成：绑定 SDL surface（非 SDL 版本无副作用，SDL3 版本必须）
                            try {
                                org.libsdl.app.SdlBridge.prepareSurface(PojavMinecraftActivity.this,
                                        nativeSurface, null, PojavMinecraftActivity.this);
                            } catch (Throwable ignored) {
                            }
                            JREUtils.setupBridgeWindowNew(nativeSurface);
                            PojavMinecraftActivity.this.startGame(((PojavMinecraftActivity)PojavMinecraftActivity.this).gameLaunchSetting.javaPath, ((PojavMinecraftActivity)PojavMinecraftActivity.this).gameLaunchSetting.home, GameLaunchSetting.isHighVersion(PojavMinecraftActivity.this.gameLaunchSetting), args, ((PojavMinecraftActivity)PojavMinecraftActivity.this).gameLaunchSetting.pojavRenderer, ((PojavMinecraftActivity)PojavMinecraftActivity.this).gameLaunchSetting.game_directory, PojavLauncher.getGlVersion(((PojavMinecraftActivity)PojavMinecraftActivity.this).gameLaunchSetting.currentVersion));
                        } catch (Throwable t) {
                            PojavMinecraftActivity.this.reportLaunchFailure("启动游戏", t);
                        }
                    });
                }).start();
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                CallbackBridge.windowWidth = (int)((float)width * PojavMinecraftActivity.this.scaleFactor);
                CallbackBridge.windowHeight = (int)((float)height * PojavMinecraftActivity.this.scaleFactor);
                surface.setDefaultBufferSize(CallbackBridge.windowWidth, CallbackBridge.windowHeight);
                CallbackBridge.sendUpdateWindowSize((int)CallbackBridge.windowWidth, (int)CallbackBridge.windowHeight);
            }

            public void onCursorModeChange(int mode) {
                if (PojavMinecraftActivity.this.menuHelper != null) {
                    if (mode == 1) {
                        PojavMinecraftActivity.this.menuHelper.enableCursor();
                    } else {
                        PojavMinecraftActivity.this.menuHelper.disableCursor();
                    }
                }
            }

            public void onStart() {
                PojavMinecraftActivity.this.baseLayout.showBackground();
                PojavMinecraftActivity.this.startFrameProbe();
                PojavMinecraftActivity.this.resetPicOutputFlag();
            }

            public void onPicOutput() {
                Log.i((String)"jrelog", (String)"[\u753b\u9762\u5207\u6362] \u6536\u5230 onPicOutput\uff0c\u64a4\u9664\u7b49\u5f85\u754c\u9762");
                // ★ 1.4.3：首帧到达 = 「世界开始出现」，护栏（首次 grab 前不投绝对光标）从这一刻起计时。
                org.lwjgl.glfw.CallbackBridge.notifyFirstFrame();
                PojavMinecraftActivity.this.stopFrameProbe();
                PojavMinecraftActivity.this.baseLayout.hideBackground();
            }

            public void onError(Exception e) {
            }

            public void onExit(int code) {
            }
        };
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (this.menuHelper.gameMenuSetting.mousePatch && keyCode == 4) {
            InputBridge.sendMouseEvent(1, 1, true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (this.menuHelper.gameMenuSetting.mousePatch && keyCode == 4) {
            InputBridge.sendMouseEvent(1, 1, false);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void onBackPressed() {
        int[] devices;
        boolean mouse = false;
        for (int j : devices = InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice((int)j);
            if (device == null || device.isVirtual() || !device.getName().contains("Mouse") && (this.menuHelper == null || this.menuHelper.touchCharInput == null || this.menuHelper.touchCharInput.isEnabled())) continue;
            if (Build.VERSION.SDK_INT >= 29 && device.isExternal()) {
                mouse = true;
                break;
            }
            if (Build.VERSION.SDK_INT >= 29) continue;
            mouse = true;
            break;
        }
        if (!mouse) {
            CallbackBridge.sendKeyPress((int)256);
        }
    }

    /**
     * ★★★ 社区版新增：启动失败时的统一上报（低版本闪退的可诊断化）。
     *
     * <p><b>为什么需要</b>：启动流程里有好几处是在**裸线程 / 主线程**上直接调用的。
     * 那些地方一旦抛异常，就是「未捕获异常终结整个进程」—— 对外表现是
     * 「点启动直接闪退」，用户看不到任何提示，我们也拿不到任何线索。
     *
     * <p>这里做三件事：① 写进启动路标；② 写进启动日志（游戏内日志浮层与
     * 「我的 → 启动诊断」都能看到）；③ 弹一句人话然后正常收尾。
     * 这样同一个崩溃从「无迹可寻」变成「一句话说得清」。
     */
    private void reportLaunchFailure(final String stage, final Throwable t) {
        try {
            com.qcl.launcher.launcher.StartupTrace.mark("★ 启动失败[" + stage + "] → " + t);
        } catch (Throwable ignored) {
        }
        try {
            android.util.Log.e("QCLStartup", "启动失败[" + stage + "]", t);
        } catch (Throwable ignored) {
        }
        try {
            net.kdt.pojavlaunch.Logger.getInstance((Context) this)
                    .appendToLog("【启动失败】" + stage + "：" + t);
        } catch (Throwable ignored) {
        }
        this.runOnUiThread(() -> {
            try {
                Toast.makeText((Context) this,
                        "启动失败（" + stage + "）：" + t
                                + "\n\n详情见「我的 → 启动诊断」，可一键分享。",
                        Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
            try {
                this.finish();
            } catch (Throwable ignored) {
            }
        });
    }

    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.setLanguage(base));
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleUtils.setLanguage((Context)this);
    }

    protected void onPause() {
        if (this.menuHelper.viewManager != null && this.menuHelper.gameCursorMode == 1) {
            CallbackBridge.sendKeyPress((int)256);
        }
        super.onPause();
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        TerracottaHelper.onActivityResult((Activity)this, requestCode);
        if (this.menuHelper != null) {
            this.menuHelper.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void onPostResume() {
        super.onPostResume();
        if (Build.VERSION.SDK_INT >= 28) {
            this.getWindow().getAttributes().layoutInDisplayCutoutMode = this.gameLaunchSetting.fullscreen ? 1 : 2;
        }
        this.getWindow().setFlags(256, 256);
    }
}

