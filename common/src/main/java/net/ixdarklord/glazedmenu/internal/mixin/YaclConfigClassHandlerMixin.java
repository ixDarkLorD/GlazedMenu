package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.source.yacl.YaclHandlers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// YACL keeps no list of its configs, so Glazed Menu notes each one as it's made. Only added when YACL is installed
// (GlazedMixinPlugin).
@Pseudo
@Mixin(targets = "dev.isxander.yacl3.config.v2.impl.ConfigClassHandlerImpl", remap = false)
public abstract class YaclConfigClassHandlerMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void glazedmenu$track(CallbackInfo info) {
        YaclHandlers.track(this);
    }
}
