package com.example.demo.engine.filter;

import com.example.demo.common.enums.LogLevel;
import com.example.demo.engine.matcher.AhoCorasickMatcher;
import com.example.demo.engine.model.FilterResult;
import com.example.demo.engine.model.LogContext;

/**
 * 第一步：强关注规则过滤器 (order = 10)
 * <p>
 * 命中 → CRITICAL（立即告警 🔴）
 */
public class CriticalRuleFilter implements LogFilter {

    private final AhoCorasickMatcher criticalMatcher;

    public CriticalRuleFilter(AhoCorasickMatcher criticalMatcher) {
        this.criticalMatcher = criticalMatcher;
    }

    @Override
    public FilterResult doFilter(LogContext context) {
        String matched = criticalMatcher.matchFirst(context.getRawLine());
        if (matched != null) {
            return FilterResult.builder()
                    .matched(true)
                    .level(LogLevel.CRITICAL)
                    .matchedRuleName("强关注规则")
                    .matchedKeyword(matched)
                    .build();
        }
        return FilterResult.builder().matched(false).build();
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
