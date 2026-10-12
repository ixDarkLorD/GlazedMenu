package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.game.LoadingLook;
import net.ixdarklord.glazedmenu.internal.game.WorkingLook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The screen shown while a world's terrain comes from the server (1.20.1's own screen for it), in Glazed Menu's look:
// the last step of the wait into the world (WorkingLook). The screen stays the game's own.
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin extends Screen {
    private ReceivingLevelScreenMixin(Component title) {
        super(title);
    }

    // Over the game's own dirt (drawn just before this): the backdrop and the ring of the waits before it. What's on
    // the screen is kept (a few times a second), for the screen to dissolve off the world from as it closes.
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/ReceivingLevelScreen;renderDirtBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void glazedmenu$content(GuiGraphics raw, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (!GlazedSettings.replaceLoading()) return;
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        WorkingLook.background(graphics, this.width, this.height);
        WorkingLook.extract(graphics, this.font, this.width, this.height, Component.translatable(WorkingLook.TERRAIN));
        LoadingLook.keepFrame();
        info.cancel();
    }

    // The world is ready and this screen closes: the wait is over, and its look fades off the world from here
    // (GameRendererMixin).
    @Inject(method = "onClose", at = @At("HEAD"))
    private void glazedmenu$fadeOut(CallbackInfo info) {
        WorkingLook.finish();
        LoadingLook.fadeOut();
    }
}
