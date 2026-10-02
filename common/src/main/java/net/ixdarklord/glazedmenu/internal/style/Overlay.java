package net.ixdarklord.glazedmenu.internal.style;

import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

// A screen drawn over another that stays visible under it (popups, dropdowns). Closing one returns to that screen
// without a page transition.
interface Overlay {
    /** The screen under this one. */
    @Nullable Screen overlayParent();
}
