package com.example.demo.engine.filter;

import com.example.demo.engine.model.FilterResult;
import com.example.demo.engine.model.LogContext;

/**
 * 日志过滤链节点接口 — 责任链模式
 * <p>
 * 每个过滤器决定：命中则终止链路，未命中则传递给下一节点。
 */
public interface LogFilter {

    /**
     * 处理单行日志
     *
     * @param context 日志上下文（包含原始行、来源应用、agentId等）
     * @return 过滤结果（命中/跳过 + 判定级别 + 原因描述）
     */
    FilterResult doFilter(LogContext context);

    /**
     * 过滤器优先级，数值越小越先执行
     */
    int getOrder();
}
