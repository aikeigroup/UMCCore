package net.aikeigroup.umccore.ui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.model.MenuButton;
import net.aikeigroup.umccore.ui.model.MenuDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders menus using Paper's native Dialog API (Minecraft 26.2).
 *
 * <p>A Dialog renders as a real client-side screen on Java <em>and</em> is
 * translated by Geyser into a native Bedrock form — this is what makes UMCCore
 * menus friendly on Bedrock without a separate form implementation.</p>
 *
 * <p>Each visible {@link MenuButton} becomes a multi-action {@link ActionButton}
 * whose {@code customClick} callback runs the button's actions on the main
 * thread. Buttons the player lacks permission for are omitted entirely.</p>
 */
public final class DialogMenuRenderer {

    private final UMCCore plugin;

    public DialogMenuRenderer(UMCCore plugin) {
        this.plugin = plugin;
    }

    /** Builds and shows the dialog to a player. */
    public void open(Player player, MenuDefinition menu) {
        Component title = plugin.text().render(player, menu.title());

        List<ActionButton> buttons = new ArrayList<>();
        for (MenuButton button : menu.buttons()) {
            if (!button.visibleTo(player)) {
                continue;
            }
            buttons.add(toActionButton(player, button));
        }

        // Optional descriptive body lines from any button descriptions are not
        // shown here; the menu body stays clean. Bodies can be added per-menu
        // later via a dedicated config key.
        List<DialogBody> body = List.of();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(columnsFor(buttons.size()))
                        .build()));

        player.showDialog(dialog);
    }

    private ActionButton toActionButton(Player player, MenuButton button) {
        Component label = plugin.text().render(player, button.label());
        ActionButton.Builder ab = ActionButton.builder(label);

        if (button.description() != null && !button.description().isEmpty()) {
            // Join description lines into a single tooltip component.
            Component tooltip = Component.empty();
            for (int i = 0; i < button.description().size(); i++) {
                if (i > 0) tooltip = tooltip.appendNewline();
                tooltip = tooltip.append(plugin.text().render(player, button.description().get(i)));
            }
            ab.tooltip(tooltip);
        }

        // customClick fires our callback; we ignore the response view (no inputs)
        // and run the button's actions on the main thread.
        DialogAction action = DialogAction.customClick(
                (view, audience) -> plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.actionExecutor().run(player, button)),
                ClickCallback.Options.builder()
                        .uses(ClickCallback.UNLIMITED_USES)
                        .build());

        ab.action(action);
        return ab.build();
    }

    /** Chooses a reasonable column count so buttons lay out nicely. */
    private int columnsFor(int count) {
        if (count <= 3) return 1;
        if (count <= 8) return 2;
        return 3;
    }
}
