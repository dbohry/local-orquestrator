package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DockerClientFactory {

    private static final Logger log = LoggerFactory.getLogger(DockerClientFactory.class);

    private final Map<String, DockerClient> clientCache = new ConcurrentHashMap<>();

    public DockerClient forHost(String dockerHost) {
        return clientCache.computeIfAbsent(dockerHost, this::createClient);
    }

    public void close(String dockerHost) {
        DockerClient client = clientCache.remove(dockerHost);
        if (client == null) return;

        try {
            client.close();
        } catch (IOException e) {
            log.warn("Failed to close Docker client for {}: {}", dockerHost, e.getMessage());
        }
    }

    private DockerClient createClient(String dockerHost) {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        var httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(URI.create(dockerHost))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
