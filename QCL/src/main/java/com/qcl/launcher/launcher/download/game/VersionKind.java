package com.qcl.launcher.launcher.download.game;

import android.content.Context;

import java.util.regex.Pattern;

/**
 * ★★★ v3.0.5：判定一个版本到底属于哪一类。
 *
 * <h3>为什么不能只信清单里的 {@code type} 字段</h3>
 * Mojang 的官方清单自己就标错了五个正式版：{@code 1.3}、{@code 1.4}、{@code 1.4.1}、
 * {@code 1.4.3}、{@code 1.5} 的 {@code type} 全部写着 {@code snapshot}（实测 2026-10-25 抓的
 * {@code version_manifest_v2.json}）。后果很直接：<b>用户勾「正式版」时这五个版本根本不出现</b>，
 * 只在「测试版」里能找到 —— 这正是「很多版本缺失」最容易踩到的一处。
 *
 * <p>判据不能改成「看 type 是不是 release」，因为那就等于相信一个已经被证伪的字段。
 * 真正稳定的特征是<b>版本号形状</b>：正式版的 id 一定长成 {@code 1.5} / {@code 1.12.2} /
 * {@code 26.1.1} 这样纯数字加点的形式；快照一定带 {@code w} 或 {@code -pre} 之类的后缀；
 * 远古版一定以字母开头（{@code b1.7.3}、{@code a1.2.6}、{@code c0.30_01c}）。
 * 所以这里用形状兜住清单的错误，而不是覆盖它。
 *
 * <p>★ 只做「release 认不认」这一件事，不重写其它类型 —— 快照 / 远古 / 归档的 type 字段
 * 没有已知错误，改动它们只有风险没有收益。
 */
public final class VersionKind {

    /** 正式版的版本号形状：纯数字 + 点，至少两段（1.5 / 1.12.2 / 26.1.1）。 */
    private static final Pattern RELEASE_ID = Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?$");

    private VersionKind() {
    }

    /** id 的形状像不像正式版（不看 type 字段）。 */
    public static boolean looksLikeRelease(String id) {
        return id != null && RELEASE_ID.matcher(id).matches();
    }

    /**
     * 这个版本该不该出现在「正式版」分类里。
     *
     * <p>清单说是 release，或者 id 形状是正式版 —— 两者满足其一即可。
     * 后者专门用来兜住那五个被标成 snapshot 的正式版。
     */
    public static boolean isRelease(Context context, VersionManifest.Version version) {
        if (version == null) {
            return false;
        }
        // 愚人节版优先：它们不参与正式/测试/远古三个分类
        if (AprilFools.is(context, version.id)) {
            return false;
        }
        if ("release".equals(version.type)) {
            return true;
        }
        return looksLikeRelease(version.id);
    }

    /** 该不该出现在「测试版（快照）」分类里。 */
    public static boolean isSnapshot(Context context, VersionManifest.Version version) {
        if (version == null) {
            return false;
        }
        if (AprilFools.is(context, version.id)) {
            return false;
        }
        if (!"snapshot".equals(version.type)) {
            return false;
        }
        // 被标错的那五个正式版不能同时出现在测试版里
        return !looksLikeRelease(version.id);
    }
}
