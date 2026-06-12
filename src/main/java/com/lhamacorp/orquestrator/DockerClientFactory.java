package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class DockerClientFactory {

    private final Map<String, DockerClient> clientCache = new HashMap<>();

    public DockerClient forHost(String dockerHost) {
        return clientCache.computeIfAbsent(dockerHost, this::createClient);
    }

    private DockerClient createClient(String dockerHost) {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create(dockerHost))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
