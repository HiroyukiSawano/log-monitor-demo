package com.example.demo.websocket.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 统一信封实体 — 所有 JSON 文本帧的顶层结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsMessage {
    /** 消息类型标识 */
    private String type;
    /** 采集端唯一 ID */
    private String agentId;
    /** 时间戳 (epoch ms) */
    private Long timestamp;
    /** 业务载荷 (动态结构，由 type 决定) */
    private JsonNode payload;
}
