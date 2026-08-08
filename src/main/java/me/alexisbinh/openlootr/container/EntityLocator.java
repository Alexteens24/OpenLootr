package me.alexisbinh.openlootr.container;

import java.util.Objects;
import java.util.UUID;

public record EntityLocator(UUID worldId, UUID entityId) implements ContainerLocator {
    public EntityLocator {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(entityId, "entityId");
    }
}
