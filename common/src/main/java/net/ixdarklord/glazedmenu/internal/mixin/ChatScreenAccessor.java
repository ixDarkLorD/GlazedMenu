package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// What the game's chat screen was opened with, for Glazed Menu's to be opened with the same, and its suggestions
// (GlazedChatScreen).
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
    @Accessor("initial")
    String glazedmenu$initial();

    @Accessor("initial")
    void glazedmenu$setInitial(String initial);

    @Accessor("commandSuggestions")
    CommandSuggestions glazedmenu$commandSuggestions();
}
