package net.ixdarklord.glazedmenu.internal.style;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.compat.RenderPipelines;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.minecraft.resources.ResourceLocation;

// The config screens' icons: white 64x64 textures (assets/glazedmenu/textures/gui/style/icons/) with linear filtering
// (their .mcmeta sets "blur"), so they shrink smoothly to the size drawn; tinted as they're drawn. A resource pack can
// replace any of them.
public final class ConfigIcons {
    /** A power symbol: the value needs a restart. */
    public static final Icon RESTART = icon("restart");
    /** A counter-clockwise arrow: back to the default. */
    public static final Icon RESET = icon("reset");
    public static final Icon UNDO = icon("undo");
    public static final Icon REDO = icon("redo");
    public static final Icon UP = icon("up");
    public static final Icon DOWN = icon("down");
    public static final Icon CLOSE = icon("close");
    public static final Icon PLUS = icon("plus");
    public static final Icon SEARCH = icon("search");
    public static final Icon LOCK = icon("lock");
    public static final Icon CHEVRON = icon("chevron");
    public static final Icon CHEVRON_LEFT = icon("chevron_left");
    public static final Icon CHECK = icon("check");
    public static final Icon WARNING = icon("warning");
    public static final Icon LIST = icon("list");
    public static final Icon GRID = icon("grid");
    /** A gear: a mod's settings. */
    public static final Icon GEAR = icon("gear");
    /** A gear and a wrench: the mods' configs. */
    public static final Icon CONFIGS = icon("configs");
    /** A folder: opens one. */
    public static final Icon FOLDER = icon("folder");
    public static final Icon SUN = icon("sun");
    public static final Icon MOON = icon("moon");

    // 32x32 icons of what each kind of config is, drawn larger: beside a config's title and on its card.
    public static final Icon SCOPE_CLIENT = icon("scope/client");
    public static final Icon SCOPE_COMMON = icon("scope/common");
    public static final Icon SCOPE_SERVER = icon("scope/server");
    public static final Icon SCOPE_WORLD = icon("scope/world");
    public static final Icon SCOPE_STARTUP = icon("scope/startup");

    /** The size icons are drawn at, in GUI pixels, when no size is given. */
    public static final int SIZE = 10;

    private ConfigIcons() {}

    public static Icon forScope(ConfigScope scope) {
        return switch (scope) {
            case CLIENT -> SCOPE_CLIENT;
            case COMMON -> SCOPE_COMMON;
            case SERVER -> SCOPE_SERVER;
            case WORLD -> SCOPE_WORLD;
            case STARTUP -> SCOPE_STARTUP;
        };
    }

    private static final int TEXTURE_SIZE = 64;

    private static Icon icon(String name) {
        return new Icon(GlazedMenu.rl("textures/gui/style/icons/" + name + ".png"));
    }

    public record Icon(ResourceLocation texture) {
        public int width() {
            return SIZE;
        }

        public int height() {
            return SIZE;
        }

        public void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
            this.draw(graphics, x, y, SIZE, color);
        }

        public void draw(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, x, y, 0, 0, size, size, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, color);
        }

        /** Draws the icon centered in a box. */
        public void drawCentered(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
            this.draw(graphics, x + (width - SIZE) / 2, y + (height - SIZE) / 2, color);
        }
    }
}
