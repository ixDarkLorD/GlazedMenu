package net.ixdarklord.glazedmenu.internal.source;

import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;

import java.util.List;
import java.util.Map;

/**
 * Where configs shown in Glazed Menu's screens come from, and how their edits are kept: CoolCatLib: Core's own configs, or
 * another config system's (NeoForge's, Cloth Config's...) seen through the same {@link Config} interfaces.
 */
public interface ConfigSource {
    /** The config system's name, for logs. */
    String name();

    /** Its configs, in order. Called often, so cheap. */
    List<? extends Config> configs();

    /** Whether a config is this source's. */
    boolean owns(Config config);

    /** Where edits to one of its configs go from this client. */
    Access access(Config config);

    /**
     * Applies values a player saved (each already valid for its value) and keeps them: saved to the file, or sent to
     * the server for {@link Access#REMOTE}.
     */
    void apply(Config config, Map<ConfigValue<?>, Object> values);

    /** The config's file name, as players would look for it in the config folder. */
    String fileName(Config config);
}
