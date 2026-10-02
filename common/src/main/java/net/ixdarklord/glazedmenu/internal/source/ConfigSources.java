package net.ixdarklord.glazedmenu.internal.source;

import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/**
 * Every config Glazed Menu's screens show, from every {@link ConfigSource}, and the mods whose own config screens it links
 * to without showing their configs itself.
 */
public final class ConfigSources {
    private static final List<ConfigSource> SOURCES = new CopyOnWriteArrayList<>();
    // Mod id -> the mod's own config screen, for mods whose configs no source reads.
    private static final Map<String, UnaryOperator<Screen>> NATIVE_SCREENS = new LinkedHashMap<>();
    // Whether each mod's own screen opens at all; checked once.
    private static final Map<String, Boolean> NATIVE_WORKS = new ConcurrentHashMap<>();

    private ConfigSources() {}

    public static void register(ConfigSource source) {
        SOURCES.add(source);
        GlazedMenu.LOGGER.debug("Showing configs from {}", source.name());
    }

    /** Every config, CoolCatLib's first, each source's in its own order. */
    public static List<Config> all() {
        List<Config> configs = new ArrayList<>();
        for (ConfigSource source : SOURCES) {
            try {
                configs.addAll(source.configs());
            } catch (RuntimeException | LinkageError e) {
                GlazedMenu.LOGGER.error("Couldn't list the configs of {}", source.name(), e);
            }
        }
        return configs;
    }

    public static List<Config> forMod(String modId) {
        return all().stream().filter(config -> config.modId().equals(modId)).toList();
    }

    /** Mods with at least one config, in the order their first config is listed. */
    public static Set<String> modIds() {
        Set<String> ids = new LinkedHashSet<>();
        all().forEach(config -> ids.add(config.modId()));
        return ids;
    }

    public static ConfigSource sourceOf(Config config) {
        for (ConfigSource source : SOURCES) {
            if (source.owns(config)) return source;
        }
        throw new IllegalArgumentException("No config source has " + config.id());
    }

    public static Access access(Config config) {
        return sourceOf(config).access(config);
    }

    public static void apply(Config config, Map<ConfigValue<?>, Object> values) {
        sourceOf(config).apply(config, values);
    }

    public static String fileName(Config config) {
        return sourceOf(config).fileName(config);
    }

    // --- Mods' own screens ---

    /** Links a mod's own config screen, shown in the list of every mod's configs when no source reads its configs. */
    public static void registerNativeScreen(String modId, UnaryOperator<Screen> factory) {
        synchronized (NATIVE_SCREENS) {
            NATIVE_SCREENS.putIfAbsent(modId, factory);
        }
    }

    /**
     * The mod's own config screen, if it has one Glazed Menu knows of that opens. Some mods list a screen that turns out to
     * be nothing (Configured's, with no configs of its own to show), so each is made once to see, the first time it's
     * asked for.
     */
    public static @Nullable UnaryOperator<Screen> nativeScreen(String modId) {
        UnaryOperator<Screen> factory;
        synchronized (NATIVE_SCREENS) {
            factory = NATIVE_SCREENS.get(modId);
        }
        if (factory == null) return null;
        return NATIVE_WORKS.computeIfAbsent(modId, id -> opens(id, factory)) ? factory : null;
    }

    private static boolean opens(String modId, UnaryOperator<Screen> factory) {
        try {
            return factory.apply(Minecraft.getInstance().gui.screen()) != null;
        } catch (RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.debug("The config screen of {} doesn't open", modId, e);
            return false;
        }
    }

    /** Mods with their own config screen (one that opens) and no config Glazed Menu shows. */
    public static List<String> nativeOnlyModIds() {
        Set<String> withConfigs = modIds();
        List<String> mods;
        synchronized (NATIVE_SCREENS) {
            mods = NATIVE_SCREENS.keySet().stream().filter(modId -> !withConfigs.contains(modId)).toList();
        }
        return mods.stream().filter(modId -> nativeScreen(modId) != null).toList();
    }
}
