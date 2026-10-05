package com.qcl.launcher.launcher.backup;

import java.io.File;

/**
 * ★ 社区版新增：一条实例备份记录。
 *
 * <p>字段刻意保持扁平、可空安全 —— 它是被 Gson 直接序列化进
 * {@code filesDir/backups/index.json} 的，将来加字段时旧索引仍能读（缺失字段为 null/0），
 * 这就是本项目"无数据库"前提下替代「数据库迁移」的做法：
 * 索引外层带 {@code schemaVersion}，读到低版本时按版本补齐，而不是靠 ALTER TABLE。
 */
public class BackupRecord {

    /** 备份唯一 id（也是备份子目录名，形如 b173_20261003_185512） */
    public String id;

    /** 实例（版本）名，仅用于展示 */
    public String instanceName;

    /** 备份时实例目录的绝对路径，仅用于展示 / 排查 */
    public String sourceDir;

    /** 备份创建时间（毫秒） */
    public long createdAt;

    /** 备份体积（字节） */
    public long sizeBytes;

    /** 备份数据目录绝对路径（BACKUP_DIR/id） */
    public String backupDir;

    /** 回滚前留下的安全副本目录（可空，表示没做过回滚） */
    public String safetyDir;

    public BackupRecord() {
    }

    public boolean isValid() {
        return id != null && !id.isEmpty() && backupDir != null
                && new File(backupDir).isDirectory();
    }

    public File dir() {
        return backupDir == null ? null : new File(backupDir);
    }
}
