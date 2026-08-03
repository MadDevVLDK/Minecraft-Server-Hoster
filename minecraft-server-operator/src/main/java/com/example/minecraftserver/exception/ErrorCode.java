package com.example.minecraftserver.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    USERNAME_ALREADY_EXISTS_OR_INVALID_DATA(1001, "Username already exists or invalid data"),
    INVALID_PASSWORD(1002, "Password length must be between 3 and 100"),
    INVALID_MINECRAFT_UUID(1003, "Minecraft UUID is invalid"),
    SERVER_MUST_BE_STOPPED_TO_UPLOAD_MODS(1004, "Server must be stopped to upload mods"),
    SERVER_MUST_BE_STOPPED_TO_DELETE_MODS(1005, "Server must be stopped to delete mods"),
    ONLY_JAR_FILES_ALLOWED_FOR_UPLOAD(1006, "Only non-empty .jar files are allowed"),
    ONLY_JAR_FILES_ALLOWED_FOR_DELETE(1007, "Only .jar files can be deleted"),
    MOD_NOT_FOUND(1008, "Mod not found"),
    INVALID_INVITE_TOKEN(1009, "Invite token is required"),
    INVITE_NOT_FOUND(1010, "Invite not found"),
    SERVER_MEMORY_TOO_LOW(1011, "Server memory must be at least 1 GB"),
    MAX_RUNNING_SERVERS_TOO_LOW(1012, "Max running servers must be at least 1"),
    MAX_TOTAL_MEMORY_TOO_LOW(1013, "Max total memory must be at least 1 GB"),
    VALIDATION_ERROR(1014, "Validation error"),
    USER_NOT_EXISTS_OR_INVALID_DATA(1015, "User not exists or invalid data"),
    REGISTRATION_INVITE_REQUIRED(1016, "Registration invite is required or invalid"),
    ACCESS_DENIED(1017, "Access denied"),
    NOT_OWNER(1018, "Not owner"),
    SERVER_NOT_AVAILABLE_FOR_VIEWING(1019, "Server is not available for viewing"),
    MINECRAFT_NOT_LINKED(1020, "Minecraft account is not linked to this user"),
    MINECRAFT_UUID_MISMATCH(1021, "Minecraft UUID does not match the authenticated user"),
    MINECRAFT_ALREADY_LINKED(1022, "Minecraft account already linked to another user"),
    MINECRAFT_NOT_LINKED_TO_USER(1023, "This Minecraft account is not linked to the selected user"),
    ADMIN_AREA_LOCAL_NETWORK_ONLY(1024, "Admin area is available only from the local network"),
    TOTP_CODE_INVALID(1025, "TOTP code is invalid"),
    TOTP_CODE_REQUIRED(1026, "TOTP code is required or invalid"),
    TOTP_CONFIRMATION_FAILED(1027, "TOTP confirmation failed"),
    SERVER_NOT_FOUND(1028, "Server not found"),
    RESOURCE_NOT_FOUND(1029, "Resource not found"),
    TOTP_SETUP_REQUIRED(1030, "Start TOTP setup first"),
    TOTP_ALREADY_DISABLED(1031, "TOTP is already disabled"),
    SERVER_ALREADY_STARTING(1032, "Server is already starting, please wait"),
    SERVER_ALREADY_RUNNING(1033, "Server already running"),
    SERVER_NOT_READY(1034, "Server is not ready"),
    SERVER_PORT_NOT_ALLOCATED(1035, "Server port not allocated yet"),
    SERVER_LIMIT_REACHED(1036, "Server limit reached (max 5)"),
    SERVER_MUST_BE_STOPPED_FOR_SETTINGS(1037, "Server must be stopped before updating settings"),
    ONLY_ACTIVE_INVITES_CAN_BE_DEACTIVATED(1038, "Only active invites can be deactivated"),
    WHITELIST_PRIVATE_SERVERS_ONLY(1039, "Whitelist is available only for private servers"),
    OWNER_ALREADY_HAS_ACCESS(1040, "Owner already has access to this server"),
    GLOBAL_RUNNING_SERVER_LIMIT_REACHED(1041, "Global running server limit reached"),
    GLOBAL_MEMORY_LIMIT_REACHED(1042, "Global memory limit reached"),
    INTERNAL_SERVER_ERROR(1043, "Internal server error"),
    FAILED_TO_DOWNLOAD_SERVER(1044, "Failed to download server"),
    FAILED_TO_GENERATE_TOTP_QR_CODE(1045, "Failed to generate TOTP QR code"),
    FAILED_TO_GENERATE_TOTP_CODE(1046, "Failed to generate TOTP code"),
    NO_FREE_PORTS_AVAILABLE(1047, "No free ports available"),
    TIMEOUT_WAITING_FOR_SERVER_TO_BECOME_READY(1048, "Timeout waiting for server to become ready"),
    SERVER_START_FAILED(1049, "Server failed to start"),
    SERVER_LOG_READ_ERROR(1050, "Unable to read log file with any known charset"),
    FAILED_TO_UPLOAD_MOD(1051, "Failed to upload mod file"),
     FAILED_TO_READ_MODS_LIST(1052, "Failed to read mods list"),
     SERVER_MUST_BE_STOPPED_FOR_DOWNLOAD(1053, "Server must be stopped before download"),
     FAILED_TO_PREPARE_SERVER_DOWNLOAD(1054, "Failed to prepare server download");

    private final int code;
    private final String error;

    ErrorCode(int code, String error) {
        this.code = code;
        this.error = error;
    }

    public static HttpStatus resolveStatus(ErrorCode error) {
        return switch (error) {
            case USERNAME_ALREADY_EXISTS_OR_INVALID_DATA,
                 INVALID_PASSWORD,
                 INVALID_MINECRAFT_UUID,
                 SERVER_MUST_BE_STOPPED_TO_UPLOAD_MODS,
                 SERVER_MUST_BE_STOPPED_TO_DELETE_MODS,
                 ONLY_JAR_FILES_ALLOWED_FOR_UPLOAD,
                 ONLY_JAR_FILES_ALLOWED_FOR_DELETE,
                 MOD_NOT_FOUND,
                 INVALID_INVITE_TOKEN,
                 INVITE_NOT_FOUND,
                 SERVER_MEMORY_TOO_LOW,
                 MAX_RUNNING_SERVERS_TOO_LOW,
                 MAX_TOTAL_MEMORY_TOO_LOW,
                 VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case USER_NOT_EXISTS_OR_INVALID_DATA,
                 REGISTRATION_INVITE_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED,
                 NOT_OWNER,
                 SERVER_NOT_AVAILABLE_FOR_VIEWING,
                 MINECRAFT_NOT_LINKED,
                 MINECRAFT_UUID_MISMATCH,
                 MINECRAFT_ALREADY_LINKED,
                 MINECRAFT_NOT_LINKED_TO_USER,
                 ADMIN_AREA_LOCAL_NETWORK_ONLY,
                 TOTP_CODE_INVALID,
                 TOTP_CODE_REQUIRED,
                 TOTP_CONFIRMATION_FAILED -> HttpStatus.FORBIDDEN;
            case SERVER_NOT_FOUND,
                 RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TOTP_SETUP_REQUIRED,
                 TOTP_ALREADY_DISABLED,
                 SERVER_ALREADY_STARTING,
                 SERVER_ALREADY_RUNNING,
                 SERVER_NOT_READY,
                 SERVER_PORT_NOT_ALLOCATED,
                 SERVER_LIMIT_REACHED,
                 SERVER_MUST_BE_STOPPED_FOR_SETTINGS,
                 ONLY_ACTIVE_INVITES_CAN_BE_DEACTIVATED,
                 WHITELIST_PRIVATE_SERVERS_ONLY,
                 OWNER_ALREADY_HAS_ACCESS,
                 GLOBAL_RUNNING_SERVER_LIMIT_REACHED,
                 GLOBAL_MEMORY_LIMIT_REACHED -> HttpStatus.CONFLICT;
            case INTERNAL_SERVER_ERROR,
                 FAILED_TO_DOWNLOAD_SERVER,
                 FAILED_TO_GENERATE_TOTP_QR_CODE,
                 FAILED_TO_GENERATE_TOTP_CODE,
                 NO_FREE_PORTS_AVAILABLE,
                 TIMEOUT_WAITING_FOR_SERVER_TO_BECOME_READY,
                 SERVER_START_FAILED,
                 SERVER_LOG_READ_ERROR,
                 FAILED_TO_UPLOAD_MOD,
                     FAILED_TO_READ_MODS_LIST,
                     FAILED_TO_PREPARE_SERVER_DOWNLOAD -> HttpStatus.INTERNAL_SERVER_ERROR;
               case SERVER_MUST_BE_STOPPED_FOR_DOWNLOAD -> HttpStatus.CONFLICT;
        };
    }
}
