package com.example.minecraftserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.minecraftserver.service.ServerProcessManager;
import com.example.minecraftserver.service.UserService;

import lombok.RequiredArgsConstructor;

@EnableScheduling
@RequiredArgsConstructor
@SpringBootApplication
public class FabricServerApplication {

    private final ServerProcessManager processManager;
    private final UserService userService;

    public static void main(String[] args) {
        SpringApplication.run(FabricServerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        userService.ensureDefaultUserExists();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            processManager.stopAllServers();
        }));
    }
}