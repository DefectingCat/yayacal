"""真实 HTTP + PostgreSQL 集成检查。仅指向一次性测试数据库，测试会创建数据。"""
import json
import os
import struct
import unittest
import urllib.error
import urllib.request
import uuid
import zlib

BASE = os.environ.get("MOMENTS_TEST_URL", "http://127.0.0.1:8088")


def call(method, path, body=None, actor="xiaobai", expected=200, content_type=None):
    headers = {"X-Account-ID": actor} if actor else {}
    if body is not None:
        if content_type:
            headers["Content-Type"] = content_type
        else:
            headers["Content-Type"] = "application/json"
            body = json.dumps(body).encode()
    request = urllib.request.Request(BASE + path, body, headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    data = response.read()
    response.close()
    assert response.status == expected, (method, path, response.status, data)
    return json.loads(data) if "application/json" in response.headers.get("Content-Type", "") else data


def upload(actor="xiaobai", data=None, expected=200):
    if data is None:
        def chunk(kind, content):
            return struct.pack(">I", len(content)) + kind + content + struct.pack(">I", zlib.crc32(kind + content))
        data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", 2, 2, 8, 2, 0, 0, 0))
        data += chunk(b"IDAT", zlib.compress(b"\x00\xff\x00\x00\x00\xff\x00" * 2)) + chunk(b"IEND", b"")
    boundary = uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="../../test.png"\r\n'
            'Content-Type: image/png\r\n\r\n').encode() + data + f'\r\n--{boundary}--\r\n'.encode()
    return call("POST", "/api/v1/media", body, actor, expected, f"multipart/form-data; boundary={boundary}")


class ApiTest(unittest.TestCase):
    def test_accounts_and_media(self):
        accounts = call("GET", "/api/v1/accounts", actor=None)
        self.assertEqual([(a["id"], a["name"]) for a in accounts], [("xiaobai", "小白"), ("xiaojimao", "小鸡毛")])
        call("PATCH", "/api/v1/me", {}, actor=None, expected=401)
        call("PATCH", "/api/v1/me", {}, actor="unknown", expected=401)
        call("PATCH", "/api/v1/me", {"name": "改名"}, expected=422)
        upload(data=b"not-an-image", expected=400)
        media = upload()
        self.assertEqual(media["width"], 2)
        path = f'/api/v1/media/{media["id"]}'
        call("GET", path + "?account_id=xiaobai")
        call("GET", path + "?account_id=xiaojimao", expected=404)
        call("PATCH", "/api/v1/me", {"cover_id": media["id"]}, actor="xiaojimao", expected=400)
        call("PATCH", "/api/v1/me", {"cover_id": media["id"]})
        call("GET", path + "?account_id=xiaojimao&thumbnail=true")


if __name__ == "__main__":
    unittest.main()
