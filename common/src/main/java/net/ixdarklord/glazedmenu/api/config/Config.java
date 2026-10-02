package net.ixdarklord.glazedmenu.api.config;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A config as Glazed Menu's screens show it: a tree of {@link ConfigGroup groups} and typed {@link ConfigValue values}.
 * Glazed Menu reads other config systems (CoolCatLib's, NeoForge's, Cloth Config's...) into this shape; mods don't
 * implement it.
 */
@ApiStatus.NonExtendable
public interface Config {
    /** {@code <modid>:<name>}. */
    ResourceLocation id();

    default String modId() {
        return this.id().getNamespace();
    }

    default String name() {
        return this.id().getPath();
    }

    ConfigScope scope();

    ConfigGroup root();

    /** The config's title. */
    Component title();

    /** How the config's screen looks: its own theme, or the mod's. */
    ConfigTheme theme();

    /** The config's comment lines. */
    List<String> comment();

    /** The config file, or null when it has none. */
    @Nullable Path filePath();

    /** Whether the values come from the file (or a server) rather than being defaults waiting for a world. */
    boolean isLoaded();

    /** Whether this client currently shows a server's values. */
    boolean isRemote();

    /** Whether a {@link ConfigScope#STARTUP} config has saved changes that apply after a restart. */
    default boolean isRestartPending() {
        return this.values().anyMatch(ConfigValue::isRestartPending);
    }

    /** Writes the current values to the file. */
    void save();

    /** Reads the file again. */
    void reload();

    /** Sets every value back to its default, in memory; {@link #save()} writes it. */
    void resetAll();

    /** The value at a dotted path, like {@code "rendering.particles"}. */
    Optional<ConfigValue<?>> find(String path);

    /** Every value, depth first. */
    default Stream<ConfigValue<?>> values() {
        return this.root().values();
    }

    /** The config's presets, by name. */
    Map<String, ConfigPreset> presets();

    /** Sets a preset's values, in memory; {@link #save()} writes them. */
    void applyPreset(ConfigPreset preset);

    /** Called with the values that changed, whenever any do. */
    void addListener(ChangeListener listener);

    boolean removeListener(ChangeListener listener);

    @FunctionalInterface
    interface ChangeListener {
        void onChanged(Config config, Set<ConfigValue<?>> changed);
    }
}
