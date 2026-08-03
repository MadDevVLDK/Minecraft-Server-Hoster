package com.example.minecraftserver.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.minecraftserver.config.AppProperties;
import com.example.minecraftserver.dto.MyResponse;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;
import com.example.minecraftserver.service.RegistrationInviteService;
import com.example.minecraftserver.service.ServerOrchestrator;
import com.example.minecraftserver.service.UserService;
import com.example.minecraftserver.service.VersionService;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final AppProperties appProperties;
    private final VersionService versionService;
    private final UserService userService;
    private final ServerOrchestrator serverOrchestrator;
    private final RegistrationInviteService registrationInviteService;

    @GetMapping("/")
    public String home(HttpSession session) {
        if (!(session.getAttribute("userId") instanceof Long)) return "redirect:/login";
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(@RequestParam(name = "invite", required = false) String inviteToken, Model model) {
        try {
            registrationInviteService.requireActiveInvite(inviteToken);
        } catch (MyException ex) {
            return "redirect:/login";
        }

        model.addAttribute("inviteToken", inviteToken);
        return "register";
    }

    @GetMapping("/profile")
    public String profilePage(HttpSession session, Model model) {
        Object userIdValue = session.getAttribute("userId");
        if (!(userIdValue instanceof Long userId)) return "redirect:/login";

        model.addAttribute("dashboardProfile", userService.getProfile(userId));
        return "profile";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Object userIdValue = session.getAttribute("userId");
        if (!(userIdValue instanceof Long userId)) return "redirect:/login";

        model.addAttribute("dashboardProfile", userService.getProfile(userId));
        model.addAttribute("myServers", serverOrchestrator.getMyServers(userId));
        model.addAttribute("publicServers", serverOrchestrator.getPublicServers(userId));
        model.addAttribute("accessibleServers", serverOrchestrator.getAccessibleServers(userId));
        model.addAttribute("runtimeSummary", serverOrchestrator.getRuntimeSummary());
        model.addAttribute("limboConnectUrl", appProperties.getVelocityUrl());

        return "dashboard";
    }

    @GetMapping("/servers/create")
    public String createServerPage(HttpSession session, Model model) {
        Object userIdValue = session.getAttribute("userId");
        if (!(userIdValue instanceof Long userId)) return "redirect:/login";

        model.addAttribute("dashboardProfile", userService.getProfile(userId));
        model.addAttribute("versions", versionService.getVersions());
        return "create-server";
    }

    @GetMapping("/servers/{id}")
    public String serverDetailsPage(@PathVariable Long id, HttpSession session, Model model) {
        Object userIdValue = session.getAttribute("userId");
        if (!(userIdValue instanceof Long userId)) return "redirect:/login";

        model.addAttribute("dashboardProfile", userService.getProfile(userId));
        model.addAttribute("serverId", id);
        model.addAttribute("readOnly", false);
        return "server-details";
    }

    @GetMapping("/servers/{id}/public")
    public String publicServerDetailsPage(@PathVariable Long id, HttpSession session, Model model) {
        Object userIdValue = session.getAttribute("userId");
        if (!(userIdValue instanceof Long userId)) return "redirect:/login";
        
        model.addAttribute("dashboardProfile", userService.getProfile(userId));
        model.addAttribute("serverId", id);
        model.addAttribute("readOnly", true);
        return "server-details";
    }

    @GetMapping("/servers/{id}/view")
    public String viewerServerDetailsPage(@PathVariable Long id, HttpSession session, Model model) {
        Object userIdValue = session.getAttribute("userId");
        if (!(userIdValue instanceof Long userId)) return "redirect:/login";

        model.addAttribute("dashboardProfile", userService.getProfile(userId));
        model.addAttribute("serverId", id);
        model.addAttribute("readOnly", true);
        return "server-details";
    }

    @ResponseBody
    @PostMapping("/web/login/success")
    public ResponseEntity<MyResponse<Object>> loginSuccess(@RequestBody Map<String, Object> payload, HttpSession session) {
        Object userIdValue = payload.get("userId");
        if (!(userIdValue instanceof Number userIdNumber)) {
            return MyResponse.error(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "userId is required");
        }

        session.setAttribute("userId", userIdNumber.longValue());
        return MyResponse.success();
    }
}