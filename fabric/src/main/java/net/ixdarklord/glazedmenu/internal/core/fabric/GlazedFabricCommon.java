package net.ixdarklord.glazedmenu.internal.core.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ixdarklord.glazedmenu.internal.net.ChatPayloads;
import net.ixdarklord.glazedmenu.internal.net.ChatRelay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

// Glazed Menu's part on both sides, a server of its own included: what its chat says between the game and the server,
// and the server's relaying of it. Nothing of the client is touched here. Fabric's channels are known to the other side
// only where it registered them too, so a game or a server without Glazed Menu is joined all the same.
public final class GlazedFabricCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        ChatRelay.setTransport(new ChatRelay.Transport() {
            @Override
            public boolean canSend(ServerPlayer player, ChatPayloads.Type<?> type) {
                return ServerPlayNetworking.canSend(player, type.id());
            }

            @Override
            public void send(ServerPlayer player, ChatPayloads.Payload payload) {
                FriendlyByteBuf buffer = PacketByteBufs.create();
                payload.write(buffer);
                ServerPlayNetworking.send(player, payload.type().id(), buffer);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ChatPayloads.Typing.TYPE.id(), (server, player, handler, buffer, responseSender) -> {
            ChatPayloads.Typing payload = ChatPayloads.Typing.TYPE.read(buffer);
            server.execute(() -> ChatRelay.typing(player, payload.typing()));
        });
        ServerPlayNetworking.registerGlobalReceiver(ChatPayloads.Showcase.TYPE.id(), (server, player, handler, buffer, responseSender) ->
                server.execute(() -> ChatRelay.showcase(player)));
    }
}
