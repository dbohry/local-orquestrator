package com.lhamacorp.orquestrator;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.IO.println;

public class ClusterConfig {

    private final Map<String, NodeInfo> nodes = new HashMap<>();
    private String managerSocket;

    public ClusterConfig() {
        loadFromClasspath();
    }

    private void loadFromClasspath() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            if (input == null) {
                println("application.yml not found");
                return;
            }
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(input);
            Map<String, Object> cluster = (Map<String, Object>) config.get("cluster");
            String managerId = (String) cluster.get("manager");
            List<Map<String, Object>> nodeList = (List<Map<String, Object>>) cluster.get("nodes");

            for (Map<String, Object> node : nodeList) {
                String id = (String) node.get("id");
                String ip = (String) node.get("ip");
                String socket = (String) node.get("socket");
                nodes.put(id, new NodeInfo(id, ip, socket));
            }

            if (managerId != null && nodes.containsKey(managerId)) {
                managerSocket = nodes.get(managerId).socket();
            }

            println("Loaded " + nodes.size() + " nodes from config (manager: " + managerId + ")");
        } catch (Exception e) {
            println("Error loading node config: " + e.getMessage());
        }
    }

    public String getSocketForNode(String nodeId) {
        NodeInfo info = nodes.get(nodeId);
        return info != null ? info.socket() : null;
    }

    public String managerSocket() {
        return managerSocket;
    }

    public Collection<NodeInfo> getAllNodes() {
        return nodes.values();
    }

    public record NodeInfo(String id, String ip, String socket) {
    }
}
