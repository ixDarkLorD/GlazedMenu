package net.ixdarklord.glazedmenu.internal.style;

import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.api.config.ConfigColorScheme;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// The look shared by every config screen: panels with softened corners, drawn in the current theme's colors, and
// the themed background.
public final class ConfigStyle {
    private static final Map<Identifier, int[]> TEXTURE_SIZES = new HashMap<>();
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

    // --- Shapes: rectangles with their corner pixels cut, which reads as slightly rounded at GUI scale ---

    public static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width <= 2 || height <= 2) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 1, y, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 1, y, x + width - 1, y + 1, color);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** A translucent panel with a hairline border and a faint accent sheen along its top. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        rect(graphics, x, y, width, height, translucent(colors().panel()));
        glaze(graphics, x, y, width, height);
        glass(graphics, x, y, width, height);
        outline(graphics, x, y, width, height, colors().panelBorder());
        graphics.fillGradient(x + 1, y + 1, x + width - 1, y + Math.min(10, height - 1), withAlpha(accent(), 0x14), withAlpha(accent(), 0));
    }

    public static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        rect(graphics, x, y, width, height, translucent(colors().bar()));
        glaze(graphics, x, y, width, height);
        glass(graphics, x, y, width, height);
        outline(graphics, x, y, width, height, colors().panelBorder());
    }

    // --- Glass ---

    // A panel as a slab of frosted glass: a soft reflection down from its top, a bright edge along its top and left and a
    // darker one along its bottom, and two faint diagonal streaks of light, placed by the screen so they run on from
    // panel to panel as across one sheet.
    private static void glass(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (width <= 4 || height <= 4) return;
        boolean light = mode() == ConfigTheme.Mode.LIGHT;
        int white = 0xFFFFFFFF;
        int sheen = Math.max(12, Math.min(height * 2 / 5, 40));
        graphics.fillGradient(x + 1, y + 1, x + width - 1, y + 1 + sheen, withAlpha(white, light ? 0x30 : 0x16), withAlpha(white, 0));
        graphics.fill(x + 2, y + 1, x + width - 2, y + 2, withAlpha(white, light ? 0x70 : 0x3A));
        graphics.fill(x + 1, y + 2, x + 2, y + height - 2, withAlpha(white, light ? 0x40 : 0x1A));
        graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, withAlpha(0xFF000000, light ? 0x18 : 0x48));
        streaks(graphics, x, y, width, height, light);
    }

    // Sun rays: soft beams fanning out from just above the screen's top left corner, swaying slowly, each fading in and
    // out on its own beat. Placed by the screen, so they run on from panel to panel as through one pane of glass.
    private static final int RAYS = 7;
    private static final int RAY_SEGMENTS = 14;
    private static final float SWAY_MILLIS = 16000;

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
        int peak = light ? 0x24 : 0x16;

        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        var pose = graphics.pose();
        for (int i = 0; i < RAYS; i++) {
            // Spread between pointing down and pointing right, unevenly, like light through leaves.
            float spread = (i + 0.5F) / RAYS;
            float angle = -(0.22F + 1.05F * spread + 0.06F * (float) Math.sin(i * 2.7)) + sway * (1 + (i % 3) * 0.4F);
            float breath = animate ? 0.35F + 0.65F * (0.5F + 0.5F * (float) Math.sin(time / (2600 + i * 530) + i * 1.9)) : 0.8F;
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

    public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
        graphics.text(font, ellipsize(font, text, maxWidth), x, y, color, false);
    }

    public static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int maxWidth, int color) {
        FormattedCharSequence line = ellipsize(font, text, maxWidth);
        graphics.text(font, line, centerX - font.width(line) / 2, y, color, false);
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
        Identifier texture = current.background();
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
        float backgroundOpacity = GlazedSettings.backgroundOpacity(current.backgroundOpacity());
        if (backgroundOpacity > 0) graphics.fill(0, 0, width, height, withAlpha(colors.backdrop(), Math.round(255 * backgroundOpacity)));
        if (!textured) graphics.fillGradient(0, 0, width, height / 3, withAlpha(colors.accent(), 0x18), withAlpha(colors.accent(), 0));
    }

    // Glazed Menu's own look: a coat over its panels and bars, one gradient of its logo's colors from the top of the screen to
    // the bottom (drifting slowly through the rest), each box showing its slice of it, so they read as one glazed surface.
    private static boolean glazedmenu() {
        return theme == ConfigTheme.DEFAULT || theme == GlazedBrand.THEME;
    }

    private static void glaze(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (!glazedmenu() || width <= 2 || height <= 2) return;
        int screenHeight = Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        float phase = GlazedBrand.phase();
        boolean light = mode() == ConfigTheme.Mode.LIGHT;
        int shade = light ? 0xFFFFFFFF : 0xFF000000;
        // Deep shades of the scheme's colors, so the coat enriches the panels rather than washing them out.
        int upper = mix(GlazedBrand.color(phase), shade, light ? 0.15F : 0.4F);
        int lower = mix(mix(GlazedBrand.color(phase + 0.35F), shade, light ? 0.15F : 0.4F), 0xFF000000, 0.2F);
        // In bands, each a short gradient, so the strong ends and the faint middle show inside tall boxes too.
        int bottom = y + height - 1;
        for (int top = y + 1; top < bottom; top += GLAZE_BAND) {
            int end = Math.min(bottom, top + GLAZE_BAND);
            graphics.fillGradient(x + 1, top, x + width - 1, end, glazeAt(top, screenHeight, upper, lower, light), glazeAt(end, screenHeight, upper, lower, light));
        }
    }

    private static final int GLAZE_BAND = 4;

    // A panel's or bar's color at the player's panel opacity.
    private static int translucent(int color) {
        return withAlpha(color, GlazedSettings.panelAlpha(color >>> 24));
    }

    // The glaze at a height: one color strong at the top of the screen, the other strong at the bottom, and between
    // them a soft blend of the two, faint through the middle.
    private static int glazeAt(int y, int screenHeight, int upper, int lower, boolean light) {
        float t = Math.clamp(y / (float) screenHeight, 0, 1);
        float blend = t * t * (3 - 2 * t);
        float edge = (float) Math.pow(Math.abs(2 * t - 1), 1.6);
        int faint = light ? 0x0C : 0x10;
        int strong = light ? 0x48 : 0x5A;
        return withAlpha(mix(upper, lower, blend), GlazedSettings.panelAlpha(Math.round(faint + (strong - faint) * edge)));
    }

    private static void texture(GuiGraphicsExtractor graphics, Identifier texture, ConfigTheme current, int width, int height, int color) {
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
    public static int[] textureSize(Identifier texture) {
        return TEXTURE_SIZES.computeIfAbsent(texture, id -> {
            try {
                var gpuTexture = Minecraft.getInstance().getTextureManager().getTexture(id).getTexture();
                return new int[]{Math.max(1, gpuTexture.getWidth(0)), Math.max(1, gpuTexture.getHeight(0))};
            } catch (RuntimeException e) {
                return new int[]{256, 256};
            }
        });
    }

    /** Forgets texture sizes, after resources reload. */
    public static void clearTextureCache(@Nullable Identifier texture) {
        if (texture == null) TEXTURE_SIZES.clear();
        else TEXTURE_SIZES.remove(texture);
    }
}
