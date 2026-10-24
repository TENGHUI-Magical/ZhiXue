package com.zhixue.diagnosis.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ApiResponse;
import com.zhixue.diagnosis.dto.ExplainRequest;
import com.zhixue.diagnosis.dto.MapRequest;
import com.zhixue.diagnosis.dto.MapResult;
import com.zhixue.diagnosis.dto.OcrAccepted;
import com.zhixue.diagnosis.dto.OcrTaskView;
import com.zhixue.diagnosis.dto.Recommendation;
import com.zhixue.diagnosis.service.KpMappingService;
import com.zhixue.diagnosis.service.OcrService;
import com.zhixue.diagnosis.service.RagExplainService;
import com.zhixue.diagnosis.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 诊断模块接口（契约 V2 第 7 节）：
 * OCR 提交/轮询、POST /api/diagnosis/map、POST /api/diagnosis/explain（SSE）、GET /api/recommendations。
 */
@RestController
@RequestMapping("/api")
public class DiagnosisController {

    private final KpMappingService kpMappingService;
    private final RagExplainService ragExplainService;
    private final RecommendationService recommendationService;
    private final OcrService ocrService;

    public DiagnosisController(KpMappingService kpMappingService,
                               RagExplainService ragExplainService,
                               RecommendationService recommendationService,
                               OcrService ocrService) {
        this.kpMappingService = kpMappingService;
        this.ragExplainService = ragExplainService;
        this.recommendationService = recommendationService;
        this.ocrService = ocrService;
    }

    /**
     * 提交 OCR 任务（契约 V2 §4）：multipart 字段名 file，可选 clientToken 幂等。
     */
    @PostMapping(value = "/error/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OcrAccepted> ocrParse(@RequestPart("file") MultipartFile file,
                                             @RequestParam(required = false) String clientToken) {
        return ApiResponse.ok(new OcrAccepted(
                ocrService.submit(file, clientToken, StpUtil.getLoginIdAsLong())));
    }

    /**
     * 轮询 OCR 任务（契约 2.2 节第二步），前端 2 秒间隔。
     */
    @GetMapping("/error/ocr/task/{taskId}")
    public ApiResponse<OcrTaskView> ocrTask(@PathVariable String taskId) {
        return ApiResponse.ok(ocrService.getTask(taskId, StpUtil.getLoginIdAsLong()));
    }

    /**
     * 知识点映射（三通道；当前实现词法向量通道）。
     * 请求：{"text":"...","courseId":1}
     */
    @PostMapping("/diagnosis/map")
    public ApiResponse<MapResult> map(@Valid @RequestBody MapRequest request) {
        return ApiResponse.ok(kpMappingService.match(request));
    }

    /**
     * RAG 流式讲解（SSE）：meta → source → delta* →（degraded）→ done / error。
     * 请求：{"kpId":"KP0201","errorType":"CONCEPT_CONFUSION","questionText":"..."}
     */
    @PostMapping(value = "/diagnosis/explain", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter explain(@Valid @RequestBody ExplainRequest request) {
        return ragExplainService.explain(request);
    }

    /**
     * 三层推荐：CONSOLIDATE / VARIANT / ADVANCE 各 1 题。
     * 契约参数为 logId；竖切阶段允许用 kpId 兜底，直到错题记录模块接入。
     */
    @GetMapping("/recommendations")
    public ApiResponse<List<Recommendation>> recommendations(
            @RequestParam(required = false) String logId,
            @RequestParam(required = false) String kpId,
            @RequestParam(defaultValue = "1") Long courseId) {
        return ApiResponse.ok(recommendationService.recommend(logId, kpId, courseId));
    }
}
