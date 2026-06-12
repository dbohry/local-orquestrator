package com.lhamacorp;

import com.github.dockerjava.api.DockerClient;
import com.lhamacorp.orquestrator.AutoScaler;
import com.lhamacorp.orquestrator.ClusterConfig;
import com.lhamacorp.orquestrator.ContainerStatsCollector;
import com.lhamacorp.orquestrator.DockerClientFactory;
import com.lhamacorp.orquestrator.SshTunnelManager;

public class Main {

    static void main() throws InterruptedException {
        while(true) {
            ClusterConfig clusterConfig = new ClusterConfig();
            SshTunnelManager tunnelManager = new SshTunnelManager();

            tunnelManager.startTunnels(clusterConfig);

            try {
                DockerClientFactory clientFactory = new DockerClientFactory();
                ContainerStatsCollector statsCollector = new ContainerStatsCollector(clientFactory, clusterConfig);
                DockerClient managerClient = clientFactory.forSocket(clusterConfig.managerSocket());
                AutoScaler autoScaler = new AutoScaler(managerClient, statsCollector);

                autoScaler.evaluate();
            } finally {
                tunnelManager.stopTunnels();
            }

            Thread.sleep(60000);
        }
    }
}
