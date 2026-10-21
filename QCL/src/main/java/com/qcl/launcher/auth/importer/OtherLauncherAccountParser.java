package com.qcl.launcher.auth.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ★★★ 其它启动器账户的**宽容解析器**（FCL / ZL2 / HMCL-PE / 以及各种分支）。
 *
 * <h3>为什么不按某个固定 schema 解析</h3>
 * 这几个启动器都是同源分叉（HMCL-PE → FCL → ZL2 …），账户文件都叫 {@code accounts.json}，
 * 但字段名**每一代都改过**：有的叫 {@code userName}，有的叫 {@code auth_player_name}，
 * 有的把 token 藏在 {@code ygg.token.accessToken} 里。写死一个 schema 只能读一种，
 * 而且上游一改版就失效 —— 这个项目已经因为「写死某版本的字段名」翻车过。
 *
 * <p>所以这里换思路：<b>不认 schema，认「长得像账户的东西」</b>。
 * <ol>
 *   <li>把整棵 JSON 树走一遍，任何「带名字字段」的对象都当作候选；</li>
 *   <li>取字段时按<b>别名表</b>匹配，并且允许往下钻一层（{@code ygg.token.accessToken}）；</li>
 *   <li>名字为空的对象一律丢弃 —— 没有名字的账户导入进来也没法用。</li>
 * </ol>
 *
 * <h3>★ 登录类型的推断（两段式）</h3>
 * <ol>
 *   <li>先看源文件自己写的类型字段（{@code loginType} / {@code accountType} / {@code authType} …），
 *       值里含 {@code microsoft}/{@code msa} → 微软；含 {@code authlib}/{@code yggdrasil}/{@code nide} → 外置；
 *       含 {@code mojang} → 旧正版；含 {@code offline}/{@code local} → 离线。</li>
 *   <li>类型字段读不出来或认不出时，按「手里有什么凭据」反推：
 *       有 refresh_token 且没有外置服务器 → 微软；有外置服务器地址 → 外置；
 *       只有 access_token → 旧正版；什么都没有 → 离线。</li>
 * </ol>
 *
 * <h3>★ 头像（用户特别问过「是否要用之前用过的头像」）</h3>
 * QCL 的 {@code Account.texture} 存的是**整张皮肤的 Base64 PNG**（64×64），
 * 渲染时再从 (8,8) 裁脸、(40,8) 裁帽子（见 {@code skin/utils/Avatar.java}）。
 * 所以：
 * <ul>
 *   <li>源里有 base64 皮肤/头像 → 原样带过来（{@link Imported#skinBase64}）；</li>
 *   <li>源里只有皮肤<b>文件路径</b> → 记在 {@link Imported#skinPath}，由 UI 层去读文件；</li>
 *   <li>都没有 → 留空，导入时用默认皮肤（与离线账户一致）。</li>
 * </ul>
 */
public final class OtherLauncherAccountParser {

    private OtherLauncherAccountParser() {
    }

    /** 解析出来的一个账户（还没变成 {@code Account}，因为要先让用户勾选）。 */
    public static final class Imported {
        public String name = "";
        public String uuid = "";
        public String accessToken = "";
        public String clientToken = "";
        public String refreshToken = "";
        public String loginServer = "";
        /** 源文件里的整张皮肤 Base64（可能是空串）。 */
        public String skinBase64 = "";
        /** 源文件里记录的皮肤文件路径（可能是空串）。 */
        public String skinPath = "";
        /** 推断出的 QCL loginType：1 离线 / 2 Mojang / 3 微软 / 4 外置 / 5 Nide8。 */
        public int loginType = 1;
        /** 来源描述，例如「FCL · accounts.json」。 */
        public String source = "";
        /** 给人看的类型标签。 */
        public String typeLabel = "离线";

        public String key() {
            return (name == null ? "" : name.toLowerCase(Locale.ROOT)) + "#" + loginType;
        }

        @Override
        public String toString() {
            return name + "（" + typeLabel + "）";
        }
    }

    // ────────────────────────────── 别名表 ──────────────────────────────
    // ★ 全部小写、去掉下划线后比较，这样 userName / user_name / USERNAME 都能命中。
    // ★★ 数组的**顺序就是优先级**（见 shallowGet 的注释），不是随便排的。

    private static final String[] NAME_KEYS = {
            // ① 明确的「角色名」字段优先
            "authplayername", "displayname", "playername", "profilename",
            "selectedprofilename", "playernamecn",
            // ② 其次才是「登录名」—— 外置登录里 username 是登录账号，
            //    displayName 才是游戏内角色名，两者可能不同，所以不能排前面
            "username", "name", "nickname",
    };
    private static final String[] UUID_KEYS = {
            "authuuid", "playeruuid", "profileid", "uuid", "id", "userid",
    };
    private static final String[] ACCESS_KEYS = {
            "authaccesstoken", "accesstoken", "yggdrasiltoken", "token", "mctoken",
    };
    private static final String[] CLIENT_KEYS = {
            "authclienttoken", "clienttoken",
    };
    private static final String[] REFRESH_KEYS = {
            "refreshtoken", "msrefreshtoken", "msarefreshtoken", "oauthtoken",
    };
    private static final String[] SERVER_KEYS = {
            // ★ serverbaseurl 是 FCL 的实际字段名（AuthlibInjectorAccountFactory.fromStorage
            //   里读的就是 storage.get("serverBaseURL")），漏了它外置账户会丢服务器地址，
            //   导入进来就成了「有账号但连不上」。
            "serverbaseurl", "loginserver", "authlibinjectorserver", "apiroot",
            "authserver", "serverurl", "yggdrasilurl", "yggdrasilapi", "authlibserver",
    };
    private static final String[] SKIN_B64_KEYS = {
            "texture", "avatar", "avatarbase64", "skinbase64", "headimage",
            "profileimage", "skinimage", "avatarimage", "faceimage",
    };
    private static final String[] SKIN_PATH_KEYS = {
            // ★ localskinpath 是 FCL 的字段名（Skin.toStorage 里 localSkinPath）
            "localskinpath", "skinpath", "localskin", "skinfile", "skinfiledir", "capeskin",
    };
    private static final String[] TYPE_KEYS = {
            "logintype", "accounttype", "authtype", "type", "authenticationtype", "authservertype",
    };

    // ────────────────────────────── 入口 ──────────────────────────────

    /**
     * 解析一个 {@code accounts.json}（或任何结构类似的 JSON）。
     *
     * @param file       源文件
     * @param sourceName 来源名（写进 {@link Imported#source}，只用于显示）
     * @return 解析出的候选账户；文件读不了/不是 JSON 时返回空表，<b>不抛异常</b>
     */
    public static List<Imported> parse(File file, String sourceName) {
        List<Imported> out = new ArrayList<>();
        if (file == null || !file.isFile()) {
            return out;
        }
        JsonElement root;
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8"));
            root = JsonParser.parseReader(reader);
        } catch (Throwable t) {
            return out;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
        Set<String> seen = new HashSet<>();
        collect(root, sourceName, out, seen, 0);
        return out;
    }

    /** 递归收集。★ 深度上限 12：防止畸形/自引用结构把递归撑爆。 */
    private static void collect(JsonElement el, String source, List<Imported> out,
                                Set<String> seen, int depth) {
        if (el == null || depth > 12) {
            return;
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                collect(arr.get(i), source, out, seen, depth + 1);
            }
            return;
        }
        if (!el.isJsonObject()) {
            return;
        }
        JsonObject obj = el.getAsJsonObject();
        Imported one = fromObject(obj, source);
        if (one != null) {
            // ★★★ 认出来了就**不再往里钻**。
            //   踩过的坑：HMCL 系的结构是
            //     {"acc-1": {"type":"microsoft", "profile":{"name":"NotchFan","id":"..."}}}
            //   外层对象靠「钻一层」拿到了 profile.name，已经被认作账户；
            //   如果继续递归，里面的 {"profile":{...}} 自己也有 name ⇒
            //   会被当成**第二个账户**（类型退化成「离线」），列表里凭空多出一条假数据。
            //   实测 hmcl.json 时就是这么冒出来的，所以这里直接 return。
            if (seen.add(one.key())) {
                out.add(one);
            }
            return;
        }
        // 没认出来（比如 {"accounts": [...]} 这层包装）⇒ 继续往下走
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            JsonElement v = e.getValue();
            if (v != null && (v.isJsonObject() || v.isJsonArray())) {
                collect(v, source, out, seen, depth + 1);
            }
        }
    }

    /** 把一个 JSON 对象当成账户试试；名字为空就返回 null（不是账户）。 */
    private static Imported fromObject(JsonObject obj, String source) {
        String name = deepGet(obj, NAME_KEYS);
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        Imported a = new Imported();
        a.name = name.trim();
        a.source = source;

        String uuid = deepGet(obj, UUID_KEYS);
        a.uuid = uuid == null ? "" : uuid.trim();
        String access = deepGet(obj, ACCESS_KEYS);
        a.accessToken = access == null ? "" : access.trim();
        String client = deepGet(obj, CLIENT_KEYS);
        a.clientToken = client == null ? "" : client.trim();
        String refresh = deepGet(obj, REFRESH_KEYS);
        a.refreshToken = refresh == null ? "" : refresh.trim();
        String server = deepGet(obj, SERVER_KEYS);
        a.loginServer = server == null ? "" : server.trim();
        String b64 = deepGet(obj, SKIN_B64_KEYS);
        a.skinBase64 = (b64 == null || b64.length() < 64) ? "" : b64.trim();
        String sp = deepGet(obj, SKIN_PATH_KEYS);
        a.skinPath = sp == null ? "" : sp.trim();

        a.loginType = inferType(obj, a);
        a.typeLabel = labelOf(a.loginType);
        return a;
    }

    // ────────────────────────────── 取字段 ──────────────────────────────

    /**
     * 按别名表取值：先看本层，再看**下一层**的每个子对象。
     *
     * <p>为什么要钻一层：FCL 系把凭据放在 {@code {"ygg": {"accessToken": ...}}}，
     * HMCL 系放在 {@code {"selectedProfile": {"name": ...}}}。钻一层能同时兜住。
     * 只钻一层是刻意的 —— 钻太深容易把「服务器配置」里的 {@code name} 误当成玩家名。
     */
    private static String deepGet(JsonObject obj, String[] keys) {
        String v = shallowGet(obj, keys);
        if (v != null) {
            return v;
        }
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            JsonElement child = e.getValue();
            if (child != null && child.isJsonObject()) {
                v = shallowGet(child.getAsJsonObject(), keys);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    /**
     * 按别名表的**顺序**取值 —— 第一个命中的键优先。
     *
     * <p>★★★ 为什么必须按别名表顺序、而不是按 JSON 里的字段顺序：
     * FCL 的外置账户同时有 {@code username}（登录账号）和 {@code displayName}（游戏内角色名），
     * 两者可能不同。如果按 JSON 字段顺序遍历，谁先出现就用谁 —— 那等于让**文件的键序**
     * 决定用哪个名字，同一个账户换个序列化库导出就可能变成另一个名字。
     * 所以这里反过来：外层循环走别名表（优先级），内层找字段。
     */
    private static String shallowGet(JsonObject obj, String[] keys) {
        for (String want : keys) {
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (!normalize(e.getKey()).equals(want)) {
                    continue;
                }
                JsonElement v = e.getValue();
                if (v == null || v.isJsonNull() || !v.isJsonPrimitive()) {
                    continue;
                }
                String s = v.getAsString();
                if (s != null && !s.isEmpty() && !"null".equals(s)) {
                    return s;
                }
            }
        }
        return null;
    }

    /** 键名归一化：小写 + 去掉 {@code _ - .} 和空格。 */
    private static String normalize(String key) {
        if (key == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_' || c == '-' || c == '.' || c == ' ') {
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    // ────────────────────────────── 类型推断 ──────────────────────────────

    private static int inferType(JsonObject obj, Imported a) {
        // ★★★ 类型字段只在本层找，**不往下钻**。
        //   踩过的坑：FCL 的离线账户带一个 skin 子对象
        //     "skin": {"type": "local_file", "localSkinPath": "..."}
        //   往下钻的话会把 skin.type 当成账户类型，"local_file" 含 "local"
        //   ⇒ 一个微软账户被推断成「离线」。只读本层就不会撞上。
        String raw = shallowGet(obj, TYPE_KEYS);
        if (raw != null) {
            String t = normalize(raw);
            if (t.contains("microsoft") || t.contains("msa") || t.contains("xbox")) {
                return 3;
            }
            if (t.contains("nide")) {
                return 5;
            }
            if (t.contains("authlib") || t.contains("yggdrasil") || t.contains("thirdparty")) {
                return 4;
            }
            if (t.contains("mojang") || t.contains("legacy")) {
                return 2;
            }
            if (t.contains("offline") || t.contains("local")) {
                return 1;
            }
            // 有些文件把类型写成数字 1~5
            try {
                int n = Integer.parseInt(t);
                if (n >= 1 && n <= 5) {
                    return n;
                }
            } catch (Throwable ignored) {
            }
        }
        // 兜底：按手里有什么凭据反推
        if (!a.loginServer.isEmpty()) {
            return a.loginServer.toLowerCase(Locale.ROOT).contains("nide") ? 5 : 4;
        }
        if (!a.refreshToken.isEmpty()) {
            return 3;
        }
        if (!a.accessToken.isEmpty()) {
            return 2;
        }
        return 1;
    }

    private static String labelOf(int loginType) {
        switch (loginType) {
            case 3:
                return "微软正版";
            case 4:
                return "外置登录";
            case 5:
                return "Nide8 外置";
            case 2:
                return "Mojang 正版";
            default:
                return "离线";
        }
    }
}
