package com.example.velocityauth;

import com.google.gson.JsonObject;

final class AuthJson {
    
    static String readString(JsonObject json, String fieldName, String fallback) {
        if (json == null || !json.has(fieldName) || json.get(fieldName).isJsonNull()) {
            return fallback;
        }
        return json.get(fieldName).getAsString();
    }

    static boolean readBoolean(JsonObject json, String fieldName, boolean fallback) {
        if (json == null || !json.has(fieldName) || json.get(fieldName).isJsonNull()) {
            return fallback;
        }
        return json.get(fieldName).getAsBoolean();
    }
}