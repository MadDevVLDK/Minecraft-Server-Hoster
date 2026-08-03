package com.example.minecraftserver.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "registration_invite_events")
public class RegistrationInviteEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invite_token", nullable = false)
    private String inviteToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private RegistrationInviteEventType eventType;

    @Column(name = "username")
    private String username;

    @Column(name = "happened_at", nullable = false)
    private LocalDateTime happenedAt;

    public RegistrationInviteEvent(String inviteToken,
                                   RegistrationInviteEventType eventType,
                                   String username,
                                   LocalDateTime happenedAt) {
        this.inviteToken = inviteToken;
        this.eventType = eventType;
        this.username = username;
        this.happenedAt = happenedAt;
    }
}