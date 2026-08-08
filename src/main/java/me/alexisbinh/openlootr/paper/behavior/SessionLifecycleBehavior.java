package me.alexisbinh.openlootr.paper.behavior;

import me.alexisbinh.openlootr.container.ContainerDescriptor;
import org.bukkit.entity.Player;

public interface SessionLifecycleBehavior {
    SessionLifecycleBehavior NOOP = new SessionLifecycleBehavior() { };

    default void opened(Player player, ContainerDescriptor descriptor, boolean created) { }

    default void closed(ContainerDescriptor descriptor) { }
}
