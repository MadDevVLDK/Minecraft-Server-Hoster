package com.example.minecraftserver.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.minecraftserver.dto.MyResponse;
import com.example.minecraftserver.dto.MyRequest;
import com.example.minecraftserver.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    
    private final UserService userService;
    
    @PostMapping("/register")
    public ResponseEntity<MyResponse<Object>> register(@Valid @RequestBody MyRequest.Registration req) {
        userService.register(req.getUsername(), req.getPassword(), req.getInviteToken());
        return MyResponse.success();
    }

    @PostMapping("/login")
    public ResponseEntity<MyResponse<Map<String, ?>>> login(@Valid @RequestBody MyRequest.Login req) {
        if (req.getTotpCode() == null || req.getTotpCode().isBlank()) {
            boolean totpRequired = userService.isTotpRequiredForLogin(req.getUsername(), req.getPassword());
            if (totpRequired) {
                return MyResponse.success(Map.of(
                    "totpRequired", true
                ));
            }
        }

        String token = userService.authenticate(
            req.getUsername(),
            req.getPassword(),
            req.getTotpCode(),
            req.getMinecraftUuid(),
            req.getMinecraftUsername()
        );

        return MyResponse.success(Map.of(
            "totpRequired", false,
            "token", token,
            "username", userService.resolveCanonicalUsername(req.getUsername()),
            "userId", userService.resolveUserIdByUsername(req.getUsername())
        ));
    }
}