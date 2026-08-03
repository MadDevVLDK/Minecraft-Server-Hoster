package com.example.velocityauth;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class AuthMessages {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final String SEPARATOR = "&8----------------------------------------";

    void sendCommandHelp(Player player) {
        sendLegacy(player, "&6Доступные команды:");
        sendLegacy(player, " &e/login <логин> <пароль> [totp-код] &7- войти в аккаунт сайта.");
        sendLegacy(player, " &e/getmy &7- показать твои серверы.");
        sendLegacy(player, " &e/getpublic &7- показать публичные серверы.");
        sendLegacy(player, " &e/getaccessible &7- показать серверы с выданным доступом.");
        sendLegacy(player, " &e/join <serverId> &7- подключиться к выбранному серверу.");
        sendLegacy(player, " &e/unlinkaccount &7- отвязать текущий Minecraft-аккаунт от сайта.");
        sendSeparator(player);
    }

    void sendTargetServerHelp(Player player) {
        sendLegacy(player, "&6На игровом сервере доступны только:");
        sendLegacy(player, " &e/limbo &7- вернуться в limbo.");
        sendLegacy(player, " &e/serverinfo &7- показать информацию о текущем сервере.");
        sendSeparator(player);
    }

    void sendCommandUsage(Player player, String usage, String description) {
        sendLegacy(player, "&eИспользование: " + usage);
        sendLegacy(player, "&7" + description);
        sendSeparator(player);
    }

    void sendSuccess(Player player, String message) {
        sendLegacy(player, "&a" + message);
        sendSeparator(player);
    }

    void sendWarning(Player player, String message) {
        sendLegacy(player, "&e" + message);
        sendSeparator(player);
    }

    void sendError(Player player, String message) {
        sendLegacy(player, "&c" + message);
        sendSeparator(player);
    }

    void sendServerInfo(Player player, ActiveServerSession serverSession) {
        sendLegacy(player, "&6Ты подключен к серверу:");
        sendLegacy(player, " &7ID: &e" + serverSession.serverId());
        sendLegacy(player, " &7Имя: &f" + serverSession.serverName());
        sendLegacy(player, " &7Владелец: &b" + serverSession.ownerUsername());
        sendLegacy(player, " &7Версия MC: &a" + serverSession.minecraftVersion());
        sendLegacy(player, " &7Порт: &e" + serverSession.port());
        sendSeparator(player);
    }

    void sendServerEntry(Player player, JsonObject server, String clientVersion) {
        long id = server.get("id").getAsLong();
        String name = AuthJson.readString(server, "serverName", "unknown");
        String status = AuthJson.readString(server, "status", "unknown");
        String ownerUsername = AuthJson.readString(server, "ownerUsername", "unknown");
        String minecraftVersion = AuthJson.readString(server, "minecraftVersion", "unknown");
        boolean versionMatches = clientVersion != null && clientVersion.equalsIgnoreCase(minecraftVersion);
        String statusColor = "RUNNING".equals(status) ? "&a" : "&c";
        String versionColor = versionMatches ? "&a" : "&c";

        sendLegacy(player,
            " &e- &7[&6" + id + "&7] &f" + name +
            " &8| &7Владелец: &b" + ownerUsername +
            " &8| &7MC: " + versionColor + minecraftVersion +
            " &8| &7Статус: " + statusColor + mapServerStatus(status)
        );
    }

    public void sendLegacy(Player player, String message) {
        player.sendMessage(LEGACY_SERIALIZER.deserialize(message));
    }

    void sendSeparator(Player player) {
        sendLegacy(player, SEPARATOR);
    }

    private String mapServerStatus(String status) {
        return switch (status) {
            case "RUNNING" -> "запущен";
            case "STARTING" -> "запускается";
            case "STOPPED" -> "остановлен";
            default -> status;
        };
    }
}