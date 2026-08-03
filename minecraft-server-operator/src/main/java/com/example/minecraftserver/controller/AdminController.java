package com.example.minecraftserver.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.minecraftserver.dto.MyResponse;
import com.example.minecraftserver.config.AppProperties;
import com.example.minecraftserver.dto.MyDto;
import com.example.minecraftserver.dto.MyRequest;
import com.example.minecraftserver.entity.RegistrationInvite;
import com.example.minecraftserver.service.AppSettingsService;
import com.example.minecraftserver.service.RegistrationInviteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final AppProperties appProperties;
    private final AppSettingsService appSettingsService;
    private final RegistrationInviteService registrationInviteService;

    
    @GetMapping({"", "/"})
    public String registrationInvitesPage(Model model) {
        String registrationUrlPrefix = buildRegistrationUrlPrefix();
        List<MyDto.RegistrationInvite> invites = registrationInviteService.getAllInvites().stream()
            .map(invite -> registrationInviteService.toDto(invite, registrationUrlPrefix + invite.getToken()))
            .toList();
        List<MyDto.RegistrationInvite> activeInvites = invites.stream()
            .filter(MyDto.RegistrationInvite::isActive)
            .toList();
        MyDto.RegistrationInviteHistoryPage inviteHistoryPage = registrationInviteService
            .getInviteHistoryPage(registrationUrlPrefix, 0, appProperties.getAdmin().getInviteHistoryPageSize());

        model.addAttribute("activeRegistrationInvites", activeInvites);
        model.addAttribute("inviteHistory", inviteHistoryPage.getItems());
        model.addAttribute("inviteHistoryHasMore", inviteHistoryPage.isHasMore());
        model.addAttribute("inviteHistoryPageSize", appProperties.getAdmin().getInviteHistoryPageSize());
        MyDto.RuntimeSettings runtimeSettings = appSettingsService.getRuntimeSettings();
        model.addAttribute("runtimeSettings", runtimeSettings);
        return "admin-invites";
    }

    @ResponseBody
    @GetMapping({"uri/active", "uri/active/"})
    public ResponseEntity<MyResponse<List<MyDto.RegistrationInvite>>> activeInvites() {
        String registrationUrlPrefix = buildRegistrationUrlPrefix();
        List<MyDto.RegistrationInvite> activeInvites = registrationInviteService.getAllInvites().stream()
            .map(invite -> registrationInviteService.toDto(invite, registrationUrlPrefix + invite.getToken()))
            .filter(MyDto.RegistrationInvite::isActive)
            .toList();
        return MyResponse.success(activeInvites);
    }

    @ResponseBody
    @GetMapping({"uri/history", "uri/history/"})
    public ResponseEntity<MyResponse<MyDto.RegistrationInviteHistoryPage>> inviteHistory(@RequestParam(defaultValue = "0") int page,
                                                                                       @RequestParam(defaultValue = "10") int size) {
        MyDto.RegistrationInviteHistoryPage historyPage = registrationInviteService
            .getInviteHistoryPage(buildRegistrationUrlPrefix(), page, size);
        return MyResponse.success(historyPage);
    }

    @ResponseBody
    @PostMapping({"uri/generate", "uri/generate/"})
    public ResponseEntity<MyResponse<MyDto.RegistrationInvite>> generateInvite() {
        RegistrationInvite invite = registrationInviteService.createInvite();
        return MyResponse.success(
            registrationInviteService.toDto(invite, buildRegistrationUrlPrefix() + invite.getToken())
        );
    }

    @ResponseBody
    @PostMapping({"uri/{token}/deactivate", "uri/{token}/deactivate/"})
    public ResponseEntity<MyResponse<MyDto.RegistrationInvite>> deactivateInvite(@PathVariable String token) {
        RegistrationInvite invite = registrationInviteService.deactivateInvite(token);
        return MyResponse.success(
            registrationInviteService.toDto(invite, buildRegistrationUrlPrefix() + invite.getToken())
        );
    }

    @ResponseBody
    @PostMapping({"/settings/runtime", "/settings/runtime/"})
    public ResponseEntity<MyResponse<MyDto.RuntimeSettings>> updateRuntimeSettings(@Valid @RequestBody MyRequest.RuntimeSettings runtimeSettingsRequest) {
        return MyResponse.success(appSettingsService.updateRuntimeSettings(
            runtimeSettingsRequest.getMaxRunningServers(),
            runtimeSettingsRequest.getMaxTotalMemoryGb()
        ));
    }

    private String buildRegistrationUrlPrefix() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/register")
            .queryParam("invite", "")
            .toUriString();
    }
}