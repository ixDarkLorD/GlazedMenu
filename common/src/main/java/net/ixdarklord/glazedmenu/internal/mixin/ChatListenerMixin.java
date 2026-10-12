package net.ixdarklord.glazedmenu.internal.mixin;

import com.mojang.authlib.GameProfile;
import net.ixdarklord.glazedmenu.internal.game.ChatLink;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;
import java.util.List;

// A player's message as the game has just put it into the chat: who sent it is noted, for Glazed Menu's chat screen to
// draw the sender's face beside it (GlazedChatScreen). 1.20.1's messages don't say what they came from, so a message
// with a sender noted is a player's.
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "showMessageToPlayer", at = @At("RETURN"))
    private void glazedmenu$noteSender(ChatType.Bound boundChatType, PlayerChatMessage message, Component decoratedMessage, GameProfile sender,
                                       boolean onlyShowSecure, Instant received, CallbackInfoReturnable<Boolean> info) {
        if (!info.getReturnValueZ()) return;
        List<GuiMessage> messages = ((ChatComponentAccessor) this.minecraft.gui.getChat()).glazedmenu$allMessages();
        if (messages.isEmpty()) return;
        GuiMessage newest = messages.get(0);
        // Only the message put in just now (a wholly filtered one puts none in).
        if (newest.addedTime() == this.minecraft.gui.getGuiTicks()) ChatLink.noteSender(newest, message.sender());
    }
}
