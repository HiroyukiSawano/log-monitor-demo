package com.example.demo.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志上下文 — 封装单行日志的完整来源信息，作为过滤链的输入
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogContext {
    /** 来源采集端 ID */
    private String agentId;
    /** 应用名称 */
    private String appName;
    /** 日志文件路径 */
    private String logPath;
    /** 原始日志行文本（全量，不做截断） */
    private String rawLine;
    /** Agent 上报时间戳 */
    private long timestamp;
}
