package com.example.minecraftserver.repository;

import com.example.minecraftserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByMinecraftUuid(String minecraftUuid);
    boolean existsByUsername(String username);
    boolean existsByMinecraftUuid(String minecraftUuid);
}