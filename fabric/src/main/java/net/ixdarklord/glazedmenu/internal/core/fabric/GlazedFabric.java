package net.ixdarklord.glazedmenu.internal.core.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.ixdarklord.glazedmenu.internal.net.ChatPayloads;
import net.ixdarklord.glazedmenu.internal.game.ChatLink;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.ixdarklord.glazedmenu.internal.core.GlazedClientConstructor;
import net.ixdarklord.glazedmenu.internal.core.GlazedCommand;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;

// Glazed Menu's Fabric entry point: Fabric's platform, the common setup, the client tick and the command.
public final class GlazedFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GlazedPlatform.set(new FabricGlazedPlatform());
        GlazedClientConstructor.init();
        // Forge Config API Port also brings Forge's configs, wrapped in its copy of NeoForge's.
        ClientTickEvents.START_CLIENT_TICK.register(GlazedClientConstructor::tick);
        // The chat's talk with a server that has Glazed Menu too (GlazedFabricCommon has the rest of it).
        ChatLink.setTransport(new ChatLink.Transport() {
            @Override
            public boolean canSend(ChatPayloads.Type<?> type) {
                return ClientPlayNetworking.canSend(type.id());
            }

            @Override
            public void send(ChatPayloads.Payload payload) {
                FriendlyByteBuf buffer = PacketByteBufs.create();
                payload.write(buffer);
                ClientPlayNetworking.send(payload.type().id(), buffer);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ChatPayloads.TypingOf.TYPE.id(), (client, handler, buffer, responseSender) -> {
            ChatPayloads.TypingOf payload = ChatPayloads.TypingOf.TYPE.read(buffer);
            client.execute(() -> ChatLink.typing(payload.player(), payload.typing()));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(GlazedCommand.<FabricClientCommandSource>build(FabricClientCommandSource::sendError)));
    }
}
