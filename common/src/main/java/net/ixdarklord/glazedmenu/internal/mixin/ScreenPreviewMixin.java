package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.gui.setup.LivePreview;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While the setup draws a screen as a preview, the screen's blur isn't drawn: it covers the whole window whatever the
// preview's size, and the setup already shows the real one behind it.
@Mixin(Screen.class)
public abstract class ScreenPreviewMixin {
    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$noPreviewBlur(float a, CallbackInfo ci) {
        if (LivePreview.drawing()) ci.cancel();
    }
}
