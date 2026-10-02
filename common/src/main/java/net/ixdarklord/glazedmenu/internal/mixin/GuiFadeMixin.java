package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.style.GuiFade;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Fades what's drawn while a Glazed Menu screen fades in or out: the colors of fills, textures and text all pass through
// these, so scaling their alpha here fades everything, vanilla widgets included.
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiFadeMixin {
    @ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int glazedmenu$fadeFill(int color) {
        return GuiFade.apply(color);
    }

    @ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true)
    private Integer glazedmenu$fadeFillEnd(Integer color) {
        return color == null ? null : GuiFade.apply(color);
    }

    @ModifyVariable(method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIFFFFI)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int glazedmenu$fadeBlit(int color) {
        return GuiFade.apply(color);
    }

    @ModifyVariable(method = "innerTiledBlit", at = @At("HEAD"), argsOnly = true, ordinal = 6)
    private int glazedmenu$fadeTiledBlit(int color) {
        return GuiFade.apply(color);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int glazedmenu$fadeText(int color) {
        return GuiFade.apply(color);
    }
}
