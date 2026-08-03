package com.example.minecraftserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "game_server")
public class GameServer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    private String serverName;

    private String minecraftVersion;

    private String loaderVersion;

    private String installerVersion;

    private LocalDateTime createdAt;
    
    private boolean isPublic;

    @Embedded
    private ServerSettings settings;

    @ElementCollection
    @CollectionTable(name = "server_whitelist", joinColumns = @JoinColumn(name = "server_id"))
    @Column(name = "user_id")
    private Set<Long> whitelist = new HashSet<>();
}