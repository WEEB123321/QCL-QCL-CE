package com.qcl.launcher.launcher.dialogs.account;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.auth.Account;
import com.qcl.launcher.auth.authlibinjector.AuthlibInjectorServer;
import com.qcl.launcher.auth.importer.OtherLauncherAccountParser;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.skin.utils.Avatar;
import com.qcl.launcher.utils.gson.GsonUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ★★★ 从其它启动器导入账户（FCL / ZL2 / HMCL-PE 及其分支）。
 *
 * <h3>为什么需要这个</h3>
 * 用户在这几个启动器里已经登过账号，换到 QCL 就要重新登一遍 —— 尤其是
 * 微软正版要重走一遍 OAuth，外置登录还要重新输服务器地址和密码。
 * 而这些账户数据就在手机里（各自目录下的 {@code accounts.json}），
 * 其中**微软的 refresh_token 是跨启动器通用的**，外置的 accessToken 同理。
 * 所以「导入」不是搬家摆设，是能真的省掉重新登录。
 *
 * <h3>两条入口，故意都留</h3>
 * <ol>
 *   <li><b>自动扫描</b> —— 扫常见路径（{@code /sdcard/FCL}、{@code /sdcard/ZL2}、
 *       {@code /sdcard/Android/data/<包名>/files} …）。方便，但 Android 11+ 上
 *       **别的应用的 Android/data 目录是读不到的**（没有 MANAGE_EXTERNAL_STORAGE 时），
 *       所以它经常扫不到东西 —— 这是系统限制，不是 bug。</li>
 *   <li><b>手动选文件</b> —— 走项目已有的 {@code FileChooser}。扫描失败时的正路，
 *       而且用户能自己把 accounts.json 复制到能读的地方再选。</li>
 * </ol>
 *
 * <h3>★ 不做的事（刻意）</h3>
 * 不覆盖、不删除、不改动源文件 —— 全程**只读**。导入的是副本，
 * 用户原来的启动器照常能用。这也是「绝不给用户做删除决定」的具体落实。
 */
public class ImportOtherLauncherAccountDialog extends Dialog implements View.OnClickListener {

    private static final int PAD_DP = 16;

    private final Context context;
    private final MainActivity activity;
    private final ArrayList<Account> accounts;

    private LinearLayout contentBox;
    private TextView statusText;
    private Button importButton;
    private Button pickButton;

    /** 解析出来的候选（顺序与 UI 行一一对应）。 */
    private final List<OtherLauncherAccountParser.Imported> candidates = new ArrayList<>();
    private final List<CheckBox> candidateChecks = new ArrayList<>();
    /** 用户手动选中的文件（由 {@code AccountUI.onActivityResult} 回填）。 */
    private File pendingFile;
    private String pendingSource = "";

    public ImportOtherLauncherAccountDialog(Context context, MainActivity activity,
                                            ArrayList<Account> accounts) {
        super(context);
        this.context = context;
        this.activity = activity;
        this.accounts = accounts;
        setContentView(buildUi());
        setCancelable(true);
        scanCommonPlaces();
    }

    // ══════════════════════════════════ UI ══════════════════════════════════

    private View buildUi() {
        int pad = dp(PAD_DP);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(18));
        root.setBackground(bg);

        TextView title = new TextView(context);
        title.setText(R.string.account_import_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTextColor(0xFF202020);
        root.addView(title);

        TextView hint = new TextView(context);
        hint.setText(R.string.account_import_hint);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(0xFF707070);
        hint.setPadding(0, dp(6), 0, dp(10));
        root.addView(hint);

        this.statusText = new TextView(context);
        this.statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        this.statusText.setTextColor(0xFF1E88E5);
        this.statusText.setPadding(0, 0, 0, dp(6));
        root.addView(this.statusText);

        ScrollView scroll = new ScrollView(context);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(slp);

        this.contentBox = new LinearLayout(context);
        this.contentBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(this.contentBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, dp(12), 0, 0);

        Button cancel = new Button(context);
        cancel.setText(R.string.account_import_cancel);
        cancel.setAllCaps(false);
        cancel.setOnClickListener(this);
        buttons.addView(cancel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        this.pickButton = new Button(context);
        this.pickButton.setText(R.string.account_import_pick_file);
        this.pickButton.setAllCaps(false);
        this.pickButton.setOnClickListener(this);
        buttons.addView(this.pickButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        this.importButton = new Button(context);
        this.importButton.setText(R.string.account_import_do);
        this.importButton.setAllCaps(false);
        this.importButton.setOnClickListener(this);
        buttons.addView(this.importButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(buttons);
        return root;
    }

    private void setStatus(String s) {
        if (this.statusText != null) {
            this.statusText.setText(s == null ? "" : s);
        }
    }

    // ══════════════════════════════════ 自动扫描 ══════════════════════════════════

    /**
     * 扫常见位置。
     *
     * <p>★ 每个候选都独立 try/catch：Android 11+ 上访问别的应用的
     * {@code Android/data/...} 会抛 {@code SecurityException}，
     * 不能让一个目录把整轮扫描带崩。
     */
    private void scanCommonPlaces() {
        this.contentBox.removeAllViews();
        List<File> found = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        File sdcard = Environment.getExternalStorageDirectory();
        // ① /sdcard/<启动器目录>/accounts.json
        String[] topDirs = {"FCL", "ZL2", "HMCLPE", "HMCL", "PojavLauncher", "FoldCraftLauncher"};
        for (String dir : topDirs) {
            addIfExists(new File(new File(sdcard, dir), "accounts.json"), dir, found, labels);
            addIfExists(new File(new File(sdcard, dir), ".accounts.json"), dir, found, labels);
        }
        // ② /sdcard/Android/data/<包名>/files/accounts.json
        //    ★ 已知包名写死一份，再扫一遍全部包名兜住没列到的分支
        String[] pkgs = {"com.tungsten.fcl", "com.tungsten.hmclpe", "com.tungsten.fclauncher",
                "org.angelauramc.fcl", "com.qcl.launcher"};
        for (String pkg : pkgs) {
            addIfExists(new File(new File(new File(sdcard,
                    "Android/data/" + pkg), "files"), "accounts.json"), pkg, found, labels);
        }
        try {
            File dataRoot = new File(sdcard, "Android/data");
            File[] children = dataRoot.listFiles();
            if (children != null) {
                for (File c : children) {
                    addIfExists(new File(new File(c, "files"), "accounts.json"),
                            c.getName(), found, labels);
                }
            }
        } catch (Throwable ignored) {
        }

        if (found.isEmpty()) {
            setStatus(context.getString(R.string.account_import_scan_none));
            return;
        }
        setStatus(context.getString(R.string.account_import_scan_found, found.size()));
        for (int i = 0; i < found.size(); i++) {
            this.contentBox.addView(fileRow(found.get(i), labels.get(i)));
        }
    }

    private void addIfExists(File f, String label, List<File> found, List<String> labels) {
        try {
            if (f != null && f.isFile() && f.length() > 0) {
                // 去重（同一个文件可能被两条规则都命中）
                for (File x : found) {
                    if (x.getAbsolutePath().equals(f.getAbsolutePath())) {
                        return;
                    }
                }
                found.add(f);
                labels.add(label);
            }
        } catch (Throwable ignored) {
        }
    }

    private View fileRow(final File file, final String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF2F4F7);
        bg.setCornerRadius(dp(10));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        TextView name = new TextView(context);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(0xFF202020);
        row.addView(name);

        TextView path = new TextView(context);
        path.setText(file.getAbsolutePath());
        path.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        path.setTextColor(0xFF808080);
        row.addView(path);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadFile(file, label);
            }
        });
        return row;
    }

    // ══════════════════════════════════ 解析并展示 ══════════════════════════════════

    /** 由 {@code AccountUI.onActivityResult} 在用户选完文件后调用。 */
    public void onFilePicked(String path) {
        if (TextUtils.isEmpty(path)) {
            setStatus(context.getString(R.string.account_import_parse_fail));
            return;
        }
        this.pendingFile = new File(path);
        this.pendingSource = this.pendingFile.getParentFile() == null
                ? "accounts.json" : this.pendingFile.getParentFile().getName();
        loadFile(this.pendingFile, this.pendingSource);
    }

    private void loadFile(File file, String label) {
        this.candidates.clear();
        this.candidateChecks.clear();
        this.contentBox.removeAllViews();

        List<OtherLauncherAccountParser.Imported> list =
                OtherLauncherAccountParser.parse(file, label);
        if (list.isEmpty()) {
            setStatus(context.getString(R.string.account_import_parse_empty));
            return;
        }
        // 已存在的账户不再列出来（同名字 + 同类型视为同一个）
        Set<String> existing = new HashSet<>();
        if (this.accounts != null) {
            for (Account a : this.accounts) {
                existing.add((a.auth_player_name == null ? "" : a.auth_player_name.toLowerCase())
                        + "#" + a.loginType);
            }
        }
        int added = 0;
        for (OtherLauncherAccountParser.Imported one : list) {
            if (existing.contains(one.key())) {
                continue;
            }
            this.candidates.add(one);
            this.contentBox.addView(accountRow(one));
            added++;
        }
        if (added == 0) {
            setStatus(context.getString(R.string.account_import_all_exist));
        } else {
            setStatus(context.getString(R.string.account_import_parsed, added));
        }
    }

    private View accountRow(final OtherLauncherAccountParser.Imported one) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));

        CheckBox cb = new CheckBox(context);
        cb.setChecked(true);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
            }
        });
        row.addView(cb);
        this.candidateChecks.add(cb);

        ImageView face = new ImageView(context);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(32), dp(32));
        flp.leftMargin = dp(4);
        flp.rightMargin = dp(8);
        face.setLayoutParams(flp);
        row.addView(face);
        // 头像预览：能拿到皮肤就直接渲染，拿不到就留空（不画假头像）
        String skin = resolveSkinBase64(one);
        if (!TextUtils.isEmpty(skin)) {
            Bitmap bmp = Avatar.stringToBitmap(skin);
            if (bmp != null) {
                try {
                    face.setImageBitmap(Bitmap.createBitmap(bmp, 8, 8, 8, 8));
                } catch (Throwable ignored) {
                }
            }
        }

        LinearLayout textBox = new LinearLayout(context);
        textBox.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(one.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(0xFF202020);
        textBox.addView(name);
        TextView meta = new TextView(context);
        meta.setText(one.typeLabel + (TextUtils.isEmpty(one.loginServer)
                ? "" : " · " + one.loginServer));
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        meta.setTextColor(0xFF808080);
        textBox.addView(meta);
        row.addView(textBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return row;
    }

    // ══════════════════════════════════ 导入 ══════════════════════════════════

    @Override
    public void onClick(View v) {
        if (v == this.importButton) {
            doImport();
        } else if (v == this.pickButton) {
            if (this.activity == null) {
                return;
            }
            try {
                android.content.Intent intent = new android.content.Intent(
                        this.activity, com.tungsten.filepicker.FileChooser.class);
                intent.putExtra(com.tungsten.filepicker.Constants.SELECTION_MODE,
                        com.tungsten.filepicker.Constants.SELECTION_MODES.SINGLE_SELECTION.ordinal());
                intent.putExtra(com.tungsten.filepicker.Constants.ALLOWED_FILE_EXTENSIONS, "json");
                intent.putExtra(com.tungsten.filepicker.Constants.INITIAL_DIRECTORY,
                        Environment.getExternalStorageDirectory().getAbsolutePath());
                this.activity.startActivityForResult(intent,
                        com.qcl.launcher.launcher.uis.account.AccountUI.SELECT_ACCOUNT_FILE_REQUEST);
            } catch (Throwable t) {
                Toast.makeText(this.context,
                        context.getString(R.string.account_import_pick_fail), Toast.LENGTH_SHORT).show();
            }
        } else {
            dismiss();
        }
    }

    private void doImport() {
        if (this.candidates.isEmpty()) {
            dismiss();
            return;
        }
        int ok = 0;
        for (int i = 0; i < this.candidates.size(); i++) {
            if (i >= this.candidateChecks.size() || !this.candidateChecks.get(i).isChecked()) {
                continue;
            }
            OtherLauncherAccountParser.Imported one = this.candidates.get(i);
            try {
                Account account = toAccount(one);
                if (account == null) {
                    continue;
                }
                // 外置登录：服务器要一并登记，否则这个账户连不上
                if ((one.loginType == 4 || one.loginType == 5)
                        && !TextUtils.isEmpty(one.loginServer)) {
                    registerServer(one.loginServer);
                }
                if (this.accounts != null) {
                    this.accounts.add(account);
                }
                ok++;
            } catch (Throwable ignored) {
            }
        }
        try {
            if (this.accounts != null && !this.accounts.isEmpty()) {
                // ★ 不自动切换当前账户 —— 导入是「搬进来」，用哪个由用户自己决定
                GsonUtils.saveAccounts(this.accounts,
                        AppManifest.ACCOUNT_DIR + "/accounts.json");
            }
            if (this.activity != null && this.activity.uiManager != null
                    && this.activity.uiManager.accountUI != null
                    && this.activity.uiManager.accountUI.accountListAdapter != null) {
                this.activity.uiManager.accountUI.accountListAdapter.notifyDataSetChanged();
            }
        } catch (Throwable ignored) {
        }
        Toast.makeText(this.context,
                context.getString(R.string.account_import_done, ok), Toast.LENGTH_SHORT).show();
        dismiss();
    }

    private Account toAccount(OtherLauncherAccountParser.Imported one) {
        String uuid = TextUtils.isEmpty(one.uuid) ? UUID.randomUUID().toString() : one.uuid;
        // ★ 头像：优先用源里的皮肤，其次读源里的皮肤文件，最后才用默认
        String skin = resolveSkinBase64(one);
        String token = TextUtils.isEmpty(one.accessToken) ? "" : one.accessToken;
        String client = TextUtils.isEmpty(one.clientToken)
                ? "00000000-0000-0000-0000-000000000000" : one.clientToken;
        // 离线账户的 accessToken 字段在 QCL 里是一个随机 UUID（照 AddOfflineAccountDialog 的写法）
        if (one.loginType == 1 && TextUtils.isEmpty(token)) {
            token = UUID.randomUUID().toString();
        }
        return new Account(one.loginType, "", "", "mojang", "0", one.name, uuid,
                token, client, one.refreshToken, one.loginServer, skin);
    }

    /**
     * 把源账户的皮肤/头像转成 QCL 的 {@code texture}（整张 64×64 皮肤的 Base64）。
     *
     * <p>★ 为什么要「转」而不是直接用：{@code Avatar.setAvatar} 会在固定坐标
     * (8,8) 和 (40,8) 裁 8×8 的块，所以 texture **必须是一张完整的 64×64 皮肤**。
     * 如果源里存的是一张 8×8 的头像图，直接塞进去会裁出越界的黑块。
     * 这里把非 64×64 的图贴到一张透明皮肤的 (8,8) 位置 —— 脸对了，帽子留空。
     */
    private String resolveSkinBase64(OtherLauncherAccountParser.Imported one) {
        // ① 源里就有 base64
        if (!TextUtils.isEmpty(one.skinBase64)) {
            Bitmap bmp = null;
            try {
                bmp = Avatar.stringToBitmap(one.skinBase64);
            } catch (Throwable ignored) {
            }
            if (bmp != null) {
                String fixed = normalizeSkin(bmp);
                if (!TextUtils.isEmpty(fixed)) {
                    return fixed;
                }
            }
        }
        // ② 源里给了皮肤文件路径
        if (!TextUtils.isEmpty(one.skinPath)) {
            try {
                File f = new File(one.skinPath);
                if (f.isFile()) {
                    Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                    String fixed = normalizeSkin(bmp);
                    if (!TextUtils.isEmpty(fixed)) {
                        return fixed;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        // ③ 默认皮肤（与离线账户一致，保证不会出现空白头像）
        try {
            return Avatar.bitmapToString(BitmapFactory.decodeStream(
                    this.context.getAssets().open("img/alex.png")));
        } catch (Throwable t) {
            return "";
        }
    }

    private String normalizeSkin(Bitmap bmp) {
        if (bmp == null) {
            return "";
        }
        try {
            if (bmp.getWidth() == 64 && bmp.getHeight() == 64) {
                return Avatar.bitmapToString(bmp);
            }
            Bitmap canvas = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(canvas);
            // 8×8 头像 → 贴到脸部位置；其它尺寸（例如 128×128 皮肤）→ 整体缩放到 64×64
            if (bmp.getWidth() == 8 && bmp.getHeight() == 8) {
                c.drawBitmap(bmp, 8, 8, null);
            } else {
                c.drawBitmap(bmp, null,
                        new android.graphics.Rect(0, 0, 64, 64), null);
            }
            return Avatar.bitmapToString(canvas);
        } catch (Throwable t) {
            return "";
        }
    }

    private void registerServer(String url) {
        try {
            if (this.activity == null || this.activity.uiManager == null
                    || this.activity.uiManager.accountUI == null) {
                return;
            }
            ArrayList<AuthlibInjectorServer> servers = this.activity.uiManager.accountUI.serverList;
            if (servers == null) {
                return;
            }
            for (AuthlibInjectorServer s : servers) {
                if (s != null && url.equalsIgnoreCase(s.getUrl())) {
                    return;
                }
            }
            servers.add(new AuthlibInjectorServer(url));
            GsonUtils.saveServer(servers, AppManifest.ACCOUNT_DIR + "/authlib_injector_server.json");
            if (this.activity.uiManager.accountUI.serverListAdapter != null) {
                this.activity.uiManager.accountUI.serverListAdapter.notifyDataSetChanged();
            }
        } catch (Throwable ignored) {
        }
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
