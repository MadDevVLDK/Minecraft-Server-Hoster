package com.example.minecraftserver.service;

import com.example.minecraftserver.config.AppProperties;
import com.example.minecraftserver.dto.MyDto;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersionService {
    
    private final AppProperties appProperties;

    private List<String> minecraftVersions = new ArrayList<>();
    private List<String> loaderVersions = new ArrayList<>();
    private List<String> installerVersions = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GAME_VERSIONS_API = "https://meta.fabricmc.net/v2/versions/game";
    private static final String LOADER_VERSIONS_API = "https://meta.fabricmc.net/v2/versions/loader";
    private static final String INSTALLER_VERSIONS_API = "https://meta.fabricmc.net/v2/versions/installer";


    public MyDto.Versions getVersions() {
        lock.lock();
        try {
            return new MyDto.Versions(
                new ArrayList<>(minecraftVersions),
                new ArrayList<>(loaderVersions),
                new ArrayList<>(installerVersions)
            );
        } finally {
            lock.unlock();
        }
    }

    public void updateVersions() throws IOException {
        String gameJson = restTemplate.getForObject(GAME_VERSIONS_API, String.class);
        JsonNode gameNode = objectMapper.readTree(gameJson);
        List<String> newMcVersions = new ArrayList<>();
        for (JsonNode version : gameNode) {
            newMcVersions.add(version.get("version").asText());
        }

        String loaderJson = restTemplate.getForObject(LOADER_VERSIONS_API, String.class);
        JsonNode loaderNode = objectMapper.readTree(loaderJson);
        List<String> newLoaderVersions = new ArrayList<>();
        for (JsonNode loader : loaderNode) {
            newLoaderVersions.add(loader.get("version").asText());
        }

        String installerJson = restTemplate.getForObject(INSTALLER_VERSIONS_API, String.class);
        JsonNode installerNode = objectMapper.readTree(installerJson);
        List<String> newInstallerVersions = new ArrayList<>();
        for (JsonNode installer : installerNode) {
            newInstallerVersions.add(installer.get("version").asText());
        }

        lock.lock();
        try {
            this.minecraftVersions = newMcVersions;
            this.loaderVersions = newLoaderVersions;
            this.installerVersions = newInstallerVersions;
        } finally {
            lock.unlock();
        }
    }

    public void downloadServerForUser(long serverId, Long ownerId,
                String minecraftVersion, String loaderVersion, String installerVersion) throws MyException {
        
        try {
            Path targetDir = Paths.get(getServersDir(), String.valueOf(ownerId), String.valueOf(serverId));
            Files.createDirectories(targetDir);

            Path modsDir = targetDir.resolve("mods");
            Files.createDirectories(modsDir);

            String downloadUrl = String.format(
                "https://meta.fabricmc.net/v2/versions/loader/%s/%s/%s/server/jar",
                minecraftVersion, loaderVersion, installerVersion
            );

            String fileName = String.format("fabric-server-mc.%s-loader.%s-launcher.%s.jar",
                    minecraftVersion, loaderVersion, installerVersion);
            Path filePath = targetDir.resolve(fileName);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            MyException.throwIf(
                response.statusCode() != 200, 
                ErrorCode.FAILED_TO_DOWNLOAD_SERVER
            );

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(response.body());
            }

            Files.writeString(targetDir.resolve("eula.txt"), "eula=true");
        } catch (IOException | InterruptedException e) {
            log.error("Failed to download and save servers list --> ", e);
            throw new MyException(ErrorCode.FAILED_TO_DOWNLOAD_SERVER);
        }
    }

    @Scheduled(fixedDelay = 7200000)
    public void scheduledUpdate() {
        try {
            updateVersions();
        } catch (IOException e) {
            log.error("Scheduled versions update failed", e);
        }
    }

    private String getServersDir() {
        return appProperties.getMinecraft().getServersDir();
    }
}