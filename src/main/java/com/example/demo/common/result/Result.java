package com.example.demo.common.result;

import lombok.Data;

/**
 * 全局统一 API 响应体
 *
 * @param <T> 能够携带任何类型的响应数据
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    // 状态码常量
    public static final int SUCCESS_CODE = 200;
    public static final int ERROR_CODE = 500;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构建成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "操作成功", data);
    }

    /**
     * 构建成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 构建失败响应
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ERROR_CODE, message, null);
    }

    /**
     * 构建失败响应（自定义状态码）
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
