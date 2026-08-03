package com.example.velocityauth;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

record PluginConfiguration(String apiBase, String authLobbyServer) {

    private static final String CONFIG_FILE_NAME = "application.yml";
    private static final String DEFAULT_API_BASE = "http://80.93.100.117:2036";
    private static final String DEFAULT_AUTH_LOBBY_SERVER = "limbo";

    static PluginConfiguration load(Path dataDirectory, Logger logger) {
        Path configPath = dataDirectory.resolve(CONFIG_FILE_NAME);
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(configPath)) {
                copyDefaultConfig(configPath);
                logger.info("Created default plugin config at {}", configPath);
            }

            try (InputStream inputStream = Files.newInputStream(configPath)) {
                Object loadedConfig = new Yaml().load(inputStream);
                if (!(loadedConfig instanceof Map<?, ?> root)) {
                    logger.warn("Plugin config {} has unsupported format. Using defaults.", configPath);
                    return defaults();
                }

                String apiBase = normalizeApiBase(readString(root, "apiBase", DEFAULT_API_BASE));
                String authLobbyServer = readString(root, "authLobbyServer", DEFAULT_AUTH_LOBBY_SERVER).trim();
                if (authLobbyServer.isEmpty()) {
                    authLobbyServer = DEFAULT_AUTH_LOBBY_SERVER;
                }

                return new PluginConfiguration(apiBase, authLobbyServer);
            }
        } catch (Exception exception) {
            logger.warn("Failed to load plugin config from {}. Using defaults.", configPath, exception);
            return defaults();
        }
    }

    private static PluginConfiguration defaults() {
        return new PluginConfiguration(DEFAULT_API_BASE, DEFAULT_AUTH_LOBBY_SERVER);
    }

    private static void copyDefaultConfig(Path configPath) throws IOException {
        try (InputStream resourceStream = PluginConfiguration.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (resourceStream == null) {
                throw new IOException("Default application.yml resource was not found.");
            }
            Files.copy(resourceStream, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readString(Map<?, ?> root, String key, String defaultValue) {
        Object value = root.get(key);
        if (value == null) {
            return defaultValue;
        }

        String normalizedValue = String.valueOf(value).trim();
        return normalizedValue.isEmpty() ? defaultValue : normalizedValue;
    }

    private static String normalizeApiBase(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? DEFAULT_API_BASE : normalized;
    }
}