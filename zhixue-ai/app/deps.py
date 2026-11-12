"""FastAPI 依赖：内部服务鉴权（P0 修复）。

Java 侧调用 Python 三路由时必须携带 X-Internal-Token 请求头，
token 由环境变量 INTERNAL_TOKEN 配置；为空时拒绝所有请求（fail-closed）。
"""
from __future__ import annotations

from fastapi import Header, HTTPException

from app.config import settings


async def require_internal(x_internal_token: str = Header(default="", alias="X-Internal-Token")) -> None:
    """校验内部服务调用凭据。token 未配置或不匹配时拒绝（401）。"""
    if not settings.internal_token:
        raise HTTPException(status_code=503, detail="INTERNAL_TOKEN 未配置，服务拒绝所有内部调用")
    if x_internal_token != settings.internal_token:
        raise HTTPException(status_code=401, detail="无效的内部服务凭据")
