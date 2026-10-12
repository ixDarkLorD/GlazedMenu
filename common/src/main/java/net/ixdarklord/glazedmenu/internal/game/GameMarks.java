package net.ixdarklord.glazedmenu.internal.game;

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
 * What the player marked in the lists of worlds and servers, in {@code config/glazedmenu-game.json}: the favorite
 * worlds (by their folder) and the favorite servers (by their address), listed first in each.
 */
public final class GameMarks {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static @Nullable Values values;

    private GameMarks() {}

    // The file's contents; Gson fills them.
    private static final class Values {
        Set<String> favoriteWorlds = new LinkedHashSet<>();
        Set<String> favoriteServers = new LinkedHashSet<>();
        // How long each world and server has been played (PlayTimes), by "world:<folder>" or "server:<address>".
        Map<String, Played> played = new LinkedHashMap<>();
    }

    // A world's or server's play time, in milliseconds: in all, and of its last session.
    private static final class Played {
        long total;
        long last;
    }

    private static Path file() {
        return GlazedPlatform.get().configDir().resolve("glazedmenu-game.json");
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
            if (values.favoriteWorlds == null) values.favoriteWorlds = new LinkedHashSet<>();
            if (values.favoriteServers == null) values.favoriteServers = new LinkedHashSet<>();
            if (values.played == null) values.played = new LinkedHashMap<>();
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

    public static synchronized boolean isFavoriteWorld(String levelId) {
        return values().favoriteWorlds.contains(levelId);
    }

    public static synchronized void toggleFavoriteWorld(String levelId) {
        if (!values().favoriteWorlds.remove(levelId)) values().favoriteWorlds.add(levelId);
        save();
    }

    static synchronized long playedTotal(String key) {
        Played played = values().played.get(key);
        return played == null ? 0 : played.total;
    }

    static synchronized long playedLast(String key) {
        Played played = values().played.get(key);
        return played == null ? 0 : played.last;
    }

    // More was played: added to the total, with how long the session under way has been so far.
    static synchronized void addPlayed(String key, long more, long session) {
        Played played = values().played.computeIfAbsent(key, each -> new Played());
        played.total += more;
        played.last = session;
        save();
    }

    static synchronized void setPlayedTotal(String key, long total) {
        values().played.computeIfAbsent(key, each -> new Played()).total = total;
        save();
    }

    public static synchronized boolean isFavoriteServer(String address) {
        return values().favoriteServers.contains(address);
    }

    public static synchronized void toggleFavoriteServer(String address) {
        if (!values().favoriteServers.remove(address)) values().favoriteServers.add(address);
        save();
    }
}
