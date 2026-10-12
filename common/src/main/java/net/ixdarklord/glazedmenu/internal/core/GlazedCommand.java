package net.ixdarklord.glazedmenu.internal.core;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.internal.config.ConfigProfiles;
import net.ixdarklord.glazedmenu.internal.gui.CategoryPopup;
import net.ixdarklord.glazedmenu.internal.gui.ConfigSelectScreen;
import net.ixdarklord.glazedmenu.internal.modlist.GlazedModsScreen;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

// /glazedmenu: every mod's configs; /glazedmenu <mod>: one mod's; /glazedmenu <mod> <config>/<category>: one category in a
// small window, e.g. "client/example_category"; /glazedmenu mods: the mod list; /glazedmenu search [words]: every mod's
// settings matching the words, to edit in place; /glazedmenu changed: the settings that aren't at their defaults;
// /glazedmenu profile <name>: sets and saves a profile's settings.
public final class GlazedCommand {
    private GlazedCommand() {}

    /** The command, for each loader's own kind of client command source; {@code error} tells the player what went wrong. */
    public static <S extends SharedSuggestionProvider> LiteralArgumentBuilder<S> build(BiConsumer<S, Component> error) {
        return LiteralArgumentBuilder.<S>literal(GlazedMenu.MOD_ID)
                .executes(command -> {
                    GlazedClient.openLater(() -> ConfigScreens.createModList(null));
                    return 1;
                })
                .then(LiteralArgumentBuilder.<S>literal("mods").executes(command -> {
                    GlazedClient.openLater(() -> new GlazedModsScreen(null));
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<S>literal("profile").then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.greedyString())
                        .suggests((command, builder) -> SharedSuggestionProvider.suggest(ConfigProfiles.names(), builder))
                        .executes(command -> {
                            String name = StringArgumentType.getString(command, "name");
                            if (!ConfigProfiles.names().contains(name)) {
                                error.accept(command.getSource(), Component.translatableWithFallback("glazedmenu.command.no_profile", "No such profile: %s", name));
                                return 0;
                            }
                            return Math.max(1, ConfigSelectScreen.useProfileNow(name));
                        })))
                .then(LiteralArgumentBuilder.<S>literal("changed").executes(command -> {
                    GlazedClient.openLater(() -> ConfigSelectScreen.changed(null));
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<S>literal("search").executes(command -> {
                    GlazedClient.openLater(() -> ConfigSelectScreen.search(null, ""));
                    return 1;
                }).then(RequiredArgumentBuilder.<S, String>argument("query", StringArgumentType.greedyString()).executes(command -> {
                    String query = StringArgumentType.getString(command, "query");
                    GlazedClient.openLater(() -> ConfigSelectScreen.search(null, query));
                    return 1;
                })))
                .then(RequiredArgumentBuilder.<S, String>argument("mod", StringArgumentType.word())
                        .suggests((command, builder) -> SharedSuggestionProvider.suggest(ConfigSources.modIds(), builder))
                        .executes(command -> {
                            String modId = StringArgumentType.getString(command, "mod");
                            if (ConfigSources.forMod(modId).isEmpty()) {
                                error.accept(command.getSource(), Component.translatableWithFallback("glazedmenu.command.no_config", "No such config"));
                                return 0;
                            }
                            GlazedClient.openLater(() -> ConfigScreens.create(null, modId));
                            return 1;
                        })
                        .then(RequiredArgumentBuilder.<S, String>argument("category", StringArgumentType.greedyString())
                                .suggests((command, builder) -> SharedSuggestionProvider.suggest(
                                        CategoryPopup.paths(StringArgumentType.getString(command, "mod")), builder))
                                .executes(command -> {
                                    String modId = StringArgumentType.getString(command, "mod");
                                    String category = StringArgumentType.getString(command, "category");
                                    if (CategoryPopup.create(null, modId, category) == null) {
                                        error.accept(command.getSource(), Component.translatableWithFallback("glazedmenu.command.no_category",
                                                "No such category: %s", category));
                                        return 0;
                                    }
                                    GlazedClient.openLater(() -> ConfigScreens.categoryPopup(null, modId, category));
                                    return 1;
                                })));
    }
}
