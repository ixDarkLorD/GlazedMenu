package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// The chat's messages, newest first, for Glazed Menu's chat screen to draw as rows of its own (GlazedChatScreen).
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> glazedmenu$allMessages();
}
