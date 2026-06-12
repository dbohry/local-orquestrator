package com.lhamacorp.orquestrator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.IO.println;

public class SshTunnelManager {

    private static final String SSH_USER = "pi";
    private static final String REMOTE_SOCKET = "/var/run/docker.sock";

    private final List<Process> tunnels = new ArrayList<>();

    public void startTunnels(ClusterConfig config) {
        config.getAllNodes().forEach(node -> {
            String localSocket = node.socket();
            String ip = node.ip();

            new File(localSocket).delete();

            try {
                Process tunnel = new ProcessBuilder(
                        "ssh", "-nNT",
                        "-o", "StrictHostKeyChecking=no",
                        "-o", "ExitOnForwardFailure=yes",
                        "-L", localSocket + ":" + REMOTE_SOCKET,
                        SSH_USER + "@" + ip
                ).redirectErrorStream(true).start();

                tunnels.add(tunnel);
//                println("SSH tunnel started: " + ip + " -> " + localSocket);
            } catch (IOException e) {
                println("Failed to start tunnel to " + ip + ": " + e.getMessage());
            }
        });

        waitForSockets(config);
    }

    private void waitForSockets(ClusterConfig config) {
        config.getAllNodes().forEach(node -> {
            File sock = new File(node.socket());
            int retries = 20;
            while (!sock.exists() && retries-- > 0) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!sock.exists()) {
                println("Warning: socket not ready: " + node.socket());
            }
        });
    }

    public void stopTunnels() {
        tunnels.forEach(Process::destroy);
        tunnels.clear();
//        println("All SSH tunnels stopped");
    }
}
