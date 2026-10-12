package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The server the game's screen was opened for, to tell adding one (it has no address yet) from editing one: 1.21.1's
// screen has one title for both (GlazedEditServerScreen).
@Mixin(EditServerScreen.class)
public interface EditServerScreenAccessor {
    @Accessor("serverData")
    ServerData glazedmenu$serverData();
}
