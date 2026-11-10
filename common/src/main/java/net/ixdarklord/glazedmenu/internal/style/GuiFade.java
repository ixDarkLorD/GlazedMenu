package net.ixdarklord.glazedmenu.internal.style;

// A fade for whatever the GUI draws while it's set: every color's alpha is scaled (GuiFadeMixin). Screens set it around
// the parts that fade and put it back after.
public final class GuiFade {
    private static float alpha = 1;
    // What every fade is set within: below 1 while something already fading (a page sliding in) draws a part that sets
    // fades of its own (a screen drawn as a preview).
    private static float base = 1;

    private GuiFade() {}

    public static void set(float value) {
        alpha = Math.clamp(value, 0, 1) * base;
    }

    /** Sets what every fade is set within, until it's set back to 1. */
    public static void setBase(float value) {
        base = Math.clamp(value, 0, 1);
        alpha = base;
    }

    /** The fade now, for a part that fades further on its own (multiplied in, then put back). */
    public static float get() {
        return alpha;
    }

    public static void reset() {
        alpha = base;
    }

    /** The color with its alpha scaled by the fade. */
    public static int apply(int color) {
        if (alpha >= 1) return color;
        int scaled = Math.round((color >>> 24) * alpha);
        return scaled << 24 | color & 0xFFFFFF;
    }
}
