package net.ixdarklord.glazedmenu.api.theme;

import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Client only: the registry of {@link ConfigEffect config screen effects}, by id. Themes name the effects they use by
 * these ids; one naming an id nobody registered skips it (and logs it once).
 * <p>
 * Built in: {@link ConfigTheme#STARFALL} (soft glows and falling stars, every theme's default).
 */
public final class ConfigEffects {
    private static final Map<Identifier, ConfigEffect> EFFECTS = new ConcurrentHashMap<>();
    private static Function<Identifier, @Nullable ConfigEffect> fallback = id -> null;

    private ConfigEffects() {}

    /**
     * Registers an effect under an id, during client setup.
     *
     * @throws IllegalArgumentException when the id is taken
     */
    public static void register(Identifier id, ConfigEffect effect) {
        if (EFFECTS.putIfAbsent(id, effect) != null) throw new IllegalArgumentException("A config effect with the id " + id + " is already registered");
    }

    public static @Nullable ConfigEffect get(Identifier id) {
        ConfigEffect effect = EFFECTS.get(id);
        return effect != null ? effect : fallback.apply(id);
    }

    /** Effects registered elsewhere (with CoolCatLib: Canvas), for ids nobody registered here. */
    @ApiStatus.Internal
    public static void setFallback(Function<Identifier, @Nullable ConfigEffect> effects) {
        fallback = effects;
    }

    /** Every registered id. */
    public static Set<Identifier> ids() {
        return Set.copyOf(EFFECTS.keySet());
    }
}
