package net.ixdarklord.glazedmenu.internal.core;

import net.ixdarklord.glazedmenu.internal.modlist.ModEntry;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** What Glazed Menu needs from the loader it runs on; each loader module sets it before Glazed Menu starts. */
public interface GlazedPlatform {
    /** The id of the mod a class belongs to, or null when no mod's files hold it. */
    @Nullable String modOf(Class<?> type);

    /** Every other mod's own config screen the loader's mod list knows (Mod Menu, NeoForge's mod list), by mod id. */
    Map<String, UnaryOperator<Screen>> nativeScreens();

    /** Every loaded mod, for the mod list. */
    List<ModEntry> mods();

    boolean isModLoaded(String modId);

    /** The mod's display name, when it's loaded. */
    Optional<String> modName(String modId);

    /** The bytes of the mod's icon (its logo on NeoForge), when it has one. */
    Optional<byte[]> readModIcon(String modId);

    /** The game's config folder. */
    Path configDir();

    /** Whether NeoForge's config system is here: on NeoForge always, on Fabric with Forge Config API Port. */
    boolean hasNeoForgeConfigs();

    /**
     * The address of the mod's update file (the update JSON format NeoForge uses: a homepage and "promos" of the newest
     * version per game version), when the mod names one and the loader doesn't check it itself. On Fabric, from
     * {@code "custom": {"glazedmenu": {"update_json": "<url>"}}} in fabric.mod.json.
     */
    default Optional<String> updateJson(String modId) {
        return Optional.empty();
    }

    /** A newer version the loader itself found for the mod (NeoForge checks mods' update JSONs). */
    default Optional<net.ixdarklord.glazedmenu.internal.modlist.ModUpdates.Update> loaderUpdate(String modId) {
        return Optional.empty();
    }

    /**
     * The loader's own lines for the title screen, as its own title screen has them at the bottom left (its name and
     * version, how many mods are loaded), top first and without the game's own line; none where the loader has none.
     */
    default List<String> titleBranding() {
        return List.of();
    }

    /** What the loader says over the credits on its own title screen (that a newer version of it is out); none mostly. */
    default List<String> titleNotices() {
        return List.of();
    }

    /** What the loader draws on the title screen itself (a warning on a beta of it), as its own title screen calls for it. */
    default void renderTitle(net.minecraft.client.gui.screens.TitleScreen screen, net.minecraft.client.gui.GuiGraphics graphics, int alpha) {}

    /** Whether the loader's own update check has something to mark (the loader or a mod is out of date). */
    default boolean hasUpdateMark() {
        return false;
    }

    /**
     * The mark of the loader's own update check, as it draws it on its Mods button (an 8 by 8 picture, blinking where
     * something is out of date); nothing where there's nothing to say, or the loader has no such check.
     */
    default void renderUpdateMark(net.minecraft.client.gui.GuiGraphics graphics, int x, int y) {}

    /** The loader's name as Modrinth lists it ("fabric", "neoforge"), or empty when it isn't known. */
    default String loaderName() {
        return "";
    }

    /** The mod's own jar on disk: none for a mod inside another's jar, or one run from folders in development. */
    default Optional<Path> modFile(String modId) {
        return Optional.empty();
    }

    /** Whether a key only modifies others here (Ctrl, Shift, Alt on NeoForge, where a key bind may need one held). */
    default boolean isKeyModifier(com.mojang.blaze3d.platform.InputConstants.Key key) {
        return false;
    }

    /** Binds a key, with the modifier key held with it ({@code InputConstants.UNKNOWN} for none) where the loader has those. */
    default void bindKey(net.minecraft.client.KeyMapping mapping, com.mojang.blaze3d.platform.InputConstants.Key modifier,
                         com.mojang.blaze3d.platform.InputConstants.Key key) {
        mapping.setKey(key);
    }

    /** Sets a key bind back to its default. */
    default void resetKey(net.minecraft.client.KeyMapping mapping) {
        mapping.setKey(mapping.getDefaultKey());
    }

    static GlazedPlatform get() {
        if (Holder.platform == null) throw new IllegalStateException("Glazed Menu's loader module hasn't started");
        return Holder.platform;
    }

    static void set(GlazedPlatform platform) {
        Holder.platform = platform;
    }

    final class Holder {
        private static @Nullable GlazedPlatform platform;

        private Holder() {}
    }
}
