package net.ixdarklord.glazedmenu.internal.style;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.compat.RenderPipelines;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.api.config.ConfigColorScheme;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// The look shared by every config screen: panels with softened corners, drawn in the current theme's colors, and
// the themed background.
public final class ConfigStyle {
    private static final Map<ResourceLocation, int[]> TEXTURE_SIZES = new HashMap<>();
    private static ConfigTheme theme = ConfigTheme.DEFAULT;

    private ConfigStyle() {}

    /** Makes a theme the one widgets draw with; screens set theirs before drawing. */
    public static void use(ConfigTheme newTheme) {
        theme = newTheme;
    }

    public static ConfigTheme theme() {
        return theme;
    }

    /** The current theme's colors. */
    public static ConfigColorScheme colors() {
        return theme.colors(mode());
    }

    /** Dark or light, as the player chose. */
    public static ConfigTheme.Mode mode() {
        return GlazedSettings.themeMode();
    }

    /** Another theme's accent in the current mode, for things drawn in a mod's own color (its config cards). */
    public static int accentOf(ConfigTheme other) {
        return other.colors(mode()).accent();
    }

    public static int accent() {
        return colors().accent();
    }

    // --- Colors ---

    public static int withAlpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | color & 0xFFFFFF;
    }

    public static int mix(int from, int to, float t) {
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Dark text on light colors, light text on dark ones. */
    public static int readableOn(int color) {
        double luminance = 0.2126 * (color >> 16 & 0xFF) + 0.7152 * (color >> 8 & 0xFF) + 0.0722 * (color & 0xFF);
        return luminance > 150 ? 0xFF0B0E14 : 0xFFFFFFFF;
    }

    // --- Shapes: rectangles with their corners rounded off, a pixel or (on anything large enough to show it) two ---

    // Whether a box is large enough for the rounder corner.
    private static boolean round(int width, int height) {
        return width >= 12 && height >= 12;
    }

    public static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width <= 2 || height <= 2) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        if (round(width, height)) {
            graphics.fill(x + 2, y, x + width - 2, y + height, color);
            graphics.fill(x + 1, y + 1, x + 2, y + height - 1, color);
            graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, color);
            graphics.fill(x, y + 2, x + 1, y + height - 2, color);
            graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
            return;
        }
        graphics.fill(x + 1, y, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /**
     * A box's border. On Glazed Menu's own look, a box of a button's size or more is also given what makes it a piece
     * of glass standing off the surface under it: a rim of light inside its top edge and a soft reflection under that,
     * a shade inside its bottom edge, and a shadow dropped below it.
     */
    public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        border(graphics, x, y, width, height, color);
        if (glazedmenu() && width >= 14 && height >= 12) {
            boolean light = mode() == ConfigTheme.Mode.LIGHT;
            int white = 0xFFFFFFFF;
            graphics.fillGradient(x + 1, y + 2, x + width - 1, y + 2 + Math.min(height / 2, 9), withAlpha(white, light ? 0x26 : 0x16), withAlpha(white, 0));
            graphics.fill(x + 2, y + 1, x + width - 2, y + 2, withAlpha(white, light ? 0x70 : 0x3C));
            graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, withAlpha(0xFF000000, light ? 0x14 : 0x30));
            shadow(graphics, x, y, width, height, 0.75F);
        }
    }

    // The border alone.
    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (round(width, height)) {
            graphics.fill(x + 2, y, x + width - 2, y + 1, color);
            graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
            graphics.fill(x, y + 2, x + 1, y + height - 2, color);
            graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
            graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
            graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
            graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
            graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
            return;
        }
        graphics.fill(x + 1, y, x + width - 1, y + 1, color);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    // A shadow dropped below a box and a little to its right, fading out over a few pixels. Only outside the box, so
    // it never darkens what the box holds (the boxes are see-through), whichever of them is drawn first.
    private static void shadow(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float strength) {
        int black = 0xFF000000;
        boolean light = mode() == ConfigTheme.Mode.LIGHT;
        float weight = strength * (light ? 0.55F : 1);
        graphics.fill(x + 2, y + height, x + width - 1, y + height + 1, withAlpha(black, Math.round(0x58 * weight)));
        graphics.fill(x + 3, y + height + 1, x + width - 1, y + height + 2, withAlpha(black, Math.round(0x2C * weight)));
        graphics.fill(x + width, y + 3, x + width + 1, y + height - 1, withAlpha(black, Math.round(0x30 * weight)));
        // Round the bottom right corner too: the pixels the box's rounded corner leaves empty, and the one past them
        // where the shadow's two sides meet.
        int corner = withAlpha(black, Math.round(0x44 * weight));
        if (round(width, height)) {
            graphics.fill(x + width - 2, y + height - 1, x + width, y + height, corner);
            graphics.fill(x + width - 1, y + height - 2, x + width, y + height - 1, corner);
        } else {
            graphics.fill(x + width - 1, y + height - 1, x + width, y + height, corner);
        }
        graphics.fill(x + width - 1, y + height, x + width, y + height + 1, withAlpha(black, Math.round(0x2C * weight)));
        if (strength >= 1) {
            graphics.fill(x + 4, y + height + 2, x + width - 1, y + height + 3, withAlpha(black, Math.round(0x14 * weight)));
            graphics.fill(x + width + 1, y + 4, x + width + 2, y + height - 1, withAlpha(black, Math.round(0x14 * weight)));
        }
    }

    /**
     * The box behind an icon, in a color of its own: filled, with an outline in a deeper shade of the color, a light
     * edge inside its top and left and a shade inside its bottom and right (so it reads as raised), and a small shadow
     * under it.
     */
    public static void iconFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        int black = 0xFF000000;
        int white = 0xFFFFFFFF;
        graphics.fill(x + 1, y + height, x + width, y + height + 1, withAlpha(black, 0x50));
        graphics.fill(x + width, y + 1, x + width + 1, y + height, withAlpha(black, 0x30));
        rect(graphics, x, y, width, height, color);
        border(graphics, x, y, width, height, withAlpha(mix(color, black, 0.45F), 0xFF));
        if (width < 8 || height < 8) return;
        graphics.fill(x + 2, y + 1, x + width - 2, y + 2, withAlpha(white, 0x6A));
        graphics.fill(x + 1, y + 2, x + 2, y + height - 2, withAlpha(white, 0x40));
        graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, withAlpha(black, 0x38));
        graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 2, withAlpha(black, 0x24));
    }

    /** The same frame over a picture already drawn in the box (a world's, a server's): the outline, the edges and the shadow. */
    public static void pictureFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int black = 0xFF000000;
        int white = 0xFFFFFFFF;
        graphics.fill(x + 1, y + height, x + width, y + height + 1, withAlpha(black, 0x50));
        graphics.fill(x + width, y + 1, x + width + 1, y + height, withAlpha(black, 0x30));
        graphics.fill(x, y, x + width, y + 1, withAlpha(black, 0xA0));
        graphics.fill(x, y + height - 1, x + width, y + height, withAlpha(black, 0xA0));
        graphics.fill(x, y + 1, x + 1, y + height - 1, withAlpha(black, 0xA0));
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, withAlpha(black, 0xA0));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, withAlpha(white, 0x50));
        graphics.fill(x + 1, y + 2, x + 2, y + height - 1, withAlpha(white, 0x30));
    }

    /** A translucent panel with a hairline border and a faint accent sheen along its top. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        panel(graphics, x, y, width, height, true);
    }

    /** As {@link #panel(GuiGraphicsExtractor, int, int, int, int)}; {@code rays} false leaves out the sun rays. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean rays) {
        if (glazedmenu()) shadow(graphics, x, y, width, height, 1);
        rect(graphics, x, y, width, height, translucent(colors().panel()));
        glaze(graphics, x, y, width, height);
        if (glazedmenu()) waves(graphics, x, y, width, height);
        glass(graphics, x, y, width, height, rays);
        border(graphics, x, y, width, height, colors().panelBorder());
        graphics.fillGradient(x + 2, y + 1, x + width - 2, y + Math.min(10, height - 1), withAlpha(accent(), 0x14), withAlpha(accent(), 0));
    }

    public static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (glazedmenu()) shadow(graphics, x, y, width, height, 1);
        rect(graphics, x, y, width, height, translucent(colors().bar()));
        glaze(graphics, x, y, width, height);
        if (glazedmenu()) waves(graphics, x, y, width, height);
        glass(graphics, x, y, width, height, true);
        border(graphics, x, y, width, height, colors().panelBorder());
    }

    // --- Glass ---

    // A panel as a slab of liquid glass: a soft reflection down from its top and a fainter glow up from its bottom, a
    // bright rim of light along its top and its left (and a faint one down its right), a darker one along its bottom,
    // and faint diagonal beams of light, placed by the screen so they run on from panel to panel as across one sheet.
    private static void glass(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean rays) {
        if (width <= 4 || height <= 4) return;
        boolean light = mode() == ConfigTheme.Mode.LIGHT;
        int white = 0xFFFFFFFF;
        int sheen = Math.max(12, Math.min(height * 2 / 5, 40));
        graphics.fillGradient(x + 1, y + 1, x + width - 1, y + 1 + sheen, withAlpha(white, light ? 0x3A : 0x24), withAlpha(white, 0));
        int glow = Math.min(height / 3, 14);
        graphics.fillGradient(x + 1, y + height - 1 - glow, x + width - 1, y + height - 1, withAlpha(white, 0), withAlpha(white, light ? 0x1C : 0x10));
        graphics.fill(x + 2, y + 1, x + width - 2, y + 2, withAlpha(white, light ? 0x90 : 0x62));
        graphics.fill(x + 1, y + 2, x + 2, y + height - 2, withAlpha(white, light ? 0x50 : 0x30));
        graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 2, withAlpha(white, light ? 0x28 : 0x14));
        graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, withAlpha(0xFF000000, light ? 0x18 : 0x40));
        if (rays) streaks(graphics, x, y, width, height, light);
    }

    // Sun rays: soft beams fanning out from just above the screen's top left corner, swaying slowly, each fading in and
    // out on its own beat. Placed by the screen, so they run on from panel to panel as through one pane of glass.
    private static final int RAYS = 7;
    private static final int RAY_SEGMENTS = 14;
    private static final float SWAY_MILLIS = 28000;

    private static void streaks(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean light) {
        var window = Minecraft.getInstance().getWindow();
        int screenWidth = window.getGuiScaledWidth();
        int screenHeight = window.getGuiScaledHeight();
        float originX = -screenWidth * 0.08F;
        float originY = -screenHeight * 0.18F;
        float reach = (float) Math.hypot(screenWidth - originX, screenHeight - originY);
        boolean animate = GlazedSettings.transitions();
        double time = animate ? System.currentTimeMillis() : 0;
        float sway = animate ? (float) Math.sin(time / SWAY_MILLIS * Math.PI * 2) * 0.05F : 0;
        // Faint: a hint of light in the glass, behind what the panels hold.
        int peak = light ? 0x0A : 0x06;

        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        var pose = graphics.pose();
        for (int i = 0; i < RAYS; i++) {
            // Spread between pointing down and pointing right, unevenly, like light through leaves.
            float spread = (i + 0.5F) / RAYS;
            float angle = -(0.22F + 1.05F * spread + 0.06F * (float) Math.sin(i * 2.7)) + sway * (1 + (i % 3) * 0.4F);
            // Slow, eased breathing: a sine run through a smoothstep, so the light lingers at its brightest and faintest.
            float wave = 0.5F + 0.5F * (float) Math.sin(time / (5200 + i * 900) + i * 1.9);
            wave = wave * wave * (3 - 2 * wave);
            float breath = animate ? 0.35F + 0.65F * wave : 0.8F;
            float rayWidth = 10 + (i * 37 % 5) * 7;
            int alpha = Math.round(peak * breath);
            if (alpha <= 0) continue;
            pose.pushMatrix();
            pose.translate(originX, originY);
            pose.rotate(angle);
            // Down the beam (its +y), in segments: widening as it goes, brightest a little way out, fading to nothing.
            float segment = reach / RAY_SEGMENTS;
            for (int k = 0; k < RAY_SEGMENTS; k++) {
                float t0 = k / (float) RAY_SEGMENTS;
                float t1 = (k + 1) / (float) RAY_SEGMENTS;
                int a0 = Math.round(alpha * rayFade(t0));
                int a1 = Math.round(alpha * rayFade(t1));
                if (a0 == 0 && a1 == 0) continue;
                float half = rayWidth * (0.3F + 1.4F * t0) / 2;
                int top = Math.round(segment * k);
                int bottom = Math.round(segment * (k + 1));
                // A soft core and fainter edges on each side.
                graphics.fillGradient(Math.round(-half * 0.45F), top, Math.round(half * 0.45F), bottom,
                        withAlpha(0xFFFFFFFF, a0), withAlpha(0xFFFFFFFF, a1));
                graphics.fillGradient(Math.round(-half), top, Math.round(-half * 0.45F), bottom,
                        withAlpha(0xFFFFFFFF, a0 / 2), withAlpha(0xFFFFFFFF, a1 / 2));
                graphics.fillGradient(Math.round(half * 0.45F), top, Math.round(half), bottom,
                        withAlpha(0xFFFFFFFF, a0 / 2), withAlpha(0xFFFFFFFF, a1 / 2));
            }
            pose.popMatrix();
        }
        graphics.disableScissor();
    }

    // A beam's brightness along it: rising from the sun, strongest near a fifth of the way, fading out by the far end.
    private static float rayFade(float t) {
        if (t < 0.2F) return t / 0.2F;
        return (float) Math.pow(1 - (t - 0.2F) / 0.8F, 1.6);
    }


    /** A small label with a colored border, like a tag. */
    public static int badge(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        int width = font.width(text) + 8;
        rect(graphics, x, y, width, 11, withAlpha(color, 0x30));
        outline(graphics, x, y, width, 11, withAlpha(color, 0xB0));
        graphics.text(font, text, x + 4, y + 2, color, false);
        return width;
    }

    // --- Text ---

    // On Glazed Menu's dark look the panels are see-through glass over whatever is behind them, so text has a shadow
    // under it there: it reads over a bright panorama or world as it does over a dark one.
    private static boolean shadowed() {
        return glazedmenu() && mode() != ConfigTheme.Mode.LIGHT;
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
        graphics.text(font, ellipsize(font, text, maxWidth), x, y, color, shadowed());
    }

    public static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int maxWidth, int color) {
        FormattedCharSequence line = ellipsize(font, text, maxWidth);
        graphics.text(font, line, centerX - font.width(line) / 2, y, color, shadowed());
    }

    public static FormattedCharSequence ellipsize(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("…")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
    }

    // --- Background ---

    /**
     * The theme's background. By default it's see-through: the title screen's panorama, or the world while playing,
     * blurred and darkened by the backdrop color so the panels stay readable. A theme's texture is drawn over it (in a
     * world only when the theme asks), fully covering it unless the texture is translucent. The theme's opacities are
     * scaled by the player's own choice in CoolCatLib: Canvas's client config.
     *
     * @param panorama draws the title screen's panorama
     */
    public static void background(GuiGraphicsExtractor graphics, int width, int height, Runnable panorama) {
        Minecraft minecraft = Minecraft.getInstance();
        ConfigTheme current = theme;
        ConfigColorScheme colors = current.colors(mode());
        ResourceLocation texture = current.background();
        boolean textured = texture != null && (minecraft.level == null || current.backgroundInWorld());
        float textureOpacity = textured ? GlazedSettings.textureOpacity(current.textureOpacity()) : 0;
        // What shows through: the panorama or the world, blurred.
        if (textureOpacity < 1) {
            if (minecraft.level == null) panorama.run();
            if (minecraft.options.getMenuBackgroundBlurriness() >= 1) graphics.blurBeforeThisStratum();
        }
        if (textured && textureOpacity > 0) {
            texture(graphics, texture, current, width, height, withAlpha(0xFFFFFFFF, Math.round(255 * textureOpacity)));
        }
        // Glazed Menu's own look has no backdrop color: the panorama or the world shows through clear (blurred as the
        // game blurs it); the color, and the waves, are in the panels themselves.
        boolean glazed = !textured && glazedmenu();
        float backgroundOpacity = GlazedSettings.backgroundOpacity(current.backgroundOpacity());
        if (!glazed && backgroundOpacity > 0) graphics.fill(0, 0, width, height, withAlpha(colors.backdrop(), Math.round(255 * backgroundOpacity)));
        if (!glazed && !textured) {
            graphics.fillGradient(0, 0, width, height / 3, withAlpha(colors.accent(), 0x18), withAlpha(colors.accent(), 0));
        }
    }

    // Waves in the panels and bars of Glazed Menu's own look, part of their backgrounds: placed by the screen (one above
    // the next from the middle of the screen down), so they run on from panel to panel as across one sheet, each box
    // showing the part of them that crosses it. Each is a long swell with shorter ones riding it, drifting sideways at
    // its own pace (every other one the other way) and rising and sinking a little, in the scheme's colors, each a step
    // further round them, and each swelling brighter and fainter on a beat of its own.
    // <p>
    // They're drawn as a liquid, with no hard edge anywhere: the surface comes in softly, a sheen of lighter color lies
    // just under it and melts into the body, and the body thins out with depth. And they're drawn in the window's own
    // pixels, not the GUI's (which are two, three or four of them wide): a GUI pixel is too coarse for a curve, and
    // shows as steps along it.
    private static final int WAVES = 6;
    private static final float WAVE_MILLIS = 26000;
    // Drawn in columns this many of the window's pixels wide: the surface slopes too gently, and is too soft, for
    // steps that small to show.
    private static final int WAVE_STEP = 3;
    // The surface's soft edge above it, and the sheen's reach below it, in GUI pixels.
    private static final float WAVE_EDGE = 5;
    private static final float WAVE_SHEEN = 8;

    private static void waves(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (width <= 2 || height <= 2) return;
        var window = Minecraft.getInstance().getWindow();
        // From here on, everything is in the window's pixels.
        float scale = Math.max(1, (float) window.getGuiScale());
        int screenWidth = Math.max(1, Math.round(window.getGuiScaledWidth() * scale));
        int screenHeight = Math.max(1, Math.round(window.getGuiScaledHeight() * scale));
        boolean light = mode() == ConfigTheme.Mode.LIGHT;
        boolean animate = GlazedSettings.transitions();
        double time = animate ? System.currentTimeMillis() : 0;
        float phase = GlazedBrand.phase();
        int body = GlazedSettings.panelAlpha(light ? 0x34 : 0x30);
        int sheen = GlazedSettings.panelAlpha(light ? 0x54 : 0x4C);
        float depth = screenHeight * 0.30F;
        float edge = WAVE_EDGE * scale;
        float sheenReach = WAVE_SHEEN * scale;
        int left = Math.round((x + 1) * scale);
        int right = Math.round((x + width - 1) * scale);
        int top = Math.round((y + 1) * scale);
        int bottom = Math.round((y + height - 1) * scale);
        // The rows a column is drawn between, as distances under the surface: the soft edge, the sheen, and the body
        // in two parts (it thins out along a curve, not a line).
        float[] stops = {-edge, 0, sheenReach, sheenReach + (depth - sheenReach) * 0.4F, depth};
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(1 / scale, 1 / scale);
        for (int i = 0; i < WAVES; i++) {
            float spread = i / (float) (WAVES - 1);
            float swell = screenHeight * (0.032F + 0.011F * (i % 3));
            // Rising and sinking a little, slowly.
            float bob = animate ? swell * 0.35F * (float) Math.sin(time / (5200 + i * 700) + i * 2.1) : 0;
            float base = screenHeight * (0.30F + 0.56F * spread) + bob;
            // Where the wave can be at all: outside the box, it's left out.
            if (base - swell * 1.7F - edge >= bottom || base + swell * 1.7F + depth <= top) continue;
            // Whole turns of the drift over each wave's own loop, so it comes round to where it began.
            float loop = WAVE_MILLIS * (0.7F + 0.16F * i);
            float drift = (float) (time / loop % 1 * Math.PI * 2) * (i % 2 == 0 ? 1 : -1);
            float turns = 1.5F + 0.4F * i;
            // The beat: brighter for a while, then fainter, each wave on its own.
            float beat = animate ? 0.74F + 0.26F * (float) Math.sin(time / (3000 + i * 470) + i * 1.7) : 0.87F;
            for (int column = left; column < right; column += WAVE_STEP) {
                int end = Math.min(right, column + WAVE_STEP);
                float along = (column + WAVE_STEP / 2F) / screenWidth;
                float surface = base + swell * (float) Math.sin(along * Math.PI * turns + drift + i * 1.3F)
                        + swell * 0.38F * (float) Math.sin(along * Math.PI * (turns * 2.1F) - drift * 2 + i * 0.7F)
                        + swell * 0.16F * (float) Math.sin(along * Math.PI * (turns * 3.7F) + drift * 3 + i * 2.9F);
                if (surface - edge >= bottom || surface + depth <= top) continue;
                int color = GlazedBrand.color(phase + i * 0.16F + along * 0.45F);
                int bright = GlazedBrand.lift(color, 0.45F);
                int row = (int) Math.floor(surface);
                for (int stop = 0; stop < stops.length - 1; stop++) {
                    int from = Math.max(top, row + Math.round(stops[stop]));
                    int to = Math.min(bottom, row + Math.round(stops[stop + 1]));
                    if (to <= from) continue;
                    graphics.fillGradient(column, from, end, to, liquid(from - surface, depth, edge, sheenReach, color, bright, body, sheen, beat),
                            liquid(to - surface, depth, edge, sheenReach, color, bright, body, sheen, beat));
                }
            }
        }
        pose.popMatrix();
    }

    // The liquid's color at a distance under its surface (negative above it): nothing above the soft edge, rising
    // smoothly to the sheen at the surface, the sheen melting into the body's color under it, and the body thinning
    // out to nothing at its depth, slowly at first.
    private static int liquid(float under, float depth, float edge, float sheenReach, int color, int bright, int body, int sheen, float beat) {
        if (under <= -edge || under >= depth) return withAlpha(color, 0);
        if (under < 0) {
            float t = 1 + under / edge;
            return withAlpha(bright, Math.round(sheen * beat * t * t * (3 - 2 * t)));
        }
        if (under < sheenReach) {
            float t = under / sheenReach;
            float eased = t * t * (3 - 2 * t);
            return withAlpha(mix(bright, color, eased), Math.round(beat * (sheen + (body - sheen) * eased)));
        }
        float t = (under - sheenReach) / Math.max(1, depth - sheenReach);
        float remaining = 1 - t;
        return withAlpha(color, Math.round(body * beat * remaining * remaining * (3 - 2 * remaining)));
    }

    // Glazed Menu's own look: a coat over its panels and bars in the gradient its mod list is drawn with, running left to
    // right across the whole screen and flowing along it, each box showing its slice of it, so they read as one surface
    // of moving color.
    private static boolean glazedmenu() {
        return theme == ConfigTheme.DEFAULT || theme == GlazedBrand.THEME;
    }

    /** Whether what's being drawn is in Glazed Menu's own look (and not a mod's theme of its own). */
    public static boolean isGlazed() {
        return glazedmenu();
    }

    // Drawn in columns this wide: the gradient changes too slowly across them for the steps to show.
    private static final int GLAZE_BAND = 6;

    private static void glaze(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (!glazedmenu() || width <= 2 || height <= 2) return;
        int screenWidth = Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        float phase = GlazedBrand.phase();
        boolean light = mode() == ConfigTheme.Mode.LIGHT;
        // A shade deeper than the gradient itself on the dark look (lighter on the light one), so text reads on it.
        int shade = light ? 0xFFFFFFFF : 0xFF0A0818;
        float shaded = light ? 0.48F : 0.46F;
        int alpha = GlazedSettings.panelAlpha(light ? 0x66 : 0x80);
        int right = x + width - 1;
        for (int left = x + 1; left < right; left += GLAZE_BAND) {
            int end = Math.min(right, left + GLAZE_BAND);
            int color = mix(GlazedBrand.color(phase + (left + GLAZE_BAND / 2F) / screenWidth * 0.9F), shade, shaded);
            graphics.fill(left, y + 1, end, y + height - 1, withAlpha(color, alpha));
        }
    }

    // A panel's or bar's color at the player's panel opacity.
    private static int translucent(int color) {
        return withAlpha(color, GlazedSettings.panelAlpha(color >>> 24));
    }

    private static void texture(GuiGraphicsExtractor graphics, ResourceLocation texture, ConfigTheme current, int width, int height, int color) {
        int[] size = textureSize(texture);
        int textureWidth = size[0];
        int textureHeight = size[1];
        switch (current.mode()) {
            case STRETCH -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, width, height, textureWidth, textureHeight, textureWidth, textureHeight, color);
            case COVER -> {
                float scale = Math.max(width / (float) textureWidth, height / (float) textureHeight);
                int drawWidth = Math.round(textureWidth * scale);
                int drawHeight = Math.round(textureHeight * scale);
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, (width - drawWidth) / 2, (height - drawHeight) / 2, 0, 0,
                        drawWidth, drawHeight, textureWidth, textureHeight, textureWidth, textureHeight, color);
            }
            case TILE -> {
                int tileWidth = current.tileSize() > 0 ? current.tileSize() : textureWidth;
                int tileHeight = Math.max(1, Math.round(tileWidth * textureHeight / (float) textureWidth));
                for (int y = 0; y < height; y += tileHeight) {
                    for (int x = 0; x < width; x += tileWidth) {
                        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, tileWidth, tileHeight, textureWidth, textureHeight, textureWidth, textureHeight, color);
                    }
                }
            }
        }
    }

    // Read once from the loaded texture; a missing texture shows as the missing-texture checkerboard.
    public static int[] textureSize(ResourceLocation texture) {
        return TEXTURE_SIZES.computeIfAbsent(texture, id -> {
            try {
                // 1.21.1's textures don't know their size: a generated one has its image, a file is read for it.
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.getTextureManager().getTexture(id) instanceof net.minecraft.client.renderer.texture.DynamicTexture dynamic && dynamic.getPixels() != null) {
                    return new int[]{Math.max(1, dynamic.getPixels().getWidth()), Math.max(1, dynamic.getPixels().getHeight())};
                }
                try (var stream = minecraft.getResourceManager().open(id); var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                    return new int[]{Math.max(1, image.getWidth()), Math.max(1, image.getHeight())};
                }
            } catch (java.io.IOException e) {
                return new int[]{256, 256};
            } catch (RuntimeException e) {
                return new int[]{256, 256};
            }
        });
    }

    /** Forgets texture sizes, after resources reload. */
    public static void clearTextureCache(@Nullable ResourceLocation texture) {
        if (texture == null) TEXTURE_SIZES.clear();
        else TEXTURE_SIZES.remove(texture);
    }
}
