package net.ixdarklord.glazedmenu.internal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Profiles: named sets of settings across any mods' configs, in {@code config/glazedmenu-profiles.json}. A profile holds
 * the settings it was saved with, each as its config's id, its path and its value (as JSON, by the value's codec); using
 * one sets those settings and leaves every other as it is. A setting whose mod is gone, or whose value no longer fits,
 * is skipped.
 */
public final class ConfigProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    // Profile name -> config id -> value path -> value.
    private static @Nullable JsonObject profiles;

    private ConfigProfiles() {}

    private static Path file() {
        return GlazedPlatform.get().configDir().resolve("glazedmenu-profiles.json");
    }

    private static synchronized JsonObject profiles() {
        if (profiles == null) {
            profiles = new JsonObject();
            Path file = file();
            if (Files.exists(file)) {
                try {
                    JsonObject read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
                    if (read != null && read.has("profiles") && read.get("profiles").isJsonObject()) profiles = read.getAsJsonObject("profiles");
                } catch (IOException | JsonParseException e) {
                    GlazedMenu.LOGGER.warn("Couldn't read {}; starting with no profiles", file, e);
                }
            }
        }
        return profiles;
    }

    private static synchronized void write() {
        JsonObject root = new JsonObject();
        root.add("profiles", profiles());
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GlazedMenu.LOGGER.error("Couldn't save {}", file(), e);
        }
    }

    /** The profiles' names, in the order they were made. */
    public static synchronized List<String> names() {
        return List.copyOf(profiles().keySet());
    }

    /** How many settings a profile holds. */
    public static synchronized int size(String name) {
        JsonObject profile = profiles().getAsJsonObject(name);
        if (profile == null) return 0;
        int count = 0;
        for (Map.Entry<String, JsonElement> config : profile.entrySet()) {
            if (config.getValue().isJsonObject()) count += config.getValue().getAsJsonObject().size();
        }
        return count;
    }

    /** Makes (or replaces) a profile holding these settings as they're stored now. */
    public static synchronized void save(String name, Collection<ConfigValue<?>> values) {
        JsonObject profile = new JsonObject();
        for (ConfigValue<?> value : values) {
            try {
                String config = value.config().id().toString();
                if (!profile.has(config)) profile.add(config, new JsonObject());
                profile.getAsJsonObject(config).add(value.path(), encode(value));
            } catch (RuntimeException e) {
                GlazedMenu.LOGGER.warn("Couldn't put {} of {} in the profile {}", value.path(), value.config().id(), name, e);
            }
        }
        profiles().add(name, profile);
        write();
    }

    public static synchronized void delete(String name) {
        if (profiles().remove(name) != null) write();
    }

    /** A profile's settings that exist here and whose values still fit, by the config they're in. */
    public static synchronized Map<Config, Map<ConfigValue<?>, Object>> resolve(String name) {
        Map<Config, Map<ConfigValue<?>, Object>> found = new LinkedHashMap<>();
        JsonObject profile = profiles().getAsJsonObject(name);
        if (profile == null) return found;
        for (Config config : ConfigSources.all()) {
            JsonElement saved = profile.get(config.id().toString());
            if (saved == null || !saved.isJsonObject()) continue;
            Map<ConfigValue<?>, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : saved.getAsJsonObject().entrySet()) {
                ConfigValue<?> value = config.find(entry.getKey()).orElse(null);
                if (value == null) continue;
                ValidationResult<?> decoded = decode(value, entry.getValue());
                if (decoded.hasValue()) values.put(value, decoded.value());
                else GlazedMenu.LOGGER.debug("The profile {} has a value for {} of {} that no longer fits: {}", name, entry.getKey(), config.id(), decoded.messageString());
            }
            if (!values.isEmpty()) found.put(config, values);
        }
        return found;
    }

    private static <T> JsonElement encode(ConfigValue<T> value) {
        return ConfigJson.encode(value.type().codec(), value.getStored());
    }

    private static <T> ValidationResult<T> decode(ConfigValue<T> value, JsonElement json) {
        try {
            return ConfigJson.decode(value.type().codec(), json).then(value::validate);
        } catch (RuntimeException e) {
            return ValidationResult.error(net.minecraft.network.chat.Component.literal(String.valueOf(e.getMessage())));
        }
    }
}
