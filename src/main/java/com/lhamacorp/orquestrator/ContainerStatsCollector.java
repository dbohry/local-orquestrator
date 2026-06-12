package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ContainerStatsCollector {

    private static final Logger log = LoggerFactory.getLogger(ContainerStatsCollector.class);
    private static final int STATS_TIMEOUT_SECONDS = 2;

    private final DockerClientFactory clientFactory;
    private final ClusterConfig clusterConfig;

    public ContainerStatsCollector(DockerClientFactory clientFactory, ClusterConfig clusterConfig) {
        this.clientFactory = clientFactory;
        this.clusterConfig = clusterConfig;
    }

    public Double getCpuPercent(String containerId, String nodeId) {
        String host = clusterConfig.getHostForNode(nodeId);
        if (host == null) {
            log.warn("No host configured for node: {}", nodeId);
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
                        log.error("Stats error on {}: {}", host, throwable.getMessage());
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
            log.warn("Stats timeout for container {}", containerId);
            Thread.currentThread().interrupt();
            return null;
        }

        return statsRef.get();
    }

    private double calculateCpuPercent(Statistics stats) {
        if (stats == null || stats.getCpuStats() == null || stats.getPreCpuStats() == null) {
            return 0.0;
        }

        var cpuStats = stats.getCpuStats();
        var preCpuStats = stats.getPreCpuStats();

        long currentSystem = getValueOrZero(cpuStats.getSystemCpuUsage());
        long preSystem = getValueOrZero(preCpuStats.getSystemCpuUsage());
        long deltaSystem = currentSystem - preSystem;

        if (deltaSystem <= 0) {
            return 0.0;
        }

        var cpuUsage = cpuStats.getCpuUsage();
        var preCpuUsage = preCpuStats.getCpuUsage();

        if (cpuUsage == null || preCpuUsage == null) {
            return 0.0;
        }

        long currentTotal = getValueOrZero(cpuUsage.getTotalUsage());
        long preTotal = getValueOrZero(preCpuUsage.getTotalUsage());
        long deltaCpu = currentTotal - preTotal;

        if (deltaCpu <= 0) {
            return 0.0;
        }

        var percpuUsage = cpuUsage.getPercpuUsage();
        long numCpus = (percpuUsage != null && !percpuUsage.isEmpty())
                ? percpuUsage.size()
                : Math.max(1, cpuStats.getOnlineCpus());

        return ((double) deltaCpu / deltaSystem) * numCpus * 100.0;
    }

    private long getValueOrZero(Long value) {
        return value != null ? value : 0L;
    }
}
