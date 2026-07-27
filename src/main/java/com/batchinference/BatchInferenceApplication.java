package com.batchinference;

import com.batchinference.config.BedrockClientFactory;
import com.batchinference.config.BedrockProperties;
import com.batchinference.service.BatchInferenceService;
import com.batchinference.service.BatchInferenceWithMessagesAPI;
import software.amazon.awssdk.services.bedrock.BedrockClient;

public class BatchInferenceApplication {

    public static void main(String[] args) {
        BedrockProperties config = BedrockProperties.fromEnv();

        var service = new BatchInferenceService(new BedrockClientFactory(config).create());

        String jobArn = service.submitJob(config);
        System.out.println(jobArn);
    }
}
