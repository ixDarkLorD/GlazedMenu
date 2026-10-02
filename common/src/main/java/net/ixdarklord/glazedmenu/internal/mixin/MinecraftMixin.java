package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.modlist.ModListHooks;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Opens Glazed Menu's mod list wherever Mod Menu's or NeoForge's would open (screens are set on the Gui since 26.2).
@Mixin(Gui.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen glazedmenu$replaceModList(Screen screen) {
        return ModListHooks.replace(screen);
    }
}
