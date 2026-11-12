"""/api/ocr/recognize 路由单测（Python_04 P1-1 / P1-4）。

覆盖：空文件 / 超限 / 非法 content_type / bad magic / 损坏图 / 内部鉴权。
deps.require_internal 需要 X-Internal-Token：用 monkeypatch 设置 settings.internal_token 并带该头。
损坏图用例只在 PIL 解码阶段失败，不会真正加载 RapidOCR 引擎。
"""
from __future__ import annotations

import logging

import pytest
from fastapi.testclient import TestClient

from app.config import settings
from app.main import app

TOKEN = "test-internal-token"
HEADERS = {"X-Internal-Token": TOKEN}
PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


@pytest.fixture()
def client(monkeypatch):
    monkeypatch.setattr(settings, "internal_token", TOKEN)
    with TestClient(app) as test_client:
        yield test_client


def _post(client, data: bytes, ctype: str = "image/png", name: str = "x.png"):
    return client.post("/api/ocr/recognize", headers=HEADERS,
                       files={"image": (name, data, ctype)})


def test_ocr_empty_file_is_rejected(client) -> None:
    resp = _post(client, b"")
    assert resp.status_code == 400
    assert resp.json()["detail"] == "图片为空"


def test_ocr_oversize_is_rejected(client, monkeypatch) -> None:
    monkeypatch.setattr(settings, "ocr_max_image_bytes", 1024)
    resp = _post(client, PNG_MAGIC + b"x" * 1100)
    assert resp.status_code == 413
    assert resp.json()["detail"] == "图片超过大小上限"


def test_ocr_illegal_content_type_is_rejected(client) -> None:
    resp = _post(client, PNG_MAGIC + b"rest", ctype="image/gif", name="x.gif")
    assert resp.status_code == 415
    assert resp.json()["detail"] == "不支持的图片类型"


def test_ocr_bad_magic_bytes_are_rejected(client) -> None:
    resp = _post(client, b"this-is-not-an-image")
    assert resp.status_code == 415
    assert resp.json()["detail"] == "图片内容校验失败"


def test_ocr_corrupted_image_returns_generic_422(client) -> None:
    # 合法 PNG 头但内容损坏：通过 magic 校验后在 PIL 解码阶段失败
    resp = _post(client, PNG_MAGIC + b"\x00" * 64)
    assert resp.status_code == 422
    detail = resp.json()["detail"]
    assert detail == "OCR 识别失败，请重试或更换图片"
    # 不得回显底层库异常 / 路径 / 栈片段等内部信息
    for leak in ("UnidentifiedImageError", "cannot identify", "Traceback", "BytesIO", "\n"):
        assert leak not in detail


def test_valid_magic_accepts_jpeg_png_webp() -> None:
    from app.routers.ocr import _valid_magic

    assert _valid_magic(b"\xff\xd8\xff" + b"rest")
    assert _valid_magic(PNG_MAGIC + b"rest")
    assert _valid_magic(b"RIFF" + b"\x00\x00\x00\x00" + b"WEBP" + b"rest")
    assert not _valid_magic(b"GIF89a" + b"rest")
    assert not _valid_magic(b"RIFF\x00\x00\x00\x00XXXXrest")


def test_ocr_still_requires_internal_token(monkeypatch) -> None:
    """主审已加的内部鉴权不得被破坏。"""
    monkeypatch.setattr(settings, "internal_token", TOKEN)
    with TestClient(app) as test_client:
        resp = test_client.post("/api/ocr/recognize",
                                files={"image": ("x.png", PNG_MAGIC, "image/png")})
    assert resp.status_code == 401


def test_ocr_trace_id_header_is_echoed_in_logs(client, caplog) -> None:
    """docs/01 §1.2：X-Trace-Id 可选透传，失败日志须带 traceId，且不影响 422 契约。"""
    broken = PNG_MAGIC + b"\x00" * 64
    with caplog.at_level(logging.WARNING):
        resp = client.post("/api/ocr/recognize",
                           headers={**HEADERS, "X-Trace-Id": "t-ocr-123"},
                           files={"image": ("x.png", broken, "image/png")})
    assert resp.status_code == 422
    assert resp.json()["detail"] == "OCR 识别失败，请重试或更换图片"
    assert "traceId=t-ocr-123" in caplog.text


def test_ocr_without_trace_id_header_still_works(client) -> None:
    """可选 header 缺省时不得改变既有行为（仍按损坏图 422 处理）。"""
    resp = _post(client, PNG_MAGIC + b"\x00" * 64)
    assert resp.status_code == 422
