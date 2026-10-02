package net.ixdarklord.glazedmenu.internal.gui.editor;

import net.ixdarklord.glazedmenu.api.editor.EditSlot;
import net.ixdarklord.glazedmenu.api.editor.ValueEditor;
import net.ixdarklord.glazedmenu.internal.style.ToggleSwitch;

// A sliding switch.
public final class BooleanEditor implements ValueEditor {
    private final ToggleSwitch toggle;

    public BooleanEditor(EditSlot<Boolean> slot, int width, int height) {
        this.toggle = new ToggleSwitch(width, height, slot::get, pressed -> slot.set(!slot.get()));
    }

    @Override
    public ToggleSwitch widget() {
        return this.toggle;
    }

    @Override
    public void refresh() {
        // The switch reads the slot as it draws.
    }
}
