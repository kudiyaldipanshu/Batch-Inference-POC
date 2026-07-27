package com.batchinference.config;

import java.util.UUID;

public class BedrockProperties {

    private final String modelId;
    private final String roleArn;
    private final String inputS3Uri;
    private final String outputS3Uri;
    private final String jobName;
    private final String region;
    private final String profileName;

    public BedrockProperties(String modelId, String roleArn, String inputS3Uri,
                             String outputS3Uri, String jobName, String region,
                             String profileName) {
        this.modelId = modelId;
        this.roleArn = roleArn;
        this.inputS3Uri = inputS3Uri;
        this.outputS3Uri = outputS3Uri;
        this.jobName = jobName;
        this.region = region;
        this.profileName = profileName;
    }

    public static BedrockProperties fromEnv() {
        return new BedrockProperties(
                getEnv("MODEL_ID", "arn:aws:bedrock:us-east-1:856021349133:inference-profile/global.amazon.nova-2-lite-v1:0"),
                getEnv("ROLE_ARN", "arn:aws:iam::856021349133:role/Bedrock-Batch-Inference-Role"),
                getEnv("INPUT_S3_URI", "s3://bedrock-surface-batch-inference-bucket/input.jsonl"),
                getEnv("OUTPUT_S3_URI", "s3://bedrock-surface-batch-inference-output-bucket/"),
                getEnv("JOB_NAME", "Batch-Inference-" + UUID.randomUUID()),
                getEnv("AWS_REGION", "us-east-1"),
                getEnv("AWS_PROFILE", "dkudiyal")
        );
    }

    private static String getEnv(String key, String fallback) {
        String value = System.getenv(key);
        return value != null ? value : fallback;
    }

    public String modelId() { return modelId; }
    public String roleArn() { return roleArn; }
    public String inputS3Uri() { return inputS3Uri; }
    public String outputS3Uri() { return outputS3Uri; }
    public String jobName() { return jobName; }
    public String region() { return region; }
    public String profileName() { return profileName; }
}
