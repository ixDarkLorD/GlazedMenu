package net.ixdarklord.glazedmenu.internal.core;

import java.util.Locale;
import java.util.regex.Pattern;

// Readable names for keys and ids that have no translation.
public final class Names {
    // Word boundaries of camelCase, snake_case and kebab-case keys.
    private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|[_-]+");

    private Names() {}

    /** {@code maxCount}, {@code max_count} and {@code max-count} all read "Max Count". */
    public static String prettify(String key) {
        StringBuilder out = new StringBuilder();
        for (String word : WORD_BOUNDARY.split(key)) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    /** A mod's display name, or its id made readable. */
    public static String modName(String modId) {
        return GlazedPlatform.get().modName(modId).orElseGet(() -> prettify(modId));
    }
}
