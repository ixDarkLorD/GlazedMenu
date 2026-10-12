package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// The game's own leaving of a world or a server (saving, then back to the title screen or the list of servers), for
// Glazed Menu's pause screen to leave the same way (GlazedPauseScreen).
@Mixin(PauseScreen.class)
public interface PauseScreenInvoker {
    @Invoker("onDisconnect")
    void glazedmenu$onDisconnect();
}
