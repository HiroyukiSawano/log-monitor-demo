package com.example.demo.config;

import com.example.demo.engine.filter.*;
import com.example.demo.engine.matcher.AhoCorasickMatcher;
import com.example.demo.engine.matcher.RegexMatcherCache;
import com.example.demo.module.rule.service.FilterRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.Arrays;

/**
 * 规则引擎组件装配配置
 */
@Slf4j
@Configuration
public class EngineConfig {

    private final FilterRuleService filterRuleService;

    public EngineConfig(FilterRuleService filterRuleService) {
        this.filterRuleService = filterRuleService;
    }

    @Bean("criticalMatcher")
    public AhoCorasickMatcher criticalMatcher() {
        return new AhoCorasickMatcher();
    }

    @Bean("excludeMatcher")
    public AhoCorasickMatcher excludeMatcher() {
        return new AhoCorasickMatcher();
    }

    @Bean("basicMatcher")
    public AhoCorasickMatcher basicMatcher() {
        return new AhoCorasickMatcher();
    }

    @Bean
    public RegexMatcherCache regexMatcherCache() {
        return new RegexMatcherCache();
    }

    @Bean
    public CriticalRuleFilter criticalRuleFilter(@Qualifier("criticalMatcher") AhoCorasickMatcher criticalMatcher) {
        return new CriticalRuleFilter(criticalMatcher);
    }

    @Bean
    public ExcludeRuleFilter excludeRuleFilter(@Qualifier("excludeMatcher") AhoCorasickMatcher excludeMatcher) {
        return new ExcludeRuleFilter(excludeMatcher);
    }

    @Bean
    public BasicFeatureFilter basicFeatureFilter(@Qualifier("basicMatcher") AhoCorasickMatcher basicMatcher) {
        return new BasicFeatureFilter(basicMatcher);
    }

    @Bean
    public LogFilterChain logFilterChain(CriticalRuleFilter criticalRuleFilter,
            ExcludeRuleFilter excludeRuleFilter,
            BasicFeatureFilter basicFeatureFilter) {
        return new LogFilterChain(Arrays.asList(criticalRuleFilter, excludeRuleFilter, basicFeatureFilter));
    }

    /**
     * 应用完全就绪后（DB 表已建好）再加载规则
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        filterRuleService.setMatchers(criticalMatcher(), excludeMatcher(), basicMatcher(), regexMatcherCache());
        try {
            filterRuleService.rebuildMatchers();
            log.info("[EngineConfig] 规则引擎初始化完成，规则已从 DB 加载");
        } catch (Exception e) {
            log.warn("[EngineConfig] 规则引擎初始化跳过: {}", e.getMessage());
        }
    }
}
