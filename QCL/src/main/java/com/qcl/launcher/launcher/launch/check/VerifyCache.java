package com.qcl.launcher.launcher.launch.check;

import android.util.Log;

import com.qcl.launcher.manifest.AppManifest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * ★★★ 社区版新增：文件校验结果缓存（启动提速的关键一环）。
 *
 * <h3>解决什么问题</h3>
 * 启动检查会对**每一个** library jar、**每一个** asset 对象做**全文件 SHA-1**。
 * 一次正常的 Minecraft 安装有 <b>3000~5000 个 asset 对象</b>（合计几百 MB）加几十个库。
 * 也就是说：<b>每次点「启动」，都要把这好几百 MB 从闪存里完整读一遍算哈希</b> ——
 * 在手机上就是十几秒起步，而且每次启动都重来一遍（哪怕文件一个字节都没变）。
 * 这就是「启动太慢」的主因。
 *
 * <h3>怎么做到「不重复算」又不误判</h3>
 * 缓存键 = <b>(路径, 文件大小, 最后修改时间, 期望 sha1)</b>，四者全中才认为「上次已经验过且结论仍是有效」。
 * <ul>
 *   <li>文件被替换/改写 → mtime 必变 → 缓存失效 → 老老实实重算；</li>
 *   <li>文件被截断/追加 → size 必变 → 缓存失效；</li>
 *   <li>换了另一个游戏目录/另一个版本 → 路径不同 → 各自独立。</li>
 * </ul>
 * ★ 理论上只有「内容变了但大小与 mtime 都被刻意改回原样」才能骗过它 ——
 * 那已经属于人为构造，不是正常使用会遇到的情况。
 *
 * <h3>为什么用纯文本而不是 JSON</h3>
 * 这个文件可能有上万行，启动时要读它。纯文本按行解析比 Gson 反序列化一个
 * 上万节点的对象图快得多，也不需要引入新的数据结构。路径里不会出现制表符。
 */
public final class VerifyCache {

    private static final String TAG = "QCLVerifyCache";

    /** 缓存文件（放 App 私有设置目录，不污染游戏目录） */
    private static String cachePath() {
        try {
            return AppManifest.SETTING_DIR + "/verify_cache.txt";
        } catch (Throwable t) {
            return null;
        }
    }

    /** 上限：超过就整体丢弃重来（防止文件无限膨胀；重来一次的代价只是下次启动慢一点） */
    private static final int MAX_ENTRIES = 40000;

    /** 两次落盘之间至少间隔多久（毫秒）—— 避免校验过程中频繁写盘 */
    private static final long SAVE_INTERVAL_MS = 10000L;

    private static final Object LOCK = new Object();
    /** path → "size|mtime|sha1" */
    private static HashMap<String, String> map;
    private static boolean dirty;
    private static long lastSave;

    /** 本次进程命中/未命中计数，供「启动诊断」页展示（让提速可被验证） */
    private static int hitCount;
    private static int missCount;

    private VerifyCache() {
    }

    private static HashMap<String, String> ensureLoaded() {
        if (map != null) {
            return map;
        }
        HashMap<String, String> m = new HashMap<String, String>();
        String p = cachePath();
        if (p != null) {
            BufferedReader r = null;
            try {
                File f = new File(p);
                if (f.isFile() && f.length() > 0 && f.length() < 32L * 1024 * 1024) {
                    r = new BufferedReader(new FileReader(f), 1 << 16);
                    String line;
                    int guard = 0;
                    while ((line = r.readLine()) != null && guard++ < MAX_ENTRIES) {
                        int t1 = line.indexOf('\t');
                        if (t1 <= 0) {
                            continue;
                        }
                        int t2 = line.indexOf('\t', t1 + 1);
                        if (t2 <= 0) {
                            continue;
                        }
                        m.put(line.substring(0, t1), line.substring(t1 + 1));
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "读取校验缓存失败（忽略，本次全部重算）: " + t);
            } finally {
                if (r != null) {
                    try {
                        r.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        map = m;
        return map;
    }

    /** 上次是否已用**同样的 sha1** 验过这个「同样大小、同样 mtime」的文件。 */
    public static boolean hit(String path, long size, long mtime, String sha1) {
        try {
            if (path == null || sha1 == null) {
                return false;
            }
            String v = ensureLoaded().get(path);
            if (v != null && v.equals(size + "|" + mtime + "|" + sha1)) {
                synchronized (LOCK) {
                    hitCount++;
                }
                return true;
            }
            synchronized (LOCK) {
                missCount++;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void put(String path, long size, long mtime, String sha1) {
        try {
            if (path == null || sha1 == null) {
                return;
            }
            HashMap<String, String> m = ensureLoaded();
            synchronized (LOCK) {
                if (m.size() >= MAX_ENTRIES) {
                    m.clear();
                }
                m.put(path, size + "|" + mtime + "|" + sha1);
                dirty = true;
            }
            saveIfDue(false);
        } catch (Throwable ignored) {
        }
    }

    private static void saveIfDue(boolean force) {
        try {
            if (!dirty) {
                return;
            }
            long now = System.currentTimeMillis();
            if (!force && now - lastSave < SAVE_INTERVAL_MS) {
                return;
            }
            synchronized (LOCK) {
                if (!dirty) {
                    return;
                }
                lastSave = now;
                dirty = false;
                writeNow(map);
            }
        } catch (Throwable t) {
            Log.w(TAG, "写入校验缓存失败（忽略）: " + t);
        }
    }

    /** 校验流程结束时调一次，保证这一轮的成果落盘（下次启动就能全命中）。 */
    public static void flush() {
        saveIfDue(true);
    }

    /** ★ 内部把 IO 异常全部吞掉并记日志：缓存写不进去只是「下次启动慢一点」，不该影响任何功能。 */
    private static void writeNow(HashMap<String, String> m) {
        try {
            writeNowInner(m);
        } catch (Throwable t) {
            Log.w(TAG, "写入校验缓存失败（忽略）: " + t);
        }
    }

    private static void writeNowInner(HashMap<String, String> m) throws Exception {
        String p = cachePath();
        if (p == null) {
            return;
        }
        FileOutputStream fos = null;
        OutputStreamWriter w = null;
        File tmp = null;
        try {
            File dir = new File(p).getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            // ★ 先写临时文件再改名：中途被杀进程也不会留下半截文件把下次启动的缓存读坏
            tmp = new File(p + ".tmp");
            fos = new FileOutputStream(tmp);
            w = new OutputStreamWriter(fos, "UTF-8");
            StringBuilder sb = new StringBuilder(m.size() * 96);
            Iterator<Map.Entry<String, String>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> e = it.next();
                sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
            w.write(sb.toString());
            w.flush();
            fos.getFD().sync();
            w.close();
            w = null;
            fos = null;
            File dst = new File(p);
            if (dst.exists() && !dst.delete()) {
                // 删不掉就退回直接覆盖（仍比不写强）
            }
            if (!tmp.renameTo(dst)) {
                Log.w(TAG, "校验缓存改名失败，本次不落盘");
            }
            tmp = null;
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Throwable ignored) {
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (Throwable ignored) {
                }
            }
            if (tmp != null && tmp.exists()) {
                try {
                    tmp.delete();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 供「启动诊断」页展示：本次启动省掉了多少次哈希、实际算了几次。 */
    public static String stats() {
        int h = hitCount;
        int mi = missCount;
        if (h + mi == 0) {
            return "本次启动未做文件校验";
        }
        return "本次启动文件校验：命中缓存 " + h + " 个，实际重算 " + mi + " 个"
                + (h + mi > 0 ? "（省掉约 " + (100 * h / (h + mi)) + "% 的读盘）" : "");
    }
}
