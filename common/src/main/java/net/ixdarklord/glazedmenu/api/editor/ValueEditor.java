package net.ixdarklord.glazedmenu.api.editor;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * The widget editing one value in the config screen. The screen positions and draws it, and greys it out when the
 * value can't be edited.
 */
public interface ValueEditor {
    AbstractWidget widget();

    /** Shows the slot's value again after it changed elsewhere (undo, reset, a preset, the server). */
    void refresh();

    /** Whether the player can change the value right now. */
    default void setActive(boolean active) {
        this.widget().active = active;
    }

    /** Builds an editor for a slot, at the given size. */
    @FunctionalInterface
    interface Factory<T> {
        ValueEditor create(EditSlot<T> slot, int width, int height);
    }
}
