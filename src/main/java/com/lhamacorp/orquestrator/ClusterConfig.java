package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.SwarmNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.IO.println;

public class ClusterConfig {

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
        println("Discovered " + nodeIps.size() + " nodes from Swarm API");
    }

    public String getHostForNode(String nodeId) {
        String ip = nodeIps.get(nodeId);
        return ip != null ? dockerUri(ip) : null;
    }

    public void refresh(DockerClient managerClient) {
        nodeIps.clear();
        loadNodes(managerClient);
    }

    private String dockerUri(String ip) {
        return "tcp://" + ip + ":" + dockerPort;
    }
}
