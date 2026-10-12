package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Leaving the world as the game's own pause screen does (1.20.1 keeps that to the screen itself).
@Mixin(PauseScreen.class)
public interface PauseScreenInvoker {
    @Invoker("onDisconnect")
    void glazedmenu$onDisconnect();
}
