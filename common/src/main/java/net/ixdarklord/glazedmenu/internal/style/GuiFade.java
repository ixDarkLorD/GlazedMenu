package net.ixdarklord.glazedmenu.internal.style;

// A fade for whatever the GUI draws while it's set: every color's alpha is scaled (GuiFadeMixin). Screens set it around
// the parts that fade and put it back after.
public final class GuiFade {
    private static float alpha = 1;

    private GuiFade() {}

    public static void set(float value) {
        alpha = Math.clamp(value, 0, 1);
    }

    public static void reset() {
        alpha = 1;
    }

    /** The color with its alpha scaled by the fade. */
    public static int apply(int color) {
        if (alpha >= 1) return color;
        int scaled = Math.round((color >>> 24) * alpha);
        return scaled << 24 | color & 0xFFFFFF;
    }
}
