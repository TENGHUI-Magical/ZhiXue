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
- 端点固定为 /api/diagnosis/map（契约唯一事实源）；实现若临时改名，用
  --endpoint 显式指定，不做旧路径回退，避免掩盖契约回归。
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
    probe_body = None
    # 探测文本用真实中文题干：纯 ASCII 的 "probe" 与中文语料零二元组交集，
    # 按契约会被 cosine>0 过滤器全部丢掉，得到"零候选"的假阴性。
    probe_text = cases[0]["question_text"]
    if endpoint is None:
        for cand in CANDIDATE_ENDPOINTS:
            st, body = post_json(
                base + cand,
                {"text": probe_text, "questionText": probe_text,
                 "courseId": args.course_id, "topN": 5, "topK": 5},
                args.token, args.timeout)
            probe_status = st
            if st == 200 and isinstance(body, dict) and body.get("code") == 0:
                endpoint = cand
                probe_body = body
                break
            print("[probe] %s -> HTTP %s %s" % (cand, st, body.get("_http_error", "")[:80]))
        if endpoint is None:
            print("\n无法找到可用端点。诊断：")
            if probe_status is None:
                print("  - 连接失败：确认后端已启动（" + base + "）")
            elif probe_status in (401, 403):
                print("  - 接口存在但需要登录：用 --token 传入 satoken 值")
            else:
                print("  - 默认端点未返回 200；确认后端版本，或用 --endpoint 显式指定")
            return 1
    print("[endpoint] 使用 " + endpoint)

    # 修 F3：探测成功但候选为空，说明"管道坏"而不是"算法差"。
    # 零候选的两种已知成因：knowledge_point 未种子化（数据卷已存在时
    # docker-entrypoint-initdb.d 不会重跑）、请求/响应编码不符导致中文零交集。
    probe_kps, probe_decision = extract_kps(probe_body) if probe_body is not None else ([], None)
    if probe_body is not None and not probe_kps:
        print("\n⚠ 端点返回零候选：疑似 DB 未种子化/编码不符")
        print("  - 决策: %s；探测题干: %s" % (probe_decision, probe_text[:40]))
        print("  - 先执行：SELECT COUNT(*) FROM knowledge_point "
              "WHERE course_id=%d AND is_active=1;  （预期 >= 20）" % args.course_id)
        print("  - 若为 0：确认 MySQL 数据卷是否已存在导致种子 SQL 未执行；"
              "若 >0：检查请求/响应编码（Content-Type charset、网关转码）")
        print("  - 结论：管道未通，本次不出算法指标（避免把部署态问题记成算法问题）")
        return 1

    results, skipped = [], []
    for c in cases:
        # 章先知：CSV 带 chapter 列时传入，命中与 docs/05 §1.4 的章过滤口径一致；
        # 不传则走全课程排序（离线基线 V0）。两口径数字不同，报告里注明。
        chapter_id = (c.get("chapter") or "").strip()
        payload = {"text": c["question_text"], "questionText": c["question_text"],
                   "courseId": args.course_id, "topN": 10, "topK": 10}
        if chapter_id.isdigit():
            payload["chapterId"] = int(chapter_id)
        st, body = post_json(base + endpoint, payload, args.token, args.timeout)
        row = {"case_id": c["case_id"], "true_kp": c["true_kp_id"], "http": st}
        biz = body.get("code") if isinstance(body, dict) else None
        if st != 200 or biz != 0:
            # 保留业务码，避免把 code!=0 的响应误记成 HTTP 失败（0.0 事故归因需要）
            row["status"] = ("BIZ_%s" % biz) if biz else (("HTTP_%s" % st) if st else "CONN_FAIL")
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
    # 候选全空的用例数：修 F2/F3，让 0.0 事故可以事后归因到"管道"而不是"算法"
    n_empty = sum(1 for r in ok if not r.get("predicted"))

    def per_group(keyfn):
        agg = defaultdict(lambda: [0, 0])
        for r in ok:
            g = keyfn(r)
            agg[g][1] += 1
            agg[g][0] += 1 if r["top1_hit"] else 0
        return {k: {"hit": v[0], "total": v[1]} for k, v in sorted(agg.items())}

    # 有效性判定：全 0 命中且候选大面积为空，多半是语料未加载/服务未就绪，
    # 不能把 0.0 当成算法准确率引用（2026-10-25 那次就是这种无效产物）。
    invalid_reason = None
    if n_ok and n_top1 == 0 and n_empty >= max(1, n_ok // 2):
        invalid_reason = ("候选大面积为空（%d/%d），疑似知识点语料未加载或服务未就绪；"
                          "本次结果不可作为准确率证据" % (n_empty, n_ok))
    elif n_ok == 0:
        invalid_reason = "全部用例请求失败，无有效样本"

    report = {
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "endpoint": endpoint, "cases_file": cases_path,
        "n_cases": len(cases), "n_ok": n_ok, "n_failed": len(skipped),
        "n_empty_candidates": n_empty,
        "top1_hits": n_top1, "top3_hits": n_top3,
        "top1_accuracy": round(n_top1 / n_ok, 4) if n_ok else None,
        "top3_accuracy": round(n_top3 / n_ok, 4) if n_ok else None,
        "auto_rate": round(n_auto / n_ok, 4) if n_ok else None,
        "chapter_prior": any((c.get("chapter") or "").strip().isdigit() for c in cases),
        "valid": invalid_reason is None,
        "invalid_reason": invalid_reason,
        "by_kp": per_group(lambda r: r["true_kp"]),
        "results": results,
        "failed_cases": skipped,
    }

    # 路径安全收口：write_text 替代 open(w)
    Path(out_path).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print("\n===== 映射评测（DQ-01） =====")
    print("端点: %s  有效用例: %d/%d  失败: %d  候选为空: %d"
          % (endpoint, n_ok, len(cases), len(skipped), n_empty))
    if n_ok:
        print("top-1 命中: %d/%d = %.1f%%   （G3 门禁 >= 70%%）"
              % (n_top1, n_ok, report["top1_accuracy"] * 100))
        print("top-3 命中: %d/%d = %.1f%%" % (n_top3, n_ok, report["top3_accuracy"] * 100))
        if any(r.get("decision") for r in ok):
            print("AUTO 占比: %.1f%%" % (report["auto_rate"] * 100))
        print("分知识点:", json.dumps(report["by_kp"], ensure_ascii=False))
    if invalid_reason:
        print("[无效运行] " + invalid_reason)
        print("可引用证据请改用离线评测：python tools/eval_mapping_offline.py")
    if skipped:
        print("失败用例:", [(r["case_id"], r["status"]) for r in skipped][:10])
    print("报告已写入:", out_path)

    if invalid_reason:
        verdict = "INVALID_RUN"
    elif not n_ok:
        verdict = "BLOCKED"
    elif n_empty == n_ok:
        # 全部用例零候选：不是"算法准确率为 0"，而是管道/数据态坏
        verdict = "PIPELINE_BROKEN"
    elif n_top1 / n_ok >= 0.7:
        verdict = "PASS"
    else:
        verdict = "NEED_DATA"
    print("G3/DQ-01 结论: " + verdict)
    if n_empty:
        print("零候选用例: %d/%d（若为全部，说明 knowledge_point 未种子化或编码不符）"
              % (n_empty, n_ok))
        print("  判定 SQL: SELECT COUNT(*) FROM knowledge_point "
              "WHERE course_id=%d AND is_active=1;" % args.course_id)
    return {"PASS": 0, "BLOCKED": 1, "INVALID_RUN": 1, "NEED_DATA": 3, "PIPELINE_BROKEN": 4}[verdict]


if __name__ == "__main__":
    sys.exit(main())
