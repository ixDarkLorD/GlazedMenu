package net.ixdarklord.glazedmenu.api.config;

/**
 * Every color of a mod's config screens (ARGB). Start from a preset and change what you like:
 * <pre>{@code
 * ConfigColorScheme.tinted(0xFF7E57C2)                 // the dark look, its greys shaded toward purple
 * ConfigColorScheme.LIGHT.toBuilder().accent(0xFF2E7D32).build()
 * ConfigColorScheme.DARK.toBuilder().panel(0xC0101820).text(0xFFF0F0F0).build()
 * }</pre>
 * and hand it to {@link ConfigTheme.Builder#colors}. Translucent panel and bar colors let the background show through.
 */
public final class ConfigColorScheme {
    /** Dark glassy panels, light text, a cyan accent. */
    public static final ConfigColorScheme DARK = new Builder()
            .accent(0xFF4FC3F7).backdrop(0xFF05070B)
            .panel(0xB814181F).panelBorder(0xFF2A313F).bar(0xC80F1218).popup(0xF712161D)
            .rowHover(0x16FFFFFF).field(0xFF0C0F14).fieldBorder(0xFF343C4C)
            .button(0xFF222936).buttonHover(0xFF2C3545).buttonDisabled(0xFF191D26)
            .toggleOff(0xFF363E4E).knob(0xFFF5F7FB)
            .text(0xFFE9EDF5).textDim(0xFF96A0B3).textMuted(0xFF5D6577)
            .modified(0xFFFFC857).error(0xFFFF5C5C).success(0xFF62D98B).warning(0xFFE8A93C)
            .build();
    /** Frosted white panels, dark text, a blue accent. */
    public static final ConfigColorScheme LIGHT = new Builder()
            .accent(0xFF1E88E5).backdrop(0xFFE9EEF5)
            .panel(0xD6F4F6FA).panelBorder(0xFFC9D1DE).bar(0xE0FFFFFF).popup(0xFAFFFFFF)
            .rowHover(0x12000000).field(0xFFFFFFFF).fieldBorder(0xFFB8C2D1)
            .button(0xFFE6EAF1).buttonHover(0xFFDAE0EA).buttonDisabled(0xFFF0F2F5)
            .toggleOff(0xFFC3CBD8).knob(0xFFFFFFFF)
            .text(0xFF1B2230).textDim(0xFF5A6477).textMuted(0xFF98A2B3)
            .modified(0xFFC98300).error(0xFFD93636).success(0xFF2E9E5B).warning(0xFFC77A00)
            .build();

    private final int accent;
    private final int backdrop;
    private final int panel;
    private final int panelBorder;
    private final int bar;
    private final int popup;
    private final int rowHover;
    private final int field;
    private final int fieldBorder;
    private final int button;
    private final int buttonHover;
    private final int buttonDisabled;
    private final int toggleOff;
    private final int knob;
    private final int text;
    private final int textDim;
    private final int textMuted;
    private final int modified;
    private final int error;
    private final int success;
    private final int warning;

    private ConfigColorScheme(Builder builder) {
        this.accent = builder.accent;
        this.backdrop = builder.backdrop;
        this.panel = builder.panel;
        this.panelBorder = builder.panelBorder;
        this.bar = builder.bar;
        this.popup = builder.popup;
        this.rowHover = builder.rowHover;
        this.field = builder.field;
        this.fieldBorder = builder.fieldBorder;
        this.button = builder.button;
        this.buttonHover = builder.buttonHover;
        this.buttonDisabled = builder.buttonDisabled;
        this.toggleOff = builder.toggleOff;
        this.knob = builder.knob;
        this.text = builder.text;
        this.textDim = builder.textDim;
        this.textMuted = builder.textMuted;
        this.modified = builder.modified;
        this.error = builder.error;
        this.success = builder.success;
        this.warning = builder.warning;
    }

    /**
     * The dark scheme in a mod's own colors: the accent, with the panels, buttons and fields shaded toward it, so
     * each mod's screens feel distinct with one color.
     */
    public static ConfigColorScheme tinted(int accent) {
        return tinted(DARK, accent, 0.14F);
    }

    /** The light scheme in a mod's own colors: the accent, with the panels faintly shaded toward it. */
    public static ConfigColorScheme tintedLight(int accent) {
        return tinted(LIGHT, accent, 0.07F);
    }

    /** A scheme with its neutral colors shaded toward the accent by {@code strength} (0 to 1). */
    public static ConfigColorScheme tinted(ConfigColorScheme base, int accent, float strength) {
        int color = opaque(accent);
        return base.toBuilder()
                .accent(color)
                .backdrop(shade(base.backdrop, color, strength * 0.6F))
                .panel(shade(base.panel, color, strength))
                .panelBorder(shade(base.panelBorder, color, strength * 1.6F))
                .bar(shade(base.bar, color, strength))
                .popup(shade(base.popup, color, strength))
                .field(shade(base.field, color, strength * 0.6F))
                .fieldBorder(shade(base.fieldBorder, color, strength * 1.6F))
                .button(shade(base.button, color, strength))
                .buttonHover(shade(base.buttonHover, color, strength * 1.4F))
                .buttonDisabled(shade(base.buttonDisabled, color, strength * 0.6F))
                .toggleOff(shade(base.toggleOff, color, strength))
                .textDim(shade(base.textDim, color, strength * 0.8F))
                .textMuted(shade(base.textMuted, color, strength))
                .build();
    }

    // Mixes the RGB toward another color, keeping the alpha.
    private static int shade(int color, int toward, float amount) {
        float t = Math.clamp(amount, 0, 1);
        int r = Math.round((color >> 16 & 0xFF) + ((toward >> 16 & 0xFF) - (color >> 16 & 0xFF)) * t);
        int g = Math.round((color >> 8 & 0xFF) + ((toward >> 8 & 0xFF) - (color >> 8 & 0xFF)) * t);
        int b = Math.round((color & 0xFF) + ((toward & 0xFF) - (color & 0xFF)) * t);
        return color & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int opaque(int color) {
        return (color >>> 24) == 0 ? color | 0xFF000000 : color;
    }

    public static Builder builder() {
        return DARK.toBuilder();
    }

    /** Highlights, toggles, sliders, the main button. */
    public int accent() {
        return this.accent;
    }

    /** Laid over the panorama, world or texture behind the screen; its strength is the theme's dim. */
    public int backdrop() {
        return this.backdrop;
    }

    public int panel() {
        return this.panel;
    }

    public int panelBorder() {
        return this.panelBorder;
    }

    /** The top and bottom bars. */
    public int bar() {
        return this.bar;
    }

    /** Popups: the color picker, presets, confirmations. */
    public int popup() {
        return this.popup;
    }

    public int rowHover() {
        return this.rowHover;
    }

    /** Text fields. */
    public int field() {
        return this.field;
    }

    public int fieldBorder() {
        return this.fieldBorder;
    }

    public int button() {
        return this.button;
    }

    public int buttonHover() {
        return this.buttonHover;
    }

    public int buttonDisabled() {
        return this.buttonDisabled;
    }

    /** A switch's track while off. */
    public int toggleOff() {
        return this.toggleOff;
    }

    /** Switch and slider knobs. */
    public int knob() {
        return this.knob;
    }

    public int text() {
        return this.text;
    }

    /** Descriptions and secondary text. */
    public int textDim() {
        return this.textDim;
    }

    /** Disabled and placeholder text. */
    public int textMuted() {
        return this.textMuted;
    }

    /** Unsaved changes. */
    public int modified() {
        return this.modified;
    }

    public int error() {
        return this.error;
    }

    public int success() {
        return this.success;
    }

    /** Restart notices and server-side edits. */
    public int warning() {
        return this.warning;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        private int accent;
        private int backdrop;
        private int panel;
        private int panelBorder;
        private int bar;
        private int popup;
        private int rowHover;
        private int field;
        private int fieldBorder;
        private int button;
        private int buttonHover;
        private int buttonDisabled;
        private int toggleOff;
        private int knob;
        private int text;
        private int textDim;
        private int textMuted;
        private int modified;
        private int error;
        private int success;
        private int warning;

        private Builder() {}

        private Builder(ConfigColorScheme scheme) {
            this.accent = scheme.accent;
            this.backdrop = scheme.backdrop;
            this.panel = scheme.panel;
            this.panelBorder = scheme.panelBorder;
            this.bar = scheme.bar;
            this.popup = scheme.popup;
            this.rowHover = scheme.rowHover;
            this.field = scheme.field;
            this.fieldBorder = scheme.fieldBorder;
            this.button = scheme.button;
            this.buttonHover = scheme.buttonHover;
            this.buttonDisabled = scheme.buttonDisabled;
            this.toggleOff = scheme.toggleOff;
            this.knob = scheme.knob;
            this.text = scheme.text;
            this.textDim = scheme.textDim;
            this.textMuted = scheme.textMuted;
            this.modified = scheme.modified;
            this.error = scheme.error;
            this.success = scheme.success;
            this.warning = scheme.warning;
        }

        /** An opaque RGB like {@code 0xFF8A3D} works too. */
        public Builder accent(int color) {
            this.accent = opaque(color);
            return this;
        }

        public Builder backdrop(int color) {
            this.backdrop = color;
            return this;
        }

        public Builder panel(int color) {
            this.panel = color;
            return this;
        }

        public Builder panelBorder(int color) {
            this.panelBorder = color;
            return this;
        }

        public Builder bar(int color) {
            this.bar = color;
            return this;
        }

        public Builder popup(int color) {
            this.popup = color;
            return this;
        }

        public Builder rowHover(int color) {
            this.rowHover = color;
            return this;
        }

        public Builder field(int color) {
            this.field = color;
            return this;
        }

        public Builder fieldBorder(int color) {
            this.fieldBorder = color;
            return this;
        }

        public Builder button(int color) {
            this.button = color;
            return this;
        }

        public Builder buttonHover(int color) {
            this.buttonHover = color;
            return this;
        }

        public Builder buttonDisabled(int color) {
            this.buttonDisabled = color;
            return this;
        }

        public Builder toggleOff(int color) {
            this.toggleOff = color;
            return this;
        }

        public Builder knob(int color) {
            this.knob = color;
            return this;
        }

        public Builder text(int color) {
            this.text = color;
            return this;
        }

        public Builder textDim(int color) {
            this.textDim = color;
            return this;
        }

        public Builder textMuted(int color) {
            this.textMuted = color;
            return this;
        }

        public Builder modified(int color) {
            this.modified = color;
            return this;
        }

        public Builder error(int color) {
            this.error = color;
            return this;
        }

        public Builder success(int color) {
            this.success = color;
            return this;
        }

        public Builder warning(int color) {
            this.warning = color;
            return this;
        }

        public ConfigColorScheme build() {
            return new ConfigColorScheme(this);
        }
    }
}
