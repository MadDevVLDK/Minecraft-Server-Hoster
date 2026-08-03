package com.example.velocityauth;

import com.velocitypowered.api.proxy.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class PlayerSessionManager {

    private static final int LOGIN_TIMEOUT_SECONDS = 120;
    private static final int REMINDER_INTERVAL_SECONDS = 20;

    private final AuthMessages messages;
    private final Map<String, String> playerTokens = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> reminderTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> kickTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> loginDeadlines = new ConcurrentHashMap<>();

    PlayerSessionManager(AuthMessages messages) {
        this.messages = messages;
    }

    String requireToken(Player player) {
        String token = getToken(player);
        if (token == null) {
            messages.sendError(player, "Ты еще не авторизован. Используй /login в limbo.");
            messages.sendCommandUsage(player, "/login <логин> <пароль> [totp-код]", "Войти в аккаунт и привязать текущий Minecraft-профиль при необходимости.");
        }
        return token;
    }

    String getToken(Player player) {
        return playerTokens.get(player.getUniqueId().toString());
    }

    void activateSession(Player player, String token) {
        String uuid = player.getUniqueId().toString();
        playerTokens.put(uuid, token);
        cancelTasks(uuid);
    }

    void clearSession(Player player) {
        clearSession(player.getUniqueId().toString());
    }

    void clearSession(String uuid) {
        playerTokens.remove(uuid);
        cancelTasks(uuid);
    }

    void expireSession(Player player) {
        playerTokens.remove(player.getUniqueId().toString());
        messages.sendWarning(player, "Сессия истекла. Нужно снова выполнить /login.");
        startLoginReminders(player);
    }

    void startLoginReminders(Player player) {
        String uuid = player.getUniqueId().toString();
        cancelTasks(uuid);
        long deadlineMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(LOGIN_TIMEOUT_SECONDS);
        loginDeadlines.put(uuid, deadlineMillis);

        messages.sendWarning(player, "Используй /login <логин> <пароль> [totp-код]. До кика осталось " + LOGIN_TIMEOUT_SECONDS + " сек.");
        messages.sendCommandHelp(player);

        ScheduledFuture<?> kickTask = scheduler.schedule(() -> {
            if (!playerTokens.containsKey(uuid) && player.isActive()) {
                player.disconnect(Component.text(
                    "Ты был отключен из limbo, потому что не прошел авторизацию за " + LOGIN_TIMEOUT_SECONDS + " сек.",
                    NamedTextColor.RED
                ));
                cancelTasks(uuid);
            }
        }, LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        ScheduledFuture<?> reminderTask = scheduler.scheduleAtFixedRate(() -> {
            if (!playerTokens.containsKey(uuid) && player.isActive()) {
                Long deadline = loginDeadlines.get(uuid);
                if (deadline == null) {
                    cancelTasks(uuid);
                    return;
                }

                long remainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(deadline - System.currentTimeMillis()));
                if (remainingSeconds > 0) {
                    messages.sendWarning(player, "Напоминание: /login <логин> <пароль> [totp-код]. До кика осталось " + remainingSeconds + " сек.");
                }
            } else {
                cancelTasks(uuid);
            }
        }, REMINDER_INTERVAL_SECONDS, REMINDER_INTERVAL_SECONDS, TimeUnit.SECONDS);

        reminderTasks.put(uuid, reminderTask);
        kickTasks.put(uuid, kickTask);
    }

    private void cancelTasks(String uuid) {
        loginDeadlines.remove(uuid);
        ScheduledFuture<?> reminder = reminderTasks.remove(uuid);
        if (reminder != null) {
            reminder.cancel(false);
        }
        ScheduledFuture<?> kick = kickTasks.remove(uuid);
        if (kick != null) {
            kick.cancel(false);
        }
    }
}