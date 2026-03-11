package com.example.demo.module.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.engine.filter.*;
import com.example.demo.engine.matcher.AhoCorasickMatcher;
import com.example.demo.engine.matcher.RegexMatcherCache;
import com.example.demo.module.rule.entity.FilterRule;
import com.example.demo.module.rule.mapper.FilterRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 过滤规则 Service — CRUD + 按 Agent 实例管理独立的匹配器集合
 * <p>
 * 每个 Agent 拥有独立的 AC 自动机和正则匹配器。
 * agent_id = '*' 的规则为全局规则，对所有 Agent 生效。
 * 规则变更时自动触发对应 Agent 的匹配器重建。
 */
@Slf4j
@Service
public class FilterRuleService {

    private final FilterRuleMapper ruleMapper;

    /**
     * 每个 agentId 对应的匹配器集合（包含 '*' 全局匹配器）
     */
    private final Map<String, AgentMatcherSet> agentMatchers = new ConcurrentHashMap<>();

    public FilterRuleService(FilterRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    // ==================== 内部匹配器集合 ====================

    /**
     * 每个 Agent 独有的匹配器集合
     */
    private static class AgentMatcherSet {
        final AhoCorasickMatcher criticalMatcher = new AhoCorasickMatcher();
        final AhoCorasickMatcher excludeMatcher = new AhoCorasickMatcher();
        final AhoCorasickMatcher basicMatcher = new AhoCorasickMatcher();
        final RegexMatcherCache regexMatcherCache = new RegexMatcherCache();
    }

    // ==================== CRUD ====================

    /**
     * 查询所有规则
     */
    public List<FilterRule> listAll() {
        return ruleMapper.selectList(null);
    }

    /**
     * 按 agentId 查询规则（包含全局 '*' 规则）
     */
    public List<FilterRule> listByAgentId(String agentId) {
        if (agentId == null || agentId.isEmpty()) {
            return listAll();
        }
        LambdaQueryWrapper<FilterRule> wrapper = new LambdaQueryWrapper<FilterRule>()
                .in(FilterRule::getAgentId, agentId, "*")
                .orderByAsc(FilterRule::getPriority);
        return ruleMapper.selectList(wrapper);
    }

    /**
     * 按类型查询启用的规则（全局）
     */
    public List<FilterRule> listEnabledByType(String ruleType) {
        LambdaQueryWrapper<FilterRule> wrapper = new LambdaQueryWrapper<FilterRule>()
                .eq(FilterRule::getRuleType, ruleType)
                .eq(FilterRule::getEnabled, true)
                .orderByAsc(FilterRule::getPriority);
        return ruleMapper.selectList(wrapper);
    }

    /**
     * 按类型查询某个 Agent 生效的规则（自身 + 全局 '*'）
     */
    public List<FilterRule> listEnabledForAgent(String agentId, String ruleType) {
        LambdaQueryWrapper<FilterRule> wrapper = new LambdaQueryWrapper<FilterRule>()
                .in(FilterRule::getAgentId, agentId, "*")
                .eq(FilterRule::getRuleType, ruleType)
                .eq(FilterRule::getEnabled, true)
                .orderByAsc(FilterRule::getPriority);
        return ruleMapper.selectList(wrapper);
    }

    /**
     * 新增规则
     */
    public FilterRule create(FilterRule rule) {
        if (rule.getAgentId() == null || rule.getAgentId().isEmpty()) {
            rule.setAgentId("*");
        }
        rule.setCreateTime(new Date());
        rule.setUpdateTime(new Date());
        ruleMapper.insert(rule);
        rebuildMatchersForAgent(rule.getAgentId());
        return rule;
    }

    /**
     * 更新规则
     */
    public FilterRule update(FilterRule rule) {
        rule.setUpdateTime(new Date());
        ruleMapper.updateById(rule);
        rebuildMatchersForAgent(rule.getAgentId());
        return rule;
    }

    /**
     * 删除规则
     */
    public void delete(Long id) {
        FilterRule rule = ruleMapper.selectById(id);
        ruleMapper.deleteById(id);
        if (rule != null) {
            rebuildMatchersForAgent(rule.getAgentId());
        }
    }

    // ==================== 匹配器管理 ====================

    /**
     * 获取指定 Agent 的过滤链
     * <p>
     * 如果该 Agent 有专属规则，使用专属匹配器；否则使用全局 '*' 匹配器。
     */
    public LogFilterChain getFilterChainForAgent(String agentId) {
        // 优先使用 agent 专属匹配器
        AgentMatcherSet matcherSet = agentMatchers.get(agentId);
        if (matcherSet == null) {
            // 回退到全局匹配器
            matcherSet = agentMatchers.get("*");
        }
        if (matcherSet == null) {
            // 尚未初始化，返回空链
            return new LogFilterChain(Collections.emptyList());
        }

        return new LogFilterChain(Arrays.asList(
                new CriticalRuleFilter(matcherSet.criticalMatcher),
                new ExcludeRuleFilter(matcherSet.excludeMatcher),
                new BasicFeatureFilter(matcherSet.basicMatcher)));
    }

    /**
     * 重建指定 Agent 的匹配器
     */
    public void rebuildMatchersForAgent(String agentId) {
        try {
            AgentMatcherSet matcherSet = agentMatchers.computeIfAbsent(agentId, k -> new AgentMatcherSet());
            List<FilterRule> rules;

            if ("*".equals(agentId)) {
                // 全局规则只取 agent_id = '*' 的
                rules = ruleMapper.selectList(new LambdaQueryWrapper<FilterRule>()
                        .eq(FilterRule::getAgentId, "*")
                        .eq(FilterRule::getEnabled, true)
                        .orderByAsc(FilterRule::getPriority));
            } else {
                // Agent 专属规则 = 自身 + 全局
                rules = listEnabledForAgent(agentId, null);
                // 不用 ruleType 过滤，下面分类处理
                rules = ruleMapper.selectList(new LambdaQueryWrapper<FilterRule>()
                        .in(FilterRule::getAgentId, agentId, "*")
                        .eq(FilterRule::getEnabled, true)
                        .orderByAsc(FilterRule::getPriority));
            }

            // 按类型分组重建
            rebuildMatcherFromRules(matcherSet.criticalMatcher, rules, "CRITICAL");
            rebuildMatcherFromRules(matcherSet.excludeMatcher, rules, "EXCLUDE");
            rebuildMatcherFromRules(matcherSet.basicMatcher, rules, "BASIC");

            // 重建正则缓存
            List<String> regexPatterns = rules.stream()
                    .filter(r -> "REGEX".equals(r.getMatchMode()))
                    .map(FilterRule::getKeyword)
                    .collect(Collectors.toList());
            matcherSet.regexMatcherCache.rebuild(regexPatterns);

            log.info("[RuleService] 匹配器重建完成: agentId={}", agentId);
        } catch (Exception e) {
            log.error("[RuleService] 匹配器重建失败: agentId={}", agentId, e);
        }
    }

    /**
     * 重建所有 Agent 的匹配器（应用启动时调用）
     */
    public void rebuildAllMatchers() {
        // 1. 先重建全局匹配器
        rebuildMatchersForAgent("*");

        // 2. 找出所有有专属规则的 agentId（排除 '*'）
        List<FilterRule> allRules = listAll();
        Set<String> agentIds = allRules.stream()
                .map(FilterRule::getAgentId)
                .filter(id -> id != null && !"*".equals(id))
                .collect(Collectors.toSet());

        for (String agentId : agentIds) {
            rebuildMatchersForAgent(agentId);
        }

        log.info("[RuleService] 全部匹配器重建完成, 全局 + {} 个 Agent 专属", agentIds.size());
    }

    /**
     * 兼容旧接口 — 重建全部匹配器
     */
    public void rebuildMatchers() {
        rebuildAllMatchers();
    }

    // ==================== 内部工具方法 ====================

    private void rebuildMatcherFromRules(AhoCorasickMatcher matcher, List<FilterRule> allRules, String ruleType) {
        List<String> keywords = allRules.stream()
                .filter(r -> ruleType.equals(r.getRuleType()))
                .filter(r -> "CONTAINS".equals(r.getMatchMode()))
                .map(FilterRule::getKeyword)
                .collect(Collectors.toList());
        matcher.rebuild(keywords);
    }
}
