package net.ixdarklord.glazedmenu.internal.compat;

import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.ixdarklord.glazedmenu.internal.mixin.PauseScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

// Minecraft 26.1's calls on vanilla screens and widgets, made the 1.20.1 way.
public final class Compat {
    private Compat() {}

    /** Draws a widget or screen. */
    public static void extractRenderState(Renderable renderable, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        renderable.render(graphics.raw(), mouseX, mouseY, a);
    }

    /** Draws a screen's background. */
    public static void extractBackground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        screen.renderBackground(graphics.raw());
    }

    /** Lays a screen out for a size. */
    public static void init(Screen screen, int width, int height) {
        screen.init(Minecraft.getInstance(), width, height);
    }

    public static void resize(Screen screen, int width, int height) {
        screen.resize(Minecraft.getInstance(), width, height);
    }

    /** Whether a pause screen is the menu, not only the word "paused" (1.20.1 keeps that to itself). */
    public static boolean showsPauseMenu(PauseScreen screen) {
        return ((PauseScreenAccessor) screen).glazedmenu$showPauseMenu();
    }

    /** Closes a world's or server's picture; 1.20.1's can't say whether it's closed already, and may only be closed once. */
    public static void close(FaviconTexture picture) {
        try {
            picture.close();
        } catch (IllegalStateException e) {
            // Closed before.
        }
    }
}
