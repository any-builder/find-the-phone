# -*- coding: utf-8 -*-
import logging
import json
import os
import oss2
import requests

OBJECT_PREFIX = "wimp_activate_codes/"
LOG_PREFIX = "logs/"
OSS_ENDPOINT = "oss-{}-internal.aliyuncs.com"
OSS_BUCKET_NAME = "rockuw-hz"

# To enable the initializer feature (https://help.aliyun.com/document_detail/2513452.html)
# please implement the initializer function as below：
# def initializer(context):
#   logger = logging.getLogger()
#   logger.info('initializing')

HUAWEI_PUSH_URL = "https://push-api.cloud.huawei.com/v1/{app_id}/messages:send"
HMS_APP_ID = ""
HMS_CLIENT_ID = ""
HMS_CLIENT_SECRET = ""

def get_access_token(client_id: str, client_secret: str) -> str:
    url = "https://oauth-login.cloud.huawei.com/oauth2/v3/token"
    payload = {
        "grant_type": "client_credentials",
        "client_id": client_id,
        "client_secret": client_secret
    }
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    response = requests.post(url, data=payload, headers=headers, timeout=30)
    response.raise_for_status()
    return response.json()["access_token"]


def send_push(token: str, message: str, app_id: str, client_id: str, client_secret: str) -> dict:
    access_token = get_access_token(client_id, client_secret)

    payload = {
        "message": {
            "token": [token],
            "data": "{'alert':'true','message':'oops'}",
        }
    }
    print(f"Payload: {payload}")

    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {access_token}"
    }

    url = HUAWEI_PUSH_URL.format(app_id=app_id)
    response = requests.post(url, json=payload, headers=headers, timeout=30)
    response.raise_for_status()
    return response.json()


def save_log_to_oss(bucket, request_id, event):
    """Save event log to OSS with key logs/{request_id}"""
    try:
        bucket.put_object(f"{LOG_PREFIX}{request_id}", json.dumps(event))
    except Exception as e:
        logging.getLogger().error(f"Failed to save log to OSS: {e}")


def handler(event, context):
    evt = json.loads(event)
    logger = logging.getLogger()
    logger.info('hello world')

    access_key_id = context.credentials.accessKeyId
    access_key_secret = context.credentials.accessKeySecret
    security_token = context.credentials.securityToken

    oss_endpoint = os.environ.get("OSS_ENDPOINT", OSS_ENDPOINT).format(context.region)
    bucket_name = os.environ.get("OSS_BUCKET_NAME", OSS_BUCKET_NAME)

    auth = oss2.StsAuth(access_key_id, access_key_secret, security_token)
    bucket = oss2.Bucket(auth, oss_endpoint, bucket_name)

    # Save request log to OSS
    save_log_to_oss(bucket, context.request_id, evt)

    # Get 'k' parameter from query string
    query_params = evt.get('queryParameters', {})
    k_value = query_params.get('k') if isinstance(query_params, dict) else None
    if not k_value:
        return {"statusCode": 500, "body": json.dumps({"error": "Missing k parameter"})}

    # Extract code from k={code}/aligenie/{id}
    code = k_value.split('/aligenie')[0]

    # Get OSS object for the code
    resp = bucket.get_object(f"{OBJECT_PREFIX}{code}.token")
    oss_data = json.loads(resp.read())

    # If original k contains /aligenie, return verify_code
    if '/aligenie' in k_value:
        return oss_data.get('verify_code', '')

    # Otherwise trigger push with push_token
    token = oss_data['push_token']

    app_id = os.environ.get("HMS_APP_ID", HMS_APP_ID)
    client_id = os.environ.get("HMS_CLIENT_ID", HMS_CLIENT_ID)
    client_secret = os.environ.get("HMS_CLIENT_SECRET", HMS_CLIENT_SECRET)

    if not all([app_id, client_id, client_secret]):
        return {"statusCode": 500, "body": json.dumps({"error": "Missing HMS credentials"})}

    send_push(token, 'oops', app_id, client_id, client_secret)

    return {
        'statusCode': 200,
        'body': {
            "returnCode": "0",
            "returnErrorSolution": "",
            "returnMessage": "",
            "returnValue": {
                "reply": "即将响铃。",
                "resultType": "RESULT",
                "executeCode": "SUCCESS"
            }
        }
    }
