package com.example.minecraftserver.security;

import org.springframework.stereotype.Service;

import com.example.minecraftserver.config.AppProperties;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    public static final String ADMIN_SESSION_KEY = "adminAuthenticated";
    private static final String ADMIN_USERNAME = "admin";

    private final AppProperties appProperties;

    @PostConstruct
    void validateConfiguration() {
        String configuredPassword = appProperties.getAdmin().getPassword();
        if (configuredPassword == null || configuredPassword.length() < 20) {
            throw new IllegalStateException("app.admin.password must contain at least 20 characters");
        }
    }

    public String getUsername() {
        return ADMIN_USERNAME;
    }

    public boolean authenticate(String username, String password, HttpSession session) {
        if (!ADMIN_USERNAME.equals(username) || !appProperties.getAdmin().getPassword().equals(password)) {
            return false;
        }

        session.setAttribute(ADMIN_SESSION_KEY, Boolean.TRUE);
        return true;
    }

    public boolean isAuthenticated(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(ADMIN_SESSION_KEY));
    }

    public void logout(HttpSession session) {
        if (session == null) {
            return;
        }

        session.removeAttribute(ADMIN_SESSION_KEY);
    }
}