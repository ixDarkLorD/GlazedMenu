package net.ixdarklord.glazedmenu.internal.source.game;

import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.api.config.type.EnumType;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.mixin.OptionInstanceAccessor;
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * 1.20.1's names aren't the same in a running game as in its code, so nothing here goes by a name of the game's: the
 * options are the ones 1.20.1's {@link Options} hands out, each asked for by its own method, and they're grouped by the
 * lists 1.20.1's options screens are written with. An option's default and its labels are read through an accessor;
 * the ends of a slider the game keeps to itself are read from the one method of that shape. An option that can't be
 * read is left out, and the game's own screens stay the place for what isn't an option of this kind (key binds, the
 * language, packs).
 */
public final class GameOptionsSource extends ExternalSource {
    public static final GameOptionsSource INSTANCE = new GameOptionsSource();
    // How the game's option enums name their constants, for the choice lists.
    private static final Map<Enum<?>, Component> ENUM_NAMES = new ConcurrentHashMap<>();
    // The mipmap levels as last applied: changing them reloads the textures, as leaving the game's video settings does
    // (1.20.1 has no other texture option).
    private static int mipmapLevels;
    // The GUI scale as last applied: 1.20.1's option doesn't lay the window out again by itself, its video settings do.
    private static int guiScale;
    // The method giving the value at a place along a slider, once found.
    private static @Nullable Method fromSlider;

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
        // Every option the game hands out, by the name of the method handing it out ("fov", "renderDistance"), in the
        // order of those names.
        Map<String, OptionInstance<?>> byKey = all(options);

        // The groups, in order; each option goes in the first that names it. 1.20.1's screens don't say how they'd be
        // split, or keep their lists where they can be asked for, so the lists are written out here as those screens
        // have them.
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Set<String> taken = new LinkedHashSet<>();
        claim(groups, taken, "general", List.of("fov", "mainHand"), byKey);
        // Video in the parts later versions' own screen splits it into (1.20.1's is one list): "video/display" is the
        // group display in the group video.
        claim(groups, taken, "video/display", List.of("fullscreen", "framerateLimit", "enableVsync", "guiScale", "gamma"), byKey);
        claim(groups, taken, "video/quality", List.of("graphicsMode", "renderDistance", "simulationDistance", "prioritizeChunkUpdates", "ambientOcclusion",
                "biomeBlendRadius", "cloudStatus", "particles", "mipmapLevels", "entityShadows", "entityDistanceScaling"), byKey);
        claim(groups, taken, "video/preferences", List.of("bobView", "attackIndicator", "showAutosaveIndicator", "screenEffectScale", "fovEffectScale",
                "glintSpeed", "glintStrength"), byKey);
        claim(groups, taken, "controls", List.of("toggleCrouch", "toggleSprint", "autoJump", "operatorItemsTab"), byKey);
        claim(groups, taken, "mouse", List.of("sensitivity", "invertYMouse", "mouseWheelSensitivity", "discreteMouseScroll", "touchscreen", "rawMouseInput"), byKey);
        claim(groups, taken, "chat", List.of("chatVisibility", "chatColors", "chatLinks", "chatLinksPrompt", "chatOpacity", "textBackgroundOpacity", "chatScale",
                "chatLineSpacing", "chatDelay", "chatWidth", "chatHeightFocused", "chatHeightUnfocused", "narrator", "autoSuggestions", "hideMatchedNames",
                "reducedDebugInfo", "onlyShowSecureChat"), byKey);
        // 1.20.1 has no font screen: its one font option is on the language screen.
        claim(groups, taken, "font", List.of("forceUnicodeFont"), byKey);
        claim(groups, taken, "sounds", List.of("showSubtitles", "directionalAudio"), byKey);
        claim(groups, taken, "online", List.of("realmsNotifications", "allowServerListing"), byKey);
        claim(groups, taken, "accessibility", List.of("highContrast", "backgroundForChatOnly", "notificationDisplayTime", "darknessEffectScale",
                "damageTiltStrength", "hideLightningFlash", "darkMojangStudiosBackground", "panoramaSpeed"), byKey);
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
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    GlazedMenu.LOGGER.debug("Left the game option {} out", key, e);
                }
            }
            // The volume of each kind of sound, which the game keeps apart from its other options.
            if (group.equals("sounds")) {
                for (SoundSource source : SoundSource.values()) {
                    try {
                        add(builder, "volume_" + source.name().toLowerCase(Locale.ROOT), options.getSoundSourceOptionInstance(source));
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                        GlazedMenu.LOGGER.debug("Left the volume of {} out", source, e);
                    }
                }
            }
            builder.pop();
        });
        if (open[0] != null) builder.pop();
        mipmapLevels = options.mipmapLevels().get();
        guiScale = options.guiScale().get();
        return List.of(builder.build(() -> Access.LOCAL, GameOptionsSource::save));
    }

    private static void claim(Map<String, List<String>> groups, Set<String> taken, String group, List<String> keys, Map<String, OptionInstance<?>> byKey) {
        List<String> claimed = new ArrayList<>();
        for (String key : keys) {
            if (byKey.containsKey(key) && taken.add(key)) claimed.add(key);
        }
        if (!claimed.isEmpty() || group.equals("sounds")) groups.put(group, claimed);
    }

    // Every option 1.20.1's Options hands out, each under the name of the method handing it out. Asked for one by one:
    // in a running game those methods don't go by these names, so they can't be looked up by them.
    private static Map<String, OptionInstance<?>> all(Options options) {
        Map<String, OptionInstance<?>> all = new TreeMap<>();
        all.put("allowServerListing", options.allowServerListing());
        all.put("ambientOcclusion", options.ambientOcclusion());
        all.put("attackIndicator", options.attackIndicator());
        all.put("autoJump", options.autoJump());
        all.put("autoSuggestions", options.autoSuggestions());
        all.put("backgroundForChatOnly", options.backgroundForChatOnly());
        all.put("biomeBlendRadius", options.biomeBlendRadius());
        all.put("bobView", options.bobView());
        all.put("chatColors", options.chatColors());
        all.put("chatDelay", options.chatDelay());
        all.put("chatHeightFocused", options.chatHeightFocused());
        all.put("chatHeightUnfocused", options.chatHeightUnfocused());
        all.put("chatLineSpacing", options.chatLineSpacing());
        all.put("chatLinks", options.chatLinks());
        all.put("chatLinksPrompt", options.chatLinksPrompt());
        all.put("chatOpacity", options.chatOpacity());
        all.put("chatScale", options.chatScale());
        all.put("chatVisibility", options.chatVisibility());
        all.put("chatWidth", options.chatWidth());
        all.put("cloudStatus", options.cloudStatus());
        all.put("damageTiltStrength", options.damageTiltStrength());
        all.put("darkMojangStudiosBackground", options.darkMojangStudiosBackground());
        all.put("darknessEffectScale", options.darknessEffectScale());
        all.put("directionalAudio", options.directionalAudio());
        all.put("discreteMouseScroll", options.discreteMouseScroll());
        all.put("enableVsync", options.enableVsync());
        all.put("entityDistanceScaling", options.entityDistanceScaling());
        all.put("entityShadows", options.entityShadows());
        all.put("forceUnicodeFont", options.forceUnicodeFont());
        all.put("fov", options.fov());
        all.put("fovEffectScale", options.fovEffectScale());
        all.put("framerateLimit", options.framerateLimit());
        all.put("fullscreen", options.fullscreen());
        all.put("gamma", options.gamma());
        all.put("glintSpeed", options.glintSpeed());
        all.put("glintStrength", options.glintStrength());
        all.put("graphicsMode", options.graphicsMode());
        all.put("guiScale", options.guiScale());
        all.put("hideLightningFlash", options.hideLightningFlash());
        all.put("hideMatchedNames", options.hideMatchedNames());
        all.put("highContrast", options.highContrast());
        all.put("invertYMouse", options.invertYMouse());
        all.put("mainHand", options.mainHand());
        all.put("mipmapLevels", options.mipmapLevels());
        all.put("mouseWheelSensitivity", options.mouseWheelSensitivity());
        all.put("narrator", options.narrator());
        all.put("notificationDisplayTime", options.notificationDisplayTime());
        all.put("onlyShowSecureChat", options.onlyShowSecureChat());
        all.put("operatorItemsTab", options.operatorItemsTab());
        all.put("panoramaSpeed", options.panoramaSpeed());
        all.put("particles", options.particles());
        all.put("prioritizeChunkUpdates", options.prioritizeChunkUpdates());
        all.put("rawMouseInput", options.rawMouseInput());
        all.put("realmsNotifications", options.realmsNotifications());
        all.put("reducedDebugInfo", options.reducedDebugInfo());
        all.put("renderDistance", options.renderDistance());
        all.put("screenEffectScale", options.screenEffectScale());
        all.put("sensitivity", options.sensitivity());
        all.put("showAutosaveIndicator", options.showAutosaveIndicator());
        all.put("showSubtitles", options.showSubtitles());
        all.put("simulationDistance", options.simulationDistance());
        // The sound device is a name among those the machine has, not a switch, a slider or a choice of the game's: it's
        // listed for completeness and left out with the others of no such kind.
        all.put("soundDevice", options.soundDevice());
        all.put("telemetryOptInExtra", options.telemetryOptInExtra());
        all.put("textBackgroundOpacity", options.textBackgroundOpacity());
        all.put("toggleCrouch", options.toggleCrouch());
        all.put("toggleSprint", options.toggleSprint());
        all.put("touchscreen", options.touchscreen());
        return all;
    }

    // Writes options.txt, and does what 1.20.1's video settings do for the two options that don't apply as they're set:
    // the textures are loaded again when the mipmap levels changed, and the window laid out again when the GUI scale did.
    private static void save() {
        Minecraft minecraft = Minecraft.getInstance();
        Options options = minecraft.options;
        options.save();
        int mipmaps = options.mipmapLevels().get();
        if (mipmaps != mipmapLevels) {
            mipmapLevels = mipmaps;
            minecraft.updateMaxMipLevel(mipmaps);
            minecraft.delayTextureReload();
        }
        int scale = options.guiScale().get();
        if (scale != guiScale) {
            guiScale = scale;
            minecraft.resizeDisplay();
        }
    }

    // --- One option ---

    @SuppressWarnings("unchecked")
    private static <T> void add(ExternalConfigBuilder builder, String key, OptionInstance<T> option) throws ReflectiveOperationException {
        OptionInstanceAccessor<T> accessor = (OptionInstanceAccessor<T>) (Object) option;
        T initial = accessor.glazedmenu$initialValue();
        Object values = option.values();
        String name = option.toString();
        if (initial instanceof Boolean) {
            // Sneak, Sprint and the like are held or toggled: as a switch, each says which the switch turns on.
            if (key.startsWith("toggle")) name = name + ": " + Component.translatable("options.key.toggle").getString();
            value(builder, key, name, ConfigTypes.BOOLEAN, (Boolean) initial, () -> (Boolean) option.get(), value -> set(option, value));
        } else if (initial instanceof Integer && values instanceof OptionInstance.IntRange range) {
            // A whole number between two ends the game says outright.
            value(builder, key, name, ConfigTypes.intRange(range.minInclusive(), range.maxInclusive()).withSlider(true), (Integer) initial,
                    () -> (Integer) option.get(), value -> set(option, value));
        } else if (initial instanceof Integer && values instanceof OptionInstance.ClampingLazyMaxIntRange range) {
            // The same, with a far end that depends on the window (the GUI scale): the one it has now.
            int low = range.minInclusive();
            value(builder, key, name, ConfigTypes.intRange(low, Math.max(low, range.maxInclusive())).withSlider(true), (Integer) initial,
                    () -> (Integer) option.get(), value -> set(option, value));
        } else if (initial instanceof Double && values == OptionInstance.UnitDouble.INSTANCE) {
            // A share of the whole, which the game shows as a percentage.
            value(builder, key, name, ConfigTypes.intRange(0, 100).withSlider(true), (int) Math.round((Double) initial * 100),
                    () -> (int) Math.round((Double) option.get() * 100), value -> set(option, value / 100.0));
        } else if (isSlider(values)) {
            // A slider over values worked out from another range (the frame rate in tens, the chat delay in tenths).
            Object low = fromSlider(values, 0);
            Object high = fromSlider(values, 1);
            if (initial instanceof Integer && low instanceof Integer first && high instanceof Integer second) {
                value(builder, key, name, ConfigTypes.intRange(Math.min(first, second), Math.max(first, second)).withSlider(true), (Integer) initial,
                        () -> (Integer) option.get(), value -> set(option, value));
            } else if (initial instanceof Double && low instanceof Double first && high instanceof Double second) {
                value(builder, key, name, ConfigTypes.doubleRange(Math.min(first, second), Math.max(first, second)).withSlider(true), (Double) initial,
                        () -> (Double) option.get(), value -> set(option, value));
            }
        } else if (initial instanceof Enum<?> constant) {
            addEnum(builder, key, name, option, constant, (Function<Object, Component>) (Object) accessor.glazedmenu$toStringFunction());
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

    // --- A slider's ends ---

    // The game keeps the kind "values along a slider" to itself (it can't be named from here, and by name it couldn't be
    // found in a running game), so its method giving the value at a place along the slider is found by its shape: the
    // one method an interface of the option's values asks for that takes a number and gives a value back. Found on the
    // first slider met and kept; values that are no slider's have no such interface.
    private static @Nullable Method fromSlider(Object values) {
        Method known = fromSlider;
        if (known != null) return known.getDeclaringClass().isInstance(values) ? known : null;
        Set<Class<?>> seen = new LinkedHashSet<>();
        for (Class<?> type = values.getClass(); type != null; type = type.getSuperclass()) interfacesOf(type, seen);
        for (Class<?> type : seen) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) && method.getParameterCount() == 1 && method.getParameterTypes()[0] == double.class
                        && method.getReturnType() == Object.class) {
                    try {
                        method.setAccessible(true);
                    } catch (RuntimeException e) {
                        GlazedMenu.LOGGER.debug("The game's sliders can't be read", e);
                        return null;
                    }
                    fromSlider = method;
                    return method;
                }
            }
        }
        return null;
    }

    private static void interfacesOf(Class<?> type, Set<Class<?>> into) {
        for (Class<?> parent : type.getInterfaces()) {
            if (into.add(parent)) interfacesOf(parent, into);
        }
    }

    private static boolean isSlider(Object values) {
        return fromSlider(values) != null;
    }

    // The value at one end of an option's slider (0) or the other (1).
    private static Object fromSlider(Object values, double at) throws ReflectiveOperationException {
        Method method = fromSlider(values);
        if (method == null) throw new NoSuchMethodException("fromSliderValue");
        return method.invoke(values, at);
    }
}
