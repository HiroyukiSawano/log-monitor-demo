package com.example.demo.common.enums;

/**
 * WebSocket 消息类型枚举
 */
public enum MessageType {
    /** 心跳 */
    HEARTBEAT,
    /** 系统指标上报 */
    METRICS,
    /** 单行日志上报 */
    LOG_LINE,
    /** Agent 对 Server 指令的响应 */
    CMD_RESPONSE,
    /** Server 下发控制指令 */
    CMD_REQUEST,
    /** Server 下发配置推送 */
    CONFIG_PUSH
}
