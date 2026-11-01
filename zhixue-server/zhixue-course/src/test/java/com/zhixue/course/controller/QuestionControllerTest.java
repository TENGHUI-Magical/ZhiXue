package com.zhixue.course.controller;

import com.zhixue.course.dto.QuestionBriefView;
import com.zhixue.course.service.QuestionBankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 按知识点列题的 MVC 边界：路径、参数与统一信封。
 */
@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {

    @Mock
    private QuestionBankService questionBankService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new QuestionController(questionBankService))
                .build();
    }

    @Test
    void listByKpReturnsEnvelopeAndItems() throws Exception {
        when(questionBankService.listBriefByKp("KP0201", 20)).thenReturn(List.of(
                new QuestionBriefView("Q_0201", "KP0201", "单选", "题干", 1)));

        mockMvc.perform(get("/api/questions").param("kpId", "KP0201").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].qId").value("Q_0201"))
                .andExpect(jsonPath("$.data[0].questionText").value("题干"));
    }
}
