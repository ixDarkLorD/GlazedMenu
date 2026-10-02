package net.ixdarklord.glazedmenu.internal.integration.coolcat;

import com.mojang.serialization.Codec;
import net.ixdarklord.glazedmenu.api.config.ConfigColorScheme;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.api.config.type.NumberType;
import net.ixdarklord.glazedmenu.api.config.type.StringType;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * CoolCatLib: Core's types, results and themes as Glazed Menu's: the same values in the same Java classes, so a Core value
 * is edited with the editor its kind of type gets in Glazed Menu. Types Glazed Menu has no counterpart for (codec types, a mod's
 * own) keep Core's behavior and are edited as text.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class CoolCatTypes {
    private static final Map<net.ixdarklord.coolcatcore.api.config.type.ConfigType<?>, ConfigType<?>> TYPES =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<net.ixdarklord.coolcatcore.api.config.ConfigTheme, ConfigTheme> THEMES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private CoolCatTypes() {}

    static <T> ConfigType<T> type(net.ixdarklord.coolcatcore.api.config.type.ConfigType<T> type) {
        return (ConfigType<T>) TYPES.computeIfAbsent(type, CoolCatTypes::convert);
    }

    private static ConfigType<?> convert(net.ixdarklord.coolcatcore.api.config.type.ConfigType<?> type) {
        if (type instanceof net.ixdarklord.coolcatcore.api.config.type.BooleanType) return ConfigTypes.BOOLEAN;
        if (type instanceof net.ixdarklord.coolcatcore.api.config.type.NumberType<?> number) return number(number);
        if (type instanceof net.ixdarklord.coolcatcore.api.config.type.EnumType<?> enumType) return ConfigTypes.enumOf((Class) enumType.enumClass());
        if (type instanceof net.ixdarklord.coolcatcore.api.config.type.StringType string) {
            StringType converted = ConfigTypes.STRING.withMaxLength(string.maxLength()).withNotEmpty(string.isNotEmpty());
            return string.pattern() != null ? converted.withPattern(string.pattern().pattern()) : converted;
        }
        if (type instanceof net.ixdarklord.coolcatcore.api.config.type.ColorType color) return color.hasAlpha() ? ConfigTypes.COLOR_ALPHA : ConfigTypes.COLOR;
        if (type instanceof net.ixdarklord.coolcatcore.api.config.type.IdentifierType) return new Delegate<>((net.ixdarklord.coolcatcore.api.config.type.ConfigType) type);
        if (type instanceof net.ixdarklord.coolcatcore.api.config.type.ListType<?> list) {
            return ConfigTypes.listOf(type(list.elementType()), list.minSize(), list.maxSize());
        }
        return new Delegate<>((net.ixdarklord.coolcatcore.api.config.type.ConfigType) type);
    }

    private static NumberType<?> number(net.ixdarklord.coolcatcore.api.config.type.NumberType<?> number) {
        net.ixdarklord.coolcatcore.api.config.type.NumberType.Kind<?> kind = number.kind();
        NumberType converted;
        if (kind == net.ixdarklord.coolcatcore.api.config.type.NumberType.INT) converted = ConfigTypes.INT;
        else if (kind == net.ixdarklord.coolcatcore.api.config.type.NumberType.LONG) converted = ConfigTypes.LONG;
        else if (kind == net.ixdarklord.coolcatcore.api.config.type.NumberType.FLOAT) converted = ConfigTypes.FLOAT;
        else converted = ConfigTypes.DOUBLE;
        if (number.isBounded()) converted = converted.withRange((Number) number.min(), (Number) number.max());
        return number.isSlider() ? converted.withSlider(true) : converted;
    }

    static <T> ValidationResult<T> result(net.ixdarklord.coolcatcore.api.config.type.ValidationResult<T> result) {
        Component message = result.message().orElse(Component.empty());
        if (result.isError()) return ValidationResult.error(message);
        if (result.isCorrected()) return ValidationResult.corrected(result.value(), message);
        return ValidationResult.ok(result.value());
    }

    static ConfigTheme theme(net.ixdarklord.coolcatcore.api.config.ConfigTheme theme) {
        return THEMES.computeIfAbsent(theme, CoolCatTypes::convertTheme);
    }

    private static ConfigTheme convertTheme(net.ixdarklord.coolcatcore.api.config.ConfigTheme theme) {
        ConfigTheme.Builder builder = ConfigTheme.builder()
                .colors(scheme(theme.colors()))
                .lightColors(scheme(theme.lightColors()))
                .icon(theme.icon())
                .background(theme.background())
                .mode(ConfigTheme.BackgroundMode.valueOf(theme.mode().name()))
                .backgroundOpacity(theme.backgroundOpacity())
                .textureOpacity(theme.textureOpacity())
                .backgroundInWorld(theme.backgroundInWorld())
                .popupSprite(theme.popupSprite())
                // Core's built-in effect is Glazed Menu's own; other ids are Canvas's effects (CanvasEffects).
                .effects(theme.effects().stream()
                        .map(id -> id.equals(net.ixdarklord.coolcatcore.api.config.ConfigTheme.STARFALL) ? ConfigTheme.STARFALL : id).toList())
                .source(theme);
        if (theme.mode() == net.ixdarklord.coolcatcore.api.config.ConfigTheme.BackgroundMode.TILE) builder.tiled(theme.tileSize());
        return builder.build();
    }

    private static ConfigColorScheme scheme(net.ixdarklord.coolcatcore.api.config.ConfigColorScheme colors) {
        return ConfigColorScheme.builder()
                .accent(colors.accent()).backdrop(colors.backdrop()).panel(colors.panel()).panelBorder(colors.panelBorder())
                .bar(colors.bar()).popup(colors.popup()).rowHover(colors.rowHover()).field(colors.field()).fieldBorder(colors.fieldBorder())
                .button(colors.button()).buttonHover(colors.buttonHover()).buttonDisabled(colors.buttonDisabled())
                .toggleOff(colors.toggleOff()).knob(colors.knob()).text(colors.text()).textDim(colors.textDim())
                .textMuted(colors.textMuted()).modified(colors.modified()).error(colors.error()).success(colors.success())
                .warning(colors.warning()).build();
    }

    // A Core type Glazed Menu has nothing like: its codec, checks and text, as they are.
    private record Delegate<T>(net.ixdarklord.coolcatcore.api.config.type.ConfigType<T> type) implements ConfigType<T> {
        @Override
        public Codec<T> codec() {
            return this.type.codec();
        }

        @Override
        public ValidationResult<T> validate(T value) {
            return result(this.type.validate(value));
        }

        @Override
        public ValidationResult<T> parse(String text) {
            return result(this.type.parse(text));
        }

        @Override
        public String format(T value) {
            return this.type.format(value);
        }

        @Override
        public List<Component> describe() {
            return this.type.describe();
        }

        @Override
        public List<String> suggestions() {
            return this.type.suggestions();
        }

        @Override
        public boolean equals(T first, T second) {
            return this.type.equals(first, second);
        }
    }

    static boolean isStarfall(Identifier id) {
        return id.equals(net.ixdarklord.coolcatcore.api.config.ConfigTheme.STARFALL);
    }
}
