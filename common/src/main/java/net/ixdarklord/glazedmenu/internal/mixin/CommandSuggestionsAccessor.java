package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// The list of suggestions and the lines of a command's usage, for Glazed Menu's chat screen to draw its own popup
// behind them, and the color the game fills behind them, to take it away there (GlazedChatScreen).
@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsAccessor {
    @Accessor("suggestions")
    CommandSuggestions.@Nullable SuggestionsList glazedmenu$suggestions();

    @Accessor("commandUsage")
    List<FormattedCharSequence> glazedmenu$commandUsage();

    @Accessor("commandUsagePosition")
    int glazedmenu$commandUsagePosition();

    @Accessor("commandUsageWidth")
    int glazedmenu$commandUsageWidth();

    @Mutable
    @Accessor("fillColor")
    void glazedmenu$setFillColor(int color);
}
