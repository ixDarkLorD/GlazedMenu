package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The screen the game's friends list is drawn over, for Glazed Menu's popup to be drawn over the same (GlazedFriendsScreen).
@Mixin(FriendsOverlayScreen.class)
public interface FriendsOverlayScreenAccessor {
    @Accessor("backgroundScreen")
    @Nullable Screen glazedmenu$backgroundScreen();
}
