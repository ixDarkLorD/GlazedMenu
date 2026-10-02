package net.ixdarklord.glazedmenu.internal.compat;

import net.minecraft.client.gui.Font;

// A text field's blinking cursor, drawn as vanilla's text field draws it (Minecraft 26.1's helper).
public final class TextCursorUtils {
    private TextCursorUtils() {}

    public static boolean isCursorVisible(long millisSinceFocus) {
        return millisSinceFocus / 300 % 2 == 0;
    }

    /** A thin bar before a character. */
    public static void extractInsertCursor(GuiGraphicsExtractor graphics, int x, int y, int color, int height) {
        graphics.fill(x, y - 1, x + 1, y + height, color);
    }

    /** An underscore after the text's end. */
    public static void extractAppendCursor(GuiGraphicsExtractor graphics, Font font, int x, int y, int color, boolean shadow) {
        graphics.text(font, "_", x, y, color, shadow);
    }
}
