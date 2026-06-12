package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.SwarmNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClusterConfig {

    private static final Logger log = LoggerFactory.getLogger(ClusterConfig.class);
    private static final int DEFAULT_DOCKER_PORT = 2375;

    private final int dockerPort;
    private final Map<String, String> nodeIps = new HashMap<>();

    public ClusterConfig(DockerClient managerClient) {
        this.dockerPort = Integer.parseInt(System.getenv().getOrDefault("DOCKER_API_PORT", String.valueOf(DEFAULT_DOCKER_PORT)));
        loadNodes(managerClient);
    }

    private void loadNodes(DockerClient managerClient) {
        List<SwarmNode> nodes = managerClient.listSwarmNodesCmd().exec();
        for (SwarmNode node : nodes) {
            String id = node.getId();
            String ip = node.getStatus().getAddress();
            nodeIps.put(id, ip);
        }
        log.debug("Discovered {} nodes from Swarm API", nodeIps.size());
    }

    public String getHostForNode(String nodeId) {
        String ip = nodeIps.get(nodeId);
        return ip != null ? dockerUri(ip) : null;
    }

    private String dockerUri(String ip) {
        return "tcp://" + ip + ":" + dockerPort;
    }
}
