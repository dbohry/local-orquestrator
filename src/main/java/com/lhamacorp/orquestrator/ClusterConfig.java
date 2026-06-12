package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.SwarmNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClusterConfig {

    private static final Logger log = LoggerFactory.getLogger(ClusterConfig.class);
    private static final int DEFAULT_DOCKER_PORT = 2375;

    private final DockerClient managerClient;
    private final int dockerPort;
    private final Map<String, String> nodeIps = new ConcurrentHashMap<>();

    public ClusterConfig(DockerClient managerClient) {
        this.managerClient = managerClient;
        this.dockerPort = Integer.parseInt(System.getenv().getOrDefault("DOCKER_API_PORT", String.valueOf(DEFAULT_DOCKER_PORT)));
        refreshNodes();
    }

    public void refreshNodes() {
        List<SwarmNode> nodes = managerClient.listSwarmNodesCmd().exec();
        nodeIps.clear();
        for (SwarmNode node : nodes) {
            String id = node.getId();
            String ip = node.getStatus().getAddress();
            nodeIps.put(id, ip);
        }
        log.debug("Discovered {} nodes from Swarm API", nodeIps.size());
    }

    public String getHostForNode(String nodeId) {
        String ip = nodeIps.get(nodeId);
        if (ip == null) {
            refreshNodes();
            ip = nodeIps.get(nodeId);
        }
        return ip != null ? dockerUri(ip) : null;
    }

    private String dockerUri(String ip) {
        return "tcp://" + ip + ":" + dockerPort;
    }
}
