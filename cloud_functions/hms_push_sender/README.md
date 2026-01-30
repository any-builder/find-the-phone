# HMS Push Sender Function

Aliyun FunctionCompute function to send HMS Push Kit notifications to trigger alert audio on the Monitor Alert app.

## Setup

1. **Configure Environment Variables**:
   - `HMS_APP_ID`: Your HMS App ID from AppGallery Connect
   - `HMS_CLIENT_ID`: OAuth 2.0 Client ID
   - `HMS_CLIENT_SECRET`: OAuth 2.0 Client Secret

2. **Deploy to Aliyun FunctionCompute**:
   ```bash
   cd cloud_functions/hms_push_sender
   s deploy
   ```

## Usage

```json
POST /invoke
{
  "token": "device_push_token_here",
  "message": "Motion detected!"
}
```

## Response

```json
{
  "statusCode": 200,
  "body": "{\"success\": true, \"result\": {...}}"
}
```

## HMS Push Format

The notification is sent with the following format that the app expects:

```json
{
  "alert": "true",
  "message": "Your alert message here"
}
```
