package net.ixdarklord.glazedmenu.api.theme;

import net.ixdarklord.glazedmenu.api.config.ConfigColorScheme;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Client only: an animated effect for config screens, drawn at any of three layers. Register it with
 * {@link ConfigEffects#register} and name its id in a theme ({@link ConfigTheme.Builder#effects}, or
 * {@code ConfigBuilder.effects} for one config):
 * <pre>{@code
 * // Client setup:
 * ConfigEffects.register(Identifier.fromNamespaceAndPath("mymod", "snow"), new SnowEffect());
 * // Anywhere, even common code:
 * Config.builder("mymod", ConfigScope.CLIENT).effects(Identifier.fromNamespaceAndPath("mymod", "snow"));
 * }</pre>
 * Every method is optional. They run every frame on the render thread, so they should be cheap; an effect that throws is
 * logged once and switched off. Players can turn every effect off, and the Fast graphics preset always does.
 */
public interface ConfigEffect {
    /** Behind the panels, over the theme's background (its texture, or the blurred panorama or world). */
    default void extractBackground(GuiGraphicsExtractor graphics, Context context) {}

    /** Over the whole screen, panels and widgets included, but under tooltips. */
    default void extractForeground(GuiGraphicsExtractor graphics, Context context) {}

    /** Over one widget, just after it's drawn: buttons, toggles, sliders, text fields, cards and popup panels. */
    default void extractWidget(GuiGraphicsExtractor graphics, WidgetContext context) {}

    /**
     * The screen being drawn.
     *
     * @param mouseX      the mouse, in GUI pixels; -1 (both) while a popup redraws the page under it
     * @param time        seconds since the game started, for animating without keeping state
     * @param partialTick how far between ticks this frame is
     * @param inWorld     whether a world is open (the screen shows it, blurred)
     */
    record Context(int width, int height, int mouseX, int mouseY, float time, float partialTick, ConfigTheme theme,
                   ConfigColorScheme colors, ConfigTheme.Mode mode, boolean inWorld) {
        /** The accent color of the current mode, ARGB. */
        public int accent() {
            return this.colors.accent();
        }

        /** Whether the mouse is over the screen (not a popup's redraw of the page under it). */
        public boolean hasMouse() {
            return this.mouseX >= 0 && this.mouseY >= 0;
        }
    }

    /** One widget, its bounds in GUI pixels as drawn, and its state. */
    record WidgetContext(WidgetKind kind, int x, int y, int width, int height, boolean hovered, boolean focused, boolean active,
                         Context screen) {}

    /** The kinds of widgets effects are told about. */
    enum WidgetKind {
        /** A flat button, including icon buttons. */
        BUTTON,
        /** An on/off switch. */
        TOGGLE,
        /** A number slider. */
        SLIDER,
        /** A text or number field. */
        TEXT_FIELD,
        /** A navigation card on a mod's main config screen. */
        CARD,
        /** A popup's panel (category popups, confirmations, the color picker...). */
        POPUP
    }
}
