package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.ExplainLogView;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 讲解记录查询单测（契约 V2 §5.4）：字段映射与不存在时 40400。
 */
@ExtendWith(MockitoExtension.class)
class ExplainQueryServiceTest {

    @Mock
    private AiGenerationLogMapper logMapper;

    @Mock
    private KnowledgePointService knowledgePointService;

    private ExplainQueryService service;

    @BeforeEach
    void setUp() {
        service = new ExplainQueryService(logMapper, knowledgePointService, new ObjectMapper());
    }

    @Test
    void returnsStoredContentWithResolvedSources() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_1");
        row.setTraceId("t-1");
        row.setModel("deepseek-chat");
        row.setIsDegraded(0);
        row.setContentText("已生成讲解全文");
        row.setSourceIds("[\"KP0201\"]");
        row.setIsBlocked(0);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 26, 12, 0));
        when(logMapper.selectOne(any())).thenReturn(row);

        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        kp.setTextbookPage("P34");
        when(knowledgePointService.findById("KP0201")).thenReturn(kp);

        ExplainLogView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.get("EX_1");
        }

        assertThat(view.explainId()).isEqualTo("EX_1");
        assertThat(view.content()).isEqualTo("已生成讲解全文");
        assertThat(view.degraded()).isFalse();
        assertThat(view.blocked()).isFalse();
        assertThat(view.createdAt()).isEqualTo("2026-10-26 12:00:00");
        assertThat(view.sources()).extracting(ExplainLogView.SourceView::title)
                .containsExactly("关系模型");
    }

    @Test
    void missingKpToleratedInSources() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_1");
        row.setContentText("正文");
        row.setSourceIds("[\"KP0201\"]");
        row.setIsDegraded(0);
        row.setIsBlocked(0);
        when(logMapper.selectOne(any())).thenReturn(row);
        when(knowledgePointService.findById("KP0201")).thenReturn(null);

        ExplainLogView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.get("EX_1");
        }

        assertThat(view.sources()).hasSize(1);
        assertThat(view.sources().getFirst().title()).isNull();
        assertThat(view.sources().getFirst().textbookPage()).isNull();
    }

    @Test
    void missingRecordThrowsNotFound() {
        when(logMapper.selectOne(any())).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThatThrownBy(() -> service.get("EX_missing"))
                    .isInstanceOf(BizException.class);
        }
    }
}
