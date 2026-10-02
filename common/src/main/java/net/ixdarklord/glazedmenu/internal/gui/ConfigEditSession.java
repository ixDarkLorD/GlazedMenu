package net.ixdarklord.glazedmenu.internal.gui;

import net.ixdarklord.glazedmenu.internal.core.GlazedClient;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.api.config.ConfigDependency;
import net.ixdarklord.glazedmenu.api.config.ConfigPreset;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.ixdarklord.glazedmenu.api.editor.EditSlot;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.internal.source.Values;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// The changes made in one config's screens: pending values (applied on save), invalid input, and undo/redo history.
public final class ConfigEditSession {
    private static final long MERGE_WINDOW_MS = 1000;
    private static final int MAX_HISTORY = 200;

    private final Config config;
    private final Access access;
    private final Map<ConfigValue<?>, Object> pending = new LinkedHashMap<>();
    private final Map<ConfigValue<?>, Component> errors = new HashMap<>();
    private final Deque<Edit> undo = new ArrayDeque<>();
    private final Deque<Edit> redo = new ArrayDeque<>();

    public ConfigEditSession(Config config) {
        this.config = config;
        this.access = ConfigSources.access(config);
    }

    public Config config() {
        return this.config;
    }

    public Access access() {
        return this.access;
    }

    // --- Values ---

    public Object get(ConfigValue<?> value) {
        return this.pending.getOrDefault(value, value.getStored());
    }

    public boolean isModified(ConfigValue<?> value) {
        return this.pending.containsKey(value) || this.errors.containsKey(value);
    }

    public boolean isDefault(ConfigValue<?> value) {
        return Values.same(value, this.get(value), value.getDefault());
    }

    public Optional<Component> error(ConfigValue<?> value) {
        return Optional.ofNullable(this.errors.get(value));
    }

    public int modifiedCount() {
        return this.pending.size();
    }

    public boolean hasErrors() {
        return !this.errors.isEmpty();
    }

    public Collection<Component> errors() {
        return this.errors.values();
    }

    /** Whether the player may change the value: the config is editable here, and a server-only value is visible. */
    public boolean isEditable(ConfigValue<?> value) {
        return switch (this.access) {
            case LOCAL -> true;
            case REMOTE -> value.isSynced() || Minecraft.getInstance().isLocalServer();
            case READ_ONLY, UNAVAILABLE -> false;
        };
    }

    /** Whether the value's dependency is met by the pending values. */
    public boolean isActive(ConfigValue<?> value) {
        Optional<ConfigDependency<?>> dependency = value.dependency();
        if (dependency.isEmpty()) return true;
        ConfigValue<?> source = dependency.get().source();
        Object sourceValue = source.config() == this.config ? this.get(source) : source.get();
        return dependency.get().isMetBy(sourceValue);
    }

    /** Whether a remote player sees this value at all: server-only values aren't sent to them. */
    public boolean isVisible(ConfigValue<?> value) {
        return !value.isHidden() && (this.access != Access.REMOTE && this.access != Access.READ_ONLY
                || value.isSynced() || Minecraft.getInstance().isLocalServer());
    }

    /** Sets a pending value, checked by the value's validators; invalid values are reported instead. */
    public void set(ConfigValue<?> value, Object newValue) {
        ValidationResult<?> result = Values.validate(value, newValue);
        if (!result.isOk()) {
            this.errors.put(value, result.message().orElse(Component.empty()));
            return;
        }
        this.errors.remove(value);
        Object before = this.get(value);
        if (Values.same(value, before, newValue)) return;
        this.record(new Edit(List.of(new Change(value, before, newValue)), System.currentTimeMillis()));
        this.store(value, newValue);
    }

    public void setError(ConfigValue<?> value, Component error) {
        this.errors.put(value, error);
    }

    /** Sets several pending values as one undoable step. */
    public void setAll(Map<ConfigValue<?>, Object> values) {
        List<Change> changes = new ArrayList<>();
        values.forEach((value, newValue) -> {
            this.errors.remove(value);
            Object before = this.get(value);
            if (!Values.same(value, before, newValue)) changes.add(new Change(value, before, newValue));
        });
        if (changes.isEmpty()) return;
        this.record(new Edit(changes, 0));
        changes.forEach(change -> this.store(change.value, change.after));
    }

    public void resetToDefaults(Collection<ConfigValue<?>> values) {
        Map<ConfigValue<?>, Object> defaults = new LinkedHashMap<>();
        for (ConfigValue<?> value : values) {
            if (this.isEditable(value)) defaults.put(value, value.getDefault());
        }
        this.setAll(defaults);
    }

    /** Drops pending changes, as if nothing had been edited. */
    public void discard(Collection<ConfigValue<?>> values) {
        Map<ConfigValue<?>, Object> current = new LinkedHashMap<>();
        for (ConfigValue<?> value : values) {
            this.errors.remove(value);
            current.put(value, value.getStored());
        }
        this.setAll(current);
    }

    public void applyPreset(ConfigPreset preset) {
        Map<ConfigValue<?>, Object> values = new LinkedHashMap<>();
        preset.values().forEach((value, presetValue) -> {
            if (this.isEditable(value)) values.put(value, presetValue);
        });
        this.setAll(values);
    }

    // Typing in a text box makes one edit per key; edits to the same value close together undo as one.
    private void record(Edit edit) {
        this.redo.clear();
        Edit last = this.undo.peek();
        if (last != null && edit.time > 0 && last.time > 0 && edit.time - last.time < MERGE_WINDOW_MS
                && last.changes.size() == 1 && edit.changes.size() == 1 && last.changes.getFirst().value == edit.changes.getFirst().value) {
            this.undo.pop();
            edit = new Edit(List.of(new Change(last.changes.getFirst().value, last.changes.getFirst().before, edit.changes.getFirst().after)), edit.time);
        }
        this.undo.push(edit);
        while (this.undo.size() > MAX_HISTORY) this.undo.removeLast();
    }

    private void store(ConfigValue<?> value, Object newValue) {
        if (Values.same(value, newValue, value.getStored())) this.pending.remove(value);
        else this.pending.put(value, newValue);
    }

    public boolean canUndo() {
        return !this.undo.isEmpty();
    }

    public boolean canRedo() {
        return !this.redo.isEmpty();
    }

    public void undo() {
        Edit edit = this.undo.poll();
        if (edit == null) return;
        for (Change change : edit.changes) {
            this.errors.remove(change.value);
            this.store(change.value, change.before);
        }
        this.redo.push(edit);
    }

    public void redo() {
        Edit edit = this.redo.poll();
        if (edit == null) return;
        for (Change change : edit.changes) {
            this.errors.remove(change.value);
            this.store(change.value, change.after);
        }
        this.undo.push(new Edit(edit.changes, 0));
    }

    /** Drops pending values that the config now holds anyway (after the server's values arrived). */
    public void onConfigChanged() {
        this.pending.entrySet().removeIf(entry -> Values.same(entry.getKey(), entry.getValue(), entry.getKey().getStored()));
    }

    /**
     * Applies the pending values: locally (and saves the file), or by sending them to the server.
     *
     * @return the strictest restart the changes need
     */
    public RestartRequirement save() {
        if (this.pending.isEmpty() || this.hasErrors()) return RestartRequirement.NONE;
        RestartRequirement restart = Values.restartFor(this.pending.keySet());
        if (this.access == Access.REMOTE || this.access == Access.LOCAL) {
            ConfigSources.apply(this.config, new LinkedHashMap<>(this.pending));
        }
        this.pending.clear();
        this.undo.clear();
        this.redo.clear();
        return restart;
    }

    /** Saves as {@link #save()} does, and tells the player: a toast for a local save, and what must restart. */
    public void saveAndNotify() {
        Access access = this.access;
        RestartRequirement restart = this.save();
        // One toast, titled with the config's name: what must restart, or that it saved.
        Component message = switch (restart) {
            case GAME -> Component.translatableWithFallback("glazedmenu.toast.restart_game",
                    "Restart the game to apply every change").withStyle(ChatFormatting.GOLD);
            case WORLD -> Component.translatableWithFallback("glazedmenu.toast.restart_world",
                    "Rejoin the world to apply every change").withStyle(ChatFormatting.GOLD);
            case NONE -> access == Access.LOCAL
                    ? Component.translatableWithFallback("glazedmenu.toast.saved", "Config saved") : null;
        };
        if (message != null) GlazedClient.toast(this.config.title(), message);
    }

    /** A slot for one of this config's values. */
    public <T> EditSlot<T> slot(ConfigValue<T> value) {
        return new EditSlot<>() {
            @Override
            public ConfigType<T> type() {
                return value.type();
            }

            @Override
            @SuppressWarnings("unchecked")
            public T get() {
                return (T) ConfigEditSession.this.get(value);
            }

            @Override
            public void set(T newValue) {
                ConfigEditSession.this.set(value, newValue);
            }

            @Override
            public void setInvalid(Component error) {
                ConfigEditSession.this.setError(value, error);
            }

            @Override
            public Optional<Component> error() {
                return ConfigEditSession.this.error(value);
            }

            @Override
            public Component name() {
                return value.displayName();
            }
        };
    }

    private record Change(ConfigValue<?> value, @Nullable Object before, Object after) {}

    private record Edit(List<Change> changes, long time) {}
}
