"""zhixue-ai 配置中心：环境变量读取（部署方案 4 节）。密钥严禁硬编码。"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # 数据库（任务表轮询用 MySQL 业务库）
    mysql_dsn: str = "mysql+pymysql://root:change-me@localhost:3306/zhixue"

    # 向量索引目录（numpy + VectorIndex，200 条规模；不引入 Chroma/Milvus）
    vector_index_dir: str = "./data/vector_index"

    # 大模型（模型名配置驱动：改模型只改 .env，不改业务代码）
    llm_model_main: str = "deepseek-chat"
    llm_model_fallback: str = "glm-4-flash"
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    glm_api_key: str = ""
    glm_base_url: str = "https://open.bigmodel.cn/api/paas/v4"
    # 契约 2.4 降级链：L1 主力 → L2 GLM → L3 本地模板（Java 侧）→ L4 60001
    # 流式超时分三级：连接 3s / 首字节 10s / 整体 60s
    llm_timeout_seconds: int = 10

    # OCR 落盘与质量阈值（契约 2.2 节：置信度 < 0.75 前端强制人工确认）
    ocr_save_dir: str = "./data/upload"
    ocr_confidence_alert: float = 0.75
    # 契约 §4：图片上限 5MB、压缩长边 1600（05 §0：参数集中配置，禁止散落硬编码）
    ocr_max_image_bytes: int = 5 * 1024 * 1024
    ocr_max_side: int = 1600


settings = Settings()
