package com.zhixue.plan.controller;

import com.zhixue.common.exception.GlobalExceptionHandler;
import com.zhixue.diagnosis.service.DueReviewService;
import com.zhixue.plan.service.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/plans 的查询参数边界：只接受 from/to，其它参数不再被静默忽略。
 */
@ExtendWith(MockitoExtension.class)
class PlanControllerTest {

    @Mock
    private PlanService planService;

    @Mock
    private DueReviewService dueReviewService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlanController(planService, dueReviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unsupportedQueryParamReturnsParamError() throws Exception {
        mockMvc.perform(get("/api/plans").param("courseId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void fromToRangeReturnsSuccessEnvelope() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 7);
        when(planService.list(from, to)).thenReturn(List.of());

        mockMvc.perform(get("/api/plans")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
