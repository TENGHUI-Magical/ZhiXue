"""数据采集与 OCR 训练集构建管线（服务内容线的工程化延伸）。

模块：
- image_math     图像矩阵预处理：灰度化 / 高斯降噪卷积 / Otsu 二值化 / 倾斜校正
- web_crawler    异步网页采集：限速 + 指数退避 + robots.txt + 断点续传
- ocr_pipeline   多引擎 OCR 降级链 + 置信度统计过滤
- dataset_builder 数据清洗去重 / 分层抽样 / 蒙特卡洛增广 / 标准格式输出

边界（AGENTS.md 红线 1）：管线产出一律视为「待人工签核」的中间产物，
不直接写数据库、不自动生成题目/答案入库；重依赖（cv2、bs4、paddleocr 等）
全部可选导入并提供降级路径，不进入 requirements.txt 生产依赖。
"""
