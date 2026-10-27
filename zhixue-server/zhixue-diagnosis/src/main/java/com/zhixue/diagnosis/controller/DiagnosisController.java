package com.zhixue.diagnosis.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.api.ApiResponse;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.common.web.TraceIdFilter;
import com.zhixue.diagnosis.dto.ExplainLogView;
import com.zhixue.diagnosis.dto.ExplainRequest;
import com.zhixue.diagnosis.dto.MapRequest;
import com.zhixue.diagnosis.dto.MapResult;
import com.zhixue.diagnosis.dto.OcrAccepted;
import com.zhixue.diagnosis.dto.OcrTaskView;
import com.zhixue.diagnosis.dto.Recommendation;
import com.zhixue.diagnosis.service.ExplainQueryService;
import com.zhixue.diagnosis.service.ExplainRateLimiter;
import com.zhixue.diagnosis.service.KpMappingService;
import com.zhixue.diagnosis.service.OcrService;
import com.zhixue.diagnosis.service.RagExplainService;
import com.zhixue.diagnosis.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final ExplainRateLimiter explainRateLimiter;
    private final ExplainQueryService explainQueryService;

    public DiagnosisController(KpMappingService kpMappingService,
                               RagExplainService ragExplainService,
                               RecommendationService recommendationService,
                               OcrService ocrService,
                               ExplainRateLimiter explainRateLimiter,
                               ExplainQueryService explainQueryService) {
        this.kpMappingService = kpMappingService;
        this.ragExplainService = ragExplainService;
        this.recommendationService = recommendationService;
        this.ocrService = ocrService;
        this.explainRateLimiter = explainRateLimiter;
        this.explainQueryService = explainQueryService;
    }

    /**
     * 提交 OCR 任务（契约 V2 §4）：multipart 字段固定 file + clientToken（必填，幂等键）。
     * 契约未声明 courseId，上传阶段不接收课程参数；课程归属由后续创建错题记录承担。
     */
    @PostMapping(value = "/error/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OcrAccepted> ocrParse(@RequestPart("file") MultipartFile file,
                                             @RequestPart("clientToken") String clientToken) {
        // 契约 §1：clientToken 必填且最长 64；空白串与超长串按 40000 口径拦截
        if (clientToken == null || clientToken.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "clientToken 不能为空");
        }
        if (clientToken.length() > 64) {
            throw new BizException(ErrorCode.PARAM_ERROR, "clientToken 最长 64 字");
        }
        return ApiResponse.ok(new OcrAccepted(
                ocrService.submit(file, clientToken, StpUtil.getLoginIdAsLong())));
    }

    /**
     * 轮询 OCR 任务（契约 §3.1：GET /api/error/tasks/{taskId}），前端 2 秒间隔。
     */
    @GetMapping("/error/tasks/{taskId}")
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
     * 请求：{"logId":"...","kpId":"KP0201","errorType":"CONCEPT_CONFUSION","questionText":"..."}
     * traceId 由 TraceIdFilter 生成（契约 §1.2），在请求线程内取出透传给服务层。
     */
    @PostMapping(value = "/diagnosis/explain", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter explain(@Valid @RequestBody ExplainRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        // SSE 帧含中文：显式 UTF-8，避免容器默认 ISO-8859-1 把讲解正文写成问号
        httpResponse.setCharacterEncoding("UTF-8");
        // 契约 §8：按用户 10 次/分钟；超限仍走 SSE error 事件（42900），并带 Retry-After
        ExplainRateLimiter.Decision decision = explainRateLimiter.acquire(StpUtil.getLoginIdAsLong());
        if (!decision.allowed()) {
            httpResponse.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            return ragExplainService.reject(ErrorCode.RATE_LIMITED.getCode(),
                    ErrorCode.RATE_LIMITED.getMsg(), true);
        }
        return ragExplainService.explain(request,
                (String) httpRequest.getAttribute(TraceIdFilter.ATTRIBUTE));
    }

    /**
     * 查询已生成讲解（契约 V2 §5.4，刷新页面恢复全文）。
     */
    @GetMapping("/diagnosis/explain/{explainId}")
    public ApiResponse<ExplainLogView> explainLog(@PathVariable String explainId) {
        return ApiResponse.ok(explainQueryService.get(explainId));
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
