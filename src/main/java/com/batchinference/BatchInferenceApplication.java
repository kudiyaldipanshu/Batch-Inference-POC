package com.batchinference;

import com.batchinference.config.BedrockProperties;
import com.batchinference.service.BatchInferenceWithMessagesAPI;

public class BatchInferenceApplication {

    public static void main(String[] args) {
        BedrockProperties config = BedrockProperties.fromEnv();

        var service = new BatchInferenceWithMessagesAPI(config);

        String jobArn = service.submitJob();
        System.out.println(jobArn);
    }
}
