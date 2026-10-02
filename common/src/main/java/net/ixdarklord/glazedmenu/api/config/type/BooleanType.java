package net.ixdarklord.glazedmenu.api.config.type;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * {@code true} or {@code false}; typed as true/false, yes/no, on/off or 1/0.
 */
public final class BooleanType implements ConfigType<Boolean> {
    static final BooleanType INSTANCE = new BooleanType();

    private BooleanType() {}

    @Override
    public Codec<Boolean> codec() {
        return Codec.BOOL;
    }

    @Override
    public ValidationResult<Boolean> validate(Boolean value) {
        return ValidationResult.ok(value);
    }

    @Override
    public ValidationResult<Boolean> parse(String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> ValidationResult.ok(true);
            case "false", "no", "off", "0" -> ValidationResult.ok(false);
            default -> ValidationResult.error(Component.translatableWithFallback("glazedmenu.error.boolean", "Must be true or false"));
        };
    }

    @Override
    public String format(Boolean value) {
        return value.toString();
    }

    @Override
    public List<String> suggestions() {
        return List.of("true", "false");
    }
}
