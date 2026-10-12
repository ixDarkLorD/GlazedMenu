package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// What one of the game's questions says under its title, for Glazed Menu's popup to say the same (GlazedWarningScreen);
// 1.20.1 keeps it in the screen, not in a widget of it.
@Mixin(ConfirmScreen.class)
public interface ConfirmScreenAccessor {
    @Accessor("message")
    Component glazedmenu$message();
}
