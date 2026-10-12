package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.game.LoadingLook;
import net.ixdarklord.glazedmenu.internal.game.WorkingLook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The screen shown while a world's terrain loads, in Glazed Menu's look: the last step of the wait into the world
// (WorkingLook), whatever came before it. The screen stays the game's own (it waits for the terrain). 1.21.1 has this
// screen apart from the one a world is made ready under (LevelLoadingScreenMixin), and it knows no progress.
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin extends Screen {
    @Shadow
    @Final
    private ReceivingLevelScreen.Reason reason;

    private ReceivingLevelScreenMixin(Component title) {
        super(title);
    }

    // The backdrop of the waits before it (WorkingLook), so the reading of a world and the loading of its terrain are
    // one screen to the eye; a portal's own background (going to the Nether or the End) is left as it is.
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$background(GuiGraphics raw, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (this.reason != ReceivingLevelScreen.Reason.OTHER || !WorkingLook.active()) return;
        WorkingLook.background(GuiGraphicsExtractor.of(raw), this.width, this.height, () -> this.renderPanorama(raw, a));
        info.cancel();
    }

    // The world is ready and this screen closes: its look fades off the world from here (GuiMixin).
    @Inject(method = "onClose", at = @At("HEAD"))
    private void glazedmenu$fadeOut(CallbackInfo info) {
        WorkingLook.finish();
        if (this.reason == ReceivingLevelScreen.Reason.OTHER) LoadingLook.fadeOut();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$content(GuiGraphics raw, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (!GlazedSettings.replaceLoading()) return;
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        this.renderBackground(raw, mouseX, mouseY, a);
        // The last step of the wait (and the only one through a portal, over the portal's own background): the game
        // doesn't say how far it is, so the ring's piece breathes.
        WorkingLook.extract(graphics, this.font, this.width, this.height, Component.translatable(WorkingLook.TERRAIN));
        // What's on the screen is kept, for the screen to dissolve off the world from.
        if (this.reason == ReceivingLevelScreen.Reason.OTHER) LoadingLook.keepFrame(graphics);
        info.cancel();
    }
}
