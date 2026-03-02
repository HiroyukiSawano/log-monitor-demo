package com.example.demo.module.loghit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 日志命中记录 — 存储规则匹配命中的日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_log_hit_record")
public class LogHitRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源采集端 ID */
    private String agentId;

    /** 应用名称 */
    private String appName;

    /** 日志文件路径 */
    private String logPath;

    /** 判定级别：CRITICAL / UNKNOWN_ERROR / EXCLUDED */
    private String level;

    /** 命中的规则名称 */
    private String matchedRuleName;

    /** 命中的关键字 */
    private String matchedKeyword;

    /** 原始日志内容（最多保留 2000 字符） */
    private String logContent;

    /** Agent 上报时间戳 */
    private Date logTime;

    /** 入库时间 */
    private Date createTime;
}
