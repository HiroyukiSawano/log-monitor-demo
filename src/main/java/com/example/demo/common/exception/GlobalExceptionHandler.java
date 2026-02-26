package com.example.demo.common.exception;

import com.example.demo.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 捕获 Controller 层抛出的所有异常，并转化为统一的 Result 格式返回给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获所有未知的 Exception 异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("服务器发生未预期的异常: ", e);
        return Result.error(Result.ERROR_CODE, "服务器内部错误，请稍后重试！");
    }

    /**
     * 捕获自定义的业务异常 (暂时处理 RuntimeException，后续可扩展业务特定异常)
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.warn("业务运行异常: {}", e.getMessage());
        return Result.error(Result.ERROR_CODE, e.getMessage());
    }
}
