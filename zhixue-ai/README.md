# zhixue-ai 工程骨架说明

智学领航 AI 服务 · FastAPI 单应用三路由（规划书 2.1 节阶段 A，3 个部署单元中的 Python 侧）。

## 结构

```
zhixue-ai/
├── requirements.txt   依赖清单（PaddleOCR 按平台单独安装）
├── .env.example       环境变量样例（复制为 .env 后填真实密钥，勿提交仓库）
└── app/
    ├── main.py        入口：三路由挂载 + /health 心跳
    ├── config.py      pydantic-settings 环境变量配置
    ├── schemas.py     Pydantic 模型（与接口契约文档一一对应）
    └── routers/
        ├── ocr.py     /api/ocr   PaddleOCR 任务表轮询（契约 2.2 节）
        ├── rag.py     /api/rag   检索/Embedding（契约 2.4 节来源追溯）
        └── llm.py     /api/llm   多模型网关 + 合规拦截器（任务 58A）
```

## 运行

```bash
cd zhixue-ai
pip install -r requirements.txt        # PaddleOCR 按平台提示安装
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
# 验证：GET http://localhost:8000/health
# 说明：默认只绑定本机回环（与下方"不对浏览器/外网暴露"口径一致）；
#       如需同组联调，临时改 0.0.0.0 后务必在防火墙限制来源
```

## 与 Java 侧的关系（规划书硬约束）

Java 不直接调用大模型。diagnosis 模块经 HTTP 网关（`zhixue.ai.base-url` = localhost:8000）调用本服务三路由；本服务不对外网暴露端口（部署方案 1 节拓扑）。

## TODO 清单

- OCR：接 PaddleOCR 真实识别 + 业务库任务表（P0）
- RAG：numpy VectorIndex 检索 + bge-small-zh Embedding（P0/P1）
- LLM：DeepSeek/GLM 双通道 + 超时降级（P0）
- 合规拦截词表扩展 + Prompt 注入防御（P0 任务 58A）
