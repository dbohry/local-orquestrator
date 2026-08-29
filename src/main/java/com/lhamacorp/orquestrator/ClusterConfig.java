package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.SwarmNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class ClusterConfig {

    private static final Logger log = LoggerFactory.getLogger(ClusterConfig.class);
    private static final int DEFAULT_DOCKER_PORT = 2375;

    private final DockerClient managerClient;
    private final DockerClientFactory clientFactory;
    private final int dockerPort;
    private final Map<String, String> nodeIps = new ConcurrentHashMap<>();

    public ClusterConfig(DockerClient managerClient, DockerClientFactory clientFactory) {
        this.managerClient = managerClient;
        this.clientFactory = clientFactory;
        this.dockerPort = Integer.parseInt(System.getenv().getOrDefault("DOCKER_API_PORT", String.valueOf(DEFAULT_DOCKER_PORT)));
        refreshNodes();
    }

    public synchronized void refreshNodes() {
        try {
            List<SwarmNode> nodes = managerClient.listSwarmNodesCmd().exec();
            if (nodes == null) return;

            Map<String, String> freshNodes = nodes.stream()
                    .filter(node -> node.getStatus() != null)
                    .filter(node -> node.getStatus().getAddress() != null)
                    .collect(Collectors.toMap(
                            SwarmNode::getId,
                            node -> node.getStatus().getAddress(),
                            (_, replacement) -> replacement
                    ));

            nodeIps.forEach((nodeId, oldIp) -> {
                String newIp = freshNodes.get(nodeId);
                if (newIp == null || !newIp.equals(oldIp)) {
                    clientFactory.close(dockerUri(oldIp));
                }
            });

            nodeIps.keySet().retainAll(freshNodes.keySet());
            nodeIps.putAll(freshNodes);

            log.debug("Discovered {} nodes from Swarm API", nodeIps.size());
        } catch (Exception e) {
            log.error("Failed to refresh nodes", e);
        }
    }

    public String getHostForNode(String nodeId) {
        return ofNullable(nodeIps.get(nodeId))
                .or(() -> {
                    refreshNodes();
                    return ofNullable(nodeIps.get(nodeId));
                })
                .map(this::dockerUri)
                .orElse(null);
    }

    private String dockerUri(String ip) {
        return "tcp://" + ip + ":" + dockerPort;
    }
}