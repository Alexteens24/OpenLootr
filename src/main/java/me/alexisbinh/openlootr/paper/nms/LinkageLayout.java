package me.alexisbinh.openlootr.paper.nms;

/** Symbol layout for one or more runtime versions. All Minecraft names stay in this island. */
record LinkageLayout(
        String id,
        String craftPlayerClass,
        String serverPlayerClass,
        String serverLevelClass,
        String minecraftPlayerClass,
        String identifierClass,
        String resourceKeyClass,
        String registriesClass,
        String criteriaTriggersClass,
        String lootTableTriggerClass,
        String piglinAiClass,
        String craftPlayerHandleMethod,
        String serverPlayerLevelMethod,
        String identifierFactoryMethod,
        String resourceKeyFactoryMethod,
        String lootTableRegistryField,
        String generateLootTriggerField,
        String lootTableTriggerMethod,
        String angerNearbyPiglinsMethod
) {
    static final LinkageLayout SHARED_1_21_11_TO_26_1_2 = new LinkageLayout(
            "mojang-1.21.11-to-26.1.2",
            "org.bukkit.craftbukkit.entity.CraftPlayer",
            "net.minecraft.server.level.ServerPlayer",
            "net.minecraft.server.level.ServerLevel",
            "net.minecraft.world.entity.player.Player",
            "net.minecraft.resources.Identifier",
            "net.minecraft.resources.ResourceKey",
            "net.minecraft.core.registries.Registries",
            "net.minecraft.advancements.CriteriaTriggers",
            "net.minecraft.advancements.criterion.LootTableTrigger",
            "net.minecraft.world.entity.monster.piglin.PiglinAi",
            "getHandle",
            "level",
            "fromNamespaceAndPath",
            "create",
            "LOOT_TABLE",
            "GENERATE_LOOT",
            "trigger",
            "angerNearbyPiglins"
    );

    static final LinkageLayout V26_2 = new LinkageLayout(
            "mojang-26.2",
            "org.bukkit.craftbukkit.entity.CraftPlayer",
            "net.minecraft.server.level.ServerPlayer",
            "net.minecraft.server.level.ServerLevel",
            "net.minecraft.world.entity.player.Player",
            "net.minecraft.resources.Identifier",
            "net.minecraft.resources.ResourceKey",
            "net.minecraft.core.registries.Registries",
            "net.minecraft.advancements.triggers.CriteriaTriggers",
            "net.minecraft.advancements.triggers.LootTableTrigger",
            "net.minecraft.world.entity.monster.piglin.PiglinAi",
            "getHandle",
            "level",
            "fromNamespaceAndPath",
            "create",
            "LOOT_TABLE",
            "GENERATE_LOOT",
            "trigger",
            "angerNearbyPiglins"
    );
}
