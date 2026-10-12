package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.game.LoadingLook;
import net.ixdarklord.glazedmenu.internal.game.WorkingLook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The screen shown while a world's terrain loads, in Glazed Menu's look: a picture of where the player last was there
// behind it, and a panel along the bottom with the progress. The screen stays the game's own (it does the loading).
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    @Shadow
    private LevelLoadTracker loadTracker;
    @Shadow
    private float smoothedProgress;
    @Shadow
    private LevelLoadingScreen.Reason reason;

    private LevelLoadingScreenMixin(Component title) {
        super(title);
    }

    // The backdrop of the waits before it (WorkingLook), so the reading of a world and the loading of its terrain are
    // one screen to the eye; a portal's own background (going to the Nether or the End) is left as it is.
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$background(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (this.reason != LevelLoadingScreen.Reason.OTHER || !WorkingLook.active()) return;
        WorkingLook.background(graphics, this.width, this.height, () -> this.extractPanorama(graphics, a));
        info.cancel();
    }

    // The world is ready and this screen closes: its picture and panel fade off the world from here (GuiMixin).
    @Inject(method = "onClose", at = @At("HEAD"))
    private void glazedmenu$fadeOut(CallbackInfo info) {
        WorkingLook.finish();
        if (this.reason == LevelLoadingScreen.Reason.OTHER) LoadingLook.fadeOut();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$content(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (!GlazedSettings.replaceLoading()) return;
        float progress = this.loadTracker.hasProgress() ? this.smoothedProgress : -1;
        ChunkLoadStatusView view = this.loadTracker.statusView();
        // The last step of the wait, whatever came before it (and the only one through a portal, over the portal's own
        // background): the ring's piece fills as the terrain comes, with no bar; the chunks in a corner.
        WorkingLook.extract(graphics, this.font, this.width, this.height, Component.translatable(WorkingLook.TERRAIN), progress);
        // What's on the screen is kept as the terrain nears its end, for the screen to dissolve off the world from.
        if (this.reason == LevelLoadingScreen.Reason.OTHER && (progress < 0 || progress >= 0.7F)) LoadingLook.keepFrame();
        // (The top right corner, clear of the ring and of the line of steps under it.)
        if (view != null) LoadingLook.chunks(graphics, this.width, 10 + 6 + (view.radius() * 2 + 1) * 2 + 10, view);
        info.cancel();
    }
}
