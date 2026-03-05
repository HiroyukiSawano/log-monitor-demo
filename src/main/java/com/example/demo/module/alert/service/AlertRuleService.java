package com.example.demo.module.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.module.alert.entity.AlertRule;
import com.example.demo.module.alert.mapper.AlertRuleMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 告警规则 CRUD 管理
 */
@Service
public class AlertRuleService {

    private final AlertRuleMapper ruleMapper;

    public AlertRuleService(AlertRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /**
     * 获取某 Agent 适用的告警规则（含全局 *）
     */
    public List<AlertRule> listByAgent(String agentId) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(AlertRule::getAgentId, agentId)
                .or().eq(AlertRule::getAgentId, "*"));
        wrapper.eq(AlertRule::getEnabled, true);
        wrapper.orderByDesc(AlertRule::getId);
        return ruleMapper.selectList(wrapper);
    }

    /**
     * 获取所有规则（管理用）
     */
    public List<AlertRule> listAll() {
        return ruleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .orderByDesc(AlertRule::getId));
    }

    /**
     * 获取某 Agent 专属的规则（不含全局）
     */
    public List<AlertRule> listByAgentOnly(String agentId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getAgentId, agentId)
                .orderByDesc(AlertRule::getId));
    }

    public AlertRule getById(Long id) {
        return ruleMapper.selectById(id);
    }

    public AlertRule create(AlertRule rule) {
        rule.setCreateTime(new Date());
        rule.setUpdateTime(new Date());
        if (rule.getEnabled() == null)
            rule.setEnabled(true);
        if (rule.getCooldownSec() == null)
            rule.setCooldownSec(300);
        if (rule.getOperator() == null)
            rule.setOperator("GT");
        if (rule.getAlertLevel() == null)
            rule.setAlertLevel("WARNING");
        ruleMapper.insert(rule);
        return rule;
    }

    public AlertRule update(Long id, AlertRule rule) {
        AlertRule existing = ruleMapper.selectById(id);
        if (existing == null)
            return null;
        rule.setId(id);
        rule.setUpdateTime(new Date());
        ruleMapper.updateById(rule);
        return ruleMapper.selectById(id);
    }

    public void delete(Long id) {
        ruleMapper.deleteById(id);
    }

    public void toggle(Long id, boolean enabled) {
        AlertRule rule = ruleMapper.selectById(id);
        if (rule != null) {
            rule.setEnabled(enabled);
            rule.setUpdateTime(new Date());
            ruleMapper.updateById(rule);
        }
    }
}
