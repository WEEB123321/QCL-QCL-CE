package com.qcl.launcher.launcher.game;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.LongTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.Logging;
import com.qcl.launcher.utils.io.FileUtils;
import com.qcl.launcher.utils.io.ZipTools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class World {

    private final Path file;
    private String fileName;
    private String worldName;
    private String gameVersion;
    private long lastPlayed;

    // ★★★ 社区版新增：世界信息面板 / 种子分享要用到的字段。
    private long seed;
    private boolean hasSeed;
    /** 0=生存 1=创造 2=冒险 3=旁观；-1 = 读不到 */
    private int gameType = -1;
    private boolean hardcore;
    private boolean allowCommands;
    /** 0=和平 1=简单 2=普通 3=困难；-1 = 读不到 */
    private int difficulty = -1;

    public World(Path file) throws IOException {
        this.file = file;

        if (Files.isDirectory(file))
            loadFromDirectory();
        else if (Files.isRegularFile(file))
            loadFromZip();
        else
            throw new IOException("Path " + file + " cannot be recognized as a Minecraft world");
    }

    private void loadFromDirectory() throws IOException {
        fileName = FileUtils.getName(file);
        Path levelDat = file.resolve("level.dat");
        getWorldName(levelDat);
    }

    public Path getFile() {
        return file;
    }

    public String getFileName() {
        return fileName;
    }

    public String getWorldName() {
        return worldName;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    /** 世界种子；{@link #hasSeed()} 为 false 时该值无意义。 */
    public long getSeed() {
        return seed;
    }

    public boolean hasSeed() {
        return hasSeed;
    }

    public int getGameType() {
        return gameType;
    }

    public boolean isHardcore() {
        return hardcore;
    }

    public boolean isAllowCommands() {
        return allowCommands;
    }

    public int getDifficulty() {
        return difficulty;
    }

    private void loadFromZipImpl(Path root) throws IOException {
        Path levelDat = root.resolve("level.dat");
        if (!Files.exists(levelDat))
            throw new IOException("Not a valid world zip file since level.dat cannot be found.");

        getWorldName(levelDat);
    }

    private void loadFromZip() throws IOException {
        String r = AppManifest.SAVES_CACHE_DIR + "/world";
        com.qcl.launcher.utils.file.FileUtils.deleteDirectory(r);
        ZipTools.unzipFile(file.toString(), r, false);
        Path cur = new File(r + "/level.dat").toPath();
        if (Files.isRegularFile(cur)) {
            fileName = FileUtils.getName(file);
            loadFromZipImpl(new File(r + "/").toPath());
            return;
        }

        try (Stream<Path> stream = Files.list(new File(r + "/").toPath())) {
            Path root = stream.filter(Files::isDirectory).findAny().orElseThrow(() -> new IOException("Not a valid world zip file"));
            fileName = FileUtils.getName(root);
            loadFromZipImpl(root);
        }
    }

    private void getWorldName(Path levelDat) throws IOException {
        CompoundTag nbt = parseLevelDat(levelDat);

        CompoundTag data = nbt.get("Data");
        if (data == null)
            throw new IOException("level.dat missing Data");

        // ★★★ 社区版加固（顺带修的 bug）：LevelName / LastPlayed 原来都是**必须**的，
        //   缺一个就 throw IOException。而 World.getWorlds() 会把「构造抛异常」的世界
        //   整个跳过 —— 于是**这些存档在列表里直接消失**，用户看到的就是「我的存档不见了」。
        //   远古版本创建的存档、以及第三方工具生成的存档经常没有 LastPlayed。
        //   改成可选：缺了就用目录名 / 0 兜底，至少世界还能正常显示和进入。
        if (data.get("LevelName") instanceof StringTag)
            worldName = data.<StringTag>get("LevelName").getValue();
        else
            worldName = FileUtils.getName(file);

        if (data.get("LastPlayed") instanceof LongTag)
            lastPlayed = data.<LongTag>get("LastPlayed").getValue();
        else
            lastPlayed = 0L;

        gameVersion = null;
        if (data.get("Version") instanceof CompoundTag) {
            CompoundTag version = data.get("Version");

            if (version.get("Name") instanceof StringTag)
                gameVersion = version.<StringTag>get("Name").getValue();
        }

        // ---- 以下为社区版新增：世界信息面板 / 种子分享所需的字段（全部可选） ----
        if (data.get("RandomSeed") instanceof LongTag) {
            seed = data.<LongTag>get("RandomSeed").getValue();
            hasSeed = true;
        }
        if (data.get("GameType") instanceof com.github.steveice10.opennbt.tag.builtin.IntTag)
            gameType = data.<com.github.steveice10.opennbt.tag.builtin.IntTag>get("GameType").getValue();
        if (data.get("hardcore") instanceof com.github.steveice10.opennbt.tag.builtin.ByteTag)
            hardcore = data.<com.github.steveice10.opennbt.tag.builtin.ByteTag>get("hardcore").getValue() != 0;
        if (data.get("allowCommands") instanceof com.github.steveice10.opennbt.tag.builtin.ByteTag)
            allowCommands = data.<com.github.steveice10.opennbt.tag.builtin.ByteTag>get("allowCommands").getValue() != 0;
        if (data.get("Difficulty") instanceof com.github.steveice10.opennbt.tag.builtin.ByteTag)
            difficulty = data.<com.github.steveice10.opennbt.tag.builtin.ByteTag>get("Difficulty").getValue();
    }

    public void rename(String newName) throws IOException {
        if (!Files.isDirectory(file))
            throw new IOException("Not a valid world directory");

        // Change the name recorded in level.dat
        Path levelDat = file.resolve("level.dat");
        CompoundTag nbt = parseLevelDat(levelDat);
        CompoundTag data = nbt.get("Data");
        data.put(new StringTag("LevelName", newName));

        try (OutputStream os = new GZIPOutputStream(Files.newOutputStream(levelDat))) {
            NBTIO.writeTag(os, nbt);
        }

        // then change the folder's name
        Files.move(file, file.resolveSibling(newName));
    }

    public void install(Path savesDir, String name) throws IOException {
        Path worldDir;
        try {
            worldDir = savesDir.resolve(name);
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }

        if (Files.isDirectory(worldDir)) {
            throw new FileAlreadyExistsException("World already exists");
        }

        if (Files.isRegularFile(file)) {
            com.qcl.launcher.utils.file.FileUtils.deleteDirectory(AppManifest.SAVES_CACHE_DIR + "/install/world");
            ZipTools.unzipFile(file.toString(),AppManifest.SAVES_CACHE_DIR + "/install/world",false);
            Path cur = new File(AppManifest.SAVES_CACHE_DIR + "/install/world/level.dat").toPath();
            if (Files.isRegularFile(cur)) {
                com.qcl.launcher.utils.file.FileUtils.rename(AppManifest.SAVES_CACHE_DIR + "/install/world",name);
                com.qcl.launcher.utils.file.FileUtils.copyDirectory(AppManifest.SAVES_CACHE_DIR + "/install/" + name,worldDir.toString());
            } else {
                try (Stream<Path> stream = Files.list(new File(AppManifest.SAVES_CACHE_DIR + "/install/world/").toPath())) {
                    List<Path> subDirs = stream.collect(Collectors.toList());
                    if (subDirs.size() != 1) {
                        throw new IOException("World zip malformed");
                    }
                    String subDirectoryName = FileUtils.getName(subDirs.get(0));
                    com.qcl.launcher.utils.file.FileUtils.rename(AppManifest.SAVES_CACHE_DIR + "/install/world/" + subDirectoryName,name);
                    com.qcl.launcher.utils.file.FileUtils.copyDirectory(AppManifest.SAVES_CACHE_DIR + "/install/world/" + name,worldDir.toString());
                }
            }
            new World(worldDir).rename(name);
        } else if (Files.isDirectory(file)) {
            FileUtils.copyDirectory(file, worldDir);
        }
    }

    public void export(String exportPath, String name) throws IOException {
        if (!Files.isDirectory(file))
            throw new IOException();

        ZipTools.zip(file.toString(), exportPath, name);
    }

    private static CompoundTag parseLevelDat(Path path) throws IOException {
        try (InputStream is = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            Tag nbt = NBTIO.readTag(is);
            if (nbt instanceof CompoundTag)
                return (CompoundTag) nbt;
            else
                throw new IOException("level.dat malformed");
        }
    }

    public static Stream<World> getWorlds(Path savesDir) {
        try {
            if (Files.exists(savesDir)) {
                return Files.list(savesDir).flatMap(world -> {
                    try {
                        return Stream.of(new World(world));
                    } catch (IOException e) {
                        Logging.LOG.log(Level.WARNING, "Failed to read world " + world, e);
                        return Stream.empty();
                    }
                });
            }
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Failed to read saves", e);
        }
        return Stream.empty();
    }
}