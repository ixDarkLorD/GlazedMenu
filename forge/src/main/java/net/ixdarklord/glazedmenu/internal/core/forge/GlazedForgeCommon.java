package net.ixdarklord.glazedmenu.internal.core.forge;

import io.netty.buffer.Unpooled;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.net.ChatPayloads;
import net.ixdarklord.glazedmenu.internal.net.ChatRelay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

import java.util.HashMap;
import java.util.Map;

// Glazed Menu's part on both sides, a server of its own included: what its chat says between the game and the server,
// and the server's relaying of it. Nothing of the client is touched here (GlazedForge is the client's, and is only
// reached in the game).
@Mod(GlazedMenu.MOD_ID)
public final class GlazedForgeCommon {
    private static final String PROTOCOL = "1";
    // A channel for each payload, by the payload's own id and holding only what the payload writes, as Fabric's are:
    // a Fabric game and a Forge server (or the other way round) understand each other.
    private static final Map<ChatPayloads.Type<?>, EventNetworkChannel> CHANNELS = new HashMap<>();

    public GlazedForgeCommon(FMLJavaModLoadingContext context) {
        ChatRelay.setTransport(new ChatRelay.Transport() {
            @Override
            public boolean canSend(ServerPlayer player, ChatPayloads.Type<?> type) {
                return channel(type).isRemotePresent(player.connection.connection);
            }

            @Override
            public void send(ServerPlayer player, ChatPayloads.Payload payload) {
                player.connection.send(new ClientboundCustomPayloadPacket(payload.type().id(), written(payload)));
            }
        });
        channel(ChatPayloads.Typing.TYPE).addListener((NetworkEvent.ClientCustomPayloadEvent event) -> {
            NetworkEvent.Context source = event.getSource().get();
            ChatPayloads.Typing payload = ChatPayloads.Typing.TYPE.read(event.getPayload());
            ServerPlayer player = source.getSender();
            if (player != null) source.enqueueWork(() -> ChatRelay.typing(player, payload.typing()));
            source.setPacketHandled(true);
        });
        channel(ChatPayloads.Showcase.TYPE).addListener((NetworkEvent.ClientCustomPayloadEvent event) -> {
            NetworkEvent.Context source = event.getSource().get();
            ServerPlayer player = source.getSender();
            if (player != null) source.enqueueWork(() -> ChatRelay.showcase(player));
            source.setPacketHandled(true);
        });
        channel(ChatPayloads.TypingOf.TYPE);
        if (FMLEnvironment.dist == Dist.CLIENT) GlazedForge.init(context);
    }

    /** A payload's channel. Optional: a game or a server without Glazed Menu is joined all the same. */
    static synchronized EventNetworkChannel channel(ChatPayloads.Type<?> type) {
        return CHANNELS.computeIfAbsent(type, each -> NetworkRegistry.ChannelBuilder.named(each.id())
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
                .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
                .eventNetworkChannel());
    }

    static FriendlyByteBuf written(ChatPayloads.Payload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        payload.write(buffer);
        return buffer;
    }
}
