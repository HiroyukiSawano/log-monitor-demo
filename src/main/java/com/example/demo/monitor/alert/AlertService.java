package com.example.demo.monitor.alert;

import com.example.demo.engine.model.FilterResult;
import com.example.demo.monitor.health.ServerHealthState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 告警服务 — 预留接口，后续对接通知渠道（钉钉/企微/邮件等）
 */
@Slf4j
@Service
public class AlertService {

    /**
     * 触发告警
     *
     * @param state        当前健康状态
     * @param filterResult 触发告警的过滤结果
     * @param logSnippet   日志片段
     */
    public void triggerAlert(ServerHealthState state, FilterResult filterResult, String logSnippet) {
        log.warn("[ALERT] 🚨 agentId={}, status={}, level={}, rule={}, keyword={}, log={}",
                state.getAgentId(),
                state.getStatus(),
                filterResult.getLevel(),
                filterResult.getMatchedRuleName(),
                filterResult.getMatchedKeyword(),
                logSnippet.length() > 200 ? logSnippet.substring(0, 200) + "..." : logSnippet);

        // TODO: 对接实际通知渠道
        // - 钉钉 WebHook
        // - 企业微信机器人
        // - 邮件 SMTP
    }
}
