package com.qcl.launcher.launcher.uis.plugin;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.launcher.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * ★★★ 社区版：插件界面工具箱。
 *
 * <p>几十个插件如果各写各的对话框，代码会失控，观感也会七零八落。
 * 这里把「插件常用到的界面」收成一组静态方法 —— 每个插件只描述**内容**，
 * 外观统一由这里决定（同一套圆角、间距、字号、配色）。
 *
 * <p>提供的四件套：
 * <ul>
 *   <li>{@link #info} 纯说明（一件事 + 一段话）</li>
 *   <li>{@link #confirm} 二次确认（危险操作前必须过这一关）</li>
 *   <li>{@link #list} 列表选择（点一行执行一个动作）</li>
 *   <li>{@link #sheet} 自定义内容（最灵活，自己往里塞 View）</li>
 * </ul>
 */
public final class PluginUiKit {

    private PluginUiKit() {
    }

    // ==================== dp / 样式 ====================

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 卡片：统一的浅色玻璃底 + 圆角。 */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(com.qcl.launcher.R.drawable.qcl_button_gray);
        int p = dp(c, 12);
        l.setPadding(p, p, p, p);
        return l;
    }

    /** 小标题（分类名 / 区块名）。 */
    public static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12f);
        t.setTextColor(Color.parseColor("#FF1B1B1B"));
        t.setAlpha(0.65f);
        t.setLetterSpacing(0.08f);
        int l = dp(c, 10), tp = dp(c, 14);
        t.setPadding(l, tp, l, dp(c, 6));
        return t;
    }

    /** 正文。 */
    public static TextView text(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(13f);
        t.setTextColor(Color.parseColor("#FF1B1B1B"));
        t.setLineSpacing(dp(c, 3), 1f);
        return t;
    }

    /** 次要文字。 */
    public static TextView sub(Context c, String text) {
        TextView t = text(c, text);
        t.setTextSize(11.5f);
        t.setAlpha(0.6f);
        return t;
    }

    /** 可点击的行（列表项）。 */
    public static TextView row(Context c, String text, View.OnClickListener onClick) {
        TextView t = text(c, text);
        t.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        t.setBackgroundResource(com.qcl.launcher.R.drawable.qcl_button_gray);
        t.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 6);
        t.setLayoutParams(lp);
        return t;
    }

    /** 纵向可滚动的容器 —— 内容可能很长时用它当对话框主体。 */
    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** 把一段内容套进 ScrollView（高度上限 60% 屏高，避免把对话框撑爆）。 */
    public static ScrollView scroller(Context c, View content) {
        ScrollView s = new ScrollView(c);
        s.setScrollbarFadingEnabled(false);
        s.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    /** 一个「键值对」小行：左边标签、右边值。 */
    public static LinearLayout kv(Context c, String k, String v) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(c, 5), 0, dp(c, 5));

        TextView kk = sub(c, k);
        kk.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(kk);

        TextView vv = text(c, v == null ? "—" : v);
        vv.setGravity(Gravity.END);
        vv.setTypeface(Typeface.MONOSPACE);
        r.addView(vv);
        return r;
    }

    // ==================== 四个对话框 ====================

    /** 纯说明。 */
    public static void info(MainActivity a, String title, String body) {
        show(a, title, wrap(a, text(a, body)), "知道了", null);
    }

    /** 二次确认。危险操作（删/覆盖/重置）必须走这个。 */
    public static void confirm(MainActivity a, String title, String body, Runnable onOk) {
        show(a, title, wrap(a, text(a, body)), "确定", onOk);
    }

    /** 列表选择。点一行执行一个动作，然后关闭。 */
    public static void list(MainActivity a, String title, List<String> items,
                            Picker picker) {
        LinearLayout col = column(a);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            col.addView(row(a, items.get(i), v -> {
                dismiss();
                if (picker != null) {
                    picker.onPick(idx);
                }
            }));
        }
        show(a, title, scroller(a, col), "取消", null);
    }

    /** 自定义内容。自己往 {@code body} 里塞 View；点「确定」执行 onOk。 */
    public static void sheet(MainActivity a, String title, View body,
                             String okText, Runnable onOk) {
        show(a, title, body, okText, onOk);
    }

    /** 列表选择回调。 */
    public interface Picker {
        void onPick(int index);
    }

    // ==================== 内部 ====================

    private static AlertDialog current;

    private static void dismiss() {
        try {
            if (current != null && current.isShowing()) {
                current.dismiss();
            }
        } catch (Throwable ignored) {
        }
        current = null;
    }

    private static View wrap(MainActivity a, View content) {
        LinearLayout l = column(a);
        int p = dp(a, 4);
        l.setPadding(p, p, p, 0);
        l.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroller(a, l);
    }

    private static void show(MainActivity a, String title, View body,
                             String okText, Runnable onOk) {
        try {
            dismiss();
            AlertDialog.Builder b = new AlertDialog.Builder(a);
            b.setTitle(title);
            b.setView(body);
            b.setCancelable(true);
            b.setPositiveButton(okText, (d, w) -> {
                if (onOk != null) {
                    try {
                        onOk.run();
                    } catch (Throwable t) {
                        toast(a, "操作失败：" + t);
                    }
                }
            });
            if (onOk != null) {
                b.setNegativeButton("取消", null);
            }
            current = b.create();
            current.show();
        } catch (Throwable t) {
            // 弹框本身失败绝不能把插件页带崩
            toast(a, "打不开：" + t);
        }
    }

    public static void toast(Context c, String msg) {
        try {
            Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    // ==================== 常用小工具 ====================

    /** 把字节数变成人能读的（1.2 GB / 340 MB / 12 KB）。 */
    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double k = bytes / 1024.0;
        if (k < 1024) {
            return String.format(java.util.Locale.US, "%.0f KB", k);
        }
        double m = k / 1024.0;
        if (m < 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", m);
        }
        return String.format(java.util.Locale.US, "%.2f GB", m / 1024.0);
    }

    /** 目录占用（递归，带深度上限防死循环）。 */
    public static long dirSize(java.io.File f, int depth) {
        if (f == null || depth > 12) {
            return 0;
        }
        try {
            if (f.isFile()) {
                return f.length();
            }
            java.io.File[] kids = f.listFiles();
            if (kids == null) {
                return 0;
            }
            long n = 0;
            for (java.io.File k : kids) {
                n += dirSize(k, depth + 1);
            }
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 列目录（不抛异常，失败返回空表）。 */
    public static List<java.io.File> listFiles(java.io.File dir) {
        List<java.io.File> out = new ArrayList<>();
        try {
            java.io.File[] fs = dir == null ? null : dir.listFiles();
            if (fs != null) {
                for (java.io.File f : fs) {
                    out.add(f);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 按扩展名过滤。 */
    public static List<java.io.File> listByExt(java.io.File dir, String ext) {
        List<java.io.File> out = new ArrayList<>();
        for (java.io.File f : listFiles(dir)) {
            if (f.getName().toLowerCase(java.util.Locale.US).endsWith(ext)) {
                out.add(f);
            }
        }
        return out;
    }

    /** 安全读文本文件（上限 2MB，防意外读到大文件卡住）。 */
    public static String readText(java.io.File f) {
        try {
            if (f == null || !f.isFile() || f.length() > 2 * 1024 * 1024) {
                return null;
            }
            byte[] buf = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int off = 0;
                while (off < buf.length) {
                    int r = in.read(buf, off, buf.length - off);
                    if (r < 0) {
                        break;
                    }
                    off += r;
                }
            }
            return new String(buf, 0, buf.length, "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 简易 JSON 取值（不引入依赖，只处理本项目自己写的扁平结构）。 */
    public static String jsonStr(String json, String key) {
        if (TextUtils.isEmpty(json) || TextUtils.isEmpty(key)) {
            return null;
        }
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0) {
            return null;
        }
        int c = json.indexOf(':', i + pat.length());
        if (c < 0) {
            return null;
        }
        int q1 = json.indexOf('"', c + 1);
        if (q1 < 0) {
            return null;
        }
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return null;
        }
        return json.substring(q1 + 1, q2);
    }
}
