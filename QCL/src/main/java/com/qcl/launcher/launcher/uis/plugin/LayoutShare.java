package com.qcl.launcher.launcher.uis.plugin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Environment;
import android.widget.LinearLayout;

import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.layout.QclLayout;

import java.io.File;

/**
 * ★★★ 社区版：界面排版的导入 / 导出（阶段 ④）。
 *
 * <p><b>为什么主要走剪贴板而不是文件选择器</b>：
 * <ul>
 *   <li>布局就是一小段 JSON（几个控件 × 四个字段）✓ 剪贴板完全放得下 ✓</li>
 *   <li><b>剪贴板能直接发到 QQ / 微信群里分享</b> ✓✓ —— 文件还得先存再发 ✗</li>
 *   <li>不用处理「文件在哪 / 权限 / 选择器」这一堆事 ✓ 少一整条出错路径 ✗</li>
 * </ul>
 * 同时也**导出一份到文件** ✓ —— 作为备份，万一剪贴板被系统清了 ✓
 *
 * <p><b>导入后的生效时机</b>：排版在**页面 onCreate 时应用** ✓
 * 所以导入完要让用户**退出再进主页**（或者切一下页面）✓
 * 界面上会如实说明这一点 ✓ —— 不假装「立刻生效」✗
 */
public final class LayoutShare {

    private LayoutShare() {
    }

    /** 导出的文件（放在 /sdcard/QCL-CE/ —— 和崩溃日志、启动路标同一个目录 ✓） */
    private static File exportFile() {
        return new File(Environment.getExternalStorageDirectory(), "QCL-CE/qcl-layout.json");
    }

    /** 插件入口。 */
    public static void ui(MainActivity a) {
        try {
            LinearLayout col = PluginUiKit.column(a);

            col.addView(PluginUiKit.text(a, "排版导入 / 导出"));
            col.addView(PluginUiKit.sub(a, "★ 把「界面排版」的设置导出成一段文本，"
                    + "发到群里别人粘贴就能用 ✓"));
            col.addView(PluginUiKit.sub(a, " "));
            col.addView(PluginUiKit.label(a, "当前状态"));
            col.addView(PluginUiKit.sub(a, "已自定义的页面："
                    + (QclLayout.has("main") ? "主页 ✓" : "（无）")
                    + "　——　其它页面还没接入排版 ✗"));
            col.addView(PluginUiKit.sub(a, " "));

            col.addView(PluginUiKit.label(a, "导出"));
            PluginUiKit.row(a, "复制排版到剪贴板", v -> doExportClip(a));
            col.addView(PluginUiKit.sub(a, "　（复制后可以直接发到 QQ / 微信 ✓）"));
            col.addView(PluginUiKit.sub(a, " "));
            PluginUiKit.row(a, "另存一份到文件", v -> doExportFile(a));
            col.addView(PluginUiKit.sub(a, "　路径：" + exportFile().getAbsolutePath()));
            col.addView(PluginUiKit.sub(a, " "));

            col.addView(PluginUiKit.label(a, "导入"));
            PluginUiKit.row(a, "从剪贴板导入", v -> doImportClip(a));
            col.addView(PluginUiKit.sub(a, "　（先复制别人的排版文本，再点这里 ✓）"));
            col.addView(PluginUiKit.sub(a, " "));
            PluginUiKit.row(a, "从文件导入", v -> doImportFile(a));
            col.addView(PluginUiKit.sub(a, "　（读上面那个文件 ✓）"));
            col.addView(PluginUiKit.sub(a, " "));

            col.addView(PluginUiKit.label(a, "重置"));
            PluginUiKit.row(a, "恢复默认排版", v -> doReset(a));
            col.addView(PluginUiKit.sub(a, "　（清掉全部自定义 ✓ 回到 XML 原样 ✓）"));
            col.addView(PluginUiKit.sub(a, " "));

            col.addView(PluginUiKit.sub(a, "★ 导入 / 重置之后**要退出再进主页**才生效 ✗ ——"
                    + "排版是在页面创建时应用的 ✓ 界面上不假装能立刻变 ✗"));

            PluginUiKit.sheet(a, "排版导入 / 导出", PluginUiKit.scroller(a, col), "关闭", null);
        } catch (Throwable t) {
            PluginUiKit.toast(a, "出错：" + t);
        }
    }

    // ==================== 导出 ====================

    private static void doExportClip(MainActivity a) {
        try {
            String json = QclLayout.exportJson();
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                PluginUiKit.toast(a, "这台设备没有剪贴板服务 ✗");
                return;
            }
            cm.setPrimaryClip(ClipData.newPlainText("QCL-CE 排版", json));
            PluginUiKit.log(a, "已导出排版到剪贴板（" + json.length() + " 字符）");
            PluginUiKit.toast(a, "已复制 ✓ 可以直接发到群里了");
        } catch (Throwable t) {
            PluginUiKit.toast(a, "导出失败：" + t);
        }
    }

    private static void doExportFile(MainActivity a) {
        try {
            File out = exportFile();
            File f = QclLayout.exportTo(out);
            if (f == null) {
                PluginUiKit.toast(a, "写文件失败 ✗（可能没有存储权限）");
                return;
            }
            PluginUiKit.log(a, "排版已导出到 " + f.getAbsolutePath());
            PluginUiKit.toast(a, "已保存到 " + f.getAbsolutePath());
        } catch (Throwable t) {
            PluginUiKit.toast(a, "导出失败：" + t);
        }
    }

    // ==================== 导入 ====================

    private static void doImportClip(MainActivity a) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                PluginUiKit.toast(a, "剪贴板里没有东西 ✗ 先复制排版文本");
                return;
            }
            ClipData cd = cm.getPrimaryClip();
            if (cd == null || cd.getItemCount() == 0) {
                PluginUiKit.toast(a, "剪贴板里没有文本 ✗");
                return;
            }
            CharSequence cs = cd.getItemAt(0).coerceToText(a);
            if (cs == null) {
                PluginUiKit.toast(a, "剪贴板内容读不出来 ✗");
                return;
            }
            applyImport(a, cs.toString(), "剪贴板");
        } catch (Throwable t) {
            PluginUiKit.toast(a, "导入失败：" + t);
        }
    }

    private static void doImportFile(MainActivity a) {
        try {
            File f = exportFile();
            if (!f.isFile()) {
                PluginUiKit.toast(a, "找不到文件：" + f.getAbsolutePath());
                return;
            }
            if (!QclLayout.importFrom(f)) {
                PluginUiKit.toast(a, "文件内容不是有效的排版 ✗");
                return;
            }
            PluginUiKit.toast(a, "已从文件导入 ✓ 退出再进主页生效");
        } catch (Throwable t) {
            PluginUiKit.toast(a, "导入失败：" + t);
        }
    }

    /** 导入的公共部分：解析 + 校验 + 明确告知生效时机 ✓ */
    private static void applyImport(MainActivity a, String text, String from) {
        if (text == null || text.trim().isEmpty()) {
            PluginUiKit.toast(a, from + "里是空的 ✗");
            return;
        }
        if (!text.contains("{") || !text.contains("}")) {
            PluginUiKit.toast(a, from + "里的内容不像排版数据 ✗（排版是一段 JSON）");
            return;
        }
        if (!QclLayout.importJson(text)) {
            PluginUiKit.toast(a, "解析失败 ✗ —— " + from + "里的内容不是有效的排版");
            return;
        }
        PluginUiKit.log(a, "已从" + from + "导入排版（" + text.length() + " 字符）");
        PluginUiKit.toast(a, "已从" + from + "导入 ✓ **退出再进主页**才生效");
    }

    // ==================== 重置 ====================

    private static void doReset(MainActivity a) {
        try {
            QclLayout.resetAll();
            PluginUiKit.log(a, "排版已恢复默认");
            PluginUiKit.toast(a, "已恢复默认 ✓ 退出再进主页生效");
        } catch (Throwable t) {
            PluginUiKit.toast(a, "重置失败：" + t);
        }
    }
}
