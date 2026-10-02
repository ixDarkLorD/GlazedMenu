package net.ixdarklord.glazedmenu.api.config;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A group or a value in a config's tree.
 */
@ApiStatus.NonExtendable
public interface ConfigNode {
    /** The node's key in its group, as written to the file. */
    String key();

    /** The dotted path from the root ({@code "rendering.particles.enabled"}); empty for the root. */
    String path();

    Config config();

    /** The group holding this node; null for the root. */
    @Nullable ConfigGroup parent();

    /** The comment lines written above the node in the file, and shown in the config screen's tooltips. */
    List<String> comment();

    /**
     * The key of the node's name: {@code config.<modid>.<config>.<path>}, unless the builder set another. The
     * tooltip uses the same key with {@code .tooltip} appended, falling back to the comment.
     */
    String translationKey();

    /** The translated name, or a readable form of the key when there's no translation. */
    Component displayName();

    /** Hidden nodes are kept in the file but not shown in the config screen. */
    boolean isHidden();
}
