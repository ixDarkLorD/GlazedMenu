package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Where the list of suggestions is and which of them is chosen, for Glazed Menu's popup behind it (GlazedChatScreen).
@Mixin(CommandSuggestions.SuggestionsList.class)
public interface SuggestionsListAccessor {
    @Accessor("rect")
    Rect2i glazedmenu$rect();

    @Accessor("offset")
    int glazedmenu$offset();

    @Accessor("current")
    int glazedmenu$current();
}
