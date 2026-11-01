package com.qcl.launcher.launcher.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ServerRuntimeTest {

    @Test
    public void runtimeNamesMatchBundledAssets() {
        assertEquals("default", ServerRuntime.runtimeName(8));
        assertEquals("JRE17", ServerRuntime.runtimeName(17));
        assertEquals("JRE21", ServerRuntime.runtimeName(21));
        assertEquals("JRE25", ServerRuntime.runtimeName(25));
        assertEquals("JRE25", ServerRuntime.runtimeName(26));
        assertEquals("default", ServerRuntime.runtimeName(7));
    }

    @Test
    public void runtimeAssetsMatchBundledArchives() {
        assertEquals("app_runtime/java/jre8", ServerRuntime.runtimeAsset(8));
        assertEquals("app_runtime/java/jre17", ServerRuntime.runtimeAsset(17));
        assertEquals("app_runtime/java/jre21", ServerRuntime.runtimeAsset(21));
        assertEquals("app_runtime/java/jre25", ServerRuntime.runtimeAsset(25));
    }
}
