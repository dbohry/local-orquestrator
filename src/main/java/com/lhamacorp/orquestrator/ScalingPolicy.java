package com.lhamacorp.orquestrator;

public record ScalingPolicy(int min, int max, double scaleUpCpu, double scaleDownCpu) {

    public long decide(long currentReplicas, double avgCpu) {
        if (avgCpu > scaleUpCpu && currentReplicas < max) {
            return Math.min(currentReplicas + 1, max);
        } else if (avgCpu < scaleDownCpu && currentReplicas > min) {
            return Math.max(currentReplicas - 1, min);
        }
        return currentReplicas;
    }

    public static ScalingPolicy fromLabels(java.util.Map<String, String> labels) {
        int min = Integer.parseInt(labels.getOrDefault("min-replicas", "1"));
        int max = Integer.parseInt(labels.getOrDefault("max-replicas", "5"));
        double scaleUpCpu = Double.parseDouble(labels.getOrDefault("scale-up-cpu", "70"));
        double scaleDownCpu = Double.parseDouble(labels.getOrDefault("scale-down-cpu", "30"));
        return new ScalingPolicy(min, max, scaleUpCpu, scaleDownCpu);
    }
}
