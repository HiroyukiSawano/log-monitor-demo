package com.example.demo.config;

import com.example.demo.module.rule.service.FilterRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * 规则引擎组件装配配置
 * <p>
 * 匹配器实例现由 FilterRuleService 内部按 Agent 管理，
 * 此处仅负责在应用就绪后触发全量规则加载。
 */
@Slf4j
@Configuration
public class EngineConfig {

    private final FilterRuleService filterRuleService;

    public EngineConfig(FilterRuleService filterRuleService) {
        this.filterRuleService = filterRuleService;
    }

    /**
     * 应用完全就绪后（DB 表已建好）再加载规则
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            filterRuleService.rebuildAllMatchers();
            log.info("[EngineConfig] 规则引擎初始化完成，规则已从 DB 加载");
        } catch (Exception e) {
            log.warn("[EngineConfig] 规则引擎初始化跳过: {}", e.getMessage());
        }
    }
}
