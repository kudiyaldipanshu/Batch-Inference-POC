package com.batchinference.config;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;

public class BedrockClientFactory {

    private final BedrockProperties properties;

    public BedrockClientFactory(BedrockProperties properties) {
        this.properties = properties;
    }

    public BedrockClient create() {
        var builder = BedrockClient.builder()
                .region(Region.of(properties.region()));

        if (properties.profileName() != null && !properties.profileName().isBlank()) {
            builder.credentialsProvider(
                    ProfileCredentialsProvider.create(properties.profileName()));
        }

        return builder.build();
    }
}
