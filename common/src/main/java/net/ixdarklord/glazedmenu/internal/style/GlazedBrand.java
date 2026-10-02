package net.ixdarklord.glazedmenu.internal.style;

import net.ixdarklord.glazedmenu.api.config.ConfigColorScheme;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Glazed Menu's own colors (its logo's violets and blues, or another scheme the player chose): the theme of its mod list,
 * its settings and every config screen without a theme of its own, and a
 * gradient that slowly flows through them, for the marks that are Glazed Menu's (its title, the chosen filter, the panes'
 * top edges). The flow holds still when the player turns animations off.
 */
public final class GlazedBrand {
    /**
     * The color schemes the player can choose for Glazed Menu's look (its mod list and every config screen without a theme of
     * its own): each a loop of four colors, the first its accent.
     */
    public enum Scheme {
        /** Glazed Menu's logo: violet, its blue, sky blue and lavender. */
        GLAZED(0xFF8B6CF6, 0xFF6C86F8, 0xFF8EC5FF, 0xFFB49CFF),
        /** CoolCatLib's original cyan. */
        CLASSIC(0xFF4FC3F7, 0xFF3A9BE0, 0xFF7FD8FA, 0xFF5FB0F0),
        OCEAN(0xFF2FA7E0, 0xFF3B6FEA, 0xFF4FD6D0, 0xFF7FB2FF),
        FOREST(0xFF4CB86A, 0xFF2E9E7A, 0xFF9AD66A, 0xFF6BC9A0),
        EMBER(0xFFF07A3A, 0xFFE0504A, 0xFFF6B04A, 0xFFF58E6E),
        ROSE(0xFFE86BA8, 0xFFB56BF0, 0xFFF59AC2, 0xFFD68EF5),
        SLATE(0xFF9AA3B5, 0xFF7C8597, 0xFFC4CBD8, 0xFFA8B0C0);

        final int[] stops;
        private final ConfigColorScheme dark;
        private final ConfigColorScheme light;

        Scheme(int... stops) {
            this.stops = stops;
            // Deep, nearly opaque panels with a touch of the scheme's color, so the screens read rich, not washed out
            // over a bright panorama.
            int accent = stops[0];
            this.dark = ConfigColorScheme.tinted(accent).toBuilder()
                    .panel(ConfigStyle.withAlpha(ConfigStyle.mix(0xFF0B0D14, accent, 0.16F), 0xD2))
                    .bar(ConfigStyle.withAlpha(ConfigStyle.mix(0xFF080A10, accent, 0.18F), 0xDC))
                    .popup(ConfigStyle.withAlpha(ConfigStyle.mix(0xFF0B0D14, accent, 0.14F), 0xF7))
                    .panelBorder(ConfigStyle.mix(0xFF2A313F, accent, 0.4F))
                    .build();
            this.light = ConfigColorScheme.tintedLight(stops[0]);
        }

        ConfigColorScheme colors(ConfigTheme.Mode mode) {
            return mode == ConfigTheme.Mode.LIGHT ? this.light : this.dark;
        }
    }

    /** The chosen scheme's accent. */
    public static int accent() {
        return GlazedSettings.colorScheme().stops[0];
    }

    /** Makes the default theme (and so Glazed Menu's) take the chosen scheme's colors; called once on the client. */
    public static void init() {
        ConfigTheme.setDefaultColors(mode -> GlazedSettings.colorScheme().colors(mode));
    }
    // What the gradient is deepened toward on the light scheme: a dark indigo.
    private static final int LIGHT_DEEPEN = 0xFF2B1D6E;
    // How long the gradient takes to flow once round its loop.
    private static final float LOOP_MILLIS = 9000;

    /** Glazed Menu's theme: the default look, in the chosen color scheme. */
    public static final ConfigTheme THEME = ConfigTheme.DEFAULT;

    private GlazedBrand() {}

    /** Where the gradient's flow is now, 0 to 1. */
    public static float phase() {
        if (!GlazedSettings.transitions()) return 0;
        return (System.currentTimeMillis() % (long) LOOP_MILLIS) / LOOP_MILLIS;
    }

    /** The gradient's color at a point of its loop (any number; it wraps), eased between the stops. */
    public static int color(float at) {
        float t = at - (float) Math.floor(at);
        int[] stops = GlazedSettings.colorScheme().stops;
        float scaled = t * stops.length;
        int index = (int) scaled;
        float part = scaled - index;
        part = part * part * (3 - 2 * part);
        int color = ConfigStyle.mix(stops[index % stops.length], stops[(index + 1) % stops.length], part);
        // On the light scheme the pale stops would wash out, so the whole loop runs deeper.
        return ConfigStyle.mode() == ConfigTheme.Mode.LIGHT ? ConfigStyle.mix(color, LIGHT_DEEPEN, 0.35F) : color;
    }

    /** The gradient's color right now, at a point along it (0 to 1 across what it's drawn on). */
    public static int now(float along) {
        return color(phase() + along * SPREAD);
    }

    // How much of the loop shows across one thing drawn with it: a little under half, so it reads as a sweep.
    private static final float SPREAD = 0.45F;

    /** A box filled with the gradient running left to right, flowing. */
    public static void fill(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int alpha) {
        fill(graphics, x, y, width, height, alpha, 0);
    }

    /**
     * As {@link #fill(GuiGraphicsExtractor, int, int, int, int, int)}, its colors deepened toward a dark indigo by
     * {@code deepen} (0 to 1): for white text on it to read at every point of the loop.
     */
    public static void fill(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int alpha, float deepen) {
        float phase = phase();
        // A column at a time; the outer columns stop short of the corners, which Glazed Menu's boxes leave out (ConfigStyle.rect).
        boolean cutCorners = width > 2 && height > 2;
        for (int i = 0; i < width; i++) {
            int color = color(phase + i / (float) Math.max(1, width) * SPREAD);
            if (deepen > 0) color = ConfigStyle.mix(color, LIGHT_DEEPEN, deepen);
            color = ConfigStyle.withAlpha(color, alpha);
            boolean edge = cutCorners && (i == 0 || i == width - 1);
            graphics.fill(x + i, y + (edge ? 1 : 0), x + i + 1, y + height - (edge ? 1 : 0), color);
        }
    }

    /** A gradient color lifted toward white by {@code amount} (0 to 1), to stand out more; unchanged on the light scheme. */
    public static int lift(int color, float amount) {
        return ConfigStyle.mode() == ConfigTheme.Mode.LIGHT ? color : ConfigStyle.mix(color, 0xFFFFFFFF, amount);
    }

    /** Bold text with the gradient running along it, flowing. Returns its width. */
    public static int text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
        return text(graphics, font, text, x, y, 0);
    }

    /** As {@link #text(GuiGraphicsExtractor, Font, String, int, int)}, its colors lifted toward white by {@code lift}. */
    public static int text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, float lift) {
        float phase = phase();
        int total = font.width(Component.literal(text).withStyle(ChatFormatting.BOLD));
        int cursor = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            Component glyph = Component.literal(new String(Character.toChars(codePoint))).withStyle(ChatFormatting.BOLD);
            int color = lift(color(phase + (cursor + font.width(glyph) / 2F) / Math.max(1, total) * SPREAD), lift);
            graphics.text(font, glyph, x + cursor, y, color, false);
            cursor += font.width(glyph);
            i += Character.charCount(codePoint);
        }
        return total;
    }
}
