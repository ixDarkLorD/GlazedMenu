package net.ixdarklord.glazedmenu.api.config.type;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Text, optionally limited in length, required not to be empty, or matched against a pattern.
 */
public final class StringType implements ConfigType<String> {
    static final StringType ANY = new StringType(Integer.MAX_VALUE, false, null);

    private final int maxLength;
    private final boolean notEmpty;
    private final @Nullable Pattern pattern;

    StringType(int maxLength, boolean notEmpty, @Nullable Pattern pattern) {
        if (maxLength < 1) throw new IllegalArgumentException("maxLength must be positive");
        this.maxLength = maxLength;
        this.notEmpty = notEmpty;
        this.pattern = pattern;
    }

    public int maxLength() {
        return this.maxLength;
    }

    public boolean isNotEmpty() {
        return this.notEmpty;
    }

    public @Nullable Pattern pattern() {
        return this.pattern;
    }

    public StringType withMaxLength(int maxLength) {
        return new StringType(maxLength, this.notEmpty, this.pattern);
    }

    public StringType withNotEmpty(boolean notEmpty) {
        return new StringType(this.maxLength, notEmpty, this.pattern);
    }

    /** The whole text must match the pattern. */
    public StringType withPattern(@Nullable String regex) {
        return new StringType(this.maxLength, this.notEmpty, regex == null ? null : Pattern.compile(regex));
    }

    @Override
    public Codec<String> codec() {
        return Codec.STRING;
    }

    @Override
    public ValidationResult<String> validate(String value) {
        if (this.notEmpty && value.isBlank()) {
            return ValidationResult.error(Component.translatableWithFallback("glazedmenu.error.empty", "Must not be empty"));
        }
        if (this.pattern != null && !this.pattern.matcher(value).matches()) {
            return ValidationResult.error(Component.translatableWithFallback("glazedmenu.error.pattern", "Must match %s", this.pattern.pattern()));
        }
        if (value.length() > this.maxLength) {
            return ValidationResult.corrected(value.substring(0, this.maxLength),
                    Component.translatableWithFallback("glazedmenu.error.length", "Must be at most %s characters", this.maxLength));
        }
        return ValidationResult.ok(value);
    }

    @Override
    public ValidationResult<String> parse(String text) {
        ValidationResult<String> result = this.validate(text);
        return result.isCorrected() ? ValidationResult.error(result.message().orElseThrow()) : result;
    }

    @Override
    public String format(String value) {
        return value;
    }

    @Override
    public List<Component> describe() {
        List<Component> lines = new ArrayList<>();
        if (this.maxLength != Integer.MAX_VALUE) {
            lines.add(Component.translatableWithFallback("glazedmenu.info.max_length", "Max length: %s", this.maxLength));
        }
        if (this.pattern != null) {
            lines.add(Component.translatableWithFallback("glazedmenu.info.pattern", "Pattern: %s", this.pattern.pattern()));
        }
        return lines;
    }
}
