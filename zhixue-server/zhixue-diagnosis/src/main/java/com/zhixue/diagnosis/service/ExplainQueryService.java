package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.ExplainLogView;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 讲解记录查询（契约 V2 §5.4：刷新页面后按 explainId 取已生成全文）。
 * <p>
 * 仅允许查询本人记录，避免 explainId 泄露他人讲解内容。
 */
@Service
public class ExplainQueryService {

    private static final Logger log = LoggerFactory.getLogger(ExplainQueryService.class);

    /** 契约 §1：日期时间统一 `yyyy-MM-dd HH:mm:ss` */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiGenerationLogMapper logMapper;
    private final KnowledgePointService knowledgePointService;
    private final ObjectMapper objectMapper;

    public ExplainQueryService(AiGenerationLogMapper logMapper,
                               KnowledgePointService knowledgePointService,
                               ObjectMapper objectMapper) {
        this.logMapper = logMapper;
        this.knowledgePointService = knowledgePointService;
        this.objectMapper = objectMapper;
    }

    /**
     * 按 explainId 查询当前用户的讲解记录。
     *
     * @param explainId 讲解编号
     * @return 讲解全文与元信息
     * @throws BizException 记录不存在或不属于当前用户时返回 40400
     */
    public ExplainLogView get(String explainId) {
        long userId = StpUtil.getLoginIdAsLong();
        AiGenerationLog row = logMapper.selectOne(new LambdaQueryWrapper<AiGenerationLog>()
                .eq(AiGenerationLog::getExplainId, explainId)
                .eq(AiGenerationLog::getUserId, userId)
                .last("LIMIT 1"));
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "讲解记录不存在");
        }
        return new ExplainLogView(
                row.getExplainId(),
                row.getTraceId(),
                row.getModel(),
                Objects.equals(row.getIsDegraded(), 1),
                row.getDegradeLevel(),
                row.getContentText() == null ? "" : row.getContentText(),
                sources(row.getSourceIds()),
                Objects.equals(row.getIsBlocked(), 1),
                row.getCreatedAt() == null ? null : row.getCreatedAt().format(DATE_TIME));
    }

    /** source_ids 是 kp_id 数组 JSON；名称与页码从知识点表补齐，缺失时降级为 null。 */
    private List<ExplainLogView.SourceView> sources(String sourceIds) {
        if (sourceIds == null || sourceIds.isBlank()) {
            return List.of();
        }
        List<String> kpIds;
        try {
            kpIds = objectMapper.readValue(sourceIds, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("source_ids 解析失败，返回空来源：{}", e.toString());
            return List.of();
        }
        List<ExplainLogView.SourceView> views = new ArrayList<>(kpIds.size());
        for (String kpId : kpIds) {
            KnowledgePoint kp = knowledgePointService.findById(kpId);
            views.add(new ExplainLogView.SourceView(kpId,
                    kp == null ? null : kp.getTextbookPage(),
                    kp == null ? null : kp.getName()));
        }
        return views;
    }
}
