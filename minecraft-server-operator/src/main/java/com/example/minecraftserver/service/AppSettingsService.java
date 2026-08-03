package com.example.minecraftserver.service;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.minecraftserver.config.AppProperties;
import com.example.minecraftserver.dto.MyDto;
import com.example.minecraftserver.dto.MyEvent;
import com.example.minecraftserver.entity.AppSetting;
import com.example.minecraftserver.exception.ErrorCode;
import com.example.minecraftserver.exception.MyException;
import com.example.minecraftserver.repository.AppSettingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppSettingsService {

    private static final String MAX_RUNNING_SERVERS_KEY = "minecraft.max-running-servers";
    private static final String MAX_TOTAL_MEMORY_MB_KEY = "minecraft.max-total-memory-mb";

    private final AppSettingRepository appSettingRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public int getMaxRunningServers() {
        return getIntSetting(MAX_RUNNING_SERVERS_KEY, appProperties.getMinecraft().getDefaultMaxRunningServers());
    }

    @Transactional(readOnly = true)
    public int getMaxTotalMemoryMb() {
        return getIntSetting(MAX_TOTAL_MEMORY_MB_KEY, appProperties.getMinecraft().getDefaultMaxTotalMemoryMb());
    }

    @Transactional(readOnly = true)
    public int getMaxTotalMemoryGb() {
        return Math.max(1, getMaxTotalMemoryMb() / 1024);
    }

    @Transactional(readOnly = true)
    public MyDto.RuntimeSettings getRuntimeSettings() {
        int maxRunningServers = getMaxRunningServers();
        int maxTotalMemoryMb = getMaxTotalMemoryMb();
        return new MyDto.RuntimeSettings(
            maxRunningServers,
            Math.max(1, maxTotalMemoryMb / 1024),
            maxTotalMemoryMb
        );
    }

    @Transactional
    public int updateMaxRunningServers(int maxRunningServers) throws MyException {
        MyException.throwIf(
            maxRunningServers < 1, 
            ErrorCode.MAX_RUNNING_SERVERS_TOO_LOW
        );

        appSettingRepository.save(new AppSetting(
            MAX_RUNNING_SERVERS_KEY,
            String.valueOf(maxRunningServers),
            LocalDateTime.now()
        ));
        return maxRunningServers;
    }

    @Transactional
    public int updateMaxTotalMemoryGb(int maxTotalMemoryGb) throws MyException {
        MyException.throwIf(
            maxTotalMemoryGb < 1, 
            ErrorCode.MAX_TOTAL_MEMORY_TOO_LOW
        );

        int maxTotalMemoryMb = maxTotalMemoryGb * 1024;
        appSettingRepository.save(new AppSetting(
            MAX_TOTAL_MEMORY_MB_KEY,
            String.valueOf(maxTotalMemoryMb),
            LocalDateTime.now()
        ));
        return maxTotalMemoryMb;
    }

    @Transactional
    public MyDto.RuntimeSettings updateRuntimeSettings(int maxRunningServers, int maxTotalMemoryGb) throws MyException {
        updateMaxRunningServers(maxRunningServers);
        int maxTotalMemoryMb = updateMaxTotalMemoryGb(maxTotalMemoryGb);
        var runtimeSettings = new MyDto.RuntimeSettings(maxRunningServers, maxTotalMemoryGb, maxTotalMemoryMb);
        applicationEventPublisher.publishEvent(new MyEvent.RuntimeSummaryChanged());
        return runtimeSettings;
    }

    private int getIntSetting(String key, int defaultValue) {
        return appSettingRepository.findById(key)
            .map(AppSetting::getSettingValue)
            .map(value -> {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ex) {
                    return defaultValue;
                }
            })
            .orElse(defaultValue);
    }
}