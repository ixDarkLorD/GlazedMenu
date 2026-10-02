package net.ixdarklord.glazedmenu.internal.mixin.fabric;

import net.ixdarklord.glazedmenu.internal.core.GenericScreens;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mod Menu takes the first screen any mod provides for another, so Forge Config API Port's or Configured's generic
// screen can win over Glazed Menu's for a mod without a screen of its own. Here Glazed Menu's replaces those (and fills in for
// a mod with no screen at all); a mod's own screen is kept. Only added when Mod Menu is installed (GlazedMixinPlugin).
@Pseudo
@Mixin(targets = "com.terraformersmc.modmenu.ModMenu", remap = false)
public abstract class ModMenuMixin {
    @Inject(method = "getConfigScreen", at = @At("RETURN"), cancellable = true, require = 0)
    private static void glazedmenu$chooseScreen(String modId, Screen parent, CallbackInfoReturnable<Screen> info) {
        Screen screen = info.getReturnValue();
        if (GenericScreens.handles(modId) && GenericScreens.isGeneric(screen)) info.setReturnValue(GenericScreens.choose(modId, parent, () -> screen));
    }

    @Inject(method = "hasConfigScreen", at = @At("RETURN"), cancellable = true, require = 0)
    private static void glazedmenu$hasScreen(String modId, CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValueZ() && GenericScreens.handles(modId)) info.setReturnValue(true);
    }
}
