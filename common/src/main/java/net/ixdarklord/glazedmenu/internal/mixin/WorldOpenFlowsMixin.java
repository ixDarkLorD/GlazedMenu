package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.game.LoadingLook;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A world being opened, however it's opened (the game's own list, Glazed Menu's, a shortcut): the loading screens are
// told which, so its picture is there from their first frame.
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
    @Inject(method = "openWorld(Ljava/lang/String;Ljava/lang/Runnable;)V", at = @At("HEAD"))
    private void glazedmenu$expect(String levelId, Runnable onFail, CallbackInfo info) {
        LoadingLook.expectWorld(levelId);
    }
}
