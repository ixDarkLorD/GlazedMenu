package net.ixdarklord.glazedmenu.api.config.type;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.function.Function;

/**
 * The built-in {@link ConfigType}s. The {@link net.ixdarklord.glazedmenu.api.config.ConfigBuilder} makes these for
 * single values; use them directly for list elements: {@code builder.list("ids", ConfigTypes.intRange(0, 9), List.of(1))}.
 */
public final class ConfigTypes {
    public static final BooleanType BOOLEAN = BooleanType.INSTANCE;
    public static final NumberType<Integer> INT = NumberType.INT.unbounded();
    public static final NumberType<Long> LONG = NumberType.LONG.unbounded();
    public static final NumberType<Float> FLOAT = NumberType.FLOAT.unbounded();
    public static final NumberType<Double> DOUBLE = NumberType.DOUBLE.unbounded();
    public static final StringType STRING = StringType.ANY;
    /** An opaque {@code #RRGGBB} color. */
    public static final ColorType COLOR = ColorType.RGB;
    /** A {@code #AARRGGBB} color. */
    public static final ColorType COLOR_ALPHA = ColorType.ARGB;
    public static final IdentifierType IDENTIFIER = IdentifierType.ANY;

    private ConfigTypes() {}

    public static NumberType<Integer> intRange(int min, int max) {
        return INT.withRange(min, max);
    }

    public static NumberType<Long> longRange(long min, long max) {
        return LONG.withRange(min, max);
    }

    public static NumberType<Float> floatRange(float min, float max) {
        return FLOAT.withRange(min, max);
    }

    public static NumberType<Double> doubleRange(double min, double max) {
        return DOUBLE.withRange(min, max);
    }

    public static StringType string(int maxLength) {
        return STRING.withMaxLength(maxLength);
    }

    /** Text matching a regular expression as a whole. */
    public static StringType pattern(String regex) {
        return STRING.withPattern(regex);
    }

    public static <E extends Enum<E>> EnumType<E> enumOf(Class<E> enumClass) {
        return new EnumType<>(enumClass);
    }

    /** An id whose suggestions come from a built-in registry, like {@code Registries.ITEM}. */
    public static IdentifierType identifier(ResourceKey<? extends Registry<?>> registry) {
        return new IdentifierType(registry);
    }

    public static <E> ListType<E> listOf(ConfigType<E> elementType) {
        return new ListType<>(elementType, 0, Integer.MAX_VALUE);
    }

    public static <E> ListType<E> listOf(ConfigType<E> elementType, int minSize, int maxSize) {
        return new ListType<>(elementType, minSize, maxSize);
    }

    /** Any codec's values, edited as JSON. */
    public static <T> CodecType<T> codec(Codec<T> codec) {
        return new CodecType<>(codec, value -> null, List.of());
    }

    /**
     * Any codec's values, checked by a validator returning an error message, or null for a valid value.
     */
    public static <T> CodecType<T> codec(Codec<T> codec, Function<T, Component> validator, Component... description) {
        return new CodecType<>(codec, validator, List.of(description));
    }
}
