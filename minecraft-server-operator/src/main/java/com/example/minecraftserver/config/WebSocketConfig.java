package com.example.minecraftserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.minecraftserver.security.WsAuthHandshakeInterceptor;
import com.example.minecraftserver.ws.WsRuntimeSummaryHandler;
import com.example.minecraftserver.ws.WsServerDetailsHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WsAuthHandshakeInterceptor webSocketAuthHandshakeInterceptor;
    private final WsRuntimeSummaryHandler runtimeSummaryWebSocketHandler;
    private final WsServerDetailsHandler serverDetailsWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(runtimeSummaryWebSocketHandler, "/ws/runtime")
            .addInterceptors(webSocketAuthHandshakeInterceptor)
            .setAllowedOriginPatterns("*");

        registry.addHandler(serverDetailsWebSocketHandler, "/ws/server-details")
            .addInterceptors(webSocketAuthHandshakeInterceptor)
            .setAllowedOriginPatterns("*");
    }
}