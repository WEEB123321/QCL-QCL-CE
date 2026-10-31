package net.kdt.pojavlaunch.utils;

public final class ServerStdio {

    static {
        System.loadLibrary("istdio");
    }

    private ServerStdio() {
    }

    public static native boolean redirectStdout(String path);

    public static native boolean redirectStdoutAppend(String path);

    public static native boolean createStdinPipe();

    public static native boolean writeStdin(byte[] data);

    public static native void closeStdinPipe();
}
