package com.example.demo.module.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.engine.matcher.AhoCorasickMatcher;
import com.example.demo.engine.matcher.RegexMatcherCache;
import com.example.demo.module.rule.entity.FilterRule;
import com.example.demo.module.rule.mapper.FilterRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 过滤规则 Service — CRUD + 规则热加载
 * <p>
 * 规则变更时自动触发 AC 自动机 / 正则缓存 重建。
 */
@Slf4j
@Service
public class FilterRuleService {

    private final FilterRuleMapper ruleMapper;

    // 由 Phase 6 集成时配置注入，这里先引用
    private AhoCorasickMatcher criticalMatcher;
    private AhoCorasickMatcher excludeMatcher;
    private AhoCorasickMatcher basicMatcher;
    private RegexMatcherCache regexMatcherCache;

    public FilterRuleService(FilterRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /**
     * 注入匹配器实例（由配置类或集成阶段调用）
     */
    public void setMatchers(AhoCorasickMatcher criticalMatcher,
            AhoCorasickMatcher excludeMatcher,
            AhoCorasickMatcher basicMatcher,
            RegexMatcherCache regexMatcherCache) {
        this.criticalMatcher = criticalMatcher;
        this.excludeMatcher = excludeMatcher;
        this.basicMatcher = basicMatcher;
        this.regexMatcherCache = regexMatcherCache;
    }

    /**
     * 查询所有规则
     */
    public List<FilterRule> listAll() {
        return ruleMapper.selectList(null);
    }

    /**
     * 按类型查询启用的规则
     */
    public List<FilterRule> listEnabledByType(String ruleType) {
        LambdaQueryWrapper<FilterRule> wrapper = new LambdaQueryWrapper<FilterRule>()
                .eq(FilterRule::getRuleType, ruleType)
                .eq(FilterRule::getEnabled, true)
                .orderByAsc(FilterRule::getPriority);
        return ruleMapper.selectList(wrapper);
    }

    /**
     * 新增规则
     */
    public FilterRule create(FilterRule rule) {
        rule.setCreateTime(new Date());
        rule.setUpdateTime(new Date());
        ruleMapper.insert(rule);
        rebuildMatchers();
        return rule;
    }

    /**
     * 更新规则
     */
    public FilterRule update(FilterRule rule) {
        rule.setUpdateTime(new Date());
        ruleMapper.updateById(rule);
        rebuildMatchers();
        return rule;
    }

    /**
     * 删除规则
     */
    public void delete(Long id) {
        ruleMapper.deleteById(id);
        rebuildMatchers();
    }

    /**
     * 重建所有匹配器 — 规则变更时调用
     */
    public void rebuildMatchers() {
        try {
            // 重建强关注 AC 自动机
            if (criticalMatcher != null) {
                List<String> criticalKeywords = listEnabledByType("CRITICAL").stream()
                        .filter(r -> "CONTAINS".equals(r.getMatchMode()))
                        .map(FilterRule::getKeyword)
                        .collect(Collectors.toList());
                criticalMatcher.rebuild(criticalKeywords);
            }

            // 重建排除 AC 自动机
            if (excludeMatcher != null) {
                List<String> excludeKeywords = listEnabledByType("EXCLUDE").stream()
                        .filter(r -> "CONTAINS".equals(r.getMatchMode()))
                        .map(FilterRule::getKeyword)
                        .collect(Collectors.toList());
                excludeMatcher.rebuild(excludeKeywords);
            }

            // 重建基础特征 AC 自动机
            if (basicMatcher != null) {
                List<String> basicKeywords = listEnabledByType("BASIC").stream()
                        .filter(r -> "CONTAINS".equals(r.getMatchMode()))
                        .map(FilterRule::getKeyword)
                        .collect(Collectors.toList());
                basicMatcher.rebuild(basicKeywords);
            }

            // 重建正则缓存
            if (regexMatcherCache != null) {
                List<String> regexPatterns = listAll().stream()
                        .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                        .filter(r -> "REGEX".equals(r.getMatchMode()))
                        .map(FilterRule::getKeyword)
                        .collect(Collectors.toList());
                regexMatcherCache.rebuild(regexPatterns);
            }

            log.info("[RuleService] 匹配器重建完成");
        } catch (Exception e) {
            log.error("[RuleService] 匹配器重建失败", e);
        }
    }
}
