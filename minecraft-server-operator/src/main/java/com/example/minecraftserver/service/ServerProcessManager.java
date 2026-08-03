package com.example.minecraftserver.service;

import com.example.minecraftserver.dto.MyDto;
import com.example.minecraftserver.dto.MyEvent;
import com.example.minecraftserver.entity.ServerSettings;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.example.minecraftserver.config.AppProperties;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerProcessManager {

    private final AppProperties appProperties;
    private final AppSettingsService appSettingsService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final Map<Long, ServerStatus> serverStatuses = new ConcurrentHashMap<>();
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<Long, Integer> serverAllocatedMemoryMb = new ConcurrentHashMap<>();

    private final Map<Long, Integer> serverPorts = new ConcurrentHashMap<>();
    private final Set<Integer> usedPorts = ConcurrentHashMap.newKeySet();


    public synchronized void startServer(Long serverId, Long ownerId,
                            String minecraftVersion, String loaderVersion, String installerVersion,
                            ServerSettings settings, int memoryMb) throws MyException {

        MyException.throwIf(
            runningProcesses.containsKey(serverId), 
            ErrorCode.SERVER_ALREADY_RUNNING
        );
        MyException.throwIf(
            memoryMb < 1024, 
            ErrorCode.SERVER_MEMORY_TOO_LOW
        );

        int maxRunningServers = appSettingsService.getMaxRunningServers();
        MyException.throwIf(
            getActiveServerCount() >= maxRunningServers, 
            ErrorCode.GLOBAL_RUNNING_SERVER_LIMIT_REACHED
        );

        int maxTotalMemoryMb = appSettingsService.getMaxTotalMemoryMb();
        int nextAllocatedMemoryMb = getAllocatedMemoryMb() + memoryMb;
        MyException.throwIf(
            nextAllocatedMemoryMb > maxTotalMemoryMb,
            ErrorCode.GLOBAL_MEMORY_LIMIT_REACHED
        );

        int port = allocateServerPort(serverId);
        try {
            Path serverDir = Paths.get(getServersDir(), String.valueOf(ownerId), String.valueOf(serverId));
            
            createServerProperties(serverDir, port, settings);

            String jarName = String.format("fabric-server-mc.%s-loader.%s-launcher.%s.jar",
                    minecraftVersion, loaderVersion, installerVersion);
            Path absoluteJarPath = serverDir.resolve(jarName).toAbsolutePath();
            MyException.throwIf(
                !Files.exists(absoluteJarPath),
                ErrorCode.FAILED_TO_DOWNLOAD_SERVER
            );

            Path logsDir = serverDir.resolve("logs");
            Files.createDirectories(logsDir);
            Path logFile = logsDir.resolve("latest.log");
            Files.writeString(logFile, "", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            ProcessBuilder pb = new ProcessBuilder(
                appProperties.getMinecraft().getJavaCommand(),
                "-Xms" + memoryMb + "M",
                "-Xmx" + memoryMb + "M",
                "-jar",
                absoluteJarPath.toString(),
                "nogui"
            );  
            pb.directory(serverDir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));

            log.info("Starting server {} using Java command {}", serverId, appProperties.getMinecraft().getJavaCommand());

            setServerStatus(serverId, ServerStatus.STARTING);
            serverAllocatedMemoryMb.put(serverId, memoryMb);
            publishServerAndRuntimeChanged(serverId);

            Process process = pb.start();
            runningProcesses.put(serverId, process);

            // Асинхронно ждём готовности по логу
            CompletableFuture.runAsync(() -> {
                try {
                    waitForServerReady(logFile, 60);
                    setServerStatus(serverId, ServerStatus.RUNNING);
                    publishServerAndRuntimeChanged(serverId);
                    log.info("Server {} is now RUNNING", serverId);
                } catch (Exception e) {
                    log.error("Server {} failed to start --> {}", serverId, e.getMessage());
                    setServerStatus(serverId, ServerStatus.ERROR);
                    
                    // убить процесс, если он висит
                    Process p = runningProcesses.get(serverId);
                    if (p != null && p.isAlive()) p.destroyForcibly();
                    runningProcesses.remove(serverId);
                    serverAllocatedMemoryMb.remove(serverId);

                    // Освобождаем порт при ошибке во время старта
                    synchronized (this) {
                        usedPorts.remove(port);
                        serverPorts.remove(serverId);
                    }
                    publishServerAndRuntimeChanged(serverId);
                }
            });

            // При завершении процесса (если сервер упал)
            process.onExit().thenRun(() -> {
                runningProcesses.remove(serverId);
                serverAllocatedMemoryMb.remove(serverId);
                ServerStatus status = getServerStatus(serverId);
                if (ServerStatus.RUNNING.equals(status) || ServerStatus.STARTING.equals(status)) {
                    setServerStatus(serverId, ServerStatus.STOPPED);
                }
                publishServerAndRuntimeChanged(serverId);
                log.info("Server {} process exited", serverId);
            });
        } catch (Exception ex) {
            log.error("Server {} failed to start --> {}", serverId, ex);
            setServerStatus(serverId, ServerStatus.ERROR);

            synchronized (this) {
                if (port != -1) {
                    usedPorts.remove(port);
                    serverPorts.remove(serverId);
                }
            }
            serverAllocatedMemoryMb.remove(serverId);
            publishServerAndRuntimeChanged(serverId);
            throw new MyException(ErrorCode.SERVER_START_FAILED);
        }
    }

    private void waitForServerReady(Path logFile, int timeoutSeconds) throws MyException, IOException, InterruptedException {
        long start = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;

        while (!Files.exists(logFile) && System.currentTimeMillis() - start < timeout) {
            Thread.sleep(500);
        }

        Charset[] charsets = {
            StandardCharsets.UTF_8,
            Charset.forName("CP866"),
            StandardCharsets.ISO_8859_1
        };

        while (System.currentTimeMillis() - start < timeout) {
            for (Charset charset : charsets) {
                try (BufferedReader reader = Files.newBufferedReader(logFile, charset)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("Done (") && line.contains(")! For help, type \"help\"")) {
                            return;
                        }
                    }
                } catch (IOException ignored) {
                    // пробуем следующую кодировку
                    continue;
                }
            }
            Thread.sleep(500);
        }
        
        throw new MyException(ErrorCode.TIMEOUT_WAITING_FOR_SERVER_TO_BECOME_READY);
    }

    public void stopServerAndWait(Long serverId) throws InterruptedException {
        Process process = runningProcesses.get(serverId);
            if (process != null && process.isAlive()) {
            process.destroyForcibly();
            process.waitFor(); // дожидаемся завершения
        }
        setServerStatus(serverId, ServerStatus.STOPPED);
        serverAllocatedMemoryMb.remove(serverId);
        synchronized (this) {
            Integer port = serverPorts.get(serverId);
            if (port != null) usedPorts.remove(port);
        }
        publishServerAndRuntimeChanged(serverId);
    }

    public void stopAllServers() {
        List<Long> ids = new ArrayList<>(runningProcesses.keySet());
        log.info("Stopping all servers...");
        ids.forEach(processId -> {
            log.info("Stopping server {}...", processId);
            try { 
                stopServerAndWait(processId); 
            } catch(Exception ignored) { }
            log.info("Server {} stopped", processId);
        });
    }

    public void cleanupAfterDelete(Long serverId) throws InterruptedException {
        Integer port = serverPorts.remove(serverId);
        if (port != null) {
            usedPorts.remove(port);
        }
        serverStatuses.remove(serverId);
        serverAllocatedMemoryMb.remove(serverId);
        applicationEventPublisher.publishEvent(new MyEvent.RuntimeSummaryChanged());
        applicationEventPublisher.publishEvent(new MyEvent.ServerDeleted(serverId));
    }

    private void createServerProperties(Path serverDir, int port, ServerSettings settings) throws IOException {
        Path propsPath = serverDir.resolve("server.properties");
        Properties properties = new Properties();
        if (Files.exists(propsPath)) {
            try (var reader = Files.newBufferedReader(propsPath, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }

        Map<String, String> managedValues = new LinkedHashMap<>();
        managedValues.put("server-port", String.valueOf(port));
        managedValues.put("online-mode", "false");
        managedValues.put("motd", settings.getMotd());
        managedValues.put("max-players", String.valueOf(settings.getMaxPlayers()));
        managedValues.put("level-name", settings.getLevelName());
        managedValues.put("allow-nether", String.valueOf(settings.getAllowNether()));
        managedValues.put("pvp", String.valueOf(settings.getPvp()));
        managedValues.put("difficulty", settings.getDifficulty());
        managedValues.put("gamemode", settings.getGamemode());
        managedValues.put("view-distance", String.valueOf(settings.getViewDistance()));
        managedValues.put("simulation-distance", String.valueOf(settings.getSimulationDistance()));

        managedValues.forEach(properties::setProperty);

        try (BufferedWriter writer = Files.newBufferedWriter(propsPath, StandardCharsets.UTF_8)) {
            properties.store(writer, "Managed by minecraft-server-operator");
        }
    }

    public Integer getServerPort(Long serverId) {
        return serverPorts.get(serverId);
    }

    public synchronized int allocateServerPort(Long serverId) throws MyException {
        int basePort = appProperties.getMinecraft().getBasePort();
        int maxPort = appProperties.getMinecraft().getMaxPort();
        for (int port = basePort; port <= maxPort; port++) {
            if (!usedPorts.contains(port) && isPortFree(port)) {
                usedPorts.add(port);
                serverPorts.put(serverId, port);
                return port;
            }
        }
        throw new MyException(ErrorCode.NO_FREE_PORTS_AVAILABLE);
    }

    public boolean isPortFree(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    public ServerStatus getServerStatus(Long serverId) {
        return serverStatuses.getOrDefault(serverId, ServerStatus.STOPPED);
    }

    public void setServerStatus(Long serverId, ServerStatus status) {
        serverStatuses.put(serverId, status);
    }

    private void publishServerAndRuntimeChanged(Long serverId) {
        applicationEventPublisher.publishEvent(new MyEvent.ServerStateChanged(serverId));
        applicationEventPublisher.publishEvent(new MyEvent.RuntimeSummaryChanged());
    }

    public int getActiveServerCount() {
        return (int) serverStatuses.values().stream()
            .filter(status -> ServerStatus.STARTING.equals(status) || ServerStatus.RUNNING.equals(status))
            .count();
    }

    public int getAllocatedMemoryMb() {
        return serverAllocatedMemoryMb.values().stream()
            .mapToInt(Integer::intValue)
            .sum();
    }

    public int getAllocatedMemoryMb(Long serverId) {
        return serverAllocatedMemoryMb.getOrDefault(serverId, 0);
    }

    public MyDto.RuntimeSummary getRuntimeSummary() {
        int maxRunningServers = appSettingsService.getMaxRunningServers();
        int maxTotalMemoryMb = appSettingsService.getMaxTotalMemoryMb();
        int allocatedMemoryMb = getAllocatedMemoryMb();
        return new MyDto.RuntimeSummary(
            getActiveServerCount(),
            maxRunningServers,
            allocatedMemoryMb,
            maxTotalMemoryMb,
            Math.max(0, maxTotalMemoryMb - allocatedMemoryMb)
        );
    }

    private String getServersDir() {
        return appProperties.getMinecraft().getServersDir();
    }

    public enum ServerStatus {
        STOPPED, STARTING, RUNNING, ERROR
    }
}