package net.ixdarklord.glazedmenu.api.config.type;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * A packed ARGB color, written as {@code "#RRGGBB"} (or {@code "#AARRGGBB"} with alpha). Without alpha the stored
 * value is always opaque. Files may also hold the plain integer.
 */
public final class ColorType implements ConfigType<Integer> {
    static final ColorType RGB = new ColorType(false);
    static final ColorType ARGB = new ColorType(true);

    private final boolean alpha;
    private final Codec<Integer> codec;

    private ColorType(boolean alpha) {
        this.alpha = alpha;
        Codec<Integer> hex = Codec.STRING.comapFlatMap(text -> {
            ValidationResult<Integer> result = this.parse(text);
            return result.hasValue() ? DataResult.success(result.value()) : DataResult.error(result::messageString);
        }, this::format);
        // A hex string, or a plain number as older files may hold.
        this.codec = Codec.either(hex, Codec.INT).xmap(either -> either.map(value -> value, value -> value), com.mojang.datafixers.util.Either::left);
    }

    public boolean hasAlpha() {
        return this.alpha;
    }

    @Override
    public Codec<Integer> codec() {
        return this.codec;
    }

    @Override
    public ValidationResult<Integer> validate(Integer value) {
        return ValidationResult.ok(this.alpha ? value : value | 0xFF000000);
    }

    @Override
    public ValidationResult<Integer> parse(String text) {
        String hex = text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        else if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
        if (hex.length() == 6 || (this.alpha && hex.length() == 8)) {
            try {
                int value = (int) Long.parseLong(hex, 16);
                return this.validate(hex.length() == 6 ? value | 0xFF000000 : value);
            } catch (NumberFormatException ignored) {
            }
        }
        return ValidationResult.error(this.alpha
                ? Component.translatableWithFallback("glazedmenu.error.color_alpha", "Must be a color like #FF8800 or #80FF8800")
                : Component.translatableWithFallback("glazedmenu.error.color", "Must be a color like #FF8800"));
    }

    @Override
    public String format(Integer value) {
        return this.alpha && (value >>> 24) != 0xFF
                ? String.format(Locale.ROOT, "#%08X", value)
                : String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF);
    }

    @Override
    public List<Component> describe() {
        return List.of(Component.translatableWithFallback("glazedmenu.info.color", "Format: %s", this.alpha ? "#RRGGBB / #AARRGGBB" : "#RRGGBB"));
    }
}
