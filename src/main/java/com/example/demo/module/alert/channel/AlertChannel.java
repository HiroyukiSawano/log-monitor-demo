package com.example.demo.module.alert.channel;

import com.example.demo.module.alert.entity.AlertEvent;

/**
 * 告警通知渠道接口 — 策略模式，新增渠道只需实现此接口
 */
public interface AlertChannel {

    /** 渠道名称 (如 "LOG", "EMAIL", "WEBHOOK") */
    String channelName();

    /** 发送告警通知 */
    void send(AlertEvent event);
}
