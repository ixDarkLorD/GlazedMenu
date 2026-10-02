package net.ixdarklord.glazedmenu.internal.core;

import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalSource;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalValue;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Glazed Menu's own settings, in {@code config/glazedmenu.json}: the mod list's, and the player's choices for every styled
 * screen's look. They're edited in Glazed Menu's screens like any config (Glazed Menu's card in the config list).
 */
public final class GlazedSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Values values = new Values();

    private GlazedSettings() {}

    /** How the mod list lays mods out. */
    public enum View {
        /** Tiles with large icons, a few to a row. */
        GRID,
        /** One mod per row, with its version and description. */
        LIST
    }

    // The file's contents; Gson fills them, keeping the defaults for anything missing.
    private static final class Values {
        boolean replaceModList = true;
        boolean modsButton = true;
        boolean showLibraries = false;
        boolean checkUpdates = true;
        View view = View.LIST;
        ConfigTheme.Mode themeMode = ConfigTheme.Mode.DARK;
        GlazedBrand.Scheme colorScheme = GlazedBrand.Scheme.GLAZED;
        int backgroundOpacity = 100;
        int textureOpacity = 100;
        int panelOpacity = 100;
        boolean themeEffects = true;
        boolean transitions = true;
        boolean replaceMezzConfigScreens = false;
        boolean replaceFtbLibraryScreens = false;
    }

    private static Path file() {
        return GlazedPlatform.get().configDir().resolve("glazedmenu.json");
    }

    /** Reads the file (writing it with the defaults the first time). */
    static void init() {
        Path file = file();
        if (Files.exists(file)) {
            try {
                Values read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Values.class);
                if (read != null) values = read;
                if (values.view == null) values.view = View.LIST;
                if (values.themeMode == null) values.themeMode = ConfigTheme.Mode.DARK;
                if (values.colorScheme == null) values.colorScheme = GlazedBrand.Scheme.GLAZED;
            } catch (IOException | JsonParseException e) {
                GlazedMenu.LOGGER.warn("Couldn't read {}; using the defaults", file, e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(values), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GlazedMenu.LOGGER.error("Couldn't save {}", file(), e);
        }
    }

    // --- The mod list ---

    public static boolean replaceModList() {
        return values.replaceModList;
    }

    public static boolean modsButton() {
        return values.modsButton;
    }

    public static boolean showLibraries() {
        return values.showLibraries;
    }

    /** Whether Glazed Menu looks for newer versions of the mods (in the update files they name). */
    public static boolean checkUpdates() {
        return values.checkUpdates;
    }

    public static View view() {
        return values.view;
    }

    /** Changes the view and saves it. */
    public static void setView(View view) {
        values.view = view;
        save();
    }

    // --- The screens' look ---

    public static ConfigTheme.Mode themeMode() {
        return values.themeMode;
    }

    /** The colors of Glazed Menu's look: its mod list and every config screen without a theme of its own. */
    public static GlazedBrand.Scheme colorScheme() {
        return values.colorScheme;
    }

    /** Switches between dark and light, and saves the choice. */
    public static void toggleThemeMode() {
        values.themeMode = values.themeMode == ConfigTheme.Mode.DARK ? ConfigTheme.Mode.LIGHT : ConfigTheme.Mode.DARK;
        save();
    }

    /** A theme's backdrop opacity, scaled by the player's choice. */
    public static float backgroundOpacity(float themeOpacity) {
        return Math.clamp(themeOpacity * values.backgroundOpacity / 100.0F, 0, 1);
    }

    /** A theme's texture opacity, scaled by the player's choice. */
    public static float textureOpacity(float themeOpacity) {
        return Math.clamp(themeOpacity * values.textureOpacity / 100.0F, 0, 1);
    }

    /** A panel's or bar's alpha, scaled by the player's panel opacity (100 keeps the theme's). */
    public static int panelAlpha(int themeAlpha) {
        return Math.clamp(Math.round(themeAlpha * values.panelOpacity / 100.0F), 0, 255);
    }

    /** Whether the screens draw their themes' effects: the player's choice, but never on Fast graphics. */
    public static boolean themeEffects() {
        return values.themeEffects && !fastGraphics();
    }

    /** Whether pages animate into place: the player's choice, but never on Fast graphics. */
    public static boolean transitions() {
        return values.transitions && !fastGraphics();
    }

    // --- Other config systems' screens ---

    /**
     * Whether Glazed Menu's screens replace MezzConfig GUI's for MezzConfig's configs (JEI's). Without MezzConfig GUI, Glazed Menu's
     * open either way, as nothing else can.
     */
    public static boolean replaceMezzConfigScreens() {
        return values.replaceMezzConfigScreens;
    }

    /** Whether Glazed Menu's screens replace FTB Library's config editor for FTB mods' configs. */
    public static boolean replaceFtbLibraryScreens() {
        return values.replaceFtbLibraryScreens;
    }

    // Fast graphics ask for the cheapest look, so it turns the screens' animations off.
    private static boolean fastGraphics() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.options.graphicsMode().get() == GraphicsStatus.FAST;
    }

    // --- In Glazed Menu's screens ---

    /** The settings as a config for Glazed Menu's screens. */
    public static final ExternalSource SOURCE = new ExternalSource() {
        @Override
        public String name() {
            return "Glazed Menu";
        }

        @Override
        protected List<ExternalConfig> discover() {
            String prefix = "config.glazedmenu.client.";
            ExternalConfigBuilder builder = new ExternalConfigBuilder(GlazedMenu.MOD_ID, "client", ConfigScope.CLIENT)
                    .title(Component.translatableWithFallback(prefix + "title", "Glazed Menu"))
                    .file("glazedmenu.json", file());
            builder.push("modList", prefix + "modList", null, List.of("Glazed Menu's mod list"));
            bool(builder, prefix + "modList.", "replaceModList", "Opens Glazed Menu's mod list where Mod Menu's, NeoForge's or Forge's would open.",
                    () -> values.replaceModList, value -> values.replaceModList = value);
            bool(builder, prefix + "modList.", "modsButton", "Adds a Mods button to the title and pause screens when they have none.",
                    () -> values.modsButton, value -> values.modsButton = value);
            bool(builder, prefix + "modList.", "showLibraries", false, "Lists the loader's own parts (Java, the loader, MixinExtras) among the other mods too, not only under Libraries.",
                    () -> values.showLibraries, value -> values.showLibraries = value);
            bool(builder, prefix + "modList.", "checkUpdates",
                    "Looks for newer versions of your mods in the update files they name, once the game has loaded, and marks the mods that have one.",
                    () -> values.checkUpdates, value -> values.checkUpdates = value);
            builder.value("view", ConfigTypes.enumOf(View.class), View.LIST, binding(() -> values.view, value -> values.view = value))
                    .translation(prefix + "modList.view", null).comment(List.of("Shows the mods as a grid of tiles or as a list.")).add();
            builder.pop();
            builder.push("look", prefix + "look", null, List.of("Every styled screen's look, for every mod"));
            builder.value("themeMode", ConfigTypes.enumOf(ConfigTheme.Mode.class), ConfigTheme.Mode.DARK,
                            binding(() -> values.themeMode, value -> values.themeMode = value))
                    .translation(prefix + "look.themeMode", null)
                    .comment(List.of("Whether the screens use their dark or light colors (the sun/moon button in their top bar).")).add();
            builder.value("colorScheme", ConfigTypes.enumOf(GlazedBrand.Scheme.class), GlazedBrand.Scheme.GLAZED,
                            binding(() -> values.colorScheme, value -> values.colorScheme = value))
                    .translation(prefix + "look.colorScheme", null)
                    .comment(List.of("The colors of the mod list and of every config screen without a theme of its own: Glazed Menu's own, or another scheme.")).add();
            builder.value("backgroundOpacity", ConfigTypes.intRange(0, 200).withSlider(true), 100,
                            binding(() -> values.backgroundOpacity, value -> values.backgroundOpacity = value))
                    .translation(prefix + "look.backgroundOpacity", null)
                    .comment(List.of("Scales how strongly each mod's backdrop color covers the background, in percent: 0 removes it, 200 doubles it.")).add();
            builder.value("textureOpacity", ConfigTypes.intRange(0, 100).withSlider(true), 100,
                            binding(() -> values.textureOpacity, value -> values.textureOpacity = value))
                    .translation(prefix + "look.textureOpacity", null)
                    .comment(List.of("Scales the opacity of each mod's background texture, in percent; below 100 the panorama or world shows through it.")).add();
            builder.value("panelOpacity", ConfigTypes.intRange(10, 100).withSlider(true), 100,
                            binding(() -> values.panelOpacity, value -> values.panelOpacity = value))
                    .translation(prefix + "look.panelOpacity", null)
                    .comment(List.of("How solid the screens' panels and bars are, in percent: lower lets more of the background show through them.")).add();
            bool(builder, prefix + "look.", "themeEffects", "Each mod's animated effects: by default soft glows and small falling stars behind the panels.",
                    () -> values.themeEffects, value -> values.themeEffects = value);
            bool(builder, prefix + "look.", "transitions", "Pages fading in and out as you open a screen or go back.",
                    () -> values.transitions, value -> values.transitions = value);
            builder.pop();
            builder.push("otherScreens", prefix + "otherScreens", null, List.of("Config systems with screens of their own"));
            bool(builder, prefix + "otherScreens.", "replaceMezzConfigScreens", false,
                    "Turn on to replace MezzConfig GUI's config screens (like JEI's) with Glazed Menu's. Without MezzConfig GUI, Glazed Menu's open either way.",
                    () -> values.replaceMezzConfigScreens, value -> values.replaceMezzConfigScreens = value);
            bool(builder, prefix + "otherScreens.", "replaceFtbLibraryScreens", false,
                    "Turn on to replace FTB Library's config editor (FTB mods' settings, like FTB Ultimine's) with Glazed Menu's screens.",
                    () -> values.replaceFtbLibraryScreens, value -> values.replaceFtbLibraryScreens = value);
            builder.pop();
            return List.of(builder.build(() -> Access.LOCAL, GlazedSettings::save));
        }
    };

    private static void bool(ExternalConfigBuilder builder, String prefix, String key, String comment, Supplier<Boolean> get, Consumer<Boolean> set) {
        bool(builder, prefix, key, true, comment, get, set);
    }

    private static void bool(ExternalConfigBuilder builder, String prefix, String key, boolean defaultValue, String comment,
                             Supplier<Boolean> get, Consumer<Boolean> set) {
        builder.value(key, ConfigTypes.BOOLEAN, defaultValue, binding(get, set)).translation(prefix + key, null).comment(List.of(comment)).add();
    }

    private static <T> ExternalValue.Binding<T> binding(Supplier<T> get, Consumer<T> set) {
        return new ExternalValue.Binding<>() {
            @Override
            public T get() {
                return get.get();
            }

            @Override
            public void set(T value) {
                set.accept(value);
            }
        };
    }
}
