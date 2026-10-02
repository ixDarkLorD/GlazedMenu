package net.ixdarklord.glazedmenu.api.config.type;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Any value with a {@link Codec}: records, maps, item stacks... Edited as JSON in the config screen. The optional
 * validator returns an error message, or null for a valid value.
 */
public final class CodecType<T> implements ConfigType<T> {
    private final Codec<T> codec;
    private final Function<T, Component> validator;
    private final List<Component> description;

    CodecType(Codec<T> codec, Function<T, Component> validator, List<Component> description) {
        this.codec = codec;
        this.validator = validator;
        this.description = List.copyOf(description);
    }

    @Override
    public Codec<T> codec() {
        return this.codec;
    }

    @Override
    public ValidationResult<T> validate(T value) {
        Component error = this.validator.apply(value);
        return error == null ? ValidationResult.ok(value) : ValidationResult.error(error);
    }

    @Override
    public List<Component> describe() {
        return this.description;
    }
}
