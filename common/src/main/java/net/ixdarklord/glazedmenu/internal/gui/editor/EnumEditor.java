package net.ixdarklord.glazedmenu.internal.gui.editor;

import net.ixdarklord.glazedmenu.api.editor.EditSlot;
import net.ixdarklord.glazedmenu.api.editor.ValueEditor;
import net.ixdarklord.glazedmenu.api.config.type.EnumType;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.DropdownScreen;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.CommonComponents;

// A selector between arrows: its arrows step to the previous or next constant, and its middle opens a dropdown of them
// all. Enter steps forward; right-click or shift steps back.
public final class EnumEditor<E extends Enum<E>> implements ValueEditor {
    private static final ConfigIcons.Icon LEFT = ConfigIcons.CHEVRON_LEFT;

    private final EditSlot<E> slot;
    private final EnumType<E> type;
    private final CycleButton button;

    public EnumEditor(EditSlot<E> slot, int width, int height) {
        this.slot = slot;
        this.type = (EnumType<E>) slot.type();
        this.button = new CycleButton(width, height);
        this.refresh();
    }

    @Override
    public FlatButton widget() {
        return this.button;
    }

    @Override
    public void refresh() {
        this.button.setMessage(this.type.displayName(this.slot.get()));
    }

    private void step(boolean backwards) {
        this.slot.set(this.type.cycle(this.slot.get(), backwards));
        this.refresh();
    }

    // Every constant in a dropdown under the box; picking one sets it.
    private void openDropdown() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new DropdownScreen<>(minecraft.gui.screen(), this.button.getX(), this.button.getY(), this.button.getWidth(), this.button.getHeight(),
                this.type.constants(), this.slot.get(), this.type::displayName, this.type::description, value -> {
                    this.slot.set(value);
                    this.refresh();
                }));
    }

    private final class CycleButton extends FlatButton {
        // How wide each arrow's clickable end is.
        private static final int ARROW_ZONE = 16;

        CycleButton(int width, int height) {
            super(width, height, CommonComponents.EMPTY, button -> {});
        }

        @Override
        public void onPress(InputWithModifiers input) {
            if (input instanceof MouseButtonEvent event && event.button() == 0) {
                if (event.x() < this.getX() + ARROW_ZONE) EnumEditor.this.step(true);
                else if (event.x() >= this.getRight() - ARROW_ZONE) EnumEditor.this.step(false);
                else EnumEditor.this.openDropdown();
                return;
            }
            EnumEditor.this.step(input.hasShiftDown() || input instanceof MouseButtonEvent);
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
            return buttonInfo.button() == 0 || buttonInfo.button() == 1;
        }

        @Override
        protected void extractButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            super.extractButton(graphics, mouseX, mouseY, a);
            // Each arrow lights up while the mouse is over its end, the one it would step toward.
            boolean hovered = this.active && this.isHovered();
            int y = this.getY() + (this.getHeight() - LEFT.height()) / 2;
            LEFT.draw(graphics, this.getX() + 5, y, this.arrowColor(hovered && mouseX < this.getX() + ARROW_ZONE));
            ConfigIcons.CHEVRON.draw(graphics, this.getRight() - 5 - ConfigIcons.CHEVRON.width(), y,
                    this.arrowColor(hovered && mouseX >= this.getRight() - ARROW_ZONE));
        }

        private int arrowColor(boolean lit) {
            if (!this.active) return ConfigStyle.colors().textMuted();
            return lit || this.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim();
        }
    }
}
