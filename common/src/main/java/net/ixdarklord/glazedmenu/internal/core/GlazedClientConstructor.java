package net.ixdarklord.glazedmenu.internal.core;

import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.internal.source.cloth.ClothSource;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalSource;
import net.ixdarklord.glazedmenu.internal.source.mezz.MezzSource;
import net.ixdarklord.glazedmenu.internal.source.midnight.MidnightSource;
import net.ixdarklord.glazedmenu.internal.source.spec.ModConfigSource;
import net.ixdarklord.glazedmenu.internal.source.yacl.YaclSource;
import net.ixdarklord.glazedmenu.internal.style.ThemeEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.function.UnaryOperator;

// Glazed Menu's client setup, called by each loader module once it has set GlazedPlatform: its settings, its effects, and the
// config systems it reads (each only when its mod is here, so none of their classes load otherwise).
public final class GlazedClientConstructor {
    private static boolean ready;

    private GlazedClientConstructor() {}

    /** While the game loads. */
    public static void init() {
        GlazedPlatform platform = GlazedPlatform.get();
        GlazedSettings.init();
        net.ixdarklord.glazedmenu.internal.style.GlazedBrand.init();
        // Glazed Menu's own screens in its logo's colors.
        net.ixdarklord.glazedmenu.api.config.ConfigTheme.setForMod(GlazedMenu.MOD_ID, net.ixdarklord.glazedmenu.internal.style.GlazedBrand.THEME);
        ThemeEffects.init();
        ConfigSources.register(GlazedSettings.SOURCE);
        // (No CoolCatLib integration on 1.21: its own config screens are there.)
        if (platform.hasForgeConfigs()) ConfigSources.register(ModConfigSource.INSTANCE);
        if (platform.isModLoaded("cloth-config") || platform.isModLoaded("cloth_config")) ConfigSources.register(ClothSource.INSTANCE);
        if (platform.isModLoaded("yet_another_config_lib_v3")) ConfigSources.register(YaclSource.INSTANCE);
        if (platform.isModLoaded("midnightlib")) ConfigSources.register(MidnightSource.INSTANCE);
        if (platform.isModLoaded("mezz_config")) ConfigSources.register(MezzSource.INSTANCE);
    }

    /** Every client tick, from the loader module. Every mod has loaded by the first. */
    public static void tick(Minecraft minecraft) {
        if (!ready) onLoaded();
        GlazedClient.tick(minecraft);
    }

    /** Every mod has loaded: other systems' configs can be read, and mods' own screens are known. */
    public static void onLoaded() {
        if (ready) return;
        ready = true;
        ExternalSource.markReady();
        net.ixdarklord.glazedmenu.internal.modlist.ModUpdates.start();
        for (Map.Entry<String, UnaryOperator<Screen>> screen : GlazedPlatform.get().nativeScreens().entrySet()) {
            if (!screen.getKey().equals(GlazedMenu.MOD_ID)) ConfigSources.registerNativeScreen(screen.getKey(), screen.getValue());
        }
    }
}
