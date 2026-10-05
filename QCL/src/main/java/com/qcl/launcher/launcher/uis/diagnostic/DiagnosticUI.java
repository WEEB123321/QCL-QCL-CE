package com.qcl.launcher.launcher.uis.diagnostic;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.qcl.launcher.R;
import com.qcl.launcher.launcher.MainActivity;
import com.qcl.launcher.launcher.ProcessExitDiagnostics;
import com.qcl.launcher.launcher.StartupTrace;
import com.qcl.launcher.launcher.diagnostic.StartupDoctor;
import com.qcl.launcher.launcher.uis.tools.BaseUI;
import com.qcl.launcher.utils.animation.CustomAnimationUtils;

/**
 * ★★★ 社区版新增：启动诊断页。
 *
 * <p><b>为什么要单独做一页</b>：启动器「一打开就闪退」时，用户手上其实是有线索的
 * （{@link StartupTrace} 一直在写路标），但线索藏在
 * {@code /sdcard/QCL/startup_trace.log} 这种用户不容易打开的位置，
 * 于是每一次排查都变成「我远程猜 → 让用户去某个目录找文件 → 用户找不到 → 再来一轮」。
 * 本页把这件事缩短成：<b>打开这一页 → 点「分享给作者」→ 发出去</b>。
 *
 * <p><b>为什么用「分享」而不是「上传」</b>：需求书明确不建自建服务器。
 * {@code ACTION_SEND} 走的是用户自己的微信/QQ，不需要任何后端，
 * 而且比截图更完整（截图常常截不到关键的那几行）。
 *
 * <p><b>只读</b>：本页不提供「清除路标」之类的写操作 ——
 * {@code StartupTrace.init()} 每次启动都会截断重写，所以显示的内容永远属于「本次启动」，
 * 不存在需要手动清理的陈旧数据。多一个按钮只会多一个把线索弄丢的机会。
 */
public class DiagnosticUI extends BaseUI implements View.OnClickListener {

    private LinearLayout diagnosticUI;
    /** 「上次启动没有走完」整段（平时隐藏，只有真发生过才出现） */
    private LinearLayout lastSection;
    private TextView lastText;
    private TextView traceText;
    private TextView deviceText;
    /** ★ 社区版新增：自动排查结果容器 */
    private LinearLayout wizardContainer;

    public DiagnosticUI(Context context, MainActivity mainActivity) {
        super(context, mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.diagnosticUI = this.activity.findViewById(R.id.ui_diagnostic);
        this.lastSection = this.activity.findViewById(R.id.diagnostic_last_section);
        this.lastText = this.activity.findViewById(R.id.diagnostic_last_text);
        this.traceText = this.activity.findViewById(R.id.diagnostic_trace_text);
        this.deviceText = this.activity.findViewById(R.id.diagnostic_device_text);
        this.wizardContainer = this.activity.findViewById(R.id.diagnostic_wizard_container);

        // 逐个判空：布局万一被改坏，也只是这一页少了几个按钮，不该把启动器带走
        bind(R.id.diagnostic_refresh);
        bind(R.id.diagnostic_copy);
        bind(R.id.diagnostic_share);
    }

    private void bind(int id) {
        try {
            View v = this.activity.findViewById(id);
            if (v != null) {
                v.setOnClickListener(this);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        this.activity.showBarTitle(
                this.context.getResources().getString(R.string.diagnostic_ui_title),
                canGoBackToLast(), true);
        CustomAnimationUtils.showViewFromLeft(this.diagnosticUI, this.activity, this.context, true);
        refresh();
    }

    @Override
    public void onStop() {
        super.onStop();
        CustomAnimationUtils.hideViewToLeft(this.diagnosticUI, this.activity, this.context, true);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.diagnostic_refresh) {
            refresh();
            toast(R.string.diagnostic_toast_refreshed);
        } else if (id == R.id.diagnostic_copy) {
            copyAll();
        } else if (id == R.id.diagnostic_share) {
            shareAll();
        }
    }

    // ------------------------------------------------------------------ 展示

    private void refresh() {
        renderWizard();
        if (this.deviceText != null) {
            this.deviceText.setText(deviceInfo());
        }
        if (this.traceText != null) {
            String all = StartupTrace.readAll();
            this.traceText.setText(all.isEmpty()
                    ? this.context.getString(R.string.diagnostic_trace_empty)
                    : all);
        }
        if (this.lastSection != null) {
            boolean abnormal = StartupTrace.lastRunWasAbnormal();
            this.lastSection.setVisibility(abnormal ? View.VISIBLE : View.GONE);
            if (abnormal && this.lastText != null) {
                this.lastText.setText(StartupTrace.lastRunTail());
            }
        }
    }

    // ------------------------------------------------------------------ 复制 / 分享

    private void copyAll() {
        try {
            ClipboardManager cm = (ClipboardManager)
                    this.context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(
                        this.context.getString(R.string.diagnostic_share_subject), buildReport()));
                toast(R.string.diagnostic_toast_copied);
                return;
            }
        } catch (Throwable ignored) {
            // 落到下面的失败提示
        }
        toast(R.string.diagnostic_toast_copy_failed);
    }

    private void shareAll() {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT,
                    this.context.getString(R.string.diagnostic_share_subject));
            intent.putExtra(Intent.EXTRA_TEXT, buildReport());
            this.activity.startActivity(Intent.createChooser(intent,
                    this.context.getString(R.string.diagnostic_share_chooser)));
        } catch (Throwable t) {
            // 用户手机上没有可接收 text/plain 的应用（极简 ROM 上会出现）
            toast(R.string.diagnostic_toast_share_failed);
        }
    }

    /**
     * 组装完整报告。
     * <p>顺序刻意是「设备信息 → 上次断点 → 本次路标」：
     * 对方（作者）从上往下读，读完设备就知道该按哪个架构排查，读完断点就知道该看哪一段代码。
     */
    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.context.getString(R.string.diagnostic_report_header)).append('\n');
        sb.append(deviceInfo()).append('\n');
        // ★★★ 本轮新增：系统侧的退出记录 —— 它是唯一能覆盖「native 崩溃 / 崩在 Provider 阶段」
        //   这类 Java 侧观测不到的情况的证据。不需要 root，也不需要 adb。
        sb.append("----- 系统记录的进程退出原因（无需 root/adb）-----\n");
        sb.append(ProcessExitDiagnostics.describeLastExit(this.context)).append('\n');
        if (StartupTrace.lastRunWasAbnormal()) {
            sb.append("----- 上次启动未走完（最后一行 = 断点）-----\n");
            sb.append(StartupTrace.lastRunTail()).append('\n');
        }
        sb.append("----- 本次启动路标 -----\n");
        String all = StartupTrace.readAll();
        sb.append(all.isEmpty()
                ? "(空 —— 连 Application.attachBaseContext 都没走完)\n"
                : all);
        sb.append('\n');
        sb.append("路标文件：").append(StartupTrace.pathForUser()).append('\n');
        String rp = StartupTrace.reportPathForUser();
        if (!rp.isEmpty()) {
            sb.append("诊断报告文件（一个文件包含以上全部信息）：").append(rp).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 自动排查

    /**
     * ★ 社区版新增：把 {@link StartupDoctor} 的逐项结论渲染成卡片。
     * <p>每张卡片：左侧状态点（绿/红）+ 检查项名，下面一行结论；
     * 不通过时再补一行「怎么办」。全部异常都被吞掉 —— 诊断页本身绝不能成为新的崩溃源。
     */
    private void renderWizard() {
        if (this.wizardContainer == null) {
            return;
        }
        this.wizardContainer.removeAllViews();
        try {
            for (StartupDoctor.Item item : StartupDoctor.run(this.context, this.activity)) {
                this.wizardContainer.addView(buildWizardCard(item));
            }
            // ★ 社区版新增：智能推荐（按当前版本 + 本机设备给建议）。
            //   接在诊断项后面：上面是「有没有坏」，这里是「怎么调更好」，性质不同但都在这一页看最顺。
            try {
                for (StartupDoctor.Item item : com.qcl.launcher.launcher.diagnostic.DeviceAdvisor
                        .run(this.context, this.activity)) {
                    this.wizardContainer.addView(buildWizardCard(item));
                }
            } catch (Throwable ignored) {
            }
            // ★ 社区版新增：启动提速的自证 —— 本次启动省掉了多少次全文件哈希。
            //   放在这里是为了让「启动变快了」这件事**可被验证**，而不是只有一句口头承诺。
            try {
                TextView cacheLine = new TextView(this.context);
                cacheLine.setText("启动提速：" + com.qcl.launcher.launcher.launch.check.VerifyCache.stats());
                cacheLine.setTextSize(11f);
                cacheLine.setTextColor(Color.parseColor("#FF185FA5"));
                cacheLine.setPadding(px(4), px(10), px(4), px(4));
                this.wizardContainer.addView(cacheLine);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            TextView tv = new TextView(this.context);
            tv.setText(String.valueOf(t));
            tv.setTextSize(10f);
            this.wizardContainer.addView(tv);
        }
    }

    private View buildWizardCard(StartupDoctor.Item item) {
        LinearLayout box = new LinearLayout(this.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.qcl_button_gray);
        box.setPadding(px(10), px(8), px(10), px(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(6);
        box.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this.context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView mark = new TextView(this.context);
        mark.setText(item.ok ? "●" : "●");
        mark.setTextSize(11f);
        mark.setTextColor(item.ok ? Color.parseColor("#FF2E7D32") : Color.parseColor("#FFB3261E"));
        head.addView(mark);

        TextView title = new TextView(this.context);
        title.setText("  " + item.title);
        title.setTextSize(12f);
        title.setTextColor(Color.BLACK);
        head.addView(title);
        box.addView(head);

        TextView detail = new TextView(this.context);
        detail.setText(item.detail);
        detail.setTextSize(10f);
        detail.setTextColor(Color.parseColor("#99000000"));
        box.addView(detail);

        if (!item.ok && item.advice != null && !item.advice.isEmpty()) {
            TextView advice = new TextView(this.context);
            advice.setText("→ " + item.advice);
            advice.setTextSize(10f);
            advice.setTextColor(Color.parseColor("#FF185FA5"));
            advice.setPadding(0, px(4), 0, 0);
            box.addView(advice);
        }
        return box;
    }

    private int px(int dp) {
        return Math.round(dp * this.context.getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ 设备信息

    private String deviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("应用版本：").append(appVersionName())
                .append(" (code ").append(appVersionCode()).append(")\n");
        sb.append("Android：").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("机型：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("ABI：").append(join(Build.SUPPORTED_ABIS)).append('\n');
        sb.append("内存：").append(memoryLine()).append('\n');
        sb.append("上次退出：").append(lastExitShort()).append('\n');
        return sb.toString();
    }

    /** 上次进程退出原因的一行摘要（详情见 buildReport 里的完整段落） */
    private String lastExitShort() {
        try {
            // ★ 不要把 lastExit() 的返回值赋给 ApplicationExitInfo 变量 ——
            //   那会在 Android 10 及以下引入 API 30 的类型引用，抛 NoClassDefFoundError。
            //   取字段/拼摘要的逻辑统一收在 ProcessExitDiagnostics 内部。
            return ProcessExitDiagnostics.lastExitOneLine(this.context);
        } catch (Throwable t) {
            return "(读取失败)";
        }
    }

    private String memoryLine() {
        try {
            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
            long maxMb = rt.maxMemory() / (1024L * 1024L);
            ActivityManager am = (ActivityManager)
                    this.context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                return "Java 堆 " + usedMb + "/" + maxMb + " MB，整机可用 "
                        + (mi.availMem / (1024L * 1024L)) + " MB / 共 "
                        + (mi.totalMem / (1024L * 1024L)) + " MB";
            }
            return "Java 堆 " + usedMb + "/" + maxMb + " MB";
        } catch (Throwable t) {
            return "(读取失败)";
        }
    }

    private String appVersionName() {
        try {
            String v = this.context.getPackageManager()
                    .getPackageInfo(this.context.getPackageName(), 0).versionName;
            return v == null ? "?" : v;
        } catch (Throwable t) {
            return "?";
        }
    }

    private int appVersionCode() {
        try {
            return this.context.getPackageManager()
                    .getPackageInfo(this.context.getPackageName(), 0).versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String join(String[] arr) {
        if (arr == null || arr.length == 0) {
            return "?";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private void toast(int res) {
        try {
            Toast.makeText(this.context, res, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
