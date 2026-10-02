package net.ixdarklord.glazedmenu.internal.style;

import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Random;

// The built-in effect (ConfigTheme.STARFALL): large soft glows in the accent drifting slowly, and small faint stars
// falling from the top. Both sit at different depths and shift with the mouse by their depth (parallax): near stars are
// larger, brighter, faster, and move more. Positions come from the time, so it runs at any frame rate and looks the same
// on every screen; only the parallax is eased toward the mouse.
final class StarfallEffect implements ConfigEffect {
    static final Identifier GLOW = GlazedMenu.rl("textures/gui/style/glow.png");
    static final int GLOW_SIZE = 64;
    private static final int STAR_COUNT = 90;
    // How far the nearest layer shifts, in GUI pixels, as the mouse crosses the screen.
    private static final float PARALLAX = 14;
    private static final Orb[] ORBS = {
            new Orb(0.18F, 0.25F, 0.55F, 0.35F, 0.0F),
            new Orb(0.82F, 0.35F, 0.45F, 0.6F, 2.1F),
            new Orb(0.5F, 0.85F, 0.6F, 0.2F, 4.2F),
    };

    private final Star[] stars = new Star[STAR_COUNT];
    // The parallax offset, eased toward where the mouse points (-1..1 on each axis).
    private float parallaxX;
    private float parallaxY;
    private float targetX;
    private float targetY;
    private long lastFrame;

    StarfallEffect() {
        Random random = new Random(0xC0FFEE);
        for (int i = 0; i < STAR_COUNT; i++) {
            // Three layers: far, middle, near.
            float depth = i % 5 == 0 ? 1.0F : i % 2 == 0 ? 0.6F : 0.3F;
            this.stars[i] = new Star(random.nextFloat(), random.nextFloat(), depth, 0.7F + random.nextFloat() * 0.6F,
                    random.nextFloat() * 6.28F, 0.4F + random.nextFloat() * 1.4F);
        }
    }

    // x, y: where it starts, as a fraction of the screen; depth: 0.3 far to 1 near; speed: a multiplier of its layer's
    // fall speed; phase and twinkle: when and how fast it sways and flickers.
    private record Star(float x, float y, float depth, float speed, float phase, float twinkle) {}

    // A glow: its center as a fraction of the screen, its size as a fraction of the screen's shorter side, its depth,
    // and its drift phase.
    private record Orb(float x, float y, float size, float depth, float phase) {}

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, Context context) {
        int width = context.width();
        int height = context.height();
        float time = context.time();
        this.updateParallax(context);
        int accent = context.accent();
        boolean light = context.mode() == ConfigTheme.Mode.LIGHT;

        // The glows, drifting on slow circles.
        int shorter = Math.min(width, height);
        for (Orb orb : ORBS) {
            float size = shorter * orb.size;
            float x = width * orb.x + (float) Math.sin(time * 0.07F + orb.phase) * width * 0.04F - this.parallaxX * PARALLAX * orb.depth;
            float y = height * orb.y + (float) Math.cos(time * 0.05F + orb.phase) * height * 0.04F - this.parallaxY * PARALLAX * orb.depth;
            glow(graphics, x, y, size, ConfigStyle.withAlpha(accent, light ? 0x40 : 0x38));
        }

        // The stars, falling and swaying, wrapping back to the top; all behind the panels.
        int starColor = starColor(accent, light);
        for (Star star : this.stars) this.extractStar(graphics, star, context, starColor, 1);
    }

    private void extractStar(GuiGraphicsExtractor graphics, Star star, Context context, int color, float strength) {
        int height = context.height();
        float time = context.time();
        float fall = (6 + 16 * star.depth) * star.speed;
        float span = height + 8;
        float y = ((star.y * span + time * fall) % span) - 4 - this.parallaxY * PARALLAX * star.depth;
        float x = star.x * context.width() + (float) Math.sin(time * 0.4F * star.speed + star.phase) * 3 * star.depth - this.parallaxX * PARALLAX * star.depth;
        // Fainter near the top so they appear softly, and a slow flicker.
        float fadeIn = Math.min(1, (y + 4) / (height * 0.15F));
        float flicker = 0.6F + 0.4F * (float) Math.sin(time * star.twinkle + star.phase);
        float alpha = (0.35F + 0.5F * star.depth) * flicker * Math.max(0, fadeIn) * strength;
        if (alpha <= 0.01F) return;
        float size = star.depth >= 1 ? 2 : 1;
        float centerX = x + size / 2;
        float centerY = y + size / 2;
        if (star.depth >= 0.6F) glow(graphics, centerX, centerY, 10 + 8 * star.depth, ConfigStyle.withAlpha(color, Math.round(150 * alpha)));
        graphics.fill(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size), ConfigStyle.withAlpha(color, Math.round(255 * alpha)));
        // The nearest ones sparkle: a faint cross, brightest as they flicker up.
        if (star.depth >= 1 && flicker > 0.75F) {
            int sparkle = ConfigStyle.withAlpha(color, Math.round(110 * alpha * (flicker - 0.75F) * 4));
            int cx = Math.round(centerX);
            int cy = Math.round(centerY);
            graphics.fill(cx - 3, cy, cx + 3, cy + 1, sparkle);
            graphics.fill(cx, cy - 3, cx + 1, cy + 3, sparkle);
        }
    }

    private static int starColor(int accent, boolean light) {
        return towardWhite(accent, light ? 0.15F : 0.7F);
    }

    // A popup redrawing the page under it passes no mouse; the parallax then stays where it was.
    private void updateParallax(Context context) {
        if (context.hasMouse() && context.width() > 0 && context.height() > 0) {
            this.targetX = Math.clamp(context.mouseX() / (float) context.width() * 2 - 1, -1, 1);
            this.targetY = Math.clamp(context.mouseY() / (float) context.height() * 2 - 1, -1, 1);
        }
        long now = System.nanoTime();
        float elapsed = this.lastFrame == 0 ? 1000 : Math.min(200, (now - this.lastFrame) / 1_000_000F);
        this.lastFrame = now;
        float keep = (float) Math.exp(-elapsed / 180);
        this.parallaxX = this.targetX + (this.parallaxX - this.targetX) * keep;
        this.parallaxY = this.targetY + (this.parallaxY - this.targetY) * keep;
    }

    /** The soft glow texture centered on a point, at a size, tinted. */
    static void glow(GuiGraphicsExtractor graphics, float x, float y, float size, int color) {
        int drawn = Math.max(2, Math.round(size));
        graphics.blit(RenderPipelines.GUI_TEXTURED, GLOW, Math.round(x - drawn / 2F), Math.round(y - drawn / 2F), 0, 0,
                drawn, drawn, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, color);
    }

    private static int towardWhite(int color, float amount) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        r = Math.round(r + (255 - r) * amount);
        g = Math.round(g + (255 - g) * amount);
        b = Math.round(b + (255 - b) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
