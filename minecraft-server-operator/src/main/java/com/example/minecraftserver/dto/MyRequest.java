package com.example.minecraftserver.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

public final class MyRequest {

    @Data
    public static class Login {

        @Pattern(
            regexp = "^[a-zA-Z0-9_-]{3,32}$",
            message = "Username must contain only letters, digits, underscore, hyphen; length 3-32."
        )
        private String username;

        @Size(
            min = 3, max = 100,
            message = "Password length must be between 3 and 100"
        )
        private String password;

        @Pattern(
            regexp = "^[0-9]{6}$",
            message = "TOTP code must contain exactly 6 digits"
        )
        private String totpCode;

        @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "Minecraft UUID must be a valid UUID"
        )
        private String minecraftUuid;

        @Pattern(
            regexp = "^[a-zA-Z0-9_]{3,16}$",
            message = "Minecraft username must contain only letters, digits, underscore; length 3-16"
        )
        private String minecraftUsername;
    }

    @Data
    public static class Registration {

        @Pattern(
            regexp = "^[a-zA-Z0-9_-]{3,32}$",
            message = "Username must contain only letters, digits, underscore, hyphen; length 3-32."
        )
        private String username;

        @Size(
            min = 3, max = 100,
            message = "Password length must be between 3 and 100"
        )
        private String password;

        private String inviteToken;
    }

    @Data
    public static class AccountMinecraftUpdate {

        @Pattern(
            regexp = "^[0-9]{6}$",
            message = "TOTP code must contain exactly 6 digits"
        )
        private String totpCode;

        @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "Minecraft UUID must be a valid UUID"
        )
        private String minecraftUuid;

        @Pattern(
            regexp = "^[a-zA-Z0-9_]{3,16}$",
            message = "Minecraft username must contain only letters, digits, underscore; length 3-16"
        )
        private String minecraftUsername;
    }

    @Data
    public static class AccountPasswordUpdate {

        @Pattern(
            regexp = "^[0-9]{6}$",
            message = "TOTP code must contain exactly 6 digits"
        )
        private String totpCode;

        @Size(
            min = 3, max = 100,
            message = "Password length must be between 3 and 100"
        )
        private String newPassword;
    }

    @Data
    public static class AccountProfileUpdate {

        @Pattern(
            regexp = "^[0-9]{6}$",
            message = "TOTP code must contain exactly 6 digits"
        )
        private String totpCode;

        @Pattern(
            regexp = "^[a-zA-Z0-9_-]{3,32}$",
            message = "Username must contain only letters, digits, underscore, hyphen; length 3-32."
        )
        private String username;
    }

    @Data
    public static class CreateServer {

        @Pattern(
            regexp = "^(?!.*\\.\\.)(?!^\\.)(?!.*\\.$)[a-zA-Z0-9_.-]{1,32}$",
            message = "Invalid server name. Allowed: letters, digits, underscore, hyphen, dot (but not at start/end, no consecutive dots); length 1-32."
        )
        private String serverName;

        @Size(min = 1, max = 50, message = "Minecraft version cannot be empty")
        private String minecraftVersion;

        @Size(min = 1, max = 50, message = "Loader version cannot be empty")
        private String loaderVersion;

        @Size(min = 1, max = 50, message = "Installer version cannot be empty")
        private String installerVersion;

        @JsonProperty("isPublic")
        @JsonAlias("public")
        private boolean isPublic;

        @Valid
        private ServerSettings settings;
    }

    @Data
    public static class RuntimeSettings {

        @Min(value = 1, message = "Max running servers must be at least 1")
        @Max(value = 100, message = "Max running servers must not exceed 100")
        private Integer maxRunningServers;

        @Min(value = 1, message = "Max total memory must be at least 1 GB")
        @Max(value = 512, message = "Max total memory must not exceed 512 GB")
        private Integer maxTotalMemoryGb;
    }

    @Data
    public static class ServerConfiguration {

        @JsonProperty("isPublic")
        @JsonAlias("public")
        private Boolean isPublic;

        @Valid
        private ServerSettings settings;
    }

    @Data
    public static class ServerSettings {

        @Size(min = 1, max = 120, message = "MOTD length must be between 1 and 120")
        private String motd;

        @Min(value = 1, message = "Max players must be at least 1")
        @Max(value = 200, message = "Max players must be at most 200")
        private Integer maxPlayers;

        @Pattern(
            regexp = "^(?!.*\\.\\.)(?!^\\.)(?!.*\\.$)[a-zA-Z0-9_.-]{1,32}$",
            message = "Invalid level name"
        )
        private String levelName;

        private Boolean allowNether;

        private Boolean pvp;

        @Pattern(regexp = "(?i)^(peaceful|easy|normal|hard)$", message = "Difficulty must be peaceful, easy, normal, or hard")
        private String difficulty;

        @Pattern(regexp = "(?i)^(survival|creative|adventure|spectator)$", message = "Gamemode must be survival, creative, adventure, or spectator")
        private String gamemode;

        @Min(value = 2, message = "View distance must be at least 2")
        @Max(value = 32, message = "View distance must be at most 32")
        private Integer viewDistance;

        @Min(value = 2, message = "Simulation distance must be at least 2")
        @Max(value = 32, message = "Simulation distance must be at most 32")
        private Integer simulationDistance;
    }

    @Data
    public static class StartServer {

        @Min(value = 1, message = "Server memory must be at least 1 GB")
        @Max(value = 64, message = "Server memory must not exceed 64 GB")
        private Integer memoryGb;
    }

    @Data
    public static class TotpVerify {

        @Pattern(
            regexp = "^[0-9]{6}$",
            message = "TOTP code must contain exactly 6 digits"
        )
        private String code;
    }
}