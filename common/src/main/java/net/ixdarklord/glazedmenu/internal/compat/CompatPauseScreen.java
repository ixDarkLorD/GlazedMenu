package net.ixdarklord.glazedmenu.internal.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;

// The game's pause screen, for a screen written against Minecraft 26.1's screen calls to extend (as CompatScreen is
// for a screen of its own).
public abstract class CompatPauseScreen extends PauseScreen {
    protected CompatPauseScreen(boolean showPauseMenu) {
        super(showPauseMenu);
        // Minecraft 26.1's screens know the game and its font from the start; 1.21.1's only once they're laid out, which
        // is after they're told they were opened.
        this.minecraft = Minecraft.getInstance();
        this.font = this.minecraft.font;
    }

    // --- Drawing: 26.1 draws the background and the rest in separate calls; 1.21.1's render does both ---

    private boolean skipBackground;

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        GuiGraphicsExtractor extractor = GuiGraphicsExtractor.of(graphics);
        Cursors.begin();
        try {
            this.extractBackground(extractor, mouseX, mouseY, a);
            this.extractRenderState(extractor, mouseX, mouseY, a);
        } finally {
            Cursors.end();
        }
    }

    /** Draws the game's screen without its background. */
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.skipBackground = true;
        try {
            super.render(graphics.raw(), mouseX, mouseY, a);
        } finally {
            this.skipBackground = false;
        }
    }

    @Override
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        if (!this.skipBackground) this.extractBackground(GuiGraphicsExtractor.of(graphics), mouseX, mouseY, a);
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.renderBackground(graphics.raw(), mouseX, mouseY, a);
    }

    protected void extractPanorama(GuiGraphicsExtractor graphics, float a) {
        this.renderPanorama(graphics.raw(), a);
    }

    // --- Input ---

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.mouseClicked(CompatInput.click(mouseX, mouseY, button), CompatInput.doubleClick());
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public final boolean keyPressed(int key, int scancode, int modifiers) {
        return this.keyPressed(CompatInput.key(key, scancode, modifiers));
    }

    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }
}
