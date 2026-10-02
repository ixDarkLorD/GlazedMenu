package net.ixdarklord.glazedmenu.internal.core.forge;

import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.modlist.ModEntry;
import net.ixdarklord.glazedmenu.internal.modlist.ModUpdates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
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

final class ForgeGlazedPlatform implements GlazedPlatform {
    @Override
    public @Nullable String modOf(Class<?> type) {
        Path location = location(type);
        if (location == null) return null;
        for (IModFileInfo file : ModList.get().getModFiles()) {
            try {
                if (!location.equals(file.getFile().getFilePath().toAbsolutePath().normalize()) || file.getMods().isEmpty()) continue;
                return file.getMods().get(0).getModId();
            } catch (RuntimeException e) {
                GlazedMenu.LOGGER.debug("Couldn't read mod file {}", file, e);
            }
        }
        return null;
    }

    // The screens mods registered for Forge's mod list.
    @Override
    public Map<String, UnaryOperator<Screen>> nativeScreens() {
        Map<String, UnaryOperator<Screen>> screens = new LinkedHashMap<>();
        ModList.get().forEachModContainer((modId, container) -> container.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class)
                .ifPresent(factory -> screens.put(modId, parent -> factory.screenFunction().apply(Minecraft.getInstance(), parent))));
        return screens;
    }

    // The game and the loader count as libraries.
    private static final Set<String> SYSTEM = Set.of("minecraft", "forge");

    @Override
    public List<ModEntry> mods() {
        List<ModEntry> mods = new ArrayList<>();
        for (IModInfo mod : ModList.get().getMods()) {
            List<String> dependencies = new ArrayList<>();
            for (IModInfo.ModVersion dependency : mod.getDependencies()) {
                if (dependency.isMandatory()) dependencies.add(dependency.getModId());
            }
            // Further mods in a file with several are listed under its first.
            List<IModInfo> fileMods = mod.getOwningFile().getMods();
            String parent = fileMods.size() > 1 && fileMods.get(0) != mod ? fileMods.get(0).getModId() : null;
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

    @Override
    public Optional<byte[]> readModIcon(String modId) {
        return ModList.get().getModContainerById(modId).flatMap(container -> container.getModInfo().getLogoFile().flatMap(logo -> {
            Path path = container.getModInfo().getOwningFile().getFile().findResource(logo);
            if (!Files.isRegularFile(path)) return Optional.empty();
            try (InputStream stream = Files.newInputStream(path)) {
                return Optional.of(stream.readAllBytes());
            } catch (IOException e) {
                return Optional.empty();
            }
        }));
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean hasForgeConfigs() {
        return true;
    }

    // Forge's own check of mods' update JSONs (when its version check is on).
    @Override
    public Optional<ModUpdates.Update> loaderUpdate(String modId) {
        return ModList.get().getModContainerById(modId).flatMap(container -> {
            var result = VersionChecker.getResult(container.getModInfo());
            boolean outdated = result.status() == VersionChecker.Status.OUTDATED || result.status() == VersionChecker.Status.BETA_OUTDATED;
            if (!outdated || result.target() == null) return Optional.empty();
            String url = result.url() != null ? result.url() : container.getModInfo().getModURL().map(Object::toString).orElse("");
            return Optional.of(new ModUpdates.Update(result.target().toString(), url));
        });
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
