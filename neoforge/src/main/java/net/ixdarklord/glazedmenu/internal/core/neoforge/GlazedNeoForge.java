package net.ixdarklord.glazedmenu.internal.core.neoforge;

import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.internal.core.GenericScreens;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedClientConstructor;
import net.ixdarklord.glazedmenu.internal.core.GlazedCommand;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;

// Glazed Menu's NeoForge entry point: NeoForge's platform, the common setup, the client tick, the command, and the mod list's
// config buttons.
@Mod(value = GlazedMenu.MOD_ID, dist = Dist.CLIENT)
public final class GlazedNeoForge {
    public GlazedNeoForge(ModContainer container, IEventBus modEventBus) {
        GlazedPlatform.set(new NeoForgeGlazedPlatform());
        GlazedClientConstructor.init();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> GlazedClientConstructor.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(GlazedCommand.<CommandSourceStack>build(CommandSourceStack::sendFailure)));
        // Glazed Menu's own button opens its settings.
        container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (mod, parent) -> ConfigScreens.create(parent, GlazedMenu.MOD_ID));
        // Queued work runs after every mod's setup, so the mods' configs and screens are all known by then (and, as
        // Glazed Menu loads after Configured, after Configured has set its screens up).
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            GlazedClientConstructor.onLoaded();
            registerConfigScreens();
        }));
    }

    // Every mod with configs Glazed Menu reads gets a "Config" button in the mod list. A mod's own screen is kept, but one
    // made for any mod (NeoForge's, Configured's) gives way to Glazed Menu's, which is decided as the button is pressed.
    private static void registerConfigScreens() {
        for (String modId : ConfigSources.modIds()) {
            if (modId.equals(GlazedMenu.MOD_ID)) continue;
            ModList.get().getModContainerById(modId).ifPresent(container -> {
                Optional<IConfigScreenFactory> existing = container.getCustomExtension(IConfigScreenFactory.class);
                container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (mod, parent) -> GenericScreens.choose(modId, parent,
                        () -> existing.map(factory -> factory.createScreen(mod, parent)).orElse(null)));
            });
        }
    }
}
