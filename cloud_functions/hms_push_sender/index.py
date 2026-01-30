# -*- coding: utf-8 -*-
import logging
import json
import os
import oss2
import requests

OBJECT_PREFIX = "wimp_activate_codes/"
OSS_ENDPOINT = "oss-cn-hangzhou-internal.aliyuncs.com"
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

def handler(event, context):
    evt = json.loads(event)
    logger = logging.getLogger()
    logger.info('hello world')
    if '/aligenie' in evt['rawPath']:
        return 'Jfc4Z4Ur15JwUBuvUQD5wg7Nu8+l+HscqYlfofbyJdaUC/XvprGmVLRZ2UFtMfMg'
    
    app_id = os.environ.get("HMS_APP_ID", HMS_APP_ID)
    client_id = os.environ.get("HMS_CLIENT_ID", HMS_CLIENT_ID)
    client_secret = os.environ.get("HMS_CLIENT_SECRET", HMS_CLIENT_SECRET)

    if not all([app_id, client_id, client_secret]):
        return {"statusCode": 500, "body": json.dumps({"error": "Missing HMS credentials"})}

    code = evt.get("headers", {}).get("Activate-Code")
    if not code:
        return {"statusCode": 500, "body": json.dumps({"error": "Missing Activate-Code parameter"})}

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
    resp = bucket.get_object(f"{OBJECT_PREFIX}{code}.token")
    token = json.loads(resp.read())['push_token']

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
