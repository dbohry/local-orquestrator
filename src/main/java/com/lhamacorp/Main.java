package com.lhamacorp;

import com.github.dockerjava.api.DockerClient;
import com.lhamacorp.orquestrator.AutoScaler;
import com.lhamacorp.orquestrator.ClusterConfig;
import com.lhamacorp.orquestrator.ContainerStatsCollector;
import com.lhamacorp.orquestrator.DockerClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String MANAGER_HOST = System.getenv().getOrDefault("MANAGER_HOST", "tcp://localhost:2375");

    static void main() throws InterruptedException {
        DockerClientFactory clientFactory = new DockerClientFactory();
        DockerClient managerClient = clientFactory.forHost(MANAGER_HOST);
        ClusterConfig clusterConfig = new ClusterConfig(managerClient);
        ContainerStatsCollector statsCollector = new ContainerStatsCollector(clientFactory, clusterConfig);
        AutoScaler autoScaler = new AutoScaler(managerClient, statsCollector);

        log.debug("Orchestrator running. Manager: {}", MANAGER_HOST);

        while (true) {
            autoScaler.evaluate();
            Thread.sleep(60000);
        }
    }
}
