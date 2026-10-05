package com.qcl.launcher.launcher.uis.log;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.log.LogCollector;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

import java.util.List;

/**
 * ★★★ 社区版新增：日志中心。
 *
 * <p><b>它和「启动诊断页」的分工</b>：诊断页看的是<b>启动路标</b>（Application 阶段，
 * 一次启动只有几十行，回答「崩在哪一步」）；日志中心看的是<b>运行期日志</b>
 * （启动之后的所有 {@code Log.*}，回答「运行中报了什么错」）。两者互补，不重复。
 *
 * <p><b>★ 为什么自己轮询而不是让采集器回调</b>：日志是高频的，逐行回调会把主线程刷爆。
 * 这里固定 1 秒拉一次快照做 diff，既保证「看起来是实时的」，又让主线程压力可控。
 *
 * <p><b>★ 为什么显示时截断</b>：缓冲里可能有几千行，全部塞进一个 Spannable 会让
 * 文本测量与高亮非常慢。只渲染**过滤后最新的 800 行**，并明确告知用户「显示 X / 共 Y 行」。
 */
public class LogCenterUI extends BaseUI implements View.OnClickListener {

    /** 单次渲染上限 —— 再多就该用关键字收窄，而不是硬塞 */
    private static final int MAX_RENDER_LINES = 800;
    /** 轮询间隔 */
    private static final long POLL_MS = 1000L;

    private LinearLayout logCenterUI;
    private TextView levelAll;
    private TextView levelWarn;
    private TextView levelError;
    private EditText keyword;
    private TextView statusText;
    private ScrollView scroll;
    private TextView logText;

    /** 0=全部 1=警告及以上 2=错误及以上 */
    private int levelFilter = 0;
    private String keywordFilter = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, POLL_MS);
        }
    };

    public LogCenterUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.logCenterUI = this.activity.findViewById(R.id.ui_log_center);
        this.levelAll = this.activity.findViewById(R.id.log_level_all);
        this.levelWarn = this.activity.findViewById(R.id.log_level_warn);
        this.levelError = this.activity.findViewById(R.id.log_level_error);
        this.keyword = this.activity.findViewById(R.id.log_keyword);
        this.statusText = this.activity.findViewById(R.id.log_status_text);
        this.scroll = this.activity.findViewById(R.id.log_scroll);
        this.logText = this.activity.findViewById(R.id.log_text);

        bind(this.levelAll);
        bind(this.levelWarn);
        bind(this.levelError);
        bind(this.activity.findViewById(R.id.log_refresh));
        bind(this.activity.findViewById(R.id.log_clear));
        bind(this.activity.findViewById(R.id.log_copy));
        bind(this.activity.findViewById(R.id.log_share));

        if (this.keyword != null) {
            this.keyword.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) {
                    keywordFilter = s == null ? "" : s.toString().trim();
                    render();
                }
            });
        }
        updateLevelButtons();
    }

    private void bind(View v) {
        if (v != null) {
            v.setOnClickListener(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.activity.showBarTitle(
                this.context.getResources().getString(R.string.log_center_title),
                canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.logCenterUI, this.activity, this.context, true);
        // ★ 进页面才起采集：别让 logcat 进程常驻（见 LogCollector 注释）
        LogCollector.startLive();
        // 先把本次进程的历史日志补进来，否则刚打开是空的
        if (LogCollector.snapshot().isEmpty()) {
            LogCollector.seedFromHistory(500);
        }
        render();
        this.handler.removeCallbacks(this.poller);
        this.handler.postDelayed(this.poller, POLL_MS);
    }

    @Override
    public void onStop() {
        super.onStop();
        this.handler.removeCallbacks(this.poller);
        LogCollector.stopLive();
        CustomAnimationUtils.hideViewToLeft(this.logCenterUI, this.activity, this.context, true);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.log_level_all) {
            this.levelFilter = 0;
            updateLevelButtons();
            render();
        } else if (id == R.id.log_level_warn) {
            this.levelFilter = 1;
            updateLevelButtons();
            render();
        } else if (id == R.id.log_level_error) {
            this.levelFilter = 2;
            updateLevelButtons();
            render();
        } else if (id == R.id.log_refresh) {
            render();
            toast(R.string.log_center_toast_refreshed);
        } else if (id == R.id.log_clear) {
            LogCollector.clear();
            render();
        } else if (id == R.id.log_copy) {
            copyVisible();
        } else if (id == R.id.log_share) {
            shareVisible();
        }
    }

    /** 选中的级别按钮高亮，其余恢复灰色 —— 让「当前过滤条件」一眼可见 */
    private void updateLevelButtons() {
        setLevelSelected(this.levelAll, this.levelFilter == 0);
        setLevelSelected(this.levelWarn, this.levelFilter == 1);
        setLevelSelected(this.levelError, this.levelFilter == 2);
    }

    private void setLevelSelected(TextView tv, boolean selected) {
        if (tv == null) {
            return;
        }
        tv.setTextColor(selected ? Color.parseColor("#FF185FA5") : Color.parseColor("#FF000000"));
    }

    // ------------------------------------------------------------------ 渲染

    private void render() {
        if (this.logText == null) {
            return;
        }
        String reason = LogCollector.unavailableReason();
        if (reason != null) {
            if (this.statusText != null) {
                this.statusText.setText(this.context.getString(R.string.log_center_unavailable, reason));
            }
        }

        List<String> all = LogCollector.snapshot();
        StringBuilder sb = new StringBuilder();
        int matched = 0;
        int shown = 0;
        for (String line : all) {
            if (!passLevel(line)) {
                continue;
            }
            if (!passKeyword(line)) {
                continue;
            }
            matched++;
        }
        // 只渲染尾部 MAX_RENDER_LINES 行：从后往前数到够为止
        int skip = Math.max(0, matched - MAX_RENDER_LINES);
        int idx = 0;
        for (String line : all) {
            if (!passLevel(line) || !passKeyword(line)) {
                continue;
            }
            if (idx++ < skip) {
                continue;
            }
            sb.append(line).append('\n');
            shown++;
        }

        SpannableString sp;
        if (this.keywordFilter.isEmpty()) {
            sp = new SpannableString(sb.toString());
        } else {
            sp = new SpannableString(sb.toString());
            highlight(sp, this.keywordFilter);
        }
        this.logText.setText(sp);

        if (this.statusText != null && reason == null) {
            this.statusText.setText(this.context.getString(
                    R.string.log_center_status, shown, all.size(),
                    LogCollector.isLive() ? this.context.getString(R.string.log_center_live_on)
                                          : this.context.getString(R.string.log_center_live_off)));
        }
        // 自动滚到底：新日志总是最有用的那几行
        if (this.scroll != null) {
            this.scroll.post(new Runnable() {
                @Override
                public void run() {
                    if (scroll != null) {
                        scroll.fullScroll(View.FOCUS_DOWN);
                    }
                }
            });
        }
    }

    private boolean passLevel(String line) {
        if (this.levelFilter == 0) {
            return true;
        }
        char c = LogCollector.levelOf(line);
        if (this.levelFilter == 1) {
            return c == 'W' || c == 'E' || c == 'F';
        }
        return c == 'E' || c == 'F';
    }

    private boolean passKeyword(String line) {
        return this.keywordFilter.isEmpty() || line.contains(this.keywordFilter);
    }

    /** 给所有命中关键词的位置加一个淡黄底 —— 比变色更醒目，且不破坏原文可读性 */
    private void highlight(SpannableString sp, String kw) {
        String s = sp.toString();
        int from = 0;
        int count = 0;
        while (count < 500) {
            int i = s.indexOf(kw, from);
            if (i < 0) {
                break;
            }
            sp.setSpan(new BackgroundColorSpan(Color.parseColor("#80FFEB3B")),
                    i, i + kw.length(), 0);
            from = i + kw.length();
            count++;
        }
    }

    // ------------------------------------------------------------------ 复制 / 分享

    private String currentText() {
        return this.logText == null || this.logText.getText() == null
                ? "" : this.logText.getText().toString();
    }

    private void copyVisible() {
        try {
            ClipboardManager cm = (ClipboardManager)
                    this.context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(
                        this.context.getString(R.string.log_center_title), currentText()));
                toast(R.string.log_center_toast_copied);
                return;
            }
        } catch (Throwable ignored) {
        }
        toast(R.string.log_center_toast_copy_failed);
    }

    private void shareVisible() {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT,
                    this.context.getString(R.string.log_center_share_subject));
            intent.putExtra(Intent.EXTRA_TEXT, currentText());
            this.activity.startActivity(Intent.createChooser(intent,
                    this.context.getString(R.string.log_center_share_chooser)));
        } catch (Throwable t) {
            toast(R.string.log_center_toast_share_failed);
        }
    }

    private void toast(int res) {
        try {
            Toast.makeText(this.context, res, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
