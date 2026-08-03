package com.example.minecraftserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.minecraftserver.dto.MyEvent;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;
import com.example.minecraftserver.service.ServerProcessManager.ServerStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModService {

    private final ServerProcessManager processManager;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${app.minecraft.servers-dir}")
    private String serversDir;

    public Path getModsFolder(Long serverId, Long ownerId) {
        return Paths.get(serversDir, String.valueOf(ownerId),
                String.valueOf(serverId), "mods").toAbsolutePath().normalize();
    }

    public List<String> listMods(Long serverId, Long ownerId) throws MyException {
        Path modsDir = getModsFolder(serverId, ownerId);
        if (!Files.exists(modsDir)) {
            return List.of();
        }
        try (var stream = Files.list(modsDir)) {
            return stream.filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(name -> name.endsWith(".jar")).toList();
        } catch (IOException e) {
            log.error("Failed to list mods for serverId: " + serverId + ", ownerId: " + ownerId, e);
            throw new MyException(ErrorCode.FAILED_TO_READ_MODS_LIST);
        }
    }

    public void uploadMod(Long serverId, Long ownerId, MultipartFile file) throws MyException {
        MyException.throwIf(
            !isModManagementAllowed(serverId), 
            ErrorCode.SERVER_MUST_BE_STOPPED_TO_UPLOAD_MODS
        );
        MyException.throwIf(
            file.isEmpty() || !file.getOriginalFilename().endsWith(".jar"), 
            ErrorCode.ONLY_JAR_FILES_ALLOWED_FOR_UPLOAD
        );

        try (InputStream inputStream = file.getInputStream()) {
            Path modsDir = getModsFolder(serverId, ownerId);
            Files.createDirectories(modsDir);
            Path targetFile = modsDir.resolve(file.getOriginalFilename()).normalize();
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to upload mod file: " + file.getOriginalFilename(), e);
            throw new MyException(ErrorCode.FAILED_TO_UPLOAD_MOD);
        }
        applicationEventPublisher.publishEvent(new MyEvent.ServerStateChanged(serverId));
    }

    public void deleteMod(Long serverId, Long ownerId, String modFileName) throws MyException {
        MyException.throwIf(
            !isModManagementAllowed(serverId), 
            ErrorCode.SERVER_MUST_BE_STOPPED_TO_DELETE_MODS
        );
        MyException.throwIf(
            !modFileName.endsWith(".jar"), 
            ErrorCode.ONLY_JAR_FILES_ALLOWED_FOR_DELETE
        );

        Path modFile = getModsFolder(serverId, ownerId).resolve(modFileName);
        MyException.throwIf(
            !Files.exists(modFile), 
            ErrorCode.MOD_NOT_FOUND
        );

        try{
            Files.deleteIfExists(modFile);
        } catch (IOException e) {
            log.error("Failed to delete mod file: " + modFile.toString(), e);
            throw new MyException(ErrorCode.MOD_NOT_FOUND);
        }
        applicationEventPublisher.publishEvent(new MyEvent.ServerStateChanged(serverId));
    }

    public boolean isModManagementAllowed(Long serverId) {
        return ServerStatus.STOPPED.equals(processManager.getServerStatus(serverId));
    }
}