"""zhixue-ai 入口：FastAPI 单应用、三路由（规划书 2.1 节 阶段 A 架构）。"""
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.config import settings
from app.routers import llm, ocr, rag


@asynccontextmanager
async def lifespan(_app: FastAPI):
    """P1-2：退出时关闭模块级单例 AsyncClient 的连接池。

    索引按需懒加载，无需在启动期预热；shutdown 时释放连接，避免事件循环
    关闭后残留 httpx 连接/线程。
    """
    yield
    from app.services import rag_service

    await rag_service.aclose_http_client()


app = FastAPI(
    title="zhixue-ai",
    description="智学领航 AI 服务网关：OCR / RAG / LLM（含合规拦截）",
    version="0.1.0",
    lifespan=lifespan,
)

# 三路由前缀与架构文档一致：/api/ocr、/api/rag、/api/llm
app.include_router(ocr.router, prefix="/api/ocr", tags=["ocr"])
app.include_router(rag.router, prefix="/api/rag", tags=["rag"])
app.include_router(llm.router, prefix="/api/llm", tags=["llm"])


@app.get("/health")
async def health() -> dict:
    """心跳：Java 侧调用前健康检查（部署方案 3 节启动顺序依据）。
    P1 修复：反映真实组件状态（索引/密钥/配置），不再静态恒 UP。"""
    from app.services import vector_index

    # 检查向量索引状态
    try:
        index_ok = vector_index.get_index_status() if hasattr(vector_index, 'get_index_status') else True
    except Exception:
        index_ok = False

    # 检查密钥配置（只检查是否配置，不输出值）
    has_deepseek = bool(settings.deepseek_api_key)
    has_glm = bool(settings.glm_api_key)
    has_internal_token = bool(settings.internal_token)

    status = "UP" if index_ok else "DEGRADED"
    return {
        "status": status,
        "service": "zhixue-ai",
        "version": "0.1.0",
        "components": {
            "vectorIndex": "UP" if index_ok else "DOWN",
            "deepseekKey": "configured" if has_deepseek else "missing",
            "glmKey": "configured" if has_glm else "missing",
            "internalToken": "configured" if has_internal_token else "missing",
        },
    }


# 说明：OCR 长任务按规划书约定走 FastAPI BackgroundTasks + 业务库任务表轮询，
# 不引入 Celery（决策：砍掉 Celery，简单异步用任务表轮询）。