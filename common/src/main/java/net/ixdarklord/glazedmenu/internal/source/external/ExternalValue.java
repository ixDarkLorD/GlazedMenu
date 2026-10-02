package net.ixdarklord.glazedmenu.internal.source.external;

import net.ixdarklord.glazedmenu.api.config.ConfigDependency;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.ixdarklord.glazedmenu.api.config.StartupSync;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A value of another config system's config, read and written through it. The type is CoolCatLib's closest match
 * (with the system's range, if any), so the screens edit it like any other value.
 */
public final class ExternalValue<T> extends ExternalNode implements ConfigValue<T> {
    private final ConfigType<T> type;
    private final T defaultValue;
    private final Binding<T> binding;
    private final RestartRequirement restart;
    private final @Nullable Predicate<Object> check;

    ExternalValue(String key, ExternalGroup parent, @Nullable String translationKey, @Nullable String tooltipKey, String fallbackName,
                  List<String> comment, ConfigType<T> type, T defaultValue, Binding<T> binding, RestartRequirement restart,
                  @Nullable Predicate<Object> check) {
        super(key, parent, translationKey, tooltipKey, fallbackName, comment);
        this.type = type;
        this.defaultValue = defaultValue;
        this.binding = binding;
        this.restart = restart;
        this.check = check;
    }

    /** How the value is read from and written to its config system. */
    public interface Binding<T> {
        T get();

        void set(T value);
    }

    @Override
    public T get() {
        try {
            T value = this.binding.get();
            return value != null ? value : this.defaultValue;
        } catch (RuntimeException e) {
            GlazedMenu.LOGGER.debug("Couldn't read {} of {}", this.path(), this.config().id(), e);
            return this.defaultValue;
        }
    }

    @Override
    public void set(T value) {
        ValidationResult<T> result = this.validate(value);
        if (result.isError()) throw new IllegalArgumentException("Invalid value for " + this.path() + ": " + result.messageString());
        this.binding.set(result.value());
    }

    @Override
    public T getStored() {
        return this.get();
    }

    @Override
    public ValidationResult<T> validate(T value) {
        if (value == null) return ValidationResult.error(Component.translatableWithFallback("glazedmenu.error.null", "Must not be empty"));
        ValidationResult<T> result = this.type.validate(value);
        if (this.check != null && result.hasValue() && !this.check.test(result.value())) {
            return ValidationResult.error(Component.translatableWithFallback("glazedmenu.error.rejected", "Not allowed by the mod"));
        }
        return result;
    }

    @Override
    public T getDefault() {
        return this.defaultValue;
    }

    @Override
    public ConfigType<T> type() {
        return this.type;
    }

    @Override
    public RestartRequirement restartRequirement() {
        return this.restart;
    }

    @Override
    public boolean isSynced() {
        return true;
    }

    @Override
    public StartupSync startupSync() {
        return StartupSync.REQUIRE_MATCH;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public Optional<ConfigDependency<?>> dependency() {
        return Optional.empty();
    }

    // Other systems notify their own listeners; nothing listens here.
    @Override
    public void addListener(ChangeListener<T> listener) {}

    @Override
    public boolean removeListener(ChangeListener<T> listener) {
        return false;
    }
}
