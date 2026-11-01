package com.qcl.launcher.launcher.server;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.qcl.launcher.launcher.setting.game.PublicGameSetting;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.gson.GsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class ServerProfile {

    public static final String FILE_NAME = "server.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String name = "main";
    public String version = "";
    public String type = "vanilla";
    public String serverJar = "server.jar";
    public String serverSha1 = "";
    public String serverSha256 = "";
    public String serverMd5 = "";
    public String loaderVersion = "";
    public String installerVersion = "";
    public String launchArgsFile = "";
    public int requiredJava = 21;
    public int port = 25565;
    public int maxPlayers = 8;
    public int memoryMb = 2048;
    public boolean onlineMode = false;
    public String motd = "QCL Server";

    private ServerProfile() {
    }

    public static ServerProfile create(String name, String version) {
        ServerProfile p = new ServerProfile();
        p.name = sanitizeName(name);
        p.version = version == null ? "" : version.trim();
        return p;
    }

    public static File serversRoot() {
        return new File(AppManifest.LAUNCHER_DIR, "servers");
    }

    public static File defaultDirectory() {
        return new File(serversRoot(), "main");
    }

    public static File profileFile(File serverDir) {
        return new File(serverDir, FILE_NAME);
    }

    public static ServerProfile load(File serverDir) {
        if (serverDir == null) {
            return null;
        }
        File file = profileFile(serverDir);
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 64 * 1024)];
            int off = 0;
            while (off < data.length) {
                int read = in.read(data, off, data.length - off);
                if (read < 0) {
                    break;
                }
                off += read;
            }
            ServerProfile p = GSON.fromJson(new String(data, 0, off, StandardCharsets.UTF_8),
                    ServerProfile.class);
            if (p != null) {
                p.normalize();
            }
            return p;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void save(File serverDir) throws Exception {
        normalize();
        if (!serverDir.isDirectory() && !serverDir.mkdirs()) {
            throw new IllegalStateException("无法创建服务器目录：" + serverDir);
        }
        byte[] data = GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(profileFile(serverDir))) {
            out.write(data);
            out.getFD().sync();
        }
    }

    public File jarFile(File serverDir) {
        return new File(serverDir, serverJar == null || serverJar.isEmpty() ? "server.jar" : serverJar);
    }

    public String typeLabel() {
        return typeLabel(type);
    }

    public static String typeLabel(String value) {
        if ("paper".equals(value)) {
            return "Paper";
        }
        if ("purpur".equals(value)) {
            return "Purpur";
        }
        if ("fabric".equals(value)) {
            return "Fabric";
        }
        if ("forge".equals(value)) {
            return "Forge";
        }
        if ("neoforge".equals(value)) {
            return "NeoForge";
        }
        return "原版";
    }

    private void normalize() {
        name = sanitizeName(name);
        if (!"paper".equals(type) && !"purpur".equals(type)
                && !"fabric".equals(type) && !"forge".equals(type)
                && !"neoforge".equals(type)) {
            type = "vanilla";
        }
        if (serverSha1 == null) {
            serverSha1 = "";
        }
        if (serverSha256 == null) {
            serverSha256 = "";
        }
        if (serverMd5 == null) {
            serverMd5 = "";
        }
        if (loaderVersion == null) {
            loaderVersion = "";
        }
        if (installerVersion == null) {
            installerVersion = "";
        }
        if (launchArgsFile == null) {
            launchArgsFile = "";
        }
        if (serverJar == null || serverJar.isEmpty()) {
            serverJar = "server.jar";
        }
        if (motd == null || motd.trim().isEmpty()) {
            motd = "QCL Server";
        }
        port = Math.max(1024, Math.min(65535, port));
        maxPlayers = Math.max(1, Math.min(100, maxPlayers));
        memoryMb = Math.max(512, Math.min(4096, memoryMb));
        requiredJava = Math.max(8, Math.min(25, requiredJava));
    }

    public static String sanitizeName(String name) {
        String value = name == null ? "" : name.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (value.isEmpty()) {
            value = "main";
        }
        return value.length() > 32 ? value.substring(0, 32) : value;
    }

    public static String currentInstanceName(Context context) {
        try {
            PublicGameSetting setting = GsonUtils.getPublicGameSettingFromFile(
                    AppManifest.SETTING_DIR + "/public_game_setting.json");
            if (setting != null && setting.currentVersion != null) {
                return new File(setting.currentVersion).getName();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
