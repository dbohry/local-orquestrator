package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.IO.println;

public class ContainerStatsCollector {

    private static final int STATS_TIMEOUT_SECONDS = 5;

    private final DockerClientFactory clientFactory;
    private final ClusterConfig clusterConfig;

    public ContainerStatsCollector(DockerClientFactory clientFactory, ClusterConfig clusterConfig) {
        this.clientFactory = clientFactory;
        this.clusterConfig = clusterConfig;
    }

    public Double getCpuPercent(String containerId, String nodeId) {
        String host = clusterConfig.getHostForNode(nodeId);
        if (host == null) {
            println("No host configured for node: " + nodeId);
            return null;
        }

        DockerClient nodeClient = clientFactory.forHost(host);
        Statistics stats = fetchStats(nodeClient, containerId, host);
        if (stats == null) return null;

        return calculateCpuPercent(stats);
    }

    private Statistics fetchStats(DockerClient client, String containerId, String host) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Statistics> statsRef = new AtomicReference<>();

        client.statsCmd(containerId)
                .withNoStream(true)
                .exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onNext(Statistics stats) {
                        statsRef.set(stats);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        println("Stats error on " + host + ": " + throwable.getMessage());
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }

                    @Override
                    public void close() {
                    }

                    @Override
                    public void onStart(Closeable closeable) {
                    }
                });

        try {
            latch.await(STATS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            println("Stats timeout for container " + containerId);
            return null;
        }

        return statsRef.get();
    }

    private double calculateCpuPercent(Statistics stats) {
        long deltaCpu = stats.getCpuStats().getCpuUsage().getTotalUsage()
                - stats.getPreCpuStats().getCpuUsage().getTotalUsage();
        long deltaSystem = stats.getCpuStats().getSystemCpuUsage()
                - stats.getPreCpuStats().getSystemCpuUsage();

        if (deltaSystem == 0) return 0.0;

        var percpuUsage = stats.getCpuStats().getCpuUsage().getPercpuUsage();
        long numCpus = percpuUsage != null ? percpuUsage.size() : 1;
        return (deltaCpu / (double) deltaSystem) * numCpus * 100;
    }
}
