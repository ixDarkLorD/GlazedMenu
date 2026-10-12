package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.game.GlazedPauseScreen;
import net.ixdarklord.glazedmenu.internal.compat.CompatScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedDeathScreen;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.game.GlazedChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The chat over the game while it isn't open, in the rows Glazed Menu's chat screen has (the open chat is that screen's
// own to draw). 1.20.1 draws the chat from the HUD whether it's open or not, so which it is is asked of the screen.
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$rows(GuiGraphics graphics, int ticks, int mouseX, int mouseY, CallbackInfo info) {
        if (!GlazedSettings.replaceChat()) return;
        Screen screen = this.minecraft.screen;
        if (screen instanceof GlazedChatScreen) {
            info.cancel();
            return;
        }
        // Another chat screen (the one in a bed) keeps the game's own lines.
        if (screen instanceof ChatScreen) return;
        // Glazed Menu's screens draw a shade over the game where newer versions blur it (1.20.1 has no blur); the shade
        // lets the world be sensed through it, and the rows' words would read through it too, so they wait till the
        // screen is closed.
        if (screen instanceof CompatScreen || screen instanceof GlazedPauseScreen || screen instanceof GlazedDeathScreen) {
            info.cancel();
            return;
        }
        GlazedChatScreen.extractHud(GuiGraphicsExtractor.of(graphics), this.minecraft.font, ticks);
        info.cancel();
    }
}
