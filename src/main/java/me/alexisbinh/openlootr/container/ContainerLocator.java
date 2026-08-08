package me.alexisbinh.openlootr.container;

import java.util.UUID;

/** Immutable physical locator; safe to cross Paper scheduler and SQLite boundaries. */
public sealed interface ContainerLocator permits BlockLocator, EntityLocator {
    UUID worldId();
}
