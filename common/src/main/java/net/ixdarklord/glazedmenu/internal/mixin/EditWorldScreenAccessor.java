package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The world the game's edit screen has open, for Glazed Menu's to back it up without the game's button, which on
// 1.20.1 closes the screen too (GlazedEditWorldScreen).
@Mixin(EditWorldScreen.class)
public interface EditWorldScreenAccessor {
    @Accessor("levelAccess")
    LevelStorageSource.LevelStorageAccess glazedmenu$levelAccess();
}
