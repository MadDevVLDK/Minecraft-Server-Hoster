package com.example.velocityauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Plugin(
    id = "velocityauth",
    name = "Velocity Auth",
    version = "1.0",
    authors = {"me"}
)
public class AuthPlugin {

    private static final Set<String> ALLOWED_TARGET_SERVER_COMMANDS = Set.of("limbo", "serverinfo");
    private static final int CHAT_CLEAR_LINES = 40;

    private final ProxyServer proxy;
    private final Logger logger;
    private final AuthMessages messages = new AuthMessages();
    private final AuthBackendClient backendClient;
    private final String authLobbyServer;
    private final PlayerSessionManager sessionManager = new PlayerSessionManager(messages);
    private final Map<String, ActiveServerSession> activeServerSessions = new ConcurrentHashMap<>();

    @Inject
    public AuthPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        PluginConfiguration configuration = PluginConfiguration.load(dataDirectory, logger);
        this.backendClient = new AuthBackendClient(configuration.apiBase());
        this.authLobbyServer = configuration.authLobbyServer();
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        var commandManager = proxy.getCommandManager();

        commandManager.register(
            commandManager.metaBuilder("login").build(),
            new SimpleCommand() {
                @Override
                public void execute(Invocation invocation) {
                    if (!(invocation.source() instanceof Player player)) {
                        return;
                    }

                    String[] args = invocation.arguments();
                    if (args.length < 2 || args.length > 3) {
                        messages.sendCommandUsage(player, "/login <логин> <пароль> [totp-код]", "Войти на сайт и при необходимости подтвердить вход TOTP-кодом.");
                        return;
                    }

                    performLogin(player, args[0], args[1], args.length == 3 ? args[2] : null);
                }
            }
        );

        commandManager.register(
            commandManager.metaBuilder("getmy").build(),
            new SimpleCommand() {
                @Override
                public void execute(Invocation invocation) {
                    if (!(invocation.source() instanceof Player player)) {
                        return;
                    }

                    String token = requireToken(player);
                    if (token != null) {
                        showServers(player, token, "/api/servers/proxy/my", "&6Твои серверы:");
                    }
                }
            }
        );

        commandManager.register(
            commandManager.metaBuilder("getpublic").build(),
            new SimpleCommand() {
                @Override
                public void execute(Invocation invocation) {
                    if (!(invocation.source() instanceof Player player)) {
                        return;
                    }

                    String token = requireToken(player);
                    if (token != null) {
                        showServers(player, token, "/api/servers/proxy/public", "&6Публичные серверы:");
                    }
                }
            }
        );

        commandManager.register(
            commandManager.metaBuilder("getaccessible").build(),
            new SimpleCommand() {
                @Override
                public void execute(Invocation invocation) {
                    if (!(invocation.source() instanceof Player player)) {
                        return;
                    }

                    String token = requireToken(player);
                    if (token != null) {
                        showServers(player, token, "/api/servers/proxy/accessible", "&6Серверы, к которым тебе выдали доступ:");
                    }
                }
            }
        );

        commandManager.register(
            commandManager.metaBuilder("join").build(),
            new SimpleCommand() {
                @Override
                public void execute(Invocation invocation) {
                    if (!(invocation.source() instanceof Player player)) {
                        return;
                    }

                    String[] args = invocation.arguments();
                    if (args.length != 1) {
                        messages.sendCommandUsage(player, "/join <serverId>", "Подключиться к конкретному серверу по его ID.");
                        return;
                    }

                    String token = requireToken(player);
                    if (token == null) {
                        return;
                    }

                    try {
                        resolveAndJoin(player, Long.parseLong(args[0]), token);
                    } catch (NumberFormatException e) {
                        messages.sendError(player, "ID сервера должен быть числом. Посмотри список через /getmy.");
                    }
                }
            }
        );

        commandManager.register(
            commandManager.metaBuilder("unlinkaccount").build(),
            new SimpleCommand() {
                @Override
                public void execute(Invocation invocation) {
                    if (!(invocation.source() instanceof Player player)) {
                        return;
                    }

                    String token = requireToken(player);
                    if (token != null) {
                        unlinkMinecraftAccount(player, token);
                    }
                }
            }
        );

        commandManager.register(
            commandManager.metaBuilder("limbo").build(),
            new SimpleCommand() {
                @Override
                public void execute(Invocation invocation) {
                    if (!(invocation.source() instanceof Player player)) {
                        return;
                    }

                    sendPlayerToLimbo(player, true);
                }
            }
        );

        commandManager.register(
            commandManager.metaBuilder("serverinfo").build(),
            new SimpleCommand() {
                @Override
                public void execute(Invocation invocation) {
                    if (!(invocation.source() instanceof Player player)) {
                        return;
                    }

                    ActiveServerSession serverSession = getActiveServerSession(player);
                    if (serverSession == null) {
                        messages.sendWarning(player, "Ты сейчас не на игровом сервере, запущенном через панель.");
                        return;
                    }

                    messages.sendServerInfo(player, serverSession);
                }
            }
        );

        logger.info("VelocityAuth plugin loaded.");
        logger.info("Registered commands: /login, /getmy, /getpublic, /getaccessible, /join, /unlinkaccount, /limbo, /serverinfo");
        logger.info("Config loaded: apiBase={}, authLobbyServer={}", backendClient == null ? "unknown" : "configured", authLobbyServer);
        if (proxy.getServer(authLobbyServer).isEmpty()) {
            logger.warn("Configured auth lobby server '{}' is not registered in Velocity.", authLobbyServer);
        }
    }

    private void performLogin(Player player, String username, String password, String totpCode) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return backendClient.login(player, username, password, totpCode);
            } catch (Exception e) {
                logger.warn("Login error for player {}", player.getUsername(), e);
                return LoginResult.failure("Сервис авторизации временно недоступен. Попробуй снова чуть позже.");
            }
        }).thenAccept(result -> {
            if (result.token() != null) {
                sessionManager.activateSession(player, result.token());
                messages.sendSuccess(player, "Авторизация прошла успешно.");
                messages.sendCommandHelp(player);
                return;
            }

            if (result.totpRequired()) {
                messages.sendWarning(player, "Для этого аккаунта включен TOTP. Используй: /login <логин> <пароль> <totp-код>");
                return;
            }

            messages.sendError(player, result.message());
        });
    }

    private void showServers(Player player, String token, String endpoint, String title) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return new ServerListResult(backendClient.fetchServerList(player, token, endpoint), null, false);
            } catch (BackendException ex) {
                logger.warn("showServers rejected for player {}: status={} code={} endpoint={} message={}", player.getUsername(), ex.status(), ex.code(), endpoint, ex.getMessage());
                return new ServerListResult(null, ex.getMessage(), ex.status() == 401);
            } catch (Exception e) {
                logger.warn("showServers error for player {} endpoint {}", player.getUsername(), endpoint, e);
                return new ServerListResult(null, "Не удалось получить список серверов. Попробуй позже.", false);
            }
        }).thenAccept(result -> {
            if (result.authExpired()) {
                expirePlayerSession(player);
                return;
            }

            JsonArray servers = result.servers();
            if (servers == null || servers.isEmpty()) {
                messages.sendError(player, result.errorMessage() != null ? result.errorMessage() : "Для этого списка серверов пока ничего нет.");
                return;
            }

            String clientVersion = getClientVersion(player);
            messages.sendLegacy(player, title);
            for (int i = 0; i < servers.size(); i++) {
                messages.sendServerEntry(player, servers.get(i).getAsJsonObject(), clientVersion);
            }
            messages.sendLegacy(player, "&7Твоя версия клиента: &b" + clientVersion);
            messages.sendLegacy(player, "&eИспользуй /join <serverId>, чтобы подключиться.");
            messages.sendSeparator(player);
        });
    }

    private void resolveAndJoin(Player player, long serverId, String token) {
        CompletableFuture.supplyAsync(() -> {
            try {
                JsonArray servers = backendClient.fetchServerList(player, token, "/api/servers/proxy/available");
                JsonObject targetServer = backendClient.findServerById(servers, serverId);
                if (targetServer == null) {
                    return JoinTarget.failure("Сервер с таким ID не найден в доступном тебе списке.", false);
                }

                String minecraftVersion = AuthJson.readString(targetServer, "minecraftVersion", "unknown");
                String clientVersion = getClientVersion(player);
                if (!versionsMatch(clientVersion, minecraftVersion)) {
                    return JoinTarget.failure(
                        "Версия не совпадает. У тебя клиент " + clientVersion + ", а сервер запущен на " + minecraftVersion + ".",
                        false
                    );
                }

                int port = backendClient.resolveServerPort(player, serverId, token);
                String serverName = AuthJson.readString(targetServer, "serverName", "unknown");
                String ownerUsername = AuthJson.readString(targetServer, "ownerUsername", "unknown");
                return JoinTarget.success(serverId, serverName, ownerUsername, port, minecraftVersion);
            } catch (BackendException ex) {
                logger.warn("Resolve rejected for player {} server {}: status={} code={} message={}", player.getUsername(), serverId, ex.status(), ex.code(), ex.getMessage());
                return JoinTarget.failure(ex.getMessage(), ex.status() == 401);
            } catch (Exception e) {
                logger.warn("Resolve error for player {} server {}", player.getUsername(), serverId, e);
                return JoinTarget.failure("Не удалось подключить тебя к серверу. Попробуй позже.", false);
            }
        }).thenAccept(targetInfo -> {
            if (targetInfo.authExpired()) {
                expirePlayerSession(player);
                return;
            }

            if (targetInfo.port() <= 0) {
                messages.sendError(player, targetInfo.errorMessage() != null ? targetInfo.errorMessage() : "Доступ запрещен или на сервере произошла ошибка.");
                return;
            }

            String registeredName = "server_" + serverId;
            ServerInfo serverInfo = new ServerInfo(registeredName, InetSocketAddress.createUnresolved("localhost", targetInfo.port()));
            RegisteredServer target = proxy.getServer(registeredName).orElseGet(() -> proxy.registerServer(serverInfo));
            activeServerSessions.put(
                player.getUniqueId().toString(),
                new ActiveServerSession(
                    targetInfo.serverId(),
                    safeValue(targetInfo.serverName()),
                    safeValue(targetInfo.ownerUsername()),
                    safeValue(targetInfo.minecraftVersion()),
                    targetInfo.port(),
                    registeredName
                )
            );
            player.createConnectionRequest(target).fireAndForget();
            messages.sendSuccess(player, "Покидаем limbo и подключаем тебя к выбранному серверу...");
        });
    }

    private void unlinkMinecraftAccount(Player player, String token) {
        String uuid = player.getUniqueId().toString();
        CompletableFuture.supplyAsync(() -> {
            try {
                backendClient.unlinkMinecraftAccount(uuid, token);
                return OperationResult.successResult();
            } catch (BackendException ex) {
                logger.warn("Unlink rejected for player {}: status={} code={} message={}", player.getUsername(), ex.status(), ex.code(), ex.getMessage());
                return OperationResult.failure(ex.getMessage(), ex.status() == 401);
            } catch (Exception e) {
                logger.warn("Unlink error for player {}", player.getUsername(), e);
                return OperationResult.failure("Не удалось отвязать Minecraft-аккаунт. Попробуй позже.", false);
            }
        }).thenAccept(result -> {
            if (result.authExpired()) {
                expirePlayerSession(player);
                return;
            }

            if (result.success()) {
                sessionManager.clearSession(player);
                sessionManager.startLoginReminders(player);
                messages.sendSuccess(player, "Minecraft-аккаунт отвязан. Теперь можно войти заново с нужного аккаунта.");
                return;
            }

            messages.sendError(player, result.message());
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        sessionManager.clearSession(event.getPlayer());
        activeServerSessions.remove(event.getPlayer().getUniqueId().toString());
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        activeServerSessions.remove(player.getUniqueId().toString());
        proxy.getServer(authLobbyServer).ifPresent(event::setInitialServer);

        if (sessionManager.getToken(player) != null) {
            messages.sendSuccess(player, "Авторизация уже активна.");
            messages.sendCommandHelp(player);
            return;
        }

        sessionManager.startLoginReminders(player);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        clearPlayerChat(player);

        ActiveServerSession serverSession = getActiveServerSession(player);
        if (serverSession != null && isPlayerOnManagedServer(player, serverSession)) {
            messages.sendWarning(player, "На этом сервере разрешены только /limbo и /serverinfo.");
            messages.sendTargetServerHelp(player);
            return;
        }

        if (isPlayerInAuthLobby(player)) {
            activeServerSessions.remove(player.getUniqueId().toString());
        }
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }

        ActiveServerSession serverSession = getActiveServerSession(player);
        if (serverSession == null || !isPlayerOnManagedServer(player, serverSession)) {
            return;
        }

        String command = normalizeCommand(event.getCommand());
        if (command.isEmpty() || ALLOWED_TARGET_SERVER_COMMANDS.contains(command)) {
            return;
        }

        event.setResult(CommandExecuteEvent.CommandResult.denied());
        messages.sendError(player, "На игровом сервере эта команда запрещена. Используй /limbo или /serverinfo.");
    }

    private String requireToken(Player player) {
        return sessionManager.requireToken(player);
    }

    private void expirePlayerSession(Player player) {
        sessionManager.expireSession(player);
    }

    private ActiveServerSession getActiveServerSession(Player player) {
        return activeServerSessions.get(player.getUniqueId().toString());
    }

    private void sendPlayerToLimbo(Player player, boolean sendFeedback) {
        RegisteredServer lobby = proxy.getServer(authLobbyServer).orElse(null);
        if (lobby == null) {
            messages.sendError(player, "Сервер limbo недоступен. Обратись к администратору.");
            return;
        }

        activeServerSessions.remove(player.getUniqueId().toString());
        player.createConnectionRequest(lobby).fireAndForget();
        if (sendFeedback) {
            messages.sendSuccess(player, "Возвращаем тебя в limbo...");
        }
    }

    private boolean isPlayerInAuthLobby(Player player) {
        return player.getCurrentServer()
            .map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(authLobbyServer))
            .orElse(false);
    }

    private boolean isPlayerOnManagedServer(Player player, ActiveServerSession serverSession) {
        return player.getCurrentServer()
            .map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(serverSession.registeredName()))
            .orElse(false);
    }

    private String normalizeCommand(String rawCommand) {
        if (rawCommand == null) {
            return "";
        }

        String command = rawCommand.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        int firstSpace = command.indexOf(' ');
        if (firstSpace >= 0) {
            command = command.substring(0, firstSpace);
        }

        return command.toLowerCase(Locale.ROOT);
    }

    private void clearPlayerChat(Player player) {
        for (int i = 0; i < CHAT_CLEAR_LINES; i++) {
            messages.sendLegacy(player, " ");
        }
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String getClientVersion(Player player) {
        return String.valueOf(player.getProtocolVersion());
    }

    private boolean versionsMatch(String clientVersion, String serverVersion) {
        if (clientVersion == null || serverVersion == null) {
            return false;
        }
        return clientVersion.equalsIgnoreCase(serverVersion);
    }
}