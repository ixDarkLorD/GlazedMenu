package net.ixdarklord.glazedmenu.internal.modlist;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One loaded mod, as the loader describes it: what Glazed Menu's mod list shows.
 *
 * @param parent  the mod it ships inside (a jar-in-jar library), or the mod it asks to be listed under
 * @param library a library or part of the game (Minecraft, the loader, Java), listed apart from other mods
 */
public record ModEntry(String id, String name, String version, String description, List<String> authors, List<String> contributors,
                       @Nullable String license, @Nullable String homepage, @Nullable String issues, @Nullable String sources,
                       @Nullable String parent, boolean library, Side side, List<String> dependencies) {
    /** Which side a mod runs on. */
    public enum Side {
        CLIENT,
        SERVER,
        BOTH
    }
}
