package com.example.demo.engine.filter;

import com.example.demo.common.enums.LogLevel;
import com.example.demo.engine.matcher.AhoCorasickMatcher;
import com.example.demo.engine.model.FilterResult;
import com.example.demo.engine.model.LogContext;

/**
 * 第二步：排除规则过滤器 (order = 20)
 * <p>
 * 命中 → EXCLUDED（正常业务日志，直接丢弃 ✅）
 */
public class ExcludeRuleFilter implements LogFilter {

    private final AhoCorasickMatcher excludeMatcher;

    public ExcludeRuleFilter(AhoCorasickMatcher excludeMatcher) {
        this.excludeMatcher = excludeMatcher;
    }

    @Override
    public FilterResult doFilter(LogContext context) {
        String matched = excludeMatcher.matchFirst(context.getRawLine());
        if (matched != null) {
            return FilterResult.builder()
                    .matched(true)
                    .level(LogLevel.EXCLUDED)
                    .matchedRuleName("排除规则")
                    .matchedKeyword(matched)
                    .build();
        }
        return FilterResult.builder().matched(false).build();
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
