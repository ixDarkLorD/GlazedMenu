package net.ixdarklord.glazedmenu.internal.core.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.modlist.ModEntry;
import net.ixdarklord.glazedmenu.internal.modlist.ModUpdates;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

final class NeoForgeGlazedPlatform implements GlazedPlatform {
    @Override
    public @Nullable String modOf(Class<?> type) {
        Path location = location(type);
        if (location == null) return null;
        for (IModFileInfo file : ModList.get().getModFiles()) {
            try {
                if (!location.equals(file.getFile().getFilePath().toAbsolutePath().normalize()) || file.getMods().isEmpty()) continue;
                return file.getMods().getFirst().getModId();
            } catch (RuntimeException e) {
                GlazedMenu.LOGGER.debug("Couldn't read mod file {}", file, e);
            }
        }
        return null;
    }

    // The screens mods registered for NeoForge's mod list.
    @Override
    public Map<String, UnaryOperator<Screen>> nativeScreens() {
        Map<String, UnaryOperator<Screen>> screens = new LinkedHashMap<>();
        ModList.get().forEachModContainer((modId, container) -> container.getCustomExtension(IConfigScreenFactory.class)
                .ifPresent(factory -> screens.put(modId, parent -> factory.createScreen(container, parent))));
        return screens;
    }

    // The game and the loader count as libraries.
    private static final Set<String> SYSTEM = Set.of("minecraft", "neoforge");
    private static @Nullable Method versionChecker;
    private static boolean versionCheckerFound;

    @Override
    public List<ModEntry> mods() {
        List<ModEntry> mods = new ArrayList<>();
        for (IModInfo mod : ModList.get().getMods()) {
            // Made in development, for libraries on the classpath.
            if (mod.getModId().startsWith("generated_")) continue;
            List<String> dependencies = new ArrayList<>();
            for (IModInfo.ModVersion dependency : mod.getDependencies()) {
                if (dependency.getType() == IModInfo.DependencyType.REQUIRED) dependencies.add(dependency.getModId());
            }
            // Further mods in a file with several are listed under its first.
            List<IModInfo> fileMods = mod.getOwningFile().getMods();
            String parent = fileMods.size() > 1 && fileMods.getFirst() != mod ? fileMods.getFirst().getModId() : null;
            mods.add(new ModEntry(mod.getModId(), mod.getDisplayName(), mod.getVersion().toString(), mod.getDescription().strip(),
                    names(mod.getConfig().getConfigElement("authors").orElse(null)), names(mod.getConfig().getConfigElement("credits").orElse(null)),
                    blankToNull(mod.getOwningFile().getLicense()),
                    mod.getConfig().<String>getConfigElement("displayURL").or(() -> mod.getModURL().map(URL::toString)).orElse(null),
                    mod.getOwningFile().getConfig().<String>getConfigElement("issueTrackerURL").orElse(null),
                    null, parent, SYSTEM.contains(mod.getModId()) || isLibrary(mod), ModEntry.Side.BOTH, dependencies));
        }
        return mods;
    }

    // A mod marks itself a library in its mods.toml: [modproperties.<id>] library = true (or Mod Menu's badges = ["library"]).
    private static boolean isLibrary(IModInfo mod) {
        Map<String, Object> properties = mod.getModProperties();
        if (Boolean.TRUE.equals(properties.get("library")) || "true".equals(properties.get("library"))) return true;
        return properties.get("badges") instanceof List<?> badges && badges.contains("library");
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Optional<String> modName(String modId) {
        return ModList.get().getModContainerById(modId).map(container -> container.getModInfo().getDisplayName());
    }

    // A mod's square icon (iconFile, since NeoForge 26.2), else its logo (logoFile, the older name).
    private static Optional<String> iconFile(IModInfo mod) {
        Optional<String> icon = mod.getConfig().<String>getConfigElement("iconFile");
        return icon.isPresent() ? icon : mod.getLogoFile();
    }

    @Override
    public Optional<byte[]> readModIcon(String modId) {
        return ModList.get().getModContainerById(modId).flatMap(container -> iconFile(container.getModInfo()).flatMap(logo -> {
            try (InputStream stream = container.getModInfo().getOwningFile().getFile().getContents().openFile(logo)) {
                return stream == null ? Optional.empty() : Optional.of(stream.readAllBytes());
            } catch (IOException e) {
                return Optional.empty();
            }
        }));
    }

    @Override
    public String loaderName() {
        return "neoforge";
    }

    // A mod inside another's jar has a path in that jar, not on disk.
    @Override
    public Optional<Path> modFile(String modId) {
        IModFileInfo file = ModList.get().getModFileById(modId);
        if (file == null) return Optional.empty();
        Path path = file.getFile().getFilePath();
        boolean onDisk = path.getFileSystem() == FileSystems.getDefault() && Files.isRegularFile(path)
                && path.getFileName().toString().endsWith(".jar");
        return onDisk ? Optional.of(path) : Optional.empty();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean hasNeoForgeConfigs() {
        return true;
    }

    // NeoForge's own check of mods' update JSONs (when its version check is on). Its checker moved from FML
    // (net.neoforged.fml.VersionChecker, the early 26.3 builds) into NeoForge (net.neoforged.neoforge.common), so it's
    // found by name: whichever this NeoForge has, or none (no result from it, rather than a crash as the game loads).
    @Override
    public Optional<ModUpdates.Update> loaderUpdate(String modId) {
        Method getResult = versionChecker();
        if (getResult == null) return Optional.empty();
        return ModList.get().getModContainerById(modId).flatMap(container -> {
            try {
                Object result = getResult.invoke(null, container.getModInfo());
                if (result == null) return Optional.empty();
                Object status = result.getClass().getMethod("status").invoke(result);
                Object target = result.getClass().getMethod("target").invoke(result);
                Object found = result.getClass().getMethod("url").invoke(result);
                String name = status instanceof Enum<?> constant ? constant.name() : "";
                if (!name.equals("OUTDATED") && !name.equals("BETA_OUTDATED") || target == null) return Optional.empty();
                String url = found != null ? found.toString() : container.getModInfo().getModURL().map(Object::toString).orElse("");
                return Optional.of(new ModUpdates.Update(target.toString(), url));
            } catch (ReflectiveOperationException | RuntimeException e) {
                GlazedMenu.LOGGER.debug("Couldn't read NeoForge's update check of {}", modId, e);
                return Optional.empty();
            }
        });
    }

    // VersionChecker.getResult(IModInfo), wherever this NeoForge keeps it; null where it has none.
    private static @Nullable Method versionChecker() {
        if (!versionCheckerFound) {
            versionCheckerFound = true;
            for (String name : List.of("net.neoforged.neoforge.common.VersionChecker", "net.neoforged.fml.VersionChecker")) {
                try {
                    versionChecker = Class.forName(name).getMethod("getResult", IModInfo.class);
                    break;
                } catch (ReflectiveOperationException | LinkageError e) {
                    GlazedMenu.LOGGER.debug("No NeoForge version checker at {}", name);
                }
            }
        }
        return versionChecker;
    }

    // NeoForge's key binds can need Ctrl, Shift or Alt held.
    @Override
    public boolean isKeyModifier(InputConstants.Key key) {
        return KeyModifier.isKeyCodeModifier(key);
    }

    @Override
    public void bindKey(KeyMapping mapping, InputConstants.Key modifier,
                        InputConstants.Key key) {
        mapping.setKeyModifierAndCode(KeyModifier.getKeyModifier(modifier), key);
    }

    @Override
    public void resetKey(KeyMapping mapping) {
        mapping.setToDefault();
    }

    // "A, B and C" or "A & B", as people write them in mods.toml.
    private static List<String> names(@Nullable Object value) {
        if (value == null) return List.of();
        return Arrays.stream(value.toString().split(",|&| and ")).map(String::strip).filter(name -> !name.isEmpty()).toList();
    }

    private static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private static @Nullable Path location(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) return null;
        try {
            return Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
