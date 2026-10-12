package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.game.GlazedDeathScreen;
import net.ixdarklord.glazedmenu.internal.game.LoadingLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Over the game and its HUD, under the toasts: where a loading screen's look fades off the world it has just loaded,
// and where the death screen's band closes as the player respawns.
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderSavingIndicator", at = @At("HEAD"))
    private void glazedmenu$loadingFade(GuiGraphics raw, DeltaTracker deltaTracker, CallbackInfo info) {
        if (!LoadingLook.firstOverWorld()) return;
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        LoadingLook.overWorld(graphics);
        GlazedDeathScreen.overWorld(graphics);
    }
}
