package me.alexisbinh.openlootr.container;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BlockLocator(UUID worldId, BlockPosition owner, List<BlockPosition> members)
        implements ContainerLocator {
    public BlockLocator {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(owner, "owner");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (members.isEmpty() || !members.contains(owner)) {
            throw new IllegalArgumentException("block locator members must include its owner");
        }
    }

    public static BlockLocator single(UUID worldId, BlockPosition position) {
        return new BlockLocator(worldId, position, List.of(position));
    }
}
