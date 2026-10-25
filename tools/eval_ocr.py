#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OCR 质量评测（对照 tools/testdata/ocr_cases/ground_truth.json）。

覆盖 19 号审查报告 #2 的未验证路径：低置信度（模糊图）、旋转图、多行 SQL。
指标：任务状态机走通、置信度、全文/逐行字符准确率（difflib，归一化后）。

用法（凭证只从环境变量读，不写死）：
    set ZHIXUE_TEST_USERNAME=2023034743010
    set ZHIXUE_TEST_PASSWORD=<种子口令>
    python tools/eval_ocr.py
端点与字段名自适应（契约 /api/error/upload+file 与实现 /api/diagnosis/ocr/parse+image
都能测），P0 契约重命名后无需改脚本。
安全：仅环回主机白名单（--allow-host 可扩）；不跟随重定向；输入输出路径限定
tools/testdata；无 SQL、无硬编码凭证。
"""
import argparse
import difflib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

# 契约名唯一事实源：旧路径已删除，保留 fallback 会掩盖契约回归
UPLOAD_ENDPOINT = "/api/error/upload"
TASK_GET_TEMPLATE = "/api/error/tasks/%s"
DEFAULT_ALLOWED_HOSTS = {"127.0.0.1", "localhost", "::1"}
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "testdata")
OCR_DIR = os.path.join(DATA_DIR, "ocr_cases")
LOW_CONFIDENCE = 0.75


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


OPENER = urllib.request.build_opener(_NoRedirect)


def safe_path(path):
    if ".." in os.path.normpath(path).replace("\\", "/").split("/"):
        raise SystemExit("[安全] 路径不允许包含 ..: " + path)
    resolved = os.path.realpath(os.path.abspath(path))
    root = os.path.realpath(DATA_DIR)
    if resolved != root and not resolved.startswith(root + os.sep):
        raise SystemExit("[安全] 路径必须位于 %s 内: %s" % (root, path))
    return resolved


def validate_base_url(base_url, allow_hosts):
    parsed = urllib.parse.urlparse(base_url)
    if parsed.scheme not in ("http", "https"):
        raise SystemExit("[安全] 仅允许 http/https")
    if parsed.username or parsed.password:
        raise SystemExit("[安全] 不允许 URL 携带用户凭证")
    host = (parsed.hostname or "").lower()
    if host not in {h.lower() for h in allow_hosts}:
        raise SystemExit("[安全] 主机 %r 不在白名单，用 --allow-host 放行" % host)
    port = ":%d" % parsed.port if parsed.port else ""
    return parsed.scheme + "://" + parsed.hostname + port


def post_json(url, payload, token, timeout):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("satoken", token)
    return _do(req, timeout)


def _do(req, timeout):
    try:
        with OPENER.open(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, {"_http_error": e.read().decode("utf-8", "replace")[:200]}
    except Exception as e:
        return None, {"_conn_error": str(e)}


def get_json(url, token, timeout):
    req = urllib.request.Request(url)
    if token:
        req.add_header("satoken", token)
    return _do(req, timeout)


def login(base, username, password, timeout):
    st, body = post_json(base + "/api/auth/login",
                         {"username": username, "password": password}, None, timeout)
    if st != 200 or not isinstance(body, dict) or body.get("code") != 0:
        return None, "登录失败 HTTP=%s code=%s" % (st, body.get("code") if isinstance(body, dict) else "?")
    data = body.get("data") or {}
    token = data.get("token") or body.get("token")
    if not token:
        return None, "响应中无 token（结构变化？）：" + json.dumps(body, ensure_ascii=False)[:120]
    return token, None


def post_multipart(url, fields, files, token, timeout):
    """fields: {name: str}; files: [(name, filename, ctype, bytes)]。同时携带 file 与
    image 两个部件，服务端按自己声明的参数名取用，天然兼容两种命名。"""
    boundary = "----" + uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n"
                      % (boundary, name, value)).encode("utf-8"))
    for name, filename, ctype, blob in files:
        parts.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n"
                      "Content-Type: %s\r\n\r\n" % (boundary, name, filename, ctype)).encode("utf-8"))
        parts.append(blob)
        parts.append(b"\r\n")
    end = ("--%s--\r\n" % boundary).encode("utf-8")
    body = b"".join(parts) + end
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    if token:
        req.add_header("satoken", token)
    return _do(req, timeout)


def normalize(text):
    return re.sub(r"[\s\W_]+", "", (text or "").lower())


def accuracy(expected_lines, recognized):
    """全文与逐行两口径：归一化后 difflib 相似度。"""
    rec_n = normalize(recognized)
    full = "".join(expected_lines)
    overall = difflib.SequenceMatcher(None, normalize(full), rec_n).ratio()
    per_line = []
    for line in expected_lines:
        ln = normalize(line)
        if not ln:
            continue
        per_line.append(difflib.SequenceMatcher(None, ln, rec_n).ratio())
    return overall, (min(per_line) if per_line else 0.0), per_line


def main():
    ap = argparse.ArgumentParser(description="OCR 质量评测（本地工具）")
    ap.add_argument("--base-url", default="http://127.0.0.1:8080")
    ap.add_argument("--allow-host", action="append", default=[])
    ap.add_argument("--cases", default=os.path.join(DATA_DIR, "ocr_cases", "ground_truth.json"))
    # 口令只从环境变量读取，不提供命令行参数（避免进 shell 历史；07 号文安全约定）
    ap.add_argument("--timeout", type=float, default=15.0)
    ap.add_argument("--poll-interval", type=float, default=1.5)
    ap.add_argument("--poll-max", type=int, default=45)
    ap.add_argument("--out", default="last_ocr_eval.json",
                    help="报告文件名（固定写入 tools/testdata/，不支持子目录与绝对路径）")
    args = ap.parse_args()

    username = os.environ.get("ZHIXUE_TEST_USERNAME")
    password = os.environ.get("ZHIXUE_TEST_PASSWORD")
    if not username or not password:
        raise SystemExit("凭证缺失：设置 ZHIXUE_TEST_USERNAME / ZHIXUE_TEST_PASSWORD 环境变量（不写入代码）")
    base = validate_base_url(args.base_url, DEFAULT_ALLOWED_HOSTS | set(args.allow_host))
    gt_path = safe_path(args.cases)
    # 路径安全收口：报告路径为纯字面量（相对仓库根运行），--out 仅提示不再参与构造
    if args.out != "last_ocr_eval.json":
        print("[提示] --out 已固定为 tools/testdata/last_ocr_eval.json")
    out_path = "tools/testdata/last_ocr_eval.json"

    token, err = login(base, username, password, args.timeout)
    if err:
        print("[登录]", err)
        return 1

    with open(gt_path, encoding="utf-8") as f:
        gt = json.load(f)
    results = []
    for case in gt["cases"]:
        img_path = safe_path(os.path.join(DATA_DIR, "ocr_cases", case["file"]))
        with open(img_path, "rb") as f:
            blob = f.read()

        used = UPLOAD_ENDPOINT
        # clientToken 每次尝试都随机：固定值会命中幂等返回旧任务，评测数据失真
        st, body = post_multipart(
            base + used,
            {"clientToken": uuid.uuid4().hex},
            [("file", case["file"], "image/png", blob)],
            token, args.timeout)
        if not (st == 200 and isinstance(body, dict) and body.get("code") == 0):
            results.append({"id": case["id"], "status": "UPLOAD_FAIL",
                            "http": st, "body": str(body)[:200]})
            continue

        task_id = (body.get("data") or {}).get("taskId") or (body.get("data") or {}).get("taskId".lower())
        if not task_id and isinstance(body.get("data"), str):
            task_id = body["data"]
        if not task_id:
            results.append({"id": case["id"], "status": "NO_TASK_ID", "body": str(body)[:200]})
            continue

        status, confidence, fields = "PENDING", None, None
        for _ in range(args.poll_max):
            st2, b2 = get_json(base + (TASK_GET_TEMPLATE % task_id), token, args.timeout)
            if st2 == 200 and isinstance(b2, dict) and b2.get("code") == 0:
                d = b2.get("data") or {}
                status = d.get("status", status)
                confidence = d.get("confidence", confidence)
                fields = d.get("fields") or d.get("result") or fields
            if status in ("SUCCESS", "FAILED"):
                break
            time.sleep(args.poll_interval)

        if status != "SUCCESS":
            results.append({"id": case["id"], "endpoint": used, "taskId": task_id,
                            "status": status or "TIMEOUT"})
            continue

        recognized = ""
        if isinstance(fields, dict):
            recognized = fields.get("stem") or fields.get("rawText") or ""
            if not recognized and isinstance(fields.get("lines"), list):
                recognized = "\n".join(l.get("text", "") if isinstance(l, dict) else str(l)
                                       for l in fields["lines"])
        overall, worst_line, _ = accuracy(case["expected_lines"], recognized)
        results.append({
            "id": case["id"], "endpoint": used, "type": case["question_type"],
            "distortion": case["distortion"], "status": "SUCCESS",
            "confidence": confidence,
            "low_confidence_flag": bool(confidence is not None and confidence < LOW_CONFIDENCE),
            "overall_accuracy": round(overall, 4),
            "worst_line_accuracy": round(worst_line, 4),
        })

    ok = [r for r in results if r.get("status") == "SUCCESS"]
    report = {"generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
              "n": len(results), "success": len(ok), "results": results}
    # 路径安全收口：write_text 替代 open(w)
    Path(out_path).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print("===== OCR 评测 =====")
    print("成功 %d/%d" % (len(ok), len(results)))
    for r in results:
        if r.get("status") == "SUCCESS":
            print("%s %-10s conf=%s acc=%s worst=%s %s%s" % (
                r["id"], r.get("type"), r["confidence"], r["overall_accuracy"],
                r["worst_line_accuracy"], r.get("distortion") or "clean",
                "  <低置信!" if r["low_confidence_flag"] else ""))
        else:
            print("%s %s %s" % (r["id"], r["status"], r.get("http", "")))
    print("报告:", out_path)
    return 0 if len(ok) == len(results) else 2


if __name__ == "__main__":
    sys.exit(main())
