package com.example.minecraftserver.service;

import com.example.minecraftserver.config.AppProperties;
import com.example.minecraftserver.dto.MyDto;
import com.example.minecraftserver.dto.MyEvent;
import com.example.minecraftserver.dto.MyRequest;
import com.example.minecraftserver.entity.GameServer;
import com.example.minecraftserver.entity.ServerSettings;
import com.example.minecraftserver.entity.User;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;
import com.example.minecraftserver.repository.GameServerRepository;
import com.example.minecraftserver.repository.UserRepository;
import com.example.minecraftserver.service.ServerProcessManager.ServerStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;


@Service
@RequiredArgsConstructor
public class ServerOrchestrator {

    private final AppProperties appProperties;
    private final UserService userService;
    private final GameServerRepository serverRepository;
    private final UserRepository userRepository;
    private final VersionService versionService;
    private final ServerProcessManager processManager;
    private final ApplicationEventPublisher applicationEventPublisher;


    public List<MyDto.GameServer> getAccessibleServers(Long ownerId) {
        return serverRepository.findAll().stream()
            .filter(server ->
                !server.getOwner().getId().equals(ownerId) &&
                !server.isPublic() &&
                hasWhitelistAccess(server, ownerId)
            ).map(this::toDto).toList();
    }

    public List<MyDto.GameServer> getPublicServers(Long ownerId) {
        return serverRepository.findAll().stream()
            .filter(server ->
                server.isPublic() &&
                ServerStatus.RUNNING.equals(processManager.getServerStatus(server.getId()))
            ).map(this::toDto).toList();
    }

    public List<MyDto.GameServer> getAvailableServers(Long ownerId) {
        return serverRepository.findAll().stream()
            .filter(server -> 
                server.getOwner().getId().equals(ownerId) ||
                server.isPublic() ||
                hasWhitelistAccess(server, ownerId)
            ).map(this::toDto).toList();
    }

    public List<MyDto.GameServer> getMyServers(Long ownerId) {
        return serverRepository.findByOwnerId(ownerId)
            .stream().map(this::toDto).toList();
    }
    
    public ServerResolveResult resolveServerById(Long serverId, Long playerId) throws MyException {
        GameServer server = serverRepository.findById(serverId)
            .orElseThrow(() -> new MyException(ErrorCode.SERVER_NOT_FOUND));

        User owner = server.getOwner();

        MyException.throwIf(
            !server.isPublic() && !owner.getId().equals(playerId) && !hasWhitelistAccess(server, playerId),
            ErrorCode.ACCESS_DENIED
        );
        MyException.throwIf(
            !ServerStatus.RUNNING.equals(processManager.getServerStatus(serverId)), 
            ErrorCode.SERVER_NOT_READY
        );

        Integer port = processManager.getServerPort(serverId);
        MyException.throwIf(
            port == null, 
            ErrorCode.SERVER_PORT_NOT_ALLOCATED
        );
        return new ServerResolveResult(
            port, owner.getId(), owner.getUsername()
        );
    }

    public Long createServer(MyRequest.CreateServer request, Long ownerId) throws MyException {
        User owner = userService.getUser(ownerId);

        // Проверка лимита (не более 5 серверов)
        MyException.throwIf(
            serverRepository.countByOwnerId(ownerId) >= 5, 
            ErrorCode.SERVER_LIMIT_REACHED
        );

        GameServer server = new GameServer();
        server.setOwner(owner);
        server.setServerName(request.getServerName());
        server.setMinecraftVersion(request.getMinecraftVersion());
        server.setLoaderVersion(request.getLoaderVersion());
        server.setInstallerVersion(request.getInstallerVersion());
        server.setCreatedAt(LocalDateTime.now());
        server.setPublic(request.isPublic());
        server.setSettings(mergeSettings(
            ServerSettings.defaults(request.getServerName(), owner.getUsername()),
            request.getSettings()
        ));

        Long serverId = serverRepository.save(server).getId();
        try {
            versionService.downloadServerForUser(
                serverId, ownerId, request.getMinecraftVersion(),
                request.getLoaderVersion(), request.getInstallerVersion()
            );
        } catch (MyException e) {
            serverRepository.deleteById(serverId);
            throw new MyException(ErrorCode.FAILED_TO_DOWNLOAD_SERVER);
        }
        return serverId;
    }

    public void startServer(Long serverId, Long ownerId, int memoryGb) throws MyException {
        GameServer server = findAndCheckOwner(serverId, ownerId);
        ServerStatus status = processManager.getServerStatus(serverId);
        
        MyException.throwIf(
            ServerStatus.STARTING.equals(status), 
            ErrorCode.SERVER_ALREADY_STARTING
        );
        MyException.throwIf(
            ServerStatus.RUNNING.equals(status), 
            ErrorCode.SERVER_ALREADY_RUNNING
        );
        MyException.throwIf(
            memoryGb < 1, 
            ErrorCode.SERVER_MEMORY_TOO_LOW
        );

        processManager.startServer(
            serverId, ownerId, server.getMinecraftVersion(),
            server.getLoaderVersion(), server.getInstallerVersion(),
            resolveSettings(server), memoryGb * 1024
        );
    }

    public MyDto.RuntimeSummary getRuntimeSummary() {
        return processManager.getRuntimeSummary();
    }

    public void stopServer(Long serverId, Long ownerId) throws MyException, InterruptedException {
        checkOwnership(serverId, ownerId);
        processManager.stopServerAndWait(serverId);
    }

    public void deleteServer(Long serverId, Long ownerId) throws MyException, IOException, InterruptedException {
        checkOwnership(serverId, ownerId);
        processManager.stopServerAndWait(serverId);

        Path serverDir = getServerDirectory(ownerId, serverId);
        if (Files.exists(serverDir)) {
            Files.walk(serverDir).sorted(Comparator.reverseOrder())
                .map(Path::toFile).forEach(File::delete);
        }
        serverRepository.deleteById(serverId);
        processManager.cleanupAfterDelete(serverId);
    }

    public MyDto.ServerDownloadInfo getServerDownloadInfo(Long serverId, Long ownerId) throws MyException {
        Path serverDir = requireDownloadableServerDirectory(serverId, ownerId);
        try {
            long sizeBytes = calculateDirectorySize(serverDir);
            GameServer server = findAndCheckOwner(serverId, ownerId);
            return new MyDto.ServerDownloadInfo(
                server.getServerName(),
                sizeBytes,
                formatSize(sizeBytes)
            );
        } catch (IOException ex) {
            throw new MyException(ErrorCode.FAILED_TO_PREPARE_SERVER_DOWNLOAD);
        }
    }

    public void writeServerArchive(Long serverId, Long ownerId, java.io.OutputStream outputStream) throws MyException {
        Path serverDir = requireDownloadableServerDirectory(serverId, ownerId);
        String rootFolder = "server-" + serverId;

        try (java.util.zip.ZipOutputStream zipOutputStream = new java.util.zip.ZipOutputStream(outputStream)) {
            Files.walk(serverDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    String relativePath = serverDir.relativize(path).toString().replace('\\', '/');
                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(rootFolder + "/" + relativePath);
                    try {
                        zipOutputStream.putNextEntry(entry);
                        Files.copy(path, zipOutputStream);
                        zipOutputStream.closeEntry();
                    } catch (IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                });
            zipOutputStream.finish();
        } catch (IOException | java.io.UncheckedIOException ex) {
            throw new MyException(ErrorCode.FAILED_TO_PREPARE_SERVER_DOWNLOAD);
        }
    }


    public void addToWhitelist(Long serverId, Long ownerId, String username) throws MyException {
        GameServer server = findAndCheckOwner(serverId, ownerId);
        MyException.throwIf(
            server.isPublic(), 
            ErrorCode.WHITELIST_PRIVATE_SERVERS_ONLY
        );

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new MyException(ErrorCode.USER_NOT_EXISTS_OR_INVALID_DATA));
        MyException.throwIf(
            server.getOwner().getId().equals(user.getId()), 
            ErrorCode.OWNER_ALREADY_HAS_ACCESS
        );

        server.getWhitelist().add(user.getId());
        toDto(serverRepository.save(server));
        applicationEventPublisher.publishEvent(new MyEvent.ServerStateChanged(serverId));
    }

    public void removeFromWhitelist(Long serverId, Long ownerId, Long playerToRemove) throws MyException {
        GameServer server = findAndCheckOwner(serverId, ownerId);
        MyException.throwIf(
            server.isPublic(), 
            ErrorCode.WHITELIST_PRIVATE_SERVERS_ONLY
        );

        server.getWhitelist().remove(playerToRemove);
        toDto(serverRepository.save(server));
        applicationEventPublisher.publishEvent(new MyEvent.ServerStateChanged(serverId));
    }

    public List<String> getServerLogs(Long serverId, Long ownerId, int lines) throws MyException {
        checkOwnership(serverId, ownerId);
        Path logFile = getServerLogFile(ownerId, serverId);
        if (!Files.exists(logFile)) return List.of();
        
        Charset[] charsets = {
            StandardCharsets.UTF_8,
            Charset.forName("CP866"),
            StandardCharsets.ISO_8859_1
        };
        for (Charset charset : charsets) {
            try {
                List<String> allLines = Files.readAllLines(logFile, charset);
                return allLines.stream().skip(Math.max(0, allLines.size() - lines)).toList();
            } catch (IOException ignored) { }
        }
        
        throw new MyException(ErrorCode.SERVER_LOG_READ_ERROR);
    }

    public List<String> getPublicServerLogs(Long serverId, int lines) throws MyException {
        GameServer server = resolveViewableServer(serverId, null);
        Path logFile = getServerLogFile(server.getOwner().getId(), serverId);
        if (!Files.exists(logFile)) return List.of();

        Charset[] charsets = {
            StandardCharsets.UTF_8,
            Charset.forName("CP866"),
            StandardCharsets.ISO_8859_1
        };
        for (Charset charset : charsets) {
            try {
                List<String> allLines = Files.readAllLines(logFile, charset);
                return allLines.stream().skip(Math.max(0, allLines.size() - lines)).toList();
            } catch (IOException ignored) { }
        }
        throw new MyException(ErrorCode.SERVER_LOG_READ_ERROR);
    }

    
    public MyDto.GameServer findAndCheckOwnerDto(Long serverId, Long ownerId) throws MyException {
        return toDto(findAndCheckOwner(serverId, ownerId));
    }

    public MyDto.GameServer getPublicServerDto(Long serverId) throws MyException {
        return toDto(resolveViewableServer(serverId, null));
    }

    @Transactional(readOnly = true)
    public MyDto.GameServer getViewerServerDto(Long serverId, Long userId) throws MyException {
        return toDto(resolveViewableServer(serverId, userId));
    }

    @Transactional(readOnly = true)
    public MyDto.ServerConfiguration getViewerServerConfiguration(Long serverId, Long userId) throws MyException {
        return toConfigurationDto(resolveViewableServer(serverId, userId));
    }

    @Transactional(readOnly = true)
    public List<MyDto.WhitelistUser> getViewerServerWhitelist(Long serverId, Long userId) throws MyException {
        return resolveWhitelistUsers(resolveViewableServer(serverId, userId));
    }

    @Transactional(readOnly = true)
    public List<String> getViewerServerLogs(Long serverId, Long userId, int lines) throws MyException {
        GameServer server = resolveViewableServer(serverId, userId);
        Path logFile = getServerLogFile(server.getOwner().getId(), serverId);
        if (!Files.exists(logFile)) return List.of();

        Charset[] charsets = {
            StandardCharsets.UTF_8,
            Charset.forName("CP866"),
            StandardCharsets.ISO_8859_1
        };
        for (Charset charset : charsets) {
            try {
                List<String> allLines = Files.readAllLines(logFile, charset);
                return allLines.stream().skip(Math.max(0, allLines.size() - lines)).toList();
            } catch (IOException ignored) { }
        }
        throw new MyException(ErrorCode.SERVER_LOG_READ_ERROR);
    }

    private GameServer resolveViewableServer(Long serverId, Long userId) throws MyException {
        GameServer server = serverRepository.findById(serverId)
            .orElseThrow(() -> new MyException(ErrorCode.SERVER_NOT_FOUND));

        boolean runningPublic = server.isPublic() && ServerStatus.RUNNING.equals(processManager.getServerStatus(serverId));
        boolean sharedAccess = userId != null && (
            server.getOwner().getId().equals(userId) ||
            hasWhitelistAccess(server, userId) ||
            runningPublic
        );

        MyException.throwIf(
            !runningPublic && !sharedAccess, 
            ErrorCode.SERVER_NOT_AVAILABLE_FOR_VIEWING
        );
        return server;
    }

    private Path getServerLogFile(Long ownerId, Long serverId) {
        return Paths.get(
            appProperties.getMinecraft().getServersDir(),
            String.valueOf(ownerId),
            String.valueOf(serverId),
            "logs",
            "latest.log"
        );
    }

    private Path getServerDirectory(Long ownerId, Long serverId) {
        return Paths.get(
            appProperties.getMinecraft().getServersDir(),
            String.valueOf(ownerId),
            String.valueOf(serverId)
        );
    }

    private Path requireDownloadableServerDirectory(Long serverId, Long ownerId) throws MyException {
        GameServer server = findAndCheckOwner(serverId, ownerId);
        ServerStatus status = processManager.getServerStatus(serverId);

        MyException.throwIf(
            !ServerStatus.STOPPED.equals(status) && !ServerStatus.ERROR.equals(status),
            ErrorCode.SERVER_MUST_BE_STOPPED_FOR_DOWNLOAD
        );

        Path serverDir = getServerDirectory(server.getOwner().getId(), serverId);
        MyException.throwIf(
            !Files.exists(serverDir) || !Files.isDirectory(serverDir),
            ErrorCode.RESOURCE_NOT_FOUND
        );
        return serverDir;
    }

    private long calculateDirectorySize(Path serverDir) throws IOException {
        try (var stream = Files.walk(serverDir)) {
            return stream
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                })
                .sum();
        } catch (java.io.UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " Б";
        }

        double size = sizeBytes;
        String[] units = {"КБ", "МБ", "ГБ", "ТБ"};
        int unitIndex = -1;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        if (unitIndex < 0) {
            return sizeBytes + " Б";
        }

        return (size >= 10 || Math.abs(size - Math.rint(size)) < 0.05
            ? String.valueOf((long) Math.rint(size))
            : String.format(java.util.Locale.US, "%.1f", size).replace('.', ',')) + " " + units[unitIndex];
    }

    public MyDto.ServerConfiguration getServerConfiguration(Long serverId, Long ownerId) throws MyException {
        GameServer server = findAndCheckOwner(serverId, ownerId);
        return toConfigurationDto(server);
    }

    public List<MyDto.WhitelistUser> getServerWhitelist(Long serverId, Long ownerId) throws MyException {
        GameServer server = findAndCheckOwner(serverId, ownerId);
        return resolveWhitelistUsers(server);
    }

    public MyDto.ServerConfiguration updateServerConfiguration(Long serverId, Long ownerId, MyRequest.ServerConfiguration request) throws MyException {
        GameServer server = findAndCheckOwner(serverId, ownerId);
        ServerStatus status = processManager.getServerStatus(serverId);
        MyException.throwIf(
            !ServerStatus.STOPPED.equals(status) && !ServerStatus.ERROR.equals(status), 
            ErrorCode.SERVER_MUST_BE_STOPPED_FOR_SETTINGS
        );

        if (request.getIsPublic() != null) {
            server.setPublic(request.getIsPublic());
            if (request.getIsPublic()) {
                server.getWhitelist().clear();
            }
        }
        server.setSettings(mergeSettings(resolveSettings(server), request.getSettings()));
        var configuration = toConfigurationDto(serverRepository.save(server));
        applicationEventPublisher.publishEvent(new MyEvent.ServerStateChanged(serverId));
        return configuration;
    }

    // оптимизировать для получения только владельца
    private GameServer findAndCheckOwner(Long serverId, Long ownerId) throws MyException {
        GameServer server = serverRepository.findById(serverId)
            .orElseThrow(() -> new MyException(ErrorCode.SERVER_NOT_FOUND));

        MyException.throwIf(
            !server.getOwner().getId().equals(ownerId), 
            ErrorCode.NOT_OWNER
        );
        return server;
    }

    // оптимизировать для получения только владельца
    private void checkOwnership(Long serverId, Long ownerId) throws MyException {
        GameServer server = serverRepository.findById(serverId)
            .orElseThrow(() -> new MyException(ErrorCode.SERVER_NOT_FOUND));

        MyException.throwIf(
            !server.getOwner().getId().equals(ownerId), 
            ErrorCode.NOT_OWNER
        );
    }


    @Data
    @AllArgsConstructor
    public static class ServerResolveResult {
        private final int port;
        private final Long ownerId;
        private final String ownerUsername;
    }

    public MyDto.GameServer toDto(GameServer server) {
        MyDto.GameServer dto = new MyDto.GameServer();
        dto.setId(server.getId());
        dto.setOwnerUserId(server.getOwner().getId());
        dto.setOwnerUsername(server.getOwner().getUsername());
        dto.setServerName(server.getServerName());
        dto.setMinecraftVersion(server.getMinecraftVersion());
        dto.setLoaderVersion(server.getLoaderVersion());
        dto.setInstallerVersion(server.getInstallerVersion());
        dto.setPort(processManager.getServerPort(server.getId()));
        dto.setCreatedAt(server.getCreatedAt());
        dto.setPublic(server.isPublic());
        dto.setWhitelistCount(server.isPublic() || server.getWhitelist() == null ? 0 : server.getWhitelist().size());
        dto.setAllocatedMemoryMb(processManager.getAllocatedMemoryMb(server.getId()));
        dto.setStatus(processManager.getServerStatus(server.getId()));
        return dto;
    }

    private MyDto.ServerConfiguration toConfigurationDto(GameServer server) {
        return new MyDto.ServerConfiguration(
            server.isPublic(),
            toSettingsDto(resolveSettings(server))
        );
    }

    private List<MyDto.WhitelistUser> resolveWhitelistUsers(GameServer server) {
        if (server.isPublic() || server.getWhitelist() == null || server.getWhitelist().isEmpty()) {
            return List.of();
        }

        Map<Long, User> usersById = userRepository.findAllById(server.getWhitelist()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

        return server.getWhitelist().stream()
            .map(usersById::get)
            .filter(user -> user != null)
            .map(user -> new MyDto.WhitelistUser(
                user.getId(),
                user.getUsername(),
                user.getMinecraftUsername()
            ))
            .toList();
    }

    private boolean hasWhitelistAccess(GameServer server, Long userId) {
        return !server.isPublic() && userId != null && server.getWhitelist().contains(userId);
    }

    private ServerSettings resolveSettings(GameServer server) {
        if (server.getSettings() != null) {
            return server.getSettings();
        }
        return ServerSettings.defaults(server.getServerName(), server.getOwner().getUsername());
    }

    private ServerSettings mergeSettings(ServerSettings base, MyRequest.ServerSettings request) {
        ServerSettings result = new ServerSettings(
            base.getMotd(),
            base.getMaxPlayers(),
            base.getLevelName(),
            base.getAllowNether(),
            base.getPvp(),
            base.getDifficulty(),
            base.getGamemode(),
            base.getViewDistance(),
            base.getSimulationDistance()
        );

        if (request == null) {
            return result;
        }
        if (request.getMotd() != null) {
            result.setMotd(request.getMotd());
        }
        if (request.getMaxPlayers() != null) {
            result.setMaxPlayers(request.getMaxPlayers());
        }
        if (request.getLevelName() != null) {
            result.setLevelName(request.getLevelName());
        }
        if (request.getAllowNether() != null) {
            result.setAllowNether(request.getAllowNether());
        }
        if (request.getPvp() != null) {
            result.setPvp(request.getPvp());
        }
        if (request.getDifficulty() != null) {
            result.setDifficulty(request.getDifficulty().toLowerCase());
        }
        if (request.getGamemode() != null) {
            result.setGamemode(request.getGamemode().toLowerCase());
        }
        if (request.getViewDistance() != null) {
            result.setViewDistance(request.getViewDistance());
        }
        if (request.getSimulationDistance() != null) {
            result.setSimulationDistance(request.getSimulationDistance());
        }
        return result;
    }

    private MyDto.ServerSettings toSettingsDto(ServerSettings settings) {
        return new MyDto.ServerSettings(
            settings.getMotd(),
            settings.getMaxPlayers(),
            settings.getLevelName(),
            settings.getAllowNether(),
            settings.getPvp(),
            settings.getDifficulty(),
            settings.getGamemode(),
            settings.getViewDistance(),
            settings.getSimulationDistance()
        );
    }
}