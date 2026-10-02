package net.ixdarklord.glazedmenu.api.config;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/**
 * A named section of a config, holding values and nested groups in declaration order.
 */
@ApiStatus.NonExtendable
public interface ConfigGroup extends ConfigNode {
    List<ConfigNode> children();

    @Nullable ConfigNode child(String key);

    /** Every value in this group and its nested groups, depth first. */
    Stream<ConfigValue<?>> values();

    /** Whether this is the config's root group. */
    default boolean isRoot() {
        return this.parent() == null;
    }
}
