package net.ixdarklord.glazedmenu.internal.game;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.ConfigScreen;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.internal.source.ConfigSource;
import net.ixdarklord.glazedmenu.internal.source.Values;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.AbstractGameRulesScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A new world's game rules, in Glazed Menu's config screen in place of the game's own: each category of rules is a
 * category of settings, each rule a toggle or a number within the rule's bounds, with the game's names and
 * descriptions, a search, and the rules changed from their defaults marked. Done hands the rules back to the screen
 * making the world, as the game's Done does; Cancel leaves them as they were.
 * <p>
 * The rules aren't a config kept anywhere: this source owns the one being edited and lists none, so the rules never
 * show among the mods' configs. (The rules of a world being played are asked of its server by the game's own screen,
 * which stays.)
 */
public final class GameRulesSource implements ConfigSource {
    public static final GameRulesSource INSTANCE = new GameRulesSource();
    private static final String VANILLA = "net.minecraft.client.gui.screens.worldselection.WorldCreationGameRulesScreen";
    // The rules being edited now, if any.
    private @Nullable ExternalConfig editing;

    private GameRulesSource() {}

    /** The screen to open instead: the rules in Glazed Menu's config screen, for the game's screen of a new world's rules. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (screen == null || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceGameRules()) return screen;
        try {
            GameRules rules = field(screen, "gameRules");
            Consumer<Optional<GameRules>> exit = field(screen, "exitCallback");
            ExternalConfig config = INSTANCE.read(rules, () -> exit.accept(Optional.of(rules)));
            return ConfigScreen.create(Minecraft.getInstance().screen, config);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.warn("Couldn't read the game rules; the game's own screen opens", e);
            return screen;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Screen screen, String name) throws ReflectiveOperationException {
        Field field = AbstractGameRulesScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(screen);
    }

    // The rules as a config: the categories by name, each one's rules by name; saving it runs done.
    private ExternalConfig read(GameRules rules, Runnable done) {
        Map<GameRuleCategory, List<GameRule<?>>> byCategory = new LinkedHashMap<>();
        rules.availableRules().sorted(Comparator.comparing((GameRule<?> rule) -> rule.category().label().getString().toLowerCase(Locale.ROOT))
                        .thenComparing(rule -> Component.translatable(rule.getDescriptionId()).getString().toLowerCase(Locale.ROOT)))
                .forEach(rule -> byCategory.computeIfAbsent(rule.category(), category -> new ArrayList<>()).add(rule));
        ExternalConfigBuilder builder = new ExternalConfigBuilder("minecraft", "game_rules", ConfigScope.WORLD)
                .title(Component.translatable("editGamerule.title"));
        byCategory.forEach((category, categoryRules) -> {
            String label = category.label().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
            builder.push(key(category.id().getNamespace(), category.id().getPath()), label, null, List.of());
            for (GameRule<?> rule : categoryRules) {
                try {
                    add(builder, rules, rule);
                } catch (RuntimeException e) {
                    GlazedMenu.LOGGER.debug("Left the game rule {} out", rule.id(), e);
                }
            }
            builder.pop();
        });
        this.editing = builder.build(() -> Access.LOCAL, done);
        return this.editing;
    }

    // A key without the characters a path gives meaning to; another mod's rules and categories say whose they are.
    private static String key(String namespace, String path) {
        String clean = path.replaceAll("[^A-Za-z0-9_]", "_");
        return namespace.equals("minecraft") ? clean : namespace.replaceAll("[^A-Za-z0-9_]", "_") + "_" + clean;
    }

    @SuppressWarnings("unchecked")
    private static void add(ExternalConfigBuilder builder, GameRules rules, GameRule<?> rule) {
        if (rule.gameRuleType() == GameRuleType.BOOL) {
            value(builder, rules, (GameRule<Boolean>) rule, ConfigTypes.BOOLEAN);
        } else if (rule.gameRuleType() == GameRuleType.INT) {
            int min = Integer.MIN_VALUE;
            int max = Integer.MAX_VALUE;
            if (rule.argument() instanceof IntegerArgumentType bounds) {
                min = bounds.getMinimum();
                max = bounds.getMaximum();
            }
            value(builder, rules, (GameRule<Integer>) rule, min == Integer.MIN_VALUE && max == Integer.MAX_VALUE ? ConfigTypes.INT : ConfigTypes.intRange(min, max));
        }
    }

    private static <T> void value(ExternalConfigBuilder builder, GameRules rules, GameRule<T> rule, ConfigType<T> type) {
        String name = rule.getDescriptionId();
        String description = name + ".description";
        builder.value(key(rule.getIdentifierWithFallback().getNamespace(), rule.getIdentifierWithFallback().getPath()), type, rule.defaultValue(),
                        new ExternalValue.Binding<T>() {
                            @Override
                            public T get() {
                                return rules.get(rule);
                            }

                            // No server yet: the rules are a new world's.
                            @Override
                            public void set(T value) {
                                rules.set(rule, value, null);
                            }
                        })
                .translation(name, null)
                .comment(Language.getInstance().has(description) ? List.of(Component.translatable(description).getString()) : List.of())
                .add();
    }

    // --- The source: owns the rules being edited, lists nothing ---

    @Override
    public String name() {
        return "Game rules";
    }

    @Override
    public List<? extends Config> configs() {
        return List.of();
    }

    @Override
    public boolean owns(Config config) {
        return config == this.editing;
    }

    @Override
    public Access access(Config config) {
        return Access.LOCAL;
    }

    @Override
    public void apply(Config config, Map<ConfigValue<?>, Object> values) {
        values.forEach(Values::set);
        config.save();
    }

    @Override
    public String fileName(Config config) {
        return "";
    }
}
