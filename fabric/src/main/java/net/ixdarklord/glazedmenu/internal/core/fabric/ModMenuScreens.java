package net.ixdarklord.glazedmenu.internal.core.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.minecraft.client.gui.screens.Screen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

// Mods' own config screens, from their Mod Menu entry points. Only loaded with Mod Menu.
final class ModMenuScreens {
    private ModMenuScreens() {}

    static Map<String, UnaryOperator<Screen>> own() {
        Map<String, UnaryOperator<Screen>> screens = new LinkedHashMap<>();
        for (EntrypointContainer<ModMenuApi> entrypoint : FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class)) {
            String modId = entrypoint.getProvider().getMetadata().getId();
            try {
                ConfigScreenFactory<?> factory = entrypoint.getEntrypoint().getModConfigScreenFactory();
                if (factory != null) screens.putIfAbsent(modId, factory::create);
            } catch (RuntimeException | LinkageError e) {
                GlazedMenu.LOGGER.debug("Couldn't read the Mod Menu screen of {}", modId, e);
            }
        }
        return screens;
    }
}
