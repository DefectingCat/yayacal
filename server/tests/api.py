"""真实 HTTP + PostgreSQL 集成检查。仅指向一次性测试数据库，测试会创建数据。"""
import json
import os
import struct
import unittest
import urllib.error
import urllib.request
import uuid
import zlib
from concurrent.futures import ThreadPoolExecutor

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
    def test_image_orientation_is_applied_before_metadata_is_removed(self):
        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
        # 2x3 PNG，EXIF orientation=6（顺时针 90 度），返回的正常方向应为 3x2。
        exif = b"II" + struct.pack("<HIH", 42, 8, 1) + struct.pack("<HHIHHI", 274, 3, 1, 6, 0, 0)
        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", 2, 3, 8, 2, 0, 0, 0))
        png += chunk(b"eXIf", exif) + chunk(b"IDAT", zlib.compress(b"\x00\xff\x00\x00\x00\xff\x00" * 3)) + chunk(b"IEND", b"")
        media = upload(data=png)
        self.assertEqual((media["width"], media["height"]), (3, 2))

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

    def test_two_accounts_complete_interaction_and_permissions(self):
        marker = uuid.uuid4().hex
        media = upload()
        payload = {"request_id": str(uuid.uuid4()), "text": "小白测试 " + marker,
                   "media_ids": [media["id"]], "visibility": "public"}
        with ThreadPoolExecutor(max_workers=4) as pool:
            copies = list(pool.map(lambda _: call("POST", "/api/v1/posts", payload), range(4)))
        self.assertEqual(len({p["id"] for p in copies}), 1)
        post = copies[0]
        path = "/api/v1/posts/" + post["id"]
        self.assertEqual(post["author_name"], "小白")
        call("POST", "/api/v1/posts", {**payload, "text": "修改"}, expected=409)
        call("DELETE", path, actor="xiaojimao", expected=404)
        call("PATCH", path, {"visibility": "private"}, actor="xiaojimao", expected=404)

        with ThreadPoolExecutor(max_workers=4) as pool:
            list(pool.map(lambda _: call("PUT", path + "/like", actor="xiaojimao", expected=204), range(4)))
        liked = call("GET", path)
        self.assertEqual([a["id"] for a in liked["likes"]], ["xiaojimao"])
        notices = call("GET", "/api/v1/notifications")["items"]
        likes = [n for n in notices if n["post_id"] == post["id"] and n["type"] == "like"]
        self.assertEqual(len(likes), 1)
        call("DELETE", "/api/v1/notifications/" + likes[0]["id"], actor="xiaojimao", expected=404)
        call("POST", "/api/v1/notifications/read", {"ids": [likes[0]["id"]]}, expected=204)

        comment_media = upload("xiaojimao")
        comment_body = {"request_id": str(uuid.uuid4()), "text": "评论 " + marker,
                        "media_id": comment_media["id"]}
        with ThreadPoolExecutor(max_workers=4) as pool:
            comments = list(pool.map(lambda _: call("POST", path + "/comments", comment_body, actor="xiaojimao"), range(4)))
        self.assertEqual(len({c["id"] for c in comments}), 1)
        comment_id = comments[0]["id"]
        reply = call("POST", path + "/comments", {"request_id": str(uuid.uuid4()), "text": "收到啦", "reply_to_id": comment_id})
        rows = call("GET", path + "/comments?limit=1")
        self.assertEqual(len(rows["items"]), 1)
        self.assertTrue(rows["next_cursor"])
        next_page = call("GET", path + "/comments?cursor=" + rows["next_cursor"])
        self.assertEqual(next_page["items"][0]["id"], reply["id"])
        self.assertEqual(next_page["items"][0]["reply_to_name"], "小鸡毛")
        self.assertTrue(any(n["type"] == "reply" and n["post_id"] == post["id"]
                            for n in call("GET", "/api/v1/notifications", actor="xiaojimao")["items"]))

        other = call("POST", "/api/v1/posts", {"request_id": str(uuid.uuid4()), "text": marker, "visibility": "public"})
        call("POST", "/api/v1/posts/" + other["id"] + "/comments",
             {"request_id": str(uuid.uuid4()), "text": "错误关联", "reply_to_id": comment_id}, expected=400)
        call("POST", "/api/v1/posts", {"request_id": str(uuid.uuid4()), "text": "偷图", "media_ids": [media["id"]], "visibility": "public"}, actor="xiaojimao", expected=400)

        call("PATCH", path, {"visibility": "private"})
        for suffix in ["", "/comments"]:
            call("GET", path + suffix, actor="xiaojimao", expected=404)
        call("PUT", path + "/like", actor="xiaojimao", expected=404)
        call("POST", path + "/comments", {"request_id": str(uuid.uuid4()), "text": "越权"}, actor="xiaojimao", expected=404)
        call("GET", f'/api/v1/media/{media["id"]}?account_id=xiaojimao', expected=404)
        for endpoint in ["/api/v1/posts?q=" + marker, "/api/v1/posts?author_id=xiaobai", "/api/v1/notifications"]:
            self.assertFalse(any(row.get("post_id", row.get("id")) == post["id"] for row in call("GET", endpoint, actor="xiaojimao")["items"]))
        self.assertEqual(call("GET", path)["visibility"], "private")

        call("PATCH", path, {"visibility": "public"})
        call("DELETE", "/api/v1/comments/" + comment_id, expected=404)
        call("DELETE", "/api/v1/comments/" + comment_id, actor="xiaojimao", expected=204)
        tombstone = call("GET", path + "/comments")["items"][0]
        self.assertTrue(tombstone["deleted"])
        self.assertIsNone(tombstone["media_id"])
        call("POST", path + "/comments", comment_body, actor="xiaojimao", expected=404)
        call("DELETE", path + "/like", actor="xiaojimao", expected=204)
        self.assertEqual(call("GET", path)["likes"], [])
        call("DELETE", path, expected=204)
        call("DELETE", path, expected=204)
        call("GET", path, expected=404)
        call("POST", "/api/v1/posts", payload, expected=404)
        call("DELETE", "/api/v1/posts/" + other["id"], expected=204)

    def test_validation_and_paging(self):
        marker = uuid.uuid4().hex
        for payload in [{"text": "", "visibility": "public"}, {"text": "x", "visibility": "nonsense"},
                        {"text": "字" * 5001, "visibility": "public"}]:
            call("POST", "/api/v1/posts", {"request_id": str(uuid.uuid4()), **payload}, expected=400)
        ids = []
        for i in range(3):
            ids.append(call("POST", "/api/v1/posts", {"request_id": str(uuid.uuid4()), "text": marker + str(i), "location_address": marker + "addr", "visibility": "public"})["id"])
        self.assertEqual(len(call("GET", "/api/v1/posts?q=" + marker + "addr")["items"]), 3)
        page = call("GET", "/api/v1/posts?q=" + marker + "&limit=2")
        self.assertEqual([p["id"] for p in page["items"]], list(reversed(ids[1:])))
        page2 = call("GET", "/api/v1/posts?q=" + marker + "&limit=2&cursor=" + page["next_cursor"])
        self.assertEqual([p["id"] for p in page2["items"]], ids[:1])
        self.assertIsNone(page2["next_cursor"])
        call("GET", "/api/v1/posts?cursor=invalid", expected=400)
        for id in ids:
            call("DELETE", "/api/v1/posts/" + id, expected=204)


if __name__ == "__main__":
    unittest.main()
