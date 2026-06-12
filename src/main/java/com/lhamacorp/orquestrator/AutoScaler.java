package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Service;
import com.github.dockerjava.api.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.dockerjava.api.model.TaskState.RUNNING;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

public class AutoScaler {

    private static final Logger log = LoggerFactory.getLogger(AutoScaler.class);

    private final DockerClient managerClient;
    private final ContainerStatsCollector statsCollector;

    public AutoScaler(DockerClient managerClient, ContainerStatsCollector statsCollector) {
        this.managerClient = managerClient;
        this.statsCollector = statsCollector;
    }

    public void evaluate() {
        List<Service> services = managerClient.listServicesCmd().exec();
        services.forEach(this::evaluateService);
    }

    private void evaluateService(Service service) {
        ofNullable(service.getSpec()).ifPresent(spec -> {
            Map<String, String> labels = new HashMap<>(ofNullable(spec.getLabels()).orElseGet(HashMap::new));

            if (!"true".equals(labels.get("autoscaling"))) return;

            ScalingPolicy policy = ScalingPolicy.fromLabels(labels);
            long currentReplicas = spec.getMode().getReplicated().getReplicas();
            double avgCpu = collectServiceCpu(service);
            if (Double.isNaN(avgCpu)) return;

            log.info("{} - Load: ~{}% - {}/{} ({})", spec.getName(), String.format("%.2f", avgCpu), policy.min(), policy.max(), currentReplicas);

            long newReplicas = policy.decide(currentReplicas, avgCpu);
            if (newReplicas != currentReplicas) {
                scale(service, currentReplicas, newReplicas);
            }
        });
    }

    private double collectServiceCpu(Service service) {
        List<Task> tasks = managerClient.listTasksCmd()
                .withServiceFilter(service.getId())
                .withStateFilter(RUNNING)
                .exec();

        try (ExecutorService executor = newVirtualThreadPerTaskExecutor()) {
            List<Future<Double>> futures = tasks.stream()
                    .filter(task -> {
                        var cs = task.getStatus().getContainerStatus();
                        return cs != null && cs.getContainerID() != null && !cs.getContainerID().isEmpty() && task.getNodeId() != null;
                    })
                    .map(task -> executor.submit(() -> statsCollector.getCpuPercent(
                            task.getStatus().getContainerStatus().getContainerID(),
                            task.getNodeId()
                    )))
                    .toList();

            double[] cpuValues = futures.stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .toArray();

            if (cpuValues.length == 0) return Double.NaN;
            return java.util.Arrays.stream(cpuValues).average().orElse(Double.NaN);
        }
    }

    private void scale(Service service, long from, long to) {
        var updatedSpec = service.getSpec();
        updatedSpec.getMode().getReplicated().withReplicas((int) to);
        managerClient.updateServiceCmd(service.getId(), updatedSpec)
                .withVersion(service.getVersion().getIndex())
                .exec();
        log.info("{} scale {}: {} -> {}", service.getSpec().getName(), to > from ? "UP" : "DOWN", from, to);
    }
}
