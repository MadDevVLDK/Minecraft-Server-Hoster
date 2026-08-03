package com.example.minecraftserver.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.minecraftserver.dto.MyDto;
import com.example.minecraftserver.entity.RegistrationInvite;
import com.example.minecraftserver.entity.RegistrationInviteEvent;
import com.example.minecraftserver.entity.RegistrationInviteEventType;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;
import com.example.minecraftserver.repository.RegistrationInviteEventRepository;
import com.example.minecraftserver.repository.RegistrationInviteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationInviteService {

    private final RegistrationInviteRepository registrationInviteRepository;
    private final RegistrationInviteEventRepository registrationInviteEventRepository;

    @Transactional
    public RegistrationInvite createInvite() {
        LocalDateTime now = LocalDateTime.now();
        RegistrationInvite invite = new RegistrationInvite(
            UUID.randomUUID().toString(),
            now,
            now.plusDays(1)
        );
        RegistrationInvite savedInvite = registrationInviteRepository.save(invite);
        recordEvent(savedInvite.getToken(), RegistrationInviteEventType.ACTIVATED, null, now);
        return savedInvite;
    }

    @Transactional(readOnly = true)
    public RegistrationInvite requireActiveInvite(String token) {
        MyException.throwIf(
            token == null || token.isBlank(), 
            ErrorCode.REGISTRATION_INVITE_REQUIRED
        );

        RegistrationInvite invite = registrationInviteRepository.findByToken(token.trim())
            .orElseThrow(() -> new MyException(ErrorCode.REGISTRATION_INVITE_REQUIRED));

        MyException.throwIf(
            !isActive(invite), 
            ErrorCode.REGISTRATION_INVITE_REQUIRED
        );
        return invite;
    }

    @Transactional
    public void consumeInvite(String token, String username) {
        RegistrationInvite invite = requireActiveInvite(token);
        LocalDateTime now = LocalDateTime.now();
        invite.setUsedAt(now);
        invite.setUsedByUsername(username);
        recordEvent(invite.getToken(), RegistrationInviteEventType.USED, username, now);
    }

    @Transactional
    public RegistrationInvite deactivateInvite(String token) throws MyException {
        MyException.throwIf(
            token == null || token.isBlank(), 
            ErrorCode.INVALID_INVITE_TOKEN
        );

        RegistrationInvite invite = registrationInviteRepository.findByToken(token.trim())
            .orElseThrow(() -> new MyException(ErrorCode.INVITE_NOT_FOUND));

        MyException.throwIf(
            !isActive(invite), 
            ErrorCode.ONLY_ACTIVE_INVITES_CAN_BE_DEACTIVATED
        );

        LocalDateTime now = LocalDateTime.now();
        invite.setDeactivatedAt(now);
        recordEvent(invite.getToken(), RegistrationInviteEventType.DEACTIVATED, null, now);
        return invite;
    }

    @Transactional(readOnly = true)
    public List<RegistrationInvite> getAllInvites() {
        return registrationInviteRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public MyDto.RegistrationInviteHistoryPage getInviteHistoryPage(String registrationUrlPrefix, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Slice<RegistrationInviteEvent> historySlice = registrationInviteEventRepository.findAllByOrderByHappenedAtDesc(pageable);

        List<MyDto.RegistrationInviteHistoryEvent> items = historySlice.getContent().stream()
            .map(event -> toHistoryDto(event, registrationUrlPrefix + event.getInviteToken()))
            .toList();

        return new MyDto.RegistrationInviteHistoryPage(items, safePage, historySlice.hasNext());
    }

    
    public MyDto.RegistrationInvite toDto(RegistrationInvite invite, String registrationUrl) {
        return new MyDto.RegistrationInvite(
            invite.getToken(),
            registrationUrl,
            invite.getCreatedAt(),
            invite.getExpiresAt(),
            invite.getUsedAt(),
            invite.getDeactivatedAt(),
            invite.getUsedByUsername(),
            isActive(invite),
            resolveStatusLabel(invite)
        );
    }

    public MyDto.RegistrationInviteHistoryEvent toHistoryDto(RegistrationInviteEvent event, String registrationUrl) {
        return new MyDto.RegistrationInviteHistoryEvent(
            event.getInviteToken(),
            registrationUrl,
            resolveEventLabel(event.getEventType()),
            event.getHappenedAt(),
            event.getUsername()
        );
    }


    private boolean isActive(RegistrationInvite invite) {
        return invite.getUsedAt() == null
            && invite.getDeactivatedAt() == null
            && invite.getExpiresAt() != null
            && invite.getExpiresAt().isAfter(LocalDateTime.now());
    }

    private String resolveStatusLabel(RegistrationInvite invite) {
        if (isActive(invite)) {
            return resolveEventLabel(RegistrationInviteEventType.ACTIVATED);
        }
        if (invite.getDeactivatedAt() != null) {
            return resolveEventLabel(RegistrationInviteEventType.DEACTIVATED);
        }
        if (invite.getUsedAt() != null) {
            return resolveEventLabel(RegistrationInviteEventType.USED);
        }
        return resolveEventLabel(RegistrationInviteEventType.EXPIRED);
    }

    private String resolveEventLabel(RegistrationInviteEventType eventType) {
        return switch (eventType) {
            case ACTIVATED -> "Активирована";
            case DEACTIVATED -> "Деактивирована";
            case USED -> "Использована";
            case EXPIRED -> "Просрочена";
            default -> "Неизвестное событие";
        };
    }

    private void recordEvent(String token, RegistrationInviteEventType eventType, 
                             String username, LocalDateTime happenedAt) {
        registrationInviteEventRepository.save(new RegistrationInviteEvent(
            token, eventType, username, happenedAt
        ));
    }
}