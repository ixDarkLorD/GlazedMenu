package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Whether the game takes the port typed on its Open to LAN screen, for Glazed Menu's popup to mark it (GlazedWorldOptionsScreen).
@Mixin(MultiplayerOptionsScreen.class)
public interface MultiplayerOptionsScreenAccessor {
    @Accessor("portValid")
    boolean glazedmenu$portValid();
}
