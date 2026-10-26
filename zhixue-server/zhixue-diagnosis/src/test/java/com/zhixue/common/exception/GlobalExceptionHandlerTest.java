package com.zhixue.common.exception;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.common.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上传异常映射：缺文件与超过容器上限都必须返回 40000，而不是兜底 50000。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingPartReturnsParamError() {
        ApiResponse<Void> response = handler.handleMissingPart(
                new MissingServletRequestPartException("file"));

        assertThat(response.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        assertThat(response.getMessage()).contains("file");
    }

    @Test
    void tooLargeUploadReturnsParamError() {
        ApiResponse<Void> response = handler.handleUploadTooLarge(
                new MaxUploadSizeExceededException(12L * 1024 * 1024));

        assertThat(response.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        assertThat(response.getMessage()).contains("10MB");
    }
}
