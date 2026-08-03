package com.example.velocityauth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class AuthBackendClient {

    private final String apiBase;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    AuthBackendClient(String apiBase) {
        this.apiBase = apiBase;
    }

    LoginResult login(Player player, String username, String password, String totpCode) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        if (totpCode != null && !totpCode.isBlank()) {
            payload.addProperty("totpCode", totpCode);
        }
        payload.addProperty("minecraftUuid", player.getUniqueId().toString());
        payload.addProperty("minecraftUsername", player.getUsername());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase + "/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .timeout(Duration.ofSeconds(5))
            .build();

        ApiResponse response = sendApiRequest(request);
        JsonObject data = extractDataObject(response);
        if (response.success() && data != null && data.has("token") && !data.get("token").isJsonNull()) {
            return LoginResult.successResult(data.get("token").getAsString());
        }
        if (response.success() && data != null && AuthJson.readBoolean(data, "totpRequired", false)) {
            return LoginResult.totpRequiredResult();
        }
        return LoginResult.failure(resolveApiErrorMessage(response, "Не удалось выполнить вход."));
    }

    JsonArray fetchServerList(Player player, String token, String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase + endpoint))
            .header("Authorization", "Bearer " + token)
            .header("X-Minecraft-UUID", player.getUniqueId().toString())
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();

        ApiResponse response = sendApiRequestOrThrow(request, "Не удалось получить список серверов.");
        JsonArray data = extractDataArray(response);
        if (data == null) {
            throw new BackendException("Сервер вернул пустой список в неподдерживаемом формате.", response.status(), response.code());
        }
        return data;
    }

    JsonObject findServerById(JsonArray servers, long serverId) {
        for (int i = 0; i < servers.size(); i++) {
            JsonObject server = servers.get(i).getAsJsonObject();
            if (server.has("id") && !server.get("id").isJsonNull() && server.get("id").getAsLong() == serverId) {
                return server;
            }
        }
        return null;
    }

    int resolveServerPort(Player player, long serverId, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase + "/api/servers/proxy/resolve?id=" + serverId))
            .header("Authorization", "Bearer " + token)
            .header("X-Minecraft-UUID", player.getUniqueId().toString())
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();

        ApiResponse response = sendApiRequestOrThrow(request, "Не удалось получить адрес сервера.");
        JsonObject data = extractDataObject(response);
        if (data != null && data.has("port") && !data.get("port").isJsonNull()) {
            return data.get("port").getAsInt();
        }
        throw new BackendException("Сервер не вернул порт для подключения.", response.status(), response.code());
    }

    void unlinkMinecraftAccount(String uuid, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBase + "/api/account/proxy/minecraft/unlink"))
            .header("Authorization", "Bearer " + token)
            .header("X-Minecraft-UUID", uuid)
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(5))
            .build();

        sendApiRequestOrThrow(request, "Не удалось отвязать Minecraft-аккаунт.");
    }

    private JsonObject extractDataObject(ApiResponse response) {
        if (response == null || response.data() == null || response.data().isJsonNull() || !response.data().isJsonObject()) {
            return null;
        }
        return response.data().getAsJsonObject();
    }

    private JsonArray extractDataArray(ApiResponse response) {
        if (response == null || response.data() == null || response.data().isJsonNull() || !response.data().isJsonArray()) {
            return null;
        }
        return response.data().getAsJsonArray();
    }

    private ApiResponse sendApiRequest(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        if (body == null || body.isBlank() || !body.trim().startsWith("{")) {
            return new ApiResponse(response.statusCode() >= 200 && response.statusCode() < 300, response.statusCode(), null, body, null);
        }

        JsonObject json = gson.fromJson(body, JsonObject.class);
        boolean success = json.has("success") && !json.get("success").isJsonNull() && json.get("success").getAsBoolean();
        Integer code = json.has("code") && !json.get("code").isJsonNull() ? json.get("code").getAsInt() : null;
        String message = json.has("message") && !json.get("message").isJsonNull() ? json.get("message").getAsString() : null;
        JsonElement data = json.has("data") ? json.get("data") : null;
        int status = json.has("status") && !json.get("status").isJsonNull() ? json.get("status").getAsInt() : response.statusCode();
        return new ApiResponse(success, status, code, message, data);
    }

    private ApiResponse sendApiRequestOrThrow(HttpRequest request, String fallbackMessage) throws Exception {
        ApiResponse response = sendApiRequest(request);
        if (response.success()) {
            return response;
        }
        throw new BackendException(resolveApiErrorMessage(response, fallbackMessage), response.status(), response.code());
    }

    private String resolveApiErrorMessage(ApiResponse response, String fallbackMessage) {
        if (response == null) {
            return fallbackMessage;
        }

        Integer code = response.code();
        if (code != null) {
            return switch (code) {
                case 1014 -> "Проверь корректность переданных данных.";
                case 1015 -> "Пользователь не найден или логин/пароль введены неверно.";
                case 1017 -> "Доступ запрещен.";
                case 1019 -> "Этот сервер сейчас недоступен для просмотра.";
                case 1020 -> "К этому сайту не привязан Minecraft-аккаунт. Сначала войди через нужный аккаунт.";
                case 1021 -> "UUID Minecraft не совпадает с авторизованным пользователем.";
                case 1022 -> "Этот Minecraft-аккаунт уже привязан к другому пользователю.";
                case 1023 -> "Этот Minecraft-аккаунт не привязан к выбранному пользователю.";
                case 1025 -> "Указан неверный TOTP-код.";
                case 1026 -> "Для входа нужен корректный TOTP-код.";
                case 1028 -> "Сервер не найден.";
                case 1034 -> "Сервер еще не готов к подключению.";
                case 1035 -> "Порт сервера еще не выделен.";
                case 1043 -> "На сервере произошла внутренняя ошибка.";
                default -> fallbackMessage;
            };
        }

        if (response.status() == 401) {
            return "Сессия авторизации истекла. Войди снова через /login.";
        }

        if (response.message() != null && !response.message().isBlank()) {
            if ("Invalid JWT token".equalsIgnoreCase(response.message())
                || "Missing or invalid Authorization header".equalsIgnoreCase(response.message())) {
                return "Сессия авторизации истекла. Войди снова через /login.";
            }
            return response.message();
        }

        return fallbackMessage;
    }
}