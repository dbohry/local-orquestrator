package com.lhamacorp.orquestrator;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.util.HashMap;
import java.util.Map;

public class DockerClientFactory {

    private final Map<String, DockerClient> clientCache = new HashMap<>();

    public DockerClient forSocket(String socketPath) {
        return clientCache.computeIfAbsent(socketPath, this::createClient);
    }

    private DockerClient createClient(String socketPath) {
        String dockerHost = "unix://" + socketPath;
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
