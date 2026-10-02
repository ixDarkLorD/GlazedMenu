package net.ixdarklord.glazedmenu.internal.source.cloth;

import net.ixdarklord.glazedmenu.internal.core.Names;
import net.ixdarklord.glazedmenu.internal.source.Access;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalSource;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalTypes;
import net.ixdarklord.glazedmenu.internal.source.external.FieldBinding;
import net.ixdarklord.glazedmenu.internal.source.external.ModIds;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cloth Config's AutoConfig: every config class registered with {@code AutoConfig.register}, its fields read as
 * AutoConfig's own screen reads them (categories, collapsible and transitive objects, bounds, color pickers, tooltips).
 */
public final class ClothSource extends ExternalSource {
    public static final ClothSource INSTANCE = new ClothSource();
    private static final String DEFAULT_CATEGORY = "default";

    private ClothSource() {}

    @Override
    public String name() {
        return "Cloth Config";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<ExternalConfig> discover() {
        Map<Class<? extends ConfigData>, ConfigHolder<?>> holders;
        try {
            Field field = AutoConfig.class.getDeclaredField("holders");
            field.setAccessible(true);
            holders = new LinkedHashMap<>((Map<Class<? extends ConfigData>, ConfigHolder<?>>) field.get(null));
        } catch (ReflectiveOperationException e) {
            GlazedMenu.LOGGER.warn("Couldn't find Cloth Config's configs", e);
            return List.of();
        }
        List<ExternalConfig> configs = new ArrayList<>();
        holders.forEach((type, holder) -> {
            // Cloth Config's own example config (registered in development) isn't a mod's.
            if (type.getName().startsWith("me.shedaniel.autoconfig.example.")) return;
            try {
                ExternalConfig config = build(type, holder);
                if (config != null) configs.add(config);
            } catch (RuntimeException | ReflectiveOperationException e) {
                GlazedMenu.LOGGER.warn("Couldn't read Cloth Config's config {}", type.getName(), e);
            }
        });
        return configs;
    }

    private static @Nullable ExternalConfig build(Class<?> type, ConfigHolder<?> holder) throws ReflectiveOperationException {
        Config annotation = type.getAnnotation(Config.class);
        if (annotation == null) return null;
        String name = annotation.name();
        String prefix = "text.autoconfig." + name;
        Object defaults = type.getDeclaredConstructor().newInstance();
        ExternalConfigBuilder builder = new ExternalConfigBuilder(ModIds.of(type, name), name, ConfigScope.CLIENT)
                .title(Component.translatableWithFallback(prefix + ".title", Names.prettify(name)));

        // Top-level fields by category, in the order their categories first appear; the default one at the root.
        Map<String, List<Field>> categories = new LinkedHashMap<>();
        categories.put(DEFAULT_CATEGORY, new ArrayList<>());
        for (Field field : FieldBinding.configFields(type, false)) {
            ConfigEntry.Category category = field.getAnnotation(ConfigEntry.Category.class);
            categories.computeIfAbsent(category != null ? category.value() : DEFAULT_CATEGORY, key -> new ArrayList<>()).add(field);
        }
        Supplier<Object> instance = holder::getConfig;
        categories.forEach((category, fields) -> {
            boolean grouped = !category.equals(DEFAULT_CATEGORY);
            if (grouped) builder.push(category, prefix + ".category." + category, null, List.of());
            addFields(builder, fields, instance, defaults, prefix + ".option.");
            if (grouped) builder.pop();
        });
        return builder.build(() -> Access.LOCAL, holder::save);
    }

    private static void addFields(ExternalConfigBuilder builder, List<Field> fields, Supplier<@Nullable Object> instance, @Nullable Object defaults, String keyPrefix) {
        for (Field field : fields) {
            if (field.isAnnotationPresent(ConfigEntry.Gui.Excluded.class)) continue;
            field.setAccessible(true);
            Object defaultValue = read(field, defaults);
            String key = keyPrefix + field.getName();
            boolean transitive = field.isAnnotationPresent(ConfigEntry.Gui.TransitiveObject.class);
            if (transitive || field.isAnnotationPresent(ConfigEntry.Gui.CollapsibleObject.class)) {
                Supplier<@Nullable Object> nested = () -> read(field, instance.get());
                List<Field> nestedFields = FieldBinding.configFields(field.getType(), false);
                if (transitive) {
                    addFields(builder, nestedFields, nested, defaultValue, key + ".");
                } else {
                    builder.push(field.getName(), key, key + ".@Tooltip", comment(field));
                    addFields(builder, nestedFields, nested, defaultValue, key + ".");
                    builder.pop();
                }
                continue;
            }
            if (defaultValue == null) continue;
            addValue(builder, field, instance, defaultValue, key);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addValue(ExternalConfigBuilder builder, Field field, Supplier<@Nullable Object> instance, Object defaultValue, String key) {
        ConfigType type;
        ConfigEntry.ColorPicker color = field.getAnnotation(ConfigEntry.ColorPicker.class);
        ConfigEntry.BoundedDiscrete bounds = field.getAnnotation(ConfigEntry.BoundedDiscrete.class);
        if (color != null && ExternalTypes.box(field.getType()) == Integer.class) {
            type = ExternalTypes.color(color.allowAlpha());
        } else if (bounds != null) {
            type = ExternalTypes.of(field.getType(), defaultValue, bounds.min(), bounds.max(), true);
        } else {
            type = ExternalTypes.of(field.getType(), defaultValue, null, null);
        }
        if (type == null || type.validate(defaultValue).isError()) return;
        ConfigEntry.Gui.RequiresRestart restart = field.getAnnotation(ConfigEntry.Gui.RequiresRestart.class);
        builder.value(field.getName(), type, defaultValue, FieldBinding.of(field, instance))
                .translation(key, field.isAnnotationPresent(ConfigEntry.Gui.Tooltip.class) ? key + ".@Tooltip" : null)
                .comment(comment(field))
                .restart(restart != null && restart.value() ? RestartRequirement.GAME : RestartRequirement.NONE)
                .add();
    }

    private static List<String> comment(Field field) {
        Comment comment = field.getAnnotation(Comment.class);
        return comment == null || comment.value().isBlank() ? List.of() : comment.value().lines().map(String::strip).toList();
    }

    private static @Nullable Object read(Field field, @Nullable Object holder) {
        if (holder == null) return null;
        try {
            field.setAccessible(true);
            return field.get(holder);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
