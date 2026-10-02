package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The selection's other end, which vanilla's text field keeps private (StyledEditBox draws the selection itself).
@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("highlightPos")
    int glazedmenu$highlightPos();
}
