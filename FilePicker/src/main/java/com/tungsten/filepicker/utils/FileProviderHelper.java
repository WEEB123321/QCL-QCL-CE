package com.tungsten.filepicker.utils;

import android.content.Context;

/**
 * FileProvider authority 与安装包 applicationId 绑定。
 * 清单里声明为 ${applicationId}.filepicker.provider；正式版与 Beta 并存安装时
 * 不会因 authority 相同而互相抢占。
 */
public final class FileProviderHelper {

    private FileProviderHelper() {
    }

    public static String getAuthority(Context context) {
        return context.getPackageName() + ".filepicker.provider";
    }
}
