"""zhixue-ai 入口：FastAPI 单应用、三路由（规划书 2.1 节 阶段 A 架构）。"""
from fastapi import FastAPI

from app.routers import llm, ocr, rag

app = FastAPI(
    title="zhixue-ai",
    description="智学领航 AI 服务网关：OCR / RAG / LLM（含合规拦截）",
    version="0.1.0",
)

# 三路由前缀与架构文档一致：/api/ocr、/api/rag、/api/llm
app.include_router(ocr.router, prefix="/api/ocr", tags=["ocr"])
app.include_router(rag.router, prefix="/api/rag", tags=["rag"])
app.include_router(llm.router, prefix="/api/llm", tags=["llm"])


@app.get("/health")
async def health() -> dict:
    """心跳：Java 侧调用前健康检查（部署方案 3 节启动顺序依据）。"""
    return {"status": "UP", "service": "zhixue-ai", "version": "0.1.0"}


# 说明：OCR 长任务按规划书约定走 FastAPI BackgroundTasks + 业务库任务表轮询，
# 不引入 Celery（决策：砍掉 Celery，简单异步用任务表轮询）。