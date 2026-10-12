package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The server the game's screen is adding or editing: one with no address yet is being added.
@Mixin(EditServerScreen.class)
public interface EditServerScreenAccessor {
    @Accessor("serverData")
    ServerData glazedmenu$serverData();
}
