package com.zhixue.common.api;

/**
 * 统一错误码枚举（接口契约文档 1.2 节）。
 * 分段约定：4xxxx 客户端态 / 5xxxx 业务态 / 6xxxx AI 服务异常态。
 */
public enum ErrorCode {

    SUCCESS(0, "success"),

    PARAM_ERROR(40000, "参数校验失败"),
    UNAUTHORIZED(40100, "未登录或 token 已过期"),
    FORBIDDEN(40300, "无权限访问"),
    NOT_FOUND(40400, "资源不存在"),

    BIZ_ERROR(50000, "业务处理失败"),

    AI_SERVICE_ERROR(60000, "AI 服务异常"),
    AI_TIMEOUT(60001, "AI 服务超时"),
    OCR_FAILED(60002, "OCR 识别失败"),
    VECTOR_NO_MATCH(60003, "向量库无匹配结果");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() { return code; }
    public String getMsg() { return msg; }
}