package com.example.minecraftserver.ws;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.minecraftserver.service.LiveUpdateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsServerDetailsHandler extends TextWebSocketHandler {

    private final LiveUpdateService liveUpdateService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.debug("Server details websocket connected: {}", session.getId());
        liveUpdateService.registerServerSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        liveUpdateService.unregisterServerSession(session);
        log.debug("Server details websocket closed: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        liveUpdateService.unregisterServerSession(session);
        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ignored) { }
        }
    }
}