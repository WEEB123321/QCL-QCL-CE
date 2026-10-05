package com.qcl.launcher.launcher.launch;

import android.util.ArrayMap;
import android.util.Log;
import com.google.gson.Gson;
import com.qcl.launcher.launcher.setting.game.GameLaunchSetting;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* loaded from: classes2.dex */
public class LaunchVersion {
    public static String LAUNCHER_NAME = "QCL";
    public static String LAUNCHER_VERSION = "";
    private Map<String, String> SHAs;
    public Arguments arguments;
    public AssetsIndex assetIndex;
    public String assets;
    public HashMap<String, Download> downloads;
    public String id;
    public String inheritsFrom;
    public Library[] libraries;
    public String mainClass;
    public String minecraftArguments;
    public String minecraftPath;
    public int minimumLauncherVersion;
    public String releaseTime;
    public String time;
    public String type;

    public static void setLauncherIdentity(String str, String str2) {
        if (str != null && !str.isEmpty()) {
            LAUNCHER_NAME = str;
        }
        if (str2 != null) {
            LAUNCHER_VERSION = str2;
        }
    }

    /* loaded from: classes2.dex */
    public class AssetsIndex {
        public String id;
        public String sha1;
        public int size;
        public int totalSize;
        public String url;

        public AssetsIndex() {
        }
    }

    /* loaded from: classes2.dex */
    public class Download {
        public String path;
        public String sha1;
        public int size;
        public String url;

        public Download() {
        }
    }

    /* loaded from: classes2.dex */
    public class Library {
        public HashMap<String, Download> downloads;
        public String name;

        public Library() {
        }
    }

    /* loaded from: classes2.dex */
    public class Arguments {
        private Object[] game;
        private Object[] jvm;

        public Arguments() {
        }
    }

    public static LaunchVersion fromDirectory(File file) {
        try {
            LaunchVersion launchVersion = (LaunchVersion) new Gson().fromJson(new String(readAllBytes(new File(file, file.getName() + ".json")), "UTF-8"), LaunchVersion.class);
            // ★★★ 社区版加固：JSON 缺失 / 0 字节 / 内容不合法时 Gson 返回 **null**，
            //   而紧接着就会被解引用 → NPE。这条路径比看起来容易走到：
            //   readAllBytes 对「文件不存在」是 **返回空数组而不是抛异常**（见本文件末尾），
            //   空串喂给 Gson 恰好得到 null。所以「版本目录里只有 jar」「json 写坏了」
            //   「远古版本安装中断」都会命中。
            //   原来这里只 catch UnsupportedEncodingException —— NPE 会一路穿到启动流程，
            //   对外表现就是「点启动直接闪退」。这里返回一个**结构完整的空壳**，
            //   让后面的检查阶段能给出「版本文件缺失」这种可读的提示，而不是把进程带走。
            if (launchVersion == null) {
                LaunchVersion fallback = new LaunchVersion();
                fallback.id = file.getName();
                fallback.libraries = new Library[0];
                fallback.minecraftPath = new File(file, file.getName() + ".jar").exists()
                        ? new File(file, file.getName() + ".jar").getAbsolutePath() : "";
                android.util.Log.w("QCL", "版本 JSON 缺失或损坏，已用空壳兜底: " + file.getAbsolutePath());
                return fallback;
            }
            if (launchVersion.libraries == null) {
                launchVersion.libraries = new Library[0];
            }
            if (new File(file, file.getName() + ".jar").exists()) {
                launchVersion.minecraftPath = new File(file, file.getName() + ".jar").getAbsolutePath();
            } else {
                launchVersion.minecraftPath = "";
            }
            String str = launchVersion.inheritsFrom;
            if (str == null || str.equals("")) {
                return launchVersion;
            }
            LaunchVersion fromDirectory = fromDirectory(new File(file.getParentFile(), launchVersion.inheritsFrom));
            // ★ 父版本读不出来时（Forge/NeoForge 的 inheritsFrom 指向的版本被删了）就返回自己，
            //   否则下面一行会解引用 null。
            if (fromDirectory == null) {
                android.util.Log.w("QCL", "父版本不存在，已回退到自身: " + launchVersion.inheritsFrom);
                return launchVersion;
            }
            AssetsIndex assetsIndex = launchVersion.assetIndex;
            if (assetsIndex != null) {
                fromDirectory.assetIndex = assetsIndex;
            }
            String str2 = launchVersion.assets;
            if (str2 != null && !str2.equals("")) {
                fromDirectory.assets = launchVersion.assets;
            }
            HashMap<String, Download> hashMap = launchVersion.downloads;
            if (hashMap != null && !hashMap.isEmpty()) {
                if (fromDirectory.downloads == null) {
                    fromDirectory.downloads = new HashMap<>();
                }
                for (Map.Entry<String, Download> entry : launchVersion.downloads.entrySet()) {
                    fromDirectory.downloads.put(entry.getKey(), entry.getValue());
                }
            }
            Library[] libraryArr = launchVersion.libraries;
            if (libraryArr != null && libraryArr.length > 0) {
                Library[] libraryArr2 = new Library[fromDirectory.libraries.length + libraryArr.length];
                int i = 0;
                for (Library library : libraryArr) {
                    libraryArr2[i] = library;
                    i++;
                }
                for (Library library2 : fromDirectory.libraries) {
                    libraryArr2[i] = library2;
                    i++;
                }
                fromDirectory.libraries = libraryArr2;
            }
            String str3 = launchVersion.mainClass;
            if (str3 != null && !str3.equals("")) {
                fromDirectory.mainClass = launchVersion.mainClass;
            }
            String str4 = launchVersion.minecraftArguments;
            if (str4 != null && !str4.equals("")) {
                fromDirectory.minecraftArguments = launchVersion.minecraftArguments;
            }
            int i2 = launchVersion.minimumLauncherVersion;
            if (i2 > fromDirectory.minimumLauncherVersion) {
                fromDirectory.minimumLauncherVersion = i2;
            }
            String str5 = launchVersion.releaseTime;
            if (str5 != null && !str5.equals("")) {
                fromDirectory.releaseTime = launchVersion.releaseTime;
            }
            String str6 = launchVersion.time;
            if (str6 != null && !str6.equals("")) {
                fromDirectory.time = launchVersion.time;
            }
            String str7 = launchVersion.type;
            if (str7 != null && !str7.equals("")) {
                fromDirectory.type = launchVersion.type;
            }
            String str8 = launchVersion.minecraftPath;
            if (str8 != null && !str8.equals("")) {
                fromDirectory.minecraftPath = launchVersion.minecraftPath;
            }
            // ★★★ 社区版加固（低版本闪退）：原判断只查了 `launchVersion.arguments.game`，
            //   却先解引用了 `launchVersion.arguments` 本身。远古版本 / 只写 minecraftArguments
            //   的整合版本 json 里**没有 arguments 对象**，这里就是一次必崩的 NPE；
            //   而外层只 catch UnsupportedEncodingException，NPE 会一路穿到启动流程。
            //   顺带把 fromDirectory.arguments 也判掉（父版本同样可能没有 arguments）。
            if (fromDirectory.minimumLauncherVersion >= 21
                    && launchVersion.arguments != null && launchVersion.arguments.game != null
                    && launchVersion.arguments.game.length > 0
                    && fromDirectory.arguments != null && fromDirectory.arguments.game != null) {
                Object[] objArr = new Object[fromDirectory.arguments.game.length + launchVersion.arguments.game.length];
                int i3 = 0;
                for (Object obj : launchVersion.arguments.game) {
                    objArr[i3] = obj;
                    i3++;
                }
                for (Object obj2 : fromDirectory.arguments.game) {
                    objArr[i3] = obj2;
                    i3++;
                }
                fromDirectory.arguments.game = objArr;
            }
            return fromDirectory;
        } catch (UnsupportedEncodingException unused) {
            return null;
        }
    }

    private static byte[] readAllBytes(File file) {
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream((int) Math.max(1024L, file.length()));
                byte[] bArr = new byte[8192];
                while (true) {
                    int read = fileInputStream.read(bArr);
                    if (read <= 0) {
                        byte[] byteArray = byteArrayOutputStream.toByteArray();
                        fileInputStream.close();
                        return byteArray;
                    }
                    byteArrayOutputStream.write(bArr, 0, read);
                }
            } finally {
            }
        } catch (Throwable th) {
            th.printStackTrace();
            return new byte[0];
        }
    }

    public String getClassPath(String str, boolean z, boolean z2) {
        String str2 = str + "/libraries/";
        int i = 0;
        String str3 = "";
        // ★★★ 社区版加固（低版本闪退）：原实现有两个必崩点 ——
        //   ① `this.libraries` 为 null 时直接 for-each → NPE
        //      （远古版本 / 只写了 jar 的整合版本 json 可能没有 libraries 字段）；
        //   ② `library.name.split(":")` 之后**直接取 split[1] / split[2]**，
        //      而库名不一定是「组:名:版本」三段式（远古 Forge 的 json 里就出现过
        //      两段甚至一段的库名）→ ArrayIndexOutOfBoundsException。
        //   两者都在「构建 classpath」这一步，属于启动必经之路，命中就是闪退。
        //   现在：libraries 为 null 直接返回；库名不是三段式就**跳过这个库**
        //   （游戏随后会以 NoClassDefFoundError 报出来，那是个可读的错误，
        //    比启动器自己崩掉强得多）。
        if (this.libraries == null) {
            return this.minecraftPath == null ? "" : this.minecraftPath;
        }
        for (Library library : this.libraries) {
            if (library == null) {
                continue;
            }
            if (library.name != null && !library.name.equals("") && !library.name.contains("org.lwjgl") && !library.name.contains("natives") && (!z2 || !library.name.contains("java-objc-bridge"))) {
                String[] split = library.name.split(":");
                if (split.length < 3) {
                    Log.w("QCL", "库名不是「组:名:版本」三段式，已跳过: " + library.name);
                    continue;
                }
                String str4 = split[0];
                String str5 = split[1];
                String str6 = split[2];
                String str7 = (((((("" + str2) + str4.replaceAll("\\.", "/")) + "/") + str5) + "/") + str6) + "/" + str5 + "-" + str6 + ".jar";
                if (new File(str7).exists()) {
                    if (i > 0) {
                        str3 = str3 + ":";
                    }
                    str3 = str3 + str7;
                    i++;
                }
            }
        }
        String str8 = i > 0 ? ":" : "";
        String mcPath = this.minecraftPath == null ? "" : this.minecraftPath;
        if (z) {
            return str3 + str8 + mcPath;
        }
        return mcPath + str8 + str3;
    }

    public String[] getJVMArguments(GameLaunchSetting gameLaunchSetting) {
        String str;
        StringBuilder sb = new StringBuilder();
        Arguments arguments = this.arguments;
        if (arguments == null || arguments.jvm == null) {
            return new String[0];
        }
        for (Object obj : this.arguments.jvm) {
            if (obj instanceof String) {
                String str2 = (String) obj;
                if (!str2.startsWith("-Djava.library.path") && !str2.startsWith("-cp") && !str2.startsWith("${classpath}")) {
                    sb.append(obj.toString()).append(" ");
                }
            }
        }
        String str3 = "";
        boolean z = false;
        int i = 0;
        for (int i2 = 0; i2 < sb.length(); i2++) {
            if (!z) {
                if (sb.charAt(i2) != '$') {
                    str3 = str3 + sb.charAt(i2);
                } else {
                    int i3 = i2 + 1;
                    if (i3 >= sb.length() || sb.charAt(i3) != '{') {
                        str3 = str3 + sb.charAt(i2);
                    } else {
                        z = true;
                        i = i2;
                    }
                }
            } else if (sb.charAt(i2) == '}') {
                String substring = sb.substring(i + 2, i2);
                if (substring.equals("version_name")) {
                    str = this.id;
                } else if (substring.equals("launcher_name")) {
                    str = LAUNCHER_NAME;
                } else if (substring.equals("launcher_version")) {
                    str = LAUNCHER_VERSION;
                } else if (substring.equals("version_type")) {
                    str = LAUNCHER_NAME;
                } else if (substring.equals("assets_index_name")) {
                    AssetsIndex assetsIndex = this.assetIndex;
                    if (assetsIndex != null) {
                        str = assetsIndex.id;
                    } else {
                        str = this.assets;
                    }
                } else if (substring.equals("game_directory")) {
                    str = gameLaunchSetting.game_directory;
                } else if (substring.equals("assets_root") || substring.equals("game_assets")) {
                    str = gameLaunchSetting.gameFileDirectory + "/assets";
                } else if (substring.equals("user_properties")) {
                    str = "{}";
                } else if (substring.equals("auth_player_name")) {
                    str = gameLaunchSetting.account.auth_player_name;
                } else if (substring.equals("auth_session")) {
                    str = gameLaunchSetting.account.auth_session;
                } else if (substring.equals("auth_uuid")) {
                    str = gameLaunchSetting.account.auth_uuid;
                } else if (substring.equals("auth_access_token")) {
                    str = gameLaunchSetting.account.auth_access_token;
                } else if (substring.equals("user_type")) {
                    str = gameLaunchSetting.account.user_type;
                } else if (substring.equals("primary_jar_name")) {
                    str = new File(gameLaunchSetting.currentVersion).getName() + ".jar";
                } else if (substring.equals("library_directory")) {
                    str = gameLaunchSetting.gameFileDirectory + "/libraries";
                } else {
                    str = substring.equals("classpath_separator") ? ":" : "";
                }
                str3 = str3 + str;
                z = false;
            }
        }
        return str3.split(" ");
    }

    public String[] getMinecraftArguments(GameLaunchSetting gameLaunchSetting, boolean z) {
        Arguments arguments;
        String str;
        StringBuilder sb = new StringBuilder();
        // ★★★ 社区版加固（低版本闪退）：原实现两条分支**都可能 NPE**，而且都是启动必经之路 ——
        //   ① z=true（minimumLauncherVersion >= 21）时直接读 `this.arguments.game`，
        //      但 json 里可能根本没有 arguments 对象；
        //   ② z=false（远古版本 / 未声明 minimumLauncherVersion）时
        //      `new StringBuilder(this.minecraftArguments)` —— 该字段为 null 时
        //      StringBuilder 构造器直接抛 NPE。
        //   远古归档版本、以及「只写 minecraftArguments 的整合版本」都容易命中。
        //   这里改成按「实际有什么」选分支：两边都没有就当空参数（不抛异常），
        //   由后面的检查阶段给出可读错误，而不是把进程带走。
        if (z && this.arguments != null && this.arguments.game != null) {
            for (Object obj : this.arguments.game) {
                if (obj instanceof String) {
                    sb.append(obj.toString()).append(" ");
                }
            }
        } else if (this.minecraftArguments != null) {
            sb = new StringBuilder(this.minecraftArguments);
        } else if (this.arguments != null && this.arguments.game != null) {
            // 兜底：即便是「远古」判定，只要有 arguments 也能用
            for (Object obj : this.arguments.game) {
                if (obj instanceof String) {
                    sb.append(obj.toString()).append(" ");
                }
            }
        }
        // 两边都没有 → sb 保持空串，继续走完流程（不在这里崩）
        boolean z2 = false;
        int i = 0;
        String str2 = "";
        for (int i2 = 0; i2 < sb.length(); i2++) {
            if (!z2) {
                if (sb.charAt(i2) != '$') {
                    str2 = str2 + sb.charAt(i2);
                } else {
                    int i3 = i2 + 1;
                    if (i3 >= sb.length() || sb.charAt(i3) != '{') {
                        str2 = str2 + sb.charAt(i2);
                    } else {
                        z2 = true;
                        i = i2;
                    }
                }
            } else if (sb.charAt(i2) == '}') {
                String substring = sb.substring(i + 2, i2);
                if (substring.equals("version_name")) {
                    str = this.id;
                } else if (substring.equals("launcher_name")) {
                    str = LAUNCHER_NAME;
                } else if (substring.equals("launcher_version")) {
                    str = LAUNCHER_VERSION;
                } else if (substring.equals("version_type")) {
                    str = LAUNCHER_NAME;
                } else if (substring.equals("assets_index_name")) {
                    AssetsIndex assetsIndex = this.assetIndex;
                    if (assetsIndex != null) {
                        str = assetsIndex.id;
                    } else {
                        str = this.assets;
                    }
                } else if (substring.equals("game_directory")) {
                    str = gameLaunchSetting.game_directory;
                } else if (substring.equals("assets_root") || substring.equals("game_assets")) {
                    str = gameLaunchSetting.gameFileDirectory + "/assets";
                } else if (substring.equals("user_properties")) {
                    str = "{}";
                } else if (substring.equals("auth_player_name")) {
                    str = gameLaunchSetting.account.auth_player_name;
                } else if (substring.equals("auth_session")) {
                    str = gameLaunchSetting.account.auth_session;
                } else if (substring.equals("auth_uuid")) {
                    str = gameLaunchSetting.account.auth_uuid;
                } else if (substring.equals("auth_access_token")) {
                    str = gameLaunchSetting.account.auth_access_token;
                } else if (substring.equals("user_type")) {
                    str = gameLaunchSetting.account.user_type;
                } else if (substring.equals("primary_jar_name")) {
                    str = new File(gameLaunchSetting.currentVersion).getName() + ".jar";
                } else if (substring.equals("library_directory")) {
                    str = gameLaunchSetting.gameFileDirectory + "/libraries";
                } else {
                    str = substring.equals("classpath_separator") ? ":" : "";
                }
                str2 = str2 + str;
                z2 = false;
            }
        }
        if (!z && (arguments = this.arguments) != null && arguments.game != null) {
            for (Object obj2 : this.arguments.game) {
                if (obj2 instanceof String) {
                    str2 = str2 + " " + obj2.toString();
                }
            }
        }
        return str2.split(" ");
    }

    public List<String> getLibraries() {
        ArrayList arrayList = new ArrayList();
        // ★★★ 社区版加固（低版本闪退）：原实现直接 for-each this.libraries 并调用
        //   parseLibNameToPath —— 而那个方法会对库名做 split(":") 后取 [1]/[2]。
        //   libraries 为 null、或库名不是三段式，都会在这里抛异常，
        //   且本方法**没有** try/catch（getSHA1 那边有），异常会一路穿到启动流程。
        if (this.libraries == null) {
            return arrayList;
        }
        for (Library library : this.libraries) {
            if (library == null) {
                continue;
            }
            if (library.name != null && !library.name.equals("") && !library.name.contains("net.java.jinput") && !library.name.contains("org.lwjgl") && !library.name.contains("platform")) {
                String libPath = parseLibNameToPath(library.name);
                if (libPath != null) {
                    arrayList.add(libPath);
                }
            }
        }
        return arrayList;
    }

    public String getSHA1(String str) {
        if (this.SHAs == null) {
            this.SHAs = new ArrayMap();
            for (Library library : this.libraries) {
                if (library.name != null && !library.name.equals("") && !library.name.contains("net.java.jinput") && !library.name.contains("org.lwjgl") && !library.name.contains("platform")) {
                    try {
                        this.SHAs.put(parseLibNameToPath(library.name), library.downloads.get("artifact").sha1);
                    } catch (Exception unused) {
                    }
                }
            }
        }
        return this.SHAs.get(str);
    }

    public String parseLibNameToPath(String str) {
        String[] split = str.split(":");
        // ★★★ 社区版加固：库名不一定是「组:名:版本」三段式 —— 远古 Forge 的版本 json 里
        //   出现过两段甚至一段的库名。原实现直接取 split[1]/split[2]，
        //   一命中就是 ArrayIndexOutOfBoundsException。
        //   getSHA1() 那边有 try/catch 兜着看不出来，但 getLibraries() 没有 → 启动闪退。
        //   这里返回 null 表示「这个库名不可解析」，由调用方跳过。
        if (split.length < 3) {
            return null;
        }
        return split[0].replace(".", "/") + "/" + split[1] + "/" + split[2] + "/" + split[1] + "-" + split[2] + ".jar";
    }
}
