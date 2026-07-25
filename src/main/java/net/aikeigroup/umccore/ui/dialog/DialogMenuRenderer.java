package net.aikeigroup.umccore.ui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.BooleanDialogInput;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.model.MenuBody;
import net.aikeigroup.umccore.ui.model.MenuButton;
import net.aikeigroup.umccore.ui.model.MenuDefinition;
import net.aikeigroup.umccore.ui.model.MenuInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders menus using Paper's native Dialog API (Minecraft 26.2).
 *
 * <p>A Dialog renders as a real client-side screen on Java <em>and</em> is
 * translated by Geyser into a native Bedrock form — this is what makes UMCCore
 * menus friendly on Bedrock without a separate form implementation.</p>
 *
 * <p>This renderer supports the full flexible menu model: rich body text and
 * icon "image" rows (for guides / tutorials), interactive inputs (whose values
 * flow to actions as {@code {input_<key>}}), multi-page navigation, and the
 * three dialog shapes MENU / NOTICE / CONFIRM.</p>
 */
public final class DialogMenuRenderer {

    private final UMCCore plugin;

    public DialogMenuRenderer(UMCCore plugin) {
        this.plugin = plugin;
    }

    /** Builds and shows a page of a menu to a player. */
    public void open(Player player, MenuDefinition menu, int page) {
        Component title = plugin.text().render(player, menu.titleFor(page));

        List<DialogBody> body = buildBody(player, menu.bodyFor(page));
        List<DialogInput> inputs = buildInputs(player, menu.inputs());

        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .body(body)
                .inputs(inputs)
                .build();

        List<MenuButton> visible = new ArrayList<>();
        for (MenuButton b : menu.buttonsFor(page)) {
            if (b.visibleTo(player)) {
                visible.add(b);
            }
        }

        DialogType type = switch (menu.kind()) {
            case NOTICE -> DialogType.notice(
                    visible.isEmpty() ? defaultOk(player) : toActionButton(player, menu, page, visible.get(0)));
            case CONFIRM -> DialogType.confirmation(
                    toActionButton(player, menu, page, buttonOr(visible, 0, "<green>Yes</green>")),
                    toActionButton(player, menu, page, buttonOr(visible, 1, "<red>No</red>")));
            case MENU -> {
                List<ActionButton> abs = new ArrayList<>();
                for (MenuButton b : visible) {
                    abs.add(toActionButton(player, menu, page, b));
                }
                addPagingButtons(player, menu, page, abs);
                yield DialogType.multiAction(abs).columns(columnsFor(abs.size())).build();
            }
        };

        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }

    // --- Body --------------------------------------------------------------

    private List<DialogBody> buildBody(Player player, List<MenuBody> elements) {
        if (elements == null || elements.isEmpty()) {
            return List.of();
        }
        List<DialogBody> out = new ArrayList<>();
        for (MenuBody el : elements) {
            if (el.isEmpty()) {
                continue;
            }
            Component text = plugin.text().render(player, el.text() == null ? "" : el.text());
            if (el.kind() == MenuBody.Kind.ITEM) {
                ItemStack icon = plugin.icons().build(el.iconOr("PAPER"), el.headTexture(), el.customModelData());
                var itemBody = DialogBody.item(icon)
                        .description(DialogBody.plainMessage(text))
                        .showDecorations(false)
                        .showTooltip(false);
                out.add(itemBody.build());
            } else if (el.width() > 0) {
                out.add(DialogBody.plainMessage(text, el.width()));
            } else {
                out.add(DialogBody.plainMessage(text));
            }
        }
        return out;
    }

    // --- Inputs ------------------------------------------------------------

    private List<DialogInput> buildInputs(Player player, List<MenuInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<DialogInput> out = new ArrayList<>();
        for (MenuInput in : inputs) {
            Component label = plugin.text().render(player, in.label());
            switch (in.kind()) {
                case BOOLEAN -> out.add(DialogInput.bool(in.key(), label)
                        .initial(Boolean.parseBoolean(in.initial()))
                        .build());
                case NUMBER -> {
                    NumberRangeDialogInput.Builder nb =
                            DialogInput.numberRange(in.key(), label, in.min(), in.max());
                    if (in.step() > 0) {
                        nb.step(in.step());
                    }
                    out.add(nb.build());
                }
                case SINGLE_OPTION -> {
                    List<SingleOptionDialogInput.OptionEntry> opts = new ArrayList<>();
                    for (MenuInput.Option o : in.options()) {
                        opts.add(SingleOptionDialogInput.OptionEntry.create(
                                o.id(), plugin.text().render(player, o.label()),
                                o.id().equals(in.initial())));
                    }
                    out.add(DialogInput.singleOption(in.key(), label, opts).build());
                }
                default -> {
                    TextDialogInput.Builder tb = DialogInput.text(in.key(), label);
                    if (in.initial() != null && !in.initial().isBlank()) tb.initial(in.initial());
                    if (in.maxLength() > 0) tb.maxLength(in.maxLength());
                    if (in.width() > 0) tb.width(in.width());
                    if (in.multiline()) {
                        tb.multiline(TextDialogInput.MultilineOptions.create(6, 300));
                    }
                    out.add(tb.build());
                }
            }
        }
        return out;
    }

    /** Reads all of a menu's input values from the response view. */
    private Map<String, String> readInputs(MenuDefinition menu, DialogResponseView view) {
        Map<String, String> values = new LinkedHashMap<>();
        if (menu.inputs() == null) {
            return values;
        }
        for (MenuInput in : menu.inputs()) {
            String v = switch (in.kind()) {
                case BOOLEAN -> {
                    Boolean b = view.getBoolean(in.key());
                    yield b == null ? "" : String.valueOf(b);
                }
                case NUMBER -> {
                    Float f = view.getFloat(in.key());
                    yield f == null ? "" : trimFloat(f);
                }
                default -> {
                    String t = view.getText(in.key());
                    yield t == null ? "" : t;
                }
            };
            values.put(in.key(), v);
        }
        return values;
    }

    // --- Buttons -----------------------------------------------------------

    private ActionButton toActionButton(Player player, MenuDefinition menu, int page, MenuButton button) {
        Component label = plugin.text().render(player, button.label());
        ActionButton.Builder ab = ActionButton.builder(label);
        if (button.width() > 0) {
            ab.width(button.width());
        }

        if (button.description() != null && !button.description().isEmpty()) {
            Component tooltip = Component.empty();
            for (int i = 0; i < button.description().size(); i++) {
                if (i > 0) tooltip = tooltip.appendNewline();
                tooltip = tooltip.append(plugin.text().render(player, button.description().get(i)));
            }
            ab.tooltip(tooltip);
        }

        DialogAction action = DialogAction.customClick(
                (view, audience) -> {
                    Map<String, String> inputs = readInputs(menu, view);
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> plugin.actionExecutor().run(player, button, menu, page, inputs));
                },
                ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build());
        ab.action(action);
        return ab.build();
    }

    /** Adds Prev/Next navigation buttons for multi-page menus. */
    private void addPagingButtons(Player player, MenuDefinition menu, int page, List<ActionButton> out) {
        if (menu.pageCount() <= 1) {
            return;
        }
        if (page > 0) {
            out.add(navButton(player, menu, page, "<yellow>« Prev</yellow>", page - 1));
        }
        if (page < menu.pageCount() - 1) {
            out.add(navButton(player, menu, page, "<yellow>Next »</yellow>", page + 1));
        }
    }

    private ActionButton navButton(Player player, MenuDefinition menu, int page, String label, int target) {
        Component c = plugin.text().render(player, label);
        DialogAction action = DialogAction.customClick(
                (view, audience) -> plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.menuService().openPage(player, menu.id(), target)),
                ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build());
        return ActionButton.builder(c).action(action).build();
    }

    private ActionButton defaultOk(Player player) {
        Component c = plugin.text().render(player, "<green>OK</green>");
        DialogAction action = DialogAction.customClick(
                (view, audience) -> plugin.getServer().getScheduler().runTask(plugin, player::closeDialog),
                ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build());
        return ActionButton.builder(c).action(action).build();
    }

    private MenuButton buttonOr(List<MenuButton> list, int index, String fallbackLabel) {
        if (index < list.size()) {
            return list.get(index);
        }
        return new MenuButton("btn" + index, fallbackLabel, List.of(), "STONE", null, -1, -1, -1, "",
                List.of(net.aikeigroup.umccore.ui.model.MenuAction.parse("CLOSE")));
    }

    private int columnsFor(int count) {
        if (count <= 3) return 1;
        if (count <= 8) return 2;
        return 3;
    }

    private String trimFloat(float f) {
        if (f == Math.rint(f)) {
            return String.valueOf((long) f);
        }
        return String.valueOf(f);
    }
}
