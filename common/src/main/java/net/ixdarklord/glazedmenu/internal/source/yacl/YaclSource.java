package net.ixdarklord.glazedmenu.internal.source.yacl;

import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.core.Names;
import net.ixdarklord.glazedmenu.internal.source.Access;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.FieldAccess;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGenField;
import dev.isxander.yacl3.config.v2.api.autogen.ColorField;
import dev.isxander.yacl3.config.v2.api.autogen.CustomName;
import dev.isxander.yacl3.config.v2.api.autogen.DoubleField;
import dev.isxander.yacl3.config.v2.api.autogen.DoubleSlider;
import dev.isxander.yacl3.config.v2.api.autogen.FloatField;
import dev.isxander.yacl3.config.v2.api.autogen.FloatSlider;
import dev.isxander.yacl3.config.v2.api.autogen.IntField;
import dev.isxander.yacl3.config.v2.api.autogen.IntSlider;
import dev.isxander.yacl3.config.v2.api.autogen.LongField;
import dev.isxander.yacl3.config.v2.api.autogen.LongSlider;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalSource;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalTypes;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalValue;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * YACL's config classes ({@code ConfigClassHandler}): the fields YACL's own generated screen shows ({@code @AutoGen}),
 * by category and group, with its sliders, bounds and color fields.
 */
public final class YaclSource extends ExternalSource {
    public static final YaclSource INSTANCE = new YaclSource();

    private YaclSource() {}

    @Override
    public String name() {
        return "YACL";
    }

    @Override
    protected List<ExternalConfig> discover() {
        List<ExternalConfig> configs = new ArrayList<>();
        for (Object handler : YaclHandlers.all()) {
            if (!(handler instanceof ConfigClassHandler<?> config)) continue;
            // YACL's own internal settings aren't a config it offers players.
            if (config.configClass().getName().startsWith("dev.isxander.yacl3.")) continue;
            try {
                if (config.supportsAutoGen()) configs.add(build(config));
            } catch (RuntimeException e) {
                GlazedMenu.LOGGER.warn("Couldn't read YACL's config {}", config.id(), e);
            }
        }
        return configs;
    }

    private static ExternalConfig build(ConfigClassHandler<?> handler) {
        String id = handler.id().toString();
        String prefix = "yacl3.config." + id;
        ExternalConfigBuilder builder = new ExternalConfigBuilder(handler.id().getNamespace(), handler.id().getPath(), ConfigScope.CLIENT)
                .title(Component.translatableWithFallback(prefix + ".title", GlazedPlatform.get().modName(handler.id().getNamespace())
                        .orElseGet(() -> Names.prettify(handler.id().getNamespace()))));

        // Fields by category, then by group within it, each in the order it first appears.
        Map<String, Map<String, List<ConfigField<?>>>> categories = new LinkedHashMap<>();
        for (ConfigField<?> field : handler.fields()) {
            Optional<AutoGenField> autoGen = field.autoGen();
            if (autoGen.isEmpty()) continue;
            categories.computeIfAbsent(autoGen.get().category(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(autoGen.get().group().orElse(""), key -> new ArrayList<>()).add(field);
        }
        boolean tabs = categories.size() > 1;
        categories.forEach((category, groups) -> {
            String categoryKey = prefix + ".category." + category;
            if (tabs) builder.push(category, categoryKey, null, List.of());
            groups.forEach((group, fields) -> {
                if (!group.isEmpty()) builder.push(group, categoryKey + ".group." + group, null, List.of());
                for (ConfigField<?> field : fields) addValue(builder, prefix, field);
                if (!group.isEmpty()) builder.pop();
            });
            if (tabs) builder.pop();
        });
        return builder.build(() -> Access.LOCAL, handler::save);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addValue(ExternalConfigBuilder builder, String prefix, ConfigField<?> field) {
        FieldAccess access = field.access();
        Object defaultValue = field.defaultAccess().get();
        if (defaultValue == null) return;
        ConfigType type;
        ExternalValue.Binding binding = new ExternalValue.Binding() {
            @Override
            public Object get() {
                return access.get();
            }

            @Override
            public void set(Object value) {
                access.set(value);
            }
        };
        Optional<ColorField> color = access.getAnnotation(ColorField.class);
        if (color.isPresent() && defaultValue instanceof Color defaultColor) {
            boolean alpha = color.get().allowAlpha();
            type = ExternalTypes.color(alpha);
            defaultValue = defaultColor.getRGB() | (alpha ? 0 : 0xFF000000);
            binding = new ExternalValue.Binding<Integer>() {
                @Override
                public Integer get() {
                    return access.get() instanceof Color current ? current.getRGB() | (alpha ? 0 : 0xFF000000) : 0xFFFFFFFF;
                }

                @Override
                public void set(Integer value) {
                    access.set(new Color(value, alpha));
                }
            };
        } else {
            Number[] bounds = bounds(access);
            boolean slider = access.getAnnotation(IntSlider.class).isPresent() || access.getAnnotation(LongSlider.class).isPresent()
                    || access.getAnnotation(DoubleSlider.class).isPresent() || access.getAnnotation(FloatSlider.class).isPresent();
            type = ExternalTypes.of(access.typeClass(), defaultValue, bounds[0], bounds[1], slider);
        }
        if (type == null || type.validate(defaultValue).isError()) return;
        String key = prefix + "." + access.name();
        Optional<CustomName> name = access.getAnnotation(CustomName.class);
        builder.value(access.name(), type, defaultValue, binding)
                .translation(key, key + ".desc")
                .name(name.map(CustomName::value).orElse(null))
                .add();
    }

    // The bounds of whichever number annotation the field has.
    private static Number[] bounds(FieldAccess<?> access) {
        Optional<IntSlider> intSlider = access.getAnnotation(IntSlider.class);
        if (intSlider.isPresent()) return new Number[]{intSlider.get().min(), intSlider.get().max()};
        Optional<IntField> intField = access.getAnnotation(IntField.class);
        if (intField.isPresent()) return new Number[]{intField.get().min(), intField.get().max()};
        Optional<LongSlider> longSlider = access.getAnnotation(LongSlider.class);
        if (longSlider.isPresent()) return new Number[]{longSlider.get().min(), longSlider.get().max()};
        Optional<LongField> longField = access.getAnnotation(LongField.class);
        if (longField.isPresent()) return new Number[]{longField.get().min(), longField.get().max()};
        Optional<DoubleSlider> doubleSlider = access.getAnnotation(DoubleSlider.class);
        if (doubleSlider.isPresent()) return new Number[]{doubleSlider.get().min(), doubleSlider.get().max()};
        Optional<DoubleField> doubleField = access.getAnnotation(DoubleField.class);
        if (doubleField.isPresent()) return new Number[]{doubleField.get().min(), doubleField.get().max()};
        Optional<FloatSlider> floatSlider = access.getAnnotation(FloatSlider.class);
        if (floatSlider.isPresent()) return new Number[]{floatSlider.get().min(), floatSlider.get().max()};
        Optional<FloatField> floatField = access.getAnnotation(FloatField.class);
        if (floatField.isPresent()) return new Number[]{floatField.get().min(), floatField.get().max()};
        return new Number[]{null, null};
    }
}
