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
public class WsRuntimeSummaryHandler extends TextWebSocketHandler {

    private final LiveUpdateService liveUpdateService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        log.debug("Runtime websocket connected: {}", session.getId());
        liveUpdateService.registerRuntimeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        liveUpdateService.unregisterRuntimeSession(session);
        log.debug("Runtime websocket closed: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        liveUpdateService.unregisterRuntimeSession(session);
        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ignored) { }
        }
    }
}