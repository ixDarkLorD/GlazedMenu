package net.ixdarklord.glazedmenu.internal.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

// A scrolling list written against Minecraft 26.1's list calls. 1.21.1's lists give every row the same height; this one
// gives each row its own (26.1's addEntry(entry, height)), laying the rows out top to bottom, and each row knows its
// own bounds.
public abstract class CompatList<E extends CompatList.Entry<E>> extends ContainerObjectSelectionList<E> {
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller");
    private static final ResourceLocation SCROLLER_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller_background");
    private static final int SCROLLBAR_WIDTH = 6;

    private @Nullable E hoveredEntry;

    protected CompatList(Minecraft minecraft, int width, int height, int y, int defaultEntryHeight) {
        super(minecraft, width, height, y, defaultEntryHeight);
    }

    // --- Entries and layout ---

    @Override
    protected int addEntry(E entry) {
        return this.addEntry(entry, this.itemHeight);
    }

    protected int addEntry(E entry, int height) {
        entry.height = height;
        int index = super.addEntry(entry);
        this.repositionEntries();
        return index;
    }

    @Override
    public void replaceEntries(Collection<E> entries) {
        this.clearEntries();
        for (E entry : entries) this.addEntry(entry);
    }

    @Override
    public void clearEntries() {
        super.clearEntries();
    }

    @Override
    protected boolean removeEntry(E entry) {
        boolean removed = super.removeEntry(entry);
        if (removed) this.repositionEntries();
        return removed;
    }

    /** Places every row under the one before it, for the current position and scroll. */
    protected void repositionEntries() {
        int y = this.getY() + 2 - (int) this.getScrollAmount();
        int left = this.getRowLeft();
        int width = this.getRowWidth();
        for (E child : this.children()) {
            child.x = left;
            child.y = y;
            child.width = width;
            y += child.height;
        }
    }

    public void updateSizeAndPosition(int width, int height, int x, int y) {
        this.setSize(width, height);
        this.setPosition(x, y);
        this.repositionEntries();
        this.clampScrollAmount();
    }

    @Override
    public void updateSizeAndPosition(int width, int height, int y) {
        this.updateSizeAndPosition(width, height, this.getX(), y);
    }

    @Override
    public int getRowLeft() {
        return this.getX() + this.width / 2 - this.getRowWidth() / 2;
    }

    @Override
    public int getRowRight() {
        return this.getRowLeft() + this.getRowWidth();
    }

    @Override
    protected int getRowTop(int index) {
        return this.children().get(index).y;
    }

    @Override
    protected int getRowBottom(int index) {
        E child = this.children().get(index);
        return child.y + child.height;
    }

    /** The rows' total height. */
    protected int contentHeight() {
        int total = 0;
        for (E child : this.children()) total += child.height;
        return total + 4;
    }

    @Override
    protected int getMaxPosition() {
        return this.contentHeight();
    }

    // --- Scrolling ---

    @Override
    public int getMaxScroll() {
        return Math.max(0, this.contentHeight() - this.height);
    }

    public int maxScrollAmount() {
        return this.getMaxScroll();
    }

    public double scrollAmount() {
        return this.getScrollAmount();
    }

    @Override
    public void setClampedScrollAmount(double scroll) {
        super.setClampedScrollAmount(scroll);
        this.repositionEntries();
    }

    protected boolean scrollable() {
        return this.getMaxScroll() > 0;
    }

    protected int scrollBarX() {
        return this.getRowRight() + SCROLLBAR_WIDTH + 2;
    }

    @Override
    protected final int getScrollbarPosition() {
        return this.scrollBarX();
    }

    protected int scrollerHeight() {
        return Mth.clamp((int) ((float) (this.height * this.height) / this.contentHeight()), 32, this.height - 8);
    }

    protected int scrollBarY() {
        int max = this.getMaxScroll();
        return max == 0 ? this.getY() : Math.max(this.getY(), (int) this.getScrollAmount() * (this.height - this.scrollerHeight()) / max + this.getY());
    }

    private boolean isOverScrollbar(double x, double y) {
        return this.scrollable() && x >= this.scrollBarX() && x <= this.scrollBarX() + SCROLLBAR_WIDTH && y >= this.getY() && y < this.getBottom();
    }

    @Override
    protected void ensureVisible(E entry) {
        int topDelta = entry.y - this.getY() - 2;
        if (topDelta < 0) this.setScrollAmount(this.getScrollAmount() + topDelta);
        int bottomDelta = this.getBottom() - entry.y - entry.height - 2;
        if (bottomDelta < 0) this.setScrollAmount(this.getScrollAmount() - bottomDelta);
    }

    @Override
    protected void centerScrollOn(E entry) {
        int y = 0;
        for (E child : this.children()) {
            if (child == entry) {
                y += child.height / 2;
                break;
            }
            y += child.height;
        }
        this.setScrollAmount(y - this.height / 2.0);
    }

    // --- Input ---

    /** The row under a point, if any. */
    protected @Nullable E entryAt(double x, double y) {
        for (E child : this.children()) {
            if (child.isMouseOver(x, y)) return child;
        }
        return null;
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.mouseClicked(CompatInput.click(mouseX, mouseY, button), CompatInput.doubleClick());
    }

    // Rows get every mouse button (a selector steps back on right-click); only the left one drags the scrollbar.
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        this.repositionEntries();
        this.updateScrollingState(mouseX, mouseY, event.button());
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        E entry = this.entryAt(mouseX, mouseY);
        if (entry != null && entry.mouseClicked(event, doubleClick)) {
            E focused = this.getFocused();
            if (focused != entry && focused instanceof ContainerEventHandler container) container.setFocused(null);
            this.setFocused(entry);
            this.setDragging(true);
            return true;
        }
        return event.button() == 0 && this.isOverScrollbar(mouseX, mouseY);
    }

    @Override
    public final boolean keyPressed(int key, int scancode, int modifiers) {
        return this.keyPressed(CompatInput.key(key, scancode, modifiers));
    }

    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    // --- Drawing ---

    @Override
    public final void renderWidget(GuiGraphics raw, int mouseX, int mouseY, float a) {
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        this.repositionEntries();
        this.hoveredEntry = this.isMouseOver(mouseX, mouseY) ? this.entryAt(mouseX, mouseY) : null;
        this.extractListBackground(graphics);
        graphics.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
        for (E child : this.children()) {
            if (child.y + child.height >= this.getY() && child.y <= this.getBottom()) {
                child.extractContent(graphics, mouseX, mouseY, child == this.hoveredEntry, a);
            }
        }
        graphics.disableScissor();
        this.extractListSeparators(graphics);
        this.extractScrollbar(graphics, mouseX, mouseY);
    }

    @Override
    protected final void renderListBackground(GuiGraphics graphics) {
        this.extractListBackground(GuiGraphicsExtractor.of(graphics));
    }

    @Override
    protected final void renderListSeparators(GuiGraphics graphics) {
        this.extractListSeparators(GuiGraphicsExtractor.of(graphics));
    }

    protected void extractListBackground(GuiGraphicsExtractor graphics) {
        super.renderListBackground(graphics.raw());
    }

    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
        super.renderListSeparators(graphics.raw());
    }

    /** Vanilla's scrollbar, while the rows don't fit. */
    protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.scrollable()) return;
        RenderSystem.enableBlend();
        graphics.raw().blitSprite(SCROLLER_BACKGROUND_SPRITE, this.scrollBarX(), this.getY(), SCROLLBAR_WIDTH, this.getHeight());
        graphics.raw().blitSprite(SCROLLER_SPRITE, this.scrollBarX(), this.scrollBarY(), SCROLLBAR_WIDTH, this.scrollerHeight());
        RenderSystem.disableBlend();
    }

    @Override
    protected @Nullable E getHovered() {
        return this.hoveredEntry;
    }

    @Override
    public NarratableEntry.NarrationPriority narrationPriority() {
        if (this.isFocused()) return NarratableEntry.NarrationPriority.FOCUSED;
        return this.hoveredEntry != null ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
    }

    /** A row, with its own height; the list sets its bounds as it lays the rows out. */
    public abstract static class Entry<E extends Entry<E>> extends ContainerObjectSelectionList.Entry<E> {
        int x;
        int y;
        int width;
        int height;

        public int getX() {
            return this.x;
        }

        public int getY() {
            return this.y;
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }

        public int getRight() {
            return this.x + this.width;
        }

        public int getBottom() {
            return this.y + this.height;
        }

        /** Draws the row within its bounds. */
        public abstract void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a);

        @Override
        public final void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
                                 boolean hovering, float a) {
            this.extractContent(GuiGraphicsExtractor.of(graphics), mouseX, mouseY, hovering, a);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }

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
}
