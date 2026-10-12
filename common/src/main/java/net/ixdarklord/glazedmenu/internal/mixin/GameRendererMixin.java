package net.ixdarklord.glazedmenu.internal.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.game.GlazedDeathScreen;
import net.ixdarklord.glazedmenu.internal.game.LoadingLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The moment the world has been drawn and nothing is over it yet (no HUD, no menu): where the picture of the player's
// place is taken from, for the loading screens. And the moment the HUD has been drawn over it, before any screen and
// the toasts: where a loading screen's look fades off the world it has just loaded, and where the death screen's band
// closes as the player respawns (on 1.20.1 Forge draws the HUD its own way, so this is hooked after the HUD, not in it).
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    Minecraft minecraft;

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void glazedmenu$snapshot(float partialTick, long finishTimeNano, PoseStack poseStack, CallbackInfo info) {
        LoadingLook.afterLevel();
        GlazedDeathScreen.frame();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V", shift = At.Shift.AFTER))
    private void glazedmenu$overHud(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo info) {
        if (!LoadingLook.fading() && !GlazedDeathScreen.closing()) return;
        GuiGraphics raw = new GuiGraphics(this.minecraft, this.minecraft.renderBuffers().bufferSource());
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        // Over the HUD's items too, which stand off the screen.
        graphics.nextStratum();
        LoadingLook.overWorld(graphics);
        GlazedDeathScreen.overWorld(graphics);
        raw.flush();
    }
}
