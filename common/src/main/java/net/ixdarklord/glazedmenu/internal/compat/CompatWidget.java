package net.ixdarklord.glazedmenu.internal.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

// A widget written against Minecraft 26.1's widget calls.
public abstract class CompatWidget extends AbstractWidget {
    public CompatWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    protected abstract void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    // --- 1.21.1's calls, passed on as Minecraft 26.1's ---

    @Override
    protected final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        this.extractWidgetRenderState(GuiGraphicsExtractor.of(graphics), mouseX, mouseY, a);
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.mouseClicked(CompatInput.click(mouseX, mouseY, button), CompatInput.doubleClick());
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public final void onClick(double mouseX, double mouseY) {
        this.onClick(CompatInput.at(mouseX, mouseY), CompatInput.doubleClick());
    }

    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        super.onClick(event.x(), event.y());
    }

    @Override
    protected final void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        this.onDrag(CompatInput.at(mouseX, mouseY), dragX, dragY);
    }

    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        super.onDrag(event.x(), event.y(), dragX, dragY);
    }

    @Override
    public final void onRelease(double mouseX, double mouseY) {
        this.onRelease(CompatInput.at(mouseX, mouseY));
    }

    public void onRelease(MouseButtonEvent event) {
        super.onRelease(event.x(), event.y());
    }

    @Override
    protected final boolean isValidClickButton(int button) {
        return this.isValidClickButton(new MouseButtonInfo(button, CompatInput.modifiers()));
    }

    protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
        return super.isValidClickButton(buttonInfo.button());
    }

    @Override
    public final boolean keyPressed(int key, int scancode, int modifiers) {
        return this.keyPressed(CompatInput.key(key, scancode, modifiers));
    }

    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    /** 1.21.1 has no mouse cursor shapes. */
    protected void handleCursor(GuiGraphicsExtractor graphics) {}
}
