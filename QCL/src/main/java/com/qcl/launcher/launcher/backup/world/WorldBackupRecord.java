package com.qcl.launcher.launcher.backup.world;

import java.io.File;

/**
 * ★ 社区版新增：一条存档（世界）备份记录。
 *
 * <p>与 {@link com.qcl.launcher.launcher.backup.BackupRecord}（实例级）刻意分开，不共用一张表：
 * 两者的<b>生命周期与删除策略完全不同</b> —— 实例备份跟着版本目录走（删版本就该清备份），
 * 存档备份跟着世界走（删版本不该动存档）。混在一张表里迟早要写一堆 if 去区分。
 *
 * <p>字段扁平、可空安全，被 Gson 直接序列化进 {@code <gameDir>/.qcl_worldbackups/index.json}。
 * 外层带 {@code schemaVersion}，这就是本项目「无数据库」前提下替代「数据库迁移」的做法。
 */
public class WorldBackupRecord {

    /** 备份唯一 id（同时是备份子目录名，形如 New_World_20261003_190233） */
    public String id;

    /**
     * 所属存档的目录名（saves 目录下的相对名），用于把备份列表按世界分组。
     * ★ 不用世界显示名做 key：显示名可以被玩家在游戏里随时改，目录名不会。
     */
    public String worldKey;

    /** 备份时世界的显示名，仅用于展示（世界可能已改过名） */
    public String worldName;

    /** 备份时存档目录的绝对路径，仅用于展示 / 排查 */
    public String sourceDir;

    /** 备份创建时间（毫秒） */
    public long createdAt;

    /** 备份体积（字节） */
    public long sizeBytes;

    /** 备份数据目录绝对路径 */
    public String backupDir;

    /** 恢复时被移走的「恢复前存档」的安置目录（可空，表示没做过恢复） */
    public String prevDir;

    /** 是否由「恢复前自动备份」产生（列表里据此打标，避免玩家误以为是自己的备份） */
    public boolean autoBeforeRestore;

    public WorldBackupRecord() {
    }

    public boolean isValid() {
        return id != null && !id.isEmpty() && backupDir != null
                && new File(backupDir).isDirectory();
    }

    public File dir() {
        return backupDir == null ? null : new File(backupDir);
    }
}
