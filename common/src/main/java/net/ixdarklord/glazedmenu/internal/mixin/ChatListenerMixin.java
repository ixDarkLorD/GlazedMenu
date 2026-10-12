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
import java.util.Objects;

// A player's message as the game has just put it into the chat: who sent it is noted, for Glazed Menu's chat screen to
// draw the sender's face beside it (GlazedChatScreen). 1.21.1's messages don't say where they came from, so a noted
// sender is also what tells a player's message from the game's own.
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
        GuiMessage newest = messages.getFirst();
        // The message just added carries this one's signature (none for both where the chat isn't signed).
        if (Objects.equals(newest.signature(), message.signature())) ChatLink.noteSender(newest, message.sender());
    }
}
