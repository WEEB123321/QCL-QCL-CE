package com.qcl.launcher.launcher.backup;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.qcl.launcher.manifest.AppManifest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ★ 社区版新增：实例备份 / 回滚。
 *
 * <p><b>★★★ 为什么整包拷贝而不是增量/压缩</b>：
 * 需求书要的是"装坏了能退回去"这个确定性。增量备份要维护变更集、压缩要额外 CPU 与
 * 峰值内存 —— 而本项目目标机型是<b>低内存手机</b>（README：按剩余内存动态夹取堆）。
 * 所以这里选最笨但最稳的做法：<b>流式整目录拷贝</b>（64KB 缓冲，任何时刻内存占用恒定），
 * 不压缩、不增量。代价是占磁盘，换的是"回滚一定成功"。
 *
 * <p><b>★ 回滚的安全性</b>：回滚前先把<b>当前</b>实例目录整体挪到 {@code <备份id>_safety_<时间>}，
 * 再拷贝备份内容回去。这样"回滚"本身也是可逆的 —— 万一备份是坏的，玩家还能从安全副本找回现状。
 * 这是备份功能最容易被忽略、也最容易造成二次数据损失的一步。
 *
 * <p><b>★ 无数据库</b>：索引写在 {@code filesDir/backups/index.json}，外层带 {@code schemaVersion}。
 * 将来加字段时，旧索引缺字段读出来是 null/0，按版本补齐即可 —— 等价于数据库迁移，但不需要 Room。
 */
public final class InstanceBackupHelper {

    private static final String TAG = "QCLBackup";

    /** 索引结构版本。加字段时 +1，并在读取处按版本补齐默认值。 */
    private static final int SCHEMA_VERSION = 1;

    private static final String INDEX_FILE = "index.json";

    private static final int BUFFER_SIZE = 64 * 1024;

    private InstanceBackupHelper() {
    }

    /** 进度回调：只在阶段切换时回调，避免高频刷 UI */
    public interface Callback {
        void onStage(String stage);
    }

    /** 索引外层包装 —— 带 schemaVersion，替代数据库迁移 */
    private static class Index {
        int schemaVersion = SCHEMA_VERSION;
        List<BackupRecord> records = new ArrayList<>();
    }

    private static File indexFile() {
        return new File(AppManifest.BACKUP_DIR, INDEX_FILE);
    }

    private static Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }

    // ------------------------------------------------------------------ 索引读写

    public static synchronized List<BackupRecord> listRecords() {
        List<BackupRecord> out = new ArrayList<>();
        try {
            File f = indexFile();
            if (!f.isFile() || f.length() == 0L) {
                return out;
            }
            byte[] b = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int n = in.read(b);
            in.close();
            Index idx = gson().fromJson(new String(b, 0, n, "UTF-8"), Index.class);
            if (idx == null) {
                return out;
            }
            // ★ 迁移点：schemaVersion 低于当前时在此补齐新字段默认值（当前 v1 无需处理）
            if (idx.records != null) {
                for (BackupRecord r : idx.records) {
                    if (r != null && r.isValid()) {
                        out.add(r);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "读备份索引失败（当作空索引处理）: " + t);
        }
        Collections.sort(out, new Comparator<BackupRecord>() {
            @Override
            public int compare(BackupRecord a, BackupRecord b) {
                return Long.compare(b.createdAt, a.createdAt);   // 新的在前
            }
        });
        return out;
    }

    private static synchronized void saveRecords(List<BackupRecord> records) {
        try {
            File dir = new File(AppManifest.BACKUP_DIR);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            Index idx = new Index();
            idx.schemaVersion = SCHEMA_VERSION;
            idx.records = records;
            byte[] data = gson().toJson(idx).getBytes("UTF-8");
            File tmp = new File(AppManifest.BACKUP_DIR, INDEX_FILE + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(data);
            out.close();
            File target = indexFile();
            if (target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(target);
        } catch (Throwable t) {
            Log.w(TAG, "写备份索引失败: " + t);
        }
    }

    // ------------------------------------------------------------------ 创建备份

    /**
     * 把 {@code versionDir} 整个打成快照。
     *
     * @return 成功返回记录，失败返回 null（调用方负责提示）
     */
    public static BackupRecord createBackup(String instanceName, File versionDir, Callback cb) {
        if (versionDir == null || !versionDir.isDirectory()) {
            return null;
        }
        String id = safeName(instanceName) + "_" + timestamp();
        File dest = new File(AppManifest.BACKUP_DIR, id);
        try {
            if (!dest.exists() && !dest.mkdirs()) {
                Log.w(TAG, "创建备份目录失败: " + dest.getAbsolutePath());
                return null;
            }
            if (cb != null) {
                cb.onStage("copy");
            }
            long size = copyRecursive(versionDir, dest);
            if (size < 0) {
                deleteRecursive(dest);
                return null;
            }
            BackupRecord r = new BackupRecord();
            r.id = id;
            r.instanceName = instanceName;
            r.sourceDir = versionDir.getAbsolutePath();
            r.createdAt = System.currentTimeMillis();
            r.sizeBytes = size;
            r.backupDir = dest.getAbsolutePath();
            List<BackupRecord> records = listRecords();
            records.add(r);
            saveRecords(records);
            return r;
        } catch (Throwable t) {
            Log.w(TAG, "创建备份失败: " + t);
            deleteRecursive(dest);
            return null;
        }
    }

    // ------------------------------------------------------------------ 回滚

    /**
     * 把实例回滚到指定快照。当前实例目录会先被整体挪成安全副本，再拷回快照内容。
     *
     * @return 成功与否
     */
    public static boolean rollback(BackupRecord record, File targetVersionDir, Callback cb) {
        if (record == null || !record.isValid() || targetVersionDir == null) {
            return false;
        }
        File src = record.dir();
        try {
            // ① 先把当前实例挪成安全副本（回滚本身也要可逆）
            if (targetVersionDir.exists()) {
                if (cb != null) {
                    cb.onStage("safety");
                }
                File safety = new File(AppManifest.BACKUP_DIR,
                        record.id + "_safety_" + timestamp());
                if (!targetVersionDir.renameTo(safety)) {
                    // rename 跨设备会失败 → 退化成拷贝
                    if (copyRecursive(targetVersionDir, safety) < 0) {
                        Log.w(TAG, "安全副本创建失败，放弃回滚（不动原文件）");
                        return false;
                    }
                    deleteRecursive(targetVersionDir);
                }
                record.safetyDir = safety.getAbsolutePath();
            }
            // ② 快照内容拷回实例目录
            if (cb != null) {
                cb.onStage("restore");
            }
            if (!targetVersionDir.exists() && !targetVersionDir.mkdirs()) {
                Log.w(TAG, "重建实例目录失败: " + targetVersionDir.getAbsolutePath());
                return false;
            }
            long size = copyRecursive(src, targetVersionDir);
            if (size < 0) {
                return false;
            }
            // ③ 记下安全副本位置，便于玩家找回回滚前的状态
            List<BackupRecord> records = listRecords();
            for (BackupRecord r : records) {
                if (record.id != null && record.id.equals(r.id)) {
                    r.safetyDir = record.safetyDir;
                }
            }
            saveRecords(records);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "回滚失败: " + t);
            return false;
        }
    }

    // ------------------------------------------------------------------ 删除

    public static boolean delete(BackupRecord record) {
        if (record == null) {
            return false;
        }
        try {
            if (record.backupDir != null) {
                deleteRecursive(new File(record.backupDir));
            }
            List<BackupRecord> records = listRecords();
            List<BackupRecord> kept = new ArrayList<>();
            for (BackupRecord r : records) {
                if (record.id == null || !record.id.equals(r.id)) {
                    kept.add(r);
                }
            }
            saveRecords(kept);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "删除备份失败: " + t);
            return false;
        }
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 流式递归拷贝。返回拷贝的总字节数；失败返回 -1。
     * ★ 全程只用一块 64KB 缓冲，任何体积的实例都不会撑爆堆。
     *
     * <p>★ 社区版：改 public 以便存档备份（{@link com.qcl.launcher.launcher.backup.world.WorldBackupHelper}）复用。
     * 这里刻意<b>不</b>抽成独立的工具类 —— 抽取要动到已在跑的实例备份代码，
     * 而这两段逻辑（流式拷贝 / 受限递归删除）本身就是「备份设施」的一部分，语义上归属此处。
     */
    public static long copyRecursive(File src, File dst) {
        try {
            if (src.isFile()) {
                File parent = dst.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    return -1;
                }
                long copied = 0L;
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(src), BUFFER_SIZE);
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dst), BUFFER_SIZE);
                try {
                    byte[] buf = new byte[BUFFER_SIZE];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        copied += n;
                    }
                    out.flush();
                } finally {
                    try {
                        in.close();
                    } catch (Throwable ignored) {
                    }
                    try {
                        out.close();
                    } catch (Throwable ignored) {
                    }
                }
                return copied;
            }
            if (!src.isDirectory()) {
                return 0L;
            }
            if (!dst.exists() && !dst.mkdirs()) {
                return -1;
            }
            File[] children = src.listFiles();
            if (children == null) {
                return 0L;
            }
            long total = 0L;
            for (File c : children) {
                long n = copyRecursive(c, new File(dst, c.getName()));
                if (n < 0) {
                    return -1;
                }
                total += n;
            }
            return total;
        } catch (Throwable t) {
            Log.w(TAG, "拷贝失败 " + src + " → " + dst + " : " + t);
            return -1;
        }
    }

    /**
     * 递归删除。★ 只用于备份目录内部与用户明确确认过的实例目录，绝不作用于外部路径。
     * <p>★ 社区版：改 public 以便存档备份复用（同上）。
     */
    public static void deleteRecursive(File f) {
        try {
            if (f == null || !f.exists()) {
                return;
            }
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    for (File c : children) {
                        deleteRecursive(c);
                    }
                }
            }
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private static String safeName(String name) {
        if (name == null || name.isEmpty()) {
            return "instance";
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** 人类可读体积 */
    public static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
        }
        return String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    /** 人类可读时间 */
    public static String formatTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
    }
}
