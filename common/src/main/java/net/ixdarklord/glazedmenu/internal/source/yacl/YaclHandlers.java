package net.ixdarklord.glazedmenu.internal.source.yacl;

import java.util.ArrayList;
import java.util.List;

// YACL's config handlers as they're made (see YaclConfigClassHandlerMixin). Kept as plain objects so this class never
// needs YACL's.
public final class YaclHandlers {
    private static final List<Object> HANDLERS = new ArrayList<>();

    private YaclHandlers() {}

    public static void track(Object handler) {
        synchronized (HANDLERS) {
            HANDLERS.add(handler);
        }
    }

    public static List<Object> all() {
        synchronized (HANDLERS) {
            return List.copyOf(HANDLERS);
        }
    }
}
