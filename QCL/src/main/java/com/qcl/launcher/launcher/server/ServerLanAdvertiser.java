package com.qcl.launcher.launcher.server;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public final class ServerLanAdvertiser {

    private static final String TAG = "QCLServerLan";
    private static final String GROUP = "224.0.2.60";
    private static final int PORT = 4445;

    private static volatile boolean running;
    private static volatile int serverPort;
    private static Thread thread;
    private static MulticastSocket socket;
    private static WifiManager.MulticastLock multicastLock;

    private ServerLanAdvertiser() {
    }

    public static synchronized void start(Context context, int port) {
        stop();
        serverPort = port;
        running = true;
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("QCL:ServerLan");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (Throwable t) {
            Log.w(TAG, "MulticastLock failed", t);
        }
        thread = new Thread(() -> {
            MulticastSocket local = null;
            try {
                local = new MulticastSocket(null);
                local.setReuseAddress(true);
                local.bind(new InetSocketAddress(0));
                local.setLoopbackMode(false);
                local.setTimeToLive(4);
                socket = local;
                InetAddress group = InetAddress.getByName(GROUP);
                while (running) {
                    String message = "[MOTD]QCL Mobile Server[/MOTD][AD]"
                            + serverPort + "[/AD]";
                    byte[] data = message.getBytes(StandardCharsets.UTF_8);
                    local.send(new DatagramPacket(data, data.length, group, PORT));
                    try {
                        Thread.sleep(1500L);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            } catch (Throwable t) {
                if (running) {
                    Log.w(TAG, "LAN advertise failed", t);
                }
            } finally {
                if (local != null) {
                    try {
                        local.close();
                    } catch (Throwable ignored) {
                    }
                }
                socket = null;
            }
        }, "qcl-server-lan-advertise");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Throwable ignored) {
        }
        socket = null;
        try {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (Throwable ignored) {
        }
        multicastLock = null;
        thread = null;
    }

    public static boolean isRunning() {
        return running;
    }

    public static String localAddress() {
        try {
            java.util.ArrayList<NetworkInterface> interfaces =
                    Collections.list(NetworkInterface.getNetworkInterfaces());
            interfaces.sort((a, b) -> rank(a.getName()) - rank(b.getName()));
            for (NetworkInterface network : interfaces) {
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "未知";
    }

    private static int rank(String name) {
        String value = name == null ? "" : name.toLowerCase(java.util.Locale.US);
        if (value.startsWith("wlan") || value.startsWith("ap")) {
            return 0;
        }
        if (value.startsWith("eth")) {
            return 1;
        }
        return 2;
    }
}
