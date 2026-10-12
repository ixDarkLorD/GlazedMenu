package net.ixdarklord.glazedmenu.internal.compat;

import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Minecraft 26.1's key bind categories have an id (whose namespace is the mod's) and a name; 1.21.1's are only the
// translation key of their name ("key.categories.movement"), so whose a key bind is has to be found another way.
public final class KeyCategories {
    private static final String GAME = "minecraft";
    // The game's own categories: a mod's key bind may sit in one, so they say nothing of whose it is.
    private static final Set<String> GAME_CATEGORIES = Set.of("key.categories.movement", "key.categories.misc", "key.categories.multiplayer",
            "key.categories.gameplay", "key.categories.inventory", "key.categories.ui", "key.categories.creative");
    // The words translation keys are made of that aren't a mod's id.
    private static final Set<String> WORDS = Set.of("key", "keys", "keybind", "keybinds", "keybinding", "keybindings", "category", "categories", "controls", GAME);
    private static final Map<String, String> BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, String> BY_CATEGORY = new ConcurrentHashMap<>();

    private KeyCategories() {}

    public static Component label(String category) {
        return Component.translatable(category);
    }

    /**
     * The mod a key bind belongs to, by the first of these that names one: a loaded mod's id in the key bind's own
     * translation key ("key.mymod.open") or in its category's ("key.categories.mymod"); the mod its class comes from,
     * where a mod made a class of its own for it; else the game ("minecraft"). The game's own key binds are the game's.
     */
    public static String modOf(KeyMapping mapping) {
        return BY_KEY.computeIfAbsent(mapping.getName(), name -> {
            // The game's own are "key.<name>" (and "key.hotbar.<number>") in the game's categories, made of the game's class.
            String[] parts = name.split("[.:]");
            boolean plain = name.startsWith("key.") && (parts.length == 2 || parts.length == 3 && parts[1].equals("hotbar"));
            if (plain && GAME_CATEGORIES.contains(mapping.getCategory()) && mapping.getClass() == KeyMapping.class) return GAME;
            String named = plain ? null : modIn(name);
            if (named == null && !GAME_CATEGORIES.contains(mapping.getCategory())) named = modIn(mapping.getCategory());
            if (named != null) return named;
            if (mapping.getClass() != KeyMapping.class) {
                try {
                    String owner = GlazedPlatform.get().modOf(mapping.getClass());
                    if (owner != null && !owner.isEmpty()) return owner;
                } catch (RuntimeException | LinkageError e) {
                    return GAME;
                }
            }
            return GAME;
        });
    }

    /** The mod a category belongs to: the one its key says, else the one all of its key binds are of, else the game. */
    public static String modOf(String category) {
        return BY_CATEGORY.computeIfAbsent(category, key -> {
            if (GAME_CATEGORIES.contains(key)) return GAME;
            String named = modIn(key);
            if (named != null) return named;
            String shared = null;
            for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
                if (!mapping.getCategory().equals(key)) continue;
                String mod = modOf(mapping);
                if (shared != null && !shared.equals(mod)) return GAME;
                shared = mod;
            }
            return shared != null ? shared : GAME;
        });
    }

    // A loaded mod's id among the parts of a translation key; null where none of them is one.
    private static @Nullable String modIn(String key) {
        for (String part : key.split("[.:]")) {
            if (part.isEmpty() || WORDS.contains(part)) continue;
            try {
                if (GlazedPlatform.get().isModLoaded(part)) return part;
                // An id written with a dash where the key has an underscore, or the reverse.
                String other = part.indexOf('_') >= 0 ? part.replace('_', '-') : part.replace('-', '_');
                if (!other.equals(part) && GlazedPlatform.get().isModLoaded(other)) return other;
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
