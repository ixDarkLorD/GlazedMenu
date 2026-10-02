package net.ixdarklord.glazedmenu.internal.source.external;

import net.ixdarklord.glazedmenu.internal.core.Names;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Builds an {@link ExternalConfig}: groups pushed and popped around their values, as a config system declares them. */
public final class ExternalConfigBuilder {
    private final String modId;
    private final String name;
    private final ConfigScope scope;
    private final ExternalGroup root;
    private final Deque<ExternalGroup> groups = new ArrayDeque<>();
    private Component title;
    private String fileName;
    private @Nullable Path filePath;

    /**
     * @param name the config's name within the mod, which may hold any characters (they're cleaned up for its id)
     */
    public ExternalConfigBuilder(String modId, String name, ConfigScope scope) {
        this.modId = modId;
        this.name = name;
        this.scope = scope;
        this.root = new ExternalGroup("", null, null, null, "", List.of());
        this.groups.push(this.root);
        this.title = Component.literal(Names.prettify(modId) + " " + Names.prettify(name));
        this.fileName = name;
    }

    public ExternalConfigBuilder title(Component title) {
        this.title = title;
        return this;
    }

    public ExternalConfigBuilder file(String fileName, @Nullable Path filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
        return this;
    }

    /** Opens a group; values and groups declared until {@link #pop()} go in it. */
    public ExternalConfigBuilder push(String key, @Nullable String translationKey, @Nullable String tooltipKey, List<String> comment) {
        ExternalGroup group = new ExternalGroup(key, this.groups.peek(), translationKey, tooltipKey, Names.prettify(key), comment);
        this.groups.peek().add(group);
        this.groups.push(group);
        return this;
    }

    public ExternalConfigBuilder pop() {
        if (this.groups.size() > 1) this.groups.pop();
        return this;
    }

    /** Starts a value in the current group; {@link Value#add()} adds it. */
    public <T> Value<T> value(String key, ConfigType<T> type, T defaultValue, ExternalValue.Binding<T> binding) {
        return new Value<>(key, type, defaultValue, binding);
    }

    /**
     * Builds the config.
     *
     * @param access where edits go from this client
     * @param saver  saves the values through the config system, after a player's edits were set
     */
    public ExternalConfig build(Supplier<Access> access, Runnable saver) {
        this.root.removeEmptyGroups();
        return new ExternalConfig(ResourceLocation.fromNamespaceAndPath(cleanId(this.modId), cleanId(this.name)), this.scope, this.root,
                this.title, this.fileName, this.filePath, access, saver);
    }

    // Ids allow fewer characters than mod ids and file names do.
    private static String cleanId(String text) {
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return cleaned.isEmpty() ? "config" : cleaned;
    }

    public final class Value<T> {
        private final String key;
        private final ConfigType<T> type;
        private final T defaultValue;
        private final ExternalValue.Binding<T> binding;
        private @Nullable String translationKey;
        private @Nullable String tooltipKey;
        private List<String> comment = List.of();
        private RestartRequirement restart = RestartRequirement.NONE;
        private @Nullable Predicate<Object> check;
        private @Nullable String name;

        private Value(String key, ConfigType<T> type, T defaultValue, ExternalValue.Binding<T> binding) {
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
            this.binding = binding;
        }

        /** The config system's translation keys for the name and the description, when it has them. */
        public Value<T> translation(@Nullable String translationKey, @Nullable String tooltipKey) {
            this.translationKey = translationKey;
            this.tooltipKey = tooltipKey;
            return this;
        }

        /** The name shown when there's no translation, instead of the key made readable. */
        public Value<T> name(@Nullable String name) {
            this.name = name;
            return this;
        }

        public Value<T> comment(List<String> comment) {
            this.comment = comment;
            return this;
        }

        public Value<T> restart(RestartRequirement restart) {
            this.restart = restart;
            return this;
        }

        /** The config system's own check, on top of the type's. */
        public Value<T> check(@Nullable Predicate<Object> check) {
            this.check = check;
            return this;
        }

        public ExternalConfigBuilder add() {
            ExternalGroup parent = ExternalConfigBuilder.this.groups.peek();
            parent.add(new ExternalValue<>(this.key, parent, this.translationKey, this.tooltipKey,
                    this.name != null ? this.name : Names.prettify(this.key),
                    this.comment, this.type, this.defaultValue, this.binding, this.restart, this.check));
            return ExternalConfigBuilder.this;
        }
    }
}
