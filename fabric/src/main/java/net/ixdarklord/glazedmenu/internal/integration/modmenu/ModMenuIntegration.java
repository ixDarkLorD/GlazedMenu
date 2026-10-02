package net.ixdarklord.glazedmenu.internal.integration.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;

import java.util.HashMap;
import java.util.Map;

// Mod Menu's config buttons: Glazed Menu's own opens its settings, and every mod with configs Glazed Menu reads gets one. A mod's own screen still wins, unless it's one of the generic ones (see ModMenuMixin). Only loaded with Mod Menu.
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> ConfigScreens.create(parent, GlazedMenu.MOD_ID);
    }

    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new HashMap<>();
        for (String modId : ConfigSources.modIds()) {
            if (!modId.equals(GlazedMenu.MOD_ID)) factories.put(modId, parent -> ConfigScreens.create(parent, modId));
        }
        return factories;
    }
}
