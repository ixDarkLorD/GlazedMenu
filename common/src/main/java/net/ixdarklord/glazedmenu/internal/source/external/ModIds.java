package net.ixdarklord.glazedmenu.internal.source.external;

import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;

import java.util.Locale;

// Which mod a config of another system belongs to: the mod whose files hold its class, else a loaded mod named like
// the config, else the config's own name.
public final class ModIds {
    private ModIds() {}

    public static String of(Class<?> configClass, String... names) {
        String owner = GlazedPlatform.get().modOf(configClass);
        if (owner != null) return owner;
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (GlazedPlatform.get().isModLoaded(lower)) return lower;
            String first = lower.split("[/:._-]", 2)[0];
            if (GlazedPlatform.get().isModLoaded(first)) return first;
        }
        return names.length > 0 && names[0] != null ? names[0].toLowerCase(Locale.ROOT) : "unknown";
    }
}
