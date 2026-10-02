package net.ixdarklord.glazedmenu.internal.source.spec;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A config spec of NeoForge's or Forge's kind ({@code ModConfigSpec}, {@code ForgeConfigSpec}): a tree of values with
 * their comments, translation keys, ranges and checks. The two have the same shape in different classes; this is what
 * Glazed Menu reads of either.
 */
public interface SpecView {
    /** The values' tree: nested configs for groups, the spec's value handles for values, in declaration order. */
    UnmodifiableConfig values();

    @Nullable String groupComment(List<String> path);

    @Nullable String groupTranslationKey(List<String> path);

    /** The value behind a handle in {@link #values()}, or null when it isn't one. */
    @Nullable ValueView value(Object handle);

    /** Whether the spec has a file loaded (a server config has none outside a world). */
    boolean isLoaded();

    void save();

    interface ValueView {
        List<String> path();

        Object get();

        void set(Object value);

        Object defaultValue();

        /** The class the spec stores, when it says. */
        @Nullable Class<?> valueClass();

        @Nullable String comment();

        @Nullable String translationKey();

        @Nullable Number min();

        @Nullable Number max();

        RestartRequirement restart();

        boolean test(Object value);
    }
}
