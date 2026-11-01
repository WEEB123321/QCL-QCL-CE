package com.qcl.launcher.launcher.server;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qcl.launcher.launcher.download.neoforge.NeoForgeVersion;
import com.qcl.launcher.launcher.download.neoforge.NeoForgeVersions;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerInstaller {

    private static final String VERSION_MANIFEST =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String BMCL_BASE = "https://bmclapi2.bangbang93.com";
    private static final String PAPER_BASE = "https://fill.papermc.io/v3";
    private static final String PURPUR_BASE = "https://api.purpurmc.org/v2";
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    private static final String FORGE_PROMOTIONS =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_MAVEN =
            "https://maven.minecraftforge.net/net/minecraftforge/forge";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 60000;

    public interface Listener {
        void onLog(String line);
        void onProgress(int percent);
    }

    public static final class Metadata {
        public String version;
        public String url;
        public String sha1;
        public long size;
        public int requiredJava;
    }

    static final class ForgeLaunch {
        final File argsFile;
        final File jar;

        ForgeLaunch(File argsFile, File jar) {
            this.argsFile = argsFile;
            this.jar = jar;
        }
    }

    private ServerInstaller() {
    }

    public static void ensure(Context context, File serverDir, ServerProfile profile,
                              Listener listener) throws Exception {
        if (serverDir == null || profile == null) {
            throw new IllegalArgumentException("服务器配置为空");
        }
        if (!serverDir.isDirectory() && !serverDir.mkdirs()) {
            throw new IllegalStateException("无法创建服务器目录");
        }
        listener.onLog("正在准备 " + profile.typeLabel() + " " + profile.version);
        if (isLocallyReady(profile, serverDir)) {
            listener.onLog("已安装文件校验通过，离线启动");
            if ("forge".equals(profile.type)) {
                ensureForgeReady(profile, serverDir, listener);
            } else if ("neoforge".equals(profile.type)) {
                ensureNeoForgeReady(profile, serverDir, listener);
            }
        } else {
            Metadata base = resolve(profile.version);
            profile.requiredJava = base.requiredJava;
            if ("paper".equals(profile.type)) {
                ensurePaper(profile, serverDir, listener);
            } else if ("purpur".equals(profile.type)) {
                ensurePurpur(profile, serverDir, listener);
            } else if ("fabric".equals(profile.type)) {
                ensureFabric(profile, serverDir, listener);
            } else if ("forge".equals(profile.type)) {
                ensureForgeReady(profile, serverDir, listener);
            } else if ("neoforge".equals(profile.type)) {
                ensureNeoForgeReady(profile, serverDir, listener);
            } else {
                ensureVanilla(profile, serverDir, listener, base);
            }
        }
        writeIfMissing(new File(serverDir, "eula.txt"),
                "# 启动服务器即表示同意 Minecraft EULA（https://aka.ms/MinecraftEULA）\n"
                        + "eula=true\n");
        upsertProperties(new File(serverDir, "server.properties"), profile);
        File logs = new File(serverDir, "logs");
        if (!logs.isDirectory() && !logs.mkdirs()) {
            listener.onLog("警告：无法创建 logs 目录");
        }
        profile.save(serverDir);
        listener.onProgress(100);
        listener.onLog("服务端文件已就绪");
    }

    public static boolean isForgeInstalled(File serverDir, ServerProfile profile) {
        if (profile != null && "neoforge".equals(profile.type)) {
            return findNeoForgeLaunch(serverDir, profile) != null;
        }
        return findForgeLaunch(serverDir, profile) != null;
    }

    private static boolean isLocallyReady(ServerProfile profile, File serverDir) {
        try {
            if ("forge".equals(profile.type) || "neoforge".equals(profile.type)) {
                return isForgeInstalled(serverDir, profile);
            }
            File jar = profile.jarFile(serverDir);
            if (!jar.isFile() || jar.length() == 0) {
                return false;
            }
            if ("paper".equals(profile.type)) {
                return profile.serverSha256 != null && !profile.serverSha256.isEmpty()
                        && profile.serverSha256.equalsIgnoreCase(sha256(jar));
            }
            if ("purpur".equals(profile.type)) {
                return profile.serverMd5 != null && !profile.serverMd5.isEmpty()
                        && profile.serverMd5.equalsIgnoreCase(hash(jar, "MD5"));
            }
            if ("fabric".equals(profile.type)) {
                return profile.serverSha256 != null && !profile.serverSha256.isEmpty()
                        && profile.serverSha256.equalsIgnoreCase(sha256(jar))
                        && profile.loaderVersion != null
                        && !profile.loaderVersion.isEmpty();
            }
            return profile.serverSha1 != null && !profile.serverSha1.isEmpty()
                    && profile.serverSha1.equalsIgnoreCase(sha1(jar));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static File prepareForgeInstaller(File serverDir, ServerProfile profile,
                                             Listener listener) throws Exception {
        String forgeVersion = resolveForgeVersion(profile.version);
        profile.type = "forge";
        profile.loaderVersion = forgeVersion;
        profile.requiredJava = requiredJavaForVersion(profile.version);
        String artifact = profile.version + "-" + forgeVersion;
        String fileName = "forge-" + artifact + "-installer.jar";
        String url = FORGE_MAVEN + "/" + artifact + "/" + fileName;
        File installer = new File(serverDir, fileName);
        String expectedSha1 = null;
        try {
            String body = get(url + ".sha1").trim();
            expectedSha1 = body.split("\\s+")[0];
        } catch (Throwable t) {
            listener.onLog("Forge 安装器未提供校验文件，将记录本地哈希");
        }
        boolean valid = installer.isFile() && installer.length() > 0;
        if (valid && expectedSha1 != null) {
            valid = expectedSha1.equalsIgnoreCase(sha1(installer));
        }
        if (!valid) {
            listener.onLog("下载 Forge " + forgeVersion + " 安装器");
            File part = new File(serverDir, fileName + ".part");
            downloadUnverified(url, part, listener);
            if (expectedSha1 != null && !expectedSha1.equalsIgnoreCase(sha1(part))) {
                part.delete();
                throw new IllegalStateException("Forge 安装器校验失败");
            }
            if (installer.exists()) {
                installer.delete();
            }
            if (!part.renameTo(installer)) {
                part.delete();
                throw new IllegalStateException("无法保存 Forge 安装器");
            }
        }
        listener.onLog("Forge 安装器：" + installer.getName());
        profile.save(serverDir);
        return installer;
    }

    public static File prepareNeoForgeInstaller(File serverDir, ServerProfile profile,
                                                Listener listener) throws Exception {
        ArrayList<NeoForgeVersion> versions = NeoForgeVersions.fetchForMc(profile.version);
        if (versions == null || versions.isEmpty()) {
            throw new IllegalStateException("NeoForge 没有适配 " + profile.version + " 的版本");
        }
        NeoForgeVersion chosen = versions.get(0);
        for (NeoForgeVersion version : versions) {
            if (!version.getVersion().contains("-beta")) {
                chosen = version;
                break;
            }
        }
        String neoVersion = chosen.getVersion();
        profile.loaderVersion = neoVersion;
        profile.requiredJava = requiredJavaForVersion(profile.version);
        String fileName = "neoforge-" + neoVersion + "-installer.jar";
        File installer = new File(serverDir, fileName);
        if (installer.isFile() && installer.length() > 0) {
            listener.onLog("NeoForge 安装器已存在：" + installer.getName());
            profile.save(serverDir);
            return installer;
        }
        File part = new File(serverDir, fileName + ".part");
        String primary = NeoForgeVersions.bmclapiInstallerUrl(chosen);
        String fallback = NeoForgeVersions.officialInstallerUrl(chosen);
        listener.onLog("下载 NeoForge " + neoVersion + " 安装器");
        try {
            downloadUnverified(primary, part, listener);
        } catch (Throwable t) {
            part.delete();
            listener.onLog("BMCLAPI 下载失败，切换官方源");
            downloadUnverified(fallback, part, listener);
        }
        if (installer.exists()) {
            installer.delete();
        }
        if (!part.renameTo(installer)) {
            part.delete();
            throw new IllegalStateException("无法保存 NeoForge 安装器");
        }
        listener.onLog("NeoForge 安装器：" + installer.getName());
        profile.save(serverDir);
        return installer;
    }

    private static void ensureNeoForgeReady(ServerProfile profile, File serverDir,
                                            Listener listener) {
        ForgeLaunch launch = findNeoForgeLaunch(serverDir, profile);
        if (launch == null) {
            throw new IllegalStateException("NeoForge 尚未安装，请从控制台重新启动以运行安装程序");
        }
        if (launch.argsFile != null) {
            profile.launchArgsFile = relative(serverDir, launch.argsFile);
            profile.serverJar = "";
        } else {
            profile.launchArgsFile = "";
            profile.serverJar = relative(serverDir, launch.jar);
        }
        listener.onLog("NeoForge 运行文件已就绪");
    }

    private static ForgeLaunch findNeoForgeLaunch(File serverDir, ServerProfile profile) {
        if (profile == null) {
            return null;
        }
        if (profile.launchArgsFile != null && !profile.launchArgsFile.isEmpty()) {
            File saved = new File(serverDir, profile.launchArgsFile);
            if (saved.isFile()) {
                return new ForgeLaunch(saved, null);
            }
        }
        File[] roots = new File[]{
                new File(serverDir, "libraries/net/neoforged/neoforge"),
                new File(serverDir, "libraries/net/neoforged/forge")
        };
        File fallback = null;
        for (File root : roots) {
            File[] versions = root.listFiles(File::isDirectory);
            if (versions == null) {
                continue;
            }
            for (File versionDir : versions) {
                File args = new File(versionDir, "unix_args.txt");
                if (!args.isFile()) {
                    continue;
                }
                if (profile.loaderVersion != null && !profile.loaderVersion.isEmpty()
                        && versionDir.getName().contains(profile.loaderVersion)) {
                    return new ForgeLaunch(args, null);
                }
                if (fallback == null) {
                    fallback = args;
                }
            }
        }
        return fallback == null ? null : new ForgeLaunch(fallback, null);
    }

    public static List<String> readArgFile(File file) throws Exception {
        String content;
        try (InputStream in = new FileInputStream(file)) {
            content = new String(readAll(in), StandardCharsets.UTF_8);
        }
        List<String> args = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean tokenStarted = false;
        boolean lineStart = true;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaped) {
                if (c != '\n' && c != '\r') {
                    token.append(c);
                }
                escaped = false;
                tokenStarted = true;
                lineStart = false;
                continue;
            }
            if (quote != 0) {
                if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                } else {
                    token.append(c);
                }
                tokenStarted = true;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                tokenStarted = true;
                lineStart = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                tokenStarted = true;
                lineStart = false;
                continue;
            }
            if (c == '#' && lineStart) {
                while (i + 1 < content.length() && content.charAt(i + 1) != '\n') {
                    i++;
                }
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (tokenStarted || token.length() > 0) {
                    args.add(token.toString());
                    token.setLength(0);
                    tokenStarted = false;
                }
                if (c == '\n' || c == '\r') {
                    lineStart = true;
                }
                continue;
            }
            token.append(c);
            tokenStarted = true;
            lineStart = false;
        }
        if (escaped) {
            token.append('\\');
        }
        if (tokenStarted || token.length() > 0) {
            args.add(token.toString());
        }
        return args;
    }

    public static int requiredJavaForVersion(String version) {
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^1\\.(\\d+)(?:\\.(\\d+))?").matcher(version);
            if (matcher.find()) {
                int minor = Integer.parseInt(matcher.group(1));
                int patch = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
                if (minor <= 16) {
                    return 8;
                }
                if (minor == 17 || minor == 18 || minor == 19) {
                    return 17;
                }
                if (minor == 20) {
                    return patch >= 5 ? 21 : 17;
                }
                return 21;
            }
            if (version.startsWith("26.")) {
                return 25;
            }
        } catch (Throwable ignored) {
        }
        return 17;
    }

    private static void ensureVanilla(ServerProfile profile, File serverDir,
                                      Listener listener, Metadata meta) throws Exception {
        profile.serverJar = "server.jar";
        File jar = profile.jarFile(serverDir);
        boolean valid = jar.isFile() && jar.length() == meta.size
                && meta.sha1.equalsIgnoreCase(sha1(jar));
        if (!valid) {
            listener.onLog("开始下载 server.jar（" + formatSize(meta.size) + "）");
            downloadWithMirror(meta.url, BMCL_BASE + "/version/" + meta.version + "/server",
                    new File(serverDir, "server.jar.part"), listener);
            File part = new File(serverDir, "server.jar.part");
            String actual = sha1(part);
            if (!meta.sha1.equalsIgnoreCase(actual)) {
                part.delete();
                throw new IllegalStateException("server.jar 校验失败，文件未保存");
            }
            if (jar.exists()) {
                jar.delete();
            }
            if (!part.renameTo(jar)) {
                part.delete();
                throw new IllegalStateException("无法保存 server.jar");
            }
        }
        profile.serverSha1 = meta.sha1;
        profile.serverSha256 = "";
        profile.serverMd5 = "";
        profile.loaderVersion = "";
    }

    private static void ensurePaper(ServerProfile profile, File serverDir,
                                    Listener listener) throws Exception {
        PaperBuild build = resolvePaperBuild(profile.version);
        profile.requiredJava = Math.max(profile.requiredJava, build.javaMinimum);
        profile.serverJar = "server.jar";
        File jar = profile.jarFile(serverDir);
        boolean valid = jar.isFile() && jar.length() == build.size
                && build.sha256.equalsIgnoreCase(sha256(jar));
        if (!valid) {
            listener.onLog("下载 Paper " + profile.version + " build " + build.id);
            File part = new File(serverDir, "server.jar.part");
            downloadUnverified(build.url, part, listener);
            if (!build.sha256.equalsIgnoreCase(sha256(part))) {
                part.delete();
                throw new IllegalStateException("Paper 服务端校验失败");
            }
            if (jar.exists()) {
                jar.delete();
            }
            if (!part.renameTo(jar)) {
                part.delete();
                throw new IllegalStateException("无法保存 Paper 服务端");
            }
        }
        profile.serverSha256 = build.sha256;
        profile.serverSha1 = "";
        profile.serverMd5 = "";
        profile.loaderVersion = String.valueOf(build.id);
    }

    private static void ensurePurpur(ServerProfile profile, File serverDir,
                                     Listener listener) throws Exception {
        JsonObject latest = JsonParser.parseString(get(PURPUR_BASE + "/purpur/"
                + profile.version + "/latest")).getAsJsonObject();
        if (latest.has("result") && !"SUCCESS".equals(
                latest.get("result").getAsString())) {
            throw new IllegalStateException("Purpur 最新构建不可用");
        }
        String build = latest.get("build").getAsString();
        String md5 = latest.get("md5").getAsString();
        String url = PURPUR_BASE + "/purpur/" + profile.version + "/" + build + "/download";
        profile.serverJar = "server.jar";
        File jar = profile.jarFile(serverDir);
        boolean valid = jar.isFile() && md5.equalsIgnoreCase(hash(jar, "MD5"));
        if (!valid) {
            listener.onLog("下载 Purpur " + profile.version + " build " + build);
            File part = new File(serverDir, "server.jar.part");
            downloadUnverified(url, part, listener);
            if (!md5.equalsIgnoreCase(hash(part, "MD5"))) {
                part.delete();
                throw new IllegalStateException("Purpur 服务端校验失败");
            }
            if (jar.exists()) {
                jar.delete();
            }
            if (!part.renameTo(jar)) {
                part.delete();
                throw new IllegalStateException("无法保存 Purpur 服务端");
            }
        }
        profile.serverMd5 = md5;
        profile.serverSha1 = "";
        profile.serverSha256 = "";
        profile.loaderVersion = build;
    }

    private static void ensureFabric(ServerProfile profile, File serverDir,
                                     Listener listener) throws Exception {
        JsonArray loaders = getJsonArray(FABRIC_META + "/versions/loader/" + profile.version);
        String loaderVersion = null;
        for (JsonElement element : loaders) {
            JsonObject loader = element.getAsJsonObject().getAsJsonObject("loader");
            if (loader != null && loader.has("stable") && loader.get("stable").getAsBoolean()) {
                loaderVersion = loader.get("version").getAsString();
                break;
            }
        }
        if (loaderVersion == null && loaders.size() > 0) {
            loaderVersion = loaders.get(0).getAsJsonObject()
                    .getAsJsonObject("loader").get("version").getAsString();
        }
        if (loaderVersion == null) {
            throw new IllegalStateException("Fabric 没有适配 " + profile.version + " 的 Loader");
        }
        String installerVersion = null;
        JsonArray installers = getJsonArray(FABRIC_META + "/versions/installer");
        for (JsonElement element : installers) {
            JsonObject item = element.getAsJsonObject();
            if (item.has("stable") && item.get("stable").getAsBoolean()) {
                installerVersion = item.get("version").getAsString();
                break;
            }
        }
        if (installerVersion == null && installers.size() > 0) {
            installerVersion = installers.get(0).getAsJsonObject()
                    .get("version").getAsString();
        }
        if (installerVersion == null) {
            throw new IllegalStateException("无法解析 Fabric Installer 版本");
        }
        String url = FABRIC_META + "/versions/loader/" + profile.version + "/"
                + loaderVersion + "/" + installerVersion + "/server/jar";
        File jar = new File(serverDir, "server.jar");
        boolean valid = jar.isFile() && loaderVersion.equals(profile.loaderVersion)
                && installerVersion.equals(profile.installerVersion)
                && (profile.serverSha256.isEmpty()
                || profile.serverSha256.equalsIgnoreCase(sha256(jar)));
        if (!valid) {
            listener.onLog("下载 Fabric Loader " + loaderVersion);
            File part = new File(serverDir, "server.jar.part");
            downloadUnverified(url, part, listener);
            if (!isZip(part)) {
                part.delete();
                throw new IllegalStateException("Fabric 服务端下载不完整");
            }
            if (jar.exists()) {
                jar.delete();
            }
            if (!part.renameTo(jar)) {
                part.delete();
                throw new IllegalStateException("无法保存 Fabric 服务端");
            }
        }
        profile.serverJar = "server.jar";
        profile.loaderVersion = loaderVersion;
        profile.installerVersion = installerVersion;
        profile.serverSha1 = "";
        profile.serverSha256 = sha256(jar);
        profile.serverMd5 = "";
    }

    private static void ensureForgeReady(ServerProfile profile, File serverDir,
                                         Listener listener) throws Exception {
        ForgeLaunch launch = findForgeLaunch(serverDir, profile);
        if (launch == null) {
            throw new IllegalStateException("Forge 尚未安装，请从控制台重新启动以运行安装程序");
        }
        if (launch.argsFile != null) {
            profile.launchArgsFile = relative(serverDir, launch.argsFile);
            profile.serverJar = "";
        } else {
            profile.launchArgsFile = "";
            profile.serverJar = relative(serverDir, launch.jar);
        }
        listener.onLog("Forge 运行文件已就绪");
    }

    private static ForgeLaunch findForgeLaunch(File serverDir, ServerProfile profile) {
        if (profile == null) {
            return null;
        }
        if (profile.launchArgsFile != null && !profile.launchArgsFile.isEmpty()) {
            File saved = new File(serverDir, profile.launchArgsFile);
            if (saved.isFile()) {
                return new ForgeLaunch(saved, null);
            }
        }
        File forgeRoot = new File(serverDir, "libraries/net/minecraftforge/forge");
        File[] versions = forgeRoot.listFiles(File::isDirectory);
        if (versions != null) {
            File fallback = null;
            for (File versionDir : versions) {
                File args = new File(versionDir, "unix_args.txt");
                if (!args.isFile()) {
                    continue;
                }
                if (profile.loaderVersion != null && !profile.loaderVersion.isEmpty()
                        && versionDir.getName().equals(profile.version + "-"
                        + profile.loaderVersion)) {
                    return new ForgeLaunch(args, null);
                }
                if (fallback == null) {
                    fallback = args;
                }
            }
            if (fallback != null) {
                return new ForgeLaunch(fallback, null);
            }
        }
        File[] files = serverDir.listFiles(file -> file.isFile()
                && file.getName().startsWith("forge-")
                && file.getName().endsWith(".jar")
                && !file.getName().endsWith("-installer.jar"));
        if (files != null && files.length > 0) {
            return new ForgeLaunch(null, files[0]);
        }
        if (versions != null) {
            for (File versionDir : versions) {
                File[] serverJars = versionDir.listFiles(file -> file.isFile()
                        && file.getName().startsWith("forge-")
                        && file.getName().endsWith("-server.jar"));
                if (serverJars != null && serverJars.length > 0) {
                    return new ForgeLaunch(null, serverJars[0]);
                }
            }
        }
        return null;
    }

    private static String resolveForgeVersion(String minecraftVersion) throws Exception {
        try {
            JsonObject promotions = JsonParser.parseString(get(FORGE_PROMOTIONS))
                    .getAsJsonObject();
            JsonObject promos = promotions.getAsJsonObject("promos");
            String recommended = minecraftVersion + "-recommended";
            String latest = minecraftVersion + "-latest";
            if (promos != null && promos.has(recommended)) {
                return promos.get(recommended).getAsString();
            }
            if (promos != null && promos.has(latest)) {
                return promos.get(latest).getAsString();
            }
        } catch (Throwable ignored) {
        }
        String xml = get(FORGE_MAVEN + "/maven-metadata.xml");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<version>" + java.util.regex.Pattern.quote(minecraftVersion)
                        + "-([^<]+)</version>").matcher(xml);
        String chosen = null;
        while (matcher.find()) {
            chosen = matcher.group(1);
        }
        if (chosen == null) {
            throw new IllegalStateException("Forge 没有适配 " + minecraftVersion + " 的版本");
        }
        return chosen;
    }

    private static PaperBuild resolvePaperBuild(String version) throws Exception {
        JsonObject info = JsonParser.parseString(get(
                PAPER_BASE + "/projects/paper/versions/" + version)).getAsJsonObject();
        int javaMinimum = 21;
        try {
            javaMinimum = info.getAsJsonObject("version").getAsJsonObject("java")
                    .getAsJsonObject("version").get("minimum").getAsInt();
        } catch (Throwable ignored) {
        }
        JsonArray builds = info.getAsJsonArray("builds");
        if (builds == null || builds.size() == 0) {
            throw new IllegalStateException("Paper 没有版本 " + version + " 的构建");
        }
        int attempts = 0;
        for (JsonElement element : builds) {
            if (attempts++ >= 20) {
                break;
            }
            int id = element.getAsInt();
            JsonObject build = JsonParser.parseString(get(PAPER_BASE + "/projects/paper/versions/"
                    + version + "/builds/" + id)).getAsJsonObject();
            if (!"STABLE".equals(build.has("channel")
                    ? build.get("channel").getAsString() : "STABLE")) {
                continue;
            }
            JsonObject downloads = build.getAsJsonObject("downloads");
            if (downloads == null || downloads.size() == 0) {
                continue;
            }
            JsonObject download = downloads.entrySet().iterator().next()
                    .getValue().getAsJsonObject();
            PaperBuild result = new PaperBuild();
            result.id = id;
            result.url = download.get("url").getAsString();
            result.size = download.has("size") ? download.get("size").getAsLong() : 0;
            result.sha256 = download.getAsJsonObject("checksums")
                    .get("sha256").getAsString();
            result.javaMinimum = javaMinimum;
            return result;
        }
        throw new IllegalStateException("Paper 没有可用的稳定构建");
    }

    private static final class PaperBuild {
        int id;
        String url;
        String sha256;
        long size;
        int javaMinimum;
    }

    private static Metadata resolve(String version) throws Exception {
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("服务端版本为空");
        }
        Exception officialFailure = null;
        try {
            JsonObject manifest = JsonParser.parseString(get(VERSION_MANIFEST)).getAsJsonObject();
            JsonArray versions = manifest.getAsJsonArray("versions");
            String metadataUrl = null;
            for (JsonElement element : versions) {
                JsonObject item = element.getAsJsonObject();
                if (version.equals(item.get("id").getAsString())) {
                    metadataUrl = item.get("url").getAsString();
                    break;
                }
            }
            if (metadataUrl == null) {
                throw new IllegalArgumentException("Mojang 清单中不存在版本 " + version);
            }
            try {
                return parseMetadata(version, get(metadataUrl));
            } catch (Exception metadataFailure) {
                officialFailure = metadataFailure;
            }
        } catch (Exception e) {
            officialFailure = e;
        }
        try {
            return parseMetadata(version, get(BMCL_BASE + "/version/" + version + "/json"));
        } catch (Exception mirrorFailure) {
            if (officialFailure != null) {
                mirrorFailure.addSuppressed(officialFailure);
            }
            throw mirrorFailure;
        }
    }

    private static Metadata parseMetadata(String version, String json) {
        JsonObject metadata = JsonParser.parseString(json).getAsJsonObject();
        JsonObject downloads = metadata.getAsJsonObject("downloads");
        if (downloads == null || !downloads.has("server")) {
            throw new IllegalStateException("版本 " + version + " 没有官方服务端");
        }
        JsonObject server = downloads.getAsJsonObject("server");
        Metadata result = new Metadata();
        result.version = version;
        result.url = server.get("url").getAsString();
        result.sha1 = server.get("sha1").getAsString();
        result.size = server.get("size").getAsLong();
        result.requiredJava = requiredJavaForVersion(version);
        if (metadata.has("javaVersion")) {
            JsonObject javaVersion = metadata.getAsJsonObject("javaVersion");
            if (javaVersion != null && javaVersion.has("majorVersion")) {
                result.requiredJava = javaVersion.get("majorVersion").getAsInt();
            }
        }
        return result;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "QCL-CE-Server");
        connection.setInstanceFollowRedirects(true);
        try (InputStream in = connection.getInputStream()) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static JsonArray getJsonArray(String url) throws Exception {
        return JsonParser.parseString(get(url)).getAsJsonArray();
    }

    private static void downloadWithMirror(String primary, String mirror, File target,
                                           Listener listener) throws Exception {
        try {
            downloadUnverified(primary, target, listener);
        } catch (Exception primaryFailure) {
            target.delete();
            if (mirror == null || mirror.equals(primary)) {
                throw primaryFailure;
            }
            listener.onLog("官方源下载失败，切换 BMCLAPI 镜像");
            downloadUnverified(mirror, target, listener);
        }
    }

    private static void downloadUnverified(String url, File target, Listener listener)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "QCL-CE-Server");
        connection.setInstanceFollowRedirects(true);
        long total = connection.getContentLengthLong();
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long done = 0;
            int lastPercent = -1;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                done += read;
                if (total > 0) {
                    int percent = (int) Math.min(100, done * 100 / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        listener.onProgress(percent);
                    }
                }
            }
            if (total > 0 && done != total) {
                throw new IllegalStateException("下载不完整：" + done + "/" + total);
            }
            out.getFD().sync();
        } finally {
            connection.disconnect();
        }
    }

    private static boolean isZip(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            return in.read() == 'P' && in.read() == 'K';
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void upsertProperties(File file, ServerProfile p) throws Exception {
        java.util.LinkedHashMap<String, String> desired = new java.util.LinkedHashMap<>();
        desired.put("server-port", String.valueOf(p.port));
        desired.put("max-players", String.valueOf(p.maxPlayers));
        desired.put("motd", p.motd.replace("\n", " "));
        desired.put("online-mode", String.valueOf(p.onlineMode));
        if (!file.exists()) {
            desired.put("difficulty", "easy");
            desired.put("gamemode", "survival");
            desired.put("pvp", "true");
            desired.put("allow-flight", "false");
            desired.put("spawn-protection", "16");
            desired.put("view-distance", "6");
            desired.put("simulation-distance", "4");
            desired.put("enable-command-block", "false");
            desired.put("white-list", "false");
            desired.put("level-name", "world");
            desired.put("enable-rcon", "false");
        }
        ArrayList<String> lines = new ArrayList<>();
        if (file.isFile()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim();
                        String value = desired.remove(key);
                        if (value != null) {
                            lines.add(key + "=" + value);
                            continue;
                        }
                    }
                    lines.add(line);
                }
            }
        } else {
            lines.add("# 由 QCL-CE 手机开服生成");
        }
        for (java.util.Map.Entry<String, String> entry : desired.entrySet()) {
            lines.add(entry.getKey() + "=" + entry.getValue());
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            StringBuilder text = new StringBuilder();
            for (String line : lines) {
                text.append(line).append('\n');
            }
            out.write(text.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }

    private static void writeIfMissing(File file, String text) throws Exception {
        if (file.exists()) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }

    private static String relative(File root, File child) {
        String rootPath = root.getAbsolutePath();
        String childPath = child.getAbsolutePath();
        if (childPath.startsWith(rootPath + File.separator)) {
            return childPath.substring(rootPath.length() + 1);
        }
        return childPath;
    }

    private static String sha1(File file) throws Exception {
        return hash(file, "SHA-1");
    }

    private static String sha256(File file) throws Exception {
        return hash(file, "SHA-256");
    }

    private static String hash(File file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) {
            out.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return out.toString();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "未知大小";
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
