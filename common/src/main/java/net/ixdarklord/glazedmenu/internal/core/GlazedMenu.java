package net.ixdarklord.glazedmenu.internal.core;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class GlazedMenu {
    public static final String MOD_ID = "glazedmenu";
    public static final String MOD_NAME = "Glazed Menu";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private GlazedMenu() {}

    public static Identifier rl(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name.toLowerCase(Locale.ROOT));
    }
}
