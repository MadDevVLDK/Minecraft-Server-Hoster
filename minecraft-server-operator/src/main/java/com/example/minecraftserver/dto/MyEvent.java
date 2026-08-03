package com.example.minecraftserver.dto;

public final class MyEvent {
    public record RuntimeSummaryChanged() { }

    public record ServerDeleted(Long serverId) { }

    public record ServerStateChanged(Long serverId) { }
}
