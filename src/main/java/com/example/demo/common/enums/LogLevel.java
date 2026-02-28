package com.example.demo.common.enums;

/**
 * 日志判定级别枚举
 */
public enum LogLevel {
    /** 强关注命中 — 立即告警 */
    CRITICAL,
    /** 排除规则命中 — 正常业务 */
    EXCLUDED,
    /** 基础特征命中 — 未知系统错误 */
    UNKNOWN_ERROR,
    /** 全链未命中 — 普通日志 */
    NORMAL
}
