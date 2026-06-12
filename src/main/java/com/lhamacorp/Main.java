package com.lhamacorp;

import com.github.dockerjava.api.DockerClient;
import com.lhamacorp.orquestrator.AutoScaler;
import com.lhamacorp.orquestrator.ClusterConfig;
import com.lhamacorp.orquestrator.ContainerStatsCollector;
import com.lhamacorp.orquestrator.DockerClientFactory;

import static java.lang.IO.println;

public class Main {

    static void main() throws InterruptedException {
        ClusterConfig clusterConfig = new ClusterConfig();
        DockerClientFactory clientFactory = new DockerClientFactory();
        ContainerStatsCollector statsCollector = new ContainerStatsCollector(clientFactory, clusterConfig);
        DockerClient managerClient = clientFactory.forHost(clusterConfig.managerUri());
        AutoScaler autoScaler = new AutoScaler(managerClient, statsCollector);

        println("Orchestrator running. Manager: " + clusterConfig.managerUri());

        while (true) {
            autoScaler.evaluate();
            Thread.sleep(60000);
        }
    }
}
