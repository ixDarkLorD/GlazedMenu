package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.gui.setup.LivePreview;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While the setup draws a screen as a preview, the screen's panorama isn't drawn: it covers the whole window whatever
// the preview's size, and the setup already shows the real one behind it.
@Mixin(PanoramaRenderer.class)
public abstract class PanoramaPreviewMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$noPreviewPanorama(float a, float fade, CallbackInfo ci) {
        if (LivePreview.drawing()) ci.cancel();
    }
}
