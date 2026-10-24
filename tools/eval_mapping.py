#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""映射准确率评测（对应 06-测试验收计划 DQ-01 / G3 门禁：30 题映射 top-1 >= 70%）。

用法：
    python tools/eval_mapping.py                       # 默认探测本机 8080，自动尝试已知端点
    python tools/eval_mapping.py --endpoint /api/diagnosis/kp/match
    python tools/eval_mapping.py --token <satoken值> --out last_run.json

安全边界（本地开发工具）：
- 仅 http/https 协议；目标主机默认仅允许 127.0.0.1 / localhost / ::1，
  如需测局域网服务器用 --allow-host 显式放行；不跟随 HTTP 重定向；
- --cases / --out 拒绝包含 .. 路径段，且 realpath 归一化后必须位于
  脚本所在 tools/testdata 目录内。

特性：
- 端点自适应：默认依次探测 /api/diagnosis/map、/api/diagnosis/kp/match、
  /api/diagnosis/analyze，以第一个返回 200 的为准（实现方命名漂移时不阻塞评测）。
- 响应宽容解析：兼容 candidates/results/list 等字段名与 kpId/kp_id 等键名，
  识别 decision（AUTO/CONFIRM/FAILED），便于契约对齐前先出数据。
- 请求兼容：同时携带 text / questionText / courseId / topK，Jackson 默认忽略未知字段。
仅用标准库，无第三方依赖。
"""
import argparse
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path

CANDIDATE_ENDPOINTS = [
    "/api/diagnosis/map",
    "/api/diagnosis/kp/match",
    "/api/diagnosis/analyze",
]
DEFAULT_ALLOWED_HOSTS = {"127.0.0.1", "localhost", "::1"}
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "testdata")


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


OPENER = urllib.request.build_opener(_NoRedirect)


def validate_base_url(base_url, allow_hosts):
    """校验协议与目标主机，返回规范化 origin。"""
    parsed = urllib.parse.urlparse(base_url)
    if parsed.scheme not in ("http", "https"):
        raise SystemExit("[安全] 仅允许 http/https")
    if parsed.username or parsed.password:
        raise SystemExit("[安全] 不允许 URL 携带用户凭证")
    host = (parsed.hostname or "").lower()
    if host not in {h.lower() for h in allow_hosts}:
        raise SystemExit("[安全] 主机 %r 不在白名单 %s；用 --allow-host 显式放行"
                         % (host, sorted(allow_hosts)))
    port = ":%d" % parsed.port if parsed.port else ""
    return parsed.scheme + "://" + parsed.hostname + port


def safe_path(path):
    """拒绝 .. 路径段；realpath 后必须位于 DATA_DIR 内。"""
    if ".." in os.path.normpath(path).replace("\\\\", "/").split("/"):
        raise SystemExit("[安全] 路径不允许包含 ..: " + path)
    resolved = os.path.realpath(os.path.abspath(path))
    root = os.path.realpath(DATA_DIR)
    if resolved != root and not resolved.startswith(root + os.sep):
        raise SystemExit("[安全] 路径必须位于 %s 内: %s" % (root, path))
    return resolved


def post_json(url, payload, token, timeout):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("satoken", token)
    try:
        with OPENER.open(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, {"_http_error": e.read().decode("utf-8", "replace")[:300]}
    except Exception as e:
        return None, {"_conn_error": str(e)}


def extract_kps(body):
    """宽容解析：返回 (按序 kpId 列表, decision)。"""
    kps, decision = [], None

    def take_kp(item):
        if isinstance(item, dict):
            return (item.get("kpId") or item.get("kp_id")
                    or item.get("kp") or item.get("id"))
        if isinstance(item, str):
            return item
        return None

    def walk(node):
        nonlocal decision
        if isinstance(node, dict):
            for key in ("decision", "matchDecision", "match_decision"):
                if isinstance(node.get(key), str):
                    decision = decision or node[key]
            for key in ("top1", "top", "best", "matched"):
                kid = take_kp(node.get(key))
                if kid and kid not in kps:
                    kps.insert(0, kid)
            for key in ("candidates", "results", "list", "records",
                        "matches", "kps", "topK", "data"):
                v = node.get(key)
                if isinstance(v, list):
                    for item in v:
                        kid = take_kp(item)
                        if kid and kid not in kps:
                            kps.append(kid)
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    walk(body)
    return kps, decision


def main():
    ap = argparse.ArgumentParser(description="映射准确率评测（本地开发工具）")
    ap.add_argument("--cases", default=os.path.join(DATA_DIR, "mapping_cases.csv"))
    ap.add_argument("--base-url", default="http://127.0.0.1:8080")
    ap.add_argument("--endpoint", default=None, help="指定映射端点；缺省自动探测")
    ap.add_argument("--token", default=None, help="satoken 请求头的值")
    ap.add_argument("--allow-host", action="append", default=[],
                    help="额外放行的目标主机，可多次使用（默认仅环回地址）")
    ap.add_argument("--course-id", type=int, default=1)
    ap.add_argument("--timeout", type=float, default=10.0)
    ap.add_argument("--limit", type=int, default=0, help="只跑前 N 题（0=全部）")
    ap.add_argument("--out", default="last_mapping_eval.json",
                    help="报告文件名（固定写入 tools/testdata/，不支持子目录与绝对路径）")
    args = ap.parse_args()

    base = validate_base_url(args.base_url, DEFAULT_ALLOWED_HOSTS | set(args.allow_host))
    cases_path = safe_path(args.cases)
    # 路径安全收口：报告路径为纯字面量（相对仓库根运行），--out 仅提示不再参与构造
    if args.out != "last_mapping_eval.json":
        print("[提示] --out 已固定为 tools/testdata/last_mapping_eval.json")
    out_path = "tools/testdata/last_mapping_eval.json"

    with open(cases_path, encoding="utf-8-sig") as f:
        cases = list(csv.DictReader(f))
    if args.limit:
        cases = cases[: args.limit]
    if not cases:
        print("标注集为空：", cases_path)
        return 2

    endpoint = args.endpoint
    probe_status = None
    if endpoint is None:
        for cand in CANDIDATE_ENDPOINTS:
            st, body = post_json(
                base + cand,
                {"text": "probe", "questionText": "probe",
                 "courseId": args.course_id, "topN": 5, "topK": 5},
                args.token, args.timeout)
            probe_status = st
            if st == 200 and isinstance(body, dict) and body.get("code") == 0:
                endpoint = cand
                break
            print("[probe] %s -> HTTP %s %s" % (cand, st, body.get("_http_error", "")[:80]))
        if endpoint is None:
            print("\n无法找到可用端点。诊断：")
            if probe_status is None:
                print("  - 连接失败：确认后端已启动（" + base + "）")
            elif probe_status in (401, 403):
                print("  - 接口存在但需要登录：用 --token 传入 satoken 值")
            else:
                print("  - 候选端点均未返回 200；若实现刚改名，用 --endpoint 显式指定")
            return 1
    print("[endpoint] 使用 " + endpoint)

    results, skipped = [], []
    for c in cases:
        payload = {"text": c["question_text"], "questionText": c["question_text"],
                   "courseId": args.course_id, "topN": 10, "topK": 10}
        st, body = post_json(base + endpoint, payload, args.token, args.timeout)
        row = {"case_id": c["case_id"], "true_kp": c["true_kp_id"], "http": st}
        biz = body.get("code") if isinstance(body, dict) else None
        if st != 200 or biz != 0:
            row["status"] = "BIZ_%s" % biz if biz is not None else (("HTTP_%s" % st) if st else "CONN_FAIL")
            row["status"] = ("HTTP_%s" % st) if st else "CONN_FAIL"
            skipped.append(row)
            results.append(row)
            continue
        kps, decision = extract_kps(body)
        hit1 = bool(kps) and kps[0] == c["true_kp_id"]
        hit3 = c["true_kp_id"] in (kps[:3] if kps else [])
        row.update({"status": "OK", "predicted": kps[:3], "decision": decision,
                    "top1_hit": hit1, "top3_hit": hit3})
        results.append(row)

    ok = [r for r in results if r["status"] == "OK"]
    n_ok = len(ok)
    n_top1 = sum(1 for r in ok if r["top1_hit"])
    n_top3 = sum(1 for r in ok if r["top3_hit"])
    n_auto = sum(1 for r in ok if r.get("decision") == "AUTO")

    def per_group(keyfn):
        agg = defaultdict(lambda: [0, 0])
        for r in ok:
            g = keyfn(r)
            agg[g][1] += 1
            agg[g][0] += 1 if r["top1_hit"] else 0
        return {k: {"hit": v[0], "total": v[1]} for k, v in sorted(agg.items())}

    report = {
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "endpoint": endpoint, "cases_file": cases_path,
        "n_cases": len(cases), "n_ok": n_ok, "n_failed": len(skipped),
        "top1_hits": n_top1, "top3_hits": n_top3,
        "top1_accuracy": round(n_top1 / n_ok, 4) if n_ok else None,
        "top3_accuracy": round(n_top3 / n_ok, 4) if n_ok else None,
        "auto_rate": round(n_auto / n_ok, 4) if n_ok else None,
        "by_kp": per_group(lambda r: r["true_kp"]),
        "failed_cases": skipped,
    }

    # 路径安全收口：write_text 替代 open(w)
    Path(out_path).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print("\n===== 映射评测（DQ-01） =====")
    print("端点: %s  有效用例: %d/%d  失败: %d" % (endpoint, n_ok, len(cases), len(skipped)))
    if n_ok:
        print("top-1 命中: %d/%d = %.1f%%   （G3 门禁 >= 70%%）"
              % (n_top1, n_ok, report["top1_accuracy"] * 100))
        print("top-3 命中: %d/%d = %.1f%%" % (n_top3, n_ok, report["top3_accuracy"] * 100))
        if any(r.get("decision") for r in ok):
            print("AUTO 占比: %.1f%%" % (report["auto_rate"] * 100))
        print("分知识点:", json.dumps(report["by_kp"], ensure_ascii=False))
    if skipped:
        print("失败用例:", [(r["case_id"], r["status"]) for r in skipped][:10])
    print("报告已写入:", out_path)

    if not n_ok:
        verdict = "BLOCKED"
    elif n_top1 / n_ok >= 0.7:
        verdict = "PASS"
    else:
        verdict = "NEED_DATA"
    print("G3/DQ-01 结论: " + verdict)
    return 0 if verdict == "PASS" else (1 if verdict == "BLOCKED" else 3)


if __name__ == "__main__":
    sys.exit(main())
