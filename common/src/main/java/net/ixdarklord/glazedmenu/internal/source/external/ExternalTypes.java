package net.ixdarklord.glazedmenu.internal.source.external;

import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.api.config.type.NumberType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * CoolCatLib's type for another config system's value, by its Java class: booleans, numbers (with the system's range),
 * strings, enums, colors and lists of those. Anything else has no type, and is left out of the screens (the mod's own
 * screen or file still has it).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ExternalTypes {
    // A bounded int with at most this many steps gets a slider.
    private static final long MAX_SLIDER_STEPS = 1000;

    private ExternalTypes() {}

    /**
     * @param clazz        the value's class (a primitive's or its box)
     * @param defaultValue the default, which tells a list's element class
     * @param min          the lowest value allowed, or null
     * @param max          the highest value allowed, or null
     */
    public static @Nullable ConfigType<?> of(Class<?> clazz, @Nullable Object defaultValue, @Nullable Number min, @Nullable Number max) {
        return of(clazz, defaultValue, min, max, null);
    }

    /** As {@link #of(Class, Object, Number, Number)}; {@code slider} forces a slider on or off (null decides by range). */
    public static @Nullable ConfigType<?> of(Class<?> clazz, @Nullable Object defaultValue, @Nullable Number min, @Nullable Number max, @Nullable Boolean slider) {
        Class<?> boxed = box(clazz);
        if (boxed == Boolean.class) return ConfigTypes.BOOLEAN;
        if (boxed == String.class) return ConfigTypes.STRING;
        if (boxed == Integer.class) {
            NumberType<Integer> type = min != null || max != null
                    ? ConfigTypes.intRange(min != null ? min.intValue() : Integer.MIN_VALUE, max != null ? max.intValue() : Integer.MAX_VALUE)
                    : ConfigTypes.INT;
            boolean steps = min != null && max != null && max.longValue() - min.longValue() <= MAX_SLIDER_STEPS;
            return type.isBounded() && (slider != null ? slider : steps) ? type.withSlider(true) : type;
        }
        if (boxed == Long.class) {
            return min != null || max != null
                    ? ConfigTypes.longRange(min != null ? min.longValue() : Long.MIN_VALUE, max != null ? max.longValue() : Long.MAX_VALUE)
                    : ConfigTypes.LONG;
        }
        if (boxed == Double.class) {
            NumberType<Double> type = min != null || max != null
                    ? ConfigTypes.doubleRange(min != null ? min.doubleValue() : -Double.MAX_VALUE, max != null ? max.doubleValue() : Double.MAX_VALUE)
                    : ConfigTypes.DOUBLE;
            return type.isBounded() && Boolean.TRUE.equals(slider) ? type.withSlider(true) : type;
        }
        if (boxed == Float.class) {
            NumberType<Float> type = min != null || max != null
                    ? ConfigTypes.floatRange(min != null ? min.floatValue() : -Float.MAX_VALUE, max != null ? max.floatValue() : Float.MAX_VALUE)
                    : ConfigTypes.FLOAT;
            return type.isBounded() && Boolean.TRUE.equals(slider) ? type.withSlider(true) : type;
        }
        if (boxed.isEnum()) return ConfigTypes.enumOf((Class) boxed);
        if (boxed.getSuperclass() != null && boxed.getSuperclass().isEnum()) return ConfigTypes.enumOf((Class) boxed.getSuperclass());
        if (List.class.isAssignableFrom(boxed)) {
            Class<?> element = String.class;
            if (defaultValue instanceof List<?> list && !list.isEmpty() && list.get(0) != null) element = list.get(0).getClass();
            ConfigType<?> elementType = of(element, null, null, null);
            return elementType != null && !(elementType instanceof net.ixdarklord.glazedmenu.api.config.type.ListType<?>)
                    ? ConfigTypes.listOf(elementType) : null;
        }
        return null;
    }

    /** A color stored as an int: {@code #RRGGBB}, or {@code #AARRGGBB} with {@code alpha}. */
    public static ConfigType<Integer> color(boolean alpha) {
        return alpha ? ConfigTypes.COLOR_ALPHA : ConfigTypes.COLOR;
    }

    public static Class<?> box(Class<?> clazz) {
        if (!clazz.isPrimitive()) return clazz;
        if (clazz == boolean.class) return Boolean.class;
        if (clazz == int.class) return Integer.class;
        if (clazz == long.class) return Long.class;
        if (clazz == double.class) return Double.class;
        if (clazz == float.class) return Float.class;
        if (clazz == short.class) return Short.class;
        if (clazz == byte.class) return Byte.class;
        if (clazz == char.class) return Character.class;
        return clazz;
    }
}
