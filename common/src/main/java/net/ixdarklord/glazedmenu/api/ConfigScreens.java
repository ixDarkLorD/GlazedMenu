package net.ixdarklord.glazedmenu.api;

import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.internal.gui.CategoryPopup;
import net.ixdarklord.glazedmenu.internal.gui.ColorPickerScreen;
import net.ixdarklord.glazedmenu.internal.gui.ConfigScreen;
import net.ixdarklord.glazedmenu.internal.gui.ConfigSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

/**
 * Glazed Menu's config screens, for CoolCatLib: Core's configs and every other config system Glazed Menu reads. Each mod with
 * one gets a config button in NeoForge's mod list and Mod Menu; open them from a button or key with these, or with
 * {@code /glazedmenu [mod] [category]}.
 */
public final class ConfigScreens {
    private ConfigScreens() {}

    /**
     * The main screen for a mod's configs: a card for each of them, even when there's only one.
     *
     * @return null when the mod registered no config
     */
    public static @Nullable Screen create(@Nullable Screen parent, String modId) {
        if (ConfigSources.forMod(modId).isEmpty()) return null;
        return new ConfigSelectScreen(parent, modId);
    }

    /** The screen editing one config, by its id ({@code mymod:client}), from any config system Glazed Menu reads. */
    public static @Nullable Screen create(@Nullable Screen parent, Identifier configId) {
        return ConfigSources.all().stream().filter(config -> config.id().equals(configId)).findFirst()
                .map(config -> create(parent, config)).orElse(null);
    }

    /** The screen editing one config. */
    public static Screen create(@Nullable Screen parent, Config config) {
        return ConfigScreen.create(parent, config);
    }

    /**
     * A small window with one category of a config: a group's settings, like the full screen's, with Save and Cancel.
     * It floats over {@code parent} (or over the game when null). The path is the group's keys joined by {@code /} or
     * {@code .}, e.g. {@code "rendering/particles"}; a single setting's path shows just that setting, and an empty path
     * the whole config.
     *
     * @throws IllegalArgumentException when the config has nothing at the path
     */
    public static Screen categoryPopup(@Nullable Screen parent, Config config, String path) {
        return CategoryPopup.create(parent, config, path);
    }

    /**
     * {@link #categoryPopup(Screen, Config, String)} drawn with its own theme instead of the config's, e.g. to match the
     * screen it's opened from (with {@link ConfigTheme.Builder#popupSprite} for the panel).
     */
    public static Screen categoryPopup(@Nullable Screen parent, Config config, String path, ConfigTheme theme) {
        return CategoryPopup.create(parent, config, path, theme);
    }

    /** {@link #categoryPopup(Screen, Config, String)} for a config by its id, from any config system Glazed Menu reads. */
    public static @Nullable Screen categoryPopup(@Nullable Screen parent, Identifier configId, String path) {
        return ConfigSources.all().stream().filter(config -> config.id().equals(configId)).findFirst()
                .map(config -> categoryPopup(parent, config, path)).orElse(null);
    }

    /**
     * {@link #categoryPopup(Screen, Config, String)} for a mod's config named by the path's first part, e.g.
     * {@code "client/example_category"} for the {@code example_category} group of the mod's {@code client} config.
     *
     * @return null when the mod has no such config or category
     */
    public static @Nullable Screen categoryPopup(@Nullable Screen parent, String modId, String path) {
        return CategoryPopup.create(parent, modId, path);
    }

    /** Opens {@link #categoryPopup(Screen, String, String)} over the current screen, or over the game. */
    public static void openCategory(String modId, String path) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = categoryPopup(minecraft.screen, modId, path);
        if (screen != null) minecraft.setScreen(screen);
    }

    /** A screen listing every mod's configs. */
    public static Screen createModList(@Nullable Screen parent) {
        return new ConfigSelectScreen(parent, null);
    }

    /** Opens a mod's config screen on top of the current screen. */
    public static void open(String modId) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = create(minecraft.screen, modId);
        if (screen != null) minecraft.setScreen(screen);
    }

    /**
     * The color picker config screens use for colors, for any color a mod lets players choose. "Done" passes the
     * picked color (ARGB; opaque unless {@code alpha}) to {@code onDone}, then returns to {@code parent}.
     */
    public static Screen colorPicker(@Nullable Screen parent, Component title, int color, boolean alpha, IntConsumer onDone) {
        return new ColorPickerScreen(parent, title, color, alpha, onDone);
    }

    public static boolean hasConfigs(String modId) {
        return !ConfigSources.forMod(modId).isEmpty();
    }
}
