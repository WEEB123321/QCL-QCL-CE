/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.content.Context
 *  android.os.AsyncTask
 *  android.util.Log
 */
package com.qcl.launcher.task;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import com.qcl.launcher.launcher.list.install.DownloadTaskListBean;
import com.qcl.launcher.utils.file.FileUtils;
import com.qcl.launcher.utils.io.NetworkStateUtils;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadTask
extends AsyncTask<ArrayList<DownloadTaskListBean>, Integer, ArrayList<DownloadTaskListBean>> {

    /**
     * ★ 1.3.0：取消标志。点「取消」后要能**中断正在进行的下载**，
     *   而不只是等它这一遍下完 —— 以前 cancel(true) 只在下一次重试时被检查到，
     *   当前正在下的那个文件会一直下完，表现就是「点取消还在后台自动下」。
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * ★★★ 1.4.3：暂停标志。与「取消」是两件不同的事，必须分开：
     *   - 取消 = 不要了 → 删掉半截文件，任务退出；
     *   - 暂停 = 还要 → **保留半截文件**，恢复时用 HTTP Range 从断点续传。
     *   1.3.0 之前只有取消语义，所以不能复用 cancelled 来做暂停，
     *   否则「暂停」会变成「取消 + 下次从头下」，弱网环境下等于白下。
     */
    private final AtomicBoolean paused = new AtomicBoolean(false);

    /** 暂停时工作线程在此等待，恢复 / 取消时唤醒（wait(500) 轮询兜底，不依赖 interrupt）。 */
    private final Object pauseLock = new Object();

    /**
     * ★ 1.4.3：记录「被暂停过、磁盘上留着半截文件」的路径。
     *   只有这些文件在下一轮才会带 Range 头续传 —— 否则无法区分
     *   「暂停留下的半截文件」和「上次失败留下的垃圾文件」，
     *   对后者发 Range 可能拿到 416（Range Not Satisfiable）。
     */
    private final Set<String> pausedPaths = Collections.synchronizedSet(new HashSet<String>());

    /**
     * ★ 社区版新增：仅 Wi-Fi 下载。开着时，若当前是按流量计费的网络，
     *   工作线程会在暂停等待点 park 住（保留分片），回到 Wi-Fi 自动放行。
     */
    private volatile boolean wifiOnly = false;

    /**
     * ★ 社区版新增：标记「当前这次暂停是网络造成的，不是用户点的」。
     *   必须在回到 Wi-Fi 时只自动放行这一类暂停 —— 用户手动暂停的不能被网络恢复顺手解掉。
     */
    private volatile boolean networkPaused = false;

    /**
     * ★★★ 社区版新增：全局下载限速（KB/s，0 = 不限速）。
     *
     * <p><b>为什么用静态字段而不是实例字段</b>：真正的下载循环
     * {@link #downloadFileMonitoredEx} 是 <b>static</b> 的，且被安装/校验等 40+ 处直接调用，
     * 改签名会把调用面铺得很大。限速本身是「全局策略」而非「单任务属性」，
     * 用静态 volatile 表达语义正确，也把改动面压到最小。
     *
     * <p><b>★ 默认 0（不限速）</b>：保证老用户行为完全不变 —— 没在设置里开过就永远不会触发。
     */
    private static volatile int speedLimitKbps = 0;

    /** ★ 社区版新增：设置全局下载限速（KB/s，&lt;=0 表示不限速）。 */
    public static void setSpeedLimitKbps(int kbps) {
        speedLimitKbps = kbps <= 0 ? 0 : kbps;
    }

    /** 单文件下载结果：成功。 */
    public static final int STATUS_SUCCESS = 0;
    /** 单文件下载结果：失败（网络/校验），可重试。 */
    public static final int STATUS_FAIL = 1;
    /** 单文件下载结果：被暂停，半截文件已保留，可续传。 */
    public static final int STATUS_PAUSED = 2;
    /** 单文件下载结果：被取消，半截文件已删除。 */
    public static final int STATUS_CANCELLED = 3;

    /**
     * ★ 1.3.0：请求取消并**中断正在进行的下载**。
     * 不能重写 {@code AsyncTask.cancel(boolean)}（它是 final 的），
     * 所以用这个方法：先设自己的标志（下载循环每读一块就查它），再走系统的 cancel。
     */
    public void requestCancel() {
        this.cancelled.set(true);
        // 唤醒所有因暂停而 park 的工作线程，否则它们要等 wait(500) 超时才醒来
        synchronized (this.pauseLock) {
            this.pauseLock.notifyAll();
        }
        cancel(true);
    }

    /** ★ 1.4.3：请求暂停。不中断连接，由下载循环在读完当前块后自行收手并保留半截文件。 */
    public void requestPause() {
        this.paused.set(true);
    }

    /** ★ 1.4.3：请求恢复。唤醒 park 中的工作线程，从断点继续。 */
    public void requestResume() {
        synchronized (this.pauseLock) {
            this.paused.set(false);
            this.pauseLock.notifyAll();
        }
    }

    public boolean isPaused() {
        return this.paused.get();
    }

    /**
     * ★ 1.4.3：工作线程的暂停等待点。它同时负责两件事：
     * <ol>
     *   <li><b>用户暂停</b> —— {@link #requestPause()} 置位后在此 park，{@link #requestResume()} 放行；</li>
     *   <li><b>仅 Wi-Fi 下载</b> —— 见 {@link #setWifiOnly(boolean)}。当前是按流量计费的网络时，
     *       这里会主动置位 {@code paused} 并 park，回到 Wi-Fi 后自动放行。</li>
     * </ol>
     *
     * <p>用 wait(500) 轮询而不是纯 wait()，是为了即使 notifyAll 丢失（极端时序）
     * 也能在 500ms 内自行醒来复查网络状态与取消标志，避免线程永久卡死。
     *
     * <p>★ 网络暂停也走 {@code paused} 这个标志位，是刻意的：{@code doInBackground} 尾部的
     * {@code awaitTermination} 循环靠 {@code paused} 判断「还在等」而不是「卡死了」。
     * 若网络暂停单独用一个标志，暂停超过 1 小时就会被误判成「下载完成」。
     *
     * @return true = 可以继续干活；false = 期间被取消，调用方应立即退出
     */
    private boolean awaitResumeIfPaused() {
        boolean notified = false;
        while (!this.cancelled.get()) {
            boolean netHold = this.shouldHoldForNetwork();
            if (netHold && !this.paused.get()) {
                // 因网络原因自动暂停：保留分片，回到 Wi-Fi 原地续传
                this.paused.set(true);
                this.networkPaused = true;
                if (!notified) {
                    notified = true;
                    this.feedback.onNetworkHold(true);
                }
            } else if (!netHold && this.networkPaused) {
                // 回到 Wi-Fi → 自动放行。只清「网络造成的暂停」；
                // 用户手动点的暂停（networkPaused 为 false）必须原样保持。
                synchronized (this.pauseLock) {
                    this.paused.set(false);
                    this.pauseLock.notifyAll();
                }
                this.networkPaused = false;
                notified = false;
                this.feedback.onNetworkHold(false);
            }
            if (!this.paused.get()) {
                break;
            }
            synchronized (this.pauseLock) {
                try {
                    this.pauseLock.wait(500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return !this.cancelled.get();
    }

    /** ★ 社区版新增：仅 Wi-Fi 开着、且当前处于计费网络 → 应 park 住不下载。 */
    private boolean shouldHoldForNetwork() {
        if (!this.wifiOnly) {
            return false;
        }
        Context c = this.ctx == null ? null : this.ctx.get();
        return NetworkStateUtils.isMeteredNetwork(c);
    }

    private final WeakReference<Context> ctx;
    private ArrayList<DownloadTaskListBean> failedFile;
    private final Feedback feedback;
    private int maxTask = 8;

    public DownloadTask(Context ctx, Feedback feedback) {
        this.ctx = new WeakReference<Context>(ctx);
        this.feedback = feedback;
        this.failedFile = new ArrayList();
    }

    public void setMaxTask(int maxTask) {
        this.maxTask = maxTask;
    }

    /**
     * ★ 社区版新增：开启「仅 Wi-Fi 下载」。必须在 {@code execute()} 之前调用。
     * 开着时，下载过程中切到计费网络会自动暂停（保留分片），回到 Wi-Fi 自动续传。
     */
    public void setWifiOnly(boolean wifiOnly) {
        this.wifiOnly = wifiOnly;
    }

    public void onPreExecute() {
    }

    @SuppressLint(value={"WrongThread"})
    public ArrayList<DownloadTaskListBean> doInBackground(ArrayList<DownloadTaskListBean> ... args) {
        ArrayList<DownloadTaskListBean> list = args[0];
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(this.maxTask, this.maxTask, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadPoolExecutor.CallerRunsPolicy());
        for (int j = 0; j < list.size(); ++j) {
            final DownloadTaskListBean bean = list.get(j);
            final String path = bean.path;
            final String sha1 = bean.sha1;
            threadPool.execute(() -> {
                // 已存在且校验通过 → 跳过（与原逻辑等价：原条件是「不存在，或存在但 sha1 不符」）
                if (new File(path).exists() && Objects.equals(FileUtils.getFileSha1(path), sha1)) {
                    return;
                }
                DownloadFeedback fb = new DownloadFeedback(){

                    @Override
                    public void updateProgress(long curr, long max) {
                        long progress = 100L * curr / max;
                        bean.progress = (int)progress;
                        DownloadTask.this.feedback.updateProgress(bean);
                    }

                    @Override
                    public void updateSpeed(String speed) {
                        DownloadTask.this.feedback.updateSpeed(speed);
                    }
                };
                // ★ 1.4.3：登记只做一次，重试不再重复插入列表项（原实现每轮重试都 add 一次）
                this.feedback.addTask(bean);
                int tryTimes = 5;
                int i = 0;
                while (i < tryTimes) {
                    if (this.cancelled.get()) {
                        break;
                    }
                    // 暂停中先 park，不消耗重试次数
                    if (!this.awaitResumeIfPaused()) {
                        break;
                    }
                    // 只对被暂停过的文件发 Range 续传请求；remove 保证仅第一次为 true，
                    // 这样万一服务器对 Range 回 416，第二次重试会自动退回「从头下」
                    boolean allowResume = this.pausedPaths.remove(path);
                    int r = DownloadTask.downloadFileMonitoredEx(bean.urlForAttempt(i), path, sha1, fb, this.cancelled, this.paused, allowResume);
                    if (r == DownloadTask.STATUS_SUCCESS) {
                        break;
                    }
                    if (r == DownloadTask.STATUS_CANCELLED) {
                        break;
                    }
                    if (r == DownloadTask.STATUS_PAUSED) {
                        // ★ 暂停不是失败：半截文件留着，记下路径，恢复后原地续传
                        this.pausedPaths.add(path);
                        continue;
                    }
                    ++i;
                }
                this.feedback.removeTask(bean);
                if (i >= tryTimes && !this.cancelled.get()) {
                    this.failedFile.add(bean);
                }
                if (this.cancelled.get()) {
                    threadPool.shutdownNow();
                }
            });
            int progress = (j + 1) * 100 / list.size();
            this.onProgressUpdate(progress);
        }
        threadPool.shutdown();
        try {
            // 暂停期间这里会一直等下去 —— 这正是我们要的：AsyncTask 保持 RUNNING，
            // 进度列表不销毁，用户点「继续」后原地接着下。
            // ★ 1.4.3：awaitTermination 有 1 小时上限，若不循环等待，
            //   用户把下载暂停超过 1 小时就会超时返回 → 被误判成「下载完成」。
            while (!threadPool.awaitTermination(1L, TimeUnit.HOURS)) {
                if (this.cancelled.get() || !this.paused.get()) {
                    // 取消，或并非因暂停而超时（真的卡死）→ 保持原行为退出
                    break;
                }
            }
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        return this.failedFile;
    }

    protected void onProgressUpdate(Integer ... p1) {
    }

    public void onPostExecute(ArrayList<DownloadTaskListBean> result) {
        for (DownloadTaskListBean bean : result) {
            Log.e((String)"url", (String)bean.url);
            Log.e((String)"path", (String)bean.path);
        }
        this.feedback.onFinished(result);
    }

    protected void onCancelled(ArrayList<DownloadTaskListBean> result) {
        this.feedback.onCancelled();
    }

    public static boolean isRightFile(String path, String sha1) {
        if (new File(path).exists()) {
            if (sha1 != null && !sha1.equals("")) {
                return Objects.equals(FileUtils.getFileSha1(path), sha1);
            }
            return true;
        }
        return false;
    }

    public static boolean downloadFileMonitored(String url, String nameOutput, String sha1, DownloadFeedback monitor) {
        return downloadFileMonitored(url, nameOutput, sha1, monitor, null);
    }

    /**
     * ★ 1.3.0：带取消标志的下载。下载循环里每读一块就检查一次取消，
     *   取消后立刻中断、删掉半截文件并返回 false。
     *   保留此签名是为了不动 40+ 处既有调用（安装/校验任务）。
     */
    public static boolean downloadFileMonitored(String url, String nameOutput, String sha1, DownloadFeedback monitor,
                                                AtomicBoolean cancelled) {
        return downloadFileMonitoredEx(url, nameOutput, sha1, monitor, cancelled, null, false) == STATUS_SUCCESS;
    }

    /**
     * ★★★ 1.4.3：支持「暂停 / 续传」的下载实现，是全项目唯一的下载循环。
     *
     * <p>三态语义（返回值不再是 boolean，因为「暂停」和「失败」必须区分开）：
     * <ul>
     *   <li>{@link #STATUS_CANCELLED} —— 取消：删掉半截文件；</li>
     *   <li>{@link #STATUS_PAUSED}    —— 暂停：flush 后关闭，**保留半截文件**；</li>
     *   <li>{@link #STATUS_FAIL}      —— 失败：交给上层重试；</li>
     *   <li>{@link #STATUS_SUCCESS}   —— 成功且 sha1 校验通过。</li>
     * </ul>
     *
     * <p>{@code allowResume=true} 时，若磁盘上已有半截文件，会带 {@code Range: bytes=N-} 续传；
     * 服务器不支持 Range（回 200 而非 206）则自动退回覆盖重下 —— 不假装续传成功。
     *
     * @param paused 传 null 表示该调用方不需要暂停能力（行为与 1.3.0 完全一致）
     * @param allowResume 是否允许对已存在的半截文件做 Range 续传
     */
    public static int downloadFileMonitoredEx(String url, String nameOutput, String sha1, DownloadFeedback monitor,
                                              AtomicBoolean cancelled, AtomicBoolean paused, boolean allowResume) {
        File nameOutputFile = new File(nameOutput);
        long startOffset = 0L;
        if (!nameOutputFile.exists()) {
            File parent = nameOutputFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
        } else if (!DownloadTask.isRightFile(nameOutput, sha1)) {
            if (allowResume && nameOutputFile.length() > 0L) {
                // 上次暂停留下的半截文件 → 保留，作为续传起点
                startOffset = nameOutputFile.length();
            } else {
                nameOutputFile.delete();
            }
        }
        // 注：isRightFile 通过时保留文件、下面以覆盖方式重下，与原实现行为一致

        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            URL downloadUrl = new URL(url);
            conn = (HttpURLConnection)downloadUrl.openConnection();
            conn.setDoInput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            if (startOffset > 0L) {
                conn.setRequestProperty("Range", "bytes=" + startOffset + "-");
            }
            conn.connect();
            int code = conn.getResponseCode();
            long total;
            if (startOffset > 0L && code == 416) {
                // ★ 1.4.3：416 = Range 不可满足（半截文件恰好等于完整大小 / 镜像换源后大小对不上）。
                //   当成可自愈：丢掉分片，返回失败让上层下一轮从头下（下一轮 allowResume 已为 false）。
                conn.disconnect();
                nameOutputFile.delete();
                return STATUS_FAIL;
            }
            if (startOffset > 0L && code == 206) {
                // ★ 1.4.3：必须核对 Content-Range 的起始偏移，否则一旦服务器/镜像换源，
                //   会把「另一份文件的后半段」拼到我们已有的前半段上，产出一个校验不过的怪文件。
                long serverStart = DownloadTask.parseContentRangeStart(conn.getHeaderField("Content-Range"));
                if (serverStart != startOffset) {
                    conn.disconnect();
                    nameOutputFile.delete();
                    return STATUS_FAIL;
                }
                // 服务器支持断点续传：追加写入，总长度 = 已有 + 剩余
                total = startOffset + conn.getContentLength();
                fos = new FileOutputStream(nameOutputFile, true);
            } else {
                // 200（服务器忽略 Range）或本就从 0 开始 → 覆盖重写
                startOffset = 0L;
                total = conn.getContentLength();
                fos = new FileOutputStream(nameOutputFile, false);
            }
            in = conn.getInputStream();
            long oval = startOffset;
            long lastLen = startOffset;
            long lastTime = System.currentTimeMillis();
            // ★ 社区版新增：限速用的「本窗口起点」，与速度显示的窗口分开维护 ——
            //   共用会让「每秒清零」与「显示速度」互相干扰，读数与节流都不准。
            long throttleWindowStart = System.currentTimeMillis();
            long throttleWindowBytes = 0L;
            byte[] buf = new byte[65535];
            int cur;
            while ((cur = in.read(buf)) != -1) {
                oval += cur;
                if (System.currentTimeMillis() - lastTime >= 1000L) {
                    if (monitor != null) {
                        // 原实现先赋值 lastLen 再相减，结果恒为 0；此处修正顺序让速度真实可读
                        monitor.updateSpeed(DownloadTask.formetFileSize(oval - lastLen) + "/s");
                    }
                    lastLen = oval;
                    lastTime = System.currentTimeMillis();
                }
                fos.write(buf, 0, cur);
                // ★★★ 社区版新增：下载限速（speedLimitKbps <= 0 时整段跳过，行为与原来完全一致）。
                //   算法：每 1 秒为一个窗口，窗口内已写字节数超过配额就 sleep 到窗口结束 ——
                //   是「平均限速」而不是「每块限速」，不会因为分块大小而失真。
                int limit = DownloadTask.speedLimitKbps;
                if (limit > 0) {
                    throttleWindowBytes += cur;
                    long windowElapsed = System.currentTimeMillis() - throttleWindowStart;
                    long quota = (long) limit * 1024L * windowElapsed / 1000L;
                    if (throttleWindowBytes > quota) {
                        long needMs = (throttleWindowBytes - quota) * 1000L / ((long) limit * 1024L);
                        if (needMs > 0L) {
                            try {
                                Thread.sleep(Math.min(needMs, 1000L));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                DownloadTask.closeQuietly(fos, in);
                                fos = null;
                                in = null;
                                return STATUS_CANCELLED;
                            }
                        }
                    }
                    if (System.currentTimeMillis() - throttleWindowStart >= 1000L) {
                        throttleWindowStart = System.currentTimeMillis();
                        throttleWindowBytes = 0L;
                    }
                }
                if (cancelled != null && cancelled.get()) {
                    // 用户点了取消：中断下载，删掉半截文件
                    DownloadTask.closeQuietly(fos, in);
                    fos = null;
                    in = null;
                    nameOutputFile.delete();
                    return STATUS_CANCELLED;
                }
                if (paused != null && paused.get()) {
                    // ★ 用户点了暂停：**不删文件**，下次带 Range 从这里继续
                    fos.flush();
                    DownloadTask.closeQuietly(fos, in);
                    fos = null;
                    in = null;
                    return STATUS_PAUSED;
                }
                if (monitor == null) continue;
                // 服务器未给 Content-Length（chunked）时 total 为 -1，算出来的百分比是负数，
                // 进度条会往回跳。这种情况干脆不更新，让进度条停在原处。
                if (total > 0L) {
                    monitor.updateProgress(oval, total);
                }
            }
            DownloadTask.closeQuietly(fos, in);
            fos = null;
            in = null;
        }
        catch (IOException e) {
            e.printStackTrace();
            DownloadTask.closeQuietly(fos, in);
            return STATUS_FAIL;
        }
        finally {
            DownloadTask.closeQuietly(fos, in);
            if (conn != null) {
                conn.disconnect();
            }
        }
        if (!DownloadTask.isRightFile(nameOutput, sha1)) {
            nameOutputFile.delete();
            return STATUS_FAIL;
        }
        return STATUS_SUCCESS;
    }

    /**
     * ★ 1.4.3：解析 {@code Content-Range: bytes 1234-5678/9999} 里的起始偏移。
     * 解析不出来返回 -1（调用方会因此判定为「偏移不符」并退回从头下，而不是盲目追加）。
     */
    private static long parseContentRangeStart(String contentRange) {
        if (contentRange == null) {
            return -1L;
        }
        try {
            int sp = contentRange.indexOf(' ');
            int dash = contentRange.indexOf('-', sp + 1);
            if (sp < 0 || dash < 0) {
                return -1L;
            }
            return Long.parseLong(contentRange.substring(sp + 1, dash).trim());
        }
        catch (Throwable t) {
            return -1L;
        }
    }

    private static void closeQuietly(Closeable... closeables) {        for (Closeable c : closeables) {
            if (c == null) continue;
            try {
                c.close();
            }
            catch (Throwable ignored) {
            }
        }
    }

    public static String formetFileSize(long fileS) {
        DecimalFormat df = new DecimalFormat("#.00");
        String fileSizeString = "";
        fileSizeString = fileS < 1024L ? df.format((double)fileS) + "B" : (fileS < 0x100000L ? df.format((double)fileS / 1024.0) + "K" : (fileS < 0x40000000L ? df.format((double)fileS / 1048576.0) + "M" : df.format((double)fileS / 1.073741824E9) + "G"));
        return fileSizeString;
    }

    public static abstract class Feedback {
        public abstract void addTask(DownloadTaskListBean var1);

        public abstract void updateProgress(DownloadTaskListBean var1);

        public abstract void updateSpeed(String var1);

        public abstract void removeTask(DownloadTaskListBean var1);

        public abstract void onFinished(ArrayList<DownloadTaskListBean> var1);

        public abstract void onCancelled();

        /**
         * ★ 社区版新增：「仅 Wi-Fi 下载」造成的自动暂停 / 自动恢复通知。
         * 默认空实现 —— 这是刻意选的：{@code Feedback} 是抽象类，加一个<b>有方法体的</b>
         * 方法不会迫使既有的 40+ 个匿名实现类全部改代码。
         *
         * @param holding true = 因网络被自动暂停；false = 回到 Wi-Fi 已自动恢复
         */
        public void onNetworkHold(boolean holding) {
        }
    }

    public static abstract class DownloadFeedback {
        public abstract void updateProgress(long var1, long var3);

        public abstract void updateSpeed(String var1);
    }
}
