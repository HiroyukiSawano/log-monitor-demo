package com.example.demo.module.alert.channel;

import com.example.demo.module.alert.entity.AlertEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 日志告警渠道（默认实现）— 将告警输出到系统日志
 */
@Slf4j
@Component
public class LogAlertChannel implements AlertChannel {

    @Override
    public String channelName() {
        return "LOG";
    }

    @Override
    public void send(AlertEvent event) {
        String levelTag = "CRITICAL".equals(event.getAlertLevel()) ? "🔴" : "⚠️";
        log.warn("[ALERT] {} [{}] agentId={} | {} | 值={} | {}",
                levelTag, event.getAlertLevel(), event.getAgentId(),
                event.getRuleName(), event.getMetricValue(), event.getMessage());
    }
}
