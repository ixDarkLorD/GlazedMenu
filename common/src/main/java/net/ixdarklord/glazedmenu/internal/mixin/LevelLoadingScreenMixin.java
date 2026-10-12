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
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The screen shown while a world of the player's own is made ready, in Glazed Menu's look: a step of the wait into
// the world (WorkingLook), its piece of the ring filling as the game says how far it is, the chunks in a corner. The
// screen stays the game's own (it does the loading). On 1.20.1 the terrain coming from the server has a screen of its
// own after this one (ReceivingLevelScreenMixin), the wait's last step.
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    @Shadow
    @Final
    private StoringChunkProgressListener progressListener;

    private LevelLoadingScreenMixin(Component title) {
        super(title);
    }

    // Over the game's own background (drawn just before this): the backdrop of the waits before it, so the reading of a
    // world and the making of it ready are one screen to the eye; the ring's piece fills with the game's own number.
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V"),
            cancellable = true)
    private void glazedmenu$content(GuiGraphics raw, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (!GlazedSettings.replaceLoading()) return;
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        WorkingLook.background(graphics, this.width, this.height);
        WorkingLook.extract(graphics, this.font, this.width, this.height, Component.translatable(WorkingLook.PREPARING),
                Mth.clamp(this.progressListener.getProgress(), 0, 100) / 100F);
        // (The top right corner, clear of the ring and of the line of steps under it.)
        LoadingLook.chunks(graphics, this.width, 10 + 6 + this.progressListener.getDiameter() * 2 + 10, this.progressListener);
        info.cancel();
    }
}
