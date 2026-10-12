package net.ixdarklord.glazedmenu.internal.game;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A new world's game rules, in Glazed Menu's config screen in place of the game's own: each category of rules is a
 * category of settings, each rule a toggle or a number, with the game's names and
 * descriptions, a search, and the rules changed from their defaults marked. Done hands the rules back to the screen
 * making the world, as the game's Done does; Cancel leaves them as they were.
 * <p>
 * The rules aren't a config kept anywhere: this source owns the one being edited and lists none, so the rules never
 * show among the mods' configs. (The rules of a world being played are asked of its server by the game's own screen,
 * which stays.)
 */
public final class GameRulesSource implements ConfigSource {
    public static final GameRulesSource INSTANCE = new GameRulesSource();
    private static final Class<?> VANILLA = net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen.class;
    // The rules being edited now, if any.
    private @Nullable ExternalConfig editing;

    private GameRulesSource() {}

    /** The screen to open instead: the rules in Glazed Menu's config screen, for the game's screen of a new world's rules. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (screen == null || screen.getClass() != VANILLA || !GlazedSettings.replaceGameRules()) return screen;
        try {
            // 1.20.1's screen keeps both to itself: they're read through an accessor.
            GameRules rules = ((EditGameRulesScreenAccessor) screen).glazedmenu$gameRules();
            Consumer<Optional<GameRules>> exit = ((EditGameRulesScreenAccessor) screen).glazedmenu$exitCallback();
            ExternalConfig config = INSTANCE.read(rules, () -> exit.accept(Optional.of(rules)));
            return ConfigScreen.create(Minecraft.getInstance().screen, config);
        } catch (RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.warn("Couldn't read the game rules; the game's own screen opens", e);
            return screen;
        }
    }

    // A rule as the game lists it: its key, what makes its value (which says its default), and which kind it is.
    private record Rule(GameRules.Key<?> key, GameRules.Type<?> type, boolean number) {
        String name() {
            return Component.translatable(this.key.getDescriptionId()).getString().toLowerCase(Locale.ROOT);
        }

        String id() {
            return this.key.getId();
        }
    }

    // The rules as a config: the categories by name, each one's rules by name; saving it runs done.
    private ExternalConfig read(GameRules rules, Runnable done) {
        // 1.20.1 has no list of the rules to ask for: the game shows each kind of them to a visitor.
        List<Rule> all = new ArrayList<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                all.add(new Rule(key, type, false));
            }

            @Override
            public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                all.add(new Rule(key, type, true));
            }
        });
        all.sort(Comparator.comparing(Rule::name).thenComparing(Rule::id));
        // The categories by their names (and by themselves, should two be named alike).
        Map<String, GameRules.Category> categories = new TreeMap<>();
        Map<GameRules.Category, List<Rule>> byCategory = new EnumMap<>(GameRules.Category.class);
        for (Rule rule : all) {
            GameRules.Category category = rule.key().getCategory();
            categories.put(Component.translatable(category.getDescriptionId()).getString().toLowerCase(Locale.ROOT) + '\n' + category.name(), category);
            byCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(rule);
        }
        ExternalConfigBuilder builder = new ExternalConfigBuilder("minecraft", "game_rules", ConfigScope.WORLD)
                .title(Component.translatable("editGamerule.title"));
        for (GameRules.Category category : categories.values()) {
            builder.push(key(category.name().toLowerCase(Locale.ROOT)), category.getDescriptionId(), null, List.of());
            for (Rule rule : byCategory.get(category)) {
                try {
                    add(builder, rules, rule);
                } catch (RuntimeException e) {
                    GlazedMenu.LOGGER.debug("Left the game rule {} out", rule.id(), e);
                }
            }
            builder.pop();
        }
        this.editing = builder.build(() -> Access.LOCAL, done);
        return this.editing;
    }

    // A key without the characters a path gives meaning to (1.20.1 names a rule by a plain word; another mod's often
    // has the mod's name before a colon or a dot).
    private static String key(String id) {
        return id.replaceAll("[^A-Za-z0-9_]", "_");
    }

    // 1.20.1's rules are toggles and whole numbers, and it tells of no bounds for a number.
    @SuppressWarnings("unchecked")
    private static void add(ExternalConfigBuilder builder, GameRules rules, Rule rule) {
        if (rule.number()) {
            GameRules.Key<GameRules.IntegerValue> key = (GameRules.Key<GameRules.IntegerValue>) rule.key();
            int normal = ((GameRules.Type<GameRules.IntegerValue>) rule.type()).createRule().get();
            finish(key, builder.value(key(key.getId()), ConfigTypes.INT, normal, new ExternalValue.Binding<Integer>() {
                @Override
                public Integer get() {
                    return rules.getRule(key).get();
                }

                // No server yet: the rules are a new world's.
                @Override
                public void set(Integer value) {
                    rules.getRule(key).set(value, null);
                }
            }));
        } else {
            GameRules.Key<GameRules.BooleanValue> key = (GameRules.Key<GameRules.BooleanValue>) rule.key();
            boolean normal = ((GameRules.Type<GameRules.BooleanValue>) rule.type()).createRule().get();
            finish(key, builder.value(key(key.getId()), ConfigTypes.BOOLEAN, normal, new ExternalValue.Binding<Boolean>() {
                @Override
                public Boolean get() {
                    return rules.getRule(key).get();
                }

                @Override
                public void set(Boolean value) {
                    rules.getRule(key).set(value, null);
                }
            }));
        }
    }

    private static void finish(GameRules.Key<?> key, ExternalConfigBuilder.Value<?> value) {
        String name = key.getDescriptionId();
        String description = name + ".description";
        value.translation(name, null)
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
