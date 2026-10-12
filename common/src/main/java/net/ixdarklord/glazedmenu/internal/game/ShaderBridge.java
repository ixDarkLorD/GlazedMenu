package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
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
    // Iris's package: its builds for 1.20.1 from 1.7 on, then the older ones (1.6, and Oculus, its port to Forge).
    private static final String[] IRIS_PACKAGES = {"net.irisshaders.iris", "net.coderbot.iris"};
    // Its API kept the one package throughout.
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    // Sodium's video settings: the 1.20.1 builds' screen (Embeddium and Rubidium, its ports to Forge, kept the name),
    // then the later ones'.
    private static final String[] SODIUM_SCREENS = {"me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI",
            "net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI", "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen"};
    // The package Iris is in here, once found; empty where it's none of those known.
    private static @Nullable String irisPackage;
    // Iris's own screen about to open on purpose (for a pack's settings): not one to open Glazed Menu's instead of.
    private static @Nullable Screen passing;

    private ShaderBridge() {}

    /** Whether Iris is here. */
    public static boolean hasShaders() {
        return GlazedPlatform.get().isModLoaded("iris") || GlazedPlatform.get().isModLoaded("oculus");
    }

    public static boolean hasSodium() {
        return GlazedPlatform.get().isModLoaded("sodium") || GlazedPlatform.get().isModLoaded("embeddium") || GlazedPlatform.get().isModLoaded("rubidium");
    }

    // A class of Iris's, by its name within Iris's package (".Iris", ".gui.screen.ShaderPackScreen").
    private static Class<?> irisClass(String name) throws ClassNotFoundException {
        if (irisPackage == null) {
            String found = "";
            for (String candidate : IRIS_PACKAGES) {
                try {
                    Class.forName(candidate + ".Iris");
                    found = candidate;
                    break;
                } catch (ClassNotFoundException | LinkageError e) {
                    // Not this one.
                }
            }
            irisPackage = found;
        }
        if (irisPackage.isEmpty()) throw new ClassNotFoundException("Iris" + name);
        return Class.forName(irisPackage + name);
    }

    private static Object iris(String method) throws ReflectiveOperationException {
        return irisClass(".Iris").getMethod(method).invoke(null);
    }

    /** Whether this is Iris's own shader packs screen. */
    static boolean isIrisScreen(Screen screen) {
        String name = screen.getClass().getName();
        for (String candidate : IRIS_PACKAGES) {
            if (name.equals(candidate + ".gui.screen.ShaderPackScreen")) return true;
        }
        return false;
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
                try {
                    irisClass(".Iris").getMethod("clearShaderPackOptionQueue").invoke(null);
                } catch (NoSuchMethodException e) {
                    // A build of Iris without the queue of a pack's changed settings: nothing to clear.
                }
                config.getClass().getMethod("setShaderPackName", String.class).invoke(config, pack);
            }
            try {
                Object api = Class.forName(IRIS_API).getMethod("getInstance").invoke(null);
                Object apiConfig = Class.forName(IRIS_API).getMethod("getConfig").invoke(api);
                Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(apiConfig, enabled);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // A build of Iris without that part of its API: what it does, by Iris's own methods.
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, enabled);
                config.getClass().getMethod("save").invoke(config);
                iris("reload");
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.warn("Couldn't tell Iris the shader pack to use", e);
            return false;
        }
    }

    /** Copies a dropped file or folder into Iris's shader packs folder, when it's a shader pack; returns whether it was. */
    public static boolean addPack(Path file) {
        try {
            if (!(boolean) irisClass(".Iris").getMethod("isValidShaderpack", Path.class).invoke(null, file)) return false;
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
            Screen screen = (Screen) irisClass(".gui.screen.ShaderPackScreen").getConstructor(Screen.class).newInstance(parent);
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
        Throwable failure = null;
        for (String name : SODIUM_SCREENS) {
            Class<?> type;
            try {
                type = Class.forName(name);
            } catch (ClassNotFoundException | LinkageError e) {
                if (failure == null) failure = e;
                continue;
            }
            // The later builds make the screen through createScreen, the earlier ones through its constructor.
            try {
                return (Screen) type.getMethod("createScreen", Screen.class).invoke(null, parent);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                failure = e;
            }
            try {
                Constructor<?> constructor = type.getDeclaredConstructor(Screen.class);
                constructor.setAccessible(true);
                return (Screen) constructor.newInstance(parent);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                failure = e;
            }
        }
        GlazedMenu.LOGGER.warn("Couldn't open Sodium's video settings", failure);
        return null;
    }
}
