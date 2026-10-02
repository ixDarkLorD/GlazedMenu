package net.ixdarklord.glazedmenu.internal.source.external;

import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.source.ConfigSource;
import net.ixdarklord.glazedmenu.internal.source.Values;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A config system other than CoolCatLib's: its configs are found once (the first time a screen asks; mods register
 * theirs while they load) and kept, each an {@link ExternalConfig} reading and writing the system's values.
 */
public abstract class ExternalSource implements ConfigSource {
    // Mods register their configs while the game loads (on Fabric, in entry points that may run after Glazed Menu's), so
    // they're looked for once it has.
    private static volatile boolean ready;
    private @Nullable List<ExternalConfig> configs;

    /** Lets the sources look for configs: every mod has loaded. */
    public static void markReady() {
        ready = true;
    }

    /** Finds the system's configs. Runs once, on the client thread, after every mod has loaded. */
    protected abstract List<ExternalConfig> discover();

    @Override
    public synchronized List<ExternalConfig> configs() {
        if (!ready) return List.of();
        if (this.configs == null) {
            try {
                this.configs = List.copyOf(this.discover());
                if (!this.configs.isEmpty()) GlazedMenu.LOGGER.info("Found {} configs of {}", this.configs.size(), this.name());
            } catch (RuntimeException | LinkageError e) {
                GlazedMenu.LOGGER.error("Couldn't read the configs of {}", this.name(), e);
                this.configs = List.of();
            }
        }
        return this.configs;
    }

    @Override
    public boolean owns(Config config) {
        return config instanceof ExternalConfig external && this.configs().contains(external);
    }

    @Override
    public Access access(Config config) {
        return ((ExternalConfig) config).access();
    }

    @Override
    public void apply(Config config, Map<ConfigValue<?>, Object> values) {
        if (this.access(config) != Access.LOCAL) return;
        values.forEach(Values::set);
        config.save();
    }

    @Override
    public String fileName(Config config) {
        return ((ExternalConfig) config).fileName();
    }
}
