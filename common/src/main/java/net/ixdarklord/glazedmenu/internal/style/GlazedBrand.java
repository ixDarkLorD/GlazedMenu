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
        private ConfigColorScheme dark;
        private final ConfigColorScheme light;

        Scheme(int... stops) {
            this.stops = stops;
            // Deep panels with a good measure of the scheme's color, and see-through enough for the colors behind them
            // to show: rich over a bright panorama, not washed out and not a slab.
            int accent = stops[0];
            this.dark = ConfigColorScheme.tinted(accent).toBuilder()
                    .panel(ConfigStyle.withAlpha(ConfigStyle.mix(0xFF0B0D14, accent, 0.24F), 0x9C))
                    .bar(ConfigStyle.withAlpha(ConfigStyle.mix(0xFF080A10, accent, 0.26F), 0xA8))
                    .popup(ConfigStyle.withAlpha(ConfigStyle.mix(0xFF0B0D14, accent, 0.14F), 0xF7))
                    .panelBorder(ConfigStyle.mix(0xFF2A313F, accent, 0.5F))
                    .build();
            // The lesser text, brighter than a grey: the panels are in color now, and a grey sinks into it. And the
            // groups inside the panels (cards, rows, buttons) a little see-through, so the panel's color and its waves
            // run through them too.
            this.dark = this.dark.toBuilder()
                    .text(0xFFFAF9FF).textDim(0xFFE3E0F7).textMuted(0xFFC8C5E6)
                    .button(ConfigStyle.withAlpha(ConfigStyle.mix(this.dark.button(), 0xFF0A0818, 0.35F), 0xA6))
                    .buttonHover(ConfigStyle.withAlpha(this.dark.buttonHover(), 0xC0))
                    .buttonDisabled(ConfigStyle.withAlpha(this.dark.buttonDisabled(), 0x90))
                    .build();
            ConfigColorScheme light = ConfigColorScheme.tintedLight(stops[0]);
            this.light = light.toBuilder()
                    .panel(ConfigStyle.withAlpha(light.panel(), 0xA4))
                    .bar(ConfigStyle.withAlpha(light.bar(), 0xB0))
                    .text(0xFF0E0A24).textDim(0xFF2C2750).textMuted(0xFF464170)
                    .button(ConfigStyle.withAlpha(light.button(), 0xB4))
                    .buttonHover(ConfigStyle.withAlpha(light.buttonHover(), 0xCC))
                    .buttonDisabled(ConfigStyle.withAlpha(light.buttonDisabled(), 0xA0))
                    .build();
        }

        ConfigColorScheme colors(ConfigTheme.Mode mode) {
            return mode == ConfigTheme.Mode.LIGHT ? this.light : this.dark;
        }
    }

    /**
     * A color that tells one thing from another (a category, a card, a kind of screen), turned toward the chosen color
     * scheme: its hue is drawn most of the way to the scheme's accent, so the things stay apart from one another but in
     * the scheme's family, and it's as vivid as the scheme is. Glazed Menu's own scheme keeps them as they were made;
     * grays stay gray. Colors that mean something (danger, success, a favorite) aren't passed through here.
     */
    public static int identity(int color) {
        Scheme scheme = GlazedSettings.colorScheme();
        if (scheme == Scheme.GLAZED) return color;
        float[] own = hsv(color);
        if (own[1] < 0.2F) return color;
        float[] accent = hsv(scheme.stops[0]);
        float apart = own[0] - accent[0];
        if (apart > 0.5F) apart -= 1;
        if (apart < -0.5F) apart += 1;
        float hue = accent[0] + apart * 0.22F;
        hue -= (float) Math.floor(hue);
        float vivid = own[1] * Math.clamp(accent[1] / 0.5F, 0.25F, 1);
        return color & 0xFF000000 | net.minecraft.util.Mth.hsvToRgb(hue, vivid, own[2]) & 0xFFFFFF;
    }

    // A color's hue, how vivid it is and how bright, each 0 to 1.
    private static float[] hsv(int color) {
        float red = (color >> 16 & 0xFF) / 255F;
        float green = (color >> 8 & 0xFF) / 255F;
        float blue = (color & 0xFF) / 255F;
        float most = Math.max(red, Math.max(green, blue));
        float spread = most - Math.min(red, Math.min(green, blue));
        float hue = 0;
        if (spread > 0) {
            if (most == red) hue = (green - blue) / spread % 6;
            else if (most == green) hue = (blue - red) / spread + 2;
            else hue = (red - green) / spread + 4;
            hue /= 6;
            if (hue < 0) hue += 1;
        }
        return new float[]{hue, most == 0 ? 0 : spread / most, most};
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

    /**
     * A gradient color made to stand out more by {@code amount} (0 to 1): lifted toward white on the dark scheme, and
     * deepened toward a dark ink on the light one, where the gradient's own pale shades fade into the background.
     */
    public static int lift(int color, float amount) {
        if (ConfigStyle.mode() == ConfigTheme.Mode.LIGHT) return ConfigStyle.mix(color, LIGHT_INK, Math.min(1, amount * 1.7F));
        return ConfigStyle.mix(color, 0xFFFFFFFF, amount);
    }

    private static final int LIGHT_INK = 0xFF1A1438;

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
