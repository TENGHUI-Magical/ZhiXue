package com.zhixue.common.exception;

import com.zhixue.common.api.ErrorCode;

/**
 * 业务异常：由各模块 service 层主动抛出，
 * 经 {@link com.zhixue.common.exception.GlobalExceptionHandler} 统一转为 ApiResponse。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
}