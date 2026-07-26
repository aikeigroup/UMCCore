package net.aikeigroup.umccore.ui.bedrock;

import net.aikeigroup.umccore.UMCCore;
import net.aikeigroup.umccore.ui.model.MenuBody;
import net.aikeigroup.umccore.ui.model.MenuButton;
import net.aikeigroup.umccore.ui.model.MenuDefinition;
import net.aikeigroup.umccore.ui.model.MenuInput;
import net.aikeigroup.umccore.util.Text;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.component.ButtonComponent;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders menus as native <b>Bedrock forms</b> using Cumulus through the
 * Floodgate API — the dedicated Bedrock path.
 *
 * <p>Earlier UMCCore relied on Geyser auto-translating the Java Dialog API into a
 * Bedrock form, which looked cramped and inconsistent. This renderer instead
 * builds a real Bedrock form so touch players get the layout their client is
 * designed for:</p>
 * <ul>
 *   <li><b>MENU</b> → {@link SimpleForm}: body text on top, one big tappable
 *       button per entry (with an optional picture), plus auto paging.</li>
 *   <li><b>NOTICE</b> → {@link SimpleForm} with a single button.</li>
 *   <li><b>CONFIRM</b> → {@link ModalForm}: a yes/no pair.</li>
 *   <li>menus with <b>inputs</b> → {@link CustomForm}: labels + fields, and the
 *       first button's actions run on submit (with {@code {input_*}} filled).</li>
 * </ul>
 *
 * <p>All text is converted from MiniMessage to {@code §}-coded legacy so colours
 * and gradients stay visible on the form's dark background.</p>
 */
public final class BedrockFormRenderer {

    private final UMCCore plugin;

    public BedrockFormRenderer(UMCCore plugin) {
        this.plugin = plugin;
    }

    /** Builds and shows a page of a menu as a Bedrock form. */
    public void open(Player player, MenuDefinition menu, int page) {
        List<MenuButton> visible = new ArrayList<>();
        for (MenuButton b : menu.buttonsFor(page)) {
            if (b.visibleTo(player)) {
                visible.add(b);
            }
        }

        boolean hasInputs = menu.inputs() != null && !menu.inputs().isEmpty();
        if (hasInputs) {
            openCustom(player, menu, page, visible);
            return;
        }
        switch (menu.kind()) {
            case CONFIRM -> openModal(player, menu, page, visible);
            case NOTICE -> openSimple(player, menu, page, visible, true);
            default -> openSimple(player, menu, page, visible, false);
        }
    }

    // --- SimpleForm (MENU / NOTICE) ---------------------------------------

    private void openSimple(Player player, MenuDefinition menu, int page,
                            List<MenuButton> visible, boolean notice) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title(legacy(player, menu.titleFor(page)))
                .content(buildContent(player, menu.bodyFor(page)));

        // Each Bedrock button maps by index to an action runnable.
        List<Runnable> handlers = new ArrayList<>();

        if (notice && visible.isEmpty()) {
            form.button("§aOK");
            handlers.add(() -> closeAndBack(player));
        } else {
            for (MenuButton b : visible) {
                addButton(form, player, b);
                handlers.add(() -> plugin.actionExecutor().run(player, b, menu, page, Map.of()));
            }
            addPagingButtons(form, player, menu, page, handlers);
        }

        form.validResultHandler(response -> {
            int id = response.clickedButtonId();
            if (id >= 0 && id < handlers.size()) {
                runOnMain(handlers.get(id));
            }
        });
        send(player, form);
    }

    /** Appends a single button with its optional picture. */
    private void addButton(SimpleForm.Builder form, Player player, MenuButton b) {
        String text = buttonText(player, b);
        FormImage image = imageFor(b.bedrockImage());
        if (image != null) {
            form.button(text, image);
        } else {
            form.button(text);
        }
    }

    /** Adds Prev/Next tap-buttons for multi-page menus (SimpleForm path). */
    private void addPagingButtons(SimpleForm.Builder form, Player player, MenuDefinition menu,
                                  int page, List<Runnable> handlers) {
        if (menu.pageCount() <= 1) {
            return;
        }
        if (page > 0) {
            form.button("§e« Prev");
            int target = page - 1;
            handlers.add(() -> plugin.menuService().openPage(player, menu.id(), target));
        }
        if (page < menu.pageCount() - 1) {
            form.button("§eNext »");
            int target = page + 1;
            handlers.add(() -> plugin.menuService().openPage(player, menu.id(), target));
        }
    }

    // --- ModalForm (CONFIRM) ----------------------------------------------

    private void openModal(Player player, MenuDefinition menu, int page, List<MenuButton> visible) {
        MenuButton yes = visible.size() > 0 ? visible.get(0) : null;
        MenuButton no = visible.size() > 1 ? visible.get(1) : null;
        String yesText = yes != null ? buttonText(player, yes) : "§aYes";
        String noText = no != null ? buttonText(player, no) : "§cNo";

        ModalForm.Builder form = ModalForm.builder()
                .title(legacy(player, menu.titleFor(page)))
                .content(buildContent(player, menu.bodyFor(page)))
                .button1(yesText)
                .button2(noText);

        form.validResultHandler(response -> runOnMain(() -> {
            if (response.clickedFirst()) {
                if (yes != null) plugin.actionExecutor().run(player, yes, menu, page, Map.of());
                else closeAndBack(player);
            } else {
                if (no != null) plugin.actionExecutor().run(player, no, menu, page, Map.of());
                else closeAndBack(player);
            }
        }));
        send(player, form);
    }

    // --- CustomForm (inputs) ----------------------------------------------

    private void openCustom(Player player, MenuDefinition menu, int page, List<MenuButton> visible) {
        CustomForm.Builder form = CustomForm.builder()
                .title(legacy(player, menu.titleFor(page)));

        // Body text becomes label components so guides keep their intro copy.
        String content = buildContent(player, menu.bodyFor(page));
        if (!content.isBlank()) {
            form.label(content);
        }

        // Inputs, in declared order. We remember the key order to read them back.
        List<MenuInput> inputs = menu.inputs();
        for (MenuInput in : inputs) {
            String label = legacy(player, in.label());
            switch (in.kind()) {
                case BOOLEAN -> form.toggle(label, Boolean.parseBoolean(in.initial()));
                case NUMBER -> {
                    float step = in.step() > 0 ? in.step() : 1f;
                    float initial = clamp(parseFloat(in.initial(), in.min()), in.min(), in.max());
                    form.slider(label, in.min(), in.max(), step, initial);
                }
                case SINGLE_OPTION -> {
                    List<String> opts = new ArrayList<>();
                    int def = 0;
                    for (int i = 0; i < in.options().size(); i++) {
                        MenuInput.Option o = in.options().get(i);
                        opts.add(legacy(player, o.label()));
                        if (o.id().equals(in.initial())) def = i;
                    }
                    form.dropdown(label, opts, def);
                }
                default -> form.input(label, "", in.initial() == null ? "" : in.initial());
            }
        }

        boolean hasLabel = !content.isBlank();
        MenuButton submit = visible.isEmpty() ? null : visible.get(0);

        form.validResultHandler(response -> {
            Map<String, String> values = readCustom(response, inputs, hasLabel);
            runOnMain(() -> {
                if (submit != null) {
                    plugin.actionExecutor().run(player, submit, menu, page, values);
                } else {
                    closeAndBack(player);
                }
            });
        });
        send(player, form);
    }

    /** Reads a CustomForm's responses back into {@code key → value} strings. */
    private Map<String, String> readCustom(org.geysermc.cumulus.response.CustomFormResponse response,
                                           List<MenuInput> inputs, boolean hasLabel) {
        Map<String, String> values = new LinkedHashMap<>();
        // Component index: an optional leading label occupies index 0.
        int index = hasLabel ? 1 : 0;
        for (MenuInput in : inputs) {
            String v;
            try {
                v = switch (in.kind()) {
                    case BOOLEAN -> String.valueOf(response.asToggle(index));
                    case NUMBER -> trimFloat(response.asSlider(index));
                    case SINGLE_OPTION -> {
                        int sel = response.asDropdown(index);
                        yield sel >= 0 && sel < in.options().size() ? in.options().get(sel).id() : "";
                    }
                    default -> {
                        String s = response.asInput(index);
                        yield s == null ? "" : s;
                    }
                };
            } catch (Exception e) {
                v = "";
            }
            values.put(in.key(), v);
            index++;
        }
        return values;
    }

    // --- Shared helpers ----------------------------------------------------

    /** Joins body elements into a single legacy content string (icons → bullets). */
    private String buildContent(Player player, List<MenuBody> elements) {
        if (elements == null || elements.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MenuBody el : elements) {
            if (el.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            // Item rows have no inline picture on Bedrock, so prefix a bullet to
            // keep the "list" feel; text rows pass through as paragraphs.
            if (el.kind() == MenuBody.Kind.ITEM) {
                sb.append("§7• §r").append(legacy(player, el.text()));
            } else {
                sb.append(legacy(player, el.text()));
            }
        }
        return sb.toString();
    }

    /**
     * Button caption: the label plus <em>every</em> description line as subtitle
     * rows. A Bedrock SimpleForm button renders multi-line text, so the full
     * tooltip that Java shows on hover is shown inline here — nothing is dropped.
     */
    private String buttonText(Player player, MenuButton b) {
        StringBuilder text = new StringBuilder(legacy(player, b.label()));
        if (b.description() != null) {
            for (String line : b.description()) {
                if (line == null || line.isBlank()) {
                    text.append("\n");           // preserve intentional blank rows
                } else {
                    text.append("\n").append(legacy(player, line));
                }
            }
        }
        return text.toString();
    }

    private FormImage imageFor(String[] img) {
        if (img == null) {
            return null;
        }
        FormImage.Type type = "path".equals(img[0]) ? FormImage.Type.PATH : FormImage.Type.URL;
        return FormImage.of(type, img[1]);
    }

    private String legacy(Player player, String miniMessage) {
        return Text.legacy(plugin.text().render(player, miniMessage == null ? "" : miniMessage));
    }

    private void closeAndBack(Player player) {
        // On Bedrock a "close"/OK simply ends the form; nothing else to do.
    }

    private void send(Player player, org.geysermc.cumulus.form.util.FormBuilder<?, ?, ?> form) {
        try {
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to send Bedrock form for menu to "
                    + player.getName() + ": " + t.getMessage());
        }
    }

    /** Runs a menu action on the main thread (Cumulus callbacks are off-thread). */
    private void runOnMain(Runnable r) {
        plugin.getServer().getScheduler().runTask(plugin, r);
    }

    private float parseFloat(String s, float def) {
        try {
            return s == null || s.isBlank() ? def : Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private String trimFloat(float f) {
        if (f == Math.rint(f)) {
            return String.valueOf((long) f);
        }
        return String.valueOf(f);
    }
}
