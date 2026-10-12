package net.ixdarklord.glazedmenu.internal.core.neoforge;

import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.net.ChatPayloads;
import net.ixdarklord.glazedmenu.internal.net.ChatRelay;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Glazed Menu's part on both sides, a server of its own included: what its chat says between the game and the server,
// and the server's relaying of it. Nothing of the client is touched here (GlazedNeoForge is the client's).
@Mod(GlazedMenu.MOD_ID)
public final class GlazedNeoForgeCommon {
    public GlazedNeoForgeCommon(IEventBus modEventBus) {
        ChatRelay.setTransport(new ChatRelay.Transport() {
            @Override
            public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
                return player.connection.hasChannel(type);
            }

            @Override
            public void send(ServerPlayer player, CustomPacketPayload payload) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        });
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            // Optional: a game or a server without Glazed Menu is joined all the same.
            PayloadRegistrar registrar = event.registrar("1").optional();
            registrar.playToServer(ChatPayloads.Typing.TYPE, ChatPayloads.Typing.CODEC, (payload, context) ->
                    context.enqueueWork(() -> ChatRelay.typing((ServerPlayer) context.player(), payload.typing())));
            registrar.playToServer(ChatPayloads.Showcase.TYPE, ChatPayloads.Showcase.CODEC, (payload, context) ->
                    context.enqueueWork(() -> ChatRelay.showcase((ServerPlayer) context.player())));
            registrar.playToClient(ChatPayloads.TypingOf.TYPE, ChatPayloads.TypingOf.CODEC);
        });
    }
}
