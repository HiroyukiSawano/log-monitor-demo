package com.example.demo.engine.filter;

import com.example.demo.engine.model.FilterResult;
import com.example.demo.engine.model.LogContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * 过滤链编排器 — 按 order 排序后依次执行
 * <p>
 * 首个命中的 filter 决定最终结果，全部未命中则返回 NORMAL。
 */
@Slf4j
public class LogFilterChain {

    private final List<LogFilter> filters;

    public LogFilterChain(List<LogFilter> filters) {
        filters.sort(Comparator.comparingInt(LogFilter::getOrder));
        this.filters = filters;
    }

    /**
     * 执行链式过滤
     */
    public FilterResult execute(LogContext context) {
        for (LogFilter filter : filters) {
            FilterResult result = filter.doFilter(context);
            if (result.isMatched()) {
                log.debug("[FilterChain] 命中: filter={}, level={}, keyword={}",
                        filter.getClass().getSimpleName(), result.getLevel(), result.getMatchedKeyword());
                return result;
            }
        }
        return FilterResult.normalLog();
    }
}
