package com.example.minecraftserver.controller;

import com.example.minecraftserver.dto.MyResponse;
import com.example.minecraftserver.dto.MyDto;
import com.example.minecraftserver.dto.MyRequest;
import com.example.minecraftserver.service.ModService;
import com.example.minecraftserver.service.ServerOrchestrator;
import com.example.minecraftserver.service.UserService;
import com.example.minecraftserver.service.VersionService;
import com.example.minecraftserver.service.ServerOrchestrator.ServerResolveResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/servers")
public class ServerController {

    private final VersionService versionService;
    private final ServerOrchestrator serverOrchestrator;
    private final ModService modService;
    private final UserService userService;


    @GetMapping("/getListVersions")
    public ResponseEntity<MyResponse<MyDto.Versions>> getListVersions() {
        return MyResponse.success(
            versionService.getVersions()
        );
    }

    @SneakyThrows
    @PostMapping("/updateListVersions")
    public ResponseEntity<MyResponse<Object>> updateListVersions() {
        versionService.updateVersions();
        return MyResponse.success();
    }

    @SneakyThrows
    @PostMapping("/create")
    public ResponseEntity<MyResponse<Map<String, Long>>> createServer(@Valid @RequestBody MyRequest.CreateServer request,
                                                       @RequestAttribute("userId") Long userId) {
        return MyResponse.success(Map.of(
            "id", serverOrchestrator.createServer(request, userId)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MyResponse<MyDto.GameServer>> getServerById(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.findAndCheckOwnerDto(id, userId)
        );
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<MyResponse<MyDto.GameServer>> getPublicServerById(@PathVariable Long id) {
        return MyResponse.success(
            serverOrchestrator.getPublicServerDto(id)
        );
    }

    @GetMapping("/viewer/{id}")
    public ResponseEntity<MyResponse<MyDto.GameServer>> getViewerServerById(@PathVariable Long id,
                                                              @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getViewerServerDto(id, userId)
        );
    }

    @GetMapping("/{id}/settings")
    public ResponseEntity<MyResponse<MyDto.ServerConfiguration>> getServerSettings(@PathVariable Long id,
                                                            @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getServerConfiguration(id, userId)
        );
    }

    @GetMapping("/viewer/{id}/settings")
    public ResponseEntity<MyResponse<MyDto.ServerConfiguration>> getViewerServerSettings(@PathVariable Long id,
                                                                  @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getViewerServerConfiguration(id, userId)
        );
    }

    @GetMapping("/{id}/whitelist")
    public ResponseEntity<MyResponse<List<MyDto.WhitelistUser>>> getServerWhitelist(@PathVariable Long id,
                                                             @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getServerWhitelist(id, userId)
        );
    }

    @GetMapping("/viewer/{id}/whitelist")
    public ResponseEntity<MyResponse<List<MyDto.WhitelistUser>>> getViewerServerWhitelist(@PathVariable Long id,
                                                                   @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getViewerServerWhitelist(id, userId)
        );
    }

    @SneakyThrows
    @PostMapping("/{id}/start")
    public ResponseEntity<MyResponse<Object>> startServer(@PathVariable Long id,
                                                           @Valid @RequestBody MyRequest.StartServer request,
                                                           @RequestAttribute("userId") Long userId) {
        serverOrchestrator.startServer(id, userId, request.getMemoryGb());
        return MyResponse.success();
    }

    @GetMapping("/runtime/summary")
    public ResponseEntity<MyResponse<MyDto.RuntimeSummary>> getRuntimeSummary() {
        return MyResponse.success(serverOrchestrator.getRuntimeSummary());
    }

    @SneakyThrows
    @PostMapping("/{id}/stop")
    public ResponseEntity<MyResponse<Object>> stopServer(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        serverOrchestrator.stopServer(id, userId);
        return MyResponse.success();
    }

    @GetMapping("/{id}/download-info")
    public ResponseEntity<MyResponse<MyDto.ServerDownloadInfo>> getDownloadInfo(@PathVariable Long id,
                                                                                @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getServerDownloadInfo(id, userId)
        );
    }

    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadServer(@PathVariable Long id,
                                                                @RequestAttribute("userId") Long userId) {
        MyDto.GameServer server = serverOrchestrator.findAndCheckOwnerDto(id, userId);
        StreamingResponseBody responseBody = outputStream ->
            serverOrchestrator.writeServerArchive(id, userId, outputStream);

        String fileName = "minecraft-server-" + server.getId() + ".zip";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(responseBody);
    }

    @SneakyThrows
    @DeleteMapping("/{id}")
    public ResponseEntity<MyResponse<Object>> deleteServer(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        serverOrchestrator.deleteServer(id, userId);
        return MyResponse.success();
    }

    @GetMapping("/my")
    public ResponseEntity<MyResponse<List<MyDto.GameServer>>> myServers(@RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getMyServers(userId)
        );
    }

    @GetMapping("/public")
    public ResponseEntity<MyResponse<List<MyDto.GameServer>>> publicServers(@RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getPublicServers(userId)
        );
    }

    @GetMapping("/accessible")
    public ResponseEntity<MyResponse<List<MyDto.GameServer>>> getAccessibleServers(@RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getAccessibleServers(userId)
        );
    }

    @GetMapping("/available")
    public ResponseEntity<MyResponse<List<MyDto.GameServer>>> getAvailableServers(@RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getAvailableServers(userId)
        );
    }

    @GetMapping("/proxy/my")
    public ResponseEntity<MyResponse<List<MyDto.GameServer>>> getMyServersForProxy(@RequestAttribute("userId") Long userId,
                                                               @RequestHeader("X-Minecraft-UUID") String minecraftUuid) {
        userService.assertMinecraftIdentity(userId, minecraftUuid);
        return MyResponse.success(
            serverOrchestrator.getMyServers(userId)
        );
    }

    @GetMapping("/proxy/public")
    public ResponseEntity<MyResponse<List<MyDto.GameServer>>> getPublicServersForProxy(@RequestAttribute("userId") Long userId,
                                                                   @RequestHeader("X-Minecraft-UUID") String minecraftUuid) {
        userService.assertMinecraftIdentity(userId, minecraftUuid);
        return MyResponse.success(
            serverOrchestrator.getPublicServers(userId)
        );
    }

    @GetMapping("/proxy/accessible")
    public ResponseEntity<MyResponse<List<MyDto.GameServer>>> getAccessibleServersForProxy(@RequestAttribute("userId") Long userId,
                                                                       @RequestHeader("X-Minecraft-UUID") String minecraftUuid) {
        userService.assertMinecraftIdentity(userId, minecraftUuid);
        return MyResponse.success(
            serverOrchestrator.getAccessibleServers(userId)
        );
    }

    @GetMapping("/proxy/available")
    public ResponseEntity<MyResponse<List<MyDto.GameServer>>> getAvailableServersForProxy(@RequestAttribute("userId") Long userId,
                                                                      @RequestHeader("X-Minecraft-UUID") String minecraftUuid) {
        userService.assertMinecraftIdentity(userId, minecraftUuid);
        return MyResponse.success(
            serverOrchestrator.getAvailableServers(userId)
        );
    }

    @GetMapping("/resolve")
    public ResponseEntity<MyResponse<Map<String, Object>>> resolveServer(@RequestParam Long id, @RequestAttribute("userId") Long userId) {
        ServerResolveResult result = serverOrchestrator.resolveServerById(id, userId);
        return MyResponse.success(Map.of(
            "port", result.getPort(),
            "ownerId", result.getOwnerId(),
            "ownerUsername", result.getOwnerUsername()
        ));
    }

    @GetMapping("/proxy/resolve")
    public ResponseEntity<MyResponse<Map<String, Object>>> resolveServerForProxy(@RequestParam Long id,
                                                                @RequestAttribute("userId") Long userId,
                                                                @RequestHeader("X-Minecraft-UUID") String minecraftUuid) {
        userService.assertMinecraftIdentity(userId, minecraftUuid);
        ServerResolveResult result = serverOrchestrator.resolveServerById(id, userId);
        return MyResponse.success(Map.of(
            "port", result.getPort(),
            "ownerId", result.getOwnerId(),
            "ownerUsername", result.getOwnerUsername()
        ));
    }

    @PutMapping("/{id}/settings")
    public ResponseEntity<MyResponse<MyDto.ServerConfiguration>> updateServerSettings(@PathVariable Long id,
                                                               @Valid @RequestBody MyRequest.ServerConfiguration request,
                                                               @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.updateServerConfiguration(id, userId, request)
        );
    }

    @PostMapping("/{id}/whitelist/add")
    public ResponseEntity<MyResponse<Object>> addToWhitelist(@PathVariable Long id, @RequestParam String username,
                                                         @RequestAttribute("userId") Long userId) {
        serverOrchestrator.addToWhitelist(id, userId, username);
        return MyResponse.success();
    }

    @PostMapping("/{id}/whitelist/remove")
    public ResponseEntity<MyResponse<Object>> removeFromWhitelist(@PathVariable Long id, @RequestParam Long playerToAddId,
                                                              @RequestAttribute("userId") Long userId) {
        serverOrchestrator.removeFromWhitelist(id, userId, playerToAddId);
        return MyResponse.success();
    }

    @SneakyThrows
    @GetMapping("/{id}/logs")
    public ResponseEntity<MyResponse<List<String>>> getServerLogs(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "100") int lines,
                                                        @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getServerLogs(id, userId, lines)
        );
    }

    @SneakyThrows
    @GetMapping("/public/{id}/logs")
    public ResponseEntity<MyResponse<List<String>>> getPublicServerLogs(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "100") int lines) {
        return MyResponse.success(
            serverOrchestrator.getPublicServerLogs(id, lines)
        );
    }

    @SneakyThrows
    @GetMapping("/viewer/{id}/logs")
    public ResponseEntity<MyResponse<List<String>>> getViewerServerLogs(@PathVariable Long id,
                                                              @RequestParam(defaultValue = "100") int lines,
                                                              @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            serverOrchestrator.getViewerServerLogs(id, userId, lines)
        );
    }

    @SneakyThrows
    @GetMapping("/{id}/mods")
    public ResponseEntity<MyResponse<List<String>>> listMods(@PathVariable Long id,
                                                   @RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            modService.listMods(id, userId)
        );
    }

    @SneakyThrows
    @GetMapping("/viewer/{id}/mods")
    public ResponseEntity<MyResponse<List<String>>> listViewerMods(@PathVariable Long id,
                                                         @RequestAttribute("userId") Long userId) {
        var server = serverOrchestrator.getViewerServerDto(id, userId);
        return MyResponse.success(
            modService.listMods(id, server.getOwnerUserId())
        );
    }

    @SneakyThrows
    @PostMapping(value = "/{id}/mods/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MyResponse<Map<String, Object>>> uploadMod(@PathVariable Long id,
                                                    @RequestParam("file") MultipartFile file,
                                                    @RequestAttribute("userId") Long userId) {
        modService.uploadMod(id, userId, file);
        return MyResponse.success(Map.of(
            "file", file.getOriginalFilename()
        ));
    }

    @SneakyThrows
    @DeleteMapping("/{id}/mods/{modFileName}")
    public ResponseEntity<MyResponse<Map<String, Object>>> deleteMod(@PathVariable Long id,
                                                    @PathVariable String modFileName,
                                                    @RequestAttribute("userId") Long userId) {
        modService.deleteMod(id, userId, modFileName);
        return MyResponse.success(Map.of(
            "file", modFileName
        ));
    }
}