package com.example.minecraftserver.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.minecraftserver.entity.RegistrationInviteEvent;

public interface RegistrationInviteEventRepository extends JpaRepository<RegistrationInviteEvent, Long> {
    Slice<RegistrationInviteEvent> findAllByOrderByHappenedAtDesc(Pageable pageable);
}