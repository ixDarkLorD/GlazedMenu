package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Adds a widget to any screen (vanilla keeps it protected). The title and pause screen mixins go through this rather
// than a method reference to themselves, which would name the mixin class at runtime.
@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T glazedmenu$addRenderableWidget(T widget);
}
