package com.qcl.launcher.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;

/* loaded from: classes2.dex */
public class LocaleUtils {
    public static boolean isChinese(Context context) {
        int i = context.getSharedPreferences("lang", 0).getInt("lang", 0);
        return i == 2 || (i == 0 && getSystemLocale() == Locale.CHINA);
    }

    public static String getMinecraftLang(Context context) {
        int i = context.getSharedPreferences("lang", 0).getInt("lang", 0);
        if (i == 1) {
            return "en_us";
        }
        if (i == 2) {
            return "zh_cn";
        }
        if (i == 3) {
            return "zh_tw";
        }
        Locale systemLocale = getSystemLocale();
        return Locale.SIMPLIFIED_CHINESE.getLanguage().equals(systemLocale.getLanguage()) ? "zh_cn" : Locale.TRADITIONAL_CHINESE.getLanguage().equals(systemLocale.getLanguage()) ? "zh_tw" : "en_us";
    }

    /**
     * 按 MC 版本规范语言代码的大小写（对齐 FCL {@code FCLGameLauncher.fixLang}）：
     * 远古/低版本（< 1.11）要求地区码**大写**（{@code zh_CN}），1.11 起改为**小写**（{@code zh_cn}）；
     * 1.1 以前的版本不动。
     *
     * @param versionId 版本目录名，如 "1.20.6"、"b1.7.3"、"1.12.2-forge-xx"
     */
    public static String normalizeMinecraftLang(String versionId, String lang) {
        if (lang == null) {
            return null;
        }
        String[] parts = lang.split("_", 2);
        if (parts.length != 2) {
            return lang;
        }
        double v = parseMcVersion(versionId);
        if (v <= 0.0 || v < 1.1) {
            return lang;
        }
        boolean toUpper = v < 1.11;
        return parts[0] + "_" + (toUpper ? parts[1].toUpperCase(Locale.ROOT) : parts[1].toLowerCase(Locale.ROOT));
    }

    /** 从版本目录名解析版本号：1.20.6 -> 1.2006；b1.7.3 -> 1.0703（够做 1.1 / 1.11 比较）。 */
    private static double parseMcVersion(String versionId) {
        if (versionId == null) {
            return 0.0;
        }
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(versionId);
            if (m.find()) {
                double v = Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2)) / 100.0;
                if (m.group(3) != null) {
                    v += Integer.parseInt(m.group(3)) / 10000.0;
                }
                return v;
            }
        } catch (Throwable ignored) {
        }
        return 0.0;
    }

    public static boolean isSimplifiedChinese(Context context) {
        return "zh_cn".equals(getMinecraftLang(context));
    }

    public static Context setLanguage(Context context) {
        // ★★★ 社区版加固：本方法是在 attachBaseContext() 里被调用的 ——
        //   也就是 **Activity.onCreate 之前**。它一旦抛异常，Activity 根本创建不出来，
        //   现象就是「打开就闪退」，而且我给 onCreate 加的所有 try/catch 都包不到这里。
        //   本地化只是锦上添花：失败就退回原始 context（界面用系统语言），绝不能让启动挂掉。
        //   updateResources 理论上可能返回 null，那会让 super.attachBaseContext(null) 崩，
        //   所以这里一并判掉。
        try {
            Context localized = updateResources(context, context.getSharedPreferences("lang", 0).getInt("lang", 0));
            if (localized != null) {
                return localized;
            }
        } catch (Throwable t) {
            try {
                android.util.Log.e("QCLStartup", "语言本地化失败，退回系统语言", t);
            } catch (Throwable ignored) {
            }
        }
        return context;
    }

    public static void changeLanguage(Context context, int i) {
        SharedPreferences.Editor edit = context.getSharedPreferences("lang", 0).edit();
        edit.putInt("lang", i);
        edit.apply();
    }

    private static Context updateResources(Context context, int i) {
        Locale locale = getLocale(i);
        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        configuration.setLocales(new LocaleList(locale));
        return context.createConfigurationContext(configuration);
    }

    private static Locale getLocale(int i) {
        if (i == 1) {
            return Locale.ENGLISH;
        }
        if (i == 2) {
            return Locale.CHINA;
        }
        if (i == 3) {
            return Locale.TAIWAN;
        }
        return getSystemLocale();
    }

    public static Locale getSystemLocale() {
        // ★★★ 社区版加固：LocaleList.getDefault() 在极少数 ROM、或进程刚起、
        //   或系统 locale 数据异常时可能是**空列表**，此时 get(0) 直接抛
        //   IndexOutOfBoundsException。这条调用链在 attachBaseContext 上
        //   （见 setLanguage 注释），一抛就是无界面闪退。退回 Locale.getDefault() 即可。
        try {
            LocaleList list = LocaleList.getDefault();
            if (list != null && list.size() > 0) {
                Locale first = list.get(0);
                if (first != null) {
                    return first;
                }
            }
        } catch (Throwable ignored) {
        }
        return Locale.getDefault();
    }
}
