package net.ixdarklord.glazedmenu.internal.core;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.internal.gui.CategoryPopup;
import net.ixdarklord.glazedmenu.internal.modlist.GlazedModsScreen;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

// /glazedmenu: every mod's configs; /glazedmenu <mod>: one mod's; /glazedmenu <mod> <config>/<category>: one category in a
// small window, e.g. "client/example_category"; /glazedmenu mods: the mod list.
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
