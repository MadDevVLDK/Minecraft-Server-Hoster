package com.example.minecraftserver.security;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String SERVER_ID_ATTRIBUTE = "serverId";

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(USER_ID_ATTRIBUTE, jwtUtil.extractUserId(token));
        String serverId = parseQuery(request.getURI().getRawQuery()).get("serverId");
        if (serverId != null && !serverId.isBlank()) {
            attributes.put(SERVER_ID_ATTRIBUTE, serverId);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) { }

    private String resolveToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            String authHeader = httpServletRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }

        URI uri = request.getURI();
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return null;
        }

        return parseQuery(uri.getRawQuery()).get("token");
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        Arrays.stream(rawQuery.split("&"))
            .map(part -> part.split("=", 2))
            .filter(parts -> parts.length == 2)
            .forEach(parts -> values.put(parts[0], java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8)));
        return values;
    }
}