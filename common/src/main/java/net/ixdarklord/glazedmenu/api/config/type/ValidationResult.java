package net.ixdarklord.glazedmenu.api.config.type;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

/**
 * The outcome of checking a value: fine as is, corrected into a valid value (a number clamped into its range, say),
 * or rejected.
 * <p>
 * A file holding a correctable value loads the corrected one and is rewritten; the config screen and commands only
 * accept values that are {@linkplain #isOk() fine as they are}.
 */
public final class ValidationResult<T> {
    private final Status status;
    private final @Nullable T value;
    private final @Nullable Component message;

    private ValidationResult(Status status, @Nullable T value, @Nullable Component message) {
        this.status = status;
        this.value = value;
        this.message = message;
    }

    public static <T> ValidationResult<T> ok(T value) {
        return new ValidationResult<>(Status.OK, value, null);
    }

    /** The value was invalid; {@code corrected} is the closest valid one and {@code message} says what was wrong. */
    public static <T> ValidationResult<T> corrected(T corrected, Component message) {
        return new ValidationResult<>(Status.CORRECTED, corrected, message);
    }

    public static <T> ValidationResult<T> error(Component message) {
        return new ValidationResult<>(Status.ERROR, null, message);
    }

    public boolean isOk() {
        return this.status == Status.OK;
    }

    public boolean isCorrected() {
        return this.status == Status.CORRECTED;
    }

    public boolean isError() {
        return this.status == Status.ERROR;
    }

    /** Whether there's a usable value: fine or corrected. */
    public boolean hasValue() {
        return this.status != Status.ERROR;
    }

    /**
     * The value, or the corrected value.
     *
     * @throws IllegalStateException for an error
     */
    public T value() {
        if (this.value == null) throw new IllegalStateException("No value: " + this.messageString());
        return this.value;
    }

    /** What was wrong; empty when the value is fine. */
    public Optional<Component> message() {
        return Optional.ofNullable(this.message);
    }

    public String messageString() {
        return this.message == null ? "" : this.message.getString();
    }

    /** Maps a usable value; a correction or an error carries over. */
    public <R> ValidationResult<R> map(Function<? super T, ? extends R> mapper) {
        return this.value == null ? error(this.messageOrEmpty()) : new ValidationResult<>(this.status, mapper.apply(this.value), this.message);
    }

    /**
     * Runs another check on the usable value. An earlier correction survives a later check that passes; the first
     * error wins.
     */
    public ValidationResult<T> then(Function<? super T, ValidationResult<T>> next) {
        if (this.value == null) return this;
        ValidationResult<T> result = next.apply(this.value);
        if (result.isOk() && this.isCorrected()) return corrected(result.value(), this.messageOrEmpty());
        return result;
    }

    private Component messageOrEmpty() {
        return this.message == null ? Component.empty() : this.message;
    }

    @Override
    public String toString() {
        return this.status + (this.value != null ? "[" + this.value + "]" : "") + (this.message != null ? ": " + this.message.getString() : "");
    }

    private enum Status {
        OK,
        CORRECTED,
        ERROR
    }
}
