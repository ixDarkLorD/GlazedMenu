package net.ixdarklord.glazedmenu.internal.source.external;

import net.ixdarklord.glazedmenu.api.config.ConfigGroup;
import net.ixdarklord.glazedmenu.api.config.ConfigNode;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ExternalGroup extends ExternalNode implements ConfigGroup {
    private final List<ExternalNode> children = new ArrayList<>();

    ExternalGroup(String key, @Nullable ExternalGroup parent, @Nullable String translationKey, @Nullable String tooltipKey,
                  String fallbackName, List<String> comment) {
        super(key, parent, translationKey, tooltipKey, fallbackName, comment);
    }

    void add(ExternalNode node) {
        this.children.add(node);
    }

    @Override
    void attach(ExternalConfig config) {
        super.attach(config);
        this.children.forEach(child -> child.attach(config));
    }

    /** Whether the group has no values, here or in its nested groups. */
    boolean isEmpty() {
        return this.values().findAny().isEmpty();
    }

    void removeEmptyGroups() {
        this.children.removeIf(child -> child instanceof ExternalGroup group && group.isEmpty());
        this.children.forEach(child -> {
            if (child instanceof ExternalGroup group) group.removeEmptyGroups();
        });
    }

    @Override
    public List<ConfigNode> children() {
        return List.copyOf(this.children);
    }

    @Override
    public @Nullable ConfigNode child(String key) {
        for (ExternalNode child : this.children) {
            if (child.key().equals(key)) return child;
        }
        return null;
    }

    @Override
    public Stream<ConfigValue<?>> values() {
        return this.children.stream().flatMap(child -> child instanceof ExternalGroup group ? group.values() : Stream.of((ConfigValue<?>) child));
    }
}
