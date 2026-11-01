package com.qcl.launcher.launcher.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServerProfileTest {

    @Test
    public void sanitizeNameRemovesPathAndSpaces() {
        assertEquals("main", ServerProfile.sanitizeName(""));
        assertEquals("main", ServerProfile.sanitizeName("   "));
        assertEquals("a_b_c", ServerProfile.sanitizeName("a/b:c"));
        assertEquals("我的服务器", ServerProfile.sanitizeName("我的服务器"));
    }

    @Test
    public void sanitizeNameCapsAt32Characters() {
        String input = "abcdefghijklmnopqrstuvwxyz0123456789";
        assertEquals(32, ServerProfile.sanitizeName(input).length());
    }

    @Test
    public void typeLabelsCoverAllServerTypes() {
        assertEquals("原版", ServerProfile.typeLabel("vanilla"));
        assertEquals("Paper", ServerProfile.typeLabel("paper"));
        assertEquals("Purpur", ServerProfile.typeLabel("purpur"));
        assertEquals("Fabric", ServerProfile.typeLabel("fabric"));
        assertEquals("Forge", ServerProfile.typeLabel("forge"));
        assertEquals("NeoForge", ServerProfile.typeLabel("neoforge"));
    }
}
