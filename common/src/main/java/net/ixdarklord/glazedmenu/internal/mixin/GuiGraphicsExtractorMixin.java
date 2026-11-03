package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.style.FillBatch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

// Lets a FillBatch be handed to the GUI as one element. The extractor's own scissor stack is a private class, so its
// scissors are followed here as it pushes and pops them, the same way it does.
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements FillBatch.Sink {
    @Shadow @Final private GuiRenderState guiRenderState;
    @Shadow @Final private Matrix3x2fStack pose;

    @Unique
    private final Deque<ScreenRectangle> glazedmenu$scissors = new ArrayDeque<>();

    @Inject(method = "enableScissor", at = @At("TAIL"))
    private void glazedmenu$pushScissor(int x0, int y0, int x1, int y1, CallbackInfo ci) {
        ScreenRectangle rectangle = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformAxisAligned(this.pose);
        ScreenRectangle last = this.glazedmenu$scissors.peekLast();
        this.glazedmenu$scissors.addLast(last == null ? rectangle : Objects.requireNonNullElse(rectangle.intersection(last), ScreenRectangle.empty()));
    }

    @Inject(method = "disableScissor", at = @At("TAIL"))
    private void glazedmenu$popScissor(CallbackInfo ci) {
        this.glazedmenu$scissors.pollLast();
    }

    @Override
    public @Nullable ScreenRectangle glazedmenu$scissor() {
        return this.glazedmenu$scissors.peekLast();
    }

    @Override
    public void glazedmenu$submit(GuiElementRenderState element) {
        this.guiRenderState.addGuiElement(element);
    }
}
