package net.ixdarklord.glazedmenu.internal.gui.editor;

import net.ixdarklord.glazedmenu.api.editor.EditSlot;
import net.ixdarklord.glazedmenu.api.editor.ValueEditor;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.minecraft.client.Minecraft;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;

// A text box for any type, through the type's parse/format. Text that doesn't parse turns red and blocks saving.
public class TextEditor<T> implements ValueEditor {
    protected final EditSlot<T> slot;
    protected final StyledEditBox box;
    private boolean updating;

    public TextEditor(EditSlot<T> slot, int width, int height) {
        this.slot = slot;
        this.box = this.createBox(width, height);
        this.box.setMaxLength(32767);
        this.refresh();
        this.box.setResponder(this::onEdited);
    }

    protected StyledEditBox createBox(int width, int height) {
        return new StyledEditBox(Minecraft.getInstance().font, width, height, this.slot.name());
    }

    private void onEdited(String text) {
        if (this.updating) return;
        ValidationResult<T> result = this.slot.type().parse(text);
        if (result.isOk()) this.slot.set(result.value());
        else this.slot.setInvalid(result.message().orElseThrow());
        this.updateColor();
    }

    private void updateColor() {
        boolean invalid = this.slot.error().isPresent();
        this.box.setTextColor(invalid ? ConfigStyle.colors().error() : ConfigStyle.colors().text());
        this.box.setInvalid(invalid);
    }

    @Override
    public StyledEditBox widget() {
        return this.box;
    }

    @Override
    public void refresh() {
        this.updating = true;
        String text = this.slot.type().format(this.slot.get());
        if (!text.equals(this.box.getValue())) {
            this.box.setValue(text);
            this.box.moveCursorTo(0, false);
        }
        this.updating = false;
        this.updateColor();
    }

    @Override
    public void setActive(boolean active) {
        this.box.active = active;
        this.box.setEditable(active);
    }
}
