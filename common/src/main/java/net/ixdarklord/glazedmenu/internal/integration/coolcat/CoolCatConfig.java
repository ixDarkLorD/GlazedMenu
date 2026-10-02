package net.ixdarklord.glazedmenu.internal.integration.coolcat;

import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigDependency;
import net.ixdarklord.glazedmenu.api.config.ConfigGroup;
import net.ixdarklord.glazedmenu.api.config.ConfigNode;
import net.ixdarklord.glazedmenu.api.config.ConfigPreset;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.ixdarklord.glazedmenu.api.config.StartupSync;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A CoolCatLib: Core config as Glazed Menu's screens see it: the same tree, values read and written through Core (which
 * checks, syncs and saves them as usual), and Core's types and theme made Glazed Menu's.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class CoolCatConfig implements Config {
    private final ConfigImpl config;
    private final Group root;
    private final Map<net.ixdarklord.coolcatcore.api.config.ConfigValue<?>, Value<?>> values = new IdentityHashMap<>();
    private final Map<ChangeListener, net.ixdarklord.coolcatcore.api.config.Config.ChangeListener> listeners = new IdentityHashMap<>();

    CoolCatConfig(ConfigImpl config) {
        this.config = config;
        this.root = new Group(config.root(), null);
    }

    /** The Core config behind this one. */
    public ConfigImpl core() {
        return this.config;
    }

    /** The value standing for one of the Core config's. */
    <T> Value<T> value(net.ixdarklord.coolcatcore.api.config.ConfigValue<T> value) {
        return (Value<T>) this.values.get(value);
    }

    @Override
    public Identifier id() {
        return this.config.id();
    }

    @Override
    public ConfigScope scope() {
        return ConfigScope.valueOf(this.config.scope().name());
    }

    @Override
    public ConfigGroup root() {
        return this.root;
    }

    @Override
    public Component title() {
        return this.config.title();
    }

    @Override
    public ConfigTheme theme() {
        return CoolCatTypes.theme(this.config.theme());
    }

    @Override
    public List<String> comment() {
        return this.config.comment();
    }

    @Override
    public @Nullable Path filePath() {
        return this.config.filePath();
    }

    @Override
    public boolean isLoaded() {
        return this.config.isLoaded();
    }

    @Override
    public boolean isRemote() {
        return this.config.isRemote();
    }

    @Override
    public void save() {
        this.config.save();
    }

    @Override
    public void reload() {
        this.config.reload();
    }

    @Override
    public void resetAll() {
        this.config.resetAll();
    }

    @Override
    public Optional<ConfigValue<?>> find(String path) {
        return this.config.find(path).map(value -> this.values.get(value));
    }

    @Override
    public Map<String, ConfigPreset> presets() {
        Map<String, ConfigPreset> presets = new LinkedHashMap<>();
        this.config.presets().forEach((name, preset) -> {
            Map<ConfigValue<?>, Object> values = new LinkedHashMap<>();
            preset.values().forEach((value, presetValue) -> {
                Value<?> wrapped = CoolCatSource.INSTANCE.value(value);
                if (wrapped != null) values.put(wrapped, presetValue);
            });
            presets.put(name, new ConfigPreset(name, preset.displayName(), values));
        });
        return presets;
    }

    @Override
    public void applyPreset(ConfigPreset preset) {
        net.ixdarklord.coolcatcore.api.config.ConfigPreset core = this.config.presets().get(preset.name());
        if (core != null) this.config.applyPreset(core);
    }

    @Override
    public void addListener(ChangeListener listener) {
        net.ixdarklord.coolcatcore.api.config.Config.ChangeListener core = (config, changed) -> {
            Set<ConfigValue<?>> wrapped = new LinkedHashSet<>();
            changed.forEach(value -> wrapped.add(this.values.get(value)));
            listener.onChanged(this, wrapped);
        };
        this.listeners.put(listener, core);
        this.config.addListener(core);
    }

    @Override
    public boolean removeListener(ChangeListener listener) {
        net.ixdarklord.coolcatcore.api.config.Config.ChangeListener core = this.listeners.remove(listener);
        return core != null && this.config.removeListener(core);
    }

    @Override
    public String toString() {
        return "CoolCatConfig[" + this.config.id() + "]";
    }

    // --- The tree ---

    private abstract class Node implements ConfigNode {
        private final net.ixdarklord.coolcatcore.api.config.ConfigNode node;
        private final @Nullable Group parent;

        Node(net.ixdarklord.coolcatcore.api.config.ConfigNode node, @Nullable Group parent) {
            this.node = node;
            this.parent = parent;
        }

        @Override
        public String key() {
            return this.node.key();
        }

        @Override
        public String path() {
            return this.node.path();
        }

        @Override
        public Config config() {
            return CoolCatConfig.this;
        }

        @Override
        public @Nullable ConfigGroup parent() {
            return this.parent;
        }

        @Override
        public List<String> comment() {
            return this.node.comment();
        }

        @Override
        public String translationKey() {
            return this.node.translationKey();
        }

        @Override
        public Component displayName() {
            return this.node.displayName();
        }

        @Override
        public boolean isHidden() {
            return this.node.isHidden();
        }
    }

    final class Group extends Node implements ConfigGroup {
        private final List<ConfigNode> children = new ArrayList<>();

        Group(net.ixdarklord.coolcatcore.api.config.ConfigGroup group, @Nullable Group parent) {
            super(group, parent);
            for (net.ixdarklord.coolcatcore.api.config.ConfigNode child : group.children()) {
                if (child instanceof net.ixdarklord.coolcatcore.api.config.ConfigGroup subgroup) {
                    this.children.add(new Group(subgroup, this));
                } else if (child instanceof net.ixdarklord.coolcatcore.api.config.ConfigValue<?> value) {
                    Value<?> wrapped = new Value<>(value, this);
                    CoolCatConfig.this.values.put(value, wrapped);
                    this.children.add(wrapped);
                }
            }
        }

        @Override
        public List<ConfigNode> children() {
            return List.copyOf(this.children);
        }

        @Override
        public @Nullable ConfigNode child(String key) {
            return this.children.stream().filter(child -> child.key().equals(key)).findFirst().orElse(null);
        }

        @Override
        public Stream<ConfigValue<?>> values() {
            return this.children.stream().flatMap(child -> child instanceof Group group ? group.values() : Stream.of((ConfigValue<?>) child));
        }
    }

    final class Value<T> extends Node implements ConfigValue<T> {
        private final net.ixdarklord.coolcatcore.api.config.ConfigValue<T> value;
        private final ConfigType<T> type;
        private final Map<ChangeListener<T>, net.ixdarklord.coolcatcore.api.config.ConfigValue.ChangeListener<T>> listeners = new IdentityHashMap<>();

        Value(net.ixdarklord.coolcatcore.api.config.ConfigValue<T> value, Group parent) {
            super(value, parent);
            this.value = value;
            this.type = CoolCatTypes.type(value.type());
        }

        /** The Core value behind this one. */
        net.ixdarklord.coolcatcore.api.config.ConfigValue<T> core() {
            return this.value;
        }

        @Override
        public T get() {
            return this.value.get();
        }

        @Override
        public void set(T value) {
            this.value.set(value);
        }

        @Override
        public T getStored() {
            return this.value.getStored();
        }

        @Override
        public ValidationResult<T> validate(T value) {
            return CoolCatTypes.result(this.value.validate(value));
        }

        @Override
        public T getDefault() {
            return this.value.getDefault();
        }

        @Override
        public ConfigType<T> type() {
            return this.type;
        }

        @Override
        public RestartRequirement restartRequirement() {
            return RestartRequirement.valueOf(this.value.restartRequirement().name());
        }

        @Override
        public boolean isSynced() {
            return this.value.isSynced();
        }

        @Override
        public StartupSync startupSync() {
            return StartupSync.valueOf(this.value.startupSync().name());
        }

        @Override
        public List<String> aliases() {
            return this.value.aliases();
        }

        @Override
        public Optional<ConfigDependency<?>> dependency() {
            return this.value.dependency().map(dependency -> {
                Value<?> source = CoolCatSource.INSTANCE.value(dependency.source());
                return source == null ? null : new ConfigDependency(source, (Predicate) dependency.condition());
            });
        }

        @Override
        public void addListener(ChangeListener<T> listener) {
            net.ixdarklord.coolcatcore.api.config.ConfigValue.ChangeListener<T> core = listener::onChanged;
            this.listeners.put(listener, core);
            this.value.addListener(core);
        }

        @Override
        public boolean removeListener(ChangeListener<T> listener) {
            var core = this.listeners.remove(listener);
            return core != null && this.value.removeListener(core);
        }
    }
}
