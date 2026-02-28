package com.example.demo.engine.filter;

import com.example.demo.common.enums.LogLevel;
import com.example.demo.engine.model.FilterResult;
import com.example.demo.engine.model.LogContext;

/**
 * 第三步：基础特征过滤器 (order = 30)
 * <p>
 * 硬编码检查 ERROR / Exception / FATAL 等常见错误标识。
 * 命中 → UNKNOWN_ERROR（未知系统错误，记录并告警 🟡）
 */
public class BasicFeatureFilter implements LogFilter {

    /** 硬编码的基础错误特征关键字 */
    private static final String[] ERROR_KEYWORDS = {
            "ERROR",
            "Exception",
            "FATAL",
            "Stacktrace",
            "NullPointerException",
            "OutOfMemoryError"
    };

    @Override
    public FilterResult doFilter(LogContext context) {
        String line = context.getRawLine();
        if (line == null) {
            return FilterResult.builder().matched(false).build();
        }

        for (String keyword : ERROR_KEYWORDS) {
            if (line.contains(keyword)) {
                return FilterResult.builder()
                        .matched(true)
                        .level(LogLevel.UNKNOWN_ERROR)
                        .matchedRuleName("基础特征匹配")
                        .matchedKeyword(keyword)
                        .build();
            }
        }
        return FilterResult.builder().matched(false).build();
    }

    @Override
    public int getOrder() {
        return 30;
    }
}
