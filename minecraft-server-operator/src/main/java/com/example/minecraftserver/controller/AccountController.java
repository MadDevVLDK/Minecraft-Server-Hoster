package com.example.minecraftserver.controller;

import com.example.minecraftserver.dto.MyResponse;
import com.example.minecraftserver.dto.MyDto;
import com.example.minecraftserver.dto.MyRequest;
import com.example.minecraftserver.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/account")
public class AccountController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<MyResponse<MyDto.UserProfile>> getProfile(@RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            userService.getProfile(userId)
        );
    }

    @PutMapping("/profile")
    public ResponseEntity<MyResponse<MyDto.UserProfile>> updateProfile(@RequestAttribute("userId") Long userId,
                                                        @Valid @RequestBody MyRequest.AccountProfileUpdate request) {
        return MyResponse.success(
            userService.updateProfile(userId, request)
        );
    }

    @PutMapping("/password")
    public ResponseEntity<MyResponse<MyDto.UserProfile>> updatePassword(@RequestAttribute("userId") Long userId,
                                                         @Valid @RequestBody MyRequest.AccountPasswordUpdate request) {
        return MyResponse.success(
            userService.updatePassword(userId, request)
        );
    }

    @PutMapping("/minecraft")
    public ResponseEntity<MyResponse<MyDto.UserProfile>> updateMinecraft(@RequestAttribute("userId") Long userId,
                                                          @Valid @RequestBody MyRequest.AccountMinecraftUpdate request) {
        return MyResponse.success(
            userService.updateMinecraftIdentity(userId, request)
        );
    }

    @PostMapping("/totp/setup")
    public ResponseEntity<MyResponse<MyDto.TotpSetupResponse>> beginTotpSetup(@RequestAttribute("userId") Long userId) {
        return MyResponse.success(
            userService.beginTotpSetup(userId)
        );
    }

    @PostMapping("/totp/enable")
    public ResponseEntity<MyResponse<MyDto.UserProfile>> enableTotp(@RequestAttribute("userId") Long userId,
                                                     @Valid @RequestBody MyRequest.TotpVerify request) {
        return MyResponse.success(
            userService.enableTotp(userId, request.getCode())
        );
    }

    @PostMapping("/totp/disable")
    public ResponseEntity<MyResponse<MyDto.UserProfile>> disableTotp(@RequestAttribute("userId") Long userId,
                                                                   @Valid @RequestBody MyRequest.TotpVerify request) {
        return MyResponse.success(
            userService.disableTotp(userId, request.getCode())
        );
    }

    @PostMapping("/proxy/minecraft/unlink")
    public ResponseEntity<MyResponse<MyDto.UserProfile>> unlinkMinecraftFromProxy(@RequestAttribute("userId") Long userId,
                                                                                @RequestHeader("X-Minecraft-UUID") String minecraftUuid) {
        return MyResponse.success(
            userService.unlinkMinecraftIdentity(userId, minecraftUuid)
        );
    }
}