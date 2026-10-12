package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.game.LoadingLook;
import net.ixdarklord.glazedmenu.internal.game.WorkingLook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The screen shown while a world of the player's own is made ready, in Glazed Menu's look: a step of the wait into the
// world (WorkingLook), over the backdrop of the steps before it, so the reading of a world and the loading of it are one
// screen to the eye. The screen stays the game's own. (On 1.21.1 the terrain then loads under another screen, the
// wait's last step: ReceivingLevelScreenMixin.)
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    @Shadow
    @Final
    private StoringChunkProgressListener progressListener;

    private LevelLoadingScreenMixin(Component title) {
        super(title);
    }

    // 1.21.1 draws a screen's background and the rest in one call: the backdrop of the waits before it, then the ring
    // (its piece fills as the world comes, with no bar), and the chunks in a corner.
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$content(GuiGraphics raw, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (!GlazedSettings.replaceLoading()) return;
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        WorkingLook.background(graphics, this.width, this.height, () -> this.renderPanorama(raw, a));
        float progress = Math.clamp(this.progressListener.getProgress(), 0, 100) / 100F;
        WorkingLook.extract(graphics, this.font, this.width, this.height, Component.translatable(WorkingLook.LOADING), progress);
        // (The top right corner, clear of the ring and of the line of steps under it.)
        LoadingLook.chunks(graphics, this.width, 10 + 6 + this.progressListener.getDiameter() * 2 + 10, this.progressListener);
        // What's on the screen is kept as the world nears its end, for the screen to dissolve off the world from
        // (where the terrain's own screen is gone before it's drawn).
        if (progress >= 0.7F) LoadingLook.keepFrame(graphics);
        info.cancel();
    }
}
