package net.ixdarklord.glazedmenu.internal.game;

import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.ConfigScreen;
import net.ixdarklord.glazedmenu.internal.mixin.EditGameRulesScreenAccessor;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.internal.source.ConfigSource;
import net.ixdarklord.glazedmenu.internal.source.Values;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfig;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalConfigBuilder;
import net.ixdarklord.glazedmenu.internal.source.external.ExternalValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    // The rules being edited now, if any.
    private @Nullable ExternalConfig editing;

    private GameRulesSource() {}

    /** The screen to open instead: the rules in Glazed Menu's config screen, for the game's screen of a new world's rules. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (screen == null || screen.getClass() != EditGameRulesScreen.class || !GlazedSettings.replaceGameRules()) return screen;
        try {
            EditGameRulesScreenAccessor game = (EditGameRulesScreenAccessor) screen;
            GameRules rules = game.glazedmenu$gameRules();
            Consumer<Optional<GameRules>> exit = game.glazedmenu$exitCallback();
            ExternalConfig config = INSTANCE.read(rules, () -> exit.accept(Optional.of(rules)));
            return ConfigScreen.create(Minecraft.getInstance().screen, config);
        } catch (RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.warn("Couldn't read the game rules; the game's own screen opens", e);
            return screen;
        }
    }

    // A rule with what kind it is (1.21.1 keeps the two apart).
    private record Rule(GameRules.Key<?> key, GameRules.Type<?> type) {}

    // The rules as a config: the categories by name, each one's rules by name; saving it runs done.
    private ExternalConfig read(GameRules rules, Runnable done) {
        List<Rule> all = new ArrayList<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                all.add(new Rule(key, type));
            }
        });
        all.sort(Comparator.comparing((Rule rule) -> Component.translatable(rule.key.getCategory().getDescriptionId()).getString().toLowerCase(Locale.ROOT))
                .thenComparing(rule -> Component.translatable(rule.key.getDescriptionId()).getString().toLowerCase(Locale.ROOT)));
        Map<GameRules.Category, List<Rule>> byCategory = new LinkedHashMap<>();
        for (Rule rule : all) byCategory.computeIfAbsent(rule.key.getCategory(), category -> new ArrayList<>()).add(rule);
        ExternalConfigBuilder builder = new ExternalConfigBuilder("minecraft", "game_rules", ConfigScope.WORLD)
                .title(Component.translatable("editGamerule.title"));
        byCategory.forEach((category, categoryRules) -> {
            builder.push(key(category.name().toLowerCase(Locale.ROOT)), category.getDescriptionId(), null, List.of());
            for (Rule rule : categoryRules) {
                try {
                    add(builder, rules, rule);
                } catch (RuntimeException e) {
                    GlazedMenu.LOGGER.debug("Left the game rule {} out", rule.key.getId(), e);
                }
            }
            builder.pop();
        });
        this.editing = builder.build(() -> Access.LOCAL, done);
        return this.editing;
    }

    // A key without the characters a path gives meaning to (another mod's rule may be named "mod:rule" or "mod.rule").
    private static String key(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    @SuppressWarnings("unchecked")
    private static void add(ExternalConfigBuilder builder, GameRules rules, Rule rule) {
        GameRules.Value<?> standard = rule.type.createRule();
        if (standard instanceof GameRules.BooleanValue bool) {
            GameRules.Key<GameRules.BooleanValue> key = (GameRules.Key<GameRules.BooleanValue>) rule.key;
            // No server yet: the rules are a new world's.
            value(builder, key, ConfigTypes.BOOLEAN, bool.get(), () -> rules.getRule(key).get(), value -> rules.getRule(key).set(value, null));
        } else if (standard instanceof GameRules.IntegerValue number) {
            GameRules.Key<GameRules.IntegerValue> key = (GameRules.Key<GameRules.IntegerValue>) rule.key;
            int min = Integer.MIN_VALUE;
            int max = Integer.MAX_VALUE;
            if (rule.type.createArgument("value").getType() instanceof IntegerArgumentType bounds) {
                min = bounds.getMinimum();
                max = bounds.getMaximum();
            }
            value(builder, key, min == Integer.MIN_VALUE && max == Integer.MAX_VALUE ? ConfigTypes.INT : ConfigTypes.intRange(min, max), number.get(),
                    () -> rules.getRule(key).get(), value -> rules.getRule(key).set(value, null));
        }
    }

    private static <T> void value(ExternalConfigBuilder builder, GameRules.Key<?> rule, ConfigType<T> type, T standard, Supplier<T> getter, Consumer<T> setter) {
        String name = rule.getDescriptionId();
        String description = name + ".description";
        builder.value(key(rule.getId()), type, standard,
                        new ExternalValue.Binding<T>() {
                            @Override
                            public T get() {
                                return getter.get();
                            }

                            @Override
                            public void set(T value) {
                                setter.accept(value);
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
