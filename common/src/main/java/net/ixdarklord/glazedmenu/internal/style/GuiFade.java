package net.ixdarklord.glazedmenu.internal.style;

import net.minecraft.util.Mth;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

// A fade for whatever the GUI draws while it's set: 1.21.1 draws each shape as it's called, tinted by the shader color,
// so the fade is that color's alpha (and GuiFadeMixin keeps it when a widget sets its own tint). Screens set it around
// the parts that fade and put it back after.
public final class GuiFade {
    private static float alpha = 1;

    private GuiFade() {}

    public static void set(float value) {
        float clamped = Mth.clamp(value, 0, 1);
        if (clamped == alpha) return;
        flush();
        alpha = clamped;
        RenderSystem.setShaderColor(1, 1, 1, alpha);
    }

    public static void reset() {
        set(1);
    }

    public static float alpha() {
        return alpha;
    }

    /** The color with its alpha scaled by the fade. */
    public static int apply(int color) {
        if (alpha >= 1) return color;
        int scaled = Math.round((color >>> 24) * alpha);
        return scaled << 24 | color & 0xFFFFFF;
    }

    // Draws what's batched with the old fade before the new one applies.
    private static void flush() {
        RenderSystem.disableDepthTest();
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        RenderSystem.enableDepthTest();
    }
}
