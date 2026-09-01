package net.aikeigroup.umccore.ui.model;

import java.util.List;

/**
 * A single body element of a menu — the content shown above the buttons.
 *
 * <p>This is what makes UMCCore menus usable as guides / tutorials / help pages:
 * long paragraphs of text plus optional "image" rows built from item icons or
 * textured player heads (Minecraft has no arbitrary-image support in dialogs).</p>
 *
 * @param kind        TEXT (a paragraph) or ITEM (an icon + caption row)
 * @param text        MiniMessage text (the paragraph, or the caption for ITEM)
 * @param width       preferred pixel width for wrapping (<=0 = client default)
 * @param icon        material name for ITEM kind (nullable → falls back to text)
 * @param headTexture player name or base64 skin value for a textured head icon
 *                    (nullable). When set, the icon is a player head — the
 *                    closest thing to an inline image, e.g. an avatar or a
 *                    custom logo head.
 * @param customModelData custom-model-data for the icon (>=0), so a resource
 *                    pack can turn the icon into any image. -1 = none.
 */
public record MenuBody(
        Kind kind,
        String text,
        int width,
        String icon,
        String headTexture,
        int customModelData
) {
    public enum Kind { TEXT, ITEM }

    /** Convenience for a plain paragraph. */
    public static MenuBody text(String text, int width) {
        return new MenuBody(Kind.TEXT, text, width, null, null, -1);
    }

    /** @return the icon material or a sensible default for ITEM bodies. */
    public String iconOr(String fallback) {
        return icon == null || icon.isBlank() ? fallback : icon;
    }

    /** @return true if this element carries any renderable content. */
    public boolean isEmpty() {
        return (text == null || text.isBlank())
                && (icon == null || icon.isBlank())
                && (headTexture == null || headTexture.isBlank());
    }

    /** Empty singleton list helper. */
    public static List<MenuBody> none() {
        return List.of();
    }
}
