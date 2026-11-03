package net.ixdarklord.glazedmenu.internal.style;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.Arrays;

/**
 * Many plain fills (each a color, or a gradient top to bottom) handed to the GUI as one element: the same quads, in the
 * same order, as drawing them one by one. The GUI places each element it's given above the ones it overlaps by
 * searching what's already there, so thousands of fills side by side (a gradient a column at a time, the waves) cost it
 * a search each, over everything drawn before; one element costs one search.
 * <p>
 * Made at the pose and scissor in force when it's started, then filled, then {@link #submit() submitted}.
 */
public final class FillBatch implements GuiElementRenderState {
    private final GuiGraphicsExtractor graphics;
    private final Matrix3x2f pose;
    private final @Nullable ScreenRectangle scissor;
    // Four numbers a quad (left, top, right, bottom) and two colors (its top's and its bottom's).
    private int[] edges = new int[64];
    private int[] colors = new int[32];
    // The colors as given, before the fade: kept only by a batch that's baked (bake()).
    private int[] given;
    private int count;
    private int left = Integer.MAX_VALUE;
    private int top = Integer.MAX_VALUE;
    private int right = Integer.MIN_VALUE;
    private int bottom = Integer.MIN_VALUE;
    private @Nullable ScreenRectangle bounds;

    private FillBatch(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
        this.pose = new Matrix3x2f(graphics.pose());
        this.scissor = ((Sink) graphics).glazedmenu$scissor();
    }

    /** A batch drawn at the pose and scissor {@code graphics} has now. */
    public static FillBatch start(GuiGraphicsExtractor graphics) {
        return new FillBatch(graphics);
    }

    /** As {@link #start(GuiGraphicsExtractor)}, for a batch whose fills are kept with {@link #bake()} to be drawn again. */
    public static FillBatch baking(GuiGraphicsExtractor graphics) {
        FillBatch batch = new FillBatch(graphics);
        batch.given = new int[batch.colors.length];
        return batch;
    }

    /**
     * The fills so far, kept to be drawn again just as they are ({@link #add(Baked)}), for what's the same from one frame
     * to the next: they're worked out once, not in every frame. Only for a batch started with {@link #baking}.
     */
    public Baked bake() {
        if (this.given == null) throw new IllegalStateException("Not a baking batch");
        return new Baked(Arrays.copyOf(this.edges, this.count * 4), Arrays.copyOf(this.given, this.count * 2), this.count,
                this.left, this.top, this.right, this.bottom);
    }

    /** Fills kept by {@link #bake()}, added again in the same order (at this batch's pose and scissor, and the fade now). */
    public void add(Baked baked) {
        // Unfaded (as nearly always), copied across whole.
        if (GuiFade.get() >= 1 && this.given == null && baked.count > 0) {
            int needed = this.count + baked.count;
            if (needed * 4 > this.edges.length) {
                int size = Math.max(needed, this.edges.length / 2);
                this.edges = Arrays.copyOf(this.edges, size * 4);
                this.colors = Arrays.copyOf(this.colors, size * 2);
            }
            System.arraycopy(baked.edges, 0, this.edges, this.count * 4, baked.count * 4);
            System.arraycopy(baked.colors, 0, this.colors, this.count * 2, baked.count * 2);
            this.count = needed;
            this.left = Math.min(this.left, baked.left);
            this.top = Math.min(this.top, baked.top);
            this.right = Math.max(this.right, baked.right);
            this.bottom = Math.max(this.bottom, baked.bottom);
            return;
        }
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
            if (this.given != null) this.given = Arrays.copyOf(this.given, this.given.length * 2);
        }
        int at = this.count * 4;
        this.edges[at] = x0;
        this.edges[at + 1] = y0;
        this.edges[at + 2] = x1;
        this.edges[at + 3] = y1;
        // Faded as the GUI fades a fill (GuiFadeMixin).
        this.colors[this.count * 2] = GuiFade.apply(topColor);
        this.colors[this.count * 2 + 1] = GuiFade.apply(bottomColor);
        if (this.given != null) {
            this.given[this.count * 2] = topColor;
            this.given[this.count * 2 + 1] = bottomColor;
        }
        this.count++;
        this.left = Math.min(this.left, Math.min(x0, x1));
        this.top = Math.min(this.top, Math.min(y0, y1));
        this.right = Math.max(this.right, Math.max(x0, x1));
        this.bottom = Math.max(this.bottom, Math.max(y0, y1));
    }

    /** Hands what was filled to the GUI; nothing, if nothing was. */
    public void submit() {
        if (this.count == 0) return;
        ScreenRectangle area = new ScreenRectangle(this.left, this.top, this.right - this.left, this.bottom - this.top).transformMaxBounds(this.pose);
        this.bounds = this.scissor != null ? this.scissor.intersection(area) : area;
        ((Sink) this.graphics).glazedmenu$submit(this);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        Matrix3x2fc pose = this.pose;
        for (int i = 0; i < this.count; i++) {
            int at = i * 4;
            int x0 = this.edges[at];
            int y0 = this.edges[at + 1];
            int x1 = this.edges[at + 2];
            int y1 = this.edges[at + 3];
            int topColor = this.colors[i * 2];
            int bottomColor = this.colors[i * 2 + 1];
            consumer.addVertexWith2DPose(pose, x0, y0).setColor(topColor);
            consumer.addVertexWith2DPose(pose, x0, y1).setColor(bottomColor);
            consumer.addVertexWith2DPose(pose, x1, y1).setColor(bottomColor);
            consumer.addVertexWith2DPose(pose, x1, y0).setColor(topColor);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return this.scissor;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return this.bounds;
    }

    /** What the GUI's extractor gives a batch (GuiGraphicsExtractorMixin): its scissor now, and a way to add an element. */
    public interface Sink {
        @Nullable ScreenRectangle glazedmenu$scissor();

        void glazedmenu$submit(GuiElementRenderState element);
    }
}
