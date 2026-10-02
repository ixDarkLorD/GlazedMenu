package net.ixdarklord.glazedmenu.internal.gui.style;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;

/**
 * Each mod's color in Glazed Menu's lists (the mod list, the list of every mod's configs): its theme's accent if it set one,
 * else its icon's color, else the current theme's accent; and boxes tinted with it.
 */
public final class ModColors {
    // The game's color: its grass.
    private static final int MINECRAFT = 0xFF7DBD42;

    private ModColors() {}

    /** The mod's color: its theme's if it set one, else its icon's (grass green for the game), else the theme's accent. */
    public static int accent(String modId) {
        if (modId.equals("minecraft")) return MINECRAFT;
        ConfigTheme theme = ConfigTheme.forMod(modId);
        if (theme != ConfigTheme.DEFAULT) return ConfigStyle.accentOf(theme);
        return ModIcons.accent(modId).orElse(ConfigStyle.accent());
    }

    /** Whether the mod has a color of its own (its theme's, its icon's, or the game's); a grey icon has none. */
    public static boolean hasOwn(String modId) {
        return modId.equals("minecraft") || ConfigTheme.forMod(modId) != ConfigTheme.DEFAULT || ModIcons.accent(modId).isPresent();
    }

    /**
     * A box tinted with the mod's color: a light tint at rest, deeper under the mouse, deepest when chosen, with a wash
     * of the color from the top ({@code washHeight} tall); a mod without a color of its own stays neutral until chosen.
     */
    public static void box(GuiGraphicsExtractor graphics, String modId, int accent, int x, int y, int width, int height, int washHeight,
                           boolean hovered, boolean chosen) {
        box(graphics, modId, accent, x, y, width, height, washHeight, hovered, chosen, true);
    }

    /**
     * A neutral box with the mod's color as a wash fading in from one side: from the left ({@code fromLeft}) or from the
     * bottom. Alike at rest; the wash shows under the mouse, and stronger when chosen. The frame itself stays neutral.
     */
    public static void washBox(GuiGraphicsExtractor graphics, int accent, int x, int y, int width, int height, boolean hovered, boolean chosen,
                               boolean fromLeft) {
        // Darker than the panel behind it, so the entries stand apart from the list's frame (only a touch on the light scheme).
        float darken = ConfigStyle.mode() == net.ixdarklord.glazedmenu.api.config.ConfigTheme.Mode.LIGHT ? 0.06F : 0.32F;
        int button = ConfigStyle.mix(ConfigStyle.colors().button(), 0xFF000000, darken);
        int fill = chosen || hovered ? ConfigStyle.mix(ConfigStyle.colors().buttonHover(), 0xFF000000, darken * 0.6F) : ConfigStyle.withAlpha(button, 0xD8);
        ConfigStyle.rect(graphics, x, y, width, height, fill);
        if (chosen || hovered) {
            int strong = ConfigStyle.withAlpha(accent, chosen ? 0x5C : 0x34);
            int clear = ConfigStyle.withAlpha(accent, 0);
            if (fromLeft) {
                // Turned a quarter: the gradient's top-to-bottom runs left to right, over most of the width.
                int length = width * 7 / 10;
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(x + 1, y + height - 1);
                pose.rotate((float) -Math.PI / 2);
                graphics.fillGradient(0, 0, height - 2, length, strong, clear);
                pose.popMatrix();
            } else {
                graphics.fillGradient(x + 1, y + height * 3 / 10, x + width - 1, y + height - 1, clear, strong);
            }
        }
        int border = chosen ? ConfigStyle.mix(ConfigStyle.colors().panelBorder(), 0xFFFFFFFF, 0.25F)
                : hovered ? ConfigStyle.mix(ConfigStyle.colors().panelBorder(), 0xFFFFFFFF, 0.12F) : ConfigStyle.colors().panelBorder();
        ConfigStyle.outline(graphics, x, y, width, height, border);
    }

    /** As the other; {@code tintAtRest} false leaves every box alike until it's hovered or chosen. */
    public static void box(GuiGraphicsExtractor graphics, String modId, int accent, int x, int y, int width, int height, int washHeight,
                           boolean hovered, boolean chosen, boolean tintAtRest) {
        boolean own = hasOwn(modId) && (tintAtRest || hovered || chosen);
        int button = ConfigStyle.colors().button();
        int fill;
        if (chosen) fill = ConfigStyle.mix(button, accent, 0.22F);
        else if (hovered) fill = own ? ConfigStyle.mix(ConfigStyle.colors().buttonHover(), accent, 0.15F) : ConfigStyle.colors().buttonHover();
        else fill = ConfigStyle.withAlpha(own ? ConfigStyle.mix(button, accent, 0.08F) : button, 0xB0);
        ConfigStyle.rect(graphics, x, y, width, height, fill);
        if (own || chosen) {
            int wash = chosen ? 0x34 : hovered ? 0x24 : 0x14;
            graphics.fillGradient(x + 1, y + 1, x + width - 1, y + washHeight, ConfigStyle.withAlpha(accent, wash), ConfigStyle.withAlpha(accent, 0));
        }
        int border = chosen ? ConfigStyle.mix(ConfigStyle.colors().panelBorder(), accent, 0.8F) : hovered ? ConfigStyle.withAlpha(accent, 0x90)
                : own ? ConfigStyle.mix(ConfigStyle.colors().panelBorder(), accent, 0.28F) : ConfigStyle.colors().panelBorder();
        ConfigStyle.outline(graphics, x, y, width, height, border);
    }
}
