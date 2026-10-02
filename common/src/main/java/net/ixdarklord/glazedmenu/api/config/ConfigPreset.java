package net.ixdarklord.glazedmenu.api.config;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named set of values applied together, like "Performance" or "Quality", chosen from the config screen. Glazed Menu makes
 * them from the presets of the config systems it reads.
 */
public final class ConfigPreset {
    private final String name;
    private final Component displayName;
    private final Map<ConfigValue<?>, Object> values;

    public ConfigPreset(String name, Component displayName, Map<ConfigValue<?>, Object> values) {
        this.name = name;
        this.displayName = displayName;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public String name() {
        return this.name;
    }

    public Component displayName() {
        return this.displayName;
    }

    /** The values the preset sets; everything else keeps its value. */
    public Map<ConfigValue<?>, Object> values() {
        return this.values;
    }
}
