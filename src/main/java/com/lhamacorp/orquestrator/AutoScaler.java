package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Service;
import com.github.dockerjava.api.model.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.dockerjava.api.model.TaskState.RUNNING;
import static java.lang.IO.println;
import static java.util.Optional.ofNullable;

public class AutoScaler {

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

            println("Service: " + spec.getName() + " - Average CPU: " + avgCpu + "%" + " - Range: " + policy.min() + " to " + policy.max() + " - Current: " + currentReplicas);

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

        List<Double> cpuValues = new ArrayList<>();

        for (Task task : tasks) {
            var containerStatus = task.getStatus().getContainerStatus();
            if (containerStatus == null) continue;

            String containerId = containerStatus.getContainerID();
            String nodeId = task.getNodeId();
            if (containerId == null || containerId.isEmpty() || nodeId == null) continue;

            Double cpu = statsCollector.getCpuPercent(containerId, nodeId);
            if (cpu != null) {
                cpuValues.add(cpu);
            }
        }

        if (cpuValues.isEmpty()) return Double.NaN;
        return cpuValues.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    private void scale(Service service, long from, long to) {
        println("Scale " + (to > from ? "UP" : "DOWN") + ": " + from + " -> " + to);
        var updatedSpec = service.getSpec();
        updatedSpec.getMode().getReplicated().withReplicas((int) to);
        managerClient.updateServiceCmd(service.getId(), updatedSpec)
                .withVersion(service.getVersion().getIndex())
                .exec();
        println("Updated service " + updatedSpec.getName() + " to " + to + " replicas");
    }
}
