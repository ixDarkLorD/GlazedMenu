package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.screens.social.PlayerEntry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

// The skin of a player listed on the game's Social Interactions screen, for Glazed Menu's popup to draw the face
// (GlazedSocialScreen); 1.20.1 keeps it to the row.
@Mixin(PlayerEntry.class)
public interface PlayerEntryAccessor {
    @Accessor("skinGetter")
    Supplier<ResourceLocation> glazedmenu$skinGetter();
}
