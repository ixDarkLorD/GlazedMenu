package net.ixdarklord.glazedmenu.internal.core;

import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Which config screen a mod's config button opens. A mod's own screen stays; but where the button would open nothing,
 * or a screen generated for any mod by a config system (NeoForge's, Forge Config API Port's, Configured's,
 * MidnightLib's, and MezzConfig GUI's when the player chose to replace it), and Glazed Menu shows that mod's configs, it opens Glazed Menu's instead. Cloth Config's and YACL's screens
 * stay, as mods often build those by hand.
 */
public final class GenericScreens {
    // Screens made for any mod, by class name: their mods aren't needed to check.
    private static final List<String> GENERIC = List.of(
            "net.neoforged.neoforge.client.gui.ConfigurationScreen",
            "com.mrcrayfish.configured.",
            "fuzs.forgeconfigapiport.",
            "eu.midnightdust.lib.config.");

    private GenericScreens() {}

    /** Whether Glazed Menu has configs of the mod to show. */
    public static boolean handles(String modId) {
        return !modId.equals(GlazedMenu.MOD_ID) && !ConfigSources.forMod(modId).isEmpty();
    }

    public static boolean isGeneric(@Nullable Screen screen) {
        if (screen == null) return true;
        String name = screen.getClass().getName();
        // MezzConfig GUI's, when the player chose to replace them.
        if (name.startsWith("net.mezzdev.") && GlazedSettings.replaceMezzConfigScreens()) return true;
        return GENERIC.stream().anyMatch(name::startsWith);
    }

    /** The screen a mod's button opens: the one the loader found, unless it's a generic one Glazed Menu can replace. */
    public static @Nullable Screen choose(String modId, @Nullable Screen parent, Supplier<@Nullable Screen> original) {
        if (!handles(modId)) return original.get();
        // FTB Library's editor, unless the player chose to replace it.
        if (GlazedPlatform.get().isModLoaded("ftblibrary")) {
            Screen ftb = net.ixdarklord.glazedmenu.internal.source.ftb.FtbSource.ownScreen(modId);
            if (ftb != null) return ftb;
        }
        Screen screen;
        try {
            screen = original.get();
        } catch (RuntimeException e) {
            screen = null;
        }
        return isGeneric(screen) ? ConfigScreens.create(parent, modId) : screen;
    }
}
