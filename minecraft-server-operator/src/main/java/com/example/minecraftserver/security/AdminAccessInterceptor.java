package com.example.minecraftserver.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.minecraftserver.config.AppProperties;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAccessInterceptor implements HandlerInterceptor {

    private final AppProperties appProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MyException.throwIf(
            !isLocalNetworkRequest(request), 
            ErrorCode.ADMIN_AREA_LOCAL_NETWORK_ONLY
        );
        return true;
    }

    public boolean isLocalNetworkRequest(HttpServletRequest request) {
        String clientAddress = extractClientAddress(request);
        if (clientAddress == null || clientAddress.isBlank()) {
            return false;
        }

        if (isExplicitlyAllowed(clientAddress)) {
            return true;
        }

        String normalized = clientAddress.trim().toLowerCase();
        if ("::1".equals(normalized) || normalized.startsWith("fc") || normalized.startsWith("fd")) {
            return true;
        }

        try {
            InetAddress address = InetAddress.getByName(clientAddress);
            return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private boolean isExplicitlyAllowed(String clientAddress) {
        List<String> allowedIpAddresses = appProperties.getAdmin().getAllowedIpAddresses();
        if (allowedIpAddresses == null || allowedIpAddresses.isEmpty()) {
            return false;
        }

        for (String allowedIpAddress : allowedIpAddresses) {
            if (allowedIpAddress == null || allowedIpAddress.isBlank()) {
                continue;
            }

            if (addressesMatch(clientAddress, allowedIpAddress.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean addressesMatch(String left, String right) {
        try {
            return InetAddress.getByName(left).equals(InetAddress.getByName(right));
        } catch (UnknownHostException ex) {
            return left.trim().equalsIgnoreCase(right.trim());
        }
    }

    private String extractClientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}