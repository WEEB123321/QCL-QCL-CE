package com.qcl.launcher.launcher.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.list.local.game.GameListBean;
import com.qcl.launcher.launcher.setting.SettingUtils;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.gson.GsonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * ★ 社区版新增：全局搜索 / 命令面板。
 *
 * <p><b>★★★ 为什么必须有它</b>：需求书的「收纳原则」把一级入口压到五个，
 * 低频功能全部收进二级页 —— 收得越干净，越需要一条兜底的检索路径，
 * 否则玩家会陷入"功能明明有，但找不到"的困境。这个面板就是那条兜底路径。
 *
 * <p><b>★ 与"新页面"的区别</b>：它不新增任何功能，只是给<b>已有</b>页面与操作
 * 建一张索引表。所以它符合需求书的去重规则（不重复创建入口）——
 * 所有条目最终都指向现有页面或现有能力。
 *
 * <p><b>索引分三段</b>：页面（现有 25 个页面里对外可见的那些）、操作（检查更新 / 切换渲染器等）、
 * 已安装版本（直接切到某个版本）。三段都用同一套 contains 匹配，零学习成本。
 */
public class GlobalSearchDialog extends Dialog {

    /** 一条索引项：属于哪一段、显示名、匹配关键词、点击后干什么 */
    private static class Entry {
        final int section;      // 0=页面 1=操作 2=版本
        final String label;
        final String keywords;
        final Runnable action;

        Entry(int section, String label, String keywords, Runnable action) {
            this.section = section;
            this.label = label;
            this.keywords = keywords == null ? "" : keywords;
            this.action = action;
        }

        boolean matches(String q) {
            if (q == null || q.isEmpty()) {
                return true;
            }
            String lower = q.toLowerCase();
            return label.toLowerCase().contains(lower) || keywords.toLowerCase().contains(lower);
        }
    }

    private final MainActivity activity;
    private final List<Entry> entries = new ArrayList<>();
    private LinearLayout results;

    public GlobalSearchDialog(MainActivity activity) {
        super(activity);
        this.activity = activity;
    }

    public static void show(MainActivity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            new GlobalSearchDialog(activity).show();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_global_search);

        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = Math.round(480f * getContext().getResources().getDisplayMetrics().density);
            lp.height = Math.round(360f * getContext().getResources().getDisplayMetrics().density);
            window.setAttributes(lp);
        }

        results = findViewById(R.id.dialog_search_results);
        EditText input = findViewById(R.id.dialog_search_input);
        buildIndex();
        render("");
        if (input != null) {
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    render(s == null ? "" : s.toString().trim());
                }
            });
        }
    }

    /** 建索引：全部指向现有页面 / 现有能力，不新增任何功能 */
    private void buildIndex() {
        entries.clear();

        // ---- 页面段 ----
        addPage(R.string.nav_home, "首页 主界面 home main 启动", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.mainUI, 0);
            }
        });
        addPage(R.string.nav_instance, "实例 版本列表 instance version", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.versionListUI, 1);
            }
        });
        addPage(R.string.nav_download, "下载 download", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.downloadUI, 2);
            }
        });
        addPage(R.string.nav_plugin, "插件 plugin", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.pluginUI, 3);
            }
        });
        addPage(R.string.nav_mine, "我的 设置 setting", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.settingUI, 4);
            }
        });
        addPage(R.string.backup_ui_title, "备份 恢复 回滚 backup restore", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.backupUI, -1);
            }
        });
        addPage(R.string.launcher_scroll_lobby, "大厅 新闻 模组 lobby news", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.lobbyUI, -1);
            }
        });
        addPage(R.string.lab_ui_title, "实验室 工具 lab tools", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.labUI, -1);
            }
        });
        addPage(R.string.launcher_scroll_text_account, "账户 账号 登录 account login", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.accountUI, -1);
            }
        });
        addPage(R.string.launcher_scroll_multi_player, "多人 联机 联机模块 multiplayer", new Runnable() {
            @Override
            public void run() {
                dismiss();
                com.qcl.launcher.launcher.terracotta.MultiplayerDialogHelper
                        .showEnable(activity, activity);
            }
        });

        // ---- 操作段 ----
        addAction("检查更新", "更新 update check", new Runnable() {
            @Override
            public void run() {
                dismiss();
                try {
                    new com.qcl.launcher.update.UpdateChecker(activity, activity).checkAuto();
                } catch (Throwable ignored) {
                }
            }
        });
        addAction("切换渲染器", "渲染器 renderer 图形 driver", new Runnable() {
            @Override
            public void run() {
                dismiss();
                try {
                    com.qcl.launcher.launcher.launch.RendererPicker.show(activity,
                            activity.privateGameSetting,
                            activity.publicGameSetting.currentVersion, null);
                } catch (Throwable ignored) {
                }
            }
        });
        addAction("备份当前实例", "备份 backup snapshot", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.backupUI, -1);
            }
        });
        addAction("打开插件目录", "插件 目录 plugin folder", new Runnable() {
            @Override
            public void run() {
                gotoPage(activity.uiManager.pluginUI, 3);
            }
        });

        // ---- 版本段（已安装版本，直接切过去）----
        try {
            ArrayList<GameListBean> versions = SettingUtils.getLocalVersionInfo(
                    activity.launcherSetting.gameFileDirectory,
                    activity.publicGameSetting.currentVersion);
            for (final GameListBean bean : versions) {
                if (bean == null || bean.name == null || bean.name.isEmpty()) {
                    continue;
                }
                entries.add(new Entry(2, bean.name, "版本 " + bean.name, new Runnable() {
                    @Override
                    public void run() {
                        switchToVersion(bean.name);
                    }
                }));
            }
        } catch (Throwable ignored) {
        }
    }

    private void addPage(int labelRes, String keywords, Runnable action) {
        entries.add(new Entry(0, activity.getString(labelRes), keywords, action));
    }

    private void addAction(String label, String keywords, Runnable action) {
        entries.add(new Entry(1, label, keywords, action));
    }

    /** 切到某个已安装版本：写回 publicGameSetting，再回首页让 Spinner 重新读取 */
    private void switchToVersion(String name) {
        try {
            activity.publicGameSetting.currentVersion =
                    activity.launcherSetting.gameFileDirectory + "/versions/" + name;
            GsonUtils.savePublicGameSetting(activity.publicGameSetting,
                    AppManifest.SETTING_DIR + "/public_game_setting.json");
        } catch (Throwable ignored) {
        }
        dismiss();
        gotoPage(activity.uiManager.mainUI, 0);
    }

    /** 切页面；tab >= 0 时同步底部导航高亮，-1 表示这是二级页（导航条会自动隐藏） */
    private void gotoPage(com.qcl.launcher.launcher.uis.tools.BaseUI ui, int tab) {
        dismiss();
        if (ui == null || activity.uiManager == null) {
            return;
        }
        if (tab >= 0) {
            activity.uiManager.switchMainUITab(ui);
        } else {
            activity.uiManager.switchMainUI(ui);
        }
    }

    /** 渲染结果：段标题 + 条目；无匹配时给一句明确提示，不留空白 */
    private void render(String query) {
        if (results == null) {
            return;
        }
        results.removeAllViews();
        int[] sectionTitles = {R.string.search_section_page, R.string.search_section_action,
                R.string.search_section_version};
        int shown = 0;
        for (int section = 0; section < sectionTitles.length; section++) {
            List<Entry> hits = new ArrayList<>();
            for (Entry e : entries) {
                if (e.section == section && e.matches(query)) {
                    hits.add(e);
                }
            }
            if (hits.isEmpty()) {
                continue;
            }
            results.addView(sectionHeader(activity.getString(sectionTitles[section])));
            for (Entry e : hits) {
                results.addView(entryRow(e));
                shown++;
            }
        }
        if (shown == 0) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.search_empty);
            empty.setTextSize(12f);
            empty.setTextColor(Color.parseColor("#B3FFFFFF"));
            empty.setPadding(dp(6), dp(10), dp(6), dp(10));
            results.addView(empty);
        }
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(10f);
        tv.setTextColor(Color.parseColor("#80FFFFFF"));
        tv.setPadding(dp(6), dp(10), dp(6), dp(2));
        return tv;
    }

    private TextView entryRow(final Entry entry) {
        TextView tv = new TextView(activity);
        tv.setText(entry.label);
        tv.setTextSize(13f);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(8), dp(10), dp(8), dp(10));
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setBackgroundResource(R.drawable.qcl_button_gray);
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    entry.action.run();
                } catch (Throwable ignored) {
                }
            }
        });
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
