package com.example.demo.config;

import com.example.demo.websocket.handler.AgentWebSocketHandler;
import com.example.demo.websocket.handler.MonitorWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 配置类 — 注册 Agent 连接端点 + 运营监控端点
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentWebSocketHandler agentWebSocketHandler;
    private final MonitorWebSocketHandler monitorWebSocketHandler;

    public WebSocketConfig(AgentWebSocketHandler agentWebSocketHandler,
            MonitorWebSocketHandler monitorWebSocketHandler) {
        this.agentWebSocketHandler = agentWebSocketHandler;
        this.monitorWebSocketHandler = monitorWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent")
                .setAllowedOrigins("*");
        registry.addHandler(monitorWebSocketHandler, "/ws/monitor")
                .setAllowedOrigins("*");
    }

    /**
     * WebSocket 容器配置（最大消息大小、缓冲区等）
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 最大文本消息大小: 1MB
        container.setMaxTextMessageBufferSize(1024 * 1024);
        // 最大二进制消息大小: 5MB（文件传输分块）
        container.setMaxBinaryMessageBufferSize(5 * 1024 * 1024);
        return container;
    }
}
