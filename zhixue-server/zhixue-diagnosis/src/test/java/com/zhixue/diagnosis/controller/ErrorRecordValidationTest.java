package com.zhixue.diagnosis.controller;

import com.zhixue.common.exception.GlobalExceptionHandler;
import com.zhixue.diagnosis.service.StudentErrorRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 创建错题记录 DTO 边界：ocrConfidence 必须落在 [0,1]，errorType 必须是契约枚举。
 */
@ExtendWith(MockitoExtension.class)
class ErrorRecordValidationTest {

    @Mock
    private StudentErrorRecordService recordService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ErrorRecordController controller = new ErrorRecordController(mock(StudentErrorRecordService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MvcResult create(String json) throws Exception {
        return mockMvc.perform(post("/api/error/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void ocrConfidenceAboveOneReturnsParamError() throws Exception {
        MvcResult result = create("{\"courseId\":1,\"clientToken\":\"ct-1\",\"ocrConfidence\":2}");
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }

    @Test
    void negativeOcrConfidenceReturnsParamError() throws Exception {
        MvcResult result = create("{\"courseId\":1,\"clientToken\":\"ct-1\",\"ocrConfidence\":-1}");
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }

    /** courseId=0 是边界探针 E05 的输入：此前会落库成孤儿记录，必须在入口按 40000 拦掉 */
    @Test
    void zeroCourseIdReturnsParamError() throws Exception {
        MvcResult result = create("{\"courseId\":0,\"clientToken\":\"ct-1\"}");
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }

    /** 负数 courseId 同属越界；Long.MAX 是"正数但不存在"，由 Service 判 40400，不在此用例覆盖 */
    @Test
    void negativeCourseIdReturnsParamError() throws Exception {
        MvcResult result = create("{\"courseId\":-1,\"clientToken\":\"ct-1\"}");
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }

    @Test
    void illegalErrorTypeReturnsParamError() throws Exception {
        MvcResult result = create("{\"courseId\":1,\"clientToken\":\"ct-1\",\"errorType\":\"HACK\"}");
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }

    @Test
    void validDraftStillAccepted() throws Exception {
        MvcResult result = create("{\"courseId\":1,\"clientToken\":\"ct-1\","
                + "\"ocrConfidence\":0.9,\"errorType\":\"CONCEPT_CONFUSION\"}");
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":0");
    }
}
