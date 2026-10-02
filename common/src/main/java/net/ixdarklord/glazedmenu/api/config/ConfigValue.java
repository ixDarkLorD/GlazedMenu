package net.ixdarklord.glazedmenu.api.config;

import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One typed setting. Reading is cheap and thread-safe, so keep the handle and call {@link #get()} where the value is
 * used instead of caching it: the value can change through the config screen, a command, the file being edited, or
 * the server syncing its own.
 *
 * @param <T> the value's type; values are never null
 */
@ApiStatus.NonExtendable
public interface ConfigValue<T> extends ConfigNode, Supplier<T> {
    /** The current value. */
    @Override
    T get();

    /**
     * Changes the value in memory and notifies listeners; {@link Config#save()} writes it. A value the type corrects
     * (a number out of range, say) is stored corrected. A {@link ConfigScope#STARTUP} value only changes its
     * {@linkplain #getStored() stored} value, for the next start.
     *
     * @throws IllegalArgumentException when the value isn't valid and can't be corrected
     */
    void set(T value);

    /**
     * The value in the file. It equals {@link #get()} except for a {@link ConfigScope#STARTUP} value changed since the
     * game started: {@code get()} keeps the value the game started with, and this one applies after a restart.
     */
    T getStored();

    /** Whether a changed {@link ConfigScope#STARTUP} value is waiting for a restart. */
    default boolean isRestartPending() {
        return !this.type().equals(this.get(), this.getStored());
    }

    /** Checks a value against the type and the entry's own validators, without setting it. */
    ValidationResult<T> validate(T value);

    T getDefault();

    /** Sets the default value back. */
    default void reset() {
        this.set(this.getDefault());
    }

    default boolean isDefault() {
        return this.type().equals(this.get(), this.getDefault());
    }

    ConfigType<T> type();

    RestartRequirement restartRequirement();

    /**
     * Whether a synced config sends this value to clients. Off for server secrets (a webhook URL, a password), which
     * stay on the server and can't be edited from a client.
     */
    boolean isSynced();

    /** How a synced {@link ConfigScope#STARTUP} value is reconciled with the server's; ignored by other scopes. */
    StartupSync startupSync();

    /** The old keys (or dotted paths from the root) this value is still read from, for renamed or moved settings. */
    List<String> aliases();

    /** The value this one depends on, if it was built with {@code enabledWhen}. */
    Optional<ConfigDependency<?>> dependency();

    /** Whether the dependency, if any, is currently met. The config screen greys the value out when it isn't. */
    default boolean isActive() {
        return this.dependency().map(ConfigDependency::isMet).orElse(true);
    }

    /** Called on the thread that changed the value, after it changed. */
    void addListener(ChangeListener<T> listener);

    boolean removeListener(ChangeListener<T> listener);

    @FunctionalInterface
    interface ChangeListener<T> {
        void onChanged(T oldValue, T newValue);
    }
}
