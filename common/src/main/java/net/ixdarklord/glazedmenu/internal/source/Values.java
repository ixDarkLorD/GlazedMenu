package net.ixdarklord.glazedmenu.internal.source;

import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.ixdarklord.glazedmenu.api.config.StartupSync;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// Config values handled without their type, as the screens keep pending edits of every value in one map.
@SuppressWarnings("unchecked")
public final class Values {
    private Values() {}

    public static boolean same(ConfigValue<?> value, Object first, Object second) {
        return ((ConfigValue<Object>) value).type().equals(first, second);
    }

    public static ValidationResult<?> validate(ConfigValue<?> value, Object candidate) {
        try {
            return ((ConfigValue<Object>) value).validate(candidate);
        } catch (ClassCastException e) {
            return ValidationResult.error(Component.literal("Wrong value type " + candidate.getClass().getSimpleName()));
        }
    }

    public static String format(ConfigValue<?> value, Object candidate) {
        return ((ConfigValue<Object>) value).type().format(candidate);
    }

    public static <T> void set(ConfigValue<T> value, Object newValue) {
        value.set((T) newValue);
    }

    /** The strictest restart any of these values needs. */
    public static RestartRequirement restartFor(Iterable<? extends ConfigValue<?>> values) {
        RestartRequirement restart = RestartRequirement.NONE;
        for (ConfigValue<?> value : values) restart = restart.max(value.restartRequirement());
        return restart;
    }

    /** How the value relates to the server's, when that isn't simply "synced". */
    public static @Nullable Component syncNote(ConfigValue<?> value) {
        ConfigScope scope = value.config().scope();
        if (scope == ConfigScope.STARTUP) {
            if (!value.isSynced()) return Component.translatableWithFallback("glazedmenu.info.local", "Each side keeps its own value");
            return value.startupSync() == StartupSync.USE_SERVER
                    ? Component.translatableWithFallback("glazedmenu.info.use_server", "Players use the server's value while connected")
                    : Component.translatableWithFallback("glazedmenu.info.require_match", "Players must have the same value as the server");
        }
        if (scope.isSynced() && !value.isSynced()) {
            return Component.translatableWithFallback("glazedmenu.info.server_only", "Kept on the server; not sent to players");
        }
        return null;
    }
}
