package net.ixdarklord.glazedmenu.internal.gui.style;

import net.minecraft.util.Mth;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.compat.RenderPipelines;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

// Mods' icons (their logo on NeoForge) as textures, loaded once from the mod files; a theme may name its own instead.
// Icons are usually far larger than they're drawn, and scaling them down by sampling skips most of their pixels, so
// each is kept at halving sizes, averaged, and drawn from the one nearest the size on screen.
public final class ModIcons {
    private static final Map<String, Optional<Icon>> LOADED = new HashMap<>();
    // Halving stops here; nothing is drawn smaller.
    private static final int SMALLEST = 16;

    private ModIcons() {}

    private record Level(ResourceLocation id, int width, int height) {}

    // Largest first; the accent is the icon's most vivid color (0 when unknown).
    private record Icon(List<Level> levels, int accent) {
        Level forSize(int pixels) {
            Level chosen = this.levels.get(0);
            for (Level level : this.levels) {
                if (Math.max(level.width, level.height) < pixels) break;
                chosen = level;
            }
            return chosen;
        }
    }

    /**
     * Draws the mod's icon fitted into a square, keeping its proportions.
     *
     * @return whether there was an icon to draw
     */
    public static boolean draw(GuiGraphicsExtractor graphics, String modId, ConfigTheme theme, int x, int y, int size) {
        Optional<Icon> icon = get(modId, theme);
        if (icon.isEmpty()) return false;
        int pixels = (int) Math.ceil(size * Minecraft.getInstance().getWindow().getGuiScale());
        Level texture = icon.get().forSize(pixels);
        float scale = Math.min(size / (float) texture.width, size / (float) texture.height);
        int width = Math.max(1, Math.round(texture.width * scale));
        int height = Math.max(1, Math.round(texture.height * scale));
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id, x + (size - width) / 2, y + (size - height) / 2, 0, 0,
                width, height, texture.width, texture.height, texture.width, texture.height);
        return true;
    }

    /** The icon's most vivid color, made bright enough to use as an accent; empty for a mod without an icon. */
    public static OptionalInt accent(String modId) {
        Optional<Icon> icon = LOADED.computeIfAbsent(modId, ModIcons::load);
        return icon.isPresent() && icon.get().accent != 0 ? OptionalInt.of(icon.get().accent) : OptionalInt.empty();
    }

    // The average color of the icon's pixels, each weighted by how opaque and how colorful it is (so a grey frame
    // doesn't wash out the logo), then lifted to a bright, saturated shade. Grey icons give no accent.
    private static int accentOf(NativeImage image) {
        double red = 0;
        double green = 0;
        double blue = 0;
        double total = 0;
        int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 64);
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                int pixel = argb(image.getPixelRGBA(x, y));
                float[] hsb = java.awt.Color.RGBtoHSB(pixel >> 16 & 0xFF, pixel >> 8 & 0xFF, pixel & 0xFF, null);
                double weight = (pixel >>> 24) / 255.0 * hsb[1] * hsb[1] * (0.3 + hsb[2]);
                red += (pixel >> 16 & 0xFF) * weight;
                green += (pixel >> 8 & 0xFF) * weight;
                blue += (pixel & 0xFF) * weight;
                total += weight;
            }
        }
        if (total < 1e-3) return 0;
        float[] hsb = java.awt.Color.RGBtoHSB((int) (red / total), (int) (green / total), (int) (blue / total), null);
        if (hsb[1] < 0.12F) return 0;
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hsb[0], Mth.clamp(hsb[1] * 1.25F, 0.45F, 0.85F), Mth.clamp(hsb[2] * 1.2F, 0.75F, 0.95F)) & 0xFFFFFF;
    }

    /** Whether the mod has an icon to draw. */
    public static boolean has(String modId, ConfigTheme theme) {
        return get(modId, theme).isPresent();
    }

    private static Optional<Icon> get(String modId, ConfigTheme theme) {
        ResourceLocation themed = theme.icon();
        if (themed != null) {
            int[] size = ConfigStyle.textureSize(themed);
            return Optional.of(new Icon(List.of(new Level(themed, size[0], size[1])), 0));
        }
        return LOADED.computeIfAbsent(modId, ModIcons::load);
    }

    private static Optional<Icon> load(String modId) {
        return GlazedPlatform.get().readModIcon(modId).flatMap(bytes -> {
            try {
                // From a stream: 1.21.1 copies a byte array through the small native stack, which large icons overflow.
                NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                int accent = accentOf(image);
                String base = "mod_icon/" + modId.replaceAll("[^a-z0-9_.-]", "_");
                List<Level> levels = new ArrayList<>();
                for (int i = 0; ; i++) {
                    ResourceLocation id = GlazedMenu.rl(i == 0 ? base : base + "_" + i);
                    int width = image.getWidth();
                    int height = image.getHeight();
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                    levels.add(new Level(id, width, height));
                    if (Math.min(width, height) / 2 < SMALLEST) break;
                    image = half(image);
                }
                return Optional.of(new Icon(List.copyOf(levels), accent));
            } catch (IOException | RuntimeException e) {
                GlazedMenu.LOGGER.debug("Couldn't load the icon of {}", modId, e);
                return Optional.empty();
            }
        });
    }

    // Each pixel the average of four, weighted by their alpha so transparent pixels don't darken the edges.
    private static NativeImage half(NativeImage source) {
        int width = source.getWidth() / 2;
        int height = source.getHeight() / 2;
        NativeImage result = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = 0;
                int red = 0;
                int green = 0;
                int blue = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int pixel = argb(source.getPixelRGBA(x * 2 + dx, y * 2 + dy));
                        int a = pixel >>> 24;
                        alpha += a;
                        red += (pixel >> 16 & 0xFF) * a;
                        green += (pixel >> 8 & 0xFF) * a;
                        blue += (pixel & 0xFF) * a;
                    }
                }
                int argb = alpha == 0 ? 0 : (alpha / 4) << 24 | (red / alpha) << 16 | (green / alpha) << 8 | blue / alpha;
                result.setPixelRGBA(x, y, argb(argb));
            }
        }
        return result;
    }

    // 1.21.1's images hold ABGR; swapping red and blue converts either way.
    private static int argb(int abgr) {
        return abgr & 0xFF00FF00 | (abgr & 0xFF) << 16 | abgr >> 16 & 0xFF;
    }
}
