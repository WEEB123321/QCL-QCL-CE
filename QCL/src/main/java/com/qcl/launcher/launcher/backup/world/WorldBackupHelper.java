package com.qcl.launcher.launcher.backup.world;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.qcl.launcher.launcher.backup.InstanceBackupHelper;

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
 * ★ 社区版新增：存档（世界）版本管理。
 *
 * <p><b>★★★ 为什么备份根目录放在游戏目录里，而不是像实例备份那样放 filesDir</b>
 * <pre>
 *   实例备份：filesDir/backups            存档备份：&lt;gameDir&gt;/.qcl_worldbackups
 * </pre>
 * 因为「恢复存档」必须先把玩家<b>当前</b>的存档挪走才能腾出位置。如果备份根目录在
 * 内置存储（/data/data/...）而存档在外置存储（/sdcard/QCL/.minecraft/saves），
 * {@code renameTo} 跨卷会失败，只能退化成「拷贝一份」—— 那正好就是需求书里
 * 想避免的「体积翻倍」。把备份根目录放在<b>与 saves 同一卷</b>的游戏目录下，
 * 挪走就是一次 rename：零额外空间、瞬时完成、且失败可原样挪回。
 *
 * <p><b>★ 恢复前一定留退路</b>：恢复存档是<b>不可逆的破坏性操作</b>（覆盖当前进度）。
 * 所以恢复流程是「先把当前存档挪成一条 autoBeforeRestore 备份 → 再拷回快照」。
 * 需求书里写的是「存档备份不做安全副本」，这里<b>刻意偏离</b>：存档体积远小于整个实例，
 * 而「恢复完发现快照是坏的、当前进度也没了」正是这个功能要防的失败。
 * 挪走在同卷下零成本，所以这条退路的代价可以忽略。
 *
 * <p><b>★ 无数据库</b>：索引写在 {@code <gameDir>/.qcl_worldbackups/index.json}，
 * 外层带 {@code schemaVersion}。加字段时旧索引缺字段读出来是 null/0，按版本补齐即可。
 */
public final class WorldBackupHelper {

    private static final String TAG = "QCLWorldBackup";

    /** 索引结构版本。加字段时 +1，并在读取处按版本补齐默认值。 */
    private static final int SCHEMA_VERSION = 1;

    private static final String ROOT_NAME = ".qcl_worldbackups";

    private static final String INDEX_FILE = "index.json";

    private WorldBackupHelper() {
    }

    /** 进度回调：只在阶段切换时回调，避免高频刷 UI。 */
    public interface Callback {
        void onStage(String stage);
    }

    /** 索引外层包装 —— 带 schemaVersion，替代数据库迁移。 */
    private static class Index {
        int schemaVersion = SCHEMA_VERSION;
        List<WorldBackupRecord> records = new ArrayList<>();
    }

    // ------------------------------------------------------------------ 路径

    /**
     * 备份根目录：{@code <gameDir>/.qcl_worldbackups}。
     * ★ 必须与 saves 同卷，理由见类注释。
     */
    public static File root(File gameDir) {
        return new File(gameDir, ROOT_NAME);
    }

    private static File indexFile(File gameDir) {
        return new File(root(gameDir), INDEX_FILE);
    }

    // ------------------------------------------------------------------ 索引读写

    /**
     * 列出备份。{@code worldKey} 为 null/空时返回全部。
     * 结果按创建时间倒序（新的在前）。
     */
    public static synchronized List<WorldBackupRecord> listRecords(File gameDir, String worldKey) {
        List<WorldBackupRecord> out = new ArrayList<>();
        try {
            File f = indexFile(gameDir);
            if (!f.isFile() || f.length() == 0L) {
                return out;
            }
            byte[] b = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int n = in.read(b);
            in.close();
            Index idx = new Gson().fromJson(new String(b, 0, n, "UTF-8"), Index.class);
            if (idx == null || idx.records == null) {
                return out;
            }
            // ★ 迁移点：schemaVersion 低于当前时在此补齐新字段默认值（当前 v1 无需处理）
            for (WorldBackupRecord r : idx.records) {
                if (r == null || !r.isValid()) {
                    continue;
                }
                if (worldKey != null && !worldKey.isEmpty()
                        && !worldKey.equals(r.worldKey)) {
                    continue;
                }
                out.add(r);
            }
        } catch (Throwable t) {
            Log.w(TAG, "读存档备份索引失败（当作空索引处理）: " + t);
        }
        Collections.sort(out, new Comparator<WorldBackupRecord>() {
            @Override
            public int compare(WorldBackupRecord a, WorldBackupRecord b) {
                return Long.compare(b.createdAt, a.createdAt);   // 新的在前
            }
        });
        return out;
    }

    private static synchronized void saveRecords(File gameDir, List<WorldBackupRecord> records) {
        try {
            File dir = root(gameDir);
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "创建存档备份目录失败: " + dir.getAbsolutePath());
                return;
            }
            Index idx = new Index();
            idx.schemaVersion = SCHEMA_VERSION;
            idx.records = records;
            byte[] data = new GsonBuilder().setPrettyPrinting().create()
                    .toJson(idx).getBytes("UTF-8");
            File tmp = new File(dir, INDEX_FILE + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(data);
            out.close();
            File target = indexFile(gameDir);
            if (target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(target);
        } catch (Throwable t) {
            Log.w(TAG, "写存档备份索引失败: " + t);
        }
    }

    // ------------------------------------------------------------------ 创建备份

    /**
     * 把整个存档目录打成快照。
     *
     * @param worldKey  存档目录名（用于分组，不随世界改名而变）
     * @param worldName 世界显示名（仅展示）
     * @param worldDir  存档目录
     * @param auto      true 表示这是「恢复前自动备份」
     * @return 成功返回记录，失败返回 null
     */
    public static WorldBackupRecord createBackup(File gameDir, String worldKey, String worldName,
                                                 File worldDir, boolean auto, Callback cb) {
        if (gameDir == null || worldDir == null || !worldDir.isDirectory()) {
            return null;
        }
        String id = safeName(worldKey) + "_" + timestamp() + (auto ? "_prev" : "");
        File dest = new File(root(gameDir), id);
        try {
            if (!dest.exists() && !dest.mkdirs()) {
                Log.w(TAG, "创建存档备份目录失败: " + dest.getAbsolutePath());
                return null;
            }
            if (cb != null) {
                cb.onStage("copy");
            }
            long size = InstanceBackupHelper.copyRecursive(worldDir, dest);
            if (size < 0) {
                InstanceBackupHelper.deleteRecursive(dest);
                return null;
            }
            WorldBackupRecord r = new WorldBackupRecord();
            r.id = id;
            r.worldKey = worldKey;
            r.worldName = worldName;
            r.sourceDir = worldDir.getAbsolutePath();
            r.createdAt = System.currentTimeMillis();
            r.sizeBytes = size;
            r.backupDir = dest.getAbsolutePath();
            r.autoBeforeRestore = auto;
            List<WorldBackupRecord> records = listRecords(gameDir, null);
            records.add(r);
            saveRecords(gameDir, records);
            return r;
        } catch (Throwable t) {
            Log.w(TAG, "创建存档备份失败: " + t);
            InstanceBackupHelper.deleteRecursive(dest);
            return null;
        }
    }

    // ------------------------------------------------------------------ 恢复

    /**
     * 用快照覆盖存档。当前存档会先被挪成一条 {@code autoBeforeRestore} 备份。
     *
     * <p><b>★ 挪走优先用 rename</b>：备份根目录与 saves 同卷（见类注释），所以正常情况
     * 下这一步是<b>瞬时且零额外空间</b>的目录改名，而不是拷贝。只有在存档被指到别的卷
     * （自定义路径）时才退化为拷贝。
     *
     * <p><b>★ 失败时保证「玩家的当前存档仍在原处」</b>：挪走之后拷贝失败会把旧目录挪回原位。
     *
     * @return 成功与否
     */
    public static boolean restore(File gameDir, WorldBackupRecord record, File targetWorldDir, Callback cb) {
        if (gameDir == null || record == null || !record.isValid() || targetWorldDir == null) {
            return false;
        }
        File src = record.dir();
        File prevDir = null;
        try {
            // ① 先把当前存档挪走，作为「恢复前」的退路
            if (targetWorldDir.exists()) {
                if (cb != null) {
                    cb.onStage("prev");
                }
                prevDir = new File(root(gameDir), safeName(record.worldKey) + "_" + timestamp() + "_prev");
                boolean moved;
                if (!prevDir.exists() && targetWorldDir.renameTo(prevDir)) {
                    moved = true;   // 同卷：零成本
                } else {
                    // 跨卷 → 退化为拷贝；再失败就中止，绝不在没有退路的情况下动玩家存档
                    moved = InstanceBackupHelper.copyRecursive(targetWorldDir, prevDir) >= 0;
                    if (moved) {
                        InstanceBackupHelper.deleteRecursive(targetWorldDir);
                    }
                }
                if (!moved) {
                    Log.w(TAG, "恢复前存档挪走失败，已中止恢复（不动原存档）");
                    return false;
                }
                // 把退路登记成一条可见、可删的备份 —— 不让玩家面对一个看不见的隐藏目录
                registerRecord(gameDir, record.worldKey, record.worldName, prevDir, true);
            }
            // ② 快照内容拷回存档目录
            if (cb != null) {
                cb.onStage("restore");
            }
            if (!targetWorldDir.exists() && !targetWorldDir.mkdirs()) {
                Log.w(TAG, "重建存档目录失败: " + targetWorldDir.getAbsolutePath());
                rollbackPrev(prevDir, targetWorldDir);
                return false;
            }
            long size = InstanceBackupHelper.copyRecursive(src, targetWorldDir);
            if (size < 0) {
                // 拷贝失败 → 把旧存档挪回原位，保证「至少还是恢复前的状态」
                rollbackPrev(prevDir, targetWorldDir);
                return false;
            }
            // ③ 记下退路位置，便于玩家找回恢复前的状态
            List<WorldBackupRecord> records = listRecords(gameDir, null);
            for (WorldBackupRecord r : records) {
                if (record.id != null && record.id.equals(r.id)) {
                    r.prevDir = prevDir == null ? null : prevDir.getAbsolutePath();
                }
            }
            saveRecords(gameDir, records);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "恢复存档失败: " + t);
            rollbackPrev(prevDir, targetWorldDir);
            return false;
        }
    }

    /** 把已存在于磁盘上的一个目录登记成备份记录（用于「恢复前自动备份」，避免重复拷贝）。 */
    private static void registerRecord(File gameDir, String worldKey, String worldName,
                                       File dir, boolean auto) {
        try {
            WorldBackupRecord r = new WorldBackupRecord();
            r.id = dir.getName();
            r.worldKey = worldKey;
            r.worldName = worldName;
            r.createdAt = System.currentTimeMillis();
            r.sizeBytes = dirSize(dir);
            r.backupDir = dir.getAbsolutePath();
            r.autoBeforeRestore = auto;
            List<WorldBackupRecord> records = listRecords(gameDir, null);
            records.add(r);
            saveRecords(gameDir, records);
        } catch (Throwable t) {
            Log.w(TAG, "登记备份记录失败（不影响恢复本身）: " + t);
        }
    }

    /** 递归统计体积。只 stat 不读内容，用于给 rename 出来的备份填 sizeBytes。 */
    private static long dirSize(File f) {
        try {
            if (f.isFile()) {
                return f.length();
            }
            if (!f.isDirectory()) {
                return 0L;
            }
            File[] children = f.listFiles();
            if (children == null) {
                return 0L;
            }
            long total = 0L;
            for (File c : children) {
                total += dirSize(c);
            }
            return total;
        } catch (Throwable t) {
            return 0L;
        }
    }

    /** 拷贝失败时把「恢复前备份」挪回原位 —— 恢复失败不该让玩家失去进度。 */
    private static void rollbackPrev(File prevDir, File targetWorldDir) {
        if (prevDir == null || !prevDir.isDirectory()) {
            return;
        }
        try {
            InstanceBackupHelper.deleteRecursive(targetWorldDir);
            File parent = targetWorldDir.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
            if (!prevDir.renameTo(targetWorldDir)) {
                // 跨卷：只能拷回去
                InstanceBackupHelper.copyRecursive(prevDir, targetWorldDir);
            }
        } catch (Throwable t) {
            Log.w(TAG, "回退「恢复前存档」失败: " + t);
        }
    }

    // ------------------------------------------------------------------ 删除

    /** 删除一条备份（含它携带的「恢复前存档」退路）。 */
    public static boolean delete(File gameDir, WorldBackupRecord record) {
        if (record == null) {
            return false;
        }
        try {
            if (record.backupDir != null) {
                InstanceBackupHelper.deleteRecursive(new File(record.backupDir));
            }
            if (record.prevDir != null && !record.prevDir.equals(record.backupDir)) {
                InstanceBackupHelper.deleteRecursive(new File(record.prevDir));
            }
            List<WorldBackupRecord> records = listRecords(gameDir, null);
            List<WorldBackupRecord> kept = new ArrayList<>();
            for (WorldBackupRecord r : records) {
                if (record.id == null || !record.id.equals(r.id)) {
                    kept.add(r);
                }
            }
            saveRecords(gameDir, kept);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "删除存档备份失败: " + t);
            return false;
        }
    }

    // ------------------------------------------------------------------ 工具

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private static String safeName(String name) {
        if (name == null || name.isEmpty()) {
            return "world";
        }
        return name.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }
}
