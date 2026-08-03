package com.example.minecraftserver.repository;

import com.example.minecraftserver.entity.GameServer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GameServerRepository extends JpaRepository<GameServer, Long> {
    @Override
    @EntityGraph(attributePaths = {"owner", "whitelist"})
    List<GameServer> findAll();

    @Override
    @EntityGraph(attributePaths = {"owner", "whitelist"})
    Optional<GameServer> findById(Long id);

    @EntityGraph(attributePaths = {"owner", "whitelist"})
    List<GameServer> findByOwnerId(Long ownerId);

    @EntityGraph(attributePaths = {"owner", "whitelist"})
    Optional<GameServer> findByOwnerIdAndServerName(Long ownerId, String serverName);

    long countByOwnerId(Long ownerId);
}