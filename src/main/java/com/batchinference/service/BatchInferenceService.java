package com.batchinference.service;

import com.batchinference.config.BedrockProperties;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.CreateModelInvocationJobResponse;
import software.amazon.awssdk.services.bedrock.model.ModelInvocationType;
import software.amazon.awssdk.services.bedrock.model.S3InputFormat;

public class BatchInferenceService {

    private final BedrockClient client;

    public BatchInferenceService(BedrockClient client) {
        this.client = client;
    }

    public String submitJob(BedrockProperties config) {
        CreateModelInvocationJobResponse response = client.createModelInvocationJob(
                request -> request
                        .modelId(config.modelId())
                        .jobName(config.jobName())
                        .roleArn(config.roleArn())
                        .inputDataConfig(input -> input
                                .s3InputDataConfig(s3 -> s3
                                        .s3Uri(config.inputS3Uri())
                                        .s3InputFormat(S3InputFormat.JSONL)))
                        .outputDataConfig(output -> output
                                .s3OutputDataConfig(s3 -> s3
                                        .s3Uri(config.outputS3Uri())))
                        .modelInvocationType(ModelInvocationType.CONVERSE)
        );
        return response.jobArn();
    }
}
