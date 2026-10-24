package com.zhixue.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.diagnosis.config.AiClientProperties;
import com.zhixue.diagnosis.dto.OcrRecognizeResponse;
import com.zhixue.diagnosis.dto.OcrTaskView;
import com.zhixue.diagnosis.entity.OcrTask;
import com.zhixue.diagnosis.mapper.OcrTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
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
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OCR 任务服务（契约 2.2 节）：提交任务 → 落盘 → 异步调 zhixue-ai → 轮询查询。
 * <p>
 * Java 持有任务表，Python 只做无状态识别；引擎为 RapidOCR，接口不变时可换回 PaddleOCR。
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    /** OCR 首次加载模型较慢，单独给 60 秒，不复用对话的 10 秒超时 */
    private static final Duration OCR_TIMEOUT = Duration.ofSeconds(60);

    private final OcrTaskMapper ocrTaskMapper;
    private final AiClientProperties aiClientProperties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ocr-worker");
        thread.setDaemon(true);
        return thread;
    });

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
     * 提交 OCR 任务：校验图片 → 落盘 → 写 PENDING → 异步识别。
     *
     * @param image       图片（字段名 image）
     * @param clientToken 幂等键，可为空
     * @param userId      当前登录用户
     * @return taskId
     */
    public String submit(MultipartFile image, String clientToken, long userId) {
        validate(image);

        if (clientToken != null && !clientToken.isBlank()) {
            OcrTask existing = ocrTaskMapper.selectOne(new LambdaQueryWrapper<OcrTask>()
                    .eq(OcrTask::getUserId, userId)
                    .eq(OcrTask::getClientToken, clientToken)
                    .last("LIMIT 1"));
            if (existing != null) {
                return existing.getTaskId();
            }
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
        task.setStatus("PENDING");
        task.setRetryCount(0);
        ocrTaskMapper.insert(task);

        String filename = target.getFileName().toString();
        executor.execute(() -> recognize(taskId, target, filename));
        return taskId;
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
        return new OcrTaskView(taskId, task.getStatus(),
                task.getConfidence() == null ? null : task.getConfidence().doubleValue(),
                fields, task.getErrorCode(), task.getErrorMsg());
    }

    private void recognize(String taskId, Path file, String filename) {
        updateStatus(taskId, "RUNNING", null, null, null);

        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("image", new FileSystemResource(file)).filename(filename);
            OcrRecognizeResponse response = webClient.post()
                    .uri("/api/ocr/recognize")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(OcrRecognizeResponse.class)
                    .block(OCR_TIMEOUT);

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
            updateStatus(taskId, "SUCCESS", confidence, objectMapper.writeValueAsString(fields), null);
        } catch (Exception e) {
            log.warn("OCR 识别失败: {}", taskId, e);
            updateStatus(taskId, "FAILED", null, null,
                    "OCR 识别失败，请手动录入题干");
        }
    }

    private void updateStatus(String taskId, String status, BigDecimal confidence,
                              String resultJson, String errorMsg) {
        OcrTask update = new OcrTask();
        update.setTaskId(taskId);
        update.setStatus(status);
        update.setConfidence(confidence);
        update.setResultJson(resultJson);
        if (errorMsg != null) {
            update.setErrorCode("60002");
            update.setErrorMsg(errorMsg.length() > 200 ? errorMsg.substring(0, 200) : errorMsg);
        }
        ocrTaskMapper.updateById(update);
    }

    private void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片不能为空");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
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
