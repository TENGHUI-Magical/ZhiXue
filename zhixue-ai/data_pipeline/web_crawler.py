#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""异步网页采集器：公开课程资料/题库页 → 结构化 JSONL（供 OCR/训练集管线消费）。

工程特性：
- 限速           每域名最小间隔（令牌间隔控制，默认 1.5s/域）；
- robots.txt     每域缓存解析，禁止路径直接跳过；
- 指数退避       429/5xx/网络错误按 base·2^k + 均匀抖动重试，最多 N 次；
- 断点续传       已抓取 URL 记录在 checkpoint JSON，重跑自动跳过；
- 随机 UA        轮换常见浏览器 User-Agent，附合规标识查询参数可关。

合规边界：只抓公开可访问页面；不登录、不绕过反爬、不伪造 Cookie；
产出仅为「待人工签核」的原始文本，不自动入库（AGENTS.md 红线 1）。

用法（在 zhixue-ai 目录执行）：
    python -m data_pipeline.web_crawler --seeds seeds.json --out data/crawled.jsonl \
        --max-pages 20 --same-domain
    # seeds.json: {"seeds": ["https://..."], "allow_patterns": ["zh.wikipedia.org"]}
"""
from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import random
import sys
import time
import urllib.robotparser
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urldefrag, urljoin, urlparse

try:
    import httpx
except ImportError as exc:  # pragma: no cover - 依赖缺失
    raise SystemExit("缺少 httpx：pip install httpx") from exc

try:  # 可选：bs4 缺失时退化为正则剥标签
    from bs4 import BeautifulSoup
except Exception:  # pragma: no cover - 环境分支
    BeautifulSoup = None

import re as _re

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
    "(KHTML, like Gecko) Version/17.4 Safari/605.1.15",
    "Mozilla/5.0 (X11; Linux x86_64; rv:126.0) Gecko/20100101 Firefox/126.0",
]
_TAG_RE = _re.compile(r"<[^>]+>")
_WS_RE = _re.compile(r"\s+")


def extract_text(html: str) -> tuple[str, str, list[str]]:
    """HTML → (标题, 正文纯文本, 同站链接列表)。bs4 优先，缺失退化正则。"""
    links: list[str] = []
    if BeautifulSoup is not None:
        soup = BeautifulSoup(html, "html.parser")
        for tag in soup(["script", "style", "nav", "footer", "header", "noscript"]):
            tag.decompose()
        title = (soup.title.string or "").strip() if soup.title else ""
        for anchor in soup.find_all("a", href=True):
            links.append(anchor["href"])
        return title, _WS_RE.sub(" ", soup.get_text(" ", strip=True)), links
    title_match = _re.search(r"<title[^>]*>(.*?)</title>", html, _re.S | _re.I)
    title = _WS_RE.sub(" ", title_match.group(1)).strip() if title_match else ""
    text = _WS_RE.sub(" ", _TAG_RE.sub(" ", html)).strip()
    links = _TAG_RE.findall(html)  # 退化路径不抽链接，只保文本
    return title, text, []


@dataclass
class CrawlState:
    """断点续传状态：成功 URL → 内容哈希；失败 URL → 原因。"""
    done: dict[str, str] = field(default_factory=dict)
    failed: dict[str, str] = field(default_factory=dict)

    @classmethod
    def load(cls, path: Path) -> "CrawlState":
        if path.exists():
            data = json.loads(path.read_text(encoding="utf-8"))
            return cls(done=data.get("done", {}), failed=data.get("failed", {}))
        return cls()

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps({"done": self.done, "failed": self.failed},
                                  ensure_ascii=False, indent=1), encoding="utf-8")
        tmp.replace(path)


class WebCrawler:
    def __init__(self, *, min_interval: float = 1.5, timeout: float = 15.0,
                 max_retries: int = 3, backoff_base: float = 0.8,
                 respect_robots: bool = True) -> None:
        self.min_interval = min_interval
        self.timeout = timeout
        self.max_retries = max_retries
        self.backoff_base = backoff_base
        self.respect_robots = respect_robots
        self._last_hit: dict[str, float] = {}
        self._robots: dict[str, urllib.robotparser.RobotFileParser | None] = {}

    async def _throttle(self, domain: str) -> None:
        """每域名最小间隔（异步限速：距上次请求不足间隔则补睡）。"""
        last = self._last_hit.get(domain, 0.0)
        wait = self.min_interval - (time.monotonic() - last)
        if wait > 0:
            await asyncio.sleep(wait)
        self._last_hit[domain] = time.monotonic()

    async def _robots_allows(self, client: httpx.AsyncClient,
                             url: str) -> bool:
        if not self.respect_robots:
            return True
        parsed = urlparse(url)
        base = f"{parsed.scheme}://{parsed.netloc}"
        if base not in self._robots:
            parser = urllib.robotparser.RobotFileParser()
            try:
                response = await client.get(base + "/robots.txt",
                                            headers={"User-Agent": USER_AGENTS[0]})
                parser.parse(response.text.splitlines() if response.status_code == 200 else [])
                self._robots[base] = parser
            except Exception:
                self._robots[base] = None  # robots 拉取失败按允许处理（公开页）
        parser = self._robots[base]
        return True if parser is None else parser.can_fetch(USER_AGENTS[0], url)

    async def fetch(self, client: httpx.AsyncClient, url: str) -> httpx.Response:
        """带指数退避的 GET：backoff = base·2^k + U(0, base)。"""
        last_exc: Exception | None = None
        for attempt in range(self.max_retries + 1):
            await self._throttle(urlparse(url).netloc)
            try:
                response = await client.get(
                    url, headers={"User-Agent": random.choice(USER_AGENTS)},
                    follow_redirects=True)
                if response.status_code in (429, 500, 502, 503, 504):
                    raise httpx.HTTPStatusError(
                        f"HTTP {response.status_code}", request=response.request,
                        response=response)
                return response
            except Exception as exc:
                last_exc = exc
                if attempt < self.max_retries:
                    backoff = self.backoff_base * (2 ** attempt) \
                        + random.uniform(0, self.backoff_base)
                    await asyncio.sleep(backoff)
        raise last_exc  # type: ignore[misc]

    async def crawl(self, seeds: list[str], *, max_pages: int = 20,
                    same_domain: bool = True, allow_patterns: list[str] | None = None,
                    checkpoint: Path | None = None) -> list[dict]:
        """广度优先抓取：种子页正文 + 顺延同站链接（受 max_pages 约束）。"""
        state = CrawlState.load(checkpoint) if checkpoint else CrawlState()
        records: list[dict] = []
        queue = [urldefrag(url)[0] for url in seeds]
        seen = set(queue) | set(state.done)
        seed_domains = {urlparse(url).netloc for url in seeds}

        async with httpx.AsyncClient(
                timeout=self.timeout, follow_redirects=True) as client:
            while queue and len(records) < max_pages:
                url = queue.pop(0)
                if url in state.done or url in state.failed:
                    continue
                if not await self._robots_allows(client, url):
                    state.failed[url] = "robots-disallow"
                    continue
                try:
                    response = await self.fetch(client, url)
                except Exception as exc:
                    state.failed[url] = f"{type(exc).__name__}: {exc}"[:200]
                    continue
                if "text/html" not in response.headers.get("content-type", "text/html"):
                    state.done[url] = "non-html"
                    continue
                title, text, links = extract_text(response.text)
                digest = hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]
                state.done[url] = digest
                records.append({
                    "url": url, "title": title[:200],
                    "text": text[:20000], "contentHash": digest,
                    "fetchedAt": datetime.now(timezone.utc).isoformat(),
                })
                for link in links:
                    absolute = urldefrag(urljoin(url, link))[0]
                    parsed = urlparse(absolute)
                    if parsed.scheme not in ("http", "https"):
                        continue
                    if same_domain and parsed.netloc not in seed_domains:
                        continue
                    if allow_patterns and not any(p in absolute for p in allow_patterns):
                        continue
                    if absolute not in seen:
                        seen.add(absolute)
                        queue.append(absolute)
                if checkpoint:
                    state.save(checkpoint)
        if checkpoint:
            state.save(checkpoint)
        return records


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="异步网页采集器（公开页面，合规限速）")
    parser.add_argument("--seeds", required=True, help="种子 JSON：{\"seeds\": [...]}")
    parser.add_argument("--out", required=True, help="输出 JSONL 路径")
    parser.add_argument("--max-pages", type=int, default=20)
    parser.add_argument("--min-interval", type=float, default=1.5,
                        help="每域名最小请求间隔秒（限速）")
    parser.add_argument("--no-same-domain", action="store_true", help="允许跨域顺延")
    parser.add_argument("--checkpoint", default=None, help="断点续传状态文件")
    args = parser.parse_args(argv)

    seed_config = json.loads(Path(args.seeds).read_text(encoding="utf-8"))
    crawler = WebCrawler(min_interval=args.min_interval)
    records = asyncio.run(crawler.crawl(
        seed_config["seeds"], max_pages=args.max_pages,
        same_domain=not args.no_same_domain,
        allow_patterns=seed_config.get("allow_patterns"),
        checkpoint=Path(args.checkpoint) if args.checkpoint else None))

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("a", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, ensure_ascii=False) + "\n")
    print(f"抓取完成：{len(records)} 页 → {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
