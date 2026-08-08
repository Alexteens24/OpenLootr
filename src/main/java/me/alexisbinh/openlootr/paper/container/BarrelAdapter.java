package me.alexisbinh.openlootr.paper.container;

import me.alexisbinh.openlootr.container.AutomationPolicy;
import me.alexisbinh.openlootr.container.ContainerAdapter;
import me.alexisbinh.openlootr.container.ContainerKind;
import me.alexisbinh.openlootr.container.IdentityStrategy;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;

public final class BarrelAdapter implements ContainerAdapter<Block> {
    @Override public boolean supports(Block candidate) { return candidate.getState() instanceof Barrel; }
    @Override public ContainerKind kind(Block candidate) { return ContainerKind.BARREL; }
    @Override public int logicalSize(Block candidate) { return ((Barrel) candidate.getState()).getInventory().getSize(); }
    @Override public IdentityStrategy identityStrategy() { return IdentityStrategy.TILE_PDC; }
    @Override public boolean canOpenAsPersonalMenu() { return true; }
    @Override public AutomationPolicy automationPolicy() { return AutomationPolicy.BLOCK; }
}
