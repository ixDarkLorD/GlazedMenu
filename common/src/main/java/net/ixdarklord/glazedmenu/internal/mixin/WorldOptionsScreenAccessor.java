package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.WorldOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Whether the game takes the port typed on its World Options screen, for Glazed Menu's popup to mark it (GlazedWorldOptionsScreen).
@Mixin(WorldOptionsScreen.class)
public interface WorldOptionsScreenAccessor {
    @Accessor("portValid")
    boolean glazedmenu$portValid();
}
