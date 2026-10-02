package net.ixdarklord.glazedmenu.internal.core.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.ixdarklord.glazedmenu.internal.core.GlazedClientConstructor;
import net.ixdarklord.glazedmenu.internal.core.GlazedCommand;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.source.spec.ForgeSpecView;
import net.ixdarklord.glazedmenu.internal.source.spec.ModConfigSource;

// Glazed Menu's Fabric entry point: Fabric's platform, the common setup, the client tick and the command.
public final class GlazedFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GlazedPlatform.set(new FabricGlazedPlatform());
        GlazedClientConstructor.init();
        // Forge Config API Port also brings Forge's configs, wrapped in its copy of NeoForge's.
        if (FabricLoader.getInstance().isModLoaded("forgeconfigapiport")) ModConfigSource.INSTANCE.addReader(ForgeSpecView::of);
        ClientTickEvents.START_CLIENT_TICK.register(GlazedClientConstructor::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(GlazedCommand.<FabricClientCommandSource>build(FabricClientCommandSource::sendError)));
    }
}
