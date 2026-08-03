package com.example.minecraftserver.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.minecraftserver.config.AppProperties;
import com.example.minecraftserver.dto.MyDto;
import com.example.minecraftserver.dto.MyEvent;
import com.example.minecraftserver.dto.MyResponse;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;
import com.example.minecraftserver.security.WsAuthHandshakeInterceptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveUpdateService {
    
    private final AppProperties appProperties;
    
    private final ModService modService;
    private final ServerOrchestrator serverOrchestrator;

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> runtimeSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, ServerSubscription> serverSubscriptions = new ConcurrentHashMap<>();


    public void registerRuntimeSession(WebSocketSession session) throws IOException {
        runtimeSessions.add(session);
        sendEnvelope(session, "runtimeSummary", serverOrchestrator.getRuntimeSummary());
    }

    public void unregisterRuntimeSession(WebSocketSession session) {
        runtimeSessions.remove(session);
    }

    public void registerServerSession(WebSocketSession session) throws IOException {
        if (!(extractServerId(session) instanceof Long serverId)) {
            sendErrorEnvelope(session, ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Invalid server id");
            session.close(new CloseStatus(4400, "Invalid server id"));
            return;
        }

        if (!(extractUserId(session) instanceof Long userId)) {
            sendErrorEnvelope(session, ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED.getError());
            session.close(new CloseStatus(4403, "Access denied"));
            return;
        }

        var subscription = new ServerSubscription(session, serverId, userId);
        serverSubscriptions.put(session.getId(), subscription);

        try {
            syncServerSubscription(subscription, true);
        } catch (Exception ex) {
            handleSubscriptionFailure(subscription, ex);
        }
    }

    public void unregisterServerSession(WebSocketSession session) {
        serverSubscriptions.remove(session.getId());
    }

    @Scheduled(fixedDelay = 2000)
    public void pushScheduledUpdates() {
        // publishRuntimeSummaryIfChanged
        MyDto.RuntimeSummary summary = serverOrchestrator.getRuntimeSummary();
        runtimeSessions.forEach(session -> 
            sendIfChanged(session, "runtimeSummary", summary)
        );
        serverSubscriptions.values().forEach(subscription -> 
            sendIfChanged(subscription, "runtimeSummary", summary, false)
        );

        serverSubscriptions.values().forEach(subscription -> {
            try {
                syncServerSubscription(subscription, false);
            } catch (Exception ex) {
                handleSubscriptionFailure(subscription, ex);
            }
        });
    }

    @EventListener
    public void onRuntimeSummaryChanged(MyEvent.RuntimeSummaryChanged event) {
        // publishRuntimeSummaryNow
        MyDto.RuntimeSummary summary = serverOrchestrator.getRuntimeSummary();
        runtimeSessions.forEach(session -> 
            sendIfOpen(session, "runtimeSummary", summary)
        );
        serverSubscriptions.values().forEach(subscription -> 
            sendIfChanged(subscription, "runtimeSummary", summary, false)
        );
    }

    @EventListener
    public void onServerStateChanged(MyEvent.ServerStateChanged event) {
        //publishServerNow
        serverSubscriptions.values().stream()
            .filter(subscription -> subscription.serverId().equals(event.serverId()))
            .forEach(subscription -> {
                try {
                    syncServerSubscription(subscription, true);
                } catch (Exception ex) {
                    handleSubscriptionFailure(subscription, ex);
                }
            });
    }

    @EventListener
    public void onServerDeleted(MyEvent.ServerDeleted event) {
        List<ServerSubscription> affectedSubscriptions = serverSubscriptions.values().stream()
            .filter(subscription -> subscription.serverId().equals(event.serverId()))
            .toList();

        affectedSubscriptions.forEach(subscription -> {
            try {
                sendEnvelope(subscription.session(), "deleted", Map.of("serverId", event.serverId()));
                subscription.session().close(CloseStatus.NORMAL);
            } catch (IOException ex) {
                log.debug("Failed to close websocket for deleted server {}", event.serverId(), ex);
            } finally {
                serverSubscriptions.remove(subscription.session().getId());
            }
        });
    }

    private void syncServerSubscription(ServerSubscription subscription, boolean force) throws MyException {
        Long serverId = subscription.serverId();
        Long userId = subscription.userId();
        MyDto.GameServer server = serverOrchestrator.getViewerServerDto(serverId, userId);
        var dto = new MyDto.ServerLiveState(
            server,
            serverOrchestrator.getViewerServerConfiguration(serverId, userId),
            serverOrchestrator.getViewerServerWhitelist(serverId, userId),
            modService.listMods(serverId, server.getOwnerUserId()),
            serverOrchestrator.getViewerServerLogs(serverId, userId, appProperties.getMinecraft().getMaxLogLines()),
            serverOrchestrator.getRuntimeSummary()
        );
        
        sendIfChanged(subscription, "server", dto.getServer(), force);
        sendIfChanged(subscription, "configuration", dto.getConfiguration(), force);
        sendIfChanged(subscription, "whitelist", dto.getWhitelist(), force);
        sendIfChanged(subscription, "mods", dto.getMods(), force);
        sendIfChanged(subscription, "logs", dto.getLogs(), force);
        sendIfChanged(subscription, "runtimeSummary", dto.getRuntimeSummary(), force);
    }

    private void handleSubscriptionFailure(ServerSubscription subscription, Exception ex) {
        if (ex instanceof MyException myException && shouldCloseDeniedSession(myException)) {
            try {
                sendErrorEnvelope(subscription.session(), myException);
                subscription.session().close(new CloseStatus(4403, "Access denied"));
            } catch (IOException ioException) {
                log.debug("Failed to close denied session {}", subscription.session().getId(), ioException);
            } finally {
                serverSubscriptions.remove(subscription.session().getId());
            }
            return;
        }

        if (ex instanceof MyException myException) {
            log.warn("Live update business error for server {} session {}: {}", subscription.serverId(), subscription.session().getId(), myException.getError(), myException);
            sendErrorIfChanged(subscription, toErrorPayload(myException));
            return;
        }

        log.error("Live update sync failed for server {} session {}", subscription.serverId(), subscription.session().getId(), ex);
        sendErrorIfChanged(subscription, toErrorPayload(
            ErrorCode.INTERNAL_SERVER_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.INTERNAL_SERVER_ERROR.getError() + " --> " + ex.getMessage()
        ));
    }

    private boolean shouldCloseDeniedSession(MyException exception) {
        return switch (exception.getError()) {
            case ACCESS_DENIED,
                 NOT_OWNER,
                 SERVER_NOT_AVAILABLE_FOR_VIEWING,
                 SERVER_NOT_FOUND,
                 RESOURCE_NOT_FOUND -> true;
            default -> false;
        };
    }


    private void sendIfChanged(ServerSubscription subscription, String type, Object payload, boolean force) {
        try {
            String serialized = serializePayload(type, payload);
            if (!force && serialized.equals(subscription.lastPayloads().get(type))) {
                return;
            }

            sendRaw(subscription.session(), serialized);
            subscription.lastPayloads().put(type, serialized);
        } catch (IOException ex) {
            handleSessionIOException(subscription.session(), ex);
        }
    }

    private void sendErrorIfChanged(ServerSubscription subscription, MyResponse<Void> payload) {
        sendIfChanged(subscription, "error", payload, false);
    }

    private void sendIfChanged(WebSocketSession session, String type, Object payload) {
        try {
            String serialized = serializePayload(type, payload);
            Object previous = session.getAttributes().put("last-" + type, serialized);
            if (serialized.equals(previous)) {
                return;
            }

            sendRaw(session, serialized);
        } catch (IOException ex) {
            handleSessionIOException(session, ex);
        }
    }

    private void sendIfOpen(WebSocketSession session, String type, Object payload) {
        try {
            sendEnvelope(session, type, payload);
        } catch (IOException ex) {
            handleSessionIOException(session, ex);
        }
    }

    private void sendEnvelope(WebSocketSession session, String type, Object payload) throws IOException {
        sendRaw(session, serializePayload(type, payload));
    }

    private void sendErrorEnvelope(WebSocketSession session, MyException exception) throws IOException {
        sendErrorEnvelope(
            session,
            exception.getError(),
            ErrorCode.resolveStatus(exception.getError()),
            exception.getMessage()
        );
    }

    private void sendErrorEnvelope(WebSocketSession session, ErrorCode errorCode, HttpStatus status, String message) throws IOException {
        sendEnvelope(session, "error", toErrorPayload(errorCode, status, message));
    }

    private void sendRaw(WebSocketSession session, String payload) throws IOException {
        if (!session.isOpen()) {
            unregisterRuntimeSession(session);
            unregisterServerSession(session);
            return;
        }

        synchronized (session) {
            session.sendMessage(new TextMessage(payload));
        }
    }

    private void handleSessionIOException(WebSocketSession session, IOException ex) {
        log.debug("Closing broken websocket session {}", session.getId(), ex);
        runtimeSessions.remove(session);
        serverSubscriptions.remove(session.getId());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) { }
    }


    private String serializePayload(String type, Object payload) throws JsonProcessingException {
        return objectMapper.writeValueAsString(new MyDto.LiveUpdateEnvelope(type, payload));
    }

    private MyResponse<Void> toErrorPayload(MyException exception) {
        return toErrorPayload(
            exception.getError(),
            ErrorCode.resolveStatus(exception.getError()),
            exception.getMessage()
        );
    }

    private MyResponse<Void> toErrorPayload(ErrorCode errorCode, HttpStatus status, String message) {
        String resolvedMessage = (message == null || message.isBlank()) ? errorCode.getError() : message;
        return new MyResponse<>(false, errorCode.getCode(), resolvedMessage, null, status.value());
    }

    private Long extractUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(WsAuthHandshakeInterceptor.USER_ID_ATTRIBUTE);
        if (value instanceof Long userId) {
            return userId;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Long extractServerId(WebSocketSession session) {
        Object value = session.getAttributes().get(WsAuthHandshakeInterceptor.SERVER_ID_ATTRIBUTE);
        if (value instanceof String serverIdValue) {
            try {
                return Long.parseLong(serverIdValue);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return extractServerId(session.getUri());
    }

    private Long extractServerId(URI uri) {
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return null;
        }

        String rawServerId = parseQuery(uri.getRawQuery()).get("serverId");
        if (rawServerId == null || rawServerId.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(rawServerId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        Arrays.stream(rawQuery.split("&"))
            .map(part -> part.split("=", 2))
            .filter(parts -> parts.length == 2)
            .forEach(parts -> values.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8)));
        return values;
    }


    private record ServerSubscription(
        WebSocketSession session,
        Long serverId,
        Long userId,
        Map<String, String> lastPayloads
    ) {
        private ServerSubscription(WebSocketSession session, Long serverId, Long userId) {
            this(session, serverId, userId, new ConcurrentHashMap<>());
        }
    }
}