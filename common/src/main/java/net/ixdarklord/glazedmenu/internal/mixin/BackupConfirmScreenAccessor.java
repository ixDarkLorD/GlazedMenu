package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// What the game's backup question says under its title, for Glazed Menu's popup to say the same (GlazedWarningScreen).
@Mixin(BackupConfirmScreen.class)
public interface BackupConfirmScreenAccessor {
    @Accessor("description")
    Component glazedmenu$description();
}
