package net.ixdarklord.glazedmenu.internal.core.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ixdarklord.glazedmenu.internal.net.ChatPayloads;
import net.ixdarklord.glazedmenu.internal.net.ChatRelay;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

// Glazed Menu's part on both sides, a server of its own included: what its chat says between the game and the server,
// and the server's relaying of it. Nothing of the client is touched here.
public final class GlazedFabricCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(ChatPayloads.Typing.TYPE, ChatPayloads.Typing.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChatPayloads.Showcase.TYPE, ChatPayloads.Showcase.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChatPayloads.TypingOf.TYPE, ChatPayloads.TypingOf.CODEC);
        ChatRelay.setTransport(new ChatRelay.Transport() {
            @Override
            public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
                return ServerPlayNetworking.canSend(player, type);
            }

            @Override
            public void send(ServerPlayer player, CustomPacketPayload payload) {
                ServerPlayNetworking.send(player, payload);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ChatPayloads.Typing.TYPE, (payload, context) ->
                context.server().execute(() -> ChatRelay.typing(context.player(), payload.typing())));
        ServerPlayNetworking.registerGlobalReceiver(ChatPayloads.Showcase.TYPE, (payload, context) ->
                context.server().execute(() -> ChatRelay.showcase(context.player())));
    }
}
