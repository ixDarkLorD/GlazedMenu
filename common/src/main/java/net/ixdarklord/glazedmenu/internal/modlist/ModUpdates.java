package net.ixdarklord.glazedmenu.internal.modlist;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which mods have a newer version, from the update files mods name themselves (the update JSON format NeoForge uses):
 * read by the loader where it checks them itself (NeoForge), or else once by Glazed Menu, in the background, after the game
 * loads (Fabric, from {@code "custom": {"glazedmenu": {"update_json": ...}}}). The mods that name none are then looked
 * up on Modrinth by the hashes of their files, in one request. The player can turn either off (GlazedSettings).
 */
public final class ModUpdates {
    private static final Gson GSON = new Gson();
    private static final String MODRINTH = "https://api.modrinth.com/v2/version_files/update";
    private static final String USER_AGENT = "ixDarkLorD/GlazedMenu (mod update check)";
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
        CompletableFuture.runAsync(() -> {
            checkUpdateFiles();
            if (GlazedSettings.checkModrinth()) checkModrinth();
        }).exceptionally(error -> {
            GlazedMenu.LOGGER.debug("Couldn't check for mod updates", error);
            return null;
        });
    }

    /** The mod's newer version, if one was found and the player didn't choose to ignore it. */
    public static Optional<Update> of(String modId) {
        if (!GlazedSettings.checkUpdates()) return Optional.empty();
        Update found = FOUND.get(modId);
        return (found != null ? Optional.of(found) : GlazedPlatform.get().loaderUpdate(modId))
                .filter(update -> !ModMarks.isIgnored(modId, update.version()));
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
                            .header("User-Agent", USER_AGENT).GET().build();
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

    // The mods left (no update file, and not inside another mod): Modrinth is asked, by the hashes of their files, for
    // the newest version of each for this game version and loader. A mod whose newest file isn't the one installed has
    // an update, unless its version reads as older than the installed one (a file made for another game version).
    private static void checkModrinth() {
        GlazedPlatform platform = GlazedPlatform.get();
        ModEntry game = ModCatalog.get("minecraft");
        if (game == null || platform.loaderName().isEmpty()) return;
        Map<String, ModEntry> byHash = new LinkedHashMap<>();
        for (ModEntry mod : ModCatalog.all()) {
            if (ModCatalog.isChild(mod) || FOUND.containsKey(mod.id()) || platform.loaderUpdate(mod.id()).isPresent()
                    || platform.updateJson(mod.id()).isPresent()) continue;
            Path file = platform.modFile(mod.id()).orElse(null);
            if (file == null) continue;
            try {
                byHash.putIfAbsent(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(file))), mod);
            } catch (Exception e) {
                GlazedMenu.LOGGER.debug("Couldn't hash the file of {}", mod.id(), e);
            }
        }
        if (byHash.isEmpty()) return;
        JsonObject body = new JsonObject();
        JsonArray hashes = new JsonArray();
        byHash.keySet().forEach(hashes::add);
        body.add("hashes", hashes);
        body.addProperty("algorithm", "sha512");
        JsonArray loaders = new JsonArray();
        loaders.add(platform.loaderName());
        body.add("loaders", loaders);
        JsonArray gameVersions = new JsonArray();
        gameVersions.add(game.version());
        body.add("game_versions", gameVersions);
        int found = 0;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(MODRINTH)).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", USER_AGENT).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                GlazedMenu.LOGGER.debug("Modrinth answered the update check with {}", response.statusCode());
                return;
            }
            JsonObject newest = GSON.fromJson(response.body(), JsonObject.class);
            for (Map.Entry<String, ModEntry> entry : byHash.entrySet()) {
                Update update = newest.has(entry.getKey()) ? modrinthUpdate(newest.getAsJsonObject(entry.getKey()), entry.getKey(), entry.getValue()) : null;
                if (update == null) continue;
                FOUND.put(entry.getValue().id(), update);
                found++;
            }
        } catch (Exception e) {
            GlazedMenu.LOGGER.debug("Couldn't ask Modrinth for mod updates", e);
        }
        if (found > 0) {
            generation++;
            GlazedMenu.LOGGER.info("Found {} updates on Modrinth", found);
        }
    }

    // The update a Modrinth version is for a mod, or null when it's the file installed or reads as older.
    private static Update modrinthUpdate(JsonObject version, String installedHash, ModEntry mod) {
        for (JsonElement file : version.getAsJsonArray("files")) {
            JsonObject hashes = file.getAsJsonObject().getAsJsonObject("hashes");
            if (hashes != null && hashes.has("sha512") && installedHash.equalsIgnoreCase(hashes.get("sha512").getAsString())) return null;
        }
        String number = version.get("version_number").getAsString();
        if (number.equals(mod.version()) || isNewer(mod.version(), number)) return null;
        return new Update(number, "https://modrinth.com/project/" + version.get("project_id").getAsString() + "/version/" + version.get("id").getAsString());
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
