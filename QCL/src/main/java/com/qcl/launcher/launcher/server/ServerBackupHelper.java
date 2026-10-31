package com.qcl.launcher.launcher.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ServerBackupHelper {

    private static final String BACKUP_DIR = "qcl-backups";
    private static final int KEEP_MANUAL = 5;
    private static final int KEEP_PRE_RESTORE = 5;
    private static final String[] CONFIG_FILES = {
            "server.properties", "whitelist.json", "ops.json",
            "banned-players.json", "banned-ips.json", "usercache.json"
    };

    public interface Listener {
        void onStage(String stage);
    }

    private ServerBackupHelper() {
    }

    public static File backupDir(File serverDir) {
        return new File(serverDir, BACKUP_DIR);
    }

    public static List<File> list(File serverDir) {
        File dir = backupDir(serverDir);
        File[] files = dir.listFiles(file -> file.isFile()
                && file.getName().endsWith(".zip"));
        List<File> result = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                result.add(file);
            }
        }
        result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result;
    }

    public static File create(File serverDir, String prefix, Listener listener)
            throws Exception {
        List<File> entries = collectEntries(serverDir);
        if (entries.isEmpty()) {
            throw new IllegalStateException("没有找到世界目录");
        }
        File dir = backupDir(serverDir);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建备份目录");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date());
        File target = new File(dir, prefix + "-" + stamp + ".zip");
        int suffix = 1;
        while (target.exists()) {
            target = new File(dir, prefix + "-" + stamp + "-" + suffix++ + ".zip");
        }
        File part = new File(dir, target.getName() + ".part");
        if (listener != null) {
            listener.onStage("正在打包 " + entries.size() + " 项");
        }
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(part)),
                StandardCharsets.UTF_8)) {
            for (File entry : entries) {
                addToZip(serverDir, entry, zip);
            }
        }
        try {
            target.delete();
            if (!part.renameTo(target)) {
                throw new IllegalStateException("无法保存备份文件");
            }
            trim(dir, prefix, prefix.startsWith("pre-restore") ? KEEP_PRE_RESTORE : KEEP_MANUAL);
            return target;
        } finally {
            part.delete();
        }
    }

    public static File restore(File serverDir, File backup, Listener listener)
            throws Exception {
        File backupRoot = backupDir(serverDir).getCanonicalFile();
        File backupCanonical = backup.getCanonicalFile();
        if (!backupCanonical.getPath().startsWith(backupRoot.getPath() + File.separator)
                || !backupCanonical.isFile()) {
            throw new IllegalArgumentException("备份文件无效");
        }
        if (listener != null) {
            listener.onStage("恢复前备份当前世界");
        }
        File safety = create(serverDir, "pre-restore", listener);
        File temp = new File(serverDir, ".qcl-restore-" + System.currentTimeMillis());
        if (!temp.mkdirs()) {
            throw new IllegalStateException("无法创建恢复临时目录");
        }
        Set<String> replaceNames = new LinkedHashSet<>();
        try {
            if (listener != null) {
                listener.onStage("正在解压并校验备份");
            }
            extract(backup, temp, replaceNames);
            if (replaceNames.isEmpty()) {
                throw new IllegalStateException("备份中没有可恢复的世界");
            }
            if (listener != null) {
                listener.onStage("正在替换世界文件");
            }
            String rootPath = temp.getCanonicalPath() + File.separator;
            for (String name : replaceNames) {
                File source = new File(temp, name);
                File target = new File(serverDir, name);
                if (!source.getCanonicalPath().startsWith(rootPath)) {
                    throw new IllegalStateException("非法路径：" + name);
                }
                deleteRecursively(target);
                if (!source.renameTo(target)) {
                    copyRecursively(source, target);
                }
            }
            return safety;
        } finally {
            deleteRecursively(temp);
        }
    }

    public static boolean delete(File backup) {
        return backup != null && backup.isFile() && backup.delete();
    }

    public static void syncProfile(File serverDir, ServerProfile profile) {
        if (profile == null) {
            return;
        }
        File file = new File(serverDir, "server.properties");
        if (!file.isFile()) {
            return;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new FileInputStream(file),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if ("server-port".equals(key)) {
                    profile.port = Integer.parseInt(value);
                } else if ("max-players".equals(key)) {
                    profile.maxPlayers = Integer.parseInt(value);
                } else if ("motd".equals(key)) {
                    profile.motd = value;
                } else if ("online-mode".equals(key)) {
                    profile.onlineMode = Boolean.parseBoolean(value);
                }
            }
            profile.save(serverDir);
        } catch (Throwable ignored) {
        }
    }

    private static List<File> collectEntries(File serverDir) {
        List<File> entries = new ArrayList<>();
        File[] children = serverDir.listFiles();
        if (children == null) {
            return entries;
        }
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                if (name.equals(BACKUP_DIR) || name.startsWith(".qcl-restore-")) {
                    continue;
                }
                if (new File(child, "level.dat").isFile()) {
                    entries.add(child);
                }
            } else if (isConfig(name)) {
                entries.add(child);
            }
        }
        return entries;
    }

    private static boolean isConfig(String name) {
        for (String config : CONFIG_FILES) {
            if (config.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void addToZip(File root, File file, ZipOutputStream zip)
            throws Exception {
        String relative = root.toURI().relativize(file.toURI()).getPath();
        if (file.isDirectory()) {
            zip.putNextEntry(new ZipEntry(relative.endsWith("/") ? relative : relative + "/"));
            zip.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToZip(root, child, zip);
                }
            }
            return;
        }
        zip.putNextEntry(new ZipEntry(relative));
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                zip.write(buffer, 0, read);
            }
        }
        zip.closeEntry();
    }

    private static void extract(File backup, File temp, Set<String> replaceNames)
            throws Exception {
        String rootPath = temp.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(backup)),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                int slash = name.indexOf('/');
                String top = slash < 0 ? name : name.substring(0, slash);
                if (top.isEmpty() || top.equals("..") || top.startsWith(".")) {
                    throw new IllegalStateException("备份包含非法路径：" + name);
                }
                File target = new File(temp, name);
                if (!target.getCanonicalPath().startsWith(rootPath)) {
                    throw new IllegalStateException("备份包含越界路径：" + name);
                }
                if (slash < 0 && !isConfig(top)) {
                    if (!entry.isDirectory()) {
                        continue;
                    }
                    if (!new File(temp, top + "/level.dat").exists()) {
                        // Directory entry precedes its files; validation below decides.
                    }
                }
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        throw new IllegalStateException("无法创建目录：" + name);
                    }
                } else {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IllegalStateException("无法创建目录：" + parent);
                    }
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = zip.read(buffer)) >= 0) {
                            out.write(buffer, 0, read);
                        }
                        out.getFD().sync();
                    }
                }
                zip.closeEntry();
            }
        }
        File[] extracted = temp.listFiles();
        if (extracted != null) {
            for (File file : extracted) {
                if (file.isDirectory() && new File(file, "level.dat").isFile()) {
                    replaceNames.add(file.getName());
                } else if (file.isFile() && isConfig(file.getName())) {
                    replaceNames.add(file.getName());
                }
            }
        }
    }

    private static void trim(File dir, String prefix, int keep) {
        File[] files = dir.listFiles(file -> file.isFile()
                && file.getName().startsWith(prefix + "-")
                && file.getName().endsWith(".zip"));
        if (files == null || files.length <= keep) {
            return;
        }
        List<File> list = new ArrayList<>();
        for (File file : files) {
            list.add(file);
        }
        list.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keep; i < list.size(); i++) {
            list.get(i).delete();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static void copyRecursively(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.isDirectory() && !target.mkdirs()) {
                throw new IllegalStateException("无法创建目录：" + target);
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursively(child, new File(target, child.getName()));
                }
            }
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建目录：" + parent);
        }
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            out.getFD().sync();
        }
    }
}
