package com.qcl.launcher.launcher.uis.main;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.auth.authlibinjector.AuthlibInjectorServer;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.download.modloader.ModLoaderDetector;
import com.qcl.launcher.launcher.launch.check.LaunchTools;
import com.qcl.launcher.launcher.list.local.game.GameListBean;
import com.qcl.launcher.launcher.uis.universal.setting.right.launcher.ExteriorSettingUI;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.launcher.setting.InitializeSetting;
import com.qcl.launcher.launcher.setting.SettingUtils;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.launcher.view.spinner.VersionSpinnerAdapter;
import com.qcl.launcher.skin.GameCharacter;
import com.qcl.launcher.skin.MinecraftSkinRenderer;
import com.qcl.launcher.skin.SkinGLSurfaceView;
import com.qcl.launcher.skin.utils.Avatar;
// ★ 1.4.1：老格式皮肤需要归一化后再渲染（否则帽子层黑块 / 模型误判）
import com.qcl.launcher.skin.utils.NormalizedSkin;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;
import com.qcl.launcher.utils.file.DrawableUtils;
import com.qcl.launcher.utils.gson.GsonUtils;
import com.qcl.launcher.utils.io.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MainUI extends BaseUI implements View.OnClickListener, AdapterView.OnItemSelectedListener {

    public LinearLayout mainUI;

    private LinearLayout startAccountUI;
    private LinearLayout startGameManagerUI;
    private LinearLayout startVersionListUI;
    private LinearLayout startDownloadUI;
    private LinearLayout startMultiPlayerUI;
    private LinearLayout startSettingUI;
    // ★ 1.4.1：大厅 / 实验室入口（自 1.4.0 朋友源码包合并）
    private LinearLayout startLobbyUI;
    private LinearLayout startLabUI;

    private LinearLayout startGame;
    private TextView launchVersionText;

    public ImageView accountSkinFace;
    public ImageView accountSkinHat;

    private LinearLayout accountModelView;
    private FrameLayout accountModelContainer;
    private SkinGLSurfaceView skinGLSurfaceView;
    /**
     * ★★★ 社区版：主界面账号形象 = **大头像**（普通 ImageView）。
     * 取代原来的 3D 人物（SkinGLSurfaceView / SurfaceView）——
     * 后者是独立窗口图层且 setZOrderOnTop，会盖住所有普通控件。
     */
    private ImageView bigAvatar;

    /** ★ 头像浮动动画（只一个 ObjectAnimator，开销极小）。 */
    private android.animation.ObjectAnimator avatarAnim;
    private MinecraftSkinRenderer skinRenderer;
    public TextView accountName;
    public TextView accountType;

    private ImageView versionIcon;
    private LinearLayout noVersionAlert;
    private TextView currentVersionText;

    private VersionSpinnerAdapter versionSpinnerAdapter;

    private ImageView versionListIcon;
    private ImageView downloadIcon;
    private ImageView multiplayerIcon;
    private ImageView settingIcon;

    /** ★ 1.2.3：FCL 同款 —— 版本装了哪个加载器就返回哪个的图标
     *  ★ 1.2.5：改走 ModLoaderDetector.iconRes 单一来源，保证各页面图标一致 */
    private Integer loaderIconFor(File versionDir) {
        int li = ModLoaderDetector.iconRes(versionDir);
        return li == 0 ? null : li;
    }

    public MainUI(Context context, MainActivity activity) {
        super(context, activity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainUI = activity.findViewById(R.id.ui_main);

        startAccountUI = activity.findViewById(R.id.start_ui_account);
        startGameManagerUI = activity.findViewById(R.id.start_ui_game_manager);
        startVersionListUI = activity.findViewById(R.id.start_ui_version_list);
        startDownloadUI = activity.findViewById(R.id.start_ui_download);
        startMultiPlayerUI = activity.findViewById(R.id.start_ui_multi_player);
        startSettingUI = activity.findViewById(R.id.start_ui_setting);
        // ★ 1.4.1 新增入口
        startLobbyUI = activity.findViewById(R.id.start_ui_lobby);
        startLabUI = activity.findViewById(R.id.start_ui_lab);

        startGame = activity.findViewById(R.id.launcher_play_button);
        launchVersionText = activity.findViewById(R.id.launch_version_text);

        accountSkinFace = activity.findViewById(R.id.account_skin_face);
        accountSkinHat = activity.findViewById(R.id.account_skin_hat);
        // ★ account_model_view 已随 3D 人物一起从布局删除 —— 不再 findViewById（会编译不过）。
        //   accountModelView 字段保留但恒为 null，所有引用处都有判空。
        accountModelContainer = activity.findViewById(R.id.account_model_container);
        setupAccountModel();
        accountName = activity.findViewById(R.id.account_name_text);
        accountType = activity.findViewById(R.id.account_state_text);

        versionIcon = activity.findViewById(R.id.current_version_icon);
        noVersionAlert = activity.findViewById(R.id.no_version_alert_text);
        currentVersionText = activity.findViewById(R.id.current_version_name_text);

        //icon
        versionListIcon = activity.findViewById(R.id.version_list_icon);
        downloadIcon = activity.findViewById(R.id.download_icon);
        multiplayerIcon = activity.findViewById(R.id.multiplayer_icon);
        settingIcon = activity.findViewById(R.id.setting_icon);

        startAccountUI.setOnClickListener(this);
        // ★ 社区版新增：**长按**账号按钮 = 快速切换账号。
        //   刻意不做成长按之外的第二个入口 —— 主界面顶部已经排了 7 个按钮，
        //   再加一个「切换账号」会挤；而「长按账号看账号列表」符合直觉。
        startAccountUI.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                com.qcl.launcher.launcher.dialogs.QuickAccountSwitchDialog.show(context, activity);
                return true;
            }
        });
        startGameManagerUI.setOnClickListener(this);
        startVersionListUI.setOnClickListener(this);
        startDownloadUI.setOnClickListener(this);
        startMultiPlayerUI.setOnClickListener(this);
        startSettingUI.setOnClickListener(this);
        // ★ 1.4.1 新增入口
        startLobbyUI.setOnClickListener(this);
        startLabUI.setOnClickListener(this);

        startGame.setOnClickListener(this);
        // ★★★ 1.1.1：长按启动按钮 → 选择渲染器（公共选择器，版本设置/全局设置共用同一套）
        startGame.setOnLongClickListener(v -> {
            com.qcl.launcher.launcher.launch.RendererPicker.show(activity,
                    activity.privateGameSetting,
                    activity.publicGameSetting.currentVersion, null);
            return true;
        });
    }

    private AuthlibInjectorServer getServerFromUrl(String url){
        ArrayList<AuthlibInjectorServer> list = InitializeSetting.initializeAuthlibInjectorServer(context);
        for (int i = 0;i < list.size();i++){
            if (list.get(i).getUrl().equals(url)){
                return list.get(i);
            }
        }
        return null;
    }

    /**
     * ★★★ 社区版：填充主界面的**编辑式区块**（大标题 / 元数据 / 轮播点 / 最近游玩）。
     *
     * <p>这些是照用户给的排版参考图新加的纯展示内容。
     * 全部包在 try/catch 里 —— 装饰性内容缺了不该影响启动，更不该让主界面崩。
     */
    private void fillEditorialBlocks() {
        try {
            String cur = activity.publicGameSetting == null
                    ? "" : activity.publicGameSetting.currentVersion;
            String name = (cur == null || cur.isEmpty())
                    ? "" : cur.substring(cur.lastIndexOf('/') + 1);

            // ---- 大标题 = 当前实例名 ----
            android.widget.TextView title = activity.findViewById(R.id.main_profile_title);
            if (title != null) {
                title.setText(name.isEmpty()
                        ? activity.getString(R.string.launcher_button_current_version) : name);
            }

            // ---- 元数据：Minecraft <版本> · <加载器> · <N> Mods ----
            android.widget.TextView meta = activity.findViewById(R.id.main_meta_text);
            if (meta != null && !name.isEmpty()) {
                java.io.File dir = new java.io.File(activity.launcherSetting.gameFileDirectory
                        + "/versions/" + name);
                StringBuilder sb = new StringBuilder();
                String mc = guessMcVersion(name);
                if (!mc.isEmpty()) {
                    sb.append("Minecraft ").append(mc);
                }
                String loader = guessLoader(name);
                if (!loader.isEmpty()) {
                    if (sb.length() > 0) sb.append("  ·  ");
                    sb.append(loader);
                }
                int mods = countMods(new java.io.File(dir, "mods"));
                if (mods > 0) {
                    if (sb.length() > 0) sb.append("  ·  ");
                    sb.append(mods).append(" Mods");
                }
                meta.setText(sb.toString());
            }

            // ---- 轮播指示器 ----
            // ★★★ 注意：main_page_dots 现在是 **LinearLayout**（参考图那种「长线 + 短线 + 圆点」），
            //   不再是 TextView。这里**绝不能**再强转成 TextView —— 那是 ClassCastException，
            //   而且是在 onStart 里，会直接崩掉主界面。
            //   现在只按轮播张数调整「当前页」那条长线的宽度：图越多，长线越短。
            android.view.View dotActive = activity.findViewById(R.id.main_dot_active);
            if (dotActive != null) {
                int n = 0;
                try {
                    n = com.qcl.launcher.launcher.uis.main.DynamicBackground.RES_IDS.length;
                } catch (Throwable ignored) {
                }
                if (n > 0) {
                    int base = (int) (52f * activity.getResources().getDisplayMetrics().density);
                    int w = Math.max(base / Math.max(1, n), (int) (14f * activity.getResources().getDisplayMetrics().density));
                    android.view.ViewGroup.LayoutParams lp = dotActive.getLayoutParams();
                    if (lp != null) {
                        lp.width = w;
                        dotActive.setLayoutParams(lp);
                    }
                }
            }

            // ---- 按钮右侧的页码「01 / 05」（照参考图）----
            try {
                int n = com.qcl.launcher.launcher.uis.main.DynamicBackground.RES_IDS.length;
                android.widget.TextView now = activity.findViewById(R.id.main_page_no_now);
                android.widget.TextView tot = activity.findViewById(R.id.main_page_no_total);
                if (now != null) {
                    now.setText("01");
                }
                if (tot != null && n > 0) {
                    tot.setText("/ " + (n < 10 ? "0" + n : String.valueOf(n)));
                }
            } catch (Throwable ignored) {
            }

            // ---- 左侧栏底部的版本号（参考图里侧栏最下方那个 V2.4）----
            try {
                android.widget.TextView ver = activity.findViewById(R.id.nav_app_version);
                if (ver != null) {
                    String vn = activity.getPackageManager()
                            .getPackageInfo(activity.getPackageName(), 0).versionName;
                    ver.setText("V" + vn);
                }
            } catch (Throwable ignored) {
            }

            // ---- 右栏：最近游玩 ----
            fillRecentList();
        } catch (Throwable ignored) {
        }
    }

    /** 最近玩过的实例（来自 StatsTracker 的会话记录，按时间倒序取前 4 条）。 */
    private void fillRecentList() {
        try {
            android.widget.LinearLayout list = activity.findViewById(R.id.main_recent_list);
            if (list == null) {
                return;
            }
            list.removeAllViews();
            com.qcl.launcher.launcher.stats.StatsTracker.Stats st =
                    com.qcl.launcher.launcher.stats.StatsTracker.get(activity);
            java.util.LinkedHashMap<String, Long> recent =
                    new java.util.LinkedHashMap<String, Long>();
            if (st != null && st.sessions != null) {
                for (int i = st.sessions.size() - 1; i >= 0 && recent.size() < 4; i--) {
                    com.qcl.launcher.launcher.stats.StatsTracker.Session s = st.sessions.get(i);
                    if (s == null || s.instance == null || s.instance.isEmpty()) {
                        continue;
                    }
                    if (!recent.containsKey(s.instance)) {
                        recent.put(s.instance, s.startAt);
                    }
                }
            }
            if (recent.isEmpty()) {
                android.widget.TextView empty = new android.widget.TextView(activity);
                empty.setText(activity.getString(R.string.main_recent_empty));
                empty.setTextSize(11f);
                empty.setTextColor(0x991B1B1B);
                list.addView(empty);
                return;
            }
            int idx = 1;
            for (java.util.Map.Entry<String, Long> e : recent.entrySet()) {
                list.addView(recentRow(idx++, e.getKey(), e.getValue()));
            }
            android.widget.TextView cnt = activity.findViewById(R.id.main_recent_count);
            if (cnt != null) {
                cnt.setText(activity.getString(R.string.main_recent_unit, recent.size()));
            }
            android.widget.TextView no = activity.findViewById(R.id.main_footer_no);
            if (no != null) {
                no.setText("№ " + String.format(java.util.Locale.US, "%03d", st == null ? 0 : st.launchCount));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 最近游玩的一行：编号 + 实例名 + 右侧「多久之前」，底部一条细分隔线。 */
    private android.view.View recentRow(int no, String name, long at) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(activity);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.LinearLayout row = new android.widget.LinearLayout(activity);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        android.widget.TextView n = new android.widget.TextView(activity);
        n.setText(String.format(java.util.Locale.US, "%02d", no));
        n.setTextSize(10f);
        n.setTextColor(0x801B1B1B);
        n.setMinWidth(dp(24));
        row.addView(n);

        android.widget.TextView t = new android.widget.TextView(activity);
        t.setText(name);
        t.setTextSize(12f);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTextColor(0xFF1B1B1B);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        t.setLayoutParams(lp);
        row.addView(t);

        android.widget.TextView ago = new android.widget.TextView(activity);
        ago.setText(agoLabel(at));
        ago.setTextSize(10f);
        ago.setTextColor(0x991B1B1B);
        row.addView(ago);

        box.addView(row);

        android.view.View line = new android.view.View(activity);
        line.setBackgroundColor(0x261B1B1B);
        box.addView(line, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return box;
    }

    private String agoLabel(long at) {
        try {
            long d = System.currentTimeMillis() - at;
            if (d < 60_000L) return "now";
            if (d < 3_600_000L) return (d / 60_000L) + " m";
            if (d < 86_400_000L) return (d / 3_600_000L) + " H";
            return (d / 86_400_000L) + " D";
        } catch (Throwable t) {
            return "";
        }
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }

    private int countMods(java.io.File modsDir) {
        try {
            java.io.File[] fs = modsDir.listFiles();
            if (fs == null) {
                return 0;
            }
            int n = 0;
            for (java.io.File f : fs) {
                String ln = f.getName().toLowerCase();
                if (f.isFile() && (ln.endsWith(".jar") || ln.endsWith(".zip") || ln.endsWith(".litemod"))) {
                    n++;
                }
            }
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 从实例名里抠出 MC 版本（和 ModScanner / 搬运工具同一套推断）。 */
    private String guessMcVersion(String name) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(name);
            String best = "";
            while (m.find()) {
                String g = m.group(1);
                if (g.startsWith("1.")) {
                    best = g;
                }
            }
            return best;
        } catch (Throwable t) {
            return "";
        }
    }

    private String guessLoader(String name) {
        if (name == null) return "";
        String n = name.toLowerCase();
        if (n.contains("neoforge")) return "NeoForge";
        if (n.contains("forge")) return "Forge";
        if (n.contains("quilt")) return "Quilt";
        if (n.contains("fabric")) return "Fabric";
        return "";
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    public void onStart() {
        super.onStart();
        // ★ 1.3.7：回到主界面恢复 3D 人物（受「主界面显示账号人物」开关控制，默认开）
        boolean showModelCfg = true;
        try {
            showModelCfg = activity.launcherSetting == null || activity.launcherSetting.showAccountModel;
        } catch (Throwable ignored) {
        }
        // ★★★ 社区版：账号形象 = **大头像**（普通 ImageView），不再是 3D 人物。
        //   「主界面显示账号人物」这个开关继续有效，控制的就是这块头像。
        if (bigAvatar != null) {
            bigAvatar.setVisibility(showModelCfg ? View.VISIBLE : View.INVISIBLE);
        }
        if (!showModelCfg && accountModelView != null) {
            accountModelView.setVisibility(View.GONE);
        }
        // 每次回到主界面同步一次，保证和左边小头像一致
        syncBigAvatar();
        // ★ 头像尺寸按屏幕高度自适应（横竖屏切换 / 分屏 / 折叠屏都能跟上）
        applyAdaptiveAvatarSize();
        // ★ 轻量动效（头像浮动 + 按钮按下缩放）
        startLightAnimations();
        CustomAnimationUtils.showViewFromLeft(mainUI,activity,context,true);
        activity.hideBarTitle();
        // ★ 社区版：刷新编辑式区块（大标题 / 元数据 / 最近游玩）
        fillEditorialBlocks();

        new Thread(() -> {
            ArrayList<GameListBean> gameList = SettingUtils.getLocalVersionInfo(activity.launcherSetting.gameFileDirectory,activity.publicGameSetting.currentVersion);
            activity.runOnUiThread(() -> {
                GameListBean currentVersion = new GameListBean("","","",true);
                if (!activity.publicGameSetting.currentVersion.equals("")){
                    for (int i = 0;i < gameList.size();i++) {
                        if (gameList.get(i).name.equals(activity.publicGameSetting.currentVersion.substring(activity.publicGameSetting.currentVersion.lastIndexOf("/") + 1))) {
                            currentVersion = gameList.get(i);
                        }
                    }
                }
                if (gameList.size() > 0 && currentVersion.name.equals("")) {
                    currentVersion = gameList.get(0);
                    activity.publicGameSetting.currentVersion = activity.launcherSetting.gameFileDirectory + "/versions/" + currentVersion.name;
                    GsonUtils.savePublicGameSetting(activity.publicGameSetting, AppManifest.SETTING_DIR + "/public_game_setting.json");
                }
                versionSpinnerAdapter = new VersionSpinnerAdapter(context,gameList,activity.launcherSetting.gameFileDirectory);
                Spinner gameVersionSpinner = activity.findViewById(R.id.launcher_spinner_version);
                gameVersionSpinner.setAdapter(versionSpinnerAdapter);
                gameVersionSpinner.setSelection(versionSpinnerAdapter.getPosition(currentVersion));
                gameVersionSpinner.setOnItemSelectedListener(this);
                if (!currentVersion.name.equals("")){
                    noVersionAlert.setVisibility(View.GONE);
                    currentVersionText.setVisibility(View.VISIBLE);
                    currentVersionText.setText(currentVersion.name);
                    launchVersionText.setText(currentVersion.name);
                    if (!currentVersion.iconPath.equals("") && new File(currentVersion.iconPath).exists()) {
                        versionIcon.setBackground(DrawableUtils.getDrawableFromFile(currentVersion.iconPath));
                    }
                    else {
                        Integer li = loaderIconFor(new File(activity.launcherSetting.gameFileDirectory
                                + "/versions/" + currentVersion.name));
                        versionIcon.setBackground(context.getDrawable(li != null ? li : R.drawable.ic_grass));
                    }
                }
                else {
                    noVersionAlert.setVisibility(View.VISIBLE);
                    currentVersionText.setVisibility(View.GONE);
                    launchVersionText.setText(context.getString(R.string.launcher_button_current_version));
                    versionIcon.setBackground(context.getDrawable(R.drawable.ic_grass));
                }
            });
        }).start();

        refreshAccount();
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public void refreshAccount() {
        switch (activity.publicGameSetting.account.loginType){
            case 1:
                accountName.setText(activity.publicGameSetting.account.auth_player_name);
                accountType.setText(context.getString(R.string.item_account_type_offline));
                // 离线账号没有 texture（空串会让 setAvatar 裁剪 NPE，头像停留在默认的艾利克斯）。
                // 头像由 applyOfflineSkin 按当前皮肤生成：默认史蒂夫，导入 PNG 则用导入皮肤的脸。
                Avatar.setAvatarFromSkin(Avatar.getBitmapFromRes(context, R.drawable.skin_steve),
                        accountSkinFace, accountSkinHat);
                break;
            case 2:
                accountName.setText(activity.publicGameSetting.account.auth_player_name);
                accountType.setText(context.getString(R.string.item_account_type_mojang));
                Avatar.setAvatar(activity.publicGameSetting.account.texture, accountSkinFace, accountSkinHat);
                break;
            case 3:
                accountName.setText(activity.publicGameSetting.account.auth_player_name);
                accountType.setText(context.getString(R.string.item_account_type_microsoft));
                Avatar.setAvatar(activity.publicGameSetting.account.texture, accountSkinFace, accountSkinHat);
                break;
            case 4:
            case 5:
                accountName.setText(activity.publicGameSetting.account.auth_player_name);
                accountType.setText(getServerFromUrl(activity.publicGameSetting.account.loginServer).getName());
                Avatar.setAvatar(activity.publicGameSetting.account.texture, accountSkinFace, accountSkinHat);
                break;
            default:
                accountName.setText(context.getString(R.string.launcher_scroll_account_name));
                accountType.setText(context.getString(R.string.launcher_scroll_account_state));
                accountSkinFace.setImageBitmap(((BitmapDrawable) context.getDrawable(R.drawable.ic_steve)).getBitmap());
                accountSkinHat.setImageBitmap(null);
                break;
        }
        refreshAccountModel();
    }

    /**
     * ★★★ 社区版：准备主界面的账号形象 —— 现在是**大头像**，不再建 3D 人物。
     *
     * <p><b>为什么彻底不建 GLSurfaceView 了</b>：{@code SkinGLSurfaceView} 是 SurfaceView，
     * 且调过 {@code setZOrderOnTop(true)} —— 独立窗口图层、永远在最上层，
     * 会把页眉 / 标题 / 一排入口按钮整块盖住（真机截图已证实）。
     * 只要它存在一天，主界面就会被它压住一部分，所以这里直接不再创建它。
     *
     * <p>副作用（都是好的）：少一个 GL 上下文、少一份每帧连续渲染、
     * 低端机不会因为创建 GL 失败而走进 catch 分支。
     */
    private void setupAccountModel() {
        // ★★★ 守卫只能用 bigAvatar —— 不能再带 accountModelView 判空。
        //   account_model_view 已随 3D 人物一起从布局删除，findViewById 恒返回 null；
        //   带上它的话这个方法**第一行就直接 return**，bigAvatar 永远找不到 →
        //   **主界面右上角那个大头像根本不显示**（用户真机反馈的正是这个）。
        if (bigAvatar != null) {
            return;
        }
        try {
            bigAvatar = activity.findViewById(R.id.account_avatar_big);
            applyAdaptiveAvatarSize();
        } catch (Throwable t) {
            bigAvatar = null;
        }
    }

    /**
     * ★★★ 社区版：头像尺寸**按屏幕高度自适应**。
     *
     * <p>为什么需要：原来写死 96dp。小屏手机上，页眉 + 入口横排 + 96dp 头像之后，
     * 留给下面两栏的高度可能只剩几十 dp —— 头像本身虽然还在，但整个版面被压得不成样子；
     * 大屏上又显得太小。
     *
     * <p>规则：取屏幕高度的 <b>18%</b>，夹在 <b>56 ~ 120dp</b> 之间。
     * 这样 4 寸小屏大约 60dp、6.7 寸大屏大约 110dp，两头都不会难看。
     *
     * <p>★ 每次 onStart 都会重算，所以横竖屏切换、分屏、折叠屏展开都能跟上。
     */
    private void applyAdaptiveAvatarSize() {
        try {
            if (bigAvatar == null) {
                return;
            }
            android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int hDp = Math.round(dm.heightPixels / dm.density);
            int wantDp = Math.max(56, Math.min(120, Math.round(hDp * 0.18f)));
            int px = Math.round(wantDp * dm.density);
            android.view.ViewGroup.LayoutParams lp = bigAvatar.getLayoutParams();
            if (lp != null && (lp.width != px || lp.height != px)) {
                lp.width = px;
                lp.height = px;
                bigAvatar.setLayoutParams(lp);
            }
        } catch (Throwable ignored) {
        }
    }

    private void refreshAccountModel() {
        if (accountModelContainer == null) {
            return;
        }
        // ★★★ 注意：这里**不能**用 accountModelView 判空提前 return ——
        //   3D 人物换成头像后，account_model_view 已从布局里删除，
        //   findViewById 永远返回 null，那样整段（含**小头像**更新）都会被跳过。
        // ★★★ 社区版：这里原来靠 `skinRenderer` 判空决定要不要继续 ——
        //   但 3D 人物已换成头像，skinRenderer 永远是 null，
        //   会导致**整段**（含下面各分支里对**小头像**的更新）被跳过。
        //   所以改判「大头像视图有没有拿到」。
        setupAccountModel();
        if (bigAvatar == null) {
            return;
        }
        try {
            com.qcl.launcher.auth.Account account = activity.publicGameSetting.account;
            // Fresh install / no account at all -> show nothing.
            if (account == null || account.loginType == 0) {
                hideModel();
                return;
            }

            // Offline accounts follow the skin picked in the offline skin editor, and nothing else.
            if (account.loginType == 1) {
                applyOfflineSkin(account);
                return;
            }

            // Mojang / Microsoft / third-party auth servers: use the skin that came with the account.
            Bitmap skin = Avatar.stringToBitmap(account.texture);
            if (skin == null) {
                hideModel();
                return;
            }
            if (accountModelView != null) {
                accountModelView.setVisibility(View.VISIBLE);
            }
            if (bigAvatar != null) {
                bigAvatar.setVisibility(View.VISIBLE);
            }
            syncBigAvatar();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void applyOfflineSkin(com.qcl.launcher.auth.Account account) {
        com.qcl.launcher.auth.offline.OfflineSkinSetting offline = account.offlineSkinSetting;
        int type = offline == null ? 0 : offline.type;

        switch (type) {
            case 1: {  // Steve
                Bitmap skin = Avatar.getBitmapFromRes(context, R.drawable.skin_steve);
                Avatar.setAvatarFromSkin(skin, accountSkinFace, accountSkinHat);
                showModel(skin, false);
                return;
            }
            case 3: {  // player-uploaded skin file: the avatar follows the imported png
                Bitmap skin = null;
                if (offline.skinPath != null && new File(offline.skinPath).isFile()) {
                    Bitmap uploaded = BitmapFactory.decodeFile(offline.skinPath);
                    if (uploaded != null) {
                        skin = uploaded;
                    }
                }
                if (skin == null) {
                    skin = Avatar.getBitmapFromRes(context, R.drawable.skin_steve);
                }
                Avatar.setAvatarFromSkin(skin, accountSkinFace, accountSkinHat);
                // ★★★ 1.4.1 修复：原写法 `skin.getHeight() != 32` 会把**任意 64x64 的 classic 皮肤**
                //   也当成细手臂（slim）→ 模型与贴图不匹配 → 脖子/腰出现黑色错位条。
                //   改成用 NormalizedSkin 正确判定（老格式一律 classic），并顺带把老格式皮肤
                //   归一化后再交给渲染器，避免帽子层黑块（与皮肤预览、游戏内保持一致）。
                Bitmap renderSkin = skin;
                boolean skinSlim = false;
                try {
                    NormalizedSkin normalized = new NormalizedSkin(skin);
                    skinSlim = normalized.isSlim();
                    renderSkin = normalized.isOldFormat() ? normalized.getNormalizedTexture() : normalized.getOriginalTexture();
                } catch (Throwable ignored) {
                }
                showModel(renderSkin, decodeCape(offline.capePath), skinSlim);
                return;
            }
            case 4:   // LittleSkin
                fetchRemoteSkin("https://mcskin.littleservice.cn/", account.auth_player_name);
                return;
            case 5:   // Blessing Skin server configured by the player
                if (offline != null && offline.server != null && !offline.server.isEmpty()) {
                    String base = offline.server.startsWith("http://")
                            ? offline.server.replace("http://", "https://") : offline.server;
                    fetchRemoteSkin(com.qcl.launcher.utils.string.StringUtils.removeSuffix(base, "/") + "/",
                            account.auth_player_name);
                    return;
                }
                showModel(Avatar.getBitmapFromRes(context, R.drawable.skin_steve), false);
                return;
            case 2:   // Alex
                Bitmap alex = Avatar.getBitmapFromRes(context, R.drawable.skin_alex);
                Avatar.setAvatarFromSkin(alex, accountSkinFace, accountSkinHat);
                showModel(alex, true);
                return;
            case 0:   // default -> Steve（用户要求：离线默认是史蒂夫）
            default:
                Bitmap steve = Avatar.getBitmapFromRes(context, R.drawable.skin_steve);
                Avatar.setAvatarFromSkin(steve, accountSkinFace, accountSkinHat);
                showModel(steve, false);
        }
    }

    /** Resolves <server><name>.json to a texture hash, then loads that texture. */
    private void fetchRemoteSkin(final String base, final String playerName) {
        new Thread(() -> {
            Bitmap skin = null;
            Bitmap cape = null;
            try {
                String json = com.qcl.launcher.utils.io.NetworkUtils.doGet(
                        com.qcl.launcher.utils.io.NetworkUtils.toURL(base + playerName + ".json"));
                com.qcl.launcher.auth.offline.SkinJson result =
                        com.qcl.launcher.utils.gson.JsonUtils.GSON.fromJson(
                                json, com.qcl.launcher.auth.offline.SkinJson.class);
                if (result != null && result.hasSkin() && result.getHash() != null) {
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                            new java.net.URL(base + "textures/" + result.getHash()).openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    skin = BitmapFactory.decodeStream(connection.getInputStream());
                }
                if (result != null && result.getCapeHash() != null) {
                    java.net.HttpURLConnection capeConnection = (java.net.HttpURLConnection)
                            new java.net.URL(base + "textures/" + result.getCapeHash()).openConnection();
                    capeConnection.setDoInput(true);
                    capeConnection.connect();
                    cape = BitmapFactory.decodeStream(capeConnection.getInputStream());
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            final Bitmap loaded = skin;
            final Bitmap loadedCape = cape;
            activity.runOnUiThread(() -> {
                if (loaded != null) {
                    Avatar.setAvatarFromSkin(loaded, accountSkinFace, accountSkinHat);
                    showModel(loaded, loadedCape, true);
                } else {
                    Bitmap fallback = Avatar.getBitmapFromRes(context, R.drawable.skin_steve);
                    Avatar.setAvatarFromSkin(fallback, accountSkinFace, accountSkinHat);
                    showModel(fallback, false);
                }
            });
        }).start();
    }

    /** Only the character is hidden; its container keeps its space so the launch button stays put. */
    /**
     * ★★★ 社区版：隐藏主界面的账号形象。
     * 现在是**大头像**（普通 ImageView），不再是 3D 人物 —— 见 {@link #showModel}。
     */
    private void hideModel() {
        if (accountModelView != null) accountModelView.setVisibility(View.INVISIBLE);
        if (bigAvatar != null) bigAvatar.setVisibility(View.INVISIBLE);
    }

    private void showModel(final Bitmap skin, final boolean slim) {
        showModel(skin, null, slim);
    }

    /**
     * ★★★ 社区版：显示账号形象 —— 已从「3D 人物」改为「**大头像**」。
     *
     * <p><b>为什么换掉 3D 人物</b>：它是 {@code SkinGLSurfaceView}（SurfaceView），
     * 还调过 {@code setZOrderOnTop(true)} —— 那是**独立窗口图层、永远渲染在最上层**，
     * 会把页眉、标题、一排入口按钮整块盖住（真机截图已证实）。
     * 换成普通 ImageView 后这个层级问题**从根上消失**，同时省掉一个 GL 上下文和每帧渲染。
     *
     * <p>★ 这里只切显隐；真正把皮肤画上去的是 {@link #syncBigAvatar()}
     * （它从小头像复制 drawable，保证和左边那个小头像**永远一致**）。
     * 这样无论走哪条分支（离线皮肤 / 正版皮肤 / 兜底 Steve），都不用各自维护一份。
     *
     * @param skin 皮肤位图；为 null 时按「无形象」处理（与原逻辑一致）
     */
    private void showModel(final Bitmap skin, final Bitmap cape, final boolean slim) {
        if (skin == null) {
            hideModel();
            return;
        }
        try {
            if (accountModelView != null) {
                accountModelView.setVisibility(View.VISIBLE);
            }
            if (bigAvatar != null) {
                bigAvatar.setVisibility(View.VISIBLE);
            }
            syncBigAvatar();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * ★★★ 社区版：加**轻量动效**。
     *
     * <p>用户的说法是「动效弄点（不要太多太多反而卡）」—— 所以这里刻意**只做两个**，
     * 而且都是「一个属性动画」级别，不引入任何每帧计算、不新建线程、不碰 GL：
     * <ol>
     *   <li><b>头像缓慢上下浮动</b>：一个 {@link android.animation.ObjectAnimator} 改
     *       {@code translationY}，2.2 秒一个来回。这是整个主界面唯一的常驻动画。</li>
     *   <li><b>启动按钮按下缩放</b>：按下缩到 96%，抬起弹回。只在触摸时跑，
     *       静止时零开销。</li>
     * </ol>
     *
     * <p>★ 为什么不多加：每个常驻动画都会让主线程每帧醒一次。低端机上堆三四个
     * 就会明显掉帧 —— 宁可少而顺，不要多而卡。
     */
    private void startLightAnimations() {
        // ① 头像浮动（常驻，仅此一个）
        try {
            if (bigAvatar != null && avatarAnim == null) {
                float d = activity.getResources().getDisplayMetrics().density;
                avatarAnim = android.animation.ObjectAnimator.ofFloat(
                        bigAvatar, "translationY", 0f, -6f * d);
                avatarAnim.setDuration(2200L);
                avatarAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                avatarAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                avatarAnim.setInterpolator(
                        new android.view.animation.AccelerateDecelerateInterpolator());
                avatarAnim.start();
            }
        } catch (Throwable ignored) {
        }
        // ② 启动按钮按下缩放（只在触摸时跑）
        try {
            final android.view.View btn = activity.findViewById(R.id.launcher_play_button);
            if (btn != null) {
                btn.setOnTouchListener(new android.view.View.OnTouchListener() {
                    @Override
                    public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                        switch (e.getActionMasked()) {
                            case android.view.MotionEvent.ACTION_DOWN:
                                v.animate().scaleX(0.96f).scaleY(0.96f)
                                        .setDuration(90L).start();
                                break;
                            case android.view.MotionEvent.ACTION_UP:
                            case android.view.MotionEvent.ACTION_CANCEL:
                                v.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(150L).start();
                                break;
                            default:
                                break;
                        }
                        // ★ 必须返回 false —— 否则会吃掉点击事件，启动按钮就点不动了
                        return false;
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 把左边小头像的图直接给大头像用。
     * ★ 复用而不是重画：小头像由 {@code Avatar.setAvatar*} 在各种分支里统一维护，
     * 这里跟着它走就不会出现「大头像和小头像不是同一个人」。
     */
    private void syncBigAvatar() {
        try {
            if (bigAvatar == null || accountSkinFace == null) {
                return;
            }
            android.graphics.drawable.Drawable d = accountSkinFace.getDrawable();
            if (d != null) {
                // ★★★ 皮肤是**像素画**。ImageView 默认会做双线性插值 ——
                //   小头像（26dp）放大到 150dp 会被糊成一团色块。
                //   关掉 filterBitmap 走**最近邻**，边缘就是硬的，才有「原边」的观感。
                if (d instanceof android.graphics.drawable.BitmapDrawable) {
                    ((android.graphics.drawable.BitmapDrawable) d).setFilterBitmap(false);
                }
                bigAvatar.setImageDrawable(d);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Bitmap decodeCape(String path) {
        if (path == null) return null;
        File file = new File(path);
        return file.isFile() ? BitmapFactory.decodeFile(path) : null;
    }

    @Override
    public void onStop() {
        super.onStop();
        // ★ 1.3.7 修复：切到其他页面时，3D 人物（GLSurfaceView）只 onPause 不够 ——
        //   部分手机上会把画面残留在上层，遮住设置/下载/版本列表界面。这里彻底隐藏。
        // ★★★ 社区版：停掉头像浮动动画。
        //   它是 INFINITE 的 —— 不停的话，用户切到设置/下载页之后它还在跑，
        //   主线程每帧醒一次，纯属白耗电。离开主界面就停，回来时 onStart 会重启。
        //   ★ 顺手把 translationY 复位，否则再回来时头像会停在一个偏移位置上。
        try {
            if (avatarAnim != null) {
                avatarAnim.cancel();
                avatarAnim = null;
            }
            if (bigAvatar != null) {
                bigAvatar.setTranslationY(0f);
            }
        } catch (Throwable ignored) {
        }
        if (skinGLSurfaceView != null) {
            skinGLSurfaceView.onPause();
            skinGLSurfaceView.setVisibility(View.GONE);
        }
        if (accountModelView != null) {
            accountModelView.setVisibility(View.GONE);
        }
        CustomAnimationUtils.hideViewToLeft(mainUI,activity,context,true);
    }

    @Override
    public void onClick(View v) {
        if (v == startAccountUI){
            activity.uiManager.switchMainUI(activity.uiManager.accountUI);
        }
        if (v == startGameManagerUI){
            if (noVersionAlert.getVisibility() == View.VISIBLE){
                activity.uiManager.switchMainUI(activity.uiManager.versionListUI);
            }
            else {
                activity.uiManager.gameManagerUI.versionName = activity.publicGameSetting.currentVersion.substring(activity.publicGameSetting.currentVersion.lastIndexOf("/") + 1);
                activity.uiManager.switchMainUI(activity.uiManager.gameManagerUI);
            }
        }
        if (v == startVersionListUI){
            activity.uiManager.switchMainUI(activity.uiManager.versionListUI);
        }
        if (v == startDownloadUI){
            activity.uiManager.switchMainUI(activity.uiManager.downloadUI);
        }
        if (v == startMultiPlayerUI){
            com.qcl.launcher.launcher.terracotta.MultiplayerDialogHelper.showEnable(activity, context);
        }
        if (v == startSettingUI){
            activity.uiManager.switchMainUI(activity.uiManager.settingUI);
        }
        // ★ 1.4.1 新增：大厅 / 实验室
        if (v == startLobbyUI){
            activity.uiManager.switchMainUI(activity.uiManager.lobbyUI);
        }
        if (v == startLabUI){
            activity.uiManager.switchMainUI(activity.uiManager.labUI);
        }
        if (v == startGame){
            String settingPath = activity.publicGameSetting.currentVersion + "/qcl.cfg";
            String finalPath;
            if (new File(settingPath).exists() && GsonUtils.getPrivateGameSettingFromFile(settingPath) != null && (GsonUtils.getPrivateGameSettingFromFile(settingPath).forceEnable || GsonUtils.getPrivateGameSettingFromFile(settingPath).enable)) {
                finalPath = settingPath;
            }
            else {
                finalPath = AppManifest.SETTING_DIR + "/private_game_setting.json";
            }
            Bundle bundle = new Bundle();
            bundle.putString("setting_path",finalPath);
            bundle.putBoolean("test",false);
            LaunchTools.launch(context,activity,activity.publicGameSetting.currentVersion,bundle);
        }
    }

    @SuppressLint({"SetTextI18n", "UseCompatLoadingForDrawables"})
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        activity.publicGameSetting.currentVersion = activity.launcherSetting.gameFileDirectory + "/versions/" + ((GameListBean) versionSpinnerAdapter.getItem(position)).name;
        if (activity.privateGameSetting.gameDirSetting.type == 1){
            activity.uiManager.settingUI.settingUIManager.universalGameSettingUI.gameDirText.setText(activity.launcherSetting.gameFileDirectory + "/versions/" + ((GameListBean) versionSpinnerAdapter.getItem(position)).name);
        }
        GsonUtils.savePublicGameSetting(activity.publicGameSetting, AppManifest.SETTING_DIR + "/public_game_setting.json");
        currentVersionText.setText(((GameListBean) versionSpinnerAdapter.getItem(position)).name);
        launchVersionText.setText(((GameListBean) versionSpinnerAdapter.getItem(position)).name);
        if (!((GameListBean) versionSpinnerAdapter.getItem(position)).iconPath.equals("") && new File(((GameListBean) versionSpinnerAdapter.getItem(position)).iconPath).exists()) {
            versionIcon.setBackground(DrawableUtils.getDrawableFromFile(((GameListBean) versionSpinnerAdapter.getItem(position)).iconPath));
        }
        else {
            // ★★★ 1.2.5 修：这里原来跟 VersionSpinnerAdapter 一样按「version 有没有逗号」
            //   分叉，带逗号（下载页装的 Fabric/Forge）显示 ic_furnace（熔炉），
            //   和版本设置页的加载器 logo 不一致。统一走 loaderIconFor。
            Integer li = loaderIconFor(new File(activity.launcherSetting.gameFileDirectory
                    + "/versions/" + ((GameListBean) versionSpinnerAdapter.getItem(position)).name));
            versionIcon.setBackground(context.getDrawable(li != null ? li : R.drawable.ic_grass));
        }
        changeIcon(versionIcon,themePath,"versionIcon");
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
    File themePath;
    public void customTheme(){
        themePath = activity.getExternalFilesDir("Theme");
        // ★★★ 社区版修复（续 6）：getExternalFilesDir() 在外部存储不可用时**返回 null**
        //   （未挂载 / 被电脑占用 / 部分 ROM 的异常状态），而这里原来紧接着就调
        //   themePath.exists() → NPE。
        //   这条链是 MainActivity.onLoad() → customTheme()，**正处在启动路径上**：
        //   改造前 onLoad() 外面没有任何 try/catch，一旦触发就是未捕获异常 →
        //   全局处理器弹崩溃页并杀进程 → 对外表现正是「打开直接闪退」。
        //   自定义主题本来就是个可选功能，取不到目录就当用户没自定义，直接返回。
        if (themePath == null) {
            return;
        }
        if (!themePath.exists()){
            return;
        }
        changeIcon(versionListIcon, themePath, "versionListIcon");
        changeIcon(downloadIcon, themePath, "downloadIcon");
        changeIcon(multiplayerIcon, themePath, "multiplayerIcon");
        changeIcon(settingIcon, themePath, "settingIcon");
        changeIcon(activity.launcherLayout, themePath, "background");
        changeIcon(versionIcon, themePath, "versionIcon");
        if (new File(themePath,"color.json").exists()) {
            try {
                JSONObject jsonObject = new JSONObject(FileUtils.readText(new File(themePath,"color.json")));
                activity.exteriorConfig.primaryColor(Color.parseColor(jsonObject.getString("primaryColor")));
                activity.exteriorConfig.accentColor(Color.parseColor(jsonObject.getString("accentColor")));
                activity.exteriorConfig.apply(activity);
                // 1.0.6：顶部标题栏已移除，主题色不再需要刷到 appBar。
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(activity, e.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void changeIcon(View view, File themePath, String iconName) {
        // ★ 社区版加固：themePath 是字段，onItemSelected 那条路径上也可能为 null；
        //   new File((File)null, "x.png") 本身不抛异常，但会解析成当前工作目录下的相对路径 ——
        //   于是 path.exists() 变成一次无意义的探测。判空后语义更清楚，也省掉一次 IO。
        if (view == null || themePath == null) {
            return;
        }
        File path = new File(themePath, iconName + ".png");
        if (path.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(path.getAbsolutePath());
            view.setBackground(new BitmapDrawable(activity.getResources(), bitmap));
        }
    }
}
