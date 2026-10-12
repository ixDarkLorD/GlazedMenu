package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// What the game's death screen was opened with, for Glazed Menu's to be opened with the same (GlazedDeathScreen).
@Mixin(DeathScreen.class)
public interface DeathScreenAccessor {
    @Accessor("causeOfDeath")
    @Nullable Component glazedmenu$causeOfDeath();

    @Accessor("hardcore")
    boolean glazedmenu$hardcore();

    @Accessor("player")
    LocalPlayer glazedmenu$player();
}
