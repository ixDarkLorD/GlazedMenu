package net.ixdarklord.glazedmenu.api.config.type;

import com.mojang.serialization.Codec;
import net.ixdarklord.glazedmenu.internal.config.ConfigJson;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * What a config value is: how it's stored (a {@link Codec}, so every file format and the network work alike), what
 * it may be, and how it's typed as text in the config screen and commands.
 * <p>
 * Types are immutable and carry their constraints ({@link ConfigTypes#intRange(int, int)}), so a list's elements are
 * checked the same way as a single value. {@link ConfigTypes} has the built-in ones; the config screen picks an
 * editor by type, and falls back to a text box using {@link #parse}/{@link #format} for types it doesn't know.
 *
 * @param <T> the value's type; values are never null
 */
public interface ConfigType<T> {
    Codec<T> codec();

    /** Checks a value, possibly correcting it. */
    ValidationResult<T> validate(T value);

    /** Reads a value typed by a player, and validates it. By default the text is JSON for the codec. */
    default ValidationResult<T> parse(String text) {
        return ConfigJson.parse(this.codec(), text).then(this::validate);
    }

    /** The value as a player would type it, the inverse of {@link #parse}. By default compact JSON. */
    default String format(T value) {
        return ConfigJson.compact(ConfigJson.encode(this.codec(), value));
    }

    /** The constraints in words, for the file's comments and the screen's tooltips ("Range: 0 ~ 10"). */
    default List<Component> describe() {
        return List.of();
    }

    /** Values to suggest in commands. */
    default List<String> suggestions() {
        return List.of();
    }

    default boolean equals(T first, T second) {
        return Objects.equals(first, second);
    }
}
