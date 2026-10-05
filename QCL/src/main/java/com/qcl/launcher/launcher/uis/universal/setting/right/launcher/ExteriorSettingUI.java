/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.app.Activity
 *  android.content.Context
 *  android.content.Intent
 *  android.graphics.Bitmap
 *  android.graphics.BitmapFactory
 *  android.graphics.BitmapFactory$Options
 *  android.graphics.Color
 *  android.graphics.drawable.BitmapDrawable
 *  android.graphics.drawable.Drawable
 *  android.net.Uri
 *  android.os.Build$VERSION
 *  android.os.Environment
 *  android.os.Handler
 *  android.os.Message
 *  android.text.Editable
 *  android.text.TextWatcher
 *  android.view.View
 *  android.view.View$OnClickListener
 *  android.view.ViewGroup
 *  android.widget.CompoundButton
 *  android.widget.CompoundButton$OnCheckedChangeListener
 *  android.widget.EditText
 *  android.widget.ImageButton
 *  android.widget.LinearLayout
 *  android.widget.RadioButton
 *  android.widget.TextView
 *  androidx.annotation.NonNull
 *  androidx.appcompat.widget.SwitchCompat
 *  com.tungsten.filepicker.Constants$SELECTION_MODES
 *  com.tungsten.filepicker.FileChooser
 */
package com.qcl.launcher.launcher.uis.universal.setting.right.launcher;

import com.qcl.launcher.utils.QclColors;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.dialogs.tools.ColorSelectorDialog;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.launcher.uis.tools.QclThemeUtils;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;
import com.qcl.launcher.utils.file.UriUtils;
import com.qcl.launcher.utils.gson.GsonUtils;
import com.tungsten.filepicker.Constants;
import com.tungsten.filepicker.FileChooser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.qcl.launcher.R;
public class ExteriorSettingUI
extends BaseUI
implements View.OnClickListener,
CompoundButton.OnCheckedChangeListener {
    private static final int PICK_BACKGROUND_REQUEST = 4000;
    public LinearLayout exteriorSettingUI;
    private LinearLayout selectTheme;
    private View colorView;
    private TextView colorText;
    private LinearLayout selectPanelColor;
    private View panelColorView;
    private TextView panelColorText;
    // ★ 社区版删除：transBarSwitch —— 对应的「标题栏透明」开关已随顶部标题栏一起移除
    //   （它的处理函数里只剩空 if 块，拨动无效）。
    private SwitchCompat fullscreenSwitch;
    private SwitchCompat grassUiSwitch;
    private SwitchCompat showAccountModelSwitch;
    private SwitchCompat transBgSwitch;
    private LinearLayout fullscreenSetting;
    private RadioButton defaultRadio;
    private RadioButton classicRadio;
    private RadioButton customRadio;
    private RadioButton onlineRadio;
    private EditText editBgPath;
    private EditText editBgUrl;
    private ImageButton selectBgPath;
    /** ★ 社区版新增：主题方案（一键预设 + 导出/导入） */
    private TextView themePresetDefault;
    private TextView themePresetDark;
    private TextView themePresetAmoled;
    private TextView themeExport;
    private TextView themeImport;

    /** ★ 社区版新增：安全区避让（刘海 / 挖孔 / 大圆角） */
    private LinearLayout safeAreaLayout;
    private TextView safeAreaValue;
    private TextView safeAreaHint;
    @SuppressLint(value={"HandlerLeak"})
    public final Handler handler = new Handler(){

        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
        }
    };

    public ExteriorSettingUI(Context context, MainActivity activity) {
        super(context, activity);
    }

    @Override
    @SuppressLint(value={"UseCompatLoadingForDrawables"})
    public void onCreate() {
        super.onCreate();
        this.exteriorSettingUI = (LinearLayout)this.activity.findViewById(R.id.ui_setting_exterior);
        this.selectTheme = (LinearLayout)this.activity.findViewById(R.id.select_theme);
        this.colorView = this.activity.findViewById(R.id.theme_color_view);
        this.colorText = (TextView)this.activity.findViewById(R.id.theme_color_text);
        this.selectPanelColor = (LinearLayout)this.activity.findViewById(R.id.select_panel_color);
        this.panelColorView = this.activity.findViewById(R.id.panel_color_view);
        this.panelColorText = (TextView)this.activity.findViewById(R.id.panel_color_text);
        this.fullscreenSwitch = (SwitchCompat)this.activity.findViewById(R.id.switch_full_screen);
        this.grassUiSwitch = (SwitchCompat)this.activity.findViewById(R.id.switch_grass_ui);
        this.showAccountModelSwitch = (SwitchCompat)this.activity.findViewById(R.id.switch_show_account_model);
        this.transBgSwitch = (SwitchCompat)this.activity.findViewById(R.id.switch_transparent_bg);
        this.fullscreenSetting = (LinearLayout)this.activity.findViewById(R.id.fullscreen_layout);
        this.defaultRadio = (RadioButton)this.activity.findViewById(R.id.select_bg_default);
        this.classicRadio = (RadioButton)this.activity.findViewById(R.id.select_bg_classic);
        this.customRadio = (RadioButton)this.activity.findViewById(R.id.select_bg_custom);
        this.onlineRadio = (RadioButton)this.activity.findViewById(R.id.select_bg_online);
        this.editBgPath = (EditText)this.activity.findViewById(R.id.edit_bg_path);
        this.editBgUrl = (EditText)this.activity.findViewById(R.id.edit_bg_url);
        this.selectBgPath = (ImageButton)this.activity.findViewById(R.id.select_bg_path);
        // ★ 社区版新增：主题方案（预设 / 导出 / 导入）
        this.themePresetDefault = (TextView)this.activity.findViewById(R.id.theme_preset_default);
        this.themePresetDark = (TextView)this.activity.findViewById(R.id.theme_preset_dark);
        this.themePresetAmoled = (TextView)this.activity.findViewById(R.id.theme_preset_amoled);
        this.themeExport = (TextView)this.activity.findViewById(R.id.theme_export);
        this.themeImport = (TextView)this.activity.findViewById(R.id.theme_import);
        for (TextView tv : new TextView[]{this.themePresetDefault, this.themePresetDark,
                this.themePresetAmoled, this.themeExport, this.themeImport}) {
            if (tv != null) {
                tv.setOnClickListener((View.OnClickListener)this);
            }
        }
        // ★ 社区版新增：安全区避让（刘海 / 挖孔 / 大圆角）
        this.safeAreaLayout = (LinearLayout)this.activity.findViewById(R.id.safe_area_layout);
        this.safeAreaValue = (TextView)this.activity.findViewById(R.id.safe_area_value);
        this.safeAreaHint = (TextView)this.activity.findViewById(R.id.safe_area_hint);
        if (this.safeAreaLayout != null) {
            this.safeAreaLayout.setOnClickListener((View.OnClickListener)this);
        }
        // ★ 1.3.4：type 0（自动切换/动态背景）改由「网络」选项承载；type 1（经典图片）成为「默认」
        if (this.activity.launcherSetting.launcherBackground.type == 0) {
            // 动态背景（自动切换）→ 选中「网络」
            this.onlineRadio.setChecked(true);
            this.editBgPath.setEnabled(false);
            this.editBgUrl.setEnabled(false);
            this.selectBgPath.setEnabled(false);
        } else if (this.activity.launcherSetting.launcherBackground.type == 1) {
            // 经典图片 → 选中「默认」
            this.defaultRadio.setChecked(true);
            this.editBgPath.setEnabled(false);
            this.editBgUrl.setEnabled(false);
            this.selectBgPath.setEnabled(false);
            this.activity.launcherLayout.setBackground(this.context.getDrawable(R.drawable.ic_background_classic));
        } else if (this.activity.launcherSetting.launcherBackground.type == 2) {
            this.customRadio.setChecked(true);
            this.editBgPath.setEnabled(true);
            this.editBgUrl.setEnabled(false);
            this.selectBgPath.setEnabled(true);
            if (new File(this.activity.launcherSetting.launcherBackground.path).exists() && ExteriorSettingUI.isImageFile(this.activity.launcherSetting.launcherBackground.path)) {
                Bitmap bitmap = BitmapFactory.decodeFile((String)this.activity.launcherSetting.launcherBackground.path);
                this.activity.launcherLayout.setBackground((Drawable)new BitmapDrawable(bitmap));
            } else {
                this.activity.launcherLayout.setBackground(this.context.getDrawable(R.drawable.qcl_bg_1));
            }
        } else {
            // ★ 1.3.4：type 3（旧「单张网络图」）与 type 0（动态背景）统一按「网络 = 自动切换」处理
            //   旧用户升级后：选中「网络」，由 DynamicBackground 接管轮播
            this.onlineRadio.setChecked(true);
            this.editBgPath.setEnabled(false);
            this.editBgUrl.setEnabled(false);
            this.selectBgPath.setEnabled(false);
            if (this.activity.launcherSetting.launcherBackground.type == 3) {
                this.activity.launcherSetting.launcherBackground.type = 0;
                this.activity.refreshDynamicBackground();
            }
        }
        this.editBgPath.setText((CharSequence)this.activity.launcherSetting.launcherBackground.path);
        this.editBgUrl.setText((CharSequence)this.activity.launcherSetting.launcherBackground.url);
        this.selectTheme.setOnClickListener((View.OnClickListener)this);
        this.selectPanelColor.setOnClickListener((View.OnClickListener)this);
        this.fullscreenSwitch.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener)this);
        if (this.grassUiSwitch != null) {
            this.grassUiSwitch.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener)this);
        }
        if (this.showAccountModelSwitch != null) {
            this.showAccountModelSwitch.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener)this);
        }
        if (this.transBgSwitch != null) {
            this.transBgSwitch.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener)this);
        }
        this.defaultRadio.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener)this);
        this.classicRadio.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener)this);
        this.customRadio.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener)this);
        this.onlineRadio.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener)this);
        this.selectBgPath.setOnClickListener((View.OnClickListener)this);
        this.editBgPath.addTextChangedListener(new TextWatcher(){

            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            public void afterTextChanged(Editable editable) {
                ExteriorSettingUI.this.activity.launcherSetting.launcherBackground.path = ExteriorSettingUI.this.editBgPath.getText().toString();
                GsonUtils.saveLauncherSetting(ExteriorSettingUI.this.activity.launcherSetting, AppManifest.SETTING_DIR + "/launcher_setting.json");
                if (new File(ExteriorSettingUI.this.editBgPath.getText().toString()).exists() && ExteriorSettingUI.isImageFile(ExteriorSettingUI.this.editBgPath.getText().toString())) {
                    Bitmap bitmap = BitmapFactory.decodeFile((String)ExteriorSettingUI.this.editBgPath.getText().toString());
                    ExteriorSettingUI.this.activity.launcherLayout.setBackground((Drawable)new BitmapDrawable(bitmap));
                } else {
                    ExteriorSettingUI.this.activity.launcherLayout.setBackground(ExteriorSettingUI.this.context.getDrawable(R.drawable.qcl_bg_1));
                }
            }
        });
        this.editBgUrl.addTextChangedListener(new TextWatcher(){

            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            public void afterTextChanged(Editable editable) {
                ExteriorSettingUI.this.activity.launcherSetting.launcherBackground.url = ExteriorSettingUI.this.editBgUrl.getText().toString();
                GsonUtils.saveLauncherSetting(ExteriorSettingUI.this.activity.launcherSetting, AppManifest.SETTING_DIR + "/launcher_setting.json");
                new Thread(() -> {
                    try {
                        URL url = new URL(ExteriorSettingUI.this.editBgUrl.getText().toString());
                        HttpURLConnection httpURLConnection = (HttpURLConnection)url.openConnection();
                        httpURLConnection.setDoInput(true);
                        httpURLConnection.connect();
                        InputStream inputStream = httpURLConnection.getInputStream();
                        Bitmap bitmap = BitmapFactory.decodeStream((InputStream)inputStream);
                        ExteriorSettingUI.this.handler.post(() -> ExteriorSettingUI.this.activity.launcherLayout.setBackground((Drawable)new BitmapDrawable(bitmap)));
                    }
                    catch (IOException e) {
                        ExteriorSettingUI.this.handler.post(() -> ExteriorSettingUI.this.activity.launcherLayout.setBackground(ExteriorSettingUI.this.context.getDrawable(R.drawable.qcl_bg_1)));
                        e.printStackTrace();
                    }
                }).start();
            }
        });
        this.fullscreenSwitch.setChecked(this.activity.launcherSetting.fullscreen);
        if (this.showAccountModelSwitch != null) {
            this.showAccountModelSwitch.setChecked(this.activity.launcherSetting.showAccountModel);
        }
        if (this.grassUiSwitch != null) {
            this.grassUiSwitch.setChecked(this.activity.launcherSetting.uiTheme == 1);
            if (this.transBgSwitch != null) {
                this.transBgSwitch.setChecked(this.activity.launcherSetting.transparentBackground);
            }
        }
        this.refreshColorEditable();
        if (Build.VERSION.SDK_INT < 28) {
            this.fullscreenSetting.setVisibility(8);
        }
    }

    private void refreshColorEditable() {
        boolean grass = this.activity.launcherSetting.uiTheme == 1;
        this.setEnabledRecursive((View)this.selectTheme, !grass);
        this.setEnabledRecursive((View)this.selectPanelColor, !grass);
        if (this.colorView != null) {
            this.colorView.setAlpha(grass ? 0.35f : 1.0f);
        }
        if (this.panelColorView != null) {
            this.panelColorView.setAlpha(grass ? 0.35f : 1.0f);
        }
        if (this.colorText != null) {
            this.colorText.setAlpha(grass ? 0.45f : 1.0f);
        }
        if (this.panelColorText != null) {
            this.panelColorText.setAlpha(grass ? 0.45f : 1.0f);
        }
    }

    private void setEnabledRecursive(View v, boolean enabled) {
        if (v == null) {
            return;
        }
        v.setEnabled(enabled);
        v.setClickable(enabled);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)v;
            for (int i = 0; i < g.getChildCount(); ++i) {
                g.getChildAt(i).setEnabled(enabled);
            }
        }
    }

    @Override
    @SuppressLint(value={"UseCompatLoadingForDrawables"})
    public void onStart() {
        super.onStart();
        CustomAnimationUtils.showViewFromLeft((View)this.exteriorSettingUI, this.activity, this.context, false);
        if (this.activity.isLoaded) {
            this.activity.uiManager.settingUI.startExteriorSettingUI.setBackground(this.context.getResources().getDrawable(R.drawable.launcher_button_white));
        }
        this.colorView.setBackgroundColor(ExteriorSettingUI.parseThemeColorSafe(this.context, this.activity.launcherSetting.launcherTheme));
        this.colorText.setText((CharSequence)ExteriorSettingUI.getThemeColor(this.context, this.activity.launcherSetting.launcherTheme));
        int pc = ExteriorSettingUI.getPanelColor(this.context, this.activity.launcherSetting.panelColor);
        this.panelColorView.setBackgroundColor(pc);
        this.panelColorText.setText((CharSequence)(QclColors.format(pc)));
        // ★ 社区版新增：安全区那一行显示「当前模式 + 实际检测到的避让量」
        refreshSafeArea();
    }

    @Override
    @SuppressLint(value={"UseCompatLoadingForDrawables"})
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft((View)this.exteriorSettingUI, this.activity, this.context, false);
        if (this.activity.isLoaded) {
            this.activity.uiManager.settingUI.startExteriorSettingUI.setBackground(this.context.getResources().getDrawable(R.drawable.launcher_button_parent));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 4000 && data != null && resultCode == -1) {
            Uri uri = data.getData();
            this.editBgPath.setText((CharSequence)UriUtils.getRealPathFromUri_AboveApi19(this.context, uri));
        }
    }

    public static int getPanelColor(Context context, String color2) {
        if (color2 == null || color2.equals("DEFAULT") || color2.isEmpty()) {
            return context.getResources().getColor(R.color.qcl_panel_gray_alt);
        }
        try {
            return QclColors.parseSafe(color2, 0xFFFFFFFF);
        }
        catch (Throwable t) {
            return context.getResources().getColor(R.color.qcl_panel_gray_alt);
        }
    }

    /**
     * 面板类 drawable —— 改「面板色」时要跟着变色的那些。
     *
     * <p>★ 原来的名单里**只有** {@code launcher_view_white} / {@code launcher_view_light_gray}
     * （都是浅灰的实心 shape）。而主界面那排入口按钮（{@code qcl_button_gray}，59 处）
     * 和设置页的按钮（{@code launcher_button_parent}，184 处）**常态是全透明的**
     * （<solid android:color="#00000000"/> / @color/colorTransparent，为的是露出背景图）——
     * 它们不在名单里，于是「面板色」在主界面怎么改都没反应。这就是用户报的那个 bug。
     */
    private static final int[] PANEL_DRAWABLES = {
            R.drawable.launcher_view_white,
            R.drawable.launcher_view_light_gray,
            R.drawable.qcl_button_gray,
            R.drawable.launcher_button_parent,
    };

    /**
     * ★ 按「设置里的原始字符串」上色 —— 这是推荐的入口。
     *
     * <p>传 null / 空 / {@code "DEFAULT"} 表示**透明**：清掉之前的着色，让面板恢复成
     * 设计时的透明形态，背景图直接透出来（这就是「背景默认透明」）。
     * 传具体色值则把面板涂成**纯色**。
     */
    public static void applyPanelTint(Context context, View root, String panelColorSetting) {
        boolean transparent = panelColorSetting == null
                || panelColorSetting.isEmpty()
                || "DEFAULT".equals(panelColorSetting);
        applyPanelTint(context, root, transparent ? 0 : getPanelColor(context, panelColorSetting), transparent);
    }

    /** 兼容旧签名：等价于「纯色模式」。 */
    public static void applyPanelTint(Context context, View root, int color2) {
        applyPanelTint(context, root, color2, false);
    }

    public static void applyPanelTint(Context context, View root, int color2, boolean transparent) {
        Drawable bg;
        if (root == null) {
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)root;
            for (int i = 0; i < g.getChildCount(); ++i) {
                ExteriorSettingUI.applyPanelTint(context, g.getChildAt(i), color2, transparent);
            }
        }
        if ((bg = root.getBackground()) == null || bg.getConstantState() == null) {
            return;
        }
        for (int id2 : PANEL_DRAWABLES) {
            Drawable ref;
            try {
                ref = context.getResources().getDrawable(id2);
            } catch (Throwable t) {
                continue;
            }
            if (ref == null || ref.getConstantState() == null
                    || !ref.getConstantState().equals(bg.getConstantState())) {
                continue;
            }
            Drawable d = bg.mutate();
            if (transparent) {
                // 透明模式：清掉着色，回到「露出背景图」的原始形态
                d.setTintList(null);
                break;
            }
            // 纯色模式。
            // ★★★ 这里必须用 SRC_OVER —— 这些面板的常态是 #00000000（alpha=0），
            //   而 setTint 的默认模式是 SRC_IN：它**保留源像素的 alpha**，
            //   所以给一张全透明的图着色，结果仍然是全透明，看着就像「改了没反应」。
            //   SRC_OVER 是把颜色**盖**上去，透明面板才会真的被涂成纯色。
            // 顺带用 ColorStateList 保住按压反馈（按下时提亮一点，而不是整个消失）。
            int pressed = blend(color2, 0x33FFFFFF);
            d.setTintList(new android.content.res.ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                    new int[]{pressed, color2}));
            d.setTintMode(android.graphics.PorterDuff.Mode.SRC_OVER);
            break;
        }
    }

    /** 把 over 以自身 alpha 叠在 base 上（用于生成按压态颜色）。 */
    private static int blend(int base, int over) {
        try {
            float a = ((over >>> 24) & 0xFF) / 255f;
            int r = Math.round(((base >>> 16) & 0xFF) * (1 - a) + ((over >>> 16) & 0xFF) * a);
            int g = Math.round(((base >>> 8) & 0xFF) * (1 - a) + ((over >>> 8) & 0xFF) * a);
            int b = Math.round((base & 0xFF) * (1 - a) + (over & 0xFF) * a);
            return (0xFF << 24) | (r << 16) | (g << 8) | b;
        } catch (Throwable t) {
            return base;
        }
    }

    /**
     * 主题色字符串 —— 读取侧已加固：null / 空 / DEFAULT / 历史非法值（如未补零的 "#ff"）
     * 一律回退为合法的默认强调色串，绝不再把非法值抛给 Color.parseColor。
     * （真机 OPPO PDVM00 曾因存档里 "#ff" 这类值在启动期 parseColor 崩溃。）
     */
    public static String getThemeColor(Context context, String color2) {
        int fallback = context.getColor(R.color.colorAccent);
        if (color2 == null || "DEFAULT".equals(color2) || color2.trim().isEmpty()) {
            return QclColors.format(fallback);
        }
        String s = color2.trim();
        try {
            Color.parseColor(s);
            return s;
        } catch (Throwable t) {
            android.util.Log.w("jrelog", "[颜色] 主题色非法值已回退: " + s);
            return QclColors.format(fallback);
        }
    }

    /** 主题色 → int，安全解析（启动期着色用，绝不抛异常）。 */
    public static int parseThemeColorSafe(Context context, String color2) {
        return QclColors.parseSafeTheme(context, getThemeColor(context, color2), R.color.colorAccent);
    }

    public static boolean isImageFile(String filePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile((String)filePath, (BitmapFactory.Options)options);
        return options.outWidth != -1;
    }

    // ------------------------------------------------------------------ 主题方案

    /**
     * ★★★ 社区版新增：一键套用主题方案。
     *
     * <p><b>为什么只是「把两个色值设好」而不是新机制</b>：主题色与面板色本来就能任选
     * （上面两行就是 ColorSelectorDialog）。预设的价值是<b>省掉「调出一套好看的搭配」这件事</b>，
     * 而不是引入第二套主题系统 —— 那会和现有设置打架。
     *
     * @param which 0 = 默认灰；1 = 深色；2 = 纯黑（AMOLED，OLED 屏省电且黑得干净）
     */
    private void applyThemePreset(int which) {
        try {
            String theme;
            String panel;
            String name;
            if (which == 1) {
                theme = "#FF455A64";
                panel = "#FF263238";
                name = this.context.getString(R.string.exterior_theme_preset_dark);
            } else if (which == 2) {
                theme = "#FF000000";
                panel = "#FF000000";
                name = this.context.getString(R.string.exterior_theme_preset_amoled);
            } else {
                theme = "#FF6E6E6E";
                panel = "DEFAULT";
                name = this.context.getString(R.string.exterior_theme_preset_default);
            }
            this.activity.launcherSetting.launcherTheme = theme;
            this.activity.launcherSetting.panelColor = panel;
            GsonUtils.saveLauncherSetting(this.activity.launcherSetting,
                    AppManifest.SETTING_DIR + "/launcher_setting.json");

            int color = parseThemeColorSafe(this.context, theme);
            this.activity.exteriorConfig.primaryColor(color);
            this.activity.exteriorConfig.accentColor(color);
            this.activity.exteriorConfig.apply((Activity)this.activity);

            if (this.colorView != null) {
                this.colorView.setBackgroundColor(color);
            }
            if (this.colorText != null) {
                this.colorText.setText((CharSequence)QclColors.format(color));
            }
            int pc = getPanelColor(this.context, panel);
            if (this.panelColorView != null) {
                this.panelColorView.setBackgroundColor(pc);
            }
            if (this.panelColorText != null) {
                this.panelColorText.setText((CharSequence)QclColors.format(pc));
            }
            // ★ 传原始字符串而不是算好的颜色值 —— 否则「DEFAULT（透明）」的语义会丢掉。
            applyPanelTint(this.context, (View)this.exteriorSettingUI, panel);
            applyPanelTint(this.context, this.activity.getWindow().getDecorView(), panel);

            Toast.makeText(this.context,
                    this.context.getString(R.string.exterior_theme_applied, name),
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    /** 当前主题的可分享文本。格式刻意做成人类可读，方便在聊天里直接看懂。 */
    private String currentThemeString() {
        String theme = getThemeColor(this.context, this.activity.launcherSetting.launcherTheme);
        String panel = this.activity.launcherSetting.panelColor;
        return "theme=" + theme
                + ";panel=" + (panel == null ? "DEFAULT" : panel)
                + ";transBg=" + (this.activity.launcherSetting.transparentBackground ? "1" : "0");
    }

    private void exportTheme() {
        final String s = currentThemeString();
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    this.context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("QCL theme", s));
            }
        } catch (Throwable ignored) {
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, s);
            this.activity.startActivity(Intent.createChooser(intent,
                    this.context.getString(R.string.exterior_theme_export)));
        } catch (Throwable t) {
            Toast.makeText(this.context, R.string.exterior_theme_export_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void importTheme() {
        try {
            final EditText input = new EditText(this.context);
            input.setHint(R.string.exterior_theme_import_hint);
            input.setMinLines(2);
            new android.app.AlertDialog.Builder(this.activity)
                    .setTitle(R.string.exterior_theme_import)
                    .setView(input)
                    .setPositiveButton(R.string.exterior_theme_import,
                            new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    String text = input.getText() == null ? "" : input.getText().toString();
                                    applyThemeString(text);
                                }
                            })
                    .setNegativeButton(R.string.legacy_cn_lang_cancel, null)
                    .create().show();
        } catch (Throwable ignored) {
        }
    }

    /** 解析并套用 {@code theme=…;panel=…;transBg=0/1}。任何一段不合法就整段拒绝。 */
    private void applyThemeString(String text) {
        try {
            if (text == null || !text.contains("theme=")) {
                Toast.makeText(this.context, R.string.exterior_theme_import_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            String theme = null;
            String panel = null;
            Boolean transBg = null;
            for (String part : text.split(";")) {
                String p = part.trim();
                if (p.startsWith("theme=")) {
                    theme = p.substring(6).trim();
                } else if (p.startsWith("panel=")) {
                    panel = p.substring(6).trim();
                } else if (p.startsWith("transBg=")) {
                    transBg = "1".equals(p.substring(8).trim());
                }
            }
            if (theme == null) {
                Toast.makeText(this.context, R.string.exterior_theme_import_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            // 先验证颜色合法，再落盘 —— 避免把非法值写进设置（历史上非法值会在启动期崩）
            try {
                Color.parseColor(theme);
                if (panel != null && !"DEFAULT".equals(panel)) {
                    Color.parseColor(panel);
                }
            } catch (Throwable t) {
                Toast.makeText(this.context, R.string.exterior_theme_import_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            this.activity.launcherSetting.launcherTheme = theme;
            if (panel != null) {
                this.activity.launcherSetting.panelColor = panel;
            }
            if (transBg != null) {
                this.activity.launcherSetting.transparentBackground = transBg;
                View container = this.activity.findViewById(R.id.main_ui_container);
                if (container != null) {
                    container.setBackgroundColor(transBg ? android.graphics.Color.TRANSPARENT
                            : android.graphics.Color.parseColor("#C8EDEDED"));
                }
                if (this.transBgSwitch != null) {
                    this.transBgSwitch.setChecked(transBg);
                }
            }
            GsonUtils.saveLauncherSetting(this.activity.launcherSetting,
                    AppManifest.SETTING_DIR + "/launcher_setting.json");

            int color = parseThemeColorSafe(this.context, theme);
            this.activity.exteriorConfig.primaryColor(color);
            this.activity.exteriorConfig.accentColor(color);
            this.activity.exteriorConfig.apply((Activity)this.activity);
            if (this.colorView != null) {
                this.colorView.setBackgroundColor(color);
            }
            if (this.colorText != null) {
                this.colorText.setText((CharSequence)QclColors.format(color));
            }
            int pc = getPanelColor(this.context, this.activity.launcherSetting.panelColor);
            if (this.panelColorView != null) {
                this.panelColorView.setBackgroundColor(pc);
            }
            if (this.panelColorText != null) {
                this.panelColorText.setText((CharSequence)QclColors.format(pc));
            }
            // ★ 传原始字符串而不是算好的颜色值 —— 否则「DEFAULT（透明）」的语义会丢掉。
            applyPanelTint(this.context, (View)this.exteriorSettingUI, this.activity.launcherSetting.panelColor);
            applyPanelTint(this.context, this.activity.getWindow().getDecorView(), this.activity.launcherSetting.panelColor);

            Toast.makeText(this.context, R.string.exterior_theme_import_done, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this.context, R.string.exterior_theme_import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * ★★★ 社区版新增：安全区避让模式选择。
     *
     * <p>改完<b>立刻 requestApplyInsets</b> 让用户当场看到效果 —— 这类「避让多少」
     * 的设置，看不到变化就等于没设置。
     */
    private void showSafeAreaDialog() {
        try {
            final String[] items = {
                    this.context.getString(R.string.safe_area_mode_auto),
                    this.context.getString(R.string.safe_area_mode_on),
                    this.context.getString(R.string.safe_area_mode_off),
            };
            int cur = this.activity.launcherSetting == null ? 0 : this.activity.launcherSetting.safeAreaMode;
            if (cur < 0 || cur > 2) {
                cur = 0;
            }
            new android.app.AlertDialog.Builder(this.context)
                    .setTitle(R.string.safe_area_dialog_title)
                    .setMessage(R.string.exterior_setting_ui_safe_area_hint)
                    .setSingleChoiceItems(items, cur, (d, which) -> {
                        try {
                            this.activity.launcherSetting.safeAreaMode = which;
                            com.qcl.launcher.utils.gson.GsonUtils.saveLauncherSetting(
                                    this.activity.launcherSetting,
                                    com.qcl.launcher.manifest.AppManifest.SETTING_DIR + "/launcher_setting.json");
                        } catch (Throwable ignored) {
                        }
                        try {
                            View root = this.activity.findViewById(R.id.launcher_root);
                            if (root != null) {
                                root.requestApplyInsets();
                            }
                        } catch (Throwable ignored) {
                        }
                        refreshSafeArea();
                        d.dismiss();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    /** 刷新安全区那一行的显示：当前模式 + 实际检测到的避让量。 */
    private void refreshSafeArea() {
        try {
            if (this.safeAreaValue != null) {
                int m = this.activity.launcherSetting == null ? 0 : this.activity.launcherSetting.safeAreaMode;
                this.safeAreaValue.setText(m == 1 ? this.context.getString(R.string.safe_area_mode_on)
                        : m == 2 ? this.context.getString(R.string.safe_area_mode_off)
                        : this.context.getString(R.string.safe_area_mode_auto));
            }
            if (this.safeAreaHint != null) {
                String detected = com.qcl.launcher.launcher.ui.SafeAreaHelper.lastApplied;
                this.safeAreaHint.setText(detected == null || detected.isEmpty()
                        ? this.context.getString(R.string.safe_area_not_yet)
                        : this.context.getString(R.string.safe_area_detected, detected));
            }
        } catch (Throwable ignored) {
        }
    }

    public void onClick(View v) {
        // ★ 社区版新增：主题方案（预设 / 导出 / 导入）。放在最前面 —— 与下面两处
        //   ColorSelectorDialog 分支无关，早返回避免互相干扰。
        if (v == this.themePresetDefault) {
            applyThemePreset(0);
            return;
        }
        if (v == this.themePresetDark) {
            applyThemePreset(1);
            return;
        }
        if (v == this.themePresetAmoled) {
            applyThemePreset(2);
            return;
        }
        if (v == this.themeExport) {
            exportTheme();
            return;
        }
        if (v == this.themeImport) {
            importTheme();
            return;
        }
        // ★ 社区版新增：安全区避让模式
        if (v == this.safeAreaLayout) {
            showSafeAreaDialog();
            return;
        }
        ColorSelectorDialog dialog;
        if (v == this.selectTheme) {
            dialog = new ColorSelectorDialog(this.context, true, ExteriorSettingUI.parseThemeColorSafe(this.context, this.activity.launcherSetting.launcherTheme));
            dialog.setColorSelectorDialogListener(new ColorSelectorDialog.ColorSelectorDialogListener(){

                @Override
                @SuppressLint(value={"SetTextI18n"})
                public void onColorSelected(int color2) {
                    ExteriorSettingUI.this.activity.exteriorConfig.primaryColor(color2);
                    ExteriorSettingUI.this.activity.exteriorConfig.accentColor(color2);
                    ExteriorSettingUI.this.activity.exteriorConfig.apply((Activity)ExteriorSettingUI.this.activity);
                    ExteriorSettingUI.this.colorView.setBackgroundColor(color2);
                    ExteriorSettingUI.this.colorText.setText((CharSequence)(QclColors.format(color2)));
                }

                @Override
                @SuppressLint(value={"SetTextI18n"})
                public void onPositive(int destColor) {
                    ExteriorSettingUI.this.activity.launcherSetting.launcherTheme = QclColors.format(destColor);
                    GsonUtils.saveLauncherSetting(ExteriorSettingUI.this.activity.launcherSetting, AppManifest.SETTING_DIR + "/launcher_setting.json");
                    ExteriorSettingUI.this.activity.exteriorConfig.primaryColor(destColor);
                    ExteriorSettingUI.this.activity.exteriorConfig.accentColor(destColor);
                    ExteriorSettingUI.this.activity.exteriorConfig.apply((Activity)ExteriorSettingUI.this.activity);
                    ExteriorSettingUI.this.colorView.setBackgroundColor(destColor);
                    ExteriorSettingUI.this.colorText.setText((CharSequence)(QclColors.format(destColor)));
                }

                @Override
                @SuppressLint(value={"SetTextI18n"})
                public void onNegative(int initColor) {
                    ExteriorSettingUI.this.activity.exteriorConfig.primaryColor(initColor);
                    ExteriorSettingUI.this.activity.exteriorConfig.accentColor(initColor);
                    ExteriorSettingUI.this.activity.exteriorConfig.apply((Activity)ExteriorSettingUI.this.activity);
                    ExteriorSettingUI.this.colorView.setBackgroundColor(initColor);
                    ExteriorSettingUI.this.colorText.setText((CharSequence)(QclColors.format(initColor)));
                }
            });
            dialog.show();
        }
        if (v == this.selectPanelColor) {
            dialog = new ColorSelectorDialog(this.context, true, ExteriorSettingUI.getPanelColor(this.context, this.activity.launcherSetting.panelColor));
            dialog.setColorSelectorDialogListener(new ColorSelectorDialog.ColorSelectorDialogListener(){

                @Override
                @SuppressLint(value={"SetTextI18n"})
                public void onColorSelected(int color2) {
                    ExteriorSettingUI.this.panelColorView.setBackgroundColor(color2);
                    ExteriorSettingUI.this.panelColorText.setText((CharSequence)(QclColors.format(color2)));
                }

                @Override
                @SuppressLint(value={"SetTextI18n"})
                public void onPositive(int destColor) {
                    ExteriorSettingUI.this.activity.launcherSetting.panelColor = QclColors.format(destColor);
                    GsonUtils.saveLauncherSetting(ExteriorSettingUI.this.activity.launcherSetting, AppManifest.SETTING_DIR + "/launcher_setting.json");
                    ExteriorSettingUI.this.panelColorView.setBackgroundColor(destColor);
                    ExteriorSettingUI.this.panelColorText.setText((CharSequence)(QclColors.format(destColor)));
                    // ★ 色盘选出来的具体颜色 → 走「纯色模式」（不是 DEFAULT，不会被当成透明）
                    String destStr = QclColors.format(destColor);
                    ExteriorSettingUI.applyPanelTint(ExteriorSettingUI.this.context, (View)ExteriorSettingUI.this.exteriorSettingUI, destStr);
                    ExteriorSettingUI.applyPanelTint(ExteriorSettingUI.this.context, ExteriorSettingUI.this.activity.getWindow().getDecorView(), destStr);
                }

                @Override
                @SuppressLint(value={"SetTextI18n"})
                public void onNegative(int initColor) {
                    ExteriorSettingUI.this.panelColorView.setBackgroundColor(initColor);
                    ExteriorSettingUI.this.panelColorText.setText((CharSequence)(QclColors.format(initColor)));
                }
            });
            dialog.show();
        }
        if (v == this.selectBgPath) {
            Intent intent = new Intent(this.context, FileChooser.class);
            intent.putExtra("SELECTION_MODE", Constants.SELECTION_MODES.SINGLE_SELECTION.ordinal());
            intent.putExtra("ALLOWED_FILE_EXTENSIONS", "png;jpg");
            intent.putExtra("INITIAL_DIRECTORY", new File(Environment.getExternalStorageDirectory().getAbsolutePath()).getAbsolutePath());
            this.activity.startActivityForResult(intent, 4000);
        }
    }

    @SuppressLint(value={"UseCompatLoadingForDrawables"})
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == this.fullscreenSwitch) {
            this.activity.launcherSetting.fullscreen = isChecked;
            GsonUtils.saveLauncherSetting(this.activity.launcherSetting, AppManifest.SETTING_DIR + "/launcher_setting.json");
            if (Build.VERSION.SDK_INT >= 28) {
                this.activity.getWindow().getAttributes().layoutInDisplayCutoutMode = isChecked ? 1 : 2;
            }
            this.activity.getWindow().setFlags(256, 256);
        }
        if (this.grassUiSwitch != null && buttonView == this.grassUiSwitch) {
            this.activity.launcherSetting.uiTheme = isChecked ? 1 : 0;
            GsonUtils.saveLauncherSetting(this.activity.launcherSetting, AppManifest.SETTING_DIR + "/launcher_setting.json");
            QclThemeUtils.apply((Activity)this.activity, this.activity.launcherSetting.uiTheme);
            this.refreshColorEditable();
        }
        // 1.3.7: 主界面是否显示账号人物（默认开）
        if (this.showAccountModelSwitch != null && buttonView == this.showAccountModelSwitch) {
            this.activity.launcherSetting.showAccountModel = isChecked;
            GsonUtils.saveLauncherSetting(this.activity.launcherSetting, AppManifest.SETTING_DIR + "/launcher_setting.json");
        }
        // ★ 1.2.3：透明界面背景（默认开；关掉恢复原来的灰色面板 #C8EDEDED）
        if (this.transBgSwitch != null && buttonView == this.transBgSwitch) {
            this.activity.launcherSetting.transparentBackground = isChecked;
            GsonUtils.saveLauncherSetting(this.activity.launcherSetting, AppManifest.SETTING_DIR + "/launcher_setting.json");
            View container = this.activity.findViewById(R.id.main_ui_container);
            if (container != null) {
                container.setBackgroundColor(isChecked
                        ? android.graphics.Color.TRANSPARENT
                        : android.graphics.Color.parseColor("#C8EDEDED"));
            }
        }
        if (buttonView == this.defaultRadio && isChecked) {
            this.classicRadio.setChecked(false);
            this.customRadio.setChecked(false);
            this.onlineRadio.setChecked(false);
            this.editBgPath.setEnabled(false);
            this.editBgUrl.setEnabled(false);
            this.selectBgPath.setEnabled(false);
            // ★ 1.3.4：「默认」= 经典图片（type 1）
            this.activity.launcherSetting.launcherBackground.type = 1;
            this.activity.refreshDynamicBackground();
            this.activity.launcherLayout.setBackground(this.context.getDrawable(R.drawable.ic_background_classic));
        }
        if (buttonView == this.classicRadio && isChecked) {
            this.defaultRadio.setChecked(false);
            this.customRadio.setChecked(false);
            this.onlineRadio.setChecked(false);
            this.editBgPath.setEnabled(false);
            this.editBgUrl.setEnabled(false);
            this.selectBgPath.setEnabled(false);
            this.activity.launcherSetting.launcherBackground.type = 1;
            this.activity.refreshDynamicBackground();
            this.activity.launcherLayout.setBackground(this.context.getDrawable(R.drawable.ic_background_classic));
        }
        if (buttonView == this.customRadio && isChecked) {
            this.defaultRadio.setChecked(false);
            this.classicRadio.setChecked(false);
            this.onlineRadio.setChecked(false);
            this.editBgPath.setEnabled(true);
            this.editBgUrl.setEnabled(false);
            this.selectBgPath.setEnabled(true);
            this.activity.launcherSetting.launcherBackground.type = 2;
            this.activity.refreshDynamicBackground();
            this.activity.launcherSetting.launcherBackground.path = this.editBgPath.getText().toString();
            if (new File(this.editBgPath.getText().toString()).exists() && ExteriorSettingUI.isImageFile(this.editBgPath.getText().toString())) {
                Bitmap bitmap = BitmapFactory.decodeFile((String)this.editBgPath.getText().toString());
                this.activity.launcherLayout.setBackground((Drawable)new BitmapDrawable(bitmap));
            } else {
                this.activity.launcherLayout.setBackground(this.context.getDrawable(R.drawable.qcl_bg_1));
            }
        }
        if (buttonView == this.onlineRadio && isChecked) {
            this.defaultRadio.setChecked(false);
            this.classicRadio.setChecked(false);
            this.customRadio.setChecked(false);
            this.editBgPath.setEnabled(false);
            this.editBgUrl.setEnabled(false);
            this.selectBgPath.setEnabled(false);
            // ★ 1.3.4：「网络」= 自动切换的动态背景（type 0，图片由 DynamicBackground 从网络拉取）
            this.activity.launcherSetting.launcherBackground.type = 0;
            this.activity.refreshDynamicBackground();
        }
        GsonUtils.saveLauncherSetting(this.activity.launcherSetting, AppManifest.SETTING_DIR + "/launcher_setting.json");
    }
}

