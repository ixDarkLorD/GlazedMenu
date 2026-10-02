package net.ixdarklord.glazedmenu.internal.source.spec;

import net.ixdarklord.glazedmenu.internal.core.Names;
import net.ixdarklord.glazedmenu.internal.source.Access;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalSource;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalTypes;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalValue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * NeoForge's config system: every {@link ModConfig} NeoForge (or, on Fabric, Forge Config API Port) tracks, with a
 * {@code ModConfigSpec}, or on Fabric a Forge {@code ForgeConfigSpec} (through a reader the Fabric module adds).
 */
public final class ModConfigSource extends ExternalSource {
    public static final ModConfigSource INSTANCE = new ModConfigSource();
    // Turns a ModConfig's spec into a view, or null for specs of another kind.
    private final List<Function<IConfigSpec, @Nullable SpecView>> readers = new CopyOnWriteArrayList<>(List.of(NeoForgeSpecView::of));

    private ModConfigSource() {}

    /** Reads another kind of spec, as Forge Config API Port wraps Forge's. */
    public void addReader(Function<IConfigSpec, @Nullable SpecView> reader) {
        this.readers.add(reader);
    }

    @Override
    public String name() {
        return "NeoForge configs";
    }

    @Override
    protected List<ExternalConfig> discover() {
        List<ModConfig> modConfigs = new ArrayList<>(ModConfigs.getFileMap().values());
        modConfigs.sort(Comparator.comparing(ModConfig::getModId).thenComparing(config -> config.getType().ordinal()));
        List<ExternalConfig> configs = new ArrayList<>();
        for (ModConfig modConfig : modConfigs) {
            SpecView view = this.view(modConfig.getSpec());
            if (view == null) continue;
            try {
                ExternalConfig config = build(modConfig, view);
                if (config.values().findAny().isPresent()) configs.add(config);
            } catch (RuntimeException e) {
                GlazedMenu.LOGGER.warn("Couldn't read config {} of {}", modConfig.getFileName(), modConfig.getModId(), e);
            }
        }
        return configs;
    }

    private @Nullable SpecView view(IConfigSpec spec) {
        for (Function<IConfigSpec, @Nullable SpecView> reader : this.readers) {
            SpecView view = reader.apply(spec);
            if (view != null) return view;
        }
        return null;
    }

    private static ExternalConfig build(ModConfig modConfig, SpecView view) {
        String fileName = modConfig.getFileName();
        // By name: NeoForge 26.3 renamed COMMON to LOCAL and SERVER to SYNCED, while the API compiled against keeps the old.
        ConfigScope scope = switch (modConfig.getType().name()) {
            case "CLIENT" -> ConfigScope.CLIENT;
            case "SERVER", "SYNCED" -> ConfigScope.WORLD;
            case "STARTUP" -> ConfigScope.STARTUP;
            default -> ConfigScope.COMMON;
        };
        ExternalConfigBuilder builder = new ExternalConfigBuilder(modConfig.getModId(), configName(modConfig), scope)
                .file(fileName, fullPath(modConfig))
                .title(Component.translatableWithFallback(modConfig.getModId() + ".configuration.title",
                        modName(modConfig.getModId()) + " " + typeName(modConfig.getType())));
        addGroup(builder, view, view.values(), new ArrayList<>());
        return builder.build(() -> access(modConfig, view), view::save);
    }

    private static void addGroup(ExternalConfigBuilder builder, SpecView view, UnmodifiableConfig values, List<String> path) {
        for (Map.Entry<String, Object> entry : values.valueMap().entrySet()) {
            List<String> childPath = new ArrayList<>(path);
            childPath.add(entry.getKey());
            if (entry.getValue() instanceof UnmodifiableConfig group) {
                String translationKey = view.groupTranslationKey(childPath);
                String comment = view.groupComment(childPath);
                builder.push(entry.getKey(), translationKey, translationKey == null ? null : translationKey + ".tooltip", lines(comment));
                addGroup(builder, view, group, childPath);
                builder.pop();
                continue;
            }
            SpecView.ValueView value = view.value(entry.getValue());
            if (value != null) addValue(builder, entry.getKey(), value);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addValue(ExternalConfigBuilder builder, String key, SpecView.ValueView value) {
        Object defaultValue = value.defaultValue();
        if (defaultValue == null) return;
        Class<?> valueClass = value.valueClass() != null && value.valueClass() != Object.class ? value.valueClass() : defaultValue.getClass();
        ConfigType type = ExternalTypes.of(valueClass, defaultValue, value.min(), value.max());
        if (type == null || type.validate(defaultValue).isError()) return;
        String translationKey = value.translationKey();
        builder.value(key, type, defaultValue, new ExternalValue.Binding() {
                    @Override
                    public Object get() {
                        return value.get();
                    }

                    @Override
                    public void set(Object newValue) {
                        value.set(newValue);
                    }
                })
                .translation(translationKey, translationKey == null ? null : translationKey + ".tooltip")
                .comment(lines(value.comment()))
                .restart(value.restart())
                .check(value::test)
                .add();
    }

    // Edited here, except a server config: only in a world, and only the host's (players see a server's values).
    private static Access access(ModConfig modConfig, SpecView view) {
        if (!isSynced(modConfig)) return Access.LOCAL;
        if (!view.isLoaded() || modConfig.getLoadedConfig() == null) return Access.UNAVAILABLE;
        return Minecraft.getInstance().isLocalServer() ? Access.LOCAL : Access.READ_ONLY;
    }

    // "mymod-client.toml" -> "client"; files in a folder keep their folder.
    private static String configName(ModConfig modConfig) {
        String name = modConfig.getFileName().replace('\\', '/');
        int dot = name.lastIndexOf('.');
        if (dot > name.lastIndexOf('/')) name = name.substring(0, dot);
        String prefix = modConfig.getModId() + "-";
        int slash = name.lastIndexOf('/');
        String file = name.substring(slash + 1);
        if (file.startsWith(prefix) && file.length() > prefix.length()) file = file.substring(prefix.length());
        return slash < 0 ? file : name.substring(0, slash + 1) + file;
    }

    private static @Nullable Path fullPath(ModConfig modConfig) {
        try {
            return modConfig.getFullPath();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // A server's config, sent to its players: SERVER, or SYNCED from NeoForge 26.3.
    private static boolean isSynced(ModConfig modConfig) {
        String type = modConfig.getType().name();
        return type.equals("SERVER") || type.equals("SYNCED");
    }

    private static String typeName(ModConfig.Type type) {
        String name = type.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String modName(String modId) {
        return Names.modName(modId);
    }

    static List<String> lines(@Nullable String comment) {
        if (comment == null || comment.isBlank()) return List.of();
        return Arrays.stream(comment.split("\\R")).map(String::strip).filter(line -> !line.isEmpty()).toList();
    }
}
