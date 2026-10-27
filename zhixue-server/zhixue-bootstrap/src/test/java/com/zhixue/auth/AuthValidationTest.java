package com.zhixue.auth;

import com.zhixue.common.exception.GlobalExceptionHandler;
import com.zhixue.user.controller.AuthController;
import com.zhixue.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录参数边界：username 对齐 DB VARCHAR(50)，password 对齐契约 6—64。
 */
class AuthValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(mock(AuthService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void usernameLongerThanDatabaseColumnIsRejected() throws Exception {
        String body = "{\"username\":\"" + "x".repeat(51) + "\",\"password\":\"123456\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }

    @Test
    void passwordShorterThanContractMinimumIsRejected() throws Exception {
        String body = "{\"username\":\"student01\",\"password\":\"12345\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":40000");
    }
}
