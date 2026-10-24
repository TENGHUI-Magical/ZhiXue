package com.zhixue.common.api;

/**
 * 统一响应包装类（接口契约文档 1.1 节）。
 * 所有 HTTP 接口（SSE 除外）均返回该结构：{code, message, data, timestamp}
 */
public class ApiResponse<T> {

    /** 失败响应的 data 占位：契约 1.1 规定无数据时 data 为空对象 {}，而非 null */
    private static final Object EMPTY = new Object();

    /** 业务状态码，0 成功，非 0 见 {@link ErrorCode} */
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), data);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMsg());
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, (T) EMPTY);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}