package me.alexisbinh.openlootr.paper.nms;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resolves the complete NMS compatibility surface before gameplay is enabled. */
public final class VanillaParityLinker {
    private static final LinkageLayout SHARED = LinkageLayout.SHARED_1_21_11_TO_26_1_2;
    private static final Map<String, LinkageLayout> LAYOUTS = Map.of(
            "1.21.11", SHARED,
            "26.1.2", SHARED,
            "26.2", LinkageLayout.V26_2
    );

    private static void requireCompleteLayoutMap() {
        if (!LAYOUTS.keySet().equals(Set.copyOf(SupportedMinecraftVersions.values()))) {
            throw new IllegalStateException(
                    "Supported Minecraft versions and NMS linkage layouts are out of sync");
        }
    }

    private VanillaParityLinker() { }

    public static VanillaParityBridge link(String runtimeVersion) {
        return link(runtimeVersion, VanillaParityLinker.class.getClassLoader());
    }

    static VanillaParityBridge link(String runtimeVersion, ClassLoader classLoader) {
        requireCompleteLayoutMap();
        SupportedMinecraftVersions.requireSupported(runtimeVersion);
        LinkageLayout layout = Objects.requireNonNull(LAYOUTS.get(runtimeVersion),
                "supported version is missing a linkage layout");
        return link(runtimeVersion, layout, classLoader);
    }

    static VanillaParityBridge link(String runtimeVersion, LinkageLayout layout,
                                    ClassLoader classLoader) {
        Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(classLoader, "classLoader");
        try {
            Class<?> craftPlayer = load(layout.craftPlayerClass(), classLoader);
            Class<?> serverPlayer = load(layout.serverPlayerClass(), classLoader);
            Class<?> serverLevel = load(layout.serverLevelClass(), classLoader);
            Class<?> minecraftPlayer = load(layout.minecraftPlayerClass(), classLoader);
            Class<?> identifier = load(layout.identifierClass(), classLoader);
            Class<?> resourceKey = load(layout.resourceKeyClass(), classLoader);
            Class<?> registries = load(layout.registriesClass(), classLoader);
            Class<?> criteriaTriggers = load(layout.criteriaTriggersClass(), classLoader);
            Class<?> lootTableTrigger = load(layout.lootTableTriggerClass(), classLoader);
            Class<?> piglinAi = load(layout.piglinAiClass(), classLoader);

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle getHandle = lookup.findVirtual(craftPlayer, layout.craftPlayerHandleMethod(),
                    MethodType.methodType(serverPlayer));
            MethodHandle getLevel = lookup.findVirtual(serverPlayer, layout.serverPlayerLevelMethod(),
                    MethodType.methodType(serverLevel));
            MethodHandle createIdentifier = lookup.findStatic(identifier, layout.identifierFactoryMethod(),
                    MethodType.methodType(identifier, String.class, String.class));
            MethodHandle createResourceKey = lookup.findStatic(resourceKey, layout.resourceKeyFactoryMethod(),
                    MethodType.methodType(resourceKey, resourceKey, identifier));
            MethodHandle triggerGeneratedLoot = lookup.findVirtual(lootTableTrigger,
                    layout.lootTableTriggerMethod(),
                    MethodType.methodType(void.class, serverPlayer, resourceKey));
            MethodHandle angerNearbyPiglins = lookup.findStatic(piglinAi,
                    layout.angerNearbyPiglinsMethod(),
                    MethodType.methodType(void.class, serverLevel, minecraftPlayer, boolean.class));
            Object lootTableRegistry = lookup.findStaticGetter(registries,
                    layout.lootTableRegistryField(), resourceKey).invoke();
            Object generateLootTrigger = lookup.findStaticGetter(criteriaTriggers,
                    layout.generateLootTriggerField(), lootTableTrigger).invoke();

            MethodType objectUnary = MethodType.methodType(Object.class, Object.class);
            return new LinkedVanillaParityBridge(runtimeVersion, layout.id(),
                    getHandle.asType(objectUnary),
                    createIdentifier.asType(MethodType.methodType(
                            Object.class, String.class, String.class)),
                    createResourceKey.asType(MethodType.methodType(
                            Object.class, Object.class, Object.class)),
                    triggerGeneratedLoot.asType(MethodType.methodType(
                            void.class, Object.class, Object.class, Object.class)),
                    getLevel.asType(objectUnary),
                    angerNearbyPiglins.asType(MethodType.methodType(
                            void.class, Object.class, Object.class, boolean.class)),
                    lootTableRegistry, generateLootTrigger);
        } catch (Throwable failure) {
            throw new IllegalStateException("Vanilla parity linkage failed for Minecraft "
                    + runtimeVersion + " using layout " + layout.id(), failure);
        }
    }

    private static Class<?> load(String name, ClassLoader classLoader) throws ClassNotFoundException {
        return Class.forName(name, false, classLoader);
    }
}
