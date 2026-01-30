import json
import oss2
import os
import base64

OBJECT_PREFIX = "wimp_activate_codes/"
OSS_ENDPOINT = "oss-cn-hangzhou-internal.aliyuncs.com"
OSS_BUCKET_NAME = "rockuw-hz"

def handler(event, context):
    try:
        event_json = json.loads(event)
    except:
        return "The request did not come from an HTTP Trigger because the event is not a json string, event: {}".format(event)
    
    if "body" not in event_json:
        return "The request did not come from an HTTP Trigger because the event does not include the 'body' field, event: {}".format(event)
    req_body = event_json['body']
    if 'isBase64Encoded' in event_json and event_json['isBase64Encoded']:
        req_body = base64.b64decode(event_json['body']).decode("utf-8")
    event = json.loads(req_body)

    code = event.get("code")
    push_token = event.get("push_token")

    if not code:
        return {"statusCode": 400, "body": json.dumps({"error": "Missing code"})}

    if not push_token:
        return {"statusCode": 400, "body": json.dumps({"error": "Missing push_token"})}

    if not context or not context.credentials:
        return {"statusCode": 500, "body": json.dumps({"error": "Missing credentials in context"})}

    access_key_id = context.credentials.accessKeyId
    access_key_secret = context.credentials.accessKeySecret
    security_token = context.credentials.securityToken

    if not security_token:
        return {"statusCode": 500, "body": json.dumps({"error": "Missing security token in credentials"})}

    oss_endpoint = os.environ.get("OSS_ENDPOINT", OSS_ENDPOINT)
    bucket_name = os.environ.get("OSS_BUCKET_NAME", OSS_BUCKET_NAME)

    if not all([oss_endpoint, bucket_name]):
        return {"statusCode": 500, "body": json.dumps({"error": "Missing OSS configuration"})}

    auth = oss2.StsAuth(access_key_id, access_key_secret, security_token)
    bucket = oss2.Bucket(auth, oss_endpoint, bucket_name)

    object_key = f"{OBJECT_PREFIX}{code}"

    content = json.dumps(event)
    bucket.put_object(f"{object_key}.token", content)

    return {"statusCode": 201, "body": json.dumps({"success": True, "code": code})}

if __name__ == "__main__":
    test_event = {"code": "test_code", "push_token": "test_token"}
    resp = handler(json.dumps(test_event), None)
    print(resp)
