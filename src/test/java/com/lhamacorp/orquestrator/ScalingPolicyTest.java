package com.lhamacorp.orquestrator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScalingPolicyTest {

    @Test
    void scaleUp_whenCpuAboveThreshold() {
        var policy = new ScalingPolicy(1, 5, 70, 30);
        assertEquals(3, policy.decide(2, 75));
    }

    @Test
    void scaleDown_whenCpuBelowThreshold() {
        var policy = new ScalingPolicy(1, 5, 70, 30);
        assertEquals(2, policy.decide(3, 20));
    }

    @Test
    void noChange_whenCpuWithinRange() {
        var policy = new ScalingPolicy(1, 5, 70, 30);
        assertEquals(2, policy.decide(2, 50));
    }

    @Test
    void respectsMaxReplicas() {
        var policy = new ScalingPolicy(1, 3, 70, 30);
        assertEquals(3, policy.decide(3, 90));
    }

    @Test
    void respectsMinReplicas() {
        var policy = new ScalingPolicy(2, 5, 70, 30);
        assertEquals(2, policy.decide(2, 10));
    }

    @Test
    void scaleUpToMin_whenBelowMinReplicas() {
        var policy = new ScalingPolicy(2, 5, 70, 30);
        assertEquals(2, policy.decide(1, 10));
    }

    @Test
    void fromLabels_usesDefaults() {
        var policy = ScalingPolicy.fromLabels(Map.of());
        assertEquals(1, policy.min());
        assertEquals(5, policy.max());
        assertEquals(70, policy.scaleUpCpu());
        assertEquals(30, policy.scaleDownCpu());
    }

    @Test
    void fromLabels_parsesCustomValues() {
        var labels = Map.of(
                "min-replicas", "2",
                "max-replicas", "10",
                "scale-up-cpu", "80",
                "scale-down-cpu", "20"
        );
        var policy = ScalingPolicy.fromLabels(labels);
        assertEquals(2, policy.min());
        assertEquals(10, policy.max());
        assertEquals(80, policy.scaleUpCpu());
        assertEquals(20, policy.scaleDownCpu());
    }
}
