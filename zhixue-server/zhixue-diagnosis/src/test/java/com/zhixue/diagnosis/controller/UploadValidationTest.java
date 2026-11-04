package com.zhixue.diagnosis.controller;

import com.zhixue.common.exception.GlobalExceptionHandler;
import com.zhixue.diagnosis.service.ExplainQueryService;
import com.zhixue.diagnosis.service.ExplainRateLimiter;
import com.zhixue.diagnosis.service.KpMappingService;
import com.zhixue.diagnosis.service.OcrService;
import com.zhixue.diagnosis.service.RagExplainService;
import com.zhixue.diagnosis.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 上传接口 MVC 边界：缺 file 部分应返回业务码 40000，而不是 50000。
 */
@ExtendWith(MockitoExtension.class)
class UploadValidationTest {

    @Mock
    private OcrService ocrService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DiagnosisController controller = new DiagnosisController(
                mock(KpMappingService.class), mock(RagExplainService.class),
                mock(RecommendationService.class), ocrService,
                mock(ExplainRateLimiter.class), mock(ExplainQueryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingFilePartReturnsParamError() throws Exception {
        // clientToken 为表单字段（契约 §4）；此处只传 clientToken、缺 file
        MockMultipartFile clientTokenPart = new MockMultipartFile(
                "clientToken", "", "text/plain", "ct-1".getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/error/upload")
                        .file(clientTokenPart))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("\"code\":40000")
                .contains("file");
    }

    @Test
    void overlongClientTokenReturnsParamError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile clientTokenPart = new MockMultipartFile(
                "clientToken", "", "text/plain",
                "x".repeat(65).getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/error/upload")
                        .file(file).file(clientTokenPart))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("\"code\":40000")
                .contains("clientToken");
    }

    @Test
    void wrongHttpMethodReturnsParamError() throws Exception {
        // GET 打到只支持 POST 的上传端点：405 也必须收敛为 40000，不能落兜底 50000
        MvcResult result = mockMvc.perform(get("/api/error/upload"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }

    @Test
    void unsupportedMediaTypeReturnsParamError() throws Exception {
        // text/plain 请求体打到 JSON 端点：415 也必须收敛为 40000
        MvcResult result = mockMvc.perform(post("/api/diagnosis/map")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }

    @Test
    void malformedMultipartReturnsParamError() throws Exception {
        // 声明 multipart 但无 boundary：MultipartException 也必须收敛为 40000
        MvcResult result = mockMvc.perform(post("/api/error/upload")
                        .header("Content-Type", "multipart/form-data"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }

    @Test
    void explainWithIllegalErrorTypeReturnsParamError() throws Exception {
        // 契约枚举外的 errorType 必须在进入 SSE/服务层前拒绝
        MvcResult result = mockMvc.perform(post("/api/diagnosis/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logId\":\"EL_1\",\"kpId\":\"KP0201\","
                                + "\"errorType\":\"HACK\",\"questionText\":\"q\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }
}
