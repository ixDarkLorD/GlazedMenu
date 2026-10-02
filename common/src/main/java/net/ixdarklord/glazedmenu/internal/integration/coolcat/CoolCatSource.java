package net.ixdarklord.glazedmenu.internal.integration.coolcat;

import com.google.gson.JsonObject;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.ConfigManager;
import net.ixdarklord.coolcatcore.internal.config.ConfigValueImpl;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.internal.source.ConfigSource;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// CoolCatLib: Core's configs: edited here and saved to their files, or sent to the server for a synced config the
// player may change there. Each Core config is shown through one CoolCatConfig, kept for as long as the config.
public final class CoolCatSource implements ConfigSource {
    public static final CoolCatSource INSTANCE = new CoolCatSource();
    private final Map<ConfigImpl, CoolCatConfig> wrapped = Collections.synchronizedMap(new IdentityHashMap<>());

    private CoolCatSource() {}

    /** The Glazed Menu config standing for a Core one. */
    public CoolCatConfig config(ConfigImpl config) {
        return this.wrapped.computeIfAbsent(config, CoolCatConfig::new);
    }

    /** The Glazed Menu value standing for a Core one, or null for a value of no registered config. */
    @Nullable CoolCatConfig.Value<?> value(net.ixdarklord.coolcatcore.api.config.ConfigValue<?> value) {
        return value instanceof ConfigValueImpl<?> impl && impl.configOrNull() != null ? this.config(impl.configOrNull()).value(value) : null;
    }

    @Override
    public String name() {
        return "CoolCatLib";
    }

    @Override
    public List<CoolCatConfig> configs() {
        return ConfigManager.all().stream().map(this::config).toList();
    }

    @Override
    public boolean owns(Config config) {
        return config instanceof CoolCatConfig;
    }

    @Override
    public Access access(Config config) {
        return Access.valueOf(ClientConfigManager.access(((CoolCatConfig) config).core()).name());
    }

    @Override
    public void apply(Config config, Map<ConfigValue<?>, Object> values) {
        ConfigImpl impl = ((CoolCatConfig) config).core();
        switch (this.access(config)) {
            case LOCAL -> {
                Map<ConfigValueImpl<?>, Object> changes = new LinkedHashMap<>();
                values.forEach((value, newValue) -> changes.put(core(value), newValue));
                impl.applyChanges(changes);
                impl.save();
            }
            case REMOTE -> {
                JsonObject changes = new JsonObject();
                values.forEach((value, newValue) -> changes.add(value.path(), core(value).encodeUnchecked(newValue)));
                ClientConfigManager.sendUpdate(impl, changes);
            }
            case READ_ONLY, UNAVAILABLE -> {
            }
        }
    }

    private static ConfigValueImpl<?> core(ConfigValue<?> value) {
        return (ConfigValueImpl<?>) ((CoolCatConfig.Value<?>) value).core();
    }

    @Override
    public String fileName(Config config) {
        return ((CoolCatConfig) config).core().fileName();
    }
}
