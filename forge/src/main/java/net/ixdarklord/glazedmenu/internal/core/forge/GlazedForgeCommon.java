package net.ixdarklord.glazedmenu.internal.core.forge;

import io.netty.buffer.ByteBuf;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.net.ChatPayloads;
import net.ixdarklord.glazedmenu.internal.net.ChatRelay;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;
import net.minecraftforge.network.payload.PayloadProtocol;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// Glazed Menu's part on both sides, a server of its own included: what its chat says between the game and the server,
// and the server's relaying of it. Nothing of the client is touched here (GlazedForge sets what the client does with
// what it's sent).
// <p>
// Each of the three is sent as the game's own custom payload under its own id, as Fabric and NeoForge send it, so a
// Forge game and a Fabric or NeoForge server (or the reverse) understand each other. Each has a channel of Forge's to
// itself: whether the other side takes one is asked of its channel, and a side only says which it takes.
final class GlazedForgeCommon {
    // What the client does when the server says a player is typing; nothing on a server of its own.
    static Consumer<ChatPayloads.TypingOf> typingOf = payload -> {};

    private static final Channel<CustomPacketPayload> TYPING = channel("chat_typing", ChatPayloads.Typing.TYPE, ChatPayloads.Typing.CODEC, true,
            (payload, context) -> {
                ServerPlayer player = context.getSender();
                if (player != null) ChatRelay.typing(player, payload.typing());
            });
    private static final Channel<CustomPacketPayload> SHOWCASE = channel("chat_showcase", ChatPayloads.Showcase.TYPE, ChatPayloads.Showcase.CODEC, true,
            (payload, context) -> {
                ServerPlayer player = context.getSender();
                if (player != null) ChatRelay.showcase(player);
            });
    private static final Channel<CustomPacketPayload> TYPING_OF = channel("chat_typing_of", ChatPayloads.TypingOf.TYPE, ChatPayloads.TypingOf.CODEC, false,
            (payload, context) -> typingOf.accept(payload));
    private static final Map<CustomPacketPayload.Type<?>, Channel<CustomPacketPayload>> CHANNELS = Map.of(
            ChatPayloads.Typing.TYPE, TYPING, ChatPayloads.Showcase.TYPE, SHOWCASE, ChatPayloads.TypingOf.TYPE, TYPING_OF);

    private GlazedForgeCommon() {}

    // Optional: a game or a server without Glazed Menu is joined all the same. The handler runs on the main thread.
    private static <T extends CustomPacketPayload> Channel<CustomPacketPayload> channel(String name, CustomPacketPayload.Type<T> type, StreamCodec<ByteBuf, T> codec,
                                                                                    boolean toServer, BiConsumer<T, CustomPayloadEvent.Context> handler) {
        PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> play = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(GlazedMenu.MOD_ID, name))
                .networkProtocolVersion(1)
                .optional()
                .payloadChannel()
                .play();
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow = toServer ? play.serverbound() : play.clientbound();
        return flow.addMain(type, codec.<RegistryFriendlyByteBuf>cast(), handler).build();
    }

    /** Whether the other end of a connection takes a payload of this kind. */
    static boolean canSend(Connection connection, CustomPacketPayload.Type<?> type) {
        Channel<CustomPacketPayload> channel = CHANNELS.get(type);
        return channel != null && channel.isRemotePresent(connection);
    }

    static void sendToServer(CustomPacketPayload payload) {
        Channel<CustomPacketPayload> channel = CHANNELS.get(payload.type());
        if (channel != null) channel.send(payload, PacketDistributor.SERVER.noArg());
    }

    static void init() {
        ChatRelay.setTransport(new ChatRelay.Transport() {
            @Override
            public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
                return GlazedForgeCommon.canSend(player.connection.getConnection(), type);
            }

            @Override
            public void send(ServerPlayer player, CustomPacketPayload payload) {
                Channel<CustomPacketPayload> channel = CHANNELS.get(payload.type());
                if (channel != null) channel.send(payload, PacketDistributor.PLAYER.with(player));
            }
        });
    }
}
