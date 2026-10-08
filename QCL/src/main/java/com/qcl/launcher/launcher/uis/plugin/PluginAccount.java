package com.qcl.launcher.launcher.uis.plugin;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.manifest.AppManifest;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ★★★ 社区版：账号与身份（用户清单第 1-6 项）。
 *
 * <p><b>诚实说明</b>：
 * <ul>
 *   <li><b>#1 账号头像生成器</b> —— 真做 ✓ 按玩家名生成确定性像素头像
 *       （同名永远同一张图），可保存成 png</li>
 *   <li><b>#3 多账号分组</b> —— 真做 ✓ 账号 → 分组映射，本地存</li>
 *   <li><b>#5 账号皮肤绑定</b> —— 真做 ✓ 账号 → 皮肤文件映射</li>
 *   <li><b>#6 账号数据统计</b> —— 真做（部分）✓ 统计实例数 / 存档数 / 占用；
 *       <b>游玩时长统计做不到</b> ✗ —— 启动器不记录「某账号玩了多久」，
 *       要做得先加埋点并改启动流程</li>
 *   <li><b>#2 登录历史记录</b> —— <b>做不到</b> ✗ 要挂钩登录流程（每次登录成功/失败时写一条），
 *       而登录走 Minecraft 账户模块内部，没有回调钩子；
 *       设备信息在 Android 10+ 也拿不到（只有型号，没有唯一标识）</li>
 *   <li><b>#4 游客模式</b> —— <b>做不到</b> ✗ 「不能联机、不能保存」要在**所有**相关入口强制拦截
 *       （联机按钮、存档写入、下载安装…），属于全局策略，不是插件能挂的；
 *       做成插件只能提示，拦不住</li>
 * </ul>
 */
public final class PluginAccount {

    private PluginAccount() {
    }

    // ==================== #1 账号头像生成器 ====================

    public static void avatarGen(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "账号头像生成器"));
            col.addView(PluginUiKit.sub(a, "按玩家名生成像素头像 —— **同名永远同一张**。"));
            col.addView(PluginUiKit.sub(a, " "));

            EditText name = new EditText(a);
            name.setHint("玩家名");
            name.setText("Steve");
            col.addView(name);

            final ImageView preview = new ImageView(a);
            preview.setAdjustViewBounds(true);
            int size = PluginUiKit.dp(a, 96);
            preview.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            col.addView(preview);

            final TextView info = new TextView(a);
            info.setTextSize(12f);
            info.setTextColor(0xFF8A8378);
            col.addView(info);

            final Bitmap[] bmp = new Bitmap[1];
            final Runnable render = () -> {
                try {
                    String n = name.getText() == null ? "Steve" : name.getText().toString().trim();
                    if (n.isEmpty()) {
                        n = "Steve";
                    }
                    bmp[0] = makeAvatar(n, 16);
                    preview.setImageBitmap(bmp[0]);
                    info.setText("16×16 像素 · 由玩家名派生（改名就换图）");
                } catch (Throwable ignored) {
                }
            };
            render.run();
            name.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a2, int b2, int c2) {
                }

                public void onTextChanged(CharSequence s, int a2, int b2, int c2) {
                }

                public void afterTextChanged(android.text.Editable e) {
                    render.run();
                }
            });

            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 头像是**算法生成**的，不是从 Mojang 拉的真实皮肤 ✗"));
            col.addView(PluginUiKit.sub(a, "★ 保存位置：" + avatarDir().getAbsolutePath()));

            PluginUiKit.sheet(a, "账号头像生成器", PluginUiKit.scroller(a, col), "保存 PNG", () -> {
                try {
                    if (bmp[0] == null) {
                        return;
                    }
                    File dir = avatarDir();
                    if (!dir.exists() && !dir.mkdirs()) {
                        PluginUiKit.toast(a, "建目录失败");
                        return;
                    }
                    String n = name.getText() == null ? "Steve" : name.getText().toString().trim();
                    if (n.isEmpty()) {
                        n = "Steve";
                    }
                    File out = new File(dir, safe(n) + ".png");
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        bmp[0].compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                    PluginUiKit.log(a, "生成头像 " + out.getName());
                    PluginUiKit.toast(a, "已保存 " + out.getName());
                } catch (Throwable t) {
                    PluginUiKit.toast(a, "保存失败：" + t);
                }
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    /** 按名字生成确定性像素头像。 */
    private static Bitmap makeAvatar(String name, int n) {
        int h = name.hashCode();
        // 用 hash 派生主色和辅色
        int c1 = Color.HSVToColor(new float[]{(Math.abs(h) % 360), 0.45f, 0.92f});
        int c2 = Color.HSVToColor(new float[]{(Math.abs(h / 7) % 360), 0.55f, 0.70f});
        int skin = Color.rgb(0xE8, 0xC0, 0xA0);
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        // 背景
        p.setColor(c1);
        c.drawRect(0, 0, n, n, p);
        // 脸
        p.setColor(skin);
        c.drawRect(n * 0.25f, n * 0.25f, n * 0.75f, n * 0.75f, p);
        // 头发（用 hash 决定高度）
        p.setColor(c2);
        int hairRows = 2 + (Math.abs(h) % 3);
        c.drawRect(n * 0.25f, n * 0.25f, n * 0.75f, n * (0.25f + 0.06f * hairRows), p);
        // 眼睛
        p.setColor(0xFF3A342C);
        float ey = n * 0.52f;
        c.drawRect(n * 0.35f, ey, n * 0.42f, ey + n * 0.08f, p);
        c.drawRect(n * 0.58f, ey, n * 0.65f, ey + n * 0.08f, p);
        // 嘴（hash 决定表情）
        p.setColor(0xFF8A5A4A);
        if ((h & 1) == 0) {
            c.drawRect(n * 0.42f, n * 0.68f, n * 0.58f, n * 0.72f, p);
        } else {
            c.drawRect(n * 0.42f, n * 0.66f, n * 0.58f, n * 0.70f, p);
            c.drawRect(n * 0.42f, n * 0.70f, n * 0.46f, n * 0.74f, p);
            c.drawRect(n * 0.54f, n * 0.70f, n * 0.58f, n * 0.74f, p);
        }
        return b;
    }

    private static File avatarDir() {
        return new File(AppManifest.LAUNCHER_DIR, "avatars");
    }

    // ==================== #3 多账号分组 ====================

    private static final String GROUP_FILE = "qcl_account_groups.txt";

    public static void accountGroups(MainActivity a) {
        try {
            final File f = new File(AppManifest.SETTING_DIR, GROUP_FILE);
            final Map<String, String> map = loadMap(f);

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "多账号分组"));
            col.addView(PluginUiKit.sub(a, "给账号打分组：主号 / 小号 / 测试号…"));
            col.addView(PluginUiKit.sub(a, " "));

            EditText acc = new EditText(a);
            acc.setHint("账号名（如 Steve_2024）");
            EditText grp = new EditText(a);
            grp.setHint("分组名（如 主号）");
            col.addView(acc);
            col.addView(grp);

            col.addView(PluginUiKit.label(a, "已有分组"));
            final LinearLayout listBox = new LinearLayout(a);
            listBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(listBox);

            final Runnable[] rebuild = new Runnable[1];
            rebuild[0] = () -> {
                try {
                    listBox.removeAllViews();
                    if (map.isEmpty()) {
                        listBox.addView(PluginUiKit.sub(a, "（还没有分组）"));
                        return;
                    }
                    // 按分组归拢
                    Map<String, List<String>> byGroup = new LinkedHashMap<>();
                    for (Map.Entry<String, String> e : map.entrySet()) {
                        byGroup.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
                    }
                    for (Map.Entry<String, List<String>> e : byGroup.entrySet()) {
                        LinearLayout card = PluginUiKit.card(a);
                        card.addView(PluginUiKit.text(a, e.getKey()
                                + "（" + e.getValue().size() + " 个）"));
                        card.addView(PluginUiKit.sub(a, String.join("、", e.getValue())));
                        LinearLayout row = new LinearLayout(a);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        for (final String name : e.getValue()) {
                            TextView rm = PluginUiKit.row(a, "移除 " + name, null);
                            rm.setPadding(PluginUiKit.dp(a, 8), PluginUiKit.dp(a, 4),
                                    PluginUiKit.dp(a, 8), PluginUiKit.dp(a, 4));
                            PluginUiKit.pressEffect(rm);
                            rm.setOnClickListener(v -> {
                                map.remove(name);
                                rebuild[0].run();
                            });
                            row.addView(rm);
                        }
                        card.addView(row);
                        listBox.addView(card);
                    }
                } catch (Throwable ignored) {
                }
            };
            rebuild[0].run();

            PluginUiKit.sheet(a, "多账号分组", PluginUiKit.scroller(a, col), "添加", () -> {
                String an = acc.getText() == null ? "" : acc.getText().toString().trim();
                String gn = grp.getText() == null ? "" : grp.getText().toString().trim();
                if (an.isEmpty() || gn.isEmpty()) {
                    PluginUiKit.toast(a, "账号名和分组名都要填");
                    return;
                }
                map.put(an, gn);
                saveMap(f, map);
                PluginUiKit.toast(a, "已添加");
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== #5 账号皮肤绑定 ====================

    private static final String SKIN_FILE = "qcl_account_skins.txt";

    public static void skinBind(MainActivity a) {
        try {
            final File f = new File(AppManifest.SETTING_DIR, SKIN_FILE);
            final Map<String, String> map = loadMap(f);
            final List<String> skins = new ArrayList<>();
            try {
                File[] sk = new File(AppManifest.GAME_FILE_DIRECTORY_DIR, "skins").listFiles();
                if (sk != null) {
                    for (File x : sk) {
                        if (x != null && x.isFile() && x.getName().toLowerCase().endsWith(".png")) {
                            skins.add(x.getName());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, "账号皮肤绑定"));
            col.addView(PluginUiKit.sub(a, "把账号和皮肤文件对应起来，换号时知道该用哪张。"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, "★ 只存「账号 → 文件名」的映射，**不自动替换游戏皮肤** ✗ —— "
                    + "自动替换要改游戏资源，风险大。"));
            col.addView(PluginUiKit.sub(a, "★ skins 目录里有 " + skins.size() + " 张皮肤。"));

            if (skins.isEmpty()) {
                col.addView(PluginUiKit.sub(a, "把 .png 放进 "
                        + new File(AppManifest.GAME_FILE_DIRECTORY_DIR, "skins").getAbsolutePath()
                        + " 再来绑定。"));
            }

            EditText acc = new EditText(a);
            acc.setHint("账号名");
            EditText skin = new EditText(a);
            skin.setHint("皮肤文件名（含 .png）");
            if (!skins.isEmpty()) {
                skin.setText(skins.get(0));
            }
            col.addView(acc);
            col.addView(skin);

            col.addView(PluginUiKit.label(a, "已绑定"));
            final LinearLayout listBox = new LinearLayout(a);
            listBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(listBox);
            final Runnable[] rebuild = new Runnable[1];
            rebuild[0] = () -> {
                try {
                    listBox.removeAllViews();
                    if (map.isEmpty()) {
                        listBox.addView(PluginUiKit.sub(a, "（还没有绑定）"));
                        return;
                    }
                    for (Map.Entry<String, String> e : map.entrySet()) {
                        listBox.addView(PluginUiKit.kv(a, e.getKey(), e.getValue()));
                    }
                } catch (Throwable ignored) {
                }
            };
            rebuild[0].run();

            PluginUiKit.sheet(a, "账号皮肤绑定", PluginUiKit.scroller(a, col), "绑定", () -> {
                String an = acc.getText() == null ? "" : acc.getText().toString().trim();
                String sn = skin.getText() == null ? "" : skin.getText().toString().trim();
                if (an.isEmpty() || sn.isEmpty()) {
                    PluginUiKit.toast(a, "账号名和皮肤文件名都要填");
                    return;
                }
                map.put(an, sn);
                saveMap(f, map);
                PluginUiKit.toast(a, "已绑定");
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== #6 账号数据统计 ====================

    public static void accountStats(MainActivity a) {
        try {
            PluginUiKit.work(a, "正在统计…", () -> {
                try {
                    File versions = new File(AppManifest.GAME_FILE_DIRECTORY_DIR, "versions");
                    File saves = new File(AppManifest.GAME_FILE_DIRECTORY_DIR, "saves");
                    int inst = 0;
                    File[] vs = versions.listFiles();
                    if (vs != null) {
                        for (File v : vs) {
                            if (v != null && v.isDirectory()) {
                                inst++;
                            }
                        }
                    }
                    int worlds = 0;
                    File[] ws = saves.listFiles();
                    if (ws != null) {
                        for (File w : ws) {
                            if (w != null && w.isDirectory()) {
                                worlds++;
                            }
                        }
                    }
                    box[0] = inst;
                    box[1] = worlds;
                    box[2] = PluginUiKit.dirSize(AppManifest.GAME_FILE_DIRECTORY_DIR == null
                            ? null : new File(AppManifest.GAME_FILE_DIRECTORY_DIR), 0);
                } catch (Throwable ignored) {
                }
            }, () -> {
                LinearLayout col = PluginUiKit.column(a);
                col.addView(PluginUiKit.text(a, "账号数据统计"));
                col.addView(PluginUiKit.sub(a, "统计的是**本机**的实例 / 存档 / 占用。"));
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.kv(a, "实例数", box[0] + " 个"));
                col.addView(PluginUiKit.kv(a, "存档数", box[1] + " 个"));
                col.addView(PluginUiKit.kv(a, "总占用", PluginUiKit.size(box[2])));
                col.addView(PluginUiKit.sub(a, " "));
                col.addView(PluginUiKit.sub(a, "★ **不统计「每个账号的游玩时长」** ✗ —— "
                        + "启动器不记录「某账号玩了多久」（要做得在启动流程里加埋点）。"
                        + "与其编一个数字，不如不做。"));
                col.addView(PluginUiKit.sub(a, "★ 账号分组用「多账号分组」插件；"
                        + "头像用「账号头像生成器」。"));
                PluginUiKit.sheet(a, "账号数据统计", PluginUiKit.scroller(a, col), "知道了", null);
            });
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    private static final long[] box = new long[3];

    // ==================== #2 / #4 做不到 ====================

    public static void loginHistory(MainActivity a) {
        notSupported(a, "登录历史记录",
                "要记「每次登录的时间、结果」，必须在**登录成功/失败的那一刻**写一条 —— "
                        + "而登录走 Minecraft 账户模块内部，没有对外回调钩子 ✗。"
                        + "另外「设备信息」在 Android 10+ 也拿不到（只有机型，"
                        + "没有可用的唯一标识），所以「异常登录提醒」也无从判断。",
                "现在能做的替代：用「多账号分组」把主号和小号分开，"
                        + "至少能一眼看出「这个账号是干嘛的」。");
    }

    public static void guestMode(MainActivity a) {
        notSupported(a, "游客模式",
                "「不能联机、不能保存」要在**所有**相关入口强制拦截 —— "
                        + "联机按钮、存档写入、下载安装、账号绑定…这是**全局策略** ✗。"
                        + "做成插件只能弹个提示，**拦不住** ✗ 那就是假的。",
                "现在能做的替代：用「多账号分组」建一个「测试号」分组，"
                        + "专门给试玩用；再配合「存档自动备份」保证主存档安全。");
    }

    private static void notSupported(MainActivity a, String title, String why, String alt) {
        try {
            LinearLayout col = PluginUiKit.column(a);
            col.addView(PluginUiKit.text(a, title));
            col.addView(PluginUiKit.sub(a, "★ 这个功能**没有实现**，原因如下："));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, why));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.sub(a, alt));
            PluginUiKit.sheet(a, title, PluginUiKit.scroller(a, col), "知道了", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== 文件工具 ====================

    private static Map<String, String> loadMap(File f) {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            if (!f.isFile()) {
                return m;
            }
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                //noinspection ResultOfMethodCallIgnored
                in.read(b);
            }
            for (String line : new String(b, "UTF-8").split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                String[] p = t.split("\t");
                if (p.length >= 2) {
                    m.put(p[0], p[1]);
                }
            }
        } catch (Throwable ignored) {
        }
        return m;
    }

    private static void saveMap(File f, Map<String, String> m) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : m.entrySet()) {
                sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(sb.toString().getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String safe(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' ? c : '_');
        }
        return sb.length() == 0 ? "avatar" : sb.toString();
    }
}
