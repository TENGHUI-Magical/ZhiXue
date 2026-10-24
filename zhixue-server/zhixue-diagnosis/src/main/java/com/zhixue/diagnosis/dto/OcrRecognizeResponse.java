package com.zhixue.diagnosis.dto;

import java.util.List;

/**
 * zhixue-ai /api/ocr/recognize 的响应。
 */
public record OcrRecognizeResponse(String text, double confidence, List<Line> lines, long elapsedMs) {

    /** 单行识别结果 */
    public record Line(String text, double score) {
    }
}
