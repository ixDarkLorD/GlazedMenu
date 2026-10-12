package net.ixdarklord.glazedmenu.internal.compat;

import net.minecraft.network.chat.Component;

// What the title screen needs of the compat layer without being one of its screens (it extends the game's own).
public final class TitleCompat {
    // A screen of the compat layer, for its panorama: the one the other screens turn, so it doesn't jump between them.
    private static final CompatScreen PANORAMA = new CompatScreen(Component.empty()) {};

    private TitleCompat() {}

    /** The title screen's turning panorama, as the compat layer's screens draw it. */
    public static void panorama(GuiGraphicsExtractor graphics, float a) {
        PANORAMA.extractPanorama(graphics, a);
    }
}
