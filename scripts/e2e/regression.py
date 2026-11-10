#!/usr/bin/env python3
"""
Lemon 端到端回归测试（只依赖 Python 3 标准库）。

全程通过 HTTP 接口操作：自己创建测试题库 / 题目 / 用户，跑完主流程后清理题库数据，
测试用户会被禁用（用户端账号不提供删除接口）。可以对开发环境或部署后的环境运行。

用法：
  # 开发环境（后端 8080 直连）
  python3 scripts/e2e/regression.py --base http://localhost:8080 --admin-password admin123

  # 部署环境（经 Nginx，按域名区分站点）
  python3 scripts/e2e/regression.py --base https://www.example.com --admin-base https://admin.example.com \\
      --api-base https://api.example.com --admin-password '***'

  # 本地起的部署栈（同一端口，用 Host 头区分站点）
  python3 scripts/e2e/regression.py --base http://127.0.0.1:18080 --host www.lemon.test \\
      --admin-host admin.lemon.test --api-host api.lemon.test --admin-password '***'
"""
import argparse
import json
import random
import ssl
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib

passed = 0
failed = 0


def check(desc, cond, detail=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  ok    {desc}")
    else:
        failed += 1
        print(f"  FAIL  {desc}  {str(detail)[:300]}")


SSL_CONTEXT = None


class Site:
    """一个站点（用户端 / 管理端 / 小程序接口域名）"""

    def __init__(self, base, host=None):
        self.base = base.rstrip("/")
        self.host = host
        self.token = None

    def origin(self):
        """浏览器会带的 Origin：scheme://host[:port]"""
        scheme, rest = self.base.split("://", 1)
        hostport = rest.split("/", 1)[0]
        if self.host:
            port = hostport.split(":", 1)[1] if ":" in hostport else None
            hostport = self.host + (f":{port}" if port else "")
        return f"{scheme}://{hostport}"

    def raw(self, method, path, body=None, headers=None):
        h = dict(headers or {})
        if self.host:
            h["Host"] = self.host
        # 和浏览器一样带上 Origin（同源请求也会带），用来发现反向代理丢端口导致的跨域误判
        h.setdefault("Origin", self.origin())
        if self.token:
            h["Authorization"] = "Bearer " + self.token
        req = urllib.request.Request(self.base + path, method=method, data=body, headers=h)
        try:
            with urllib.request.urlopen(req, timeout=30, context=SSL_CONTEXT) as resp:
                return resp.status, resp.headers, resp.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

    def call(self, method, path, data=None):
        body = json.dumps(data).encode() if data is not None else None
        status, _, content = self.raw(method, path, body, {"Content-Type": "application/json"})
        try:
            return json.loads(content)
        except ValueError:
            return {"code": -status, "msg": content[:120].decode(errors="replace")}

    def upload(self, path, filename, content, content_type, fields=None):
        boundary = uuid.uuid4().hex
        parts = []
        for k, v in (fields or {}).items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                     f'Content-Type: {content_type}\r\n\r\n'.encode() + content + b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        status, _, resp = self.raw("POST", path, b"".join(parts), {"Content-Type": f"multipart/form-data; boundary={boundary}"})
        return json.loads(resp)


def png(w=8, h=8):
    rows = b"".join(b"\x00" + bytes((245, 184, 0)) * w for _ in range(h))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def ok(resp, what):
    if resp.get("code") != 0:
        raise SystemExit(f"准备数据失败：{what} → {resp}")
    return resp.get("data")


def main():
    ap = argparse.ArgumentParser(description="Lemon 端到端回归测试")
    ap.add_argument("--base", required=True, help="用户端地址（www 或开发环境后端）")
    ap.add_argument("--admin-base", help="管理端地址，默认同 --base")
    ap.add_argument("--api-base", help="小程序接口域名地址，可选")
    ap.add_argument("--host", help="用户端 Host 头（同端口多站点时用）")
    ap.add_argument("--admin-host")
    ap.add_argument("--api-host")
    ap.add_argument("--admin-user", default="admin")
    ap.add_argument("--admin-password", required=True)
    ap.add_argument("--insecure", action="store_true", help="不校验 HTTPS 证书（仅用于自签名证书的测试环境）")
    args = ap.parse_args()
    if args.insecure:
        global SSL_CONTEXT
        SSL_CONTEXT = ssl._create_unverified_context()

    app = Site(args.base, args.host)
    admin = Site(args.admin_base or args.base, args.admin_host or args.host)
    # 小程序接口域名：未单独给地址时与用户端同一地址（靠 Host 头区分）
    api = Site(args.api_base or args.base, args.api_host) if args.api_base or args.api_host else None
    via_nginx = bool(args.admin_base or args.admin_host)
    tag = f"[e2e-{time.strftime('%m%d%H%M%S')}]"

    print("== 管理端登录")
    r = admin.call("POST", "/api/admin/auth/login", {"username": args.admin_user, "password": args.admin_password})
    admin.token = ok(r, "管理员登录")["token"]
    check("管理员登录、获取角色", "super_admin" in admin.call("GET", "/api/admin/auth/info")["data"]["roles"])

    created = {"banks": [], "courses": [], "chapters": [], "questions": []}
    user_id = None
    try:
        print("== 准备测试数据（管理端接口）")
        module_id = ok(admin.call("GET", "/api/admin/modules"), "模块")[0]["id"]
        bank = ok(admin.call("POST", "/api/admin/banks", {"moduleId": module_id, "name": f"{tag} 科目型", "type": 1}), "题库")
        created["banks"].append(bank)
        course = ok(admin.call("POST", "/api/admin/courses", {"bankId": bank, "name": f"{tag} 科目"}), "科目")
        created["courses"].append(course)
        chapter = ok(admin.call("POST", "/api/admin/chapters", {"bankId": bank, "courseId": course, "name": f"{tag} 第一章"}), "章节")
        created["chapters"].append(chapter)
        paper_bank = ok(admin.call("POST", "/api/admin/banks", {"moduleId": module_id, "name": f"{tag} 试卷型", "type": 2}), "试卷题库")
        created["banks"].append(paper_bank)
        paper = ok(admin.call("POST", "/api/admin/chapters", {"bankId": paper_bank, "name": f"{tag} 模拟卷", "timeLimit": 60, "totalScore": 10}), "试卷")
        created["chapters"].append(paper)

        img = admin.upload("/api/admin/files/upload", "e2e.png", png(), "image/png")
        check("上传图片", img.get("code") == 0, img)
        img_url = img["data"]["url"]
        abcd = [{"content": c} for c in ("甲", "乙", "丙", "丁")]
        qs = [
            (chapter, {"type": 1, "stem": f"<p>{tag} 单选</p><img src=\"{img_url}\">", "options": abcd, "answer": "B", "analysis": "<p>选 B</p>"}),
            (chapter, {"type": 2, "stem": f"<p>{tag} 多选</p>", "options": abcd, "answer": "ACD"}),
            (chapter, {"type": 3, "stem": f"<p>{tag} 判断</p>", "answer": "对"}),
            (paper, {"type": 1, "stem": f"<p>{tag} 卷-单选</p>", "options": abcd, "answer": "A", "score": 4}),
            (paper, {"type": 2, "stem": f"<p>{tag} 卷-多选</p>", "options": abcd, "answer": "BC", "score": 6}),
            (paper, {"type": 4, "stem": f"<p>{tag} 卷-主观</p>", "analysis": "<p>参考答案</p>", "score": 0}),
        ]
        ids = []
        for ch, q in qs:
            ids.append(ok(admin.call("POST", "/api/admin/questions", {"chapterId": ch, **q}), "题目"))
        created["questions"] = ids
        q_single, q_multi, q_judge, p_single, p_multi, p_subj = ids
        check("XSS 过滤", "<script" not in admin.call("GET", f"/api/admin/questions/{q_single}")["data"]["stem"])
        status, headers, _ = admin.raw("GET", "/api/admin/questions/import-template")
        check("下载导入模板", status == 200 and "spreadsheetml" in headers.get("Content-Type", ""), status)

        print("== 静态资源与站点隔离")
        status, headers, body = app.raw("GET", img_url)
        check("用户端可访问上传的图片", status == 200 and body.startswith(b"\x89PNG"), status)
        if via_nginx:
            status, _, _ = app.raw("GET", "/uploads/../../etc/passwd")
            check("上传目录不能路径穿越", status in (400, 404), status)
            status, _, _ = app.raw("GET", "/uploads/evil.html")
            check("上传目录只放行图片", status == 404, status)
            check("用户端域名访问不到管理接口（404）", app.raw("GET", "/api/admin/auth/info")[0] == 404)
            check("管理端域名访问不到用户端接口（404）", admin.raw("GET", "/api/app/home")[0] == 404)
            status, _, body = app.raw("GET", "/")
            check("用户端首页", status == 200 and b"<div id=\"app\"" in body, status)
            status, headers, body = admin.raw("GET", "/content/questions")
            check("管理端 history 路由回落 index.html", status == 200 and b"<div id=\"app\">" in body, status)
            check("管理端页面带安全头（防点击劫持、nosniff）",
                  headers.get("X-Frame-Options") == "DENY" and headers.get("X-Content-Type-Options") == "nosniff", dict(headers))
            if admin.base.startswith("https"):
                check("HTTPS 带 HSTS", "max-age" in (headers.get("Strict-Transport-Security") or ""), dict(headers))
            status, headers, _ = app.raw("GET", img_url)
            check("图片带缓存和 nosniff", "max-age" in (headers.get("Cache-Control") or "")
                  and headers.get("X-Content-Type-Options") == "nosniff", dict(headers))
            status, _, body = app.raw("GET", "/actuator/health")
            check("健康检查端点不对外", b'"UP"' not in body, status)
            if api:
                check("小程序域名只开放 /api/app", api.raw("GET", "/")[0] == 404 and api.call("GET", "/api/app/home")["code"] == 0)

        print("== 用户注册与首页")
        phone = "199" + "".join(random.choice("0123456789") for _ in range(8))
        r = app.call("POST", "/api/app/auth/register", {"phone": phone, "password": "e2e-pass", "nickname": "回归测试"})
        app.token = ok(r, "注册")["token"]
        home = app.call("GET", "/api/app/home")["data"]
        check("首页模块", any(m["id"] == module_id for m in home["modules"]))
        chapters = app.call("GET", f"/api/app/banks/{bank}/chapters?courseId={course}")["data"]
        check("章节题量 3", chapters[0]["questionCount"] == 3, chapters)

        print("== 练习模式")
        s = app.call("POST", "/api/app/sessions", {"chapterId": chapter, "mode": 1})["data"]
        check("开始练习", s["questionIds"] == [q_single, q_multi, q_judge], s)
        items = app.call("GET", f"/api/app/sessions/{s['id']}/questions?ids={q_single}")["data"]
        check("作答前不返回答案", "answer" not in items[0])
        r = app.call("POST", f"/api/app/sessions/{s['id']}/answers", {"questionId": q_single, "answer": "A"})["data"]
        check("答错返回正确答案", r["correct"] is False and r["answer"] == "B", r)
        r = app.call("POST", f"/api/app/sessions/{s['id']}/answers", {"questionId": q_multi, "answer": "DCA"})["data"]
        check("多选乱序判对", r["correct"] is True, r)
        app.call("POST", f"/api/app/sessions/{s['id']}/answers", {"questionId": q_judge, "answer": "A"})
        res = app.call("POST", f"/api/app/sessions/{s['id']}/submit")["data"]
        check("练习结果 3 题对 2", res["correct"] == 2 and res["wrongQuestionIds"] == [q_single], res)
        ch = app.call("GET", f"/api/app/banks/{bank}/chapters?courseId={course}")["data"][0]
        check("章节进度", ch["doneCount"] == 3 and ch["correctCount"] == 2, ch)

        print("== 考试模式")
        e = app.call("POST", "/api/app/sessions", {"chapterId": paper, "mode": 3})["data"]
        check("开考计时", e["timeLimit"] == 60 and e["remainingSeconds"] > 3500, e)
        r = app.call("POST", f"/api/app/sessions/{e['id']}/answers", {"questionId": p_single, "answer": "A"})["data"]
        check("考试作答不公布对错", r.get("correct") is None, r)
        app.call("POST", f"/api/app/sessions/{e['id']}/answers", {"questionId": p_multi, "answer": "B"})
        res = app.call("POST", f"/api/app/sessions/{e['id']}/submit")["data"]
        check("交卷得 4 分", res["score"] == 4 and res["totalScore"] == 10 and res["subjectiveCount"] == 1, res)

        print("== 错题集 / 收藏集")
        groups = app.call("GET", "/api/app/wrongs/groups")["data"]
        check("错题集 2 题", sum(g["count"] for g in groups) == 2, groups)
        w = app.call("POST", "/api/app/wrongs/practice", {"bankId": bank})["data"]
        check("错题重练", w["mode"] == 4 and w["questionIds"] == [q_single], w)
        app.call("POST", f"/api/app/sessions/{w['id']}/answers", {"questionId": q_single, "answer": "B"})
        app.call("POST", f"/api/app/sessions/{w['id']}/submit")
        check("收藏", app.call("POST", f"/api/app/favorites/{p_subj}")["code"] == 0)
        f = app.call("POST", "/api/app/favorites/practice", {})["data"]
        check("收藏练习", f["mode"] == 5 and f["questionIds"] == [p_subj], f)
        check("移出错题", app.call("DELETE", f"/api/app/wrongs/{p_multi}")["code"] == 0)

        print("== 统计与反馈")
        st = app.call("GET", "/api/app/user/stats")["data"]
        check("统计", st["answerTotal"] == 6 and st["favoriteCount"] == 1 and st["wrongCount"] == 1 and st["streakDays"] == 1, st)
        fid = app.call("POST", "/api/app/feedback", {"type": 1, "questionId": q_single, "content": f"{tag} 纠错"})["data"]
        check("提交纠错", isinstance(fid, int))
        check("管理端回复", admin.call("PUT", f"/api/admin/feedback/{fid}", {"reply": "已处理", "status": 1})["code"] == 0)
        mine = app.call("GET", "/api/app/feedback/mine")["data"]["list"]
        check("用户看到回复", mine[0]["reply"] == "已处理", mine)

        print("== 权限")
        saved, admin.token = admin.token, app.token
        check("用户 token 访问管理接口 → 401", admin.call("GET", "/api/admin/questions")["code"] == 401)
        admin.token = saved
        user_id = admin.call("GET", f"/api/admin/app-users?keyword={phone}")["data"]["list"][0]["id"]
    finally:
        print("== 清理测试数据")
        if created["questions"]:
            admin.call("POST", "/api/admin/questions/batch-delete", {"ids": created["questions"]})
        for c in created["chapters"]:
            admin.call("DELETE", f"/api/admin/chapters/{c}")
        for c in created["courses"]:
            admin.call("DELETE", f"/api/admin/courses/{c}")
        for b in created["banks"]:
            admin.call("DELETE", f"/api/admin/banks/{b}")
        left = [b for b in admin.call("GET", "/api/admin/banks?keyword=" + urllib.parse.quote(tag))["data"]["list"]]
        check("测试题库已删除", not left, left)
        if user_id:
            check("测试用户已禁用", admin.call("PUT", f"/api/admin/app-users/{user_id}/status", {"status": 0})["code"] == 0)

    print(f"\n通过 {passed}，失败 {failed}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
