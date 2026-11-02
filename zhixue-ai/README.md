# zhixue-ai 工程骨架说明

智学领航 AI 服务 · FastAPI 单应用三路由（规划书 2.1 节阶段 A，3 个部署单元中的 Python 侧）。

## 结构

```
zhixue-ai/
├── requirements.txt   核心依赖（RAG/LLM；不含 OCR）
├── requirements-ocr.txt  OCR 依赖（RapidOCR，单独安装）
├── .env.example       环境变量样例（复制为 .env 后填真实密钥，勿提交仓库）
└── app/
    ├── main.py        入口：三路由挂载 + /health 心跳
    ├── config.py      pydantic-settings 环境变量配置
    ├── schemas.py     Pydantic 模型（与接口契约文档一一对应）
    └── routers/
        ├── ocr.py     /api/ocr   RapidOCR 识别（PaddleOCR 模型的 ONNX 版；契约 2.2 节）
        ├── rag.py     /api/rag   检索/Embedding（契约 2.4 节来源追溯）
        └── llm.py     /api/llm   多模型网关 + 合规拦截器（任务 58A）
```

## 运行

```bash
cd zhixue-ai
pip install -r requirements.txt        # 核心依赖；OCR 另装 requirements-ocr.txt
pip install -r requirements-ocr.txt    # 需要 OCR 时
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
# 验证：GET http://localhost:8000/health
# 说明：默认只绑定本机回环（与下方"不对浏览器/外网暴露"口径一致）；
#       如需同组联调，临时改 0.0.0.0 后务必在防火墙限制来源
```

## RAG 索引（新环境必做）

`data/vector_index/` 是本地生成产物，按 `.gitignore` 规则不随代码分发。**新环境克隆代码、重建演示库或更新知识点 `concept_text` 后，必须重建索引**；否则 `/api/rag/retrieve` 会为空或继续使用旧切片，表现为“检索增强失效”。先配置 MySQL DSN 并启动本服务，再执行：

```bash
python tools/build_demo_index.py --base-url http://127.0.0.1:8000 --index-dir data/vector_index
```

## 与 Java 侧的关系（规划书硬约束）

Java 不直接调用大模型。diagnosis 模块经 HTTP 网关（`zhixue.ai.base-url` = localhost:8000）调用本服务三路由；本服务不对外网暴露端口（部署方案 1 节拓扑）。

## 状态（2026-10-29）

- OCR：RapidOCR 已接；真图识别率待 P0-4 验收
- RAG：numpy VectorIndex + 混合检索已接；bge 模型未落地，当前走 hash-bigram
- LLM：DeepSeek/GLM 双通道 + L1—L4 降级已完成
- 合规拦截：4 条词表已接；Prompt 注入/灰区二分类待做
