package net.ixdarklord.glazedmenu.internal.style;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

// A fade for whatever the GUI draws while it's set: 1.21.1 draws each shape as it's called, tinted by the shader color,
// so the fade is that color's alpha (and GuiFadeMixin keeps it when a widget sets its own tint). Screens set it around
// the parts that fade and put it back after.
public final class GuiFade {
    private static float alpha = 1;
    // What every fade is set within: below 1 while something already fading (a page sliding in) draws a part that sets
    // fades of its own (a screen drawn as a preview).
    private static float base = 1;

    private GuiFade() {}

    public static void set(float value) {
        change(Mth.clamp(value, 0, 1) * base);
    }

    /** Sets what every fade is set within, until it's set back to 1. */
    public static void setBase(float value) {
        base = Mth.clamp(value, 0, 1);
        change(base);
    }

    private static void change(float value) {
        if (value == alpha) return;
        flush();
        alpha = value;
        RenderSystem.setShaderColor(1, 1, 1, alpha);
    }

    /** The fade now, for a part that fades further on its own (multiplied in, then put back). */
    public static float get() {
        return alpha;
    }

    public static void reset() {
        change(base);
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
