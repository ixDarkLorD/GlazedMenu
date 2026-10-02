package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.modlist.ModListHooks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A Mods button on the pause screen, when no mod list put one there.
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    private PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void glazedmenu$addModsButton(CallbackInfo info) {
        ModListHooks.addModsButton(this, this.children(), false);
    }
}
