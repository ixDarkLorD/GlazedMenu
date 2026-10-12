package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Whether a pause screen is the menu, or only the word "paused" (1.20.1 has no way to ask it).
@Mixin(PauseScreen.class)
public interface PauseScreenAccessor {
    @Accessor("showPauseMenu")
    boolean glazedmenu$showPauseMenu();
}
