package com.qcl.launcher.launcher.server;

import com.qcl.launcher.launcher.download.neoforge.NeoForgeVersions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ServerInstallerTest {

    @Test
    public void requiredJavaFollowsMinecraftEra() {
        assertEquals(8, ServerInstaller.requiredJavaForVersion("1.16.5"));
        assertEquals(17, ServerInstaller.requiredJavaForVersion("1.18.2"));
        assertEquals(17, ServerInstaller.requiredJavaForVersion("1.20.4"));
        assertEquals(21, ServerInstaller.requiredJavaForVersion("1.20.5"));
        assertEquals(21, ServerInstaller.requiredJavaForVersion("1.21.1"));
        assertEquals(25, ServerInstaller.requiredJavaForVersion("26.3"));
    }

    @Test
    public void neoForgeVersionMapsToMinecraftVersion() {
        assertEquals("1.21.1", NeoForgeVersions.mcVersionOf("21.1.72"));
        assertEquals("1.21", NeoForgeVersions.mcVersionOf("21.0.98-beta"));
        assertEquals("1.20.2", NeoForgeVersions.mcVersionOf("20.2.93"));
        assertNull(NeoForgeVersions.mcVersionOf("47.1.105"));
        assertNull(NeoForgeVersions.mcVersionOf(null));
    }
}
