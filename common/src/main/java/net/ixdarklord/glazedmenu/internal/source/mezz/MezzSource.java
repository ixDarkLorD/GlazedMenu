package net.ixdarklord.glazedmenu.internal.source.mezz;

import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.Names;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalSource;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalTypes;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalValue;
import net.mezzdev.config.api.Configs;
import net.mezzdev.config.api.schema.ConfigSchemaType;
import net.mezzdev.config.api.schema.IConfigSchema;
import net.mezzdev.config.api.schema.category.IConfigCategory;
import net.mezzdev.config.api.value.IConfigValue;
import net.mezzdev.config.api.value.color.ConfigColorFormat;
import net.mezzdev.config.api.value.color.PackedColor;
import net.mezzdev.config.api.value.editor.IConfigValueEditorInfo;
import net.mezzdev.config.api.value.serializer.ConfigValueRange;
import net.mezzdev.config.api.value.serializer.IConfigListValueSerializer;
import net.mezzdev.config.api.value.serializer.IConfigValueSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MezzConfig's configs (JEI's, and any other mod using it): every schema, its categories and their values, with
 * MezzConfig's ranges, choices and colors. A value of a type Glazed Menu has no editor for is edited as text, through
 * MezzConfig's own serializer.
 */
public final class MezzSource extends ExternalSource {
    public static final MezzSource INSTANCE = new MezzSource();

    private MezzSource() {}

    @Override
    public String name() {
        return "MezzConfig";
    }

    @Override
    protected List<ExternalConfig> discover() {
        List<ExternalConfig> configs = new ArrayList<>();
        for (IConfigSchema schema : Configs.getSchemas()) {
            try {
                configs.add(build(schema));
            } catch (RuntimeException e) {
                GlazedMenu.LOGGER.warn("Couldn't read MezzConfig's config {} of {}", schema.getId(), schema.getModId(), e);
            }
        }
        return configs;
    }

    private static ExternalConfig build(IConfigSchema schema) {
        String modId = schema.getModId();
        ConfigScope scope = schema.getType() == ConfigSchemaType.SERVER ? ConfigScope.SERVER : ConfigScope.CLIENT;
        Optional<Path> path = schema.getPath();
        // Schemas are named after their files ("jei/client.ini"); the config's name is the file's, without its type or mod.
        String name = schema.getId().substring(schema.getId().lastIndexOf('/') + 1);
        if (name.lastIndexOf('.') > 0) name = name.substring(0, name.lastIndexOf('.'));
        if (name.startsWith(modId + "-") || name.startsWith(modId + "_")) name = name.substring(modId.length() + 1);
        ExternalConfigBuilder builder = new ExternalConfigBuilder(modId, name, scope)
                .title(Component.literal(Names.modName(modId) + " " + Names.prettify(name)))
                .file(path.map(file -> file.getFileName().toString()).orElse(schema.getId()), path.orElse(null));
        for (IConfigCategory category : schema.getCategories()) {
            builder.push(category.getName(), category.getLocalizationKey(), null, List.of());
            for (IConfigValue<?> value : category.getConfigValues()) {
                try {
                    addValue(builder, value);
                } catch (RuntimeException e) {
                    GlazedMenu.LOGGER.debug("Skipped MezzConfig value {} of {}", value.getEditorInfo().getName(), schema.getId(), e);
                }
            }
            builder.pop();
        }
        // MezzConfig saves its files itself when values are set.
        return builder.build(() -> access(schema), () -> {});
    }

    private static Access access(IConfigSchema schema) {
        if (!schema.isActive()) return Access.UNAVAILABLE;
        if (schema.getType() != ConfigSchemaType.SERVER) return Access.LOCAL;
        // A server's config: editable in single player, the server's values otherwise.
        return Minecraft.getInstance().isLocalServer() || Minecraft.getInstance().level == null ? Access.LOCAL : Access.READ_ONLY;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> void addValue(ExternalConfigBuilder builder, IConfigValue<T> value) {
        IConfigValueEditorInfo<T> info = value.getEditorInfo();
        T defaultValue = info.getDefaultValue();
        IConfigValueSerializer<T> serializer = info.getSerializer();
        ExternalConfigBuilder.Value<?> entry;
        if (defaultValue instanceof PackedColor color) {
            // Colors, as Glazed Menu's color type (an int) packed back into MezzConfig's.
            ConfigColorFormat format = color.format();
            IConfigValue<PackedColor> colorValue = (IConfigValue<PackedColor>) value;
            entry = builder.value(info.getName(), ExternalTypes.color(format == ConfigColorFormat.ARGB), color.packedValue(), new ExternalValue.Binding<>() {
                @Override
                public Integer get() {
                    return colorValue.getEditorInfo().getPendingValue().packedValue();
                }

                @Override
                public void set(Integer packed) {
                    colorValue.set(new PackedColor(packed, format));
                }
            });
        } else {
            ConfigType type = typeOf(defaultValue, serializer);
            if (type != null && !type.validate(defaultValue).isError()) {
                entry = builder.value(info.getName(), type, defaultValue, new ExternalValue.Binding<T>() {
                    @Override
                    public T get() {
                        return info.getPendingValue();
                    }

                    @Override
                    public void set(T newValue) {
                        value.set(newValue);
                    }
                }).check(object -> serializer.isValid((T) object));
            } else {
                // Anything else, as the text MezzConfig writes to its file.
                entry = builder.value(info.getName(), ConfigTypes.STRING, serializer.serialize(defaultValue), new ExternalValue.Binding<String>() {
                    @Override
                    public String get() {
                        return serializer.serialize(info.getPendingValue());
                    }

                    @Override
                    public void set(String text) {
                        serializer.deserialize(text).getResult().ifPresent(value::set);
                    }
                }).check(object -> object instanceof String text && serializer.deserialize(text).getDiagnostics().isEmpty());
            }
        }
        entry.translation(info.getLocalizationKey(), info.getLocalizationKey() + ".description")
                .restart(switch (info.getRestartRequirement()) {
                    case NONE -> RestartRequirement.NONE;
                    case WORLD_RESTART -> RestartRequirement.WORLD;
                    case GAME_RESTART -> RestartRequirement.GAME;
                })
                .add();
    }

    // Glazed Menu's type for a value, by its default: booleans, numbers (with MezzConfig's range), text, choices and lists.
    private static <T> @Nullable ConfigType<?> typeOf(T defaultValue, IConfigValueSerializer<T> serializer) {
        if (defaultValue instanceof List<?> list) {
            if (!(serializer instanceof IConfigListValueSerializer<?> listSerializer)) return null;
            Object sample = !list.isEmpty() ? list.get(0)
                    : listSerializer.getElementSerializer().getAllValidValues().filter(values -> !values.isEmpty()).map(validValues -> validValues.get(0)).orElse(null);
            if (sample == null) return null;
            ConfigType<?> element = ExternalTypes.of(sample.getClass(), null, null, null);
            return element != null && !(element instanceof net.ixdarklord.glazedmenu.api.config.type.ListType<?>) ? ConfigTypes.listOf(element) : null;
        }
        Optional<ConfigValueRange<T>> range = serializer.getRange();
        Number min = range.map(ConfigValueRange::min).filter(Number.class::isInstance).map(Number.class::cast).orElse(null);
        Number max = range.map(ConfigValueRange::max).filter(Number.class::isInstance).map(Number.class::cast).orElse(null);
        return ExternalTypes.of(defaultValue.getClass(), defaultValue, min, max);
    }
}
