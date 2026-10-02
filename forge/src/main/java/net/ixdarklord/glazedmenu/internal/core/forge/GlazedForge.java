package net.ixdarklord.glazedmenu.internal.core.forge;

import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.internal.core.GenericScreens;
import net.ixdarklord.glazedmenu.internal.core.GlazedClientConstructor;
import net.ixdarklord.glazedmenu.internal.core.GlazedCommand;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.internal.source.spec.forge.ForgeConfigSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Optional;

// Glazed Menu's Forge entry point: Forge's platform, the common setup, the client tick, the command, and the mod list's
// config buttons. Glazed Menu is client-only; on a server it does nothing.
@Mod(GlazedMenu.MOD_ID)
public final class GlazedForge {
    public GlazedForge(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        GlazedPlatform.set(new ForgeGlazedPlatform());
        GlazedClientConstructor.init();
        ConfigSources.register(ForgeConfigSource.INSTANCE);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Pre event) -> GlazedClientConstructor.tick(Minecraft.getInstance()));
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(GlazedCommand.<CommandSourceStack>build(CommandSourceStack::sendFailure)));
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
