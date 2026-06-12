package com.lhamacorp;

import com.github.dockerjava.api.DockerClient;
import com.lhamacorp.orquestrator.AutoScaler;
import com.lhamacorp.orquestrator.ClusterConfig;
import com.lhamacorp.orquestrator.ContainerStatsCollector;
import com.lhamacorp.orquestrator.DockerClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.ofVirtual;
import static java.util.concurrent.Executors.newScheduledThreadPool;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String MANAGER_HOST = System.getenv().getOrDefault("MANAGER_HOST", "tcp://localhost:2375");
    private static final String FREQUENCY = System.getenv().getOrDefault("FREQUENCY", "60000");

    static void main() {
        DockerClientFactory clientFactory = new DockerClientFactory();
        DockerClient managerClient = clientFactory.forHost(MANAGER_HOST);
        ClusterConfig clusterConfig = new ClusterConfig(managerClient);
        ContainerStatsCollector statsCollector = new ContainerStatsCollector(clientFactory, clusterConfig);
        AutoScaler autoScaler = new AutoScaler(managerClient, statsCollector);

        log.debug("Orchestrator running. Manager: {}", MANAGER_HOST);

        long periodMillis = Long.parseLong(FREQUENCY);
        run(autoScaler, periodMillis);
    }

    static void run(AutoScaler autoScaler, long frequency) {
        try (ScheduledExecutorService executor = newScheduledThreadPool(1, ofVirtual().factory())) {
            executor.scheduleAtFixedRate(
                    autoScaler::evaluate,
                    0,
                    frequency,
                    TimeUnit.MILLISECONDS
            );

            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
