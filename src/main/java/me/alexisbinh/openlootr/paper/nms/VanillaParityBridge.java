package me.alexisbinh.openlootr.paper.nms;

import me.alexisbinh.openlootr.container.ResourceKey;
import org.bukkit.entity.Player;

public interface VanillaParityBridge {
    void triggerGeneratedLoot(Player player, ResourceKey lootTable);

    void angerNearbyPiglins(Player player);

    String status();
}
