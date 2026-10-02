package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.source.ftb.FtbSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Wherever an FTB mod opens FTB Library's config editor (its sidebar button, commands, packets), Glazed Menu's screen opens
// instead, when the player chose to replace FTB's editor. Only added when FTB Library is installed (GlazedMixinPlugin).
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.config.manager.ConfigManagerClient", remap = false)
public abstract class FtbConfigManagerClientMixin {
    @Inject(method = "editConfig(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void glazedmenu$openGlazed(String configName, boolean readOnly, CallbackInfo info) {
        if (FtbSource.useOwnEditor(configName)) return;
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = FtbSource.screenFor(configName, minecraft.screen);
        if (screen == null) return;
        minecraft.setScreen(screen);
        info.cancel();
    }
}
