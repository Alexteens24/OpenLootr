package me.alexisbinh.openlootr.paper.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public interface MenuFactory {
    InventoryView open(Player player, int size, ItemStack[] contents, Component title);
}
