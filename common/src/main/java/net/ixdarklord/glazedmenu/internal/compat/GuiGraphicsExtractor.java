package net.ixdarklord.glazedmenu.internal.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

// Minecraft 26.1's GUI drawing calls, on 1.20.1's GuiGraphics, so the screens read the same on every version. 1.20.1
// draws as it's called, so a "stratum" is just what's drawn after the last flush.
public final class GuiGraphicsExtractor {
    private static @Nullable GuiGraphicsExtractor last;

    private final GuiGraphics raw;
    private final Pose pose;

    private GuiGraphicsExtractor(GuiGraphics raw) {
        this.raw = raw;
        this.pose = new Pose(raw.pose());
    }

    /** The 26.1-style view of a GuiGraphics (one per frame's graphics, reused). */
    public static GuiGraphicsExtractor of(GuiGraphics raw) {
        GuiGraphicsExtractor cached = last;
        if (cached != null && cached.raw == raw) return cached;
        return last = new GuiGraphicsExtractor(raw);
    }

    public GuiGraphics raw() {
        return this.raw;
    }

    public Pose pose() {
        return this.pose;
    }

    public int guiWidth() {
        return this.raw.guiWidth();
    }

    public int guiHeight() {
        return this.raw.guiHeight();
    }

    // --- Shapes ---

    public void fill(int x0, int y0, int x1, int y1, int color) {
        this.raw.fill(x0, y0, x1, y1, color);
    }

    public void fillGradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        this.raw.fillGradient(x0, y0, x1, y1, top, bottom);
    }

    /** A text selection's inverted box. */
    public void textHighlight(int x0, int y0, int x1, int y1, boolean focused) {
        this.raw.fill(RenderType.guiTextHighlight(), x0, y0, x1, y1, 0xFF0000FF);
    }

    // --- Text: 1.20.1 draws a nearly transparent color opaque, so those are skipped ---

    public void text(Font font, @Nullable String text, int x, int y, int color, boolean shadow) {
        if (text != null && visible(color)) this.raw.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        if (visible(color)) this.raw.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        if (visible(color)) this.raw.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, @Nullable String text, int x, int y, int color) {
        this.text(font, text, x, y, color, true);
    }

    public void text(Font font, Component text, int x, int y, int color) {
        this.text(font, text, x, y, color, true);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
        this.text(font, text, x, y, color, true);
    }

    private static boolean visible(int color) {
        return (color & 0xFC000000) != 0;
    }

    // --- Textures (the pipeline is 26.1's; 1.20.1 blends them all alike) ---

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                     int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        if ((color >>> 24) == 0) return;
        this.tint(color);
        this.raw.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        this.untint();
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                     int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        this.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, 0xFFFFFFFF);
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                     int textureWidth, int textureHeight, int color) {
        this.blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color);
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        this.blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, 0xFFFFFFFF);
    }

    public void blitSprite(Object pipeline, ResourceLocation sprite, int x, int y, int width, int height, int color) {
        if ((color >>> 24) == 0) return;
        this.tint(color);
        // 1.20.1 has no GUI sprite atlas: a sprite is its own texture under textures/gui/sprites/, stretched.
        ResourceLocation texture = new ResourceLocation(sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
        int[] size = net.ixdarklord.glazedmenu.internal.style.ConfigStyle.textureSize(texture);
        this.raw.blit(texture, x, y, width, height, 0, 0, size[0], size[1], size[0], size[1]);
        this.untint();
    }

    public void blitSprite(Object pipeline, ResourceLocation sprite, int x, int y, int width, int height) {
        this.blitSprite(pipeline, sprite, x, y, width, height, 0xFFFFFFFF);
    }

    private void tint(int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        this.raw.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
    }

    private void untint() {
        this.raw.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    // --- Clipping and layers ---

    /** Clips to a box given in the current transform (1.20.1's scissor ignores the transform, 26.1's doesn't). */
    public void enableScissor(int x0, int y0, int x1, int y1) {
        Matrix4f matrix = this.raw.pose().last().pose();
        Vector3f a = matrix.transformPosition(new Vector3f(x0, y0, 0));
        Vector3f b = matrix.transformPosition(new Vector3f(x1, y1, 0));
        this.raw.enableScissor(Math.round(Math.min(a.x, b.x)), Math.round(Math.min(a.y, b.y)), Math.round(Math.max(a.x, b.x)), Math.round(Math.max(a.y, b.y)));
    }

    public void disableScissor() {
        this.raw.disableScissor();
    }

    /** What's drawn next goes over what came before. */
    public void nextStratum() {
        this.raw.flush();
    }

    /** 1.20.1 has no menu blur. */
    public void blurBeforeThisStratum() {}

    /** 1.20.1 has no mouse cursor shapes. */
    public void requestCursor(CursorTypes cursor) {}

    // --- Tooltips, drawn by the screen after everything else ---

    public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y) {
        this.setTooltipForNextFrame(font, lines, DefaultTooltipPositioner.INSTANCE, x, y, false);
    }

    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        this.setTooltipForNextFrame(font, font.split(text, Math.max(this.guiWidth() / 2, 200)), x, y);
    }

    public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, ClientTooltipPositioner positioner, int x, int y, boolean focused) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null || lines.isEmpty()) return;
        screen.setTooltipForNextRenderPass(lines, (screenWidth, screenHeight, mouseX, mouseY, width, height) ->
                positioner.positionTooltip(screenWidth, screenHeight, x, y, width, height), true);
    }

    /** 26.1's 2D transform stack, on 1.20.1's PoseStack. */
    public static final class Pose {
        private final PoseStack stack;

        Pose(PoseStack stack) {
            this.stack = stack;
        }

        public Pose pushMatrix() {
            this.stack.pushPose();
            return this;
        }

        public Pose popMatrix() {
            this.stack.popPose();
            return this;
        }

        public Pose translate(float x, float y) {
            this.stack.translate(x, y, 0);
            return this;
        }

        public Pose scale(float x, float y) {
            this.stack.scale(x, y, 1);
            return this;
        }

        public Pose scale(float scale) {
            return this.scale(scale, scale);
        }

        public Pose rotate(float radians) {
            this.stack.mulPose(Axis.ZP.rotation(radians));
            return this;
        }
    }
}
