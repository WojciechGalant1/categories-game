package com.example.panstwamiasta.config;

import com.example.panstwamiasta.websocket.RoomWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private RoomWebSocketHandler roomWebSocketHandler;

    @Value("${app.ws.allowed-origins:*}")
    private String wsAllowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] patterns = "*".equals(wsAllowedOrigins.trim())
                ? new String[]{"*"}
                : Arrays.stream(wsAllowedOrigins.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);

        registry.addHandler(roomWebSocketHandler, "/api/ws/rooms/*")
                .setAllowedOriginPatterns(patterns);
    }
}
