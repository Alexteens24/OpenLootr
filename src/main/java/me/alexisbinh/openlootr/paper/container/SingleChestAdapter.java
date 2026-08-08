package me.alexisbinh.openlootr.paper.container;

import me.alexisbinh.openlootr.container.AutomationPolicy;
import me.alexisbinh.openlootr.container.ContainerAdapter;
import me.alexisbinh.openlootr.container.ContainerKind;
import me.alexisbinh.openlootr.container.IdentityStrategy;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.Material;

public final class SingleChestAdapter implements ContainerAdapter<Block> {
    @Override
    public boolean supports(Block candidate) {
        if (candidate.getType() != Material.CHEST || !(candidate.getState() instanceof Chest chest)) {
            return false;
        }
        return chest.getBlockInventory().getSize() == chest.getInventory().getSize();
    }

    @Override public ContainerKind kind(Block candidate) { return ContainerKind.CHEST; }
    @Override public int logicalSize(Block candidate) { return ((Chest) candidate.getState()).getInventory().getSize(); }
    @Override public IdentityStrategy identityStrategy() { return IdentityStrategy.TILE_PDC; }
    @Override public boolean canOpenAsPersonalMenu() { return true; }
    @Override public AutomationPolicy automationPolicy() { return AutomationPolicy.BLOCK; }
}
