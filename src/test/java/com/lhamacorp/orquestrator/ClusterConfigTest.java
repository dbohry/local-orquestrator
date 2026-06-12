package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListSwarmNodesCmd;
import com.github.dockerjava.api.model.SwarmNode;
import com.github.dockerjava.api.model.SwarmNodeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClusterConfigTest {

    @Test
    void getHostForNode_returnsUriForKnownNode() {
        DockerClient client = mockClientWithNodes(List.of(
                swarmNode("node-1", "192.168.1.10"),
                swarmNode("node-2", "192.168.1.11")
        ));

        ClusterConfig config = new ClusterConfig(client);

        assertEquals("tcp://192.168.1.10:2375", config.getHostForNode("node-1"));
        assertEquals("tcp://192.168.1.11:2375", config.getHostForNode("node-2"));
    }

    @Test
    void getHostForNode_returnsNullForUnknownNode() {
        DockerClient client = mockClientWithNodes(List.of(
                swarmNode("node-1", "192.168.1.10")
        ));

        ClusterConfig config = new ClusterConfig(client);

        assertNull(config.getHostForNode("unknown-node"));
    }

    private DockerClient mockClientWithNodes(List<SwarmNode> nodes) {
        DockerClient client = mock(DockerClient.class);
        ListSwarmNodesCmd cmd = mock(ListSwarmNodesCmd.class);
        when(client.listSwarmNodesCmd()).thenReturn(cmd);
        when(cmd.exec()).thenReturn(nodes);
        return client;
    }

    private SwarmNode swarmNode(String id, String ip) {
        SwarmNode node = mock(SwarmNode.class);
        SwarmNodeStatus status = mock(SwarmNodeStatus.class);
        when(node.getId()).thenReturn(id);
        when(node.getStatus()).thenReturn(status);
        when(status.getAddress()).thenReturn(ip);
        return node;
    }
}
