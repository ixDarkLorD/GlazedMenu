package net.ixdarklord.glazedmenu.api.config;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * How a mod's config screens look: its {@link ConfigColorScheme color schemes} for dark and light mode, an optional
 * background texture, and the icon beside the title (the mod's own icon unless set). Players pick dark or light mode
 * for every mod with the sun/moon button in the top bar.
 * Set it for all of a mod's screens (its configs, config list and pickers) with {@link #setForMod}; mods using
 * CoolCatLib: Core's configs can set theirs there instead, and Glazed Menu uses it:
 * <pre>{@code
 * ConfigTheme.setForMod("mymod", ConfigTheme.builder()
 *         .colors(ConfigColorScheme.tinted(0xFFFF8A3D))
 *         .background(ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/config_background.png"))
 *         .mode(ConfigTheme.BackgroundMode.COVER)
 *         .textureOpacity(0.8F)       // lets the panorama or world show through the texture a little
 *         .backgroundOpacity(0.3F)    // how strongly the backdrop color covers it
 *         .effects(ResourceLocation.fromNamespaceAndPath("mymod", "snow"))   // animated effects, instead of the starfall
 *         .build());
 * }</pre>
 * The texture is a full path in a resource pack ({@code assets/mymod/textures/gui/config_background.png}). Without one,
 * the screens are see-through: the title panorama, or the world while playing, blurred behind the panels. While a
 * world is open, screens show the blurred world instead, so players still see where they are.
 * <p>
 * Resource packs can restyle a mod's screens too, with {@code assets/<modid>/glazedmenu/config_theme.json}:
 * <pre>{@code
 * {
 *   "base": "tinted",                // "dark", "light" or "tinted" (the dark scheme shaded toward the accent)
 *   "colors": { "accent": "#FF8A3D", "panel": "#C0141018" },
 *   "light_base": "light",           // the light mode's scheme: "light", "dark" or "tinted"
 *   "light_colors": { "accent": "#D9601A" },
 *   "icon": "mymod:textures/gui/config_icon.png",   // instead of the mod's own icon
 *   "background": "mymod:textures/gui/config_background.png",
 *   "background_mode": "cover",      // "cover", "stretch" or "tile"
 *   "tile_size": 32,
 *   "texture_opacity": 0.8,          // the texture's own opacity, from 0 to 1
 *   "background_opacity": 0.3,       // the backdrop color over the background, from 0 to 1
 *   "background_in_world": false,
 *   "popup_sprite": "mymod:config/popup",  // a nine-slice GUI sprite for popup panels
 *   "effects": ["mymod:snow"]        // animated effects by id; [] for none
 * }
 * }</pre>
 * Every field is optional and overrides the mod's own theme; color names match {@link ConfigColorScheme.Builder}.
 * Players can scale both opacities for every mod in Glazed Menu's settings.
 * <p>
 * Effects are animations drawn behind the panels, over the whole screen, or over each widget, registered by id on the
 * client ({@link net.ixdarklord.glazedmenu.api.theme.ConfigEffects#register}); every theme uses {@link #STARFALL} unless it
 * names its own.
 */
public final class ConfigTheme {
    // Declared before DEFAULT, which uses it.
    /** The built-in effect: soft glows in the accent and small faint stars falling, shifting with the mouse. */
    public static final ResourceLocation STARFALL = ResourceLocation.fromNamespaceAndPath("glazedmenu", "starfall");
    /**
     * Glazed Menu's own look, for every mod that sets no theme: see-through to the title panorama or the world, with the dark
     * scheme tinted in the violet of Glazed Menu's logo (as its mod list).
     */
    public static final ConfigTheme DEFAULT = builder().colors(ConfigColorScheme.tinted(0xFF8B6CF6)).build();
    // (Its colors follow the scheme the player chose in Glazed Menu's settings: see colors(Mode).)
    private static final Map<String, ConfigTheme> BY_MOD = new ConcurrentHashMap<>();
    // A mod's theme from a config system Glazed Menu reads (CoolCatLib: Core's), for mods that set none here.
    private static Function<String, @Nullable ConfigTheme> fallback = modId -> null;

    private final ConfigColorScheme colors;
    private final @Nullable ConfigColorScheme lightColors;
    private final @Nullable ResourceLocation icon;
    private final @Nullable ResourceLocation background;
    private final BackgroundMode mode;
    private final int tileSize;
    private final float backgroundOpacity;
    private final float textureOpacity;
    private final boolean backgroundInWorld;
    private final @Nullable ResourceLocation popupSprite;
    private final List<ResourceLocation> effects;
    private final @Nullable Object source;

    private ConfigTheme(Builder builder) {
        this.colors = builder.colors;
        this.lightColors = builder.lightColors;
        this.icon = builder.icon;
        this.background = builder.background;
        this.mode = builder.mode;
        this.tileSize = builder.tileSize;
        this.backgroundOpacity = builder.backgroundOpacity;
        this.textureOpacity = builder.textureOpacity;
        this.backgroundInWorld = builder.backgroundInWorld;
        this.popupSprite = builder.popupSprite;
        this.effects = List.copyOf(builder.effects);
        this.source = builder.source;
    }

    /** The theme of another config system this one was made from (CoolCatLib's), for its own effects. */
    @ApiStatus.Internal
    public @Nullable Object source() {
        return this.source;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The theme of every screen of a mod's configs that don't set their own. */
    public static void setForMod(String modId, ConfigTheme theme) {
        BY_MOD.put(modId, theme);
    }

    /** The mod's theme, or {@link #DEFAULT}; a resource pack's {@code config_theme.json} is applied on top when drawn. */
    public static ConfigTheme forMod(String modId) {
        ConfigTheme theme = BY_MOD.get(modId);
        if (theme != null) return theme;
        theme = fallback.apply(modId);
        return theme != null ? theme : DEFAULT;
    }

    /** Where mods' themes come from when they set none here. */
    @ApiStatus.Internal
    public static void setFallback(Function<String, @Nullable ConfigTheme> themes) {
        fallback = themes;
    }

    /** The colors in dark mode. */
    public ConfigColorScheme colors() {
        return this.colors;
    }

    /** The colors in light mode: the ones set, or the light scheme in the dark scheme's accent. */
    public ConfigColorScheme lightColors() {
        return this.lightColors != null ? this.lightColors : ConfigColorScheme.tintedLight(this.colors.accent());
    }

    /** The colors in a mode; players switch modes with the button in the config screens' top bar. */
    public ConfigColorScheme colors(Mode mode) {
        if (this == DEFAULT && defaultColors != null) return defaultColors.apply(mode);
        return mode == Mode.LIGHT ? this.lightColors() : this.colors;
    }

    // The default theme's colors, as the player chose them in Glazed Menu's settings.
    private static @Nullable Function<Mode, ConfigColorScheme> defaultColors;

    /** Where the default theme's colors come from (Glazed Menu's color scheme setting). */
    @ApiStatus.Internal
    public static void setDefaultColors(Function<Mode, ConfigColorScheme> colors) {
        defaultColors = colors;
    }

    /** The dark mode's accent color, ARGB. */
    public int accent() {
        return this.colors.accent();
    }

    /** The icon beside the title in the top bar, or null for the mod's own icon (its logo on NeoForge). */
    public @Nullable ResourceLocation icon() {
        return this.icon;
    }

    /** The background texture's full path, or null for the see-through background. */
    public @Nullable ResourceLocation background() {
        return this.background;
    }

    public BackgroundMode mode() {
        return this.mode;
    }

    /** The size one tile is drawn at, for {@link BackgroundMode#TILE}; 0 for the texture's own size. */
    public int tileSize() {
        return this.tileSize;
    }

    /**
     * The opacity of the scheme's backdrop color drawn over the background (the texture, or the blurred panorama or
     * world), from 0 (not drawn) to 1 (hides it completely). It keeps the panels readable over busy backgrounds.
     */
    public float backgroundOpacity() {
        return this.backgroundOpacity;
    }

    /**
     * The background texture's own opacity, from 0 to 1. Below 1 the blurred panorama, or the world, shows through it.
     */
    public float textureOpacity() {
        return this.textureOpacity;
    }

    /** Whether the texture is drawn in a world too, instead of the blurred world. */
    public boolean backgroundInWorld() {
        return this.backgroundInWorld;
    }

    /**
     * The GUI sprite popups (category popups, confirmations, the color picker) draw as their panel, or null for the
     * scheme's flat panel. It should be a nine-slice sprite; the title is drawn 12 pixels from its top-left corner and
     * content starts 30 pixels down, so its top border can hold a title bar.
     */
    public @Nullable ResourceLocation popupSprite() {
        return this.popupSprite;
    }

    /** The effects drawn, by id, in order ({@link #STARFALL} by default; empty for none). */
    public List<ResourceLocation> effects() {
        return this.effects;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.colors = this.colors;
        builder.lightColors = this.lightColors;
        builder.icon = this.icon;
        builder.background = this.background;
        builder.mode = this.mode;
        builder.tileSize = this.tileSize;
        builder.backgroundOpacity = this.backgroundOpacity;
        builder.textureOpacity = this.textureOpacity;
        builder.backgroundInWorld = this.backgroundInWorld;
        builder.popupSprite = this.popupSprite;
        builder.effects = this.effects;
        builder.source = this.source;
        return builder;
    }

    /** Dark or light: each player's choice for every mod's config screens. */
    public enum Mode {
        DARK,
        LIGHT
    }

    /** How the background texture fills the screen. */
    public enum BackgroundMode {
        /** Scaled to cover the whole screen, keeping its proportions (edges may be cut off). */
        COVER,
        /** Stretched to the screen's size. */
        STRETCH,
        /** Repeated, like the vanilla dirt background. */
        TILE
    }

    public static final class Builder {
        private ConfigColorScheme colors = ConfigColorScheme.DARK;
        private @Nullable ConfigColorScheme lightColors;
        private @Nullable ResourceLocation icon;
        private @Nullable ResourceLocation background;
        private BackgroundMode mode = BackgroundMode.COVER;
        private int tileSize;
        private float backgroundOpacity = 0.35F;
        private float textureOpacity = 1.0F;
        private boolean backgroundInWorld;
        private @Nullable ResourceLocation popupSprite;
        private List<ResourceLocation> effects = List.of(STARFALL);
        private @Nullable Object source;

        private Builder() {}

        /** The other config system's theme this one is made from. */
        @ApiStatus.Internal
        public Builder source(@Nullable Object source) {
            this.source = source;
            return this;
        }

        /** Every color of the screens in dark mode. */
        public Builder colors(ConfigColorScheme colors) {
            this.colors = colors;
            return this;
        }

        /** Every color of the screens in light mode; by default the light scheme in the dark scheme's accent. */
        public Builder lightColors(ConfigColorScheme lightColors) {
            this.lightColors = lightColors;
            return this;
        }

        /** Just the accent (of both modes), keeping the rest; an opaque RGB like {@code 0xFF8A3D} works too. */
        public Builder accent(int color) {
            this.colors = this.colors.toBuilder().accent(color).build();
            if (this.lightColors != null) this.lightColors = this.lightColors.toBuilder().accent(color).build();
            return this;
        }

        /** An icon texture's full path for the top bar, instead of the mod's own icon. */
        public Builder icon(@Nullable ResourceLocation texture) {
            this.icon = texture;
            return this;
        }

        /** A texture's full path, like {@code mymod:textures/gui/config_background.png}. */
        public Builder background(@Nullable ResourceLocation texture) {
            this.background = texture;
            return this;
        }

        public Builder mode(BackgroundMode mode) {
            this.mode = mode;
            return this;
        }

        /** Tiles the texture at this size (in GUI pixels); 0 for the texture's own size. */
        public Builder tiled(int tileSize) {
            this.mode = BackgroundMode.TILE;
            this.tileSize = Math.max(0, tileSize);
            return this;
        }

        /**
         * The opacity of the backdrop color over the background, from 0 (not drawn) to 1 (hides it); 0.35 by default.
         */
        public Builder backgroundOpacity(float opacity) {
            this.backgroundOpacity = Math.clamp(opacity, 0, 1);
            return this;
        }

        /** The background texture's opacity, from 0 (invisible) to 1 (opaque, the default). */
        public Builder textureOpacity(float opacity) {
            this.textureOpacity = Math.clamp(opacity, 0, 1);
            return this;
        }

        /** Draws the texture in a world too, instead of the blurred world. */
        public Builder backgroundInWorld(boolean backgroundInWorld) {
            this.backgroundInWorld = backgroundInWorld;
            return this;
        }

        /**
         * A GUI sprite id (like {@code mymod:config/popup}, a texture in {@code textures/gui/sprites/}, usually with a
         * nine-slice {@code .mcmeta}) drawn as the panel of popups instead of the scheme's flat panel.
         */
        public Builder popupSprite(@Nullable ResourceLocation sprite) {
            this.popupSprite = sprite;
            return this;
        }

        /**
         * The effects the screens draw, by the ids they're registered under, instead of {@link #STARFALL}; drawn in this
         * order. With none, the screens are still.
         */
        public Builder effects(ResourceLocation... effects) {
            this.effects = List.of(effects);
            return this;
        }

        /** The effects, as a list. */
        public Builder effects(List<ResourceLocation> effects) {
            this.effects = List.copyOf(effects);
            return this;
        }

        public ConfigTheme build() {
            return new ConfigTheme(this);
        }
    }
}
