package com.zhixue.common.exception;

import com.zhixue.common.api.ApiResponse;
import com.zhixue.common.api.ErrorCode;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：保证任何异常都返回统一 ApiResponse 结构，前端错误分支只认 code。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        return ApiResponse.fail(e.getErrorCode(), e.getMessage());
    }

    /** Sa-Token：未登录或 token 失效 → 40100（契约 1.2 节） */
    @ExceptionHandler(NotLoginException.class)
    public ApiResponse<Void> handleNotLogin(NotLoginException e) {
        return ApiResponse.fail(ErrorCode.UNAUTHORIZED);
    }

    /** Sa-Token：角色/权限不足 → 40300 */
    @ExceptionHandler({ NotRoleException.class, NotPermissionException.class })
    public ApiResponse<Void> handleForbidden(Exception e) {
        return ApiResponse.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? ErrorCode.PARAM_ERROR.getMsg() : fe.getField() + " " + fe.getDefaultMessage();
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, msg);
    }

    /** 请求体缺失/JSON 格式错误/参数缺失/类型不匹配/单参数校验 → 40000（契约 1.2「格式错误/越界」） */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ApiResponse<Void> handleBadRequest(Exception e) {
        return ApiResponse.fail(ErrorCode.PARAM_ERROR);
    }

    /** 上传缺文件或多部件请求格式错误 → 40000，而不是落到兜底 50000 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ApiResponse<Void> handleMissingPart(MissingServletRequestPartException e) {
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, "缺少上传文件字段: " + e.getRequestPartName());
    }

    /** multipart 超过容器上限 → 40000；业务侧 5MB 限制由 OcrService 再校验 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, "图片不能超过 10MB");
    }

    /** 未知路径（含未知静态资源）→ 40400；Spring 6.1 起统一抛 NoResourceFoundException */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handleNoResource(NoResourceFoundException e) {
        return ApiResponse.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("未预期异常", e);
        return ApiResponse.fail(ErrorCode.BIZ_ERROR);
    }
}