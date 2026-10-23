package com.wayfare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Kích hoạt Simple Broker để gửi tin nhắn tới /topic (cho phòng nhóm) và /queue (cho cá nhân)
        config.enableSimpleBroker("/topic", "/queue");
        // Prefix cho các tin nhắn từ client gửi lên server
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Raw WebSocket endpoint for modern STOMP clients (@stomp/stompjs on Web & Mobile)
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*");

        // Optional SockJS fallback
        registry.addEndpoint("/ws/chat/sockjs", "/ws/chat-sockjs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
