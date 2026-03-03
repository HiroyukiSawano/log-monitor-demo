package com.example.demo.engine.filter;

import com.example.demo.common.enums.LogLevel;
import com.example.demo.engine.matcher.AhoCorasickMatcher;
import com.example.demo.engine.model.FilterResult;
import com.example.demo.engine.model.LogContext;

/**
 * 第三步：基础特征过滤器 (order = 30)
 * <p>
 * 从数据库加载 BASIC 类型规则关键字，通过 AC 自动机匹配。
 * 命中 → UNKNOWN_ERROR（未知系统错误，记录并告警 🟡）
 */
public class BasicFeatureFilter implements LogFilter {

    private final AhoCorasickMatcher basicMatcher;

    public BasicFeatureFilter(AhoCorasickMatcher basicMatcher) {
        this.basicMatcher = basicMatcher;
    }

    @Override
    public FilterResult doFilter(LogContext context) {
        String matched = basicMatcher.matchFirst(context.getRawLine());
        if (matched != null) {
            return FilterResult.builder()
                    .matched(true)
                    .level(LogLevel.UNKNOWN_ERROR)
                    .matchedRuleName("基础特征匹配")
                    .matchedKeyword(matched)
                    .build();
        }
        return FilterResult.builder().matched(false).build();
    }

    @Override
    public int getOrder() {
        return 30;
    }
}
