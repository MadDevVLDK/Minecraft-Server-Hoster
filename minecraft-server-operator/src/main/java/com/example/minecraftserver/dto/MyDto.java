package com.example.minecraftserver.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.minecraftserver.service.ServerProcessManager.ServerStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class MyDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameServer {
        private Long id;
        private Long ownerUserId;
        private String ownerUsername;
        private String serverName;
        private String minecraftVersion;
        private String loaderVersion;
        private String installerVersion;
        private Integer port;
        private LocalDateTime createdAt;
        private boolean isPublic;
        private int whitelistCount;
        private int allocatedMemoryMb;
        private ServerStatus status;
    }

    @Data
    @AllArgsConstructor
    public static class LiveUpdateEnvelope {
        private String type;
        private Object data;
    }

    @Data
    @AllArgsConstructor
    public static class RegistrationInvite {
        private String token;
        private String registrationUrl;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private LocalDateTime usedAt;
        private LocalDateTime deactivatedAt;
        private String usedByUsername;
        private boolean active;
        private String statusLabel;
    }

    @Data
    @AllArgsConstructor
    public static class RegistrationInviteHistoryEvent {
        private String token;
        private String registrationUrl;
        private String eventLabel;
        private LocalDateTime happenedAt;
        private String username;
    }

    @Data
    @AllArgsConstructor
    public static class RegistrationInviteHistoryPage {
        private List<RegistrationInviteHistoryEvent> items;
        private int page;
        private boolean hasMore;
    }

    @Data
    @AllArgsConstructor
    public static class RuntimeSettings {
        private int maxRunningServers;
        private int maxTotalMemoryGb;
        private int maxTotalMemoryMb;
    }

    @Data
    @AllArgsConstructor
    public static class RuntimeSummary {
        private int runningServers;
        private int maxRunningServers;
        private int allocatedMemoryMb;
        private int maxTotalMemoryMb;
        private int availableMemoryMb;
    }

    @Data
    @AllArgsConstructor
    public static class ServerDownloadInfo {
        private String serverName;
        private long sizeBytes;
        private String sizeLabel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerConfiguration {
        private boolean isPublic;
        private ServerSettings settings;
    }

    @Data
    @AllArgsConstructor
    public static class ServerLiveState {
        private GameServer server;
        private ServerConfiguration configuration;
        private List<WhitelistUser> whitelist;
        private List<String> mods;
        private List<String> logs;
        private RuntimeSummary runtimeSummary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerSettings {
        private String motd;
        private Integer maxPlayers;
        private String levelName;
        private Boolean allowNether;
        private Boolean pvp;
        private String difficulty;
        private String gamemode;
        private Integer viewDistance;
        private Integer simulationDistance;
    }

    @Data
    @AllArgsConstructor
    public static class TotpSetupResponse {
        private boolean enabled;
        private String secret;
        private String qrCodeDataUrl;
        private String manualEntryKey;
    }

    @Data
    @AllArgsConstructor
    public static class UserProfile {
        private Long id;
        private String username;
        private boolean totpEnabled;
        private String minecraftUuid;
        private String minecraftUsername;
        private boolean minecraftLinked;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Versions {
        private List<String> minecraftVersions;
        private List<String> loaderVersions;
        private List<String> installerVersions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WhitelistUser {
        private Long userId;
        private String username;
        private String minecraftUsername;
    }
}