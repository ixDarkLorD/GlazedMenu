package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.game.GlazedChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The chat over the game while it isn't open, in the rows Glazed Menu's chat screen has (the open chat is that screen's
// own to draw).
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$rows(GuiGraphics graphics, int ticks, int mouseX, int mouseY, boolean focused, CallbackInfo info) {
        if (focused || !GlazedSettings.replaceChat()) return;
        GlazedChatScreen.extractHud(GuiGraphicsExtractor.of(graphics), Minecraft.getInstance().font, ticks);
        info.cancel();
    }
}
