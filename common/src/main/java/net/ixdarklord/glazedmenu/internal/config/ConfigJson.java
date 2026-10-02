package net.ixdarklord.glazedmenu.internal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.StringReader;

// JSON helpers shared by the config types, formats and network: values go through their codec with JsonOps.
public final class ConfigJson {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ConfigJson() {}

    public static <T> JsonElement encode(Codec<T> codec, T value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow(error -> new IllegalStateException("Couldn't encode " + value + ": " + error));
    }

    public static <T> ValidationResult<T> decode(Codec<T> codec, JsonElement json) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
        return result.result().map(ValidationResult::ok)
                .orElseGet(() -> ValidationResult.error(Component.literal(result.error().map(DataResult.Error::message).orElse("?"))));
    }

    /** Parses JSON leniently: comments, unquoted keys and single quotes are allowed. */
    public static JsonElement parseLenient(String text) throws JsonParseException {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.LENIENT);
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new JsonParseException(e);
        }
    }

    public static <T> ValidationResult<T> parse(Codec<T> codec, String text) {
        JsonElement json;
        try {
            json = parseLenient(text);
        } catch (JsonParseException | IllegalStateException e) {
            return ValidationResult.error(Component.translatableWithFallback("glazedmenu.error.json", "Not valid JSON"));
        }
        return decode(codec, json);
    }

    public static String compact(JsonElement json) {
        return GSON.toJson(json);
    }
}
