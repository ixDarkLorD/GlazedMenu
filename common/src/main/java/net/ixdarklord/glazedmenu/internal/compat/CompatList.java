package net.ixdarklord.glazedmenu.internal.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

// A scrolling list written against Minecraft 26.1's list calls. 1.20.1's lists give every row the same height; this one
// gives each row its own (26.1's addEntry(entry, height)), laying the rows out top to bottom, and each row knows its
// own bounds. 1.20.1's lists aren't widgets: their bounds are x0..x1 and y0..y1, read here as 26.1's position and size.
public abstract class CompatList<E extends CompatList.Entry<E>> extends ContainerObjectSelectionList<E> {
    private static final int SCROLLBAR_WIDTH = 6;

    private @Nullable E hoveredEntry;

    protected CompatList(Minecraft minecraft, int width, int height, int y, int defaultEntryHeight) {
        super(minecraft, width, height, y, y + height, defaultEntryHeight);
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
    }

    // --- 26.1's bounds ---

    public int getX() {
        return this.x0;
    }

    public int getY() {
        return this.y0;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getRight() {
        return this.x1;
    }

    public int getBottom() {
        return this.y1;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        this.x1 = this.x0 + width;
        this.y1 = this.y0 + height;
    }

    public void setPosition(int x, int y) {
        this.x0 = x;
        this.x1 = x + this.width;
        this.y0 = y;
        this.y1 = y + this.height;
    }

    public void clampScrollAmount() {
        this.setScrollAmount(this.getScrollAmount());
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
    public void setScrollAmount(double scroll) {
        super.setScrollAmount(scroll);
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
            if (focused != null && focused != entry) focused.setFocused((net.minecraft.client.gui.components.events.GuiEventListener) null);
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
    public final void render(GuiGraphics raw, int mouseX, int mouseY, float a) {
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

    /** Nothing by default (1.20.1's list background is the dirt texture). */
    protected void extractListBackground(GuiGraphicsExtractor graphics) {}

    protected void extractListSeparators(GuiGraphicsExtractor graphics) {}

    /** A plain scrollbar like 1.20.1's, while the rows don't fit. */
    protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.scrollable()) return;
        int x = this.scrollBarX();
        int top = this.scrollBarY();
        graphics.fill(x, this.getY(), x + SCROLLBAR_WIDTH, this.getBottom(), 0xFF000000);
        graphics.fill(x, top, x + SCROLLBAR_WIDTH, top + this.scrollerHeight(), 0xFF808080);
        graphics.fill(x, top, x + SCROLLBAR_WIDTH - 1, top + this.scrollerHeight() - 1, 0xFFC0C0C0);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return this.mouseScrolled(mouseX, mouseY, 0, delta);
    }

    /** Minecraft 26.1's scroll, with a horizontal amount (1.20.1 has none). */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollY);
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
