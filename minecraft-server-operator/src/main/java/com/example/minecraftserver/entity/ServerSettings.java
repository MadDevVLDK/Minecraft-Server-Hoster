package com.example.minecraftserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class ServerSettings {

    @Column(name = "motd")
    private String motd;

    @Column(name = "max_players")
    private Integer maxPlayers;

    @Column(name = "level_name")
    private String levelName;

    @Column(name = "allow_nether")
    private Boolean allowNether;

    @Column(name = "pvp_enabled")
    private Boolean pvp;

    @Column(name = "difficulty")
    private String difficulty;

    @Column(name = "gamemode")
    private String gamemode;

    @Column(name = "view_distance")
    private Integer viewDistance;

    @Column(name = "simulation_distance")
    private Integer simulationDistance;

    public static ServerSettings defaults(String serverName, String ownerUsername) {
        return new ServerSettings(
            serverName + " | owner " + ownerUsername,
            20,
            "world",
            true,
            true,
            "normal",
            "survival",
            10,
            10
        );
    }
}