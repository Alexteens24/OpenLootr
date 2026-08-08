package me.alexisbinh.openlootr.paper.container;

import me.alexisbinh.openlootr.container.BlockPosition;
import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.container.ContainerAdapter;
import me.alexisbinh.openlootr.container.ContainerAdapterRegistry;
import me.alexisbinh.openlootr.container.ContainerKind;
import me.alexisbinh.openlootr.container.ContainerMetadata;
import me.alexisbinh.openlootr.container.ContainerResolution;
import me.alexisbinh.openlootr.container.ResourceKey;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public final class PaperContainerResolver implements ContainerResolver {
    private final NamespacedKey metadataVersionKey;
    private final NamespacedKey containerIdKey;
    private final NamespacedKey lootTableKey;
    private final NamespacedKey sourceLootSeedKey;
    private final ContainerAdapterRegistry<Block> adapters;

    public PaperContainerResolver(Plugin plugin) {
        metadataVersionKey = new NamespacedKey(plugin, "metadata_version");
        containerIdKey = new NamespacedKey(plugin, "container_id");
        lootTableKey = new NamespacedKey(plugin, "loot_table");
        sourceLootSeedKey = new NamespacedKey(plugin, "source_loot_seed");
        adapters = new ContainerAdapterRegistry<>(List.of(new SingleChestAdapter(), new BarrelAdapter()));
    }

    @Override
    public ContainerResolution resolve(Block block) {
        if (!(block.getState() instanceof TileState state) || !(state instanceof Lootable lootable)) {
            return new ContainerResolution.Ignored("not a tile lootable");
        }
        if (state instanceof Chest chest) {
            if (chest.getBlockInventory().getSize() != chest.getInventory().getSize()) {
                boolean managedHalf = hasAnyMetadata(state.getPersistentDataContainer());
                if (chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
                    managedHalf = managedHalf || holderHasMetadata(doubleChest.getLeftSide())
                            || holderHasMetadata(doubleChest.getRightSide());
                }
                return managedHalf
                        ? new ContainerResolution.Broken("managed double chests are unsupported")
                        : new ContainerResolution.Ignored("unmanaged double chest");
            }
        }
        Optional<ContainerAdapter<? super Block>> selected = adapters.find(block);
        if (selected.isEmpty()) {
            return hasAnyMetadata(state.getPersistentDataContainer())
                    ? new ContainerResolution.Broken("metadata exists on unsupported lootable")
                    : new ContainerResolution.Ignored("lootable type is not allowlisted");
        }
        ContainerAdapter<? super Block> adapter = selected.orElseThrow();
        ContainerKind kind = adapter.kind(block);
        int logicalSize = adapter.logicalSize(block);

        PersistentDataContainer pdc = state.getPersistentDataContainer();
        if (hasAnyMetadata(pdc)) {
            return readManaged(block, state, kind, pdc);
        }
        if (lootable.getLootTable() == null) {
            return new ContainerResolution.Ignored("no loot table and no OpenLootr identity");
        }
        ContainerDescriptor descriptor = descriptor(block, kind, logicalSize,
                Optional.empty(), lootable.getLootTable().getKey(), lootable.getSeed(), 0);
        return new ContainerResolution.Candidate(descriptor);
    }

    @Override
    public ContainerResolution adopt(Block block, ContainerResolution.Candidate candidate) {
        ContainerResolution current = resolve(block);
        if (!(current instanceof ContainerResolution.Candidate live)
                || !samePhysicalDescriptor(candidate.descriptor(), live.descriptor())) {
            return current instanceof ContainerResolution.Ignored
                    ? new ContainerResolution.Broken("container changed before identity adoption")
                    : current;
        }
        if (!(block.getState() instanceof TileState state)) {
            return new ContainerResolution.Broken("tile state disappeared during adoption");
        }
        UUID id = UUID.randomUUID();
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        pdc.set(metadataVersionKey, PersistentDataType.INTEGER, ContainerMetadata.CURRENT_VERSION);
        pdc.set(containerIdKey, PersistentDataType.BYTE_ARRAY, uuidBytes(id));
        pdc.set(lootTableKey, PersistentDataType.STRING, live.descriptor().sourceLootTable().toString());
        pdc.set(sourceLootSeedKey, PersistentDataType.LONG, live.descriptor().sourceLootSeed());
        if (!state.update(true, false)) {
            return new ContainerResolution.Broken("failed to persist OpenLootr identity");
        }
        ContainerResolution verified = resolve(block);
        if (verified instanceof ContainerResolution.Managed managed
                && managed.descriptor().containerId().orElseThrow().equals(id)) {
            return verified;
        }
        return new ContainerResolution.Broken("OpenLootr identity did not round-trip after adoption");
    }

    private ContainerResolution readManaged(
            Block block, TileState state, ContainerKind kind, PersistentDataContainer pdc
    ) {
        Integer version = pdc.get(metadataVersionKey, PersistentDataType.INTEGER);
        byte[] rawId = pdc.get(containerIdKey, PersistentDataType.BYTE_ARRAY);
        String rawTable = pdc.get(lootTableKey, PersistentDataType.STRING);
        Long seed = pdc.get(sourceLootSeedKey, PersistentDataType.LONG);
        if (version == null || rawId == null || rawTable == null || seed == null) {
            return new ContainerResolution.Broken("partial OpenLootr metadata");
        }
        if (version != ContainerMetadata.CURRENT_VERSION || rawId.length != 16) {
            return new ContainerResolution.Broken("unsupported or malformed OpenLootr metadata");
        }
        try {
            UUID id = bytesUuid(rawId);
            NamespacedKey table = NamespacedKey.fromString(rawTable);
            if (table == null) {
                return new ContainerResolution.Broken("invalid stored loot table");
            }
            return new ContainerResolution.Managed(descriptor(block, kind, ((Container) state).getInventory().getSize(),
                    Optional.of(id), table, seed, version));
        } catch (RuntimeException exception) {
            return new ContainerResolution.Broken("malformed OpenLootr metadata: " + exception.getMessage());
        }
    }

    private boolean hasAnyMetadata(PersistentDataContainer pdc) {
        return pdc.has(metadataVersionKey) || pdc.has(containerIdKey)
                || pdc.has(lootTableKey) || pdc.has(sourceLootSeedKey);
    }

    private boolean holderHasMetadata(org.bukkit.inventory.InventoryHolder holder) {
        return holder instanceof TileState tile && hasAnyMetadata(tile.getPersistentDataContainer());
    }

    private static ContainerDescriptor descriptor(Block block, ContainerKind kind, int size,
                                                   Optional<UUID> id, NamespacedKey table,
                                                   long seed, int version) {
        return new ContainerDescriptor(id, block.getWorld().getUID(),
                new BlockPosition(block.getX(), block.getY(), block.getZ()), kind, size,
                new ResourceKey(table.getNamespace(), table.getKey()), seed, version);
    }

    private static boolean samePhysicalDescriptor(ContainerDescriptor first, ContainerDescriptor second) {
        return first.worldId().equals(second.worldId())
                && first.position().equals(second.position())
                && first.kind() == second.kind()
                && first.logicalSize() == second.logicalSize()
                && first.sourceLootTable().equals(second.sourceLootTable())
                && first.sourceLootSeed() == second.sourceLootSeed();
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits()).array();
    }

    private static UUID bytesUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
