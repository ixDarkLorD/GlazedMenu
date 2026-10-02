package net.ixdarklord.glazedmenu.internal.modlist;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which mods have a newer version, from the update files mods name themselves (the update JSON format NeoForge uses):
 * read by the loader where it checks them itself (NeoForge), or else once by Glazed Menu, in the background, after the game
 * loads (Fabric, from {@code "custom": {"glazedmenu": {"update_json": ...}}}). The player can turn it off (GlazedSettings).
 */
public final class ModUpdates {
    private static final Gson GSON = new Gson();
    private static final Map<String, Update> FOUND = new ConcurrentHashMap<>();
    private static volatile boolean started;
    // Changes whenever results arrive, for screens to refresh what they show.
    private static volatile int generation;

    /** A newer version of a mod: its version, and the page to get it from. */
    public record Update(String version, String url) {}

    private ModUpdates() {}

    /** Starts the check, once, when the player allows it. */
    public static void start() {
        if (started || !GlazedSettings.checkUpdates()) return;
        started = true;
        CompletableFuture.runAsync(ModUpdates::checkUpdateFiles).exceptionally(error -> {
            GlazedMenu.LOGGER.debug("Couldn't check for mod updates", error);
            return null;
        });
    }

    /** The mod's newer version, if one was found. */
    public static Optional<Update> of(String modId) {
        if (!GlazedSettings.checkUpdates()) return Optional.empty();
        Update found = FOUND.get(modId);
        return found != null ? Optional.of(found) : GlazedPlatform.get().loaderUpdate(modId);
    }

    /** How many mods (not counting bundled ones) have a newer version. */
    public static long count() {
        return ModCatalog.all().stream().filter(mod -> !ModCatalog.isChild(mod) && of(mod.id()).isPresent()).count();
    }

    /** Changes whenever results arrive. */
    public static int generation() {
        return generation;
    }

    // Mods that name an update file: its newest version for this game version, recommended or else latest, when newer
    // than the one installed.
    private static void checkUpdateFiles() {
        GlazedPlatform platform = GlazedPlatform.get();
        ModEntry game = ModCatalog.get("minecraft");
        if (game == null) return;
        int found = 0;
        {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
            for (ModEntry mod : ModCatalog.all()) {
                if (FOUND.containsKey(mod.id())) continue;
                String url = platform.updateJson(mod.id()).orElse(null);
                if (url == null) continue;
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                            .header("User-Agent", "ixDarkLorD/GlazedMenu (mod update check)").GET().build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) continue;
                    JsonObject file = GSON.fromJson(response.body(), JsonObject.class);
                    JsonObject promos = file.has("promos") ? file.getAsJsonObject("promos") : null;
                    if (promos == null) continue;
                    String gameVersion = game.version();
                    JsonElement newest = promos.has(gameVersion + "-recommended") ? promos.get(gameVersion + "-recommended") : promos.get(gameVersion + "-latest");
                    if (newest == null || !isNewer(newest.getAsString(), mod.version())) continue;
                    String page = file.has("homepage") ? file.get("homepage").getAsString() : mod.homepage() != null ? mod.homepage() : "";
                    FOUND.put(mod.id(), new Update(newest.getAsString(), page));
                    found++;
                } catch (Exception e) {
                    GlazedMenu.LOGGER.debug("Couldn't read the update file of {}", mod.id(), e);
                }
            }
        }
        if (found > 0) {
            generation++;
            GlazedMenu.LOGGER.info("Found {} updates in mods' update files", found);
        }
    }

    /** Whether one version is newer than another: their numbers compared in turn ("26.1.2-10" after "26.1.2-9"). */
    static boolean isNewer(String candidate, String installed) {
        // Versions like "26.1.2+1.0" compare by the mod's own part, after the "+", when the other has none.
        if (installed.contains("+") && !candidate.contains("+")) installed = installed.substring(installed.indexOf('+') + 1);
        if (candidate.contains("+") && !installed.contains("+")) candidate = candidate.substring(candidate.indexOf('+') + 1);
        String[] a = candidate.split("[.\\-+_ ]");
        String[] b = installed.split("[.\\-+_ ]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String x = i < a.length ? a[i] : "0";
            String y = i < b.length ? b[i] : "0";
            int order;
            if (x.chars().allMatch(Character::isDigit) && y.chars().allMatch(Character::isDigit) && !x.isEmpty() && !y.isEmpty()) {
                order = new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y));
            } else {
                order = x.compareToIgnoreCase(y);
            }
            if (order != 0) return order > 0;
        }
        return false;
    }
}
