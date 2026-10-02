package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.modlist.ModListHooks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A Mods button on the title screen, when no mod list put one there.
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void glazedmenu$addModsButton(CallbackInfo info) {
        ModListHooks.addModsButton(this, this.children(), true);
    }
}
