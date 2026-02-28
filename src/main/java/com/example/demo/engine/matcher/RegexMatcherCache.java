package com.example.demo.engine.matcher;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 预编译正则表达式缓存
 * <p>
 * 启动时或规则变更时 Pattern.compile() 编译并缓存，避免运行时重复编译。
 */
@Slf4j
public class RegexMatcherCache {

    /** keyword (正则表达式字符串) → 编译后的 Pattern */
    private final Map<String, Pattern> cache = new ConcurrentHashMap<>();

    /**
     * 用新的正则规则列表替换缓存
     */
    public void rebuild(List<String> regexPatterns) {
        cache.clear();
        if (regexPatterns == null)
            return;

        for (String regex : regexPatterns) {
            if (regex == null || regex.isEmpty())
                continue;
            try {
                cache.put(regex, Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                log.error("[Regex Cache] 正则编译失败: pattern={}", regex, e);
            }
        }
        log.info("[Regex Cache] 正则缓存重建完成，规则数量: {}", cache.size());
    }

    /**
     * 对单行日志执行所有正则匹配
     *
     * @param logLine 原始日志行
     * @return 首个匹配的正则表达式字符串，未匹配返回 null
     */
    public String matchFirst(String logLine) {
        if (logLine == null || logLine.isEmpty())
            return null;

        for (Map.Entry<String, Pattern> entry : cache.entrySet()) {
            if (entry.getValue().matcher(logLine).find()) {
                return entry.getKey();
            }
        }
        return null;
    }
}
