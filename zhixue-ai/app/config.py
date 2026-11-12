"""zhixue-ai 配置中心：环境变量读取（部署方案 4 节）。密钥严禁硬编码。"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # 数据库（任务表轮询用 MySQL 业务库）
    mysql_dsn: str = "mysql+pymysql://root:change-me@localhost:3306/zhixue"

    # 向量索引目录（numpy + VectorIndex，200 条规模；不引入 Chroma/Milvus）
    vector_index_dir: str = "./data/vector_index"
    # RAG 检索参数（docs/05 §0：集中配置，禁止散落硬编码）
    rag_top_k: int = 5
    rag_score_threshold: float = 0.35
    rag_chunk_size: int = 450
    rag_chunk_overlap: int = 50
    rag_hybrid_vector: float = 0.7
    rag_embed_model: str = "BAAI/bge-small-zh-v1.5"

    # 大模型（模型名配置驱动：改模型只改 .env，不改业务代码）
    llm_model_main: str = "deepseek-chat"
    llm_model_fallback: str = "glm-4-flash"
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    glm_api_key: str = ""
    glm_base_url: str = "https://open.bigmodel.cn/api/paas/v4"
    # 契约 2.4 降级链：L1 主力 → L2 GLM → L3 本地模板（Java 侧）→ L4 60001
    # 流式超时：连接 3s（rag_service 固定）/ 读空闲 10s（覆盖首字节与帧间停顿）；
    # 契约 §5.2 的“整体 60s”由 Java 侧 SSE 收口，不在本服务内实现
    llm_timeout_seconds: int = 10

    # OCR 落盘与质量阈值（契约 2.2 节：置信度 < 0.75 前端强制人工确认）
    ocr_save_dir: str = "./data/upload"
    ocr_confidence_alert: float = 0.75
    # 契约 §4：图片上限 5MB、压缩长边 1600（05 §0：参数集中配置，禁止散落硬编码）
    ocr_max_image_bytes: int = 5 * 1024 * 1024
    ocr_max_side: int = 1600

    # 内部服务鉴权（P0 修复）：Java 调用 Python 三路由时必须携带 X-Internal-Token
    # 为空时拒绝所有请求（fail-closed），生产环境必须配置
    internal_token: str = ""


settings = Settings()
