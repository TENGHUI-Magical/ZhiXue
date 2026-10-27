package com.zhixue.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.diagnosis.config.AiClientProperties;
import com.zhixue.diagnosis.dto.OcrRecognizeResponse;
import com.zhixue.diagnosis.dto.OcrTaskView;
import com.zhixue.diagnosis.entity.OcrTask;
import com.zhixue.diagnosis.mapper.OcrTaskMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OCR 任务服务（契约 2.2 节）：提交任务 → 落盘 → 异步调 zhixue-ai → 轮询查询。
 * <p>
 * Java 持有任务表，Python 只做无状态识别；引擎为 RapidOCR，接口不变时可换回 PaddleOCR。
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    /** OCR 队列上限：超出后立即把任务标 FAILED，由前端提示重试，避免无界队列堆积 */
    private static final int OCR_QUEUE_CAPACITY = 50;

    /** 允许的图片上限与整体超时，配置化（05 §0：禁止代码硬编码），默认值对齐契约 §3/§4 */
    @Value("${zhixue.ocr.max-image-bytes:5242880}")
    private long maxImageBytes;

    @Value("${zhixue.ocr.timeout-seconds:60}")
    private long ocrTimeoutSeconds;

    private final OcrTaskMapper ocrTaskMapper;
    private final AiClientProperties aiClientProperties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(OCR_QUEUE_CAPACITY),
            daemonFactory("ocr-worker"),
            new ThreadPoolExecutor.AbortPolicy());

    @Value("${zhixue.ocr.save-dir:./data/upload}")
    private String saveDir;

    public OcrService(OcrTaskMapper ocrTaskMapper, AiClientProperties aiClientProperties,
                      ObjectMapper objectMapper) {
        this.ocrTaskMapper = ocrTaskMapper;
        this.aiClientProperties = aiClientProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(aiClientProperties.baseUrl()).build();
    }

    /**
     * 提交 OCR 任务：校验图片 → 落盘 → 写 PENDING（幂等）→ 异步识别。
     * 幂等按 uk_user_client 唯一键兜底：并发同 clientToken 时捕获重复键异常回查首次任务，
     * 竞态方刚落盘的图片删除，避免孤儿文件（契约 §1：同用户同 token 返回首次结果）。
     *
     * @param image       图片（契约 §4：multipart 字段名 file）
     * @param clientToken 幂等键，Controller 已保证非空
     * @param userId      当前登录用户
     * @return taskId；同 clientToken 重复提交返回首次 taskId
     */
    public String submit(MultipartFile image, String clientToken, long userId) {
        validate(image);

        OcrTask existing = ocrTaskMapper.selectOne(new LambdaQueryWrapper<OcrTask>()
                .eq(OcrTask::getUserId, userId)
                .eq(OcrTask::getClientToken, clientToken)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing.getTaskId();
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        String extension = resolveExtension(image);
        // 契约 V2 §4：data/upload/{userId}/{yyyyMM}/{uuid}.{ext}
        Path directory = Paths.get(saveDir).toAbsolutePath().normalize()
                .resolve(String.valueOf(userId))
                .resolve(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
        Path target = directory.resolve(taskId + "." + extension);
        try {
            Files.createDirectories(directory);
            image.transferTo(target.toFile());
        } catch (Exception e) {
            log.error("OCR 图片落盘失败", e);
            throw new BizException(ErrorCode.BIZ_ERROR, "图片保存失败，请重试");
        }

        OcrTask task = new OcrTask();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setClientToken(clientToken);
        task.setImagePath(target.toString());
        task.setStatus(PENDING);
        task.setRetryCount(0);
        try {
            ocrTaskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            // 并发竞态：另一同 token 请求先插入；回查首次任务并清理本请求的孤儿图片
            OcrTask winner = ocrTaskMapper.selectOne(new LambdaQueryWrapper<OcrTask>()
                    .eq(OcrTask::getUserId, userId)
                    .eq(OcrTask::getClientToken, clientToken)
                    .last("LIMIT 1"));
            try {
                Files.deleteIfExists(target);
            } catch (Exception ignore) {
                // 删除失败留待周任务清理 30 天前孤儿文件（契约 §4）
            }
            if (winner != null) {
                return winner.getTaskId();
            }
            throw e;
        }

        String filename = target.getFileName().toString();
        try {
            executor.execute(() -> recognize(taskId, target, filename));
        } catch (RejectedExecutionException e) {
            log.warn("OCR 队列已满，任务直接标记失败 taskId={}", taskId);
            transition(taskId, PENDING, FAILED, null, null, "OCR 队列已满，请稍后重试");
        }
        return taskId;
    }

    /**
     * 超时任务回收器（契约 §3：超时未完成标记 FAILED；JVM 崩溃/线程池打满场景兜底）。
     * 单实例部署语义；若未来多实例需改分布式锁或分片扫描。
     */
    @Scheduled(fixedDelayString = "${zhixue.ocr.reclaim-interval-ms:60000}")
    public void reclaimStaleTasks() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(ocrTimeoutSeconds);
        List<OcrTask> stale = ocrTaskMapper.selectList(new LambdaQueryWrapper<OcrTask>()
                .in(OcrTask::getStatus, PENDING, RUNNING)
                .lt(OcrTask::getUpdatedAt, deadline)
                .last("LIMIT 50"));
        int reclaimed = 0;
        for (OcrTask task : stale) {
            // CAS：只允许从 PENDING/RUNNING 转 FAILED，避免覆盖并发完成的 SUCCESS
            if (transition(task.getTaskId(), task.getStatus(), FAILED, null, null,
                    "OCR 任务超时，已被回收器标记失败，请手动录入题干")) {
                reclaimed++;
            }
        }
        if (reclaimed > 0) {
            log.warn("OCR 回收器标记 {} 个超时任务为 FAILED", reclaimed);
        }
    }

    /** 查询任务状态；任务不存在或不属于当前用户均按 40400 处理，避免泄露任务存在性。 */
    @SuppressWarnings("unchecked")
    public OcrTaskView getTask(String taskId, long userId) {
        OcrTask task = ocrTaskMapper.selectById(taskId);
        if (task == null || task.getUserId() == null || task.getUserId() != userId) {
            throw new BizException(ErrorCode.NOT_FOUND, "OCR 任务不存在: " + taskId);
        }
        Map<String, Object> fields = null;
        if (task.getResultJson() != null && !task.getResultJson().isBlank()) {
            try {
                fields = objectMapper.readValue(task.getResultJson(), Map.class);
            } catch (Exception e) {
                log.warn("OCR 结果 JSON 解析失败: {}", taskId, e);
            }
        }
        // 契约 OcrTask.errorCode 为整数；DB 存字符串，视图层转码，坏码降级为 null
        Integer errorCode = null;
        if (task.getErrorCode() != null) {
            try {
                errorCode = Integer.valueOf(task.getErrorCode());
            } catch (NumberFormatException e) {
                log.warn("OCR 错误码非法: {}", task.getErrorCode(), e);
            }
        }
        String ocrText = null;
        if (fields != null) {
            Object rawText = fields.get("rawText");
            Object stem = fields.get("stem");
            if (rawText != null) {
                ocrText = String.valueOf(rawText);
            } else if (stem != null) {
                ocrText = String.valueOf(stem);
            }
        }
        return new OcrTaskView(taskId, task.getStatus(), ocrText,
                task.getConfidence() == null ? null : task.getConfidence().doubleValue(),
                fields, errorCode, task.getErrorMsg());
    }

    private void recognize(String taskId, Path file, String filename) {
        // 任务可能已被回收器标 FAILED 或已完成，CAS 失败即不再执行，避免终态回跳
        if (!transition(taskId, PENDING, RUNNING, null, null, null)) {
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignore) {
                // 删除失败留待孤儿文件清理任务
            }
            return;
        }

        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", new FileSystemResource(file)).filename(filename);
            OcrRecognizeResponse response = webClient.post()
                    .uri("/api/ocr/recognize")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(OcrRecognizeResponse.class)
                    .block(Duration.ofSeconds(ocrTimeoutSeconds));

            if (response == null || response.text() == null || response.text().isBlank()) {
                throw new IllegalStateException("未识别到文字");
            }

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("stem", response.text());
            fields.put("rawText", response.text());
            fields.put("lines", response.lines());
            fields.put("structured", false);
            fields.put("engine", "rapidocr");

            BigDecimal confidence = BigDecimal.valueOf(response.confidence())
                    .setScale(3, RoundingMode.HALF_UP);
            transition(taskId, RUNNING, SUCCESS, confidence, objectMapper.writeValueAsString(fields), null);
        } catch (Exception e) {
            log.warn("OCR 识别失败: {}", taskId, e);
            transition(taskId, RUNNING, FAILED, null, null, "OCR 识别失败，请手动录入题干");
        }
    }

    /**
     * 状态条件迁移：只有当前状态等于 from 才更新，返回是否真正迁移成功。
     * 回收器与识别线程并发时，只有一方能成功，避免 FAILED/SUCCESS 互相覆盖。
     */
    private boolean transition(String taskId, String from, String to, BigDecimal confidence,
                               String resultJson, String errorMsg) {
        LambdaUpdateWrapper<OcrTask> update = new LambdaUpdateWrapper<OcrTask>()
                .eq(OcrTask::getTaskId, taskId)
                .eq(OcrTask::getStatus, from)
                .set(OcrTask::getStatus, to)
                .set(OcrTask::getConfidence, confidence)
                .set(OcrTask::getResultJson, resultJson)
                .set(OcrTask::getErrorCode, errorMsg == null ? null : String.valueOf(ErrorCode.OCR_FAILED.getCode()))
                .set(OcrTask::getErrorMsg,
                        errorMsg == null ? null : (errorMsg.length() > 200 ? errorMsg.substring(0, 200) : errorMsg));
        return ocrTaskMapper.update(null, update) > 0;
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    private void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片不能为空");
        }
        if (image.getSize() > maxImageBytes) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片不能超过 5MB");
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持图片文件");
        }
        if (!ALLOWED_EXTENSIONS.contains(resolveExtension(image))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 JPG/PNG");
        }
    }

    private String resolveExtension(MultipartFile image) {
        String original = image.getOriginalFilename();
        String ext = "";
        if (original != null && original.lastIndexOf('.') >= 0) {
            ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        }
        if (ALLOWED_EXTENSIONS.contains(ext)) {
            return ext;
        }
        String contentType = image.getContentType();
        if (contentType == null) {
            return "";
        }
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            default -> "";
        };
    }
}
