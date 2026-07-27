package com.batchinference.service;

import com.batchinference.config.BedrockClientFactory;
import com.batchinference.config.BedrockProperties;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.S3InputFormat;

public class BatchInferenceWithMessagesAPI {

    private final BedrockClient client;
    private final BedrockProperties config;

    public BatchInferenceWithMessagesAPI(BedrockProperties config) {
        this.config = config;
        this.client = new BedrockClientFactory(config).create();
    }

    public String submitJob() {
        return client.createModelInvocationJob(request -> request
                .modelId(config.modelId())
                .jobName(config.jobName())
                .roleArn(config.roleArn())
                .inputDataConfig(input -> input
                        .s3InputDataConfig(s3 -> s3
                                .s3Uri("s3://bedrock-surface-batch-inference-bucket/messages-input/")
                                .s3InputFormat(S3InputFormat.JSONL)))
                .outputDataConfig(output -> output
                        .s3OutputDataConfig(s3 -> s3
                                .s3Uri("s3://bedrock-surface-batch-inference-output-bucket/")))
        ).jobArn();
    }
}
