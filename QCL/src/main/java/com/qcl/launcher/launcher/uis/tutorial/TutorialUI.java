package com.qcl.launcher.launcher.uis.tutorial;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

/**
 * ★★★ 社区版新增：新手上路 + 常见问题（本地知识库）。
 *
 * <p><b>为什么不做「强制分步引导」</b>：强制引导（遮罩 + 高亮 + 必须点对才放行）
 * 对老玩家是纯打扰，而且启动器的操作路径本来就短（装运行环境 → 下版本 → 启动）。
 * 这里做成<b>可随时查阅的一页</b>：新玩家第一次进来能一次看完，老玩家当手册用。
 *
 * <p><b>★ 内容全部来自本地字符串数组</b>（{@code strings_tutorial.xml}）：
 * 不联网、不建服务器、不依赖任何外部服务；改文案只改资源，不动代码。
 *
 * <p><b>★ 诚实原则</b>：Q&A 里只写「这个启动器真的会这样」的答案，
 * 不抄别家启动器的说法。例如「打开停在安装运行环境页」是本项目真实行为
 * （入口 Activity 就是 RuntimeInstallActivity），必须写清楚，否则用户会当成故障。
 */
public class TutorialUI extends BaseUI implements View.OnClickListener {

    private LinearLayout tutorialUI;
    private LinearLayout stepsContainer;
    private LinearLayout faqContainer;

    public TutorialUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.tutorialUI = this.activity.findViewById(R.id.ui_tutorial);
        this.stepsContainer = this.activity.findViewById(R.id.tutorial_steps_container);
        this.faqContainer = this.activity.findViewById(R.id.tutorial_faq_container);
        View done = this.activity.findViewById(R.id.tutorial_done);
        if (done != null) {
            done.setOnClickListener(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.activity.showBarTitle(
                this.context.getResources().getString(R.string.tutorial_title),
                canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.tutorialUI, this.activity, this.context, true);
        buildContent();
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.tutorialUI, this.activity, this.context, true);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.tutorial_done) {
            // 记一笔「看过了」，用于首页是否再提示（不强制，不阻断）
            try {
                this.context.getSharedPreferences("qcl_community", Context.MODE_PRIVATE)
                        .edit().putBoolean("tutorialSeen", true).apply();
            } catch (Throwable ignored) {
            }
            toast(R.string.tutorial_done_toast);
            if (canGoBackToLast()) {
                try {
                    this.activity.onBackPressed();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------------ 内容构建

    private void buildContent() {
        fill(this.stepsContainer, R.array.tutorial_steps, true);
        fill(this.faqContainer, R.array.tutorial_faq, false);
    }

    /**
     * 把字符串数组渲染成一列卡片。每条格式为 {@code 标题|正文}（用竖线分隔，
     * 分隔符在资源里不会出现，简单可靠）。
     *
     * @param numbered true = 前面加序号（步骤），false = 不加（Q&A）
     */
    private void fill(LinearLayout container, int arrayRes, boolean numbered) {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        String[] items;
        try {
            items = this.context.getResources().getStringArray(arrayRes);
        } catch (Throwable t) {
            return;
        }
        if (items == null) {
            return;
        }
        int n = 0;
        for (String raw : items) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            n++;
            int sep = raw.indexOf('|');
            String title = sep >= 0 ? raw.substring(0, sep) : raw;
            String body = sep >= 0 ? raw.substring(sep + 1) : "";
            container.addView(card((numbered ? n + ". " : "") + title, body));
        }
    }

    private View card(String title, String body) {
        LinearLayout box = new LinearLayout(this.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.qcl_button_gray);
        box.setPadding(px(10), px(10), px(10), px(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        box.setLayoutParams(lp);

        TextView t = new TextView(this.context);
        t.setText(title);
        t.setTextSize(13f);
        t.setTextColor(Color.BLACK);
        box.addView(t);

        if (body != null && !body.isEmpty()) {
            TextView b = new TextView(this.context);
            b.setText(body);
            b.setTextSize(11f);
            b.setTextColor(Color.parseColor("#CC000000"));
            b.setPadding(0, px(4), 0, 0);
            box.addView(b);
        }
        return box;
    }

    private int px(int dp) {
        return Math.round(dp * this.context.getResources().getDisplayMetrics().density);
    }

    private void toast(int res) {
        try {
            Toast.makeText(this.context, res, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
