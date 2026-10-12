package net.ixdarklord.glazedmenu.internal.modlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the player marked in the mod list, in {@code config/glazedmenu-mods.json}: favorite mods (listed first, and under
 * Favorites), hidden mods (listed only under Hidden), and the newer versions they chose not to be told about.
 */
public final class ModMarks {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static @Nullable Values values;

    private ModMarks() {}

    // The file's contents; Gson fills them.
    private static final class Values {
        Set<String> favorites = new LinkedHashSet<>();
        Set<String> hidden = new LinkedHashSet<>();
        // Mod id -> the newer version to say nothing of; a version after it is told of again.
        Map<String, String> ignoredUpdates = new LinkedHashMap<>();
    }

    private static Path file() {
        return GlazedPlatform.get().configDir().resolve("glazedmenu-mods.json");
    }

    private static synchronized Values values() {
        if (values == null) {
            Values read = null;
            Path file = file();
            if (Files.exists(file)) {
                try {
                    read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Values.class);
                } catch (IOException | JsonParseException e) {
                    GlazedMenu.LOGGER.warn("Couldn't read {}; starting with nothing marked", file, e);
                }
            }
            values = read != null ? read : new Values();
            if (values.favorites == null) values.favorites = new LinkedHashSet<>();
            if (values.hidden == null) values.hidden = new LinkedHashSet<>();
            if (values.ignoredUpdates == null) values.ignoredUpdates = new LinkedHashMap<>();
        }
        return values;
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(values()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GlazedMenu.LOGGER.error("Couldn't save {}", file(), e);
        }
    }

    public static synchronized boolean isFavorite(String modId) {
        return values().favorites.contains(modId);
    }

    public static synchronized void toggleFavorite(String modId) {
        if (!values().favorites.remove(modId)) values().favorites.add(modId);
        save();
    }

    public static synchronized boolean isHidden(String modId) {
        return values().hidden.contains(modId);
    }

    public static synchronized void toggleHidden(String modId) {
        if (!values().hidden.remove(modId)) values().hidden.add(modId);
        save();
    }

    /** Whether the player chose not to be told about this newer version of the mod. */
    public static synchronized boolean isIgnored(String modId, String version) {
        return version.equals(values().ignoredUpdates.get(modId));
    }

    public static synchronized void ignoreUpdate(String modId, String version) {
        values().ignoredUpdates.put(modId, version);
        save();
    }
}
