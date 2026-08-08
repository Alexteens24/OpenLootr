package me.alexisbinh.openlootr.paper.container;

import me.alexisbinh.openlootr.container.BlockLocator;
import me.alexisbinh.openlootr.container.BlockPosition;
import me.alexisbinh.openlootr.container.ContainerAdapter;
import me.alexisbinh.openlootr.container.ContainerAdapterRegistry;
import me.alexisbinh.openlootr.container.ContainerDescriptor;
import me.alexisbinh.openlootr.container.ContainerKind;
import me.alexisbinh.openlootr.container.ContainerMetadata;
import me.alexisbinh.openlootr.container.ContainerResolution;
import me.alexisbinh.openlootr.container.EntityLocator;
import me.alexisbinh.openlootr.container.LootSourceDescriptor;
import me.alexisbinh.openlootr.container.ResourceKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Paper PDC resolver for the deliberately small v1 allowlist. */
public final class PaperContainerResolver implements ContainerResolver {
    private final NamespacedKey metadataVersionKey;
    private final NamespacedKey containerIdKey;
    private final NamespacedKey lootTableKey;
    private final NamespacedKey sourceLootSeedKey;
    private final NamespacedKey logicalOwnerKey;
    private final NamespacedKey memberIndexKey;
    private final ContainerAdapterRegistry<Block> adapters;

    public PaperContainerResolver(Plugin plugin) {
        metadataVersionKey = new NamespacedKey(plugin, "metadata_version");
        containerIdKey = new NamespacedKey(plugin, "container_id");
        lootTableKey = new NamespacedKey(plugin, "loot_table");
        sourceLootSeedKey = new NamespacedKey(plugin, "source_loot_seed");
        logicalOwnerKey = new NamespacedKey(plugin, "logical_owner");
        memberIndexKey = new NamespacedKey(plugin, "member_index");
        adapters = new ContainerAdapterRegistry<>(List.of(new SingleChestAdapter(), new BarrelAdapter()));
    }

    @Override
    public ContainerResolution resolve(Block block) {
        if (!(block.getState() instanceof TileState state) || !(state instanceof Lootable lootable)) {
            return new ContainerResolution.Ignored("not a tile lootable");
        }
        if (state instanceof Chest chest && chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            return resolveDouble(doubleChest);
        }
        Optional<ContainerAdapter<? super Block>> selected = adapters.find(block);
        if (selected.isEmpty()) {
            return hasAnyMetadata(state.getPersistentDataContainer())
                    ? new ContainerResolution.Broken("metadata exists on unsupported lootable")
                    : new ContainerResolution.Ignored("lootable type is not allowlisted");
        }
        ContainerAdapter<? super Block> adapter = selected.orElseThrow();
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        if (hasAnyMetadata(pdc)) {
            return readSingle(block, state, adapter.kind(block), pdc);
        }
        if (lootable.getLootTable() == null) {
            return new ContainerResolution.Ignored("no loot table and no OpenLootr identity");
        }
        return new ContainerResolution.Candidate(singleDescriptor(block, adapter.kind(block),
                adapter.logicalSize(block), Optional.empty(), lootable.getLootTable().getKey(),
                lootable.getSeed(), 0));
    }

    @Override
    public ContainerResolution adopt(Block block, ContainerResolution.Candidate candidate) {
        ContainerResolution current = resolve(block);
        if (!(current instanceof ContainerResolution.Candidate live)
                || !samePhysicalDescriptor(candidate.descriptor(), live.descriptor())) {
            return current instanceof ContainerResolution.Ignored
                    ? new ContainerResolution.Broken("container changed before identity adoption") : current;
        }
        return live.descriptor().kind() == ContainerKind.DOUBLE_CHEST
                ? adoptDouble(live.descriptor()) : adoptSingle(block, live.descriptor());
    }

    @Override
    public ContainerResolution resolve(Entity entity) {
        if (!(entity instanceof StorageMinecart minecart)) {
            return new ContainerResolution.Ignored("entity type is not allowlisted");
        }
        PersistentDataContainer pdc = minecart.getPersistentDataContainer();
        if (hasAnyMetadata(pdc)) {
            return readMinecart(minecart, pdc);
        }
        if (minecart.getLootTable() == null) {
            return new ContainerResolution.Ignored("no loot table and no OpenLootr identity");
        }
        return new ContainerResolution.Candidate(entityDescriptor(minecart, Optional.empty(),
                minecart.getLootTable().getKey(), minecart.getSeed(), 0));
    }

    @Override
    public ContainerResolution adopt(Entity entity, ContainerResolution.Candidate candidate) {
        ContainerResolution current = resolve(entity);
        if (!(entity instanceof StorageMinecart minecart)
                || !(current instanceof ContainerResolution.Candidate live)
                || !samePhysicalDescriptor(candidate.descriptor(), live.descriptor())) {
            return current instanceof ContainerResolution.Ignored
                    ? new ContainerResolution.Broken("entity changed before identity adoption") : current;
        }
        writeBase(minecart, ContainerMetadata.SINGLE_VERSION, minecart.getUniqueId(),
                live.descriptor().lootSources().getFirst());
        ContainerResolution verified = resolve(minecart);
        return verified instanceof ContainerResolution.Managed ? verified
                : new ContainerResolution.Broken("minecart identity did not round-trip after adoption");
    }

    @Override
    public Optional<ContainerRepairPlan> planRepair(Block block) {
        if (!(block.getState() instanceof Chest chest)
                || !(chest.getInventory().getHolder() instanceof DoubleChest holder)
                || !(holder.getLeftSide() instanceof Chest left)
                || !(holder.getRightSide() instanceof Chest right)) {
            return Optional.empty();
        }
        BlockPosition owner = position(left.getBlock());
        MemberMetadata leftMetadata = readMember(left.getPersistentDataContainer());
        MemberMetadata rightMetadata = readMember(right.getPersistentDataContainer());
        boolean validLeft = validMember(leftMetadata, owner, 0);
        boolean validRight = validMember(rightMetadata, owner, 1);
        if (validLeft == validRight) {
            return Optional.empty();
        }
        Chest missing = validLeft ? right : left;
        MemberMetadata existing = validLeft ? leftMetadata : rightMetadata;
        int missingIndex = validLeft ? 1 : 0;
        if (hasAnyMetadata(missing.getPersistentDataContainer()) || missing.getLootTable() == null) {
            return Optional.empty();
        }
        LootSourceDescriptor missingSource = source(missing, missingIndex * 27);
        List<BlockPosition> members = List.of(owner, position(right.getBlock()));
        String fingerprint = fingerprint(existing, members, missingIndex, missingSource);
        return Optional.of(new ContainerRepairPlan(existing.id(), left.getWorld().getUID(), members,
                position(missing.getBlock()), missingIndex, missingSource, fingerprint));
    }

    @Override
    public ContainerResolution repair(Block block, ContainerRepairPlan plan) {
        Optional<ContainerRepairPlan> current = planRepair(block);
        if (current.isEmpty() || !current.orElseThrow().equals(plan)) {
            return new ContainerResolution.Broken("repair target changed after confirmation was issued");
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(plan.worldId());
        if (world == null || !(block(world, plan.missingMember()).getState() instanceof Chest missing)) {
            return new ContainerResolution.Broken("repair target is unavailable");
        }
        writeDoubleMember(missing, plan.containerId(), plan.missingSource(),
                xyz(plan.members().getFirst()), plan.missingIndex());
        if (!missing.update(false, false)) {
            return new ContainerResolution.Broken("repair metadata update failed");
        }
        ContainerResolution verified = resolve(block);
        return verified instanceof ContainerResolution.Managed managed
                && managed.descriptor().containerId().orElseThrow().equals(plan.containerId())
                ? verified : new ContainerResolution.Broken("repair did not restore a valid double chest");
    }

    private ContainerResolution resolveDouble(DoubleChest holder) {
        if (!(holder.getLeftSide() instanceof Chest left) || !(holder.getRightSide() instanceof Chest right)
                || left.getBlock().getType() != Material.CHEST || right.getBlock().getType() != Material.CHEST) {
            return new ContainerResolution.Ignored("unsupported double chest members");
        }
        boolean any = hasAnyMetadata(left.getPersistentDataContainer())
                || hasAnyMetadata(right.getPersistentDataContainer());
        if (any) {
            return readDouble(left, right);
        }
        if (left.getLootTable() == null && right.getLootTable() == null) {
            return new ContainerResolution.Ignored("unmanaged double chest without loot tables");
        }
        if (left.getLootTable() == null || right.getLootTable() == null) {
            return new ContainerResolution.Broken("double chest has only one physical loot source");
        }
        return new ContainerResolution.Candidate(doubleDescriptor(left, right, Optional.empty(), 0,
                source(left, 0), source(right, 27)));
    }

    private ContainerResolution adoptSingle(Block block, ContainerDescriptor descriptor) {
        if (!(block.getState() instanceof TileState state)) {
            return new ContainerResolution.Broken("tile state disappeared during adoption");
        }
        UUID id = UUID.randomUUID();
        writeBase(state, ContainerMetadata.SINGLE_VERSION, id, descriptor.lootSources().getFirst());
        if (!state.update(false, false)) {
            return new ContainerResolution.Broken("failed to persist OpenLootr identity");
        }
        ContainerResolution verified = resolve(block);
        return verified instanceof ContainerResolution.Managed managed
                && managed.descriptor().containerId().orElseThrow().equals(id)
                ? verified : new ContainerResolution.Broken("OpenLootr identity did not round-trip after adoption");
    }

    private ContainerResolution adoptDouble(ContainerDescriptor descriptor) {
        if (!(descriptor.locator() instanceof BlockLocator locator)) {
            return new ContainerResolution.Broken("double chest has a non-block locator");
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(locator.worldId());
        if (world == null || locator.members().size() != 2) {
            return new ContainerResolution.Broken("double chest world or members unavailable");
        }
        Block firstBlock = block(world, locator.members().get(0));
        Block secondBlock = block(world, locator.members().get(1));
        if (!(firstBlock.getState() instanceof Chest left) || !(secondBlock.getState() instanceof Chest right)) {
            return new ContainerResolution.Broken("double chest changed during adoption");
        }
        UUID id = UUID.randomUUID();
        int[] owner = xyz(locator.owner());
        writeDoubleMember(right, id, descriptor.lootSources().get(1), owner, 1);
        if (!right.update(false, false)) {
            return new ContainerResolution.Broken("failed to persist non-owner double chest metadata");
        }
        writeDoubleMember(left, id, descriptor.lootSources().get(0), owner, 0);
        if (!left.update(false, false)) {
            return new ContainerResolution.Broken("partial double chest metadata; owner write failed");
        }
        ContainerResolution verified = resolve(firstBlock);
        return verified instanceof ContainerResolution.Managed managed
                && managed.descriptor().containerId().orElseThrow().equals(id)
                ? verified : new ContainerResolution.Broken("double chest identity verification failed");
    }

    private ContainerResolution readSingle(Block block, TileState state, ContainerKind kind,
                                           PersistentDataContainer pdc) {
        BaseMetadata base = readBase(pdc, ContainerMetadata.SINGLE_VERSION);
        if (base == null) {
            return new ContainerResolution.Broken("partial, malformed, or unsupported OpenLootr metadata");
        }
        return new ContainerResolution.Managed(singleDescriptor(block, kind,
                ((Container) state).getInventory().getSize(), Optional.of(base.id()), base.table(), base.seed(),
                ContainerMetadata.SINGLE_VERSION));
    }

    private ContainerResolution readDouble(Chest left, Chest right) {
        MemberMetadata first = readMember(left.getPersistentDataContainer());
        MemberMetadata second = readMember(right.getPersistentDataContainer());
        BlockPosition expectedOwner = position(left.getBlock());
        if (first == null || second == null || !first.id().equals(second.id())
                || first.index() != 0 || second.index() != 1
                || !first.owner().equals(expectedOwner) || !second.owner().equals(expectedOwner)) {
            return new ContainerResolution.Broken("inconsistent or partial double chest metadata");
        }
        return new ContainerResolution.Managed(doubleDescriptor(left, right, Optional.of(first.id()),
                ContainerMetadata.DOUBLE_VERSION,
                new LootSourceDescriptor(key(first.table()), first.seed(), 0, 27),
                new LootSourceDescriptor(key(second.table()), second.seed(), 27, 27)));
    }

    private ContainerResolution readMinecart(StorageMinecart minecart, PersistentDataContainer pdc) {
        BaseMetadata base = readBase(pdc, ContainerMetadata.SINGLE_VERSION);
        if (base == null || !base.id().equals(minecart.getUniqueId())) {
            return new ContainerResolution.Broken("invalid storage minecart identity metadata");
        }
        return new ContainerResolution.Managed(entityDescriptor(minecart, Optional.of(base.id()),
                base.table(), base.seed(), ContainerMetadata.SINGLE_VERSION));
    }

    private BaseMetadata readBase(PersistentDataContainer pdc, int expectedVersion) {
        Integer version = pdc.get(metadataVersionKey, PersistentDataType.INTEGER);
        byte[] rawId = pdc.get(containerIdKey, PersistentDataType.BYTE_ARRAY);
        String rawTable = pdc.get(lootTableKey, PersistentDataType.STRING);
        Long seed = pdc.get(sourceLootSeedKey, PersistentDataType.LONG);
        if (version == null || version != expectedVersion || rawId == null || rawId.length != 16
                || rawTable == null || seed == null) {
            return null;
        }
        NamespacedKey table = NamespacedKey.fromString(rawTable);
        return table == null ? null : new BaseMetadata(bytesUuid(rawId), table, seed);
    }

    private MemberMetadata readMember(PersistentDataContainer pdc) {
        BaseMetadata base = readBase(pdc, ContainerMetadata.DOUBLE_VERSION);
        int[] owner = pdc.get(logicalOwnerKey, PersistentDataType.INTEGER_ARRAY);
        Integer index = pdc.get(memberIndexKey, PersistentDataType.INTEGER);
        return base == null || owner == null || owner.length != 3 || index == null
                ? null : new MemberMetadata(base.id(), base.table(), base.seed(),
                new BlockPosition(owner[0], owner[1], owner[2]), index);
    }

    private void writeDoubleMember(TileState state, UUID id, LootSourceDescriptor source,
                                   int[] owner, int index) {
        writeBase(state, ContainerMetadata.DOUBLE_VERSION, id, source);
        state.getPersistentDataContainer().set(logicalOwnerKey, PersistentDataType.INTEGER_ARRAY, owner);
        state.getPersistentDataContainer().set(memberIndexKey, PersistentDataType.INTEGER, index);
    }

    private void writeBase(PersistentDataHolder holder, int version, UUID id, LootSourceDescriptor source) {
        PersistentDataContainer pdc = holder.getPersistentDataContainer();
        pdc.set(metadataVersionKey, PersistentDataType.INTEGER, version);
        pdc.set(containerIdKey, PersistentDataType.BYTE_ARRAY, uuidBytes(id));
        pdc.set(lootTableKey, PersistentDataType.STRING, source.lootTable().toString());
        pdc.set(sourceLootSeedKey, PersistentDataType.LONG, source.physicalSeed());
    }

    private boolean hasAnyMetadata(PersistentDataContainer pdc) {
        return pdc.has(metadataVersionKey) || pdc.has(containerIdKey) || pdc.has(lootTableKey)
                || pdc.has(sourceLootSeedKey) || pdc.has(logicalOwnerKey) || pdc.has(memberIndexKey);
    }

    private static ContainerDescriptor singleDescriptor(Block block, ContainerKind kind, int size,
                                                        Optional<UUID> id, NamespacedKey table,
                                                        long seed, int version) {
        BlockPosition position = position(block);
        return new ContainerDescriptor(id, BlockLocator.single(block.getWorld().getUID(), position), kind, size,
                List.of(new LootSourceDescriptor(key(table), seed, 0, size)), version);
    }

    private static ContainerDescriptor doubleDescriptor(Chest left, Chest right, Optional<UUID> id, int version,
                                                        LootSourceDescriptor first, LootSourceDescriptor second) {
        BlockPosition owner = position(left.getBlock());
        return new ContainerDescriptor(id, new BlockLocator(left.getWorld().getUID(), owner,
                List.of(owner, position(right.getBlock()))), ContainerKind.DOUBLE_CHEST, 54,
                List.of(first, second), version);
    }

    private static ContainerDescriptor entityDescriptor(StorageMinecart minecart, Optional<UUID> id,
                                                        NamespacedKey table, long seed, int version) {
        return new ContainerDescriptor(id, new EntityLocator(minecart.getWorld().getUID(), minecart.getUniqueId()),
                ContainerKind.STORAGE_MINECART, 27,
                List.of(new LootSourceDescriptor(key(table), seed, 0, 27)), version);
    }

    private static LootSourceDescriptor source(Chest chest, int offset) {
        return new LootSourceDescriptor(key(chest.getLootTable().getKey()), chest.getSeed(), offset, 27);
    }

    private static ResourceKey key(NamespacedKey table) {
        return new ResourceKey(table.getNamespace(), table.getKey());
    }

    private static boolean samePhysicalDescriptor(ContainerDescriptor first, ContainerDescriptor second) {
        return first.locator().equals(second.locator()) && first.kind() == second.kind()
                && first.logicalSize() == second.logicalSize() && first.lootSources().equals(second.lootSources());
    }

    private static Block block(org.bukkit.World world, BlockPosition position) {
        return world.getBlockAt(position.x(), position.y(), position.z());
    }

    private static BlockPosition position(Block block) {
        return new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    private static int[] xyz(BlockPosition position) {
        return new int[]{position.x(), position.y(), position.z()};
    }

    private static boolean validMember(MemberMetadata metadata, BlockPosition owner, int index) {
        return metadata != null && metadata.index() == index && metadata.owner().equals(owner);
    }

    private static String fingerprint(MemberMetadata existing, List<BlockPosition> members,
                                      int missingIndex, LootSourceDescriptor source) {
        String input = existing.id() + "|" + existing.table() + "|" + existing.seed() + "|"
                + existing.owner() + "|" + existing.index() + "|" + members + "|"
                + missingIndex + "|" + source;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits()).array();
    }

    private static UUID bytesUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private record BaseMetadata(UUID id, NamespacedKey table, long seed) { }

    private record MemberMetadata(UUID id, NamespacedKey table, long seed,
                                  BlockPosition owner, int index) { }
}
