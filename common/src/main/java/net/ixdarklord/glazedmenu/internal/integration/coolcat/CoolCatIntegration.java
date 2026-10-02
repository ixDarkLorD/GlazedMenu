package net.ixdarklord.glazedmenu.internal.integration.coolcat;

import net.ixdarklord.coolcatcore.api.config.ConfigEvents;
import net.ixdarklord.coolcatcore.internal.config.ConfigImpl;
import net.ixdarklord.coolcatcore.internal.config.client.ClientConfigManager;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.type.EnumType;
import net.ixdarklord.glazedmenu.internal.gui.CategoryPopup;
import net.ixdarklord.glazedmenu.internal.gui.ConfigScreen;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Glazed Menu with CoolCatLib: Core installed: Core's configs in Glazed Menu's screens (as CoolCatConfigs), the themes and enum
 * names mods set with Core's API, the server's values refreshing an open screen, and the screen offering a server's
 * startup values after a mismatch. Only loaded when Core is.
 */
public final class CoolCatIntegration {
    private CoolCatIntegration() {}

    public static void init() {
        ConfigSources.register(CoolCatSource.INSTANCE);
        ConfigTheme.setFallback(CoolCatIntegration::theme);
        EnumType.addNames(constant -> constant instanceof net.ixdarklord.coolcatcore.api.config.type.EnumType.Displayable displayable
                ? new EnumType.Displayable() {
                    @Override
                    public Component displayName() {
                        return displayable.displayName();
                    }

                    @Override
                    public @Nullable Component description() {
                        return displayable.description();
                    }
                } : null);
        ClientConfigManager.setMismatchScreen(StartupMismatchScreen::new);
        // Core's screen API (for mods that only depend on Core) opens Glazed Menu's screens.
        net.ixdarklord.coolcatcore.api.config.client.ConfigScreens.setProvider(new CoreScreens());
        ConfigEvents.SYNCED.register(config -> {
            if (!(config instanceof ConfigImpl impl)) return;
            CoolCatConfig wrapped = CoolCatSource.INSTANCE.config(impl);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof ConfigScreen screen) screen.onConfigSynced(wrapped);
            if (minecraft.screen instanceof CategoryPopup popup) popup.onConfigSynced(wrapped);
        });
    }

    // A mod's theme set with Core's API, when it set one.
    private static @Nullable ConfigTheme theme(String modId) {
        net.ixdarklord.coolcatcore.api.config.ConfigTheme theme = net.ixdarklord.coolcatcore.api.config.ConfigTheme.forMod(modId);
        return theme == net.ixdarklord.coolcatcore.api.config.ConfigTheme.DEFAULT ? null : CoolCatTypes.theme(theme);
    }

    // Core's screen API, answered with Glazed Menu's screens for Core's configs.
    private static final class CoreScreens implements net.ixdarklord.coolcatcore.api.config.client.ConfigScreens.Provider {
        @Override
        public @Nullable Screen create(@Nullable Screen parent, String modId) {
            return net.ixdarklord.glazedmenu.api.ConfigScreens.create(parent, modId);
        }

        @Override
        public @Nullable Screen create(@Nullable Screen parent, net.ixdarklord.coolcatcore.api.config.Config config) {
            CoolCatConfig wrapped = wrap(config);
            return wrapped != null ? net.ixdarklord.glazedmenu.api.ConfigScreens.create(parent, wrapped)
                    : net.ixdarklord.glazedmenu.api.ConfigScreens.create(parent, config.id());
        }

        @Override
        public @Nullable Screen categoryPopup(@Nullable Screen parent, net.ixdarklord.coolcatcore.api.config.Config config, String path,
                                              net.ixdarklord.coolcatcore.api.config.@Nullable ConfigTheme theme) {
            CoolCatConfig wrapped = wrap(config);
            if (wrapped == null) return net.ixdarklord.glazedmenu.api.ConfigScreens.categoryPopup(parent, config.id(), path);
            return theme != null ? net.ixdarklord.glazedmenu.api.ConfigScreens.categoryPopup(parent, wrapped, path, convert(theme))
                    : net.ixdarklord.glazedmenu.api.ConfigScreens.categoryPopup(parent, wrapped, path);
        }

        @Override
        public @Nullable Screen categoryPopup(@Nullable Screen parent, String modId, String path) {
            return net.ixdarklord.glazedmenu.api.ConfigScreens.categoryPopup(parent, modId, path);
        }

        @Override
        public Screen createModList(@Nullable Screen parent) {
            return net.ixdarklord.glazedmenu.api.ConfigScreens.createModList(parent);
        }

        @Override
        public Screen colorPicker(@Nullable Screen parent, Component title, int color, boolean alpha, java.util.function.IntConsumer onDone) {
            return net.ixdarklord.glazedmenu.api.ConfigScreens.colorPicker(parent, title, color, alpha, onDone);
        }

        @Override
        public boolean hasConfigs(String modId) {
            return net.ixdarklord.glazedmenu.api.ConfigScreens.hasConfigs(modId);
        }

        private static @Nullable CoolCatConfig wrap(net.ixdarklord.coolcatcore.api.config.Config config) {
            return config instanceof ConfigImpl impl ? CoolCatSource.INSTANCE.config(impl) : null;
        }
    }

    /** A Core theme as Glazed Menu's, for screens about Core's configs. */
    public static ConfigTheme convert(net.ixdarklord.coolcatcore.api.config.ConfigTheme theme) {
        return CoolCatTypes.theme(theme);
    }
}
