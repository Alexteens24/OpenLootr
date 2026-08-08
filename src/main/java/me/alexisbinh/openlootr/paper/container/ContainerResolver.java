package me.alexisbinh.openlootr.paper.container;

import me.alexisbinh.openlootr.container.ContainerResolution;
import org.bukkit.block.Block;

public interface ContainerResolver {
    ContainerResolution resolve(Block block);

    ContainerResolution adopt(Block block, ContainerResolution.Candidate candidate);
}
