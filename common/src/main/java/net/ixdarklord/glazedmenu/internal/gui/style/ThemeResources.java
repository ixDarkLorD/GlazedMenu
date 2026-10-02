package net.ixdarklord.glazedmenu.internal.gui.style;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.ixdarklord.glazedmenu.api.config.ConfigColorScheme;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// A resource pack's assets/<modid>/glazedmenu/config_theme.json (or coolcatcore/, as CoolCatLib read it), laid over a mod's
// theme; see ConfigTheme's docs.
// Read when a screen opens, so a resource reload shows on the next screen.
public final class ThemeResources {
    private ThemeResources() {}

    public static ConfigTheme resolve(String modId, ConfigTheme theme) {
        // Glazed Menu's own path, then CoolCatLib's, where packs made for its screens put theirs.
        ResourceLocation location = null;
        Optional<Resource> resource = Optional.empty();
        for (String path : new String[]{"glazedmenu/config_theme.json", "coolcatcore/config_theme.json"}) {
            location = ResourceLocation.tryBuild(modId, path);
            if (location == null) return theme;
            resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isPresent()) break;
        }
        if (resource.isEmpty()) return theme;
        try (Reader reader = resource.get().openAsReader()) {
            return apply(JsonParser.parseReader(reader).getAsJsonObject(), theme);
        } catch (IOException | JsonParseException | IllegalStateException | IllegalArgumentException e) {
            GlazedMenu.LOGGER.warn("Ignoring {}: {}", location, e.getMessage());
            return theme;
        }
    }

    private static ConfigTheme apply(JsonObject json, ConfigTheme theme) {
        ConfigTheme.Builder builder = theme.toBuilder();
        JsonObject colors = json.has("colors") ? json.getAsJsonObject("colors") : new JsonObject();
        ConfigColorScheme scheme = theme.colors();
        if (json.has("base")) {
            int accent = colors.has("accent") ? color(colors.get("accent")) : scheme.accent();
            scheme = scheme(json.get("base").getAsString(), accent, false);
        }
        ConfigColorScheme.Builder colorBuilder = applyColors(colors, scheme.toBuilder());
        builder.colors(colorBuilder.build());
        if (json.has("light_base") || json.has("light_colors")) {
            JsonObject lightColors = json.has("light_colors") ? json.getAsJsonObject("light_colors") : new JsonObject();
            ConfigColorScheme light = theme.lightColors();
            if (json.has("light_base")) {
                int accent = lightColors.has("accent") ? color(lightColors.get("accent")) : colorBuilder.build().accent();
                light = scheme(json.get("light_base").getAsString(), accent, true);
            }
            builder.lightColors(applyColors(lightColors, light.toBuilder()).build());
        }
        if (json.has("icon")) builder.icon(identifier(json.get("icon")));
        if (json.has("background")) builder.background(identifier(json.get("background")));
        if (json.has("background_mode")) builder.mode(ConfigTheme.BackgroundMode.valueOf(json.get("background_mode").getAsString().toUpperCase(Locale.ROOT)));
        if (json.has("tile_size")) builder.tiled(json.get("tile_size").getAsInt());
        if (json.has("background_opacity")) builder.backgroundOpacity(json.get("background_opacity").getAsFloat());
        if (json.has("texture_opacity")) builder.textureOpacity(json.get("texture_opacity").getAsFloat());
        if (json.has("background_in_world")) builder.backgroundInWorld(json.get("background_in_world").getAsBoolean());
        if (json.has("popup_sprite")) builder.popupSprite(identifier(json.get("popup_sprite")));
        if (json.has("effects")) {
            JsonElement effects = json.get("effects");
            List<ResourceLocation> ids = new ArrayList<>();
            if (effects.isJsonArray()) effects.getAsJsonArray().forEach(element -> ids.add(identifier(element)));
            else ids.add(identifier(effects));
            builder.effects(ids);
        }
        return builder.build();
    }

    // "dark", "light", or "tinted": the dark (or, for light mode, the light) scheme in the accent.
    private static ConfigColorScheme scheme(String name, int accent, boolean light) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "dark" -> ConfigColorScheme.DARK.toBuilder().accent(accent).build();
            case "light" -> ConfigColorScheme.LIGHT.toBuilder().accent(accent).build();
            case "tinted" -> light ? ConfigColorScheme.tintedLight(accent) : ConfigColorScheme.tinted(accent);
            default -> throw new IllegalArgumentException("Unknown base scheme " + name + "; use dark, light or tinted");
        };
    }

    private static ConfigColorScheme.Builder applyColors(JsonObject colors, ConfigColorScheme.Builder colorBuilder) {
        for (Map.Entry<String, JsonElement> entry : colors.entrySet()) {
            int color = color(entry.getValue());
            switch (entry.getKey()) {
                case "accent" -> colorBuilder.accent(color);
                case "backdrop" -> colorBuilder.backdrop(color);
                case "panel" -> colorBuilder.panel(color);
                case "panel_border", "panelBorder" -> colorBuilder.panelBorder(color);
                case "bar" -> colorBuilder.bar(color);
                case "popup" -> colorBuilder.popup(color);
                case "row_hover", "rowHover" -> colorBuilder.rowHover(color);
                case "field" -> colorBuilder.field(color);
                case "field_border", "fieldBorder" -> colorBuilder.fieldBorder(color);
                case "button" -> colorBuilder.button(color);
                case "button_hover", "buttonHover" -> colorBuilder.buttonHover(color);
                case "button_disabled", "buttonDisabled" -> colorBuilder.buttonDisabled(color);
                case "toggle_off", "toggleOff" -> colorBuilder.toggleOff(color);
                case "knob" -> colorBuilder.knob(color);
                case "text" -> colorBuilder.text(color);
                case "text_dim", "textDim" -> colorBuilder.textDim(color);
                case "text_muted", "textMuted" -> colorBuilder.textMuted(color);
                case "modified" -> colorBuilder.modified(color);
                case "error" -> colorBuilder.error(color);
                case "success" -> colorBuilder.success(color);
                case "warning" -> colorBuilder.warning(color);
                default -> throw new IllegalArgumentException("Unknown color " + entry.getKey());
            }
        }
        return colorBuilder;
    }

    // "#RRGGBB", "#AARRGGBB", or a number.
    private static int color(JsonElement json) {
        if (json.getAsJsonPrimitive().isNumber()) return json.getAsInt();
        ValidationResult<Integer> result = ConfigTypes.COLOR_ALPHA.parse(json.getAsString());
        if (!result.isOk()) throw new IllegalArgumentException("Invalid color " + json.getAsString());
        return result.value();
    }

    private static ResourceLocation identifier(JsonElement json) {
        ResourceLocation id = ResourceLocation.tryParse(json.getAsString());
        if (id == null) throw new IllegalArgumentException("Invalid texture path " + json.getAsString());
        return id;
    }
}
