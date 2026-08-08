package me.alexisbinh.openlootr.paper.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
public final class PaperMenuFactory implements MenuFactory {
    private final Plugin plugin;

    public PaperMenuFactory(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public InventoryView open(Player player, int size, ItemStack[] contents, Component title) {
        try {
            InventoryView view = menuType(size).create(player, title);
            view.getTopInventory().setContents(contents);
            view.open();
            return view;
        } catch (RuntimeException experimentalFailure) {
            plugin.getSLF4JLogger().warn("Paper Menu Type API failed; using stable inventory fallback",
                    experimentalFailure);
            FallbackHolder holder = new FallbackHolder();
            Inventory inventory = Bukkit.createInventory(holder, size, title);
            holder.inventory = inventory;
            inventory.setContents(contents);
            return player.openInventory(inventory);
        }
    }

    private static MenuType menuType(int size) {
        return switch (size) {
            case 9 -> MenuType.GENERIC_9X1;
            case 18 -> MenuType.GENERIC_9X2;
            case 27 -> MenuType.GENERIC_9X3;
            case 36 -> MenuType.GENERIC_9X4;
            case 45 -> MenuType.GENERIC_9X5;
            case 54 -> MenuType.GENERIC_9X6;
            default -> throw new IllegalArgumentException("unsupported menu size: " + size);
        };
    }

    private static final class FallbackHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() { return inventory; }
    }
}
