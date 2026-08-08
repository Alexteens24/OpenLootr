package me.alexisbinh.openlootr.paper.container;

import me.alexisbinh.openlootr.container.BlockPosition;
import me.alexisbinh.openlootr.container.LootSourceDescriptor;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ContainerRepairPlan(UUID containerId, UUID worldId, List<BlockPosition> members,
                                  BlockPosition missingMember, int missingIndex,
                                  LootSourceDescriptor missingSource, String fingerprint) {
    public ContainerRepairPlan {
        Objects.requireNonNull(containerId, "containerId");
        Objects.requireNonNull(worldId, "worldId");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        Objects.requireNonNull(missingMember, "missingMember");
        Objects.requireNonNull(missingSource, "missingSource");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (members.size() != 2 || !members.contains(missingMember)
                || (missingIndex != 0 && missingIndex != 1)) {
            throw new IllegalArgumentException("invalid double chest repair plan");
        }
    }
}
