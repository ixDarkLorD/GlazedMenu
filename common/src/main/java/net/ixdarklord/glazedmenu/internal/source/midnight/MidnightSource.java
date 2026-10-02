package net.ixdarklord.glazedmenu.internal.source.midnight;

import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.core.Names;
import net.ixdarklord.glazedmenu.internal.source.Access;
import eu.midnightdust.lib.config.MidnightConfig;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalSource;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalTypes;
import net.ixdarklord.glazedmenu.internal.source.external.FieldBinding;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MidnightLib's configs: every config class a mod set up with {@code MidnightConfig.init}, its {@code @Entry} fields
 * by category, with MidnightLib's bounds, sliders and colors.
 */
public final class MidnightSource extends ExternalSource {
    public static final MidnightSource INSTANCE = new MidnightSource();
    // MidnightLib's colors are text: "#RRGGBB".
    private static final String COLOR_PATTERN = "#[0-9a-fA-F]{6}";

    private MidnightSource() {}

    @Override
    public String name() {
        return "MidnightLib";
    }

    @Override
    protected List<ExternalConfig> discover() {
        List<ExternalConfig> configs = new ArrayList<>();
        new LinkedHashMap<>(MidnightConfig.configInstances).forEach((modId, instance) -> {
            try {
                configs.add(build(modId, instance));
            } catch (RuntimeException e) {
                GlazedMenu.LOGGER.warn("Couldn't read MidnightLib's config of {}", modId, e);
            }
        });
        return configs;
    }

    private static ExternalConfig build(String modId, MidnightConfig instance) {
        Class<?> type = instance.configClass != null ? instance.configClass : instance.getClass();
        String prefix = modId + ".midnightconfig.";
        ExternalConfigBuilder builder = new ExternalConfigBuilder(modId, "config", ConfigScope.CLIENT)
                .title(Component.translatableWithFallback(prefix + "title", Names.modName(modId)))
                .file(modId + ".json", instance.getJsonFilePath());

        // Entries by category, in the order their categories first appear; one category is shown without a group.
        Map<String, List<Field>> categories = new LinkedHashMap<>();
        for (Field field : FieldBinding.configFields(type, true)) {
            MidnightConfig.Entry entry = field.getAnnotation(MidnightConfig.Entry.class);
            if (entry == null || field.isAnnotationPresent(MidnightConfig.Hidden.class)) continue;
            if (!entry.requiredMod().isEmpty() && !GlazedPlatform.get().isModLoaded(entry.requiredMod())) continue;
            categories.computeIfAbsent(entry.category(), key -> new ArrayList<>()).add(field);
        }
        boolean grouped = categories.size() > 1;
        categories.forEach((category, fields) -> {
            if (grouped) builder.push(category, prefix + "category." + category, null, List.of());
            for (Field field : fields) addValue(builder, modId, prefix, field);
            if (grouped) builder.pop();
        });
        return builder.build(() -> Access.LOCAL, () -> MidnightConfig.write(modId));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addValue(ExternalConfigBuilder builder, String modId, String prefix, Field field) {
        MidnightConfig.Entry entry = field.getAnnotation(MidnightConfig.Entry.class);
        Object defaultValue = MidnightConfig.getDefaultValue(modId, field.getName());
        if (defaultValue == null) return;
        Class<?> valueClass = ExternalTypes.box(field.getType());
        ConfigType type;
        if (entry.isColor() && valueClass == String.class) {
            type = ConfigTypes.pattern(COLOR_PATTERN);
        } else {
            // Unset bounds are MidnightLib's defaults.
            Double min = entry.min() != Double.MIN_NORMAL ? entry.min() : null;
            Double max = entry.max() != Double.MAX_VALUE ? entry.max() : null;
            type = ExternalTypes.of(field.getType(), defaultValue, min, max, entry.isSlider());
        }
        if (type == null || type.validate(defaultValue).isError()) return;
        String key = prefix + field.getName();
        builder.value(field.getName(), type, defaultValue, FieldBinding.of(field, () -> null))
                .translation(key, key + ".tooltip")
                .name(entry.name().isEmpty() ? null : entry.name())
                .add();
    }
}
