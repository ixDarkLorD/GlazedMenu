package net.ixdarklord.glazedmenu.api.config.type;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.Function;

/**
 * An {@code int}, {@code long}, {@code float} or {@code double}, optionally within a range. Out-of-range values are
 * clamped when a file is read. A bounded number can be shown as a slider.
 */
public final class NumberType<N extends Number & Comparable<N>> implements ConfigType<N> {
    public static final Kind<Integer> INT = new Kind<>("int", Codec.INT, Integer::parseInt, d -> (int) Math.round(d), Integer.MIN_VALUE, Integer.MAX_VALUE, true);
    public static final Kind<Long> LONG = new Kind<>("long", Codec.LONG, Long::parseLong, Math::round, Long.MIN_VALUE, Long.MAX_VALUE, true);
    public static final Kind<Float> FLOAT = new Kind<>("float", Codec.FLOAT, Float::parseFloat, d -> (float) d, -Float.MAX_VALUE, Float.MAX_VALUE, false);
    public static final Kind<Double> DOUBLE = new Kind<>("double", Codec.DOUBLE, Double::parseDouble, d -> d, -Double.MAX_VALUE, Double.MAX_VALUE, false);

    private final Kind<N> kind;
    private final N min;
    private final N max;
    private final boolean slider;

    NumberType(Kind<N> kind, N min, N max, boolean slider) {
        if (min.compareTo(max) > 0) throw new IllegalArgumentException("min " + min + " is greater than max " + max);
        if (slider && (min.equals(kind.lowest) || max.equals(kind.highest))) {
            throw new IllegalArgumentException("A slider needs a range");
        }
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.slider = slider;
    }

    public Kind<N> kind() {
        return this.kind;
    }

    public N min() {
        return this.min;
    }

    public N max() {
        return this.max;
    }

    /** Whether the config screen shows a slider rather than a text box. */
    public boolean isSlider() {
        return this.slider;
    }

    /** Whether a range narrower than the whole type was set. */
    public boolean isBounded() {
        return !this.min.equals(this.kind.lowest) || !this.max.equals(this.kind.highest);
    }

    public boolean isIntegral() {
        return this.kind.integral;
    }

    public NumberType<N> withRange(N min, N max) {
        return new NumberType<>(this.kind, min, max, this.slider);
    }

    public NumberType<N> withSlider(boolean slider) {
        return new NumberType<>(this.kind, this.min, this.max, slider);
    }

    /** The number nearest to a double, clamped into the range. */
    public N fromDouble(double value) {
        N number = this.kind.fromDouble.apply(value);
        if (number.compareTo(this.min) < 0) return this.min;
        if (number.compareTo(this.max) > 0) return this.max;
        return number;
    }

    @Override
    public Codec<N> codec() {
        return this.kind.codec;
    }

    @Override
    public ValidationResult<N> validate(N value) {
        if (!this.kind.integral && !Double.isFinite(value.doubleValue())) {
            return ValidationResult.error(Component.translatableWithFallback("glazedmenu.error.finite", "Must be a finite number"));
        }
        if (value.compareTo(this.min) < 0) {
            return ValidationResult.corrected(this.min, Component.translatableWithFallback("glazedmenu.error.min", "Must be at least %s", this.format(this.min)));
        }
        if (value.compareTo(this.max) > 0) {
            return ValidationResult.corrected(this.max, Component.translatableWithFallback("glazedmenu.error.max", "Must be at most %s", this.format(this.max)));
        }
        return ValidationResult.ok(value);
    }

    @Override
    public ValidationResult<N> parse(String text) {
        N value;
        try {
            value = this.kind.parser.apply(text.trim().replace("_", ""));
        } catch (NumberFormatException e) {
            return ValidationResult.error(this.kind.integral
                    ? Component.translatableWithFallback("glazedmenu.error.integer", "Must be a whole number")
                    : Component.translatableWithFallback("glazedmenu.error.number", "Must be a number"));
        }
        ValidationResult<N> result = this.validate(value);
        // Typed values must already be in range; only files get corrected.
        return result.isCorrected() ? ValidationResult.error(result.message().orElseThrow()) : result;
    }

    @Override
    public String format(N value) {
        return value.toString();
    }

    @Override
    public List<Component> describe() {
        if (!this.isBounded()) return List.of();
        String min = this.min.equals(this.kind.lowest) ? "-∞" : this.format(this.min);
        String max = this.max.equals(this.kind.highest) ? "∞" : this.format(this.max);
        return List.of(Component.translatableWithFallback("glazedmenu.info.range", "Range: %s ~ %s", min, max));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NumberType<?> type && type.kind == this.kind && type.min.equals(this.min) && type.max.equals(this.max) && type.slider == this.slider;
    }

    @Override
    public int hashCode() {
        return this.kind.hashCode() * 31 + this.min.hashCode() * 17 + this.max.hashCode();
    }

    /** One of the four number types: {@link #INT}, {@link #LONG}, {@link #FLOAT}, {@link #DOUBLE}. */
    public static final class Kind<N extends Number & Comparable<N>> {
        private final String name;
        private final Codec<N> codec;
        private final Function<String, N> parser;
        private final DoubleFunction<N> fromDouble;
        private final N lowest;
        private final N highest;
        private final boolean integral;

        private Kind(String name, Codec<N> codec, Function<String, N> parser, DoubleFunction<N> fromDouble, N lowest, N highest, boolean integral) {
            this.name = name;
            this.codec = codec;
            this.parser = parser;
            this.fromDouble = fromDouble;
            this.lowest = lowest;
            this.highest = highest;
            this.integral = integral;
        }

        /** The whole type's range, unbounded. */
        public NumberType<N> unbounded() {
            return new NumberType<>(this, this.lowest, this.highest, false);
        }

        public N lowest() {
            return this.lowest;
        }

        public N highest() {
            return this.highest;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
