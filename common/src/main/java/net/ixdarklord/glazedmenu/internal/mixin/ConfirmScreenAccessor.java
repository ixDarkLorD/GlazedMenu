package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// What the game's question says under its title, for Glazed Menu's popup to say the same (GlazedWarningScreen). 1.21.1
// draws it as lines of its own, not as a widget that could be asked.
@Mixin(ConfirmScreen.class)
public interface ConfirmScreenAccessor {
    @Accessor("message")
    Component glazedmenu$message();
}
