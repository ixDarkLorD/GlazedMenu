package net.ixdarklord.glazedmenu.internal.source.game;

import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.api.config.type.EnumType;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalSource;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The game's own options (options.txt) as a config, "minecraft:options", so Glazed Menu's screens, its search across
 * every mod's settings, its list of changed settings and its profiles cover them too. Each option the game keeps as an
 * {@link OptionInstance} becomes a setting: a toggle, a slider over the option's own range, or a choice among an enum's
 * constants, named as the game names it. They're grouped as the game's options screens group them, where those say.
 * <p>
 * The game keeps what an option may be to itself, so that's read by reflection; an option that can't be read is left
 * out, and the game's own screens stay the place for what isn't an option of this kind (key binds, the language, packs).
 */
public final class GameOptionsSource extends ExternalSource {
    public static final GameOptionsSource INSTANCE = new GameOptionsSource();
    private static final String SCREENS = "net.minecraft.client.gui.screens.options.";
    // How the game's option enums name their constants, for the choice lists.
    private static final Map<Enum<?>, Component> ENUM_NAMES = new ConcurrentHashMap<>();
    // The texture options as last applied: changing one reloads the textures, as leaving the game's video settings does.
    private static int mipmapLevels;
    private static int anisotropy;
    private static @Nullable Object textureFiltering;

    static {
        EnumType.addNames(constant -> {
            Component name = ENUM_NAMES.get(constant);
            return name == null ? null : () -> name;
        });
    }

    private GameOptionsSource() {}

    @Override
    public String name() {
        return "Minecraft";
    }

    // The player may turn the game's options off in Glazed Menu's settings, at any time.
    @Override
    public synchronized List<ExternalConfig> configs() {
        return GlazedSettings.gameOptions() ? super.configs() : List.of();
    }

    @Override
    protected List<ExternalConfig> discover() {
        Minecraft minecraft = Minecraft.getInstance();
        Options options = minecraft.options;
        // Every option the game hands out, by the name of the method handing it out ("fov", "renderDistance").
        Map<String, OptionInstance<?>> byKey = new LinkedHashMap<>();
        Method[] methods = Options.class.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) {
            if (method.getReturnType() != OptionInstance.class || method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) continue;
            try {
                byKey.put(method.getName(), (OptionInstance<?>) method.invoke(options));
            } catch (ReflectiveOperationException | RuntimeException e) {
                GlazedMenu.LOGGER.debug("Couldn't read the game option {}", method.getName(), e);
            }
        }
        Map<OptionInstance<?>, String> keys = new IdentityHashMap<>();
        byKey.forEach((key, option) -> keys.put(option, key));

        // The groups, in order; each option goes in the first that names it.
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Set<String> taken = new LinkedHashSet<>();
        claim(groups, taken, "general", List.of("fov", "mainHand"), byKey);
        // Video in the parts the game's own screen splits it into: "video/display" is the group display in the group video.
        claim(groups, taken, "video/display", keysOf(keys, options, "VideoSettingsScreen", "displayOptions"), byKey);
        claim(groups, taken, "video/quality", keysOf(keys, options, "VideoSettingsScreen", "qualityOptions"), byKey);
        claim(groups, taken, "video/preferences", keysOf(keys, options, "VideoSettingsScreen", "preferenceOptions"), byKey);
        claim(groups, taken, "controls", keysOf(keys, options, "controls.ControlsScreen", "options"), byKey);
        claim(groups, taken, "mouse", keysOf(keys, options, "MouseSettingsScreen", "options"), byKey);
        claim(groups, taken, "chat", keysOf(keys, options, "ChatOptionsScreen", "options"), byKey);
        claim(groups, taken, "font", keysOf(keys, options, "FontOptionsScreen", "options"), byKey);
        claim(groups, taken, "sounds", List.of("showSubtitles", "directionalAudio", "musicFrequency", "musicToast"), byKey);
        claim(groups, taken, "online", List.of("realmsNotifications", "allowServerListing"), byKey);
        claim(groups, taken, "accessibility", keysOf(keys, options, "AccessibilityOptionsScreen", "options"), byKey);
        claim(groups, taken, "other", List.copyOf(byKey.keySet()), byKey);

        ExternalConfigBuilder builder = new ExternalConfigBuilder("minecraft", "options", ConfigScope.CLIENT)
                .title(Component.translatable("options.title"))
                .file("options.txt", minecraft.gameDirectory.toPath().resolve("options.txt"));
        String[] open = {null};
        groups.forEach((path, groupKeys) -> {
            // A part of a category opens the category first, once; the next category closes it.
            String parent = path.contains("/") ? path.substring(0, path.indexOf('/')) : null;
            String group = parent != null ? path.substring(parent.length() + 1) : path;
            if (open[0] != null && !open[0].equals(parent)) {
                builder.pop();
                open[0] = null;
            }
            if (parent != null && open[0] == null) {
                builder.push(parent, "config.minecraft.options." + parent, null, List.of());
                open[0] = parent;
            }
            builder.push(group, "config.minecraft.options." + path.replace('/', '.'), null, List.of());
            for (String key : groupKeys) {
                try {
                    add(builder, key, byKey.get(key));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    GlazedMenu.LOGGER.debug("Left the game option {} out", key, e);
                }
            }
            // The volume of each kind of sound, which the game keeps apart from its other options.
            if (group.equals("sounds")) {
                for (SoundSource source : SoundSource.values()) {
                    try {
                        add(builder, "volume_" + source.name().toLowerCase(Locale.ROOT), options.getSoundSourceOptionInstance(source));
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        GlazedMenu.LOGGER.debug("Left the volume of {} out", source, e);
                    }
                }
            }
            builder.pop();
        });
        if (open[0] != null) builder.pop();
        mipmapLevels = options.mipmapLevels().get();
        anisotropy = options.maxAnisotropyBit().get();
        textureFiltering = options.textureFiltering().get();
        return List.of(builder.build(() -> Access.LOCAL, GameOptionsSource::save));
    }

    private static void claim(Map<String, List<String>> groups, Set<String> taken, String group, List<String> keys, Map<String, OptionInstance<?>> byKey) {
        List<String> claimed = new ArrayList<>();
        for (String key : keys) {
            if (byKey.containsKey(key) && taken.add(key)) claimed.add(key);
        }
        if (!claimed.isEmpty() || group.equals("sounds")) groups.put(group, claimed);
    }

    // The options one of the game's options screens lists (its private static methods naming them), as their keys; none
    // where a version's screen doesn't say.
    private static List<String> keysOf(Map<OptionInstance<?>, String> keys, Options options, String screen, String... lists) {
        List<String> found = new ArrayList<>();
        for (String list : lists) {
            try {
                Method method = Class.forName(SCREENS + screen).getDeclaredMethod(list, Options.class);
                method.setAccessible(true);
                for (OptionInstance<?> option : (OptionInstance<?>[]) method.invoke(null, options)) {
                    String key = keys.get(option);
                    if (key != null) found.add(key);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                GlazedMenu.LOGGER.debug("Couldn't read {} of the game's {}", list, screen, e);
            }
        }
        return found;
    }

    // Writes options.txt, and reloads the textures when a texture option changed (the others apply as they're set).
    private static void save() {
        Minecraft minecraft = Minecraft.getInstance();
        Options options = minecraft.options;
        options.save();
        int mipmaps = options.mipmapLevels().get();
        int bit = options.maxAnisotropyBit().get();
        Object filtering = options.textureFiltering().get();
        if (mipmaps != mipmapLevels || bit != anisotropy || !Objects.equals(filtering, textureFiltering)) {
            mipmapLevels = mipmaps;
            anisotropy = bit;
            textureFiltering = filtering;
            minecraft.updateMaxMipLevel(mipmaps);
            minecraft.delayTextureReload();
        }
    }

    // --- One option ---

    private static <T> void add(ExternalConfigBuilder builder, String key, OptionInstance<T> option) throws ReflectiveOperationException {
        T initial = field(option, "initialValue");
        Object values = option.values();
        String name = option.toString();
        if (initial instanceof Boolean) {
            // Sneak, Sprint and the like are held or toggled: as a switch, each says which the switch turns on.
            if (key.startsWith("toggle")) name = name + ": " + Component.translatable("options.key.toggle").getString();
            value(builder, key, name, ConfigTypes.BOOLEAN, (Boolean) initial, () -> (Boolean) option.get(), value -> set(option, value));
        } else if (Class.forName("net.minecraft.client.OptionInstance$SliderableValueSet").isInstance(values)) {
            Object low = fromSlider(values, 0);
            Object high = fromSlider(values, 1);
            if (initial instanceof Integer && low instanceof Integer first && high instanceof Integer second) {
                value(builder, key, name, ConfigTypes.intRange(Math.min(first, second), Math.max(first, second)).withSlider(true), (Integer) initial,
                        () -> (Integer) option.get(), value -> set(option, value));
            } else if (initial instanceof Double && values.getClass().getName().endsWith("$UnitDouble")) {
                // A share of the whole, which the game shows as a percentage.
                value(builder, key, name, ConfigTypes.intRange(0, 100).withSlider(true), (int) Math.round((Double) initial * 100),
                        () -> (int) Math.round((Double) option.get() * 100), value -> set(option, value / 100.0));
            } else if (initial instanceof Double && low instanceof Double first && high instanceof Double second) {
                value(builder, key, name, ConfigTypes.doubleRange(Math.min(first, second), Math.max(first, second)).withSlider(true), (Double) initial,
                        () -> (Double) option.get(), value -> set(option, value));
            }
        } else if (initial instanceof Enum<?> constant) {
            addEnum(builder, key, name, option, constant, field(option, "toString"));
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> void addEnum(ExternalConfigBuilder builder, String key, String name, OptionInstance<?> option, Enum<?> initial,
                                                    Function<Object, Component> label) {
        Class<E> type = (Class<E>) initial.getDeclaringClass();
        for (E constant : type.getEnumConstants()) {
            try {
                // The game's label for the value; some say the option's name before it.
                String text = label.apply(constant).getString();
                if (text.startsWith(name + ": ")) text = text.substring(name.length() + 2);
                if (!text.isBlank()) ENUM_NAMES.putIfAbsent(constant, Component.literal(text));
            } catch (RuntimeException e) {
                GlazedMenu.LOGGER.debug("The game has no name for {} of {}", constant, key, e);
            }
        }
        value(builder, key, name, ConfigTypes.enumOf(type), (E) initial, () -> (E) option.get(), value -> set(option, value));
    }

    private static <T> void value(ExternalConfigBuilder builder, String key, String name, ConfigType<T> type, T initial, Supplier<T> get, Consumer<T> set) {
        builder.value(key, type, initial, new ExternalValue.Binding<T>() {
            @Override
            public T get() {
                return get.get();
            }

            @Override
            public void set(T value) {
                set.accept(value);
            }
        }).name(name).add();
    }

    @SuppressWarnings("unchecked")
    private static void set(OptionInstance<?> option, Object value) {
        ((OptionInstance<Object>) option).set(value);
    }

    @SuppressWarnings("unchecked")
    private static <R> R field(OptionInstance<?> option, String name) throws ReflectiveOperationException {
        Field field = OptionInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        return (R) field.get(option);
    }

    // The value at one end of an option's slider (0) or the other (1).
    private static Object fromSlider(Object values, double at) throws ReflectiveOperationException {
        Method method = Class.forName("net.minecraft.client.OptionInstance$SliderableValueSet").getMethod("fromSliderValue", double.class);
        method.setAccessible(true);
        return method.invoke(values, at);
    }
}
