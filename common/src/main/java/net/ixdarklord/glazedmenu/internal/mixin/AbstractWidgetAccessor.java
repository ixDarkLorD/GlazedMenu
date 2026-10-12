package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// What one of the game's widgets says under the mouse (why it's off, what it does), for Glazed Menu's popups over the
// game's own screens to say the same (GlazedFriendsScreen, GlazedWorldOptionsScreen).
@Mixin(AbstractWidget.class)
public interface AbstractWidgetAccessor {
    @Accessor("tooltip")
    WidgetTooltipHolder glazedmenu$tooltip();
}
