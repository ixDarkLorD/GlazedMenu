package net.ixdarklord.glazedmenu.internal.modlist;

import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.internal.core.GenericScreens;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

// The loaded mods for the mod list, read from the loader once, with which ships inside which and which have settings.
public final class ModCatalog {
    private static @Nullable Map<String, ModEntry> mods;
    private static final Map<String, List<ModEntry>> CHILDREN = new LinkedHashMap<>();

    private ModCatalog() {}

    private static synchronized Map<String, ModEntry> mods() {
        if (mods == null) {
            Map<String, ModEntry> byId = new LinkedHashMap<>();
            GlazedPlatform.get().mods().stream()
                    .sorted(Comparator.comparing(mod -> mod.name().toLowerCase(Locale.ROOT)))
                    .forEach(mod -> byId.putIfAbsent(mod.id(), mod));
            // The game says little about itself: its maker, a description and its pages are filled in.
            ModEntry game = byId.get("minecraft");
            if (game != null) {
                byId.put("minecraft", new ModEntry(game.id(), game.name(), game.version(),
                        game.description().isBlank() ? Component.translatableWithFallback("glazedmenu.mods.minecraft.description",
                                "The game itself: build, explore and survive in a world made of blocks, alone or with friends. "
                                        + "Every other mod here builds on it.").getString() : game.description(),
                        game.authors().isEmpty() ? List.of("Mojang Studios") : game.authors(), game.contributors(),
                        game.license() != null ? game.license() : "Minecraft EULA",
                        game.homepage() != null ? game.homepage() : "https://www.minecraft.net",
                        game.issues() != null ? game.issues() : "https://bugs.mojang.com",
                        game.sources(), game.parent(), game.library(), game.side(), game.dependencies()));
            }
            byId.values().forEach(mod -> {
                if (mod.parent() != null && byId.containsKey(mod.parent())) CHILDREN.computeIfAbsent(mod.parent(), id -> new ArrayList<>()).add(mod);
            });
            mods = byId;
        }
        return mods;
    }

    /** Every mod, by name. */
    public static List<ModEntry> all() {
        return List.copyOf(mods().values());
    }

    public static @Nullable ModEntry get(String modId) {
        return mods().get(modId);
    }

    /** The mods shipped inside this one, or listed under it. */
    public static List<ModEntry> children(String modId) {
        mods();
        return CHILDREN.getOrDefault(modId, List.of());
    }

    /** Whether the mod is listed under another rather than on its own. */
    public static boolean isChild(ModEntry mod) {
        return mod.parent() != null && mods().containsKey(mod.parent());
    }

    /** Whether the mod has a settings screen: Glazed Menu's for its configs, or its own. */
    public static boolean hasSettings(String modId) {
        return modId.equals(GlazedMenu.MOD_ID) || modId.equals("minecraft") || GenericScreens.handles(modId) || ConfigSources.nativeScreen(modId) != null;
    }

    /** The mod's settings: Glazed Menu's screen for configs Glazed Menu reads (unless the mod has a screen of its own), else its own. */
    public static @Nullable Screen settings(Screen parent, String modId) {
        if (modId.equals(GlazedMenu.MOD_ID)) return ConfigScreens.create(parent, modId);
        // The game's settings are its options.
        if (modId.equals("minecraft")) return new net.minecraft.client.gui.screens.options.OptionsScreen(parent, net.minecraft.client.Minecraft.getInstance().options,
                net.minecraft.client.Minecraft.getInstance().level != null);
        UnaryOperator<Screen> own = ConfigSources.nativeScreen(modId);
        if (GenericScreens.handles(modId)) return GenericScreens.choose(modId, parent, () -> own != null ? own.apply(parent) : null);
        return own != null ? own.apply(parent) : null;
    }
}
