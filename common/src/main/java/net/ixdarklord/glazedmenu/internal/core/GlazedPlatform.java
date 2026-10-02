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
