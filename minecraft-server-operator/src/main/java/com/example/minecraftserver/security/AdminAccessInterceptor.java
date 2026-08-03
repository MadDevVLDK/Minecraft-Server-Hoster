package com.example.minecraftserver.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.minecraftserver.dto.MyResponse;
import com.example.minecraftserver.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAccessInterceptor implements HandlerInterceptor {

    private final AdminAuthService adminAuthService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (adminAuthService.isAuthenticated(request.getSession(false))) {
            return true;
        }

        if (requiresJsonResponse(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(
                response.getWriter(),
                new MyResponse<>(
                    false,
                    ErrorCode.ACCESS_DENIED.getCode(),
                    "Admin authentication is required",
                    null,
                    HttpStatus.UNAUTHORIZED.value()
                )
            );
            return false;
        }

        response.sendRedirect(request.getContextPath() + "/admin/login");
        return false;
    }

    private boolean requiresJsonResponse(HttpServletRequest request) {
        String path = request.getServletPath();
        String acceptHeader = request.getHeader("Accept");
        return path.startsWith("/admin/uri/")
            || !"GET".equalsIgnoreCase(request.getMethod())
            || (acceptHeader != null && acceptHeader.contains(MediaType.APPLICATION_JSON_VALUE));
    }
}