package com.zhixue.diagnosis.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.diagnosis.config.AiClientProperties;
import com.zhixue.diagnosis.entity.OcrTask;
import com.zhixue.diagnosis.mapper.OcrTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OCR 状态机单测：回收器必须用条件更新，不能无条件覆盖并发完成的 SUCCESS。
 */
@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    private OcrTaskMapper ocrTaskMapper;

    private OcrService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                OcrTask.class);
    }

    @BeforeEach
    void setUp() {
        service = new OcrService(ocrTaskMapper,
                new AiClientProperties("http://127.0.0.1:1", 1), new ObjectMapper());
    }

    @Test
    void reclaimUsesConditionalUpdateInsteadOfOverwritingById() {
        OcrTask stale = new OcrTask();
        stale.setTaskId("T1");
        stale.setStatus("PENDING");
        stale.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        when(ocrTaskMapper.selectList(any())).thenReturn(List.of(stale));
        when(ocrTaskMapper.update(isNull(), any())).thenReturn(1);

        service.reclaimStaleTasks();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<OcrTask>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(ocrTaskMapper).update(isNull(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertThat(sql).contains("status");
        assertThat(sql).contains("task_id");
        verify(ocrTaskMapper, never()).updateById(any(OcrTask.class));
    }
}
