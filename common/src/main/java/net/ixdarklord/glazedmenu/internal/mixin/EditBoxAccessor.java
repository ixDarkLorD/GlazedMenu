package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.BiFunction;

// The selection's other end, which vanilla's text field keeps private (StyledEditBox draws the selection itself).
@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("highlightPos")
    int glazedmenu$highlightPos();

    /** How the field colors its text, for a screen to color it another way for a while and hand back to this (GlazedChatScreen). */
    @Accessor("formatter")
    BiFunction<String, Integer, FormattedCharSequence> glazedmenu$formatter();
}
