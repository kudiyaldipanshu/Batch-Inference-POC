# AWS Bedrock Batch Inference

## Overview

POC to evaluate AWS Bedrock Batch Inference (API: `CreateModelInvocationJob`) for processing large volumes of inference requests asynchronously at 50% reduced cost.

---

## Supported Invocation Types

Batch inference supports two invocation types via the `modelInvocationType` parameter:

| Invocation Type | Description | When to Use |
|----------------|-------------|-------------|
| `InvokeModel` (default) | Model provider's native request format | Need provider-specific features (e.g., Anthropic `system` array, `anthropic_version`) |
| `Converse` | Bedrock's unified Converse API format | Model-agnostic, simpler payloads |

---

## Input Format

### Converse Invocation Type

```json
{
  "recordId": "CALL0000001",
  "modelInput": {
    "messages": [
      {
        "role": "user",
        "content": [{"text": "Your prompt here"}]
      }
    ],
    "inferenceConfig": {
      "maxTokens": 1024
    }
  }
}
```

### InvokeModel Invocation Type (Anthropic Format)

```json
{
  "recordId": "CALL0000001",
  "modelInput": {
    "anthropic_version": "bedrock-2023-05-31",
    "max_tokens": 1024,
    "messages": [
      {
        "role": "user",
        "content": [{"type": "text", "text": "Your prompt here"}]
      }
    ],
    "system": [{"text": "System prompt here"}]
  }
}
```

**Notes:**
- `recordId` is optional on input — Bedrock adds it if omitted.
- Output record order is **not guaranteed** to match input order.
- Input file must have `.jsonl` extension.
- S3 URI can point to a folder (all `.jsonl` files processed) or a single file.

---

## Output Format

### Success Record

```json
{
  "recordId": "1",
  "modelInput": { ... },
  "modelOutput": {
    "output": {
      "message": {
        "role": "assistant",
        "content": [{"text": "Response text"}]
      }
    },
    "stopReason": "end_turn",
    "usage": {
      "inputTokens": 69,
      "outputTokens": 44,
      "totalTokens": 113
    },
    "metrics": {
      "latencyMs": 537
    }
  }
}
```

### Error Record

Replaces `modelOutput` with `error`:

```json
{
  "recordId": "5",
  "modelInput": { ... },
  "error": {
    "errorCode": 403,
    "errorMessage": "You invoked an unsupported model or your request did not allow prompt caching.",
    "expired": false,
    "retryable": false
  }
}
```

### Job Summary (manifest.json.out)

```json
{
  "totalRecordCount": 200,
  "processedRecordCount": 200,
  "successRecordCount": 200,
  "errorRecordCount": 0,
  "inputTokenCount": 13142,
  "outputTokenCount": 84959
}
```

---

## Key Findings

### 1. Prompt Caching Not Supported

- Batch inference **does not support prompt caching**.
- Records containing `cachePoint` fail with HTTP 403:
  > "You invoked an unsupported model or your request did not allow prompt caching."
- Errors appear per-record in output; non-cached records succeed normally.
- Observed: 200 records, 67 failed (had `cachePoint`), 133 succeeded.

### 2. No Model Invocation Logging

- Model invocation logging only applies to `bedrock-runtime` endpoint operations.
- Batch inference runs through the `bedrock` control-plane — **not logged**.
- Monitoring alternatives:
    - `manifest.json.out` in S3 output (token counts, success/error counts)
    - `GetModelInvocationJob` API (job status, progress counters)
    - **Amazon EventBridge** for automated state-change notifications

### 3. No Tool Calling or Structured Output

- Batch inference **does not support tool calling (function calling)**.
- `response_format` (structured output) is **not supported**.
- Each record is processed independently — no multi-turn interaction.

### 4. Media Files Must Be Co-located

- Media (images, videos, documents) referenced via S3 URIs must reside in the **same S3 bucket and folder** as the input JSONL.
- `InputDataConfig` must point to the **folder** containing all linked resources, not just the `.jsonl` file.
- S3 paths are case-sensitive.

### 5. Thinking Tokens Counted as Output Tokens

- Extended thinking tokens are included in `outputTokenCount` in the manifest and per-record usage.

### 6. Supported Models

- **Text generation**: Anthropic Claude family, Amazon Nova, Meta Llama, Mistral, DeepSeek, and others.
- **Embedding**: Amazon Titan Text Embeddings V2, Titan Multimodal Embeddings, Nova Multimodal Embeddings.
- **Cross-region inference profiles**: Supported.
- **Provisioned models**: NOT supported.

### 7. VPC / PrivateLink Support

- Subnet IDs and Security Group IDs can be provided via API (not available in console).
- Bedrock creates ENIs in your subnets tagged with `BedrockManaged`.
- Recommend at least one subnet per AZ.
- Combine with S3 VPC endpoints for fully private connectivity.
- Requires additional IAM permissions: `ec2:CreateNetworkInterface`, `ec2:DeleteNetworkInterface`, `ec2:DescribeNetworkInterfaces`.

### 8. Pricing

- **50% discount** compared to on-demand inference pricing.
- Token-based billing (per 1M input/output tokens).
- You are charged for tokens already processed even if you stop a job mid-flight.

### 9. Cross-Account S3 Access

- Input/output buckets can be in a **different AWS account**.
- Requires: S3 bucket policy granting the batch inference service role access.
- **Only available via API** (not console).
- If KMS-encrypted, also need `kms:Decrypt` and `kms:DescribeKey` permissions.

### 10. Job Timeout (`timeoutDurationInHours`)

- Set via `timeoutDurationInHours` parameter when creating the job.
- **Minimum**: 24 hours.
- **Maximum**: 168 hours (7 days).
- The timeout clock starts from job creation, not from when processing begins.
- If the job is still in `Scheduled` state and hasn't started processing before the timeout, it moves to **`Expired`** status.
- If the job is `InProgress` but cannot finish all records within the timeout, it moves to **`PartiallyCompleted`** — already-processed results are available in S3.
- The `jobExpirationTime` field in `GetModelInvocationJob` response shows the exact expiry timestamp.

### 11. Quotas & Limits

| Quota | Description |
|-------|-------------|
| Minimum records per job | Model-specific minimum |
| Records per input file | Max records in a single JSONL file |
| Records per job | Max records across all JSONL files |
| Input file size | Max size of a single file |
| Job size | Max cumulative size of all input files |

---

## Job Lifecycle

```
Submitted → Validating → Scheduled → InProgress → Completed
                                                 → PartiallyCompleted
                                                 → Failed
                                   → Expired (timeout before start)
                       → Stopping → Stopped (user-initiated)
```

**Validation checks:**
- IAM service role has S3 access
- Files are `.jsonl` with valid JSON per line
- Files meet size/record count quotas
- Does **NOT** validate if `modelInput` matches the model's expected request schema

---

## IAM Requirements

### Service Role Trust Policy

```json
{
  "Effect": "Allow",
  "Principal": {"Service": "bedrock.amazonaws.com"},
  "Action": "sts:AssumeRole",
  "Condition": {
    "StringEquals": {"aws:SourceAccount": "<account-id>"},
    "ArnEquals": {"aws:SourceArn": "arn:aws:bedrock:<region>:<account-id>:model-invocation-job/*"}
  }
}
```

### Service Role S3 Permissions

- `s3:GetObject`, `s3:ListBucket` on input bucket
- `s3:GetObject`, `s3:PutObject`, `s3:ListBucket` on output bucket
- For inference profiles: `bedrock:InvokeModel` on both inference profile and foundation model ARNs

---

## S3 Structure

```
Input Bucket:
  converse-input/
    part-01.jsonl            (Converse invocation type)
  messages-input/
    part-01.jsonl            (InvokeModel type - Anthropic format)
    image.png                (media co-located in same folder)

Output Bucket:
  <job-id>/
    manifest.json.out        (job summary)
    part-01.jsonl.out        (per-record results)
```

---

## Limitations Summary

| Limitation | Impact |
|-----------|--------|
| No prompt caching | Cannot reduce token costs via cache; records with `cachePoint` fail |
| No tool calling | Cannot use function calling in batch |
| No structured output | `response_format` not supported |
| No model invocation logging | Must rely on manifest + EventBridge |
| No streaming | Results only in S3 after completion |
| No provisioned throughput | Only on-demand models |
| Output order not guaranteed | Must use `recordId` for correlation |
| VPC config API-only | Cannot set via console |
| Cross-account S3 API-only | Cannot set via console |

---

## Recommendations

1. **Do not use `cachePoint`** — causes per-record 403 failures.
2. Use **Converse** for model-agnostic workloads. Use **InvokeModel** when you need provider-specific features (Anthropic `system` prompts, `anthropic_version`, etc.).
3. Set up **EventBridge rules** for job state-change notifications instead of polling.
4. Point `InputDataConfig` to the **folder** (not file) when referencing media in prompts.
5. Set `timeoutDurationInHours` appropriately — minimum 24h, max 7 days. Jobs that don't start before timeout expire.
6. Use `recordId` in input to correlate with output since order is not preserved.
7. Leverage the 50% pricing discount for non-latency-sensitive, high-volume workloads.
