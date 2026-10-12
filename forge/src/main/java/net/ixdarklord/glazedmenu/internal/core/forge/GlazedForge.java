package net.ixdarklord.glazedmenu.internal.core.forge;

import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.ixdarklord.glazedmenu.internal.net.ChatPayloads;
import net.ixdarklord.glazedmenu.internal.game.ChatLink;
import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.internal.core.GenericScreens;
import net.ixdarklord.glazedmenu.internal.core.GlazedClientConstructor;
import net.ixdarklord.glazedmenu.internal.core.GlazedCommand;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.Optional;

// Glazed Menu's Forge entry point in the game: Forge's platform, the common setup, the client tick, the command, and
// the mod list's config buttons. GlazedForgeCommon (the mod's class, on a server too) calls it in the game only.
public final class GlazedForge {
    private GlazedForge() {}

    static void init(FMLJavaModLoadingContext context) {
        GlazedPlatform.set(new ForgeGlazedPlatform());
        GlazedClientConstructor.init();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START) GlazedClientConstructor.tick(Minecraft.getInstance());
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(GlazedCommand.<CommandSourceStack>build(CommandSourceStack::sendFailure)));
        // The chat's talk with a server that has Glazed Menu too (GlazedForgeCommon has the rest of it).
        ChatLink.setTransport(new ChatLink.Transport() {
            @Override
            public boolean canSend(ChatPayloads.Type<?> type) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                return connection != null && GlazedForgeCommon.channel(type).isRemotePresent(connection.getConnection());
            }

            @Override
            public void send(ChatPayloads.Payload payload) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection != null) connection.send(new ServerboundCustomPayloadPacket(payload.type().id(), GlazedForgeCommon.written(payload)));
            }
        });
        GlazedForgeCommon.channel(ChatPayloads.TypingOf.TYPE).addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
            NetworkEvent.Context source = event.getSource().get();
            ChatPayloads.TypingOf payload = ChatPayloads.TypingOf.TYPE.read(event.getPayload());
            source.enqueueWork(() -> ChatLink.typing(payload.player(), payload.typing()));
            source.setPacketHandled(true);
        });
        // Glazed Menu's own button opens its settings.
        context.getContainer().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> ConfigScreens.create(parent, GlazedMenu.MOD_ID)));
        // Queued work runs after every mod's setup, so the mods' configs and screens are all known by then.
        context.getModEventBus().addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            GlazedClientConstructor.onLoaded();
            registerConfigScreens();
        }));
    }

    // Every mod with configs Glazed Menu reads gets a "Config" button in the mod list. A mod's own screen is kept, but one
    // made for any mod (Configured's) gives way to Glazed Menu's, which is decided as the button is pressed.
    private static void registerConfigScreens() {
        for (String modId : ConfigSources.modIds()) {
            if (modId.equals(GlazedMenu.MOD_ID)) continue;
            ModList.get().getModContainerById(modId).ifPresent(container -> {
                Optional<ConfigScreenHandler.ConfigScreenFactory> existing = container.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class);
                container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> GenericScreens.choose(modId, parent,
                                () -> existing.map(factory -> factory.screenFunction().apply(minecraft, parent)).orElse(null))));
            });
        }
    }
}
