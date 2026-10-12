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
    public String loaderName() {
        return "forge";
    }

    // A mod inside another's jar has a path in that jar, not on disk.
    @Override
    public Optional<Path> modFile(String modId) {
        IModFileInfo file = ModList.get().getModFileById(modId);
        if (file == null) return Optional.empty();
        Path path = file.getFile().getFilePath();
        boolean onDisk = path.getFileSystem() == java.nio.file.FileSystems.getDefault() && Files.isRegularFile(path)
                && path.getFileName().toString().endsWith(".jar");
        return onDisk ? Optional.of(path) : Optional.empty();
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

    // Forge's own words for the title screen, from Forge itself: the lines its title screen draws at the bottom left
    // (its branding), of which the first is Forge with its version.
    private static List<String> branding() {
        List<String> lines = new ArrayList<>();
        net.minecraftforge.internal.BrandingControl.forEachLine(true, false, (index, line) -> lines.add(line));
        return lines;
    }

    @Override
    public String loaderLabel() {
        List<String> lines = branding();
        return lines.isEmpty() ? "" : lines.get(0);
    }

    // The rest of the branding, without what the frame says already: the game's version and how many mods are loaded
    // (told apart as Forge itself writes them). And what Forge draws over the copyright (a newer Forge), once its hook
    // has worked that out: Forge keeps the first answer, so it isn't asked before.
    @Override
    public List<String> titleExtras() {
        List<String> lines = branding();
        List<String> extras = new ArrayList<>(lines.subList(Math.min(1, lines.size()), lines.size()));
        extras.remove("Minecraft " + net.minecraftforge.versions.mcp.MCPVersion.getMCVersion());
        extras.remove(net.minecraftforge.common.ForgeI18n.parseMessage("fml.menu.loadingmods", ModList.get().size()));
        if (this.titleHooked) net.minecraftforge.internal.BrandingControl.forEachAboveCopyrightLine((index, line) -> extras.add(line));
        return extras;
    }

    private boolean titleHooked;

    // Forge's hook into its title screen: it works out whether there's a newer Forge, and draws its own warning on a
    // build that isn't a release.
    @Override
    public void extractTitle(net.minecraft.client.gui.screens.TitleScreen title, net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor graphics, float fade) {
        net.minecraftforge.client.ForgeHooksClient.renderMainMenu(title, graphics.raw(), Minecraft.getInstance().font, title.width, title.height, net.minecraft.util.Mth.ceil(fade * 255.0F));
        this.titleHooked = true;
    }

    // Whether Forge would draw its mark: as its own class for it decides.
    @Override
    public boolean hasUpdateMark() {
        if (this.updateStatus == null) this.updateStatus = Optional.ofNullable(net.minecraftforge.client.loading.ClientModLoader.checkForUpdates());
        return this.updateStatus.map(VersionChecker.Status::shouldDraw).orElse(false)
                && net.minecraftforge.fml.loading.FMLConfig.getBoolConfigValue(net.minecraftforge.fml.loading.FMLConfig.ConfigValue.VERSION_CHECK);
    }

    private @Nullable Optional<VersionChecker.Status> updateStatus;

    // Forge's own mark, drawn by Forge's own class for it: it draws on the button it's given, so it's given one that
    // puts the mark where it's wanted.
    private final net.minecraft.client.gui.components.Button markAnchor = net.minecraft.client.gui.components.Button.builder(
            net.minecraft.network.chat.Component.empty(), button -> {}).bounds(0, 0, 20, 20).build();
    private net.minecraftforge.client.gui.@Nullable TitleScreenModUpdateIndicator updateMark;

    @Override
    public void extractUpdateMark(net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor graphics, int x, int y) {
        if (this.updateMark == null) {
            this.updateMark = new net.minecraftforge.client.gui.TitleScreenModUpdateIndicator(this.markAnchor);
            this.updateMark.init();
        }
        // Forge draws it six pixels into a button of this size, from its left and its top.
        this.markAnchor.setPosition(x - 6, y - 6);
        this.updateMark.render(graphics.raw(), 0, 0, 0);
    }

    // Forge's key binds can need Ctrl, Shift or Alt held.
    @Override
    public boolean isKeyModifier(com.mojang.blaze3d.platform.InputConstants.Key key) {
        return net.minecraftforge.client.settings.KeyModifier.isKeyCodeModifier(key);
    }

    @Override
    public void bindKey(net.minecraft.client.KeyMapping mapping, com.mojang.blaze3d.platform.InputConstants.Key modifier,
                        com.mojang.blaze3d.platform.InputConstants.Key key) {
        net.minecraftforge.client.settings.KeyModifier held = net.minecraftforge.client.settings.KeyModifier.getModifier(modifier);
        mapping.setKeyModifierAndCode(held != null ? held : net.minecraftforge.client.settings.KeyModifier.NONE, key);
    }

    @Override
    public void resetKey(net.minecraft.client.KeyMapping mapping) {
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
