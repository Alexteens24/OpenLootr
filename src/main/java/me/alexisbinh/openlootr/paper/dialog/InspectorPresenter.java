package me.alexisbinh.openlootr.paper.dialog;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

public interface InspectorPresenter {
    void show(Player player, Component title, List<Component> lines);
}
