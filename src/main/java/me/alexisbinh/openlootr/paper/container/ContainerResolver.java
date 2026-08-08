package me.alexisbinh.openlootr.paper.container;

import me.alexisbinh.openlootr.container.ContainerResolution;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import java.util.Optional;

public interface ContainerResolver {
    ContainerResolution resolve(Block block);

    ContainerResolution adopt(Block block, ContainerResolution.Candidate candidate);

    default ContainerResolution resolve(Entity entity) {
        return new ContainerResolution.Ignored("not a supported entity lootable");
    }

    default ContainerResolution adopt(Entity entity, ContainerResolution.Candidate candidate) {
        return new ContainerResolution.Broken("entity identity adoption is unsupported");
    }

    default Optional<ContainerRepairPlan> planRepair(Block block) { return Optional.empty(); }

    default ContainerResolution repair(Block block, ContainerRepairPlan plan) {
        return new ContainerResolution.Broken("repair is unsupported");
    }
}
