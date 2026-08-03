package com.example.minecraftserver.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.minecraftserver.entity.AppSetting;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> { }