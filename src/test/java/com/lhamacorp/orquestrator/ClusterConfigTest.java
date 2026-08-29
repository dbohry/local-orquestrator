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

        ClusterConfig config = new ClusterConfig(client, new DockerClientFactory());

        assertEquals("tcp://192.168.1.10:2375", config.getHostForNode("node-1"));
        assertEquals("tcp://192.168.1.11:2375", config.getHostForNode("node-2"));
    }

    @Test
    void getHostForNode_returnsNullForUnknownNode() {
        DockerClient client = mockClientWithNodes(List.of(
                swarmNode("node-1", "192.168.1.10")
        ));

        ClusterConfig config = new ClusterConfig(client, new DockerClientFactory());

        assertNull(config.getHostForNode("unknown-node"));
    }

    @Test
    void refreshNodes_filtersNodesWithNullStatus() {
        SwarmNode goodNode = swarmNode("node-1", "10.0.0.1");
        SwarmNode nullStatusNode = mock(SwarmNode.class);
        when(nullStatusNode.getId()).thenReturn("node-2");
        when(nullStatusNode.getStatus()).thenReturn(null);

        DockerClient client = mockClientWithNodes(List.of(goodNode, nullStatusNode));
        ClusterConfig config = new ClusterConfig(client, new DockerClientFactory());

        assertEquals("tcp://10.0.0.1:2375", config.getHostForNode("node-1"));
        assertNull(config.getHostForNode("node-2"));
    }

    @Test
    void refreshNodes_filtersNodesWithNullAddress() {
        SwarmNode goodNode = swarmNode("node-1", "10.0.0.1");
        SwarmNode nullAddrNode = mock(SwarmNode.class);
        SwarmNodeStatus status = mock(SwarmNodeStatus.class);
        when(nullAddrNode.getId()).thenReturn("node-3");
        when(nullAddrNode.getStatus()).thenReturn(status);
        when(status.getAddress()).thenReturn(null);

        DockerClient client = mockClientWithNodes(List.of(goodNode, nullAddrNode));
        ClusterConfig config = new ClusterConfig(client, new DockerClientFactory());

        assertEquals("tcp://10.0.0.1:2375", config.getHostForNode("node-1"));
        assertNull(config.getHostForNode("node-3"));
    }

    @Test
    void refreshNodes_handlesNullListGracefully() {
        DockerClient client = mock(DockerClient.class);
        ListSwarmNodesCmd cmd = mock(ListSwarmNodesCmd.class);
        when(client.listSwarmNodesCmd()).thenReturn(cmd);
        when(cmd.exec()).thenReturn(null);

        ClusterConfig config = new ClusterConfig(client, new DockerClientFactory());

        assertNull(config.getHostForNode("any-node"));
    }

    @Test
    void refreshNodes_handlesExceptionGracefully() {
        DockerClient client = mock(DockerClient.class);
        ListSwarmNodesCmd cmd = mock(ListSwarmNodesCmd.class);
        when(client.listSwarmNodesCmd()).thenReturn(cmd);
        when(cmd.exec()).thenThrow(new RuntimeException("connection refused"));

        ClusterConfig config = new ClusterConfig(client, new DockerClientFactory());

        assertNull(config.getHostForNode("any-node"));
    }

    @Test
    void refreshNodes_removesStaleNodes() {
        DockerClient client = mock(DockerClient.class);
        ListSwarmNodesCmd cmd = mock(ListSwarmNodesCmd.class);
        when(client.listSwarmNodesCmd()).thenReturn(cmd);

        SwarmNode node1 = swarmNode("node-1", "10.0.0.1");
        SwarmNode node2 = swarmNode("node-2", "10.0.0.2");
        when(cmd.exec()).thenReturn(List.of(node1, node2));
        ClusterConfig config = new ClusterConfig(client, new DockerClientFactory());

        assertEquals("tcp://10.0.0.1:2375", config.getHostForNode("node-1"));
        assertEquals("tcp://10.0.0.2:2375", config.getHostForNode("node-2"));

        SwarmNode node2Again = swarmNode("node-2", "10.0.0.2");
        when(cmd.exec()).thenReturn(List.of(node2Again));
        config.refreshNodes();

        assertNull(config.getHostForNode("node-1"));
        assertEquals("tcp://10.0.0.2:2375", config.getHostForNode("node-2"));
    }

    @Test
    void getHostForNode_lazyRefreshFindsNewNode() {
        DockerClient client = mock(DockerClient.class);
        ListSwarmNodesCmd cmd = mock(ListSwarmNodesCmd.class);
        when(client.listSwarmNodesCmd()).thenReturn(cmd);

        SwarmNode node1 = swarmNode("node-1", "10.0.0.1");
        when(cmd.exec()).thenReturn(List.of(node1));
        ClusterConfig config = new ClusterConfig(client, new DockerClientFactory());

        SwarmNode node1Again = swarmNode("node-1", "10.0.0.1");
        SwarmNode node2 = swarmNode("node-2", "10.0.0.2");
        when(cmd.exec()).thenReturn(List.of(node1Again, node2));

        assertEquals("tcp://10.0.0.2:2375", config.getHostForNode("node-2"));
    }

    @Test
    void refreshNodes_duplicateNodeIds_lastOneWins() {
        SwarmNode first = swarmNode("node-1", "10.0.0.1");
        SwarmNode duplicate = swarmNode("node-1", "10.0.0.99");

        DockerClient client = mockClientWithNodes(List.of(first, duplicate));
        ClusterConfig config = new ClusterConfig(client, new DockerClientFactory());

        assertEquals("tcp://10.0.0.99:2375", config.getHostForNode("node-1"));
    }

    @Test
    void refreshNodes_updatesExistingNodeIp() {
        DockerClient client = mock(DockerClient.class);
        ListSwarmNodesCmd cmd = mock(ListSwarmNodesCmd.class);
        when(client.listSwarmNodesCmd()).thenReturn(cmd);

        SwarmNode node1 = swarmNode("node-1", "10.0.0.1");
        when(cmd.exec()).thenReturn(List.of(node1));
        ClusterConfig config = new ClusterConfig(client, new DockerClientFactory());
        assertEquals("tcp://10.0.0.1:2375", config.getHostForNode("node-1"));

        SwarmNode node1Updated = swarmNode("node-1", "10.0.0.99");
        when(cmd.exec()).thenReturn(List.of(node1Updated));
        config.refreshNodes();
        assertEquals("tcp://10.0.0.99:2375", config.getHostForNode("node-1"));
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
