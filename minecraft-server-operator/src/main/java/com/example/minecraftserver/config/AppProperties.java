package com.example.minecraftserver.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String velocityUrl;
    private final Minecraft minecraft = new Minecraft();
    private final Admin admin = new Admin();
    private final Security security = new Security();
    private final Jwt jwt = new Jwt();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private Long expiration;
    }

    @Getter
    @Setter
    public static class Minecraft {
        private int basePort;
        private int maxPort;
        private int defaultMaxRunningServers;
        private int defaultMaxTotalMemoryMb;
        private int maxLogLines;
        private String serversDir;
        private String javaCommand;
    }

    @Getter
    @Setter
    public static class Admin {
        private List<String> allowedIpAddresses = new ArrayList<>();
        private int inviteHistoryPageSize;
    }

    @Getter
    @Setter
    public static class Security {
        private final Totp totp = new Totp();

        @Getter
        @Setter
        public static class Totp {
            private int secretSize;
            private int timeStepSeconds;
        }
    }
}