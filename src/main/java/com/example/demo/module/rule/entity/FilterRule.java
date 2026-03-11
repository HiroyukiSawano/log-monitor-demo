package com.example.demo.module.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 规则定义实体 — 对应 t_filter_rule 表
 */
@Data
@TableName("t_filter_rule")
public class FilterRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 绑定的采集端 ID（'*' 表示全局生效） */
    private String agentId;

    /** 规则类型：CRITICAL(强关注) / EXCLUDE(排除) / BASIC(基础特征) */
    private String ruleType;

    /** 规则名称（人类可读），如 "第三方接口超时" */
    private String ruleName;

    /** 匹配关键字，如 "调用第三方接口超时" */
    private String keyword;

    /** 匹配模式：CONTAINS(包含) / REGEX(正则) / STARTS_WITH / ENDS_WITH */
    private String matchMode;

    /** 所属应用名（为空则全局生效） */
    private String appName;

    /** 是否启用 */
    private Boolean enabled;

    /** 优先级（同类型内排序用） */
    private Integer priority;

    private Date createTime;
    private Date updateTime;
}
