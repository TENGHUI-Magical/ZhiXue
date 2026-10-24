"""OCR 路由：错题图像结构化抽取（契约 2.2 节，任务表轮询模式）。"""
from __future__ import annotations

import uuid

from fastapi import APIRouter, File, UploadFile

from app.schemas import OcrTaskOut

router = APIRouter()


@router.post("/parse", response_model=OcrTaskOut)
async def parse(image: UploadFile = File(..., description="错题图片，字段名 image（契约 2.2 节），支持常见手机拍摄格式")):
    """提交 OCR 任务：先返回 taskId，前端按 1.5s 间隔轮询 /task/{taskId}，骨架屏过渡。"""
    task_id = uuid.uuid4().hex

    # TODO(P0)：保存图片至 settings.ocr_save_dir →
    #           写入业务库 ocr_task 表(PENDING) →
    #           BackgroundTasks 调 PaddleOCR 做题干/选项/答案分块识别 →
    #           置信度写入 running/result 两条记录
    # 失败路径：无文字/图片模糊 → status=FAILED，Java 侧映射 60002，前端弹手动录入降级表单

    return OcrTaskOut(taskId=task_id, status="PENDING")


@router.get("/task/{task_id}", response_model=OcrTaskOut)
async def task_status(task_id: str):
    """轮询任务状态。TODO(P0)：按 ocr_task 表查询真实状态。"""
    # 骨架期占位：契约字段先返回，下一阶段接任务表
    return OcrTaskOut(taskId=task_id, status="PENDING")