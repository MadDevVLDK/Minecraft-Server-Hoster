package com.example.minecraftserver.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.minecraftserver.entity.RegistrationInvite;

public interface RegistrationInviteRepository extends JpaRepository<RegistrationInvite, Long> {
    Optional<RegistrationInvite> findByToken(String token);
    List<RegistrationInvite> findAllByOrderByCreatedAtDesc();
}