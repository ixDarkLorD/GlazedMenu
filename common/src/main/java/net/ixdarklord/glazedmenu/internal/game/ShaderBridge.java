package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What Glazed Menu asks of Iris (the shader mod), and of Sodium for its video settings: neither is compiled against, so
 * every call goes by name and fails quietly, leaving the mod's own screen the way in. Iris's shader packs are its own
 * to find, choose and load; this only reads which there are and which is in use, and tells it the player's choice the
 * way its own screen does.
 */
public final class ShaderBridge {
    private static final String IRIS = "net.irisshaders.iris.Iris";
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    static final String IRIS_SCREEN = "net.irisshaders.iris.gui.screen.ShaderPackScreen";
    private static final String SODIUM_SCREEN = "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen";
    // Iris's own screen about to open on purpose (for a pack's settings): not one to open Glazed Menu's instead of.
    private static @Nullable Screen passing;

    private ShaderBridge() {}

    /** Whether Iris is here. */
    public static boolean hasShaders() {
        return GlazedPlatform.get().isModLoaded("iris");
    }

    public static boolean hasSodium() {
        return GlazedPlatform.get().isModLoaded("sodium");
    }

    private static Object iris(String method) throws ReflectiveOperationException {
        return Class.forName(IRIS).getMethod(method).invoke(null);
    }

    private static Object config() throws ReflectiveOperationException {
        return iris("getIrisConfig");
    }

    /** The shader packs in Iris's folder, by name, as Iris lists them. */
    @SuppressWarnings("unchecked")
    public static List<String> packs() {
        try {
            Object manager = iris("getShaderpacksDirectoryManager");
            return List.copyOf((List<String>) manager.getClass().getMethod("enumerate").invoke(manager));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.debug("Couldn't list Iris's shader packs", e);
            return List.of();
        }
    }

    /** The pack Iris is set to use, whether or not shaders are on. */
    @SuppressWarnings("unchecked")
    public static Optional<String> chosenPack() {
        try {
            Object config = config();
            return (Optional<String>) config.getClass().getMethod("getShaderPackName").invoke(config);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    public static boolean enabled() {
        try {
            Object config = config();
            return (boolean) config.getClass().getMethod("areShadersEnabled").invoke(config);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    public static @Nullable Path folder() {
        try {
            return (Path) iris("getShaderpacksDirectory");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * Tells Iris the player's choice, as its own screen's Apply does: the pack (when it changed), then whether shaders
     * are on, which loads the pack.
     *
     * @return whether Iris took it
     */
    public static boolean apply(@Nullable String pack, boolean enabled) {
        try {
            Object config = config();
            if (pack != null && !pack.equals(chosenPack().orElse(null))) {
                Class.forName(IRIS).getMethod("clearShaderPackOptionQueue").invoke(null);
                config.getClass().getMethod("setShaderPackName", String.class).invoke(config, pack);
            }
            Object api = Class.forName(IRIS_API).getMethod("getInstance").invoke(null);
            Object apiConfig = Class.forName(IRIS_API).getMethod("getConfig").invoke(api);
            Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(apiConfig, enabled);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.warn("Couldn't tell Iris the shader pack to use", e);
            return false;
        }
    }

    /** Copies a dropped file or folder into Iris's shader packs folder, when it's a shader pack; returns whether it was. */
    public static boolean addPack(Path file) {
        try {
            if (!(boolean) Class.forName(IRIS).getMethod("isValidShaderpack", Path.class).invoke(null, file)) return false;
            Object manager = iris("getShaderpacksDirectoryManager");
            manager.getClass().getMethod("copyPackIntoDirectory", String.class, Path.class).invoke(manager, file.getFileName().toString(), file);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.warn("Couldn't copy {} into the shader packs folder", file, e);
            return false;
        }
    }

    /** Iris's own screen, for what only it has (a pack's settings); it opens as itself, not as Glazed Menu's. */
    public static @Nullable Screen irisScreen(@Nullable Screen parent) {
        try {
            Screen screen = (Screen) Class.forName(IRIS_SCREEN).getConstructor(Screen.class).newInstance(parent);
            passing = screen;
            return screen;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.warn("Couldn't open Iris's screen", e);
            return null;
        }
    }

    /** Whether this is Iris's screen opened on purpose; asked once, as it opens. */
    static boolean isPassing(Screen screen) {
        if (screen != passing) return false;
        passing = null;
        return true;
    }

    /** Sodium's video settings, which hold what the game's own video options don't with Sodium here. */
    public static @Nullable Screen sodiumScreen(@Nullable Screen parent) {
        try {
            return (Screen) Class.forName(SODIUM_SCREEN).getMethod("createScreen", Screen.class).invoke(null, parent);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.warn("Couldn't open Sodium's video settings", e);
            return null;
        }
    }
}
