"""真实 HTTP + PostgreSQL 集成检查。仅指向一次性测试数据库，测试会创建数据。"""
import json
import http.client
import hashlib
import os
import re
import struct
import time
import unittest
import urllib.error
import urllib.request
import urllib.parse
import uuid
import zlib
from concurrent.futures import ThreadPoolExecutor

BASE = os.environ.get("MOMENTS_TEST_URL", "http://127.0.0.1:8088")
EXPECTED_SERVER = os.environ.get("MOMENTS_EXPECTED_SERVER")


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
    assert response.status in (expected if isinstance(expected, tuple) else (expected,)), (method, path, response.status, data)
    server = response.headers.get("X-Server", "")
    assert re.fullmatch(r"yaya server v[0-9]+\.[0-9]+\.[0-9]+-(?:[0-9a-f]{7}|unknown)", server), server
    if EXPECTED_SERVER:
        assert server == EXPECTED_SERVER, (server, EXPECTED_SERVER)
    return json.loads(data) if data and "application/json" in response.headers.get("Content-Type", "") else data


def png_image(size=None):
    def chunk(kind, content):
        return struct.pack(">I", len(content)) + kind + content + struct.pack(">I", zlib.crc32(kind + content))
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", 2, 2, 8, 2, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(b"\x00\xff\x00\x00\x00\xff\x00" * 2))
    if size is not None:
        # 合法私有 ancillary chunk 控制文件大小，避免尺寸/解码内存干扰字节边界测试。
        padding = size - len(data) - 24
        assert padding >= 0
        data += chunk(b"paDd", b"\x00" * padding)
    return data + chunk(b"IEND", b"")


def upload(actor="xiaobai", data=None, expected=200):
    if data is None:
        data = png_image()
    boundary = uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="../../test.png"\r\n'
            'Content-Type: image/png\r\n\r\n').encode() + data + f'\r\n--{boundary}--\r\n'.encode()
    return call("POST", "/api/v1/media", body, actor, expected, f"multipart/form-data; boundary={boundary}")


class ApiTest(unittest.TestCase):
    def test_legacy_media_backfill_preserves_original_and_publishes_ready_variants(self):
        fixture_path = os.environ.get("MOMENTS_TEST_LEGACY_FIXTURE")
        if not fixture_path:
            self.skipTest("启动补图检查需要通过 tests/run.sh 创建旧图片记录")
        with open(fixture_path) as source:
            fixture = json.load(source)
        path = f'/api/v1/media/{fixture["id"]}?account_id=xiaobai'
        self.assertEqual(hashlib.sha256(call("GET", path)).hexdigest(), fixture["sha256"])
        post = call("POST", "/api/v1/posts", {"request_id": str(uuid.uuid4()), "text": "旧图片补图", "visibility": "private", "media_ids": [fixture["id"]]})
        photo = post["photos"][0]
        self.assertEqual(len(call("GET", path)), photo["bytes"])
        for variant in ("thumbnail", "preview"):
            self.assertGreater(photo[variant + "_bytes"], 0)
            self.assertEqual(len(call("GET", path + "&variant=" + variant)), photo[variant + "_bytes"])
        call("DELETE", f'/api/v1/posts/{post["id"]}', expected=204)

    def test_media_upload_concurrency_is_bounded_before_reading_file(self):
        address = urllib.parse.urlsplit(BASE)
        connections = []
        try:
            for _ in range(1):
                connection = http.client.HTTPConnection(address.hostname, address.port, timeout=5)
                connections.append(connection)
                connection.putrequest("POST", "/api/v1/media")
                connection.putheader("X-Account-ID", "xiaobai")
                connection.putheader("Content-Type", "multipart/form-data; boundary=held-upload")
                connection.putheader("Content-Length", str(1024 * 1024))
                # 保持一个未读完的请求，验证图片处理并发限制在文件读取前生效。
                connection.endheaders(b'--held-upload\r\nContent-Disposition: form-data; name="file"; filename="test.png"\r\n\r\nx')
            deadline = time.monotonic() + 5
            while True:
                result = upload(expected=(200, 429))
                if "error" in result:
                    self.assertEqual(result["error"], "图片上传繁忙，请稍后重试")
                    break
                self.assertLess(time.monotonic(), deadline, "上传并发限制未生效")
                time.sleep(0.01)
        finally:
            for connection in connections:
                connection.close()
        deadline = time.monotonic() + 5
        while "error" in upload(expected=(200, 429)):
            self.assertLess(time.monotonic(), deadline, "取消上传后处理名额未释放")
            time.sleep(0.01)

    def test_media_upload_size_boundaries(self):
        limit = 50 * 1024 * 1024
        for size in (10 * 1024 * 1024 + 1, limit):
            with self.subTest(size=size):
                media = upload(data=png_image(size))
                self.assertEqual((media["width"], media["height"]), (2, 2))
                stored = call("GET", f'/api/v1/media/{media["id"]}?account_id=xiaobai')
                self.assertEqual(stored[:4], b"RIFF")
                self.assertEqual(stored[8:12], b"WEBP")
        for size in (limit + 1, 51 * 1024 * 1024 + 1):
            with self.subTest(size=size):
                error = upload(data=b"\x00" * size, expected=413)
                self.assertEqual(error["error"], "图片不能超过 50 MiB")

    def test_version_header_on_health_head_errors_and_fallback(self):
        self.assertEqual(call("GET", "/health", actor=None), b"ok")
        self.assertEqual(call("HEAD", "/health", actor=None), b"")
        call("GET", "/api/v1/posts", actor=None, expected=401)
        call("GET", "/missing", actor=None, expected=404)
        self.assertEqual(call("HEAD", "/missing", actor=None, expected=404), b"")
        call("POST", "/health", actor=None, expected=405)

    def test_reset_avatar_is_account_scoped_idempotent_and_preserves_cover(self):
        call("DELETE", "/api/v1/me/avatar", actor=None, expected=401)
        call("DELETE", "/api/v1/me/avatar", actor="unknown", expected=401)
        avatar = upload()
        cover = upload()
        other_avatar = upload("xiaojimao")
        call("PATCH", "/api/v1/me", {"avatar_id": avatar["id"], "cover_id": cover["id"]})
        call("PATCH", "/api/v1/me", {"avatar_id": other_avatar["id"]}, actor="xiaojimao")
        call("GET", f'/api/v1/media/{avatar["id"]}?account_id=xiaojimao')
        call("PATCH", "/api/v1/me", {"avatar_id": avatar["id"]}, actor="xiaojimao", expected=400)
        post = call("POST", "/api/v1/posts", {"request_id": str(uuid.uuid4()), "text": "头像同步", "visibility": "public"})
        path = "/api/v1/posts/" + post["id"]
        self.assertEqual(call("GET", path, actor="xiaojimao")["avatar_id"], avatar["id"])

        for _ in range(2):
            profile = call("DELETE", "/api/v1/me/avatar")
            self.assertEqual(profile["id"], "xiaobai")
            self.assertIsNone(profile["avatar_id"])
            self.assertEqual(profile["cover_id"], cover["id"])
        accounts = {a["id"]: a for a in call("GET", "/api/v1/accounts", actor=None)}
        self.assertIsNone(accounts["xiaobai"]["avatar_id"])
        self.assertEqual(accounts["xiaojimao"]["avatar_id"], other_avatar["id"])
        self.assertIsNone(call("GET", path, actor="xiaojimao")["avatar_id"])
        call("GET", f'/api/v1/media/{avatar["id"]}?account_id=xiaojimao', expected=404)
        call("GET", f'/api/v1/media/{cover["id"]}?account_id=xiaojimao')
        call("DELETE", path, expected=204)
        call("DELETE", "/api/v1/me/avatar", actor="xiaojimao")

    def test_unlike_then_relike_creates_one_new_unread_notification(self):
        post = call("POST", "/api/v1/posts", {"request_id": str(uuid.uuid4()), "text": "重新点赞", "visibility": "public"})
        path = "/api/v1/posts/" + post["id"]
        def notices():
            return [n for n in call("GET", "/api/v1/notifications")["items"] if n["post_id"] == post["id"]]
        call("PUT", path + "/like", actor="xiaojimao", expected=204)
        first = notices()[0]
        call("POST", "/api/v1/notifications/read", {"ids": [first["id"]]}, expected=204)
        call("DELETE", path + "/like", actor="xiaojimao", expected=204)
        self.assertEqual(notices(), [])
        call("PUT", path + "/like", actor="xiaojimao", expected=204)
        call("PUT", path + "/like", actor="xiaojimao", expected=204)
        second = notices()
        self.assertEqual(len(second), 1)
        self.assertNotEqual(first["id"], second[0]["id"])
        self.assertFalse(second[0]["read"])
        call("DELETE", path, expected=204)

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

    def test_media_variants_share_visibility_and_report_exact_sizes(self):
        media = upload()
        path = f'/api/v1/media/{media["id"]}?account_id=xiaobai'
        for variant, size in [("original", media["bytes"]), ("thumbnail", media["thumbnail_bytes"]), ("preview", media["preview_bytes"])]:
            data = call("GET", path + "&variant=" + variant)
            self.assertEqual(len(data), size)
            self.assertEqual(data[:4], b"RIFF")
            self.assertEqual(call("HEAD", path + "&variant=" + variant), b"")
        self.assertEqual(call("GET", path + "&thumbnail=true"), call("GET", path + "&variant=thumbnail"))
        call("GET", path + "&variant=unsupported", expected=400)
        original = call("GET", path)
        request = urllib.request.Request(BASE + path, headers={"Range": "bytes=0-15"})
        with urllib.request.urlopen(request) as response:
            self.assertEqual(response.status, 206)
            self.assertEqual(response.read(), original[:16])
            self.assertEqual(response.headers["Content-Range"], f"bytes 0-15/{len(original)}")
            self.assertEqual(response.headers["Cache-Control"], "private, no-store")
            self.assertRegex(response.headers["X-Server"], r"^yaya server v[0-9]+\.[0-9]+\.[0-9]+-(?:[0-9a-f]{7}|unknown)$")
            if EXPECTED_SERVER:
                self.assertEqual(response.headers["X-Server"], EXPECTED_SERVER)
        post = call("POST", "/api/v1/posts", {"request_id": str(uuid.uuid4()), "text": "规格权限", "visibility": "public", "media_ids": [media["id"]]})
        self.assertEqual(post["photos"][0]["preview_bytes"], media["preview_bytes"])
        comment_media = upload("xiaojimao")
        call("POST", f'/api/v1/posts/{post["id"]}/comments', {"request_id": str(uuid.uuid4()), "text": "图片评论", "media_id": comment_media["id"]}, actor="xiaojimao")
        comments = call("GET", f'/api/v1/posts/{post["id"]}/comments')["items"]
        self.assertEqual(comments[0]["media_info"]["bytes"], comment_media["bytes"])
        self.assertEqual(comments[0]["media_info"]["preview_bytes"], comment_media["preview_bytes"])
        for variant in ("original", "thumbnail", "preview"):
            other = path.replace("account_id=xiaobai", "account_id=xiaojimao") + "&variant=" + variant
            call("GET", other)
        call("PATCH", f'/api/v1/posts/{post["id"]}', {"visibility": "private"})
        for variant in ("original", "thumbnail", "preview"):
            other = path.replace("account_id=xiaobai", "account_id=xiaojimao") + "&variant=" + variant
            call("GET", other, expected=404)
            call("HEAD", other, expected=404)
        call("DELETE", f'/api/v1/posts/{post["id"]}', expected=204)

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
