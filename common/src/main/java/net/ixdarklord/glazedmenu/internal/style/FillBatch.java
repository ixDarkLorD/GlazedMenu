package net.ixdarklord.glazedmenu.internal.style;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;

import java.util.Arrays;

/**
 * Many plain fills (each a color, or a gradient top to bottom), kept and drawn together on {@link #submit()}: the same
 * quads, in the same order, as drawing them one by one. On the newer versions a batch is handed to the GUI as one
 * element; this version of the game draws as it goes, so here they're simply drawn in turn, and what a batch adds is
 * that its fills can be {@link #bake() kept} and drawn again without being worked out again.
 */
public final class FillBatch {
    private final GuiGraphicsExtractor graphics;
    // Four numbers a quad (left, top, right, bottom) and two colors (its top's and its bottom's), as given.
    private int[] edges = new int[64];
    private int[] colors = new int[32];
    private int count;

    private FillBatch(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    /** A batch drawn with {@code graphics}. */
    public static FillBatch start(GuiGraphicsExtractor graphics) {
        return new FillBatch(graphics);
    }

    /** As {@link #start(GuiGraphicsExtractor)}, for a batch whose fills are kept with {@link #bake()} to be drawn again. */
    public static FillBatch baking(GuiGraphicsExtractor graphics) {
        return new FillBatch(graphics);
    }

    /** The fills so far, kept to be drawn again just as they are ({@link #add(Baked)}). */
    public Baked bake() {
        return new Baked(Arrays.copyOf(this.edges, this.count * 4), Arrays.copyOf(this.colors, this.count * 2), this.count, 0, 0, 0, 0);
    }

    /** Fills kept by {@link #bake()}, added again in the same order. */
    public void add(Baked baked) {
        for (int i = 0; i < baked.count; i++) {
            int at = i * 4;
            this.add(baked.edges[at], baked.edges[at + 1], baked.edges[at + 2], baked.edges[at + 3], baked.colors[i * 2], baked.colors[i * 2 + 1]);
        }
    }

    /** Fills kept to be drawn again (FillBatch.bake). */
    public record Baked(int[] edges, int[] colors, int count, int left, int top, int right, int bottom) {}

    /** As {@link GuiGraphicsExtractor#fill(int, int, int, int, int)}. */
    public void fill(int x0, int y0, int x1, int y1, int color) {
        this.add(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), color, color);
    }

    /** As {@link GuiGraphicsExtractor#fillGradient(int, int, int, int, int, int)}. */
    public void fillGradient(int x0, int y0, int x1, int y1, int topColor, int bottomColor) {
        this.add(x0, y0, x1, y1, topColor, bottomColor);
    }

    private void add(int x0, int y0, int x1, int y1, int topColor, int bottomColor) {
        // An empty box draws nothing.
        if (x0 == x1 || y0 == y1) return;
        if (this.count * 4 == this.edges.length) {
            this.edges = Arrays.copyOf(this.edges, this.edges.length * 2);
            this.colors = Arrays.copyOf(this.colors, this.colors.length * 2);
        }
        int at = this.count * 4;
        this.edges[at] = x0;
        this.edges[at + 1] = y0;
        this.edges[at + 2] = x1;
        this.edges[at + 3] = y1;
        this.colors[this.count * 2] = topColor;
        this.colors[this.count * 2 + 1] = bottomColor;
        this.count++;
    }

    /** Draws what was filled, in order (faded as any fill is, GuiFadeMixin). */
    public void submit() {
        for (int i = 0; i < this.count; i++) {
            int at = i * 4;
            int top = this.colors[i * 2];
            int bottom = this.colors[i * 2 + 1];
            if (top == bottom) this.graphics.fill(this.edges[at], this.edges[at + 1], this.edges[at + 2], this.edges[at + 3], top);
            else this.graphics.fillGradient(this.edges[at], this.edges[at + 1], this.edges[at + 2], this.edges[at + 3], top, bottom);
        }
        this.count = 0;
    }
}
