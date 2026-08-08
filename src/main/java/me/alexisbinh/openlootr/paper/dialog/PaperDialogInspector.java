package me.alexisbinh.openlootr.paper.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

/** Experimental Dialog API is isolated here; text remains a runtime fallback. */
@SuppressWarnings("UnstableApiUsage")
public final class PaperDialogInspector implements InspectorPresenter {
    private final Logger logger;

    public PaperDialogInspector(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void show(Player player, Component title, List<Component> lines) {
        try {
            Dialog dialog = Dialog.create(factory -> factory.empty()
                    .base(DialogBase.builder(title)
                            .pause(false)
                            .body(lines.stream().map(DialogBody::plainMessage).toList())
                            .build())
                    .type(DialogType.notice()));
            player.showDialog(dialog);
        } catch (RuntimeException | LinkageError unavailable) {
            logger.warn("Dialog inspector unavailable; falling back to chat", unavailable);
            player.sendMessage(title);
            lines.forEach(player::sendMessage);
        }
    }
}
