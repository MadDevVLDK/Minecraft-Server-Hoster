package com.example.velocityauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

record ApiResponse(boolean success, int status, Integer code, String message, JsonElement data) { }

record LoginResult(String token, boolean totpRequired, String message) {
    static LoginResult successResult(String token) {
        return new LoginResult(token, false, null);
    }

    static LoginResult totpRequiredResult() {
        return new LoginResult(null, true, null);
    }

    static LoginResult failure(String message) {
        return new LoginResult(null, false, message);
    }
}

record ServerListResult(JsonArray servers, String errorMessage, boolean authExpired) { }

record OperationResult(boolean success, String message, boolean authExpired) {
    static OperationResult successResult() {
        return new OperationResult(true, null, false);
    }

    static OperationResult failure(String message, boolean authExpired) {
        return new OperationResult(false, message, authExpired);
    }
}

record JoinTarget(long serverId,
                  String serverName,
                  String ownerUsername,
                  int port,
                  String minecraftVersion,
                  String errorMessage,
                  boolean authExpired) {
    static JoinTarget success(long serverId,
                              String serverName,
                              String ownerUsername,
                              int port,
                              String minecraftVersion) {
        return new JoinTarget(serverId, serverName, ownerUsername, port, minecraftVersion, null, false);
    }

    static JoinTarget failure(String errorMessage, boolean authExpired) {
        return new JoinTarget(-1, null, null, -1, null, errorMessage, authExpired);
    }
}

record ActiveServerSession(long serverId,
                           String serverName,
                           String ownerUsername,
                           String minecraftVersion,
                           int port,
                           String registeredName) {
}

final class BackendException extends RuntimeException {
    private final int status;
    private final Integer code;

    BackendException(String message, int status, Integer code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    int status() {
        return status;
    }

    Integer code() {
        return code;
    }
}