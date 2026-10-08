package com.qcl.launcher.launcher.uis.main;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.launcher.uis.plugin.PluginServer;
import com.qcl.launcher.launcher.uis.plugin.PluginUiKit;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版：功能菜单（自绘卡片网格 + 自定义排版）。
 *
 * <p><b>为什么单独一个类</b>：上一版我把菜单 UI 直接写在 {@code MainUI.onCreate} 里，
 * 那个方法本来就长，再塞 60 行网格代码之后，变量重名和作用域问题极难排查，
 * 连着三次编译不过还没法定位 ✗ —— 这是那次失败的根因。
 * 抽成独立类之后，作用域干净，编译错误一眼就能看出。
 *
 * <p><b>自定义排版</b>：用户能决定菜单里显示哪些项、以及它们的顺序。
 * 配置存在 {@code AppManifest.SETTING_DIR/qcl_menu_layout.txt}，
 * 一行一个 id，**文件里出现的顺序就是菜单顺序**；没出现的项视为「隐藏」。
 * 文件不存在时用默认顺序（全部显示）。
 */
public final class MainMenuDialog {

    private MainMenuDialog() {
    }

    private static final String LAYOUT_FILE = "qcl_menu_layout.txt";

    /** 菜单项定义：id / 显示名 / 动作。 */
    private static final class Item {
        final String id;
        final String label;
        final Runnable action;

        Item(String id, String label, Runnable action) {
            this.id = id;
            this.label = label;
            this.action = action;
        }
    }

    /**
     * 弹出功能菜单。
     *
     * @param act     宿主 Activity
     * @param entries 主页那 8 个入口 View（按 账号/版本管理/版本列表/下载/大厅/实验室/多人联机/设置 顺序），
     *                菜单项通过 performClick() 复用它们原有的路由 —— 不重写任何路由逻辑
     */
    public static void show(final MainActivity act, final View[] entries) {
        try {
            final List<Item> all = buildItems(act, entries);
            final List<Item> shown = applyLayout(all);

            LinearLayout root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(act, 16);
            root.setPadding(pad, pad, pad, pad);

            // 标题行：标题 + 「排版」按钮
            LinearLayout head = new LinearLayout(act);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(act);
            title.setText("功能菜单");
            title.setTextSize(16f);
            title.setTextColor(0xFF3A342C);
            title.setTypeface(null, Typeface.BOLD);
            title.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(title);

            TextView edit = new TextView(act);
            edit.setText("排版");
            edit.setTextSize(13f);
            edit.setTextColor(0xFFA8452F);
            edit.setPadding(dp(act, 10), dp(act, 6), dp(act, 10), dp(act, 6));
            edit.setClickable(true);
            PluginUiKit.pressEffect(edit);
            head.addView(edit);
            root.addView(head);

            // 卡片网格（3 列）
            final AlertDialog[] holder = new AlertDialog[1];
            LinearLayout rowBox = null;
            for (int i = 0; i < shown.size(); i++) {
                if (i % 3 == 0) {
                    rowBox = new LinearLayout(act);
                    rowBox.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.bottomMargin = dp(act, 6);
                    rowBox.setLayoutParams(rlp);
                    root.addView(rowBox);
                }
                final Item it = shown.get(i);
                TextView cell = new TextView(act);
                cell.setText(it.label);
                cell.setTextSize(13f);
                cell.setTextColor(0xFF3A342C);
                cell.setGravity(Gravity.CENTER);
                cell.setBackgroundColor(0xFFF7F4EF);
                cell.setPadding(0, dp(act, 12), 0, dp(act, 12));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                clp.leftMargin = (i % 3 == 0) ? 0 : dp(act, 3);
                clp.rightMargin = (i % 3 == 2) ? 0 : dp(act, 3);
                cell.setLayoutParams(clp);
                cell.setClickable(true);
                cell.setFocusable(true);
                PluginUiKit.pressEffect(cell);
                cell.setOnClickListener(v -> {
                    try {
                        if (holder[0] != null) {
                            holder[0].dismiss();
                        }
                        it.action.run();
                    } catch (Throwable t) {
                        Toast.makeText(act, "打开失败：" + t, Toast.LENGTH_SHORT).show();
                    }
                });
                rowBox.addView(cell);
            }

            if (shown.isEmpty()) {
                TextView empty = new TextView(act);
                empty.setText("菜单是空的 —— 点右上角「排版」把功能加回来。");
                empty.setTextSize(13f);
                empty.setTextColor(0xFF8A8378);
                root.addView(empty);
            }

            AlertDialog dlg = new AlertDialog.Builder(act).setView(root).create();
            holder[0] = dlg;

            edit.setOnClickListener(v -> {
                try {
                    if (holder[0] != null) {
                        holder[0].dismiss();
                    }
                    showLayoutEditor(act, all);
                } catch (Throwable t) {
                    Toast.makeText(act, "排版打不开：" + t, Toast.LENGTH_SHORT).show();
                }
            });

            dlg.show();
            // 窗口进出场动效（只动 alpha/scale，合成层属性）
            try {
                android.view.Window w = dlg.getWindow();
                if (w != null) {
                    w.setWindowAnimations(0);
                    final View dv = w.getDecorView();
                    dv.setAlpha(0f);
                    dv.setScaleX(0.94f);
                    dv.setScaleY(0.94f);
                    dv.animate().alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(180)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Toast.makeText(act, "菜单打开失败：" + t, Toast.LENGTH_SHORT).show();
        }
    }

    /** 排版编辑器：勾选显示 / 上下移动顺序。 */
    private static void showLayoutEditor(final MainActivity act, final List<Item> all) {
        try {
            final List<String> order = readLayout();   // 当前顺序（含隐藏项在后面）
            final LinearLayout col = new LinearLayout(act);
            col.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(act, 16);
            col.setPadding(pad, pad, pad, pad);

            TextView tip = new TextView(act);
            tip.setText("勾选 = 显示在菜单里；↑↓ 调顺序。改完点「保存」。");
            tip.setTextSize(12f);
            tip.setTextColor(0xFF8A8378);
            tip.setPadding(0, 0, 0, dp(act, 10));
            col.addView(tip);

            final List<String> ids = new ArrayList<>();
            for (Item it : all) {
                ids.add(it.id);
            }
            // 按当前顺序重排（未在配置里的排后面）
            final List<String> working = new ArrayList<>();
            for (String id : order) {
                if (ids.contains(id) && !working.contains(id)) {
                    working.add(id);
                }
            }
            for (String id : ids) {
                if (!working.contains(id)) {
                    working.add(id);
                }
            }
            final List<String> visible = new ArrayList<>(order);   // 当前「显示」的集合

            final LinearLayout listBox = new LinearLayout(act);
            listBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(listBox);

            final Runnable[] rebuild = new Runnable[1];
            rebuild[0] = () -> {
                try {
                    listBox.removeAllViews();
                    for (int i = 0; i < working.size(); i++) {
                        final String id = working.get(i);
                        String label = id;
                        for (Item it : all) {
                            if (it.id.equals(id)) {
                                label = it.label;
                                break;
                            }
                        }
                        final int idx = i;
                        LinearLayout row = new LinearLayout(act);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(0, dp(act, 6), 0, dp(act, 6));

                        android.widget.CheckBox cb = new android.widget.CheckBox(act);
                        cb.setText(label);
                        cb.setTextSize(13f);
                        cb.setTextColor(0xFF3A342C);
                        cb.setChecked(visible.contains(id));
                        cb.setLayoutParams(new LinearLayout.LayoutParams(0,
                                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                        cb.setOnCheckedChangeListener((b, checked) -> {
                            if (checked) {
                                if (!visible.contains(id)) {
                                    visible.add(id);
                                }
                            } else {
                                visible.remove(id);
                            }
                        });
                        row.addView(cb);

                        TextView up = new TextView(act);
                        up.setText("↑");
                        up.setTextSize(16f);
                        up.setTextColor(0xFFA8452F);
                        up.setPadding(dp(act, 12), dp(act, 4), dp(act, 12), dp(act, 4));
                        up.setClickable(true);
                        PluginUiKit.pressEffect(up);
                        up.setOnClickListener(v -> {
                            if (idx > 0) {
                                String s = working.remove(idx);
                                working.add(idx - 1, s);
                                rebuild[0].run();
                            }
                        });
                        row.addView(up);

                        TextView down = new TextView(act);
                        down.setText("↓");
                        down.setTextSize(16f);
                        down.setTextColor(0xFFA8452F);
                        down.setPadding(dp(act, 12), dp(act, 4), dp(act, 12), dp(act, 4));
                        down.setClickable(true);
                        PluginUiKit.pressEffect(down);
                        down.setOnClickListener(v -> {
                            if (idx < working.size() - 1) {
                                String s = working.remove(idx);
                                working.add(idx + 1, s);
                                rebuild[0].run();
                            }
                        });
                        row.addView(down);

                        listBox.addView(row);
                    }
                } catch (Throwable ignored) {
                }
            };
            rebuild[0].run();

            new AlertDialog.Builder(act)
                    .setTitle("菜单排版")
                    .setView(PluginUiKit.scroller(act, col))
                    .setPositiveButton("保存", (d, w) -> {
                        saveLayout(working, visible);
                        Toast.makeText(act, "已保存", Toast.LENGTH_SHORT).show();
                    })
                    .setNeutralButton("恢复默认", (d, w) -> {
                        try {
                            File f = new File(AppManifest.SETTING_DIR, LAYOUT_FILE);
                            if (f.isFile()) {
                                //noinspection ResultOfMethodCallIgnored
                                f.delete();
                            }
                        } catch (Throwable ignored) {
                        }
                        Toast.makeText(act, "已恢复默认", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Throwable t) {
            Toast.makeText(act, "排版编辑器出错：" + t, Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 菜单项定义 ====================

    private static List<Item> buildItems(final MainActivity act, final View[] entries) {
        List<Item> list = new ArrayList<>();
        String[] ids = {"account", "manage", "version_list", "download",
                "lobby", "lab", "multi", "setting"};
        String[] labels = {"账号", "版本管理", "版本列表", "下载",
                "大厅", "实验室", "多人联机", "设置"};
        for (int i = 0; i < ids.length; i++) {
            final View v = (entries != null && i < entries.length) ? entries[i] : null;
            list.add(new Item(ids[i], labels[i], () -> {
                if (v != null) {
                    v.performClick();
                }
            }));
        }
        // 一键开服（不走 performClick，直接调插件入口）
        list.add(new Item("server", "一键开服", () -> PluginServer.open(act)));
        return list;
    }

    /** 按配置过滤 + 排序；配置为空则原样返回。 */
    private static List<Item> applyLayout(List<Item> all) {
        List<String> order = readLayout();
        if (order.isEmpty()) {
            return all;
        }
        List<Item> out = new ArrayList<>();
        for (String id : order) {
            for (Item it : all) {
                if (it.id.equals(id)) {
                    out.add(it);
                    break;
                }
            }
        }
        return out;
    }

    // ==================== 配置读写 ====================

    private static File layoutFile() {
        return new File(AppManifest.SETTING_DIR, LAYOUT_FILE);
    }

    /** 读配置。返回的列表 = 「显示的项 + 顺序」；空列表 = 没配置过（全部显示）。 */
    private static List<String> readLayout() {
        List<String> out = new ArrayList<>();
        try {
            File f = layoutFile();
            if (!f.isFile()) {
                return out;
            }
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                //noinspection ResultOfMethodCallIgnored
                in.read(b);
            }
            for (String line : new String(b, "UTF-8").split("\n")) {
                String t = line.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void saveLayout(List<String> order, List<String> visible) {
        try {
            File f = layoutFile();
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            StringBuilder sb = new StringBuilder();
            for (String id : order) {
                if (visible.contains(id)) {
                    sb.append(id).append('\n');
                }
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(sb.toString().getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    private static int dp(MainActivity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
    }
}
