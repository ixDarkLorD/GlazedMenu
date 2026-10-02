package net.ixdarklord.glazedmenu.internal.source.ftb;

import dev.ftb.mods.ftblibrary.config.manager.ConfigManager;
import dev.ftb.mods.ftblibrary.config.manager.ConfigManagerClient;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.net.SyncConfigToServerPacket;
import dev.ftb.mods.ftblibrary.snbt.config.BaseValue;
import dev.ftb.mods.ftblibrary.snbt.config.BooleanValue;
import dev.ftb.mods.ftblibrary.snbt.config.EnumValue;
import dev.ftb.mods.ftblibrary.snbt.config.NumberValue;
import dev.ftb.mods.ftblibrary.snbt.config.SNBTConfig;
import dev.ftb.mods.ftblibrary.snbt.config.StringListValue;
import dev.ftb.mods.ftblibrary.snbt.config.StringValue;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.core.Names;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.internal.source.Values;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalSource;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalTypes;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * FTB Library's configs (FTB mods'): the ones FTB's own editor offers, which its manager tracks on this client (each
 * mod's client configs, and the server configs it syncs; not startup configs), their groups and values, with FTB's
 * ranges and choices. Server configs are edited as FTB's own editor does: saved here in single player, sent to the
 * server (which checks the player may) otherwise.
 */
public final class FtbSource extends ExternalSource {
    public static final FtbSource INSTANCE = new FtbSource();
    private static final java.util.Set<String> TEST_CONFIGS = java.util.Set.of("ftblibrary-server");
    // The FTB config of each of Glazed Menu's configs, and whether it's a server's.
    private final Map<ExternalConfig, SNBTConfig> ftbConfigs = new HashMap<>();
    private final Map<ExternalConfig, Boolean> serverConfigs = new HashMap<>();

    private FtbSource() {}

    @Override
    public String name() {
        return "FTB Library";
    }

    @Override
    protected List<ExternalConfig> discover() {
        List<ExternalConfig> configs = new ArrayList<>();
        for (Map.Entry<String, Object> tracked : tracked().entrySet()) {
            try {
                Object entry = tracked.getValue();
                SNBTConfig config = (SNBTConfig) component(entry, "config");
                String type = String.valueOf(component(entry, "configType"));
                // Startup configs (tracked as servers', named "<mod>-startup") are read once as the game starts, and FTB
                // offers no editor for them; nor does Glazed Menu.
                if (type.equals("STARTUP") || tracked.getKey().endsWith("-startup")) continue;
                // FTB Library's own server config is a test one, registered only in development.
                if (TEST_CONFIGS.contains(tracked.getKey())) continue;
                boolean server = type.equals("SERVER");
                ExternalConfig built = build(tracked.getKey(), config, (String) component(entry, "groupPrefix"), server, (Path) component(entry, "loadedFrom"));
                this.ftbConfigs.put(built, config);
                this.serverConfigs.put(built, server);
                configs.add(built);
            } catch (ReflectiveOperationException | RuntimeException e) {
                GlazedMenu.LOGGER.warn("Couldn't read FTB Library's config {}", tracked.getKey(), e);
            }
        }
        return configs;
    }

    // Edits apply here, then go where the config lives (a server's are sent to it, as FTB's editor sends them).
    @Override
    public void apply(net.ixdarklord.glazedmenu.api.config.Config config, Map<ConfigValue<?>, Object> values) {
        Access access = this.access(config);
        if (access != Access.LOCAL && access != Access.REMOTE) return;
        values.forEach(Values::set);
        config.save();
    }

    // --- FTB's editor ---

    /** Whether FTB's editor should open for this config key: the player didn't replace it, or Glazed Menu doesn't have the config. */
    public static boolean useOwnEditor(String key) {
        return !GlazedSettings.replaceFtbLibraryScreens() || INSTANCE.byKey(key) == null;
    }

    /** Glazed Menu's screen for an FTB config, opened where FTB would open its editor. */
    public static @Nullable Screen screenFor(String key, @Nullable Screen parent) {
        ExternalConfig config = INSTANCE.byKey(key);
        return config != null ? net.ixdarklord.glazedmenu.api.ConfigScreens.create(parent, config) : null;
    }

    /**
     * FTB's own editor for a mod's settings, unless the player chose to replace it: its first client config (FTB's editor shows
     * one at a time), opened over the current screen. Null when it doesn't apply.
     */
    public static @Nullable Screen ownScreen(String modId) {
        if (GlazedSettings.replaceFtbLibraryScreens()) return null;
        for (ExternalConfig config : INSTANCE.configs()) {
            if (!config.modId().equals(modId) || INSTANCE.serverConfigs.getOrDefault(config, false)) continue;
            ConfigManagerClient.editConfig(INSTANCE.ftbConfigs.get(config).getKey());
            return Minecraft.getInstance().screen;
        }
        return null;
    }

    private @Nullable ExternalConfig byKey(String key) {
        for (ExternalConfig config : this.configs()) {
            if (this.ftbConfigs.get(config).getKey().equals(key)) return config;
        }
        return null;
    }

    // --- Reading FTB's configs ---

    private static ExternalConfig build(String key, SNBTConfig config, String groupPrefix, boolean server, @Nullable Path path) {
        // Keys are "<mod>-<name>" ("ftblibrary-client").
        int dash = key.lastIndexOf('-');
        String modId = dash > 0 && GlazedPlatform.get().isModLoaded(key.substring(0, dash)) ? key.substring(0, dash) : modOf(groupPrefix);
        String name = dash > 0 ? key.substring(dash + 1) : key;
        ExternalConfigBuilder builder = new ExternalConfigBuilder(modId, name, server ? ConfigScope.SERVER : ConfigScope.CLIENT)
                .title(Component.translatableWithFallback(groupPrefix, Names.modName(modId) + " " + Names.prettify(name)))
                .file(path != null ? path.getFileName().toString() : key + ".json5", path);
        addChildren(builder, config, groupPrefix);
        return builder.build(() -> access(key, server), () -> save(config, server));
    }

    // The mod a config's translation prefix names ("ftblibrary.client_settings").
    private static String modOf(String groupPrefix) {
        int dot = groupPrefix.indexOf('.');
        String first = dot > 0 ? groupPrefix.substring(0, dot) : groupPrefix;
        return GlazedPlatform.get().isModLoaded(first) ? first : "ftblibrary";
    }

    private static void addChildren(ExternalConfigBuilder builder, SNBTConfig group, String path) {
        List<BaseValue<?>> children;
        try {
            @SuppressWarnings("unchecked")
            List<BaseValue<?>> list = (List<BaseValue<?>>) field(BaseValue.class, "defaultValue").get(group);
            children = new ArrayList<>(list);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        children.removeIf(FtbSource::excluded);
        children.sort(Comparator.comparingInt(FtbSource::displayOrder));
        for (BaseValue<?> child : children) {
            String key = path + "." + child.getKey();
            if (child instanceof SNBTConfig subgroup) {
                builder.push(child.getKey(), key, key + ".tooltip", comment(child));
                addChildren(builder, subgroup, key);
                builder.pop();
            } else {
                try {
                    addValue(builder, child, key);
                } catch (RuntimeException e) {
                    GlazedMenu.LOGGER.debug("Skipped FTB Library value {}", key, e);
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addValue(ExternalConfigBuilder builder, BaseValue<?> value, String key) {
        ExternalConfigBuilder.Value<?> entry;
        if (value instanceof EnumValue<?> enumValue) {
            entry = choice(builder, enumValue);
        } else {
            Object defaultValue = defaultOf(value);
            ConfigType type = switch (value) {
                case BooleanValue ignored -> ConfigTypes.BOOLEAN;
                case StringValue ignored -> ConfigTypes.STRING;
                case StringListValue ignored -> ConfigTypes.listOf(ConfigTypes.STRING);
                case NumberValue<?> number -> ExternalTypes.of(defaultValue.getClass(), defaultValue, bound(number, "minValue"), bound(number, "maxValue"));
                default -> null;
            };
            if (type == null || type.validate(defaultValue).isError()) return;
            BaseValue raw = value;
            entry = builder.value(value.getKey(), type, defaultValue, new ExternalValue.Binding<>() {
                @Override
                public Object get() {
                    return raw.get();
                }

                @Override
                public void set(Object newValue) {
                    raw.set(newValue instanceof List<?> list ? new ArrayList<>(list) : newValue);
                }
            });
        }
        entry.translation(key, key + ".tooltip").comment(comment(value)).add();
    }

    // FTB's choices: a Java enum's constants, or else their names as text limited to those names.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ExternalConfigBuilder.Value<?> choice(ExternalConfigBuilder builder, EnumValue<T> value) {
        NameMap<T> names = nameMap(value);
        T defaultValue = (T) defaultOf(value);
        if (defaultValue instanceof Enum<?> constant) {
            return builder.value(value.getKey(), (ConfigType) ConfigTypes.enumOf(constant.getDeclaringClass()), defaultValue, new ExternalValue.Binding<T>() {
                @Override
                public T get() {
                    return value.get();
                }

                @Override
                public void set(T newValue) {
                    value.set(newValue);
                }
            });
        }
        String pattern = names.keys.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        return builder.value(value.getKey(), ConfigTypes.pattern(pattern), names.getName(defaultValue), new ExternalValue.Binding<String>() {
            @Override
            public String get() {
                return names.getName(value.get());
            }

            @Override
            public void set(String name) {
                value.set(names.get(name));
            }
        });
    }

    private static Access access(String key, boolean server) {
        if (!server) return Access.LOCAL;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return Access.UNAVAILABLE;
        return minecraft.isLocalServer() ? Access.LOCAL : Access.REMOTE;
    }

    // Saves as FTB's editor does: a client config to its file, a server's on the server.
    private static void save(SNBTConfig config, boolean server) {
        var singleplayer = Minecraft.getInstance().getSingleplayerServer();
        if (server && singleplayer != null) {
            // In single player the server shares these values: it saves them on its own thread and runs the mod's
            // change hook, without the permission check (and cheats) FTB's packet would need.
            String key = config.getKey();
            singleplayer.execute(() -> {
                ConfigManager.getInstance().save(key);
                onServerEdited(key);
            });
        } else if (server) {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) connection.send(new ServerboundCustomPayloadPacket(SyncConfigToServerPacket.create(config)));
        } else {
            ConfigManager.getInstance().editedOnClient(config.getKey());
            ConfigManager.getInstance().save(config.getKey());
        }
    }

    // --- FTB's internals ---

    // Runs the mod's hook for its server config changing, as FTB does when a client's edits arrive.
    private static void onServerEdited(String key) {
        try {
            Object tracked = tracked().get(key);
            if (tracked == null) return;
            Object hook = component(tracked, "onEdited");
            hook.getClass().getMethod("accept", boolean.class).invoke(hook, true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            GlazedMenu.LOGGER.debug("Couldn't run the change hook of FTB config {}", key, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tracked() {
        try {
            return new HashMap<>((Map<String, Object>) field(ConfigManager.class, "trackedConfigs").get(ConfigManager.getInstance()));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FTB Library's config manager changed", e);
        }
    }

    private static Object component(Object record, String name) throws ReflectiveOperationException {
        Method accessor = record.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    // FTB Library 2101 keeps an enum value's names private.
    @SuppressWarnings("unchecked")
    private static <T> NameMap<T> nameMap(EnumValue<T> value) {
        try {
            return (NameMap<T>) field(EnumValue.class, "nameMap").get(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object defaultOf(BaseValue<?> value) {
        try {
            return field(BaseValue.class, "defaultValue").get(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static @Nullable Number bound(NumberValue<?> value, String name) {
        try {
            return (Number) field(NumberValue.class, name).get(value);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean excluded(BaseValue<?> value) {
        try {
            return field(BaseValue.class, "excluded").getBoolean(value);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static int displayOrder(BaseValue<?> value) {
        try {
            return field(BaseValue.class, "displayOrder").getInt(value);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> comment(BaseValue<?> value) {
        try {
            return List.copyOf((List<String>) field(BaseValue.class, "comment").get(value));
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }
}
