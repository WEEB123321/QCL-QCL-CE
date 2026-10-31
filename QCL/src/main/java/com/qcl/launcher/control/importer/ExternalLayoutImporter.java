package com.qcl.launcher.control.importer;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qcl.launcher.control.bean.BaseButtonInfo;
import com.qcl.launcher.control.bean.BaseRockerViewInfo;
import com.qcl.launcher.control.bean.button.ButtonSize;
import com.qcl.launcher.control.bean.button.ButtonStyle;
import com.qcl.launcher.control.bean.rocker.RockerSize;
import com.qcl.launcher.control.bean.rocker.RockerStyle;
import com.qcl.launcher.launcher.list.local.controller.ChildLayout;
import com.qcl.launcher.launcher.list.local.controller.ControlPattern;
import com.qcl.launcher.manifest.AppManifest;
import com.qcl.launcher.utils.file.FileStringUtils;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 外部启动器控键布局导入器。
 *
 * <p>支持：
 * <ul>
 *   <li>FCL（controllerVersion 21 的 JSON）</li>
 *   <li>Zalith Launcher 2（editorVersion 12 的 JSON）</li>
 *   <li>Zalith Launcher（version 8 的 JSON）</li>
 *   <li>HMCL-PE（.hmclpe，密码 HMCL-PE-Password 的 ZIP）</li>
 * </ul>
 *
 * <p>导入结果始终另存为新的布局目录，不覆盖已有布局。
 */
public final class ExternalLayoutImporter {

    private static final String HMCL_PE_PASSWORD = "HMCL-PE-Password";
    private static final long MAX_HMCL_PE_UNCOMPRESSED = 128L * 1024 * 1024;

    private ExternalLayoutImporter() {
    }

    public static final class Result {
        public final String format;
        public final String sourceName;
        public final String author;
        public final String versionName;
        public final String description;
        public final List<ChildLayout> children;

        Result(String format, String sourceName, String author, String versionName,
               String description, List<ChildLayout> children) {
            this.format = format;
            this.sourceName = sourceName;
            this.author = author;
            this.versionName = versionName;
            this.description = description;
            this.children = children;
        }

        public int buttonCount() {
            int count = 0;
            for (ChildLayout child : children) {
                count += child.baseButtonList == null ? 0 : child.baseButtonList.size();
                count += child.baseRockerViewList == null ? 0 : child.baseRockerViewList.size();
            }
            return count;
        }
    }

    /** 文件头是 PK\\003\\004。扩展名不可靠时（.zip 改名等）用它兜底。 */
    public static boolean isZip(File file) {
        if (file == null || !file.isFile() || file.length() < 4) {
            return false;
        }
        try (InputStream input = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = input.read(header);
            return read == 4 && header[0] == 'P' && header[1] == 'K'
                    && header[2] == 3 && header[3] == 4;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Result parseJson(String jsonText, String fallbackName) {
        JsonElement element = JsonParser.parseString(jsonText);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("不是有效的控制布局 JSON");
        }
        JsonObject root = element.getAsJsonObject();
        if (root.has("layers") && root.has("editorVersion")) {
            return Zl2LayoutConverter.convert(root, fallbackName);
        }
        if (root.has("viewGroups") || root.has("controllerVersion")) {
            return FclLayoutConverter.convert(root, fallbackName);
        }
        if (root.has("mControlDataList") || root.has("mJoystickDataList") || root.has("mDrawerDataList")) {
            return Zl1LayoutConverter.convert(root, fallbackName);
        }
        if (root.has("baseButtonList") || root.has("baseRockerViewList")) {
            // 单个 QCL/HMCL-PE 子布局，包成一个只含该子布局的布局集
            ChildLayout child = new Gson().fromJson(root, ChildLayout.class);
            if (child == null) {
                throw new IllegalArgumentException("不是有效的控制布局 JSON");
            }
            if (child.name == null || child.name.trim().isEmpty()) {
                child.name = "game_layout";
            }
            normalizeChild(child, child.name);
            List<ChildLayout> children = new ArrayList<>();
            children.add(child);
            return new Result("HMCL-PE", fallbackName, "HMCL-PE", "1.0", "", children);
        }
        throw new IllegalArgumentException("无法识别的控制布局格式");
    }

    /**
     * 解压 .hmclpe 并解析。返回的 Result 已包含完整子布局。
     */
    public static Result parseHmclPeZip(File zipFile, File workDir, String fallbackName) throws IOException {
        extractZip(zipFile, workDir);
        File layoutDir = findHmclDirectory(workDir, 0);
        if (layoutDir == null) {
            throw new IOException("压缩包里没有找到 info.json");
        }
        return parseHmclDirectory(layoutDir, fallbackName);
    }

    public static Result parseHmclDirectory(File dir, String fallbackName) {
        File infoFile = new File(dir, "info.json");
        ControlPattern info = null;
        if (infoFile.isFile()) {
            try {
                info = new Gson().fromJson(
                        FileStringUtils.getStringFromFile(infoFile.getAbsolutePath()),
                        ControlPattern.class);
            } catch (Throwable ignored) {
            }
        }
        String name = info == null || info.name == null || info.name.trim().isEmpty()
                ? fallbackName : info.name;
        String author = info == null || info.author == null ? "HMCL-PE" : info.author;
        String versionName = info == null || info.versionName == null ? "1.0" : info.versionName;
        String description = info == null || info.describe == null ? "" : info.describe;

        List<ChildLayout> children = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                    continue;
                }
                if ("info.json".equalsIgnoreCase(file.getName())) {
                    continue;
                }
                ChildLayout child;
                try {
                    child = new Gson().fromJson(
                            FileStringUtils.getStringFromFile(file.getAbsolutePath()),
                            ChildLayout.class);
                } catch (Throwable ignored) {
                    continue;
                }
                if (child == null) {
                    continue;
                }
                if (child.name == null || child.name.trim().isEmpty()) {
                    child.name = file.getName().substring(0, file.getName().length() - 5);
                }
                normalizeChild(child, child.name);
                children.add(child);
            }
        }
        if (children.isEmpty()) {
            throw new IllegalArgumentException("布局目录里没有可用的子布局");
        }
        return new Result("HMCL-PE", name, author, versionName, description, children);
    }

    /**
     * 另存为新布局，返回最终目录名（即布局名）。
     *
     * @throws IOException 写入失败
     */
    public static String saveAsNewPattern(Context context, Result result) throws IOException {
        if (result == null || result.children == null || result.children.isEmpty()) {
            throw new IOException("布局里没有控件");
        }
        String finalName = uniquePatternName(sanitizeName(result.sourceName, "导入布局"));

        Set<String> usedChildNames = new HashSet<>();
        Map<String, String> renamedChildren = new HashMap<>();
        List<String> finalChildNames = new ArrayList<>();
        for (int i = 0; i < result.children.size(); i++) {
            ChildLayout child = result.children.get(i);
            String proposed = sanitizeName(child.name, "layout_" + (i + 1));
            String finalChild = uniqueChildName(proposed, usedChildNames);
            usedChildNames.add(finalChild.toLowerCase(Locale.ROOT));
            finalChildNames.add(finalChild);
            if (!renamedChildren.containsKey(child.name)) {
                renamedChildren.put(child.name, finalChild);
            }
            child.name = finalChild;
        }

        for (ChildLayout child : result.children) {
            normalizeChild(child, child.name);
            if (child.baseButtonList != null) {
                for (BaseButtonInfo button : child.baseButtonList) {
                    button.pattern = finalName;
                    button.child = child.name;
                    button.visibilityControl = remapVisibility(button.visibilityControl,
                            renamedChildren, new HashSet<>(finalChildNames));
                }
            }
            if (child.baseRockerViewList != null) {
                for (BaseRockerViewInfo rocker : child.baseRockerViewList) {
                    rocker.pattern = finalName;
                    rocker.child = child.name;
                }
            }
        }

        File patternDir = new File(AppManifest.CONTROLLER_DIR, finalName);
        if (!patternDir.exists() && !patternDir.mkdirs()) {
            throw new IOException("无法创建布局目录：" + patternDir);
        }
        ControlPattern pattern = new ControlPattern(
                finalName,
                result.author == null || result.author.trim().isEmpty() ? "导入布局" : result.author,
                result.versionName == null || result.versionName.trim().isEmpty() ? "1.0" : result.versionName,
                result.description == null ? "" : result.description,
                1);
        FileStringUtils.writeFile(new File(patternDir, "info.json").getAbsolutePath(),
                new Gson().toJson(pattern));
        for (ChildLayout child : result.children) {
            ChildLayout.saveChildLayout(finalName, child);
        }
        return finalName;
    }

    // ------------------------------------------------------------- internals --

    private static void normalizeChild(ChildLayout child, String fallbackName) {
        if (child.name == null || child.name.trim().isEmpty()) {
            child.name = fallbackName;
        }
        if (child.baseButtonList == null) {
            child.baseButtonList = new ArrayList<>();
        }
        if (child.baseRockerViewList == null) {
            child.baseRockerViewList = new ArrayList<>();
        }
        for (BaseButtonInfo button : child.baseButtonList) {
            if (button.uuid == null || button.uuid.isEmpty()) {
                button.uuid = LayoutImportUtils.newUuid();
            }
            if (button.text == null) {
                button.text = "";
            }
            if (button.visibilityControl == null) {
                button.visibilityControl = new ArrayList<>();
            }
            if (button.outputKeycode == null) {
                button.outputKeycode = new ArrayList<>();
            }
            if (button.outputText == null) {
                button.outputText = "";
            }
            if (button.width == null) {
                button.width = new ButtonSize(50, 0.06f, LayoutImportUtils.REF_WIDTH);
            }
            if (button.height == null) {
                button.height = new ButtonSize(50, 0.06f, LayoutImportUtils.REF_HEIGHT);
            }
            if (button.xPosition == null) {
                button.xPosition = LayoutImportUtils.percentPosition(0.5f);
            }
            if (button.yPosition == null) {
                button.yPosition = LayoutImportUtils.percentPosition(0.5f);
            }
            if (button.buttonStyle == null) {
                button.buttonStyle = new ButtonStyle();
            }
        }
        for (BaseRockerViewInfo rocker : child.baseRockerViewList) {
            if (rocker.uuid == null || rocker.uuid.isEmpty()) {
                rocker.uuid = LayoutImportUtils.newUuid();
            }
            if (rocker.size == null) {
                rocker.size = new RockerSize(160, 0.2f, LayoutImportUtils.REF_HEIGHT);
            }
            if (rocker.xPosition == null) {
                rocker.xPosition = LayoutImportUtils.percentPosition(0.1f);
            }
            if (rocker.yPosition == null) {
                rocker.yPosition = LayoutImportUtils.percentPosition(0.8f);
            }
            if (rocker.rockerStyle == null) {
                rocker.rockerStyle = new RockerStyle();
            }
        }
    }

    private static ArrayList<String> remapVisibility(ArrayList<String> visibilityControl,
                                                      Map<String, String> renamedChildren,
                                                      Set<String> finalChildNames) {
        ArrayList<String> result = new ArrayList<>();
        if (visibilityControl == null) {
            return result;
        }
        for (String oldName : visibilityControl) {
            String mapped = renamedChildren.get(oldName);
            if (mapped == null) {
                mapped = oldName;
            }
            if (finalChildNames.contains(mapped) && !result.contains(mapped)) {
                result.add(mapped);
            }
        }
        return result;
    }

    private static void extractZip(File zipFile, File workDir) throws IOException {
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new IOException("无法创建临时目录：" + workDir);
        }
        try {
            ZipFile zip = new ZipFile(zipFile);
            if (zip.isEncrypted()) {
                zip.setPassword(HMCL_PE_PASSWORD);
            }
            long total = 0;
            @SuppressWarnings("unchecked")
            List<FileHeader> headers = zip.getFileHeaders();
            for (FileHeader header : headers) {
                String name = header.getFileName();
                if (name == null) {
                    throw new IOException("压缩包条目名无效");
                }
                String normalized = name.replace('\\', '/');
                if (normalized.startsWith("/") || normalized.contains("..")
                        || normalized.matches("^[A-Za-z]:.*")) {
                    throw new IOException("压缩包包含非法路径：" + name);
                }
                total += Math.max(0, header.getUncompressedSize());
                if (total > MAX_HMCL_PE_UNCOMPRESSED) {
                    throw new IOException("压缩包解压后过大");
                }
            }
            zip.extractAll(workDir.getAbsolutePath());
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("解压 HMCL-PE 布局失败（密码或文件格式不正确）", t);
        }
    }

    private static File findHmclDirectory(File dir, int depth) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        if (new File(dir, "info.json").isFile()) {
            return dir;
        }
        if (depth >= 2) {
            return null;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findHmclDirectory(child, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String uniquePatternName(String base) {
        Set<String> existing = new HashSet<>();
        File[] dirs = new File(AppManifest.CONTROLLER_DIR).listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                // 大小写不敏感：FAT/exFAT 外置存储与 Windows 上 "Default"/"default" 是同一目录
                existing.add(dir.getName().toLowerCase(Locale.ROOT));
            }
        }
        String candidate = base;
        int index = 2;
        while (existing.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + " (" + index + ")";
            index++;
        }
        return candidate;
    }

    private static String uniqueChildName(String base, Set<String> used) {
        String candidate = base;
        int index = 2;
        while (used.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + "_" + index;
            index++;
        }
        return candidate;
    }

    private static String sanitizeName(String raw, String fallback) {
        String name = raw == null ? "" : raw.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        name = name.replaceAll("^[.\\s]+", "");
        name = name.replaceAll("[.\\s]+$", "");
        if (name.length() > 48) {
            name = name.substring(0, 48).trim();
        }
        return name.isEmpty() ? fallback : name;
    }
}
