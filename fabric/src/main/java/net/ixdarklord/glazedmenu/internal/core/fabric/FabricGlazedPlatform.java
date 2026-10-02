package net.ixdarklord.glazedmenu.internal.core.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.api.metadata.Person;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.modlist.ModEntry;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

final class FabricGlazedPlatform implements GlazedPlatform {
    @Override
    public @Nullable String modOf(Class<?> type) {
        Path location = location(type);
        if (location == null) return null;
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            // Mods inside another mod's jar have no paths of their own.
            if (mod.getOrigin().getKind() != ModOrigin.Kind.PATH) continue;
            for (Path path : mod.getOrigin().getPaths()) {
                if (location.equals(path.toAbsolutePath().normalize())) return mod.getMetadata().getId();
            }
        }
        return null;
    }

    // Each mod's own Mod Menu screen (not the ones mods provide for others).
    @Override
    public Map<String, UnaryOperator<Screen>> nativeScreens() {
        return FabricLoader.getInstance().isModLoaded("modmenu") ? ModMenuScreens.own() : Map.of();
    }

    // The game and the loader's own entries count as libraries, as do mods Mod Menu's metadata calls one.
    private static final Set<String> SYSTEM = Set.of("minecraft", "java", "fabricloader", "mixinextras");

    @Override
    public List<ModEntry> mods() {
        List<ModEntry> mods = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            ModMetadata metadata = mod.getMetadata();
            // Loom makes these in development, for libraries on the classpath.
            if (metadata.getId().startsWith("generated_")) continue;
            List<String> badges = modMenuList(metadata, "badges");
            String parent = mod.getContainingMod().map(container -> container.getMetadata().getId()).orElse(null);
            CustomValue modMenuParent = modMenuValue(metadata, "parent");
            if (parent == null && modMenuParent != null) {
                parent = modMenuParent.getType() == CustomValue.CvType.STRING ? modMenuParent.getAsString()
                        : modMenuParent.getType() == CustomValue.CvType.OBJECT && modMenuParent.getAsObject().get("id") != null
                        ? modMenuParent.getAsObject().get("id").getAsString() : null;
            }
            // Fabric API's modules: inside its jar in a normal install, loose in development; listed under it either way.
            boolean apiModule = metadata.containsCustomValue("fabric-api:module-lifecycle");
            if (parent == null && apiModule && !metadata.getId().equals("fabric-api") && FabricLoader.getInstance().isModLoaded("fabric-api")) {
                parent = "fabric-api";
            }
            List<String> dependencies = new ArrayList<>();
            for (ModDependency dependency : metadata.getDependencies()) {
                if (dependency.getKind() == ModDependency.Kind.DEPENDS) dependencies.add(dependency.getModId());
            }
            mods.add(new ModEntry(metadata.getId(), metadata.getName(), metadata.getVersion().getFriendlyString(), metadata.getDescription(),
                    metadata.getAuthors().stream().map(Person::getName).toList(), metadata.getContributors().stream().map(Person::getName).toList(),
                    metadata.getLicense().isEmpty() ? null : String.join(", ", metadata.getLicense()),
                    metadata.getContact().get("homepage").orElse(null), metadata.getContact().get("issues").orElse(null),
                    metadata.getContact().get("sources").orElse(null), parent,
                    SYSTEM.contains(metadata.getId()) || badges.contains("library") || declaresLibrary(metadata) || apiModule && !metadata.getId().equals("fabric-api"),
                    metadata.getEnvironment() == ModEnvironment.CLIENT ? ModEntry.Side.CLIENT
                            : metadata.getEnvironment() == ModEnvironment.SERVER ? ModEntry.Side.SERVER : ModEntry.Side.BOTH,
                    dependencies));
        }
        return mods;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Optional<String> modName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(container -> container.getMetadata().getName());
    }

    @Override
    public Optional<byte[]> readModIcon(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .flatMap(container -> container.getMetadata().getIconPath(64).flatMap(container::findPath))
                .flatMap(path -> {
                    try {
                        return Optional.of(Files.readAllBytes(path));
                    } catch (IOException e) {
                        return Optional.empty();
                    }
                });
    }

    @Override
    public Optional<String> updateJson(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).flatMap(container -> {
            CustomValue glazedmenu = container.getMetadata().getCustomValue("glazedmenu");
            if (glazedmenu == null || glazedmenu.getType() != CustomValue.CvType.OBJECT) return Optional.empty();
            CustomValue url = glazedmenu.getAsObject().get("update_json");
            return url != null && url.getType() == CustomValue.CvType.STRING && url.getAsString().startsWith("http")
                    ? Optional.of(url.getAsString()) : Optional.empty();
        });
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean hasForgeConfigs() {
        return FabricLoader.getInstance().isModLoaded("forgeconfigapiport");
    }

    private static @Nullable CustomValue modMenuValue(ModMetadata metadata, String key) {
        CustomValue modMenu = metadata.getCustomValue("modmenu");
        return modMenu != null && modMenu.getType() == CustomValue.CvType.OBJECT ? modMenu.getAsObject().get(key) : null;
    }

    // Glazed Menu's own mark, for mods that don't use Mod Menu's badges: "custom": { "glazedmenu": { "library": true } }.
    private static boolean declaresLibrary(ModMetadata metadata) {
        CustomValue glazedmenu = metadata.getCustomValue("glazedmenu");
        if (glazedmenu == null || glazedmenu.getType() != CustomValue.CvType.OBJECT) return false;
        CustomValue library = glazedmenu.getAsObject().get("library");
        return library != null && library.getType() == CustomValue.CvType.BOOLEAN && library.getAsBoolean();
    }

    private static List<String> modMenuList(ModMetadata metadata, String key) {
        CustomValue value = modMenuValue(metadata, key);
        List<String> list = new ArrayList<>();
        if (value != null && value.getType() == CustomValue.CvType.ARRAY) {
            value.getAsArray().forEach(element -> {
                if (element.getType() == CustomValue.CvType.STRING) list.add(element.getAsString());
            });
        }
        return list;
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
