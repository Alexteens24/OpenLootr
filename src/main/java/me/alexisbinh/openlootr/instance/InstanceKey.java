package me.alexisbinh.openlootr.instance;

import java.util.Objects;
import java.util.UUID;

public record InstanceKey(UUID containerId, UUID playerId) {
    public InstanceKey {
        Objects.requireNonNull(containerId, "containerId");
        Objects.requireNonNull(playerId, "playerId");
    }
}
