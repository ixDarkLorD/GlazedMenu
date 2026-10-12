package net.ixdarklord.glazedmenu.internal.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

// Minecraft 26.1's calls on vanilla screens and widgets, made the 1.21.1 way.
public final class Compat {
    private Compat() {}

    /** Draws a widget or screen. */
    public static void extractRenderState(Renderable renderable, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        renderable.render(graphics.raw(), mouseX, mouseY, a);
    }

    /** Draws a screen's background. */
    public static void extractBackground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        screen.renderBackground(graphics.raw(), mouseX, mouseY, a);
    }

    /** Lays a screen out for a size. */
    public static void init(Screen screen, int width, int height) {
        screen.init(Minecraft.getInstance(), width, height);
    }

    public static void resize(Screen screen, int width, int height) {
        screen.resize(Minecraft.getInstance(), width, height);
    }

    /** A click handed to a widget or screen. */
    public static boolean mouseClicked(GuiEventListener listener, MouseButtonEvent event, boolean doubleClick) {
        return listener.mouseClicked(event.x(), event.y(), event.button());
    }

    /** The key a key press is of. */
    public static InputConstants.Key getKey(KeyEvent event) {
        return InputConstants.getKey(event.key(), event.scancode());
    }

    /** Whether a key press is of a key bind's key. */
    public static boolean matches(KeyMapping mapping, KeyEvent event) {
        return mapping.matches(event.key(), event.scancode());
    }
}
