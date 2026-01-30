# OSS Event Store Function

This function stores events in OSS with the code as the object name.

## Environment Variables

- OSS_ENDPOINT: OSS endpoint (e.g., oss-cn-hangzhou.aliyuncs.com)
- OSS_BUCKET_NAME: Name of the OSS bucket

## Credentials

OSS access credentials are provided via `context.credentials` (automatically managed by FunctionCompute).

## Object Key Format

Objects are stored with prefix: `wimp_activate_codes/{code}`

## Event Format

{
    "code": "object_name",
    "push_token": "token_value"
}

## Response Format

- Success (201): {"statusCode": 201, "body": "{\"success\": true, \"code\": \"object_name\", \"object_key\": \"wimp_activate_codes/object_name\"}"}
- Error (400): {"statusCode": 400, "body": "{\"error\": \"error_message\"}"}
- Conflict (409): {"statusCode": 409, "body": "{\"error\": \"Object 'wimp_activate_codes/object_name' already exists\"}"}
- Server Error (500): {"statusCode": 500, "body": "{\"error\": \"error_message\"}"}
