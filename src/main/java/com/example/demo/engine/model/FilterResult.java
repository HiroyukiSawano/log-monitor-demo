package com.example.demo.engine.model;

import com.example.demo.common.enums.LogLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 过滤结果 — 每个过滤器的输出，包含是否命中及判定详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterResult {
    /** 是否命中了某条规则 */
    private boolean matched;
    /** 判定级别 */
    private LogLevel level;
    /** 命中的规则名称/描述（用于前端展示异常原因） */
    private String matchedRuleName;
    /** 命中的关键字内容 */
    private String matchedKeyword;

    /**
     * 工厂方法 — 全链未命中时返回的普通日志结果
     */
    public static FilterResult normalLog() {
        return FilterResult.builder()
                .matched(false)
                .level(LogLevel.NORMAL)
                .build();
    }
}
