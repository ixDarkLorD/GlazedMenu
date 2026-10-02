package net.ixdarklord.glazedmenu.internal.gui.editor;

import net.ixdarklord.glazedmenu.api.editor.EditSlot;
import net.ixdarklord.glazedmenu.api.config.type.ColorType;
import net.ixdarklord.glazedmenu.internal.gui.ColorPickerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// A hex text box with a swatch of the color at its right end (a checkerboard shows through translucent colors);
// clicking the swatch opens the color picker.
public final class ColorEditor extends TextEditor<Integer> {
    private static final int SWATCH = 12;

    public ColorEditor(EditSlot<Integer> slot, int width, int height) {
        super(slot, width, height);
        this.box.setTooltip(Tooltip.create(Component.translatableWithFallback("glazedmenu.color.hint", "Click the swatch to pick a color")));
    }

    private void openPicker() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean alpha = this.slot.type() instanceof ColorType color && color.hasAlpha();
        minecraft.gui.setScreen(new ColorPickerScreen(minecraft.gui.screen(), this.slot.name(), this.slot.get(), alpha, color -> {
            this.slot.set(color);
            this.refresh();
        }));
    }

    @Override
    protected StyledEditBox createBox(int width, int height) {
        return new StyledEditBox(Minecraft.getInstance().font, width, height, this.slot.name()) {
            private int swatchLeft() {
                return this.getRight() - 4 - SWATCH;
            }

            @Override
            public void onClick(MouseButtonEvent event, boolean doubleClick) {
                if (this.active && event.x() >= this.swatchLeft() - 1) {
                    ColorEditor.this.openPicker();
                    return;
                }
                super.onClick(event, doubleClick);
            }

            @Override
            public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
                int x0 = this.swatchLeft();
                int x1 = x0 + SWATCH;
                int y0 = this.getY() + (this.getHeight() - SWATCH) / 2;
                int y1 = y0 + SWATCH;
                int half = SWATCH / 2;
                graphics.fill(x0, y0, x1, y1, 0xFFFFFFFF);
                graphics.fill(x0, y0, x0 + half, y0 + half, 0xFFBFBFBF);
                graphics.fill(x0 + half, y0 + half, x1, y1, 0xFFBFBFBF);
                graphics.fill(x0, y0, x1, y1, ColorEditor.this.slot.get());
                boolean hovered = this.active && mouseX >= x0 - 1 && mouseX <= x1 && mouseY >= y0 - 1 && mouseY <= y1;
                ConfigStyle.outline(graphics, x0 - 1, y0 - 1, SWATCH + 2, SWATCH + 2, hovered ? ConfigStyle.accent() : 0xFF000000);
            }
        };
    }
}
