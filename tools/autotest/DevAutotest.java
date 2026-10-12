package net.ixdarklord.glazedmenu.internal.core;

import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.internal.gui.ConfigSelectScreen;
import net.ixdarklord.glazedmenu.internal.modlist.GlazedModsScreen;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

// Glazed Menu's client smoke test, copied into a version's sources by tools/autotest/run.py (never committed there) and
// run with -Dglazedmenu.autotest=true. From the title screen it presses the Mods button and checks Glazed Menu's mod
// list opens, then opens the config list, a config, a popup, a search (changing a setting in its results) and the key
// binds, drawing each for a few frames, and quits. Each check
// logs "AUTOTEST PASS|FAIL <name>"; the last line is "AUTOTEST RESULT passed=<n> failed=<n>".
//
// One file serves every Minecraft version: the calls that differ between versions (where the open screen lives, how a
// button is pressed, how a screenshot is taken) go through reflection.
public final class DevAutotest {
    // Ticks between steps: enough for a screen to be laid out and drawn a few times.
    private static final int STEP_TICKS = 6;
    private static final Set<String> MODS_BUTTONS = Set.of("fml.menu.mods", "modmenu.title", "glazedmenu.mods.button");

    private static int ticks = -1;
    private static int step;
    private static int passed;
    private static int failed;

    private DevAutotest() {}

    public static void tick(Minecraft minecraft) {
        if (!Boolean.getBoolean("glazedmenu.autotest")) return;
        if (ticks < 0) {
            if (!(screen(minecraft) instanceof TitleScreen)) return;
            ticks = 0;
        }
        if (++ticks % STEP_TICKS != 0) return;
        try {
            switch (step++) {
                case 0 -> pressModsButton(minecraft);
                case 1 -> {
                    check("mods_button_opens_glazed_list", screen(minecraft) instanceof GlazedModsScreen, describe(screen(minecraft)));
                    shot(minecraft, "a_mods");
                }
                case 2 -> setScreen(minecraft, new ConfigSelectScreen(null, null));
                case 3 -> {
                    check("config_list_opens", screen(minecraft) instanceof ConfigSelectScreen, describe(screen(minecraft)));
                    shot(minecraft, "b_configs");
                }
                case 4 -> setScreen(minecraft, ConfigScreens.create(null, ownConfig()));
                case 5 -> {
                    check("config_screen_opens", describe(screen(minecraft)).endsWith(".ConfigScreen"), describe(screen(minecraft)));
                    shot(minecraft, "c_config");
                }
                case 6 -> setScreen(minecraft, ConfigScreens.categoryPopup(screen(minecraft), ownConfig(), ""));
                case 7 -> {
                    check("category_popup_opens", describe(screen(minecraft)).endsWith(".CategoryPopup"), describe(screen(minecraft)));
                    shot(minecraft, "d_popup");
                }
                case 8 -> {
                    Screen search = searchScreen("transitions");
                    if (search == null) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP search (this version has none)");
                        step = 11;
                    } else {
                        setScreen(minecraft, search);
                    }
                }
                case 9 -> {
                    AbstractWidget editor = firstResultEditor(screen(minecraft));
                    check("search_lists_editable_settings", editor != null, describe(screen(minecraft)));
                    if (editor != null) press(editor);
                }
                case 10 -> {
                    check("search_edit_is_pending", hasVisibleButton(screen(minecraft), "glazedmenu.button.save_short"), describe(screen(minecraft)));
                    shot(minecraft, "e_search");
                }
                case 11 -> {
                    Screen keys = gameKeyBindsScreen(minecraft);
                    if (keys == null) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP key binds (this version has no screen for them)");
                        finish(minecraft);
                    } else {
                        setScreen(minecraft, keys);
                    }
                }
                case 12 -> {
                    check("key_binds_open_glazed_screen", describe(screen(minecraft)).endsWith(".GlazedKeysScreen"), describe(screen(minecraft)));
                    shot(minecraft, "f_keys");
                    // Jump's key button, then K: the key bind takes it.
                    AbstractWidget key = rowWidget(screen(minecraft), "KeyRow", 0);
                    check("key_binds_list_keys", key != null, describe(screen(minecraft)));
                    if (key == null) {
                        finish(minecraft);
                    } else {
                        press(key);
                        pressKey(screen(minecraft), 75);
                    }
                }
                case 13 -> {
                    check("key_bind_takes_pressed_key", !minecraft.options.keyJump.isDefault(), minecraft.options.keyJump.saveString());
                    shot(minecraft, "g_key_changed");
                    AbstractWidget reset = rowWidget(screen(minecraft), "KeyRow", 1);
                    if (reset != null) press(reset);
                }
                case 14 -> {
                    check("key_bind_resets", minecraft.options.keyJump.isDefault(), minecraft.options.keyJump.saveString());
                    // "=space" lists what's bound to exactly that key: Jump alone.
                    search(screen(minecraft), "=space");
                }
                case 15 -> {
                    check("key_search_by_key", countRows(screen(minecraft), "KeyRow") == 1, countRows(screen(minecraft), "KeyRow") + " rows");
                    search(screen(minecraft), "@minecraft");
                }
                case 16 -> {
                    check("key_search_by_mod", countRows(screen(minecraft), "KeyRow") > 20, countRows(screen(minecraft), "KeyRow") + " rows");
                    shot(minecraft, "h_keys_by_mod");
                }
                case 17 -> setScreen(minecraft, new GlazedModsScreen(null));
                case 18 -> {
                    // The star and the eye in the chosen mod's details, where this version's mod list has them.
                    markedMod = String.valueOf(markedModId(screen(minecraft)));
                    AbstractWidget star = markButton(screen(minecraft), "favoriteButton");
                    if (star == null) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP favorites (this version has none)");
                        finish(minecraft);
                    } else {
                        press(star);
                    }
                }
                case 19 -> {
                    check("mod_becomes_favorite", isMarked("isFavorite", markedMod), markedMod);
                    shot(minecraft, "i_favorite");
                    press(markButton(screen(minecraft), "favoriteButton"));
                }
                case 20 -> {
                    check("mod_favorite_removed", !isMarked("isFavorite", markedMod), markedMod);
                    press(markButton(screen(minecraft), "hideButton"));
                }
                case 21 -> {
                    check("mod_becomes_hidden", isMarked("isHidden", markedMod) && !markedMod.equals(String.valueOf(markedModId(screen(minecraft)))), markedMod);
                    shot(minecraft, "j_hidden");
                    Class.forName("net.ixdarklord.glazedmenu.internal.modlist.ModMarks").getMethod("toggleHidden", String.class).invoke(null, markedMod);
                }
                case 22 -> {
                    // The settings that aren't at their defaults, where this version lists them.
                    Screen changed;
                    try {
                        changed = (Screen) ConfigSelectScreen.class.getMethod("changed", Screen.class).invoke(null, (Object) null);
                    } catch (NoSuchMethodException e) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP changed settings (this version has no list of them)");
                        finish(minecraft);
                        return;
                    }
                    setScreen(minecraft, changed);
                }
                case 23 -> {
                    check("changed_settings_list_opens", hasVisibleButton(screen(minecraft), "glazedmenu.changed.copy"),
                            countRows(screen(minecraft), "ValueEntry") + " changed settings");
                    shot(minecraft, "k_changed");
                    // A profile of what's listed, where this version has profiles: saved, there, and deleted again.
                    Class<?> profiles;
                    try {
                        profiles = Class.forName("net.ixdarklord.glazedmenu.internal.config.ConfigProfiles");
                    } catch (ClassNotFoundException e) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP profiles (this version has none)");
                        finish(minecraft);
                        return;
                    }
                    java.lang.reflect.Field listed = screen(minecraft).getClass().getDeclaredField("listed");
                    listed.setAccessible(true);
                    List<Object> values = new java.util.ArrayList<>();
                    ((java.util.Map<?, ?>) listed.get(screen(minecraft))).values().forEach(group -> values.addAll((List<?>) group));
                    profiles.getMethod("save", String.class, java.util.Collection.class).invoke(null, "Autotest", values);
                    int size = (int) profiles.getMethod("size", String.class).invoke(null, "Autotest");
                    Object resolved = profiles.getMethod("resolve", String.class).invoke(null, "Autotest");
                    check("profile_saves_listed_settings", size == values.size() && size > 0 && !((java.util.Map<?, ?>) resolved).isEmpty(), size + " settings");
                    press(buttonNamed(screen(minecraft), "glazedmenu.profiles.title"));
                }
                case 24 -> {
                    check("profiles_popup_opens", describe(screen(minecraft)).endsWith(".ProfilesPopup"), describe(screen(minecraft)));
                    shot(minecraft, "l_profiles");
                    Class.forName("net.ixdarklord.glazedmenu.internal.config.ConfigProfiles").getMethod("delete", String.class).invoke(null, "Autotest");
                }
                case 25 -> {
                    // The game's options as a config, where this version reads them.
                    if (ConfigSources.forMod("minecraft").isEmpty()) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP game options (this version doesn't list them)");
                        finish(minecraft);
                    } else {
                        Config game = ConfigSources.forMod("minecraft").get(0);
                        long settings = game.values().count();
                        check("game_options_are_listed", settings > 60 && game.find("general.fov").isPresent() && game.values().anyMatch(value -> value.path().endsWith(".renderDistance")),
                                settings + " settings");
                        setScreen(minecraft, ConfigScreens.create(null, game));
                    }
                }
                case 26 -> {
                    check("game_options_screen_opens", describe(screen(minecraft)).endsWith(".ConfigScreen"), describe(screen(minecraft)));
                    shot(minecraft, "m_game_options");
                    setScreen(minecraft, searchScreen("fov"));
                }
                case 27 -> {
                    // Changing FOV in the search and saving sets the game's own option; then it's put back.
                    int before = minecraft.options.fov().get();
                    check("search_finds_game_option", countRows(screen(minecraft), "ValueEntry") >= 1, countRows(screen(minecraft), "ValueEntry") + " rows");
                    shot(minecraft, "n_search_fov");
                    Config game = ConfigSources.forMod("minecraft").get(0);
                    setValue(game, "general.fov", before == 90 ? 80 : 90);
                    check("game_option_is_set", minecraft.options.fov().get() == (before == 90 ? 80 : 90), "fov " + minecraft.options.fov().get());
                    setValue(game, "general.fov", before);
                }
                case 28 -> {
                    // The game's controls screen, which opens as the game's options at Controls where this version does that.
                    Screen controls;
                    try {
                        controls = (Screen) Class.forName("net.minecraft.client.gui.screens.options.controls.ControlsScreen")
                                .getConstructor(Screen.class, minecraft.options.getClass()).newInstance(null, minecraft.options);
                        Class.forName("net.ixdarklord.glazedmenu.internal.keys.GameControlsScreen");
                    } catch (ClassNotFoundException | NoSuchMethodException e) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP controls (this version keeps the game's screen)");
                        finish(minecraft);
                        return;
                    }
                    setScreen(minecraft, controls);
                }
                case 29 -> {
                    check("controls_open_glazed_page", describe(screen(minecraft)).endsWith(".GameControlsScreen")
                            && hasVisibleButton(screen(minecraft), "glazedmenu.controls.view.list"), describe(screen(minecraft)));
                    shot(minecraft, "o_controls");
                    press(buttonNamed(screen(minecraft), "glazedmenu.controls.view.list"));
                }
                case 30 -> {
                }
                case 31 -> {
                    check("controls_lead_to_key_binds", describe(screen(minecraft)).endsWith(".GlazedKeysScreen"), describe(screen(minecraft)));
                    // The game's options screen, which opens as the hub where this version has one.
                    try {
                        Class.forName("net.ixdarklord.glazedmenu.internal.game.GameOptionsScreen");
                    } catch (ClassNotFoundException e) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP options hub (this version keeps the game's screen)");
                        finish(minecraft);
                        return;
                    }
                    setScreen(minecraft, (Screen) Class.forName("net.minecraft.client.gui.screens.options.OptionsScreen")
                            .getConstructor(Screen.class, minecraft.options.getClass(), boolean.class).newInstance(null, minecraft.options, false));
                }
                case 32 -> {
                    check("options_open_glazed_hub", describe(screen(minecraft)).endsWith(".GameOptionsScreen"), describe(screen(minecraft)));
                    shot(minecraft, "p_options_hub");
                }
                case 33 -> {
                    // A new world's game rules, which open in the config screen where this version does that.
                    Class<?> rulesClass;
                    Screen vanilla;
                    try {
                        Class.forName("net.ixdarklord.glazedmenu.internal.game.GameRulesSource");
                        rulesClass = Class.forName("net.minecraft.world.level.gamerules.GameRules");
                        Object flags = Class.forName("net.minecraft.world.flag.FeatureFlags").getField("DEFAULT_FLAGS").get(null);
                        gameRules = rulesClass.getConstructor(flags.getClass()).newInstance(flags);
                        java.util.function.Consumer<java.util.Optional<?>> exit = result -> {
                            gameRulesDone = result.isPresent();
                            try {
                                setScreen(minecraft, new TitleScreen());
                            } catch (ReflectiveOperationException e) {
                                throw new IllegalStateException(e);
                            }
                        };
                        vanilla = (Screen) Class.forName("net.minecraft.client.gui.screens.worldselection.WorldCreationGameRulesScreen")
                                .getConstructor(rulesClass, java.util.function.Consumer.class).newInstance(gameRules, exit);
                    } catch (ClassNotFoundException e) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP game rules (this version keeps the game's screen)");
                        finish(minecraft);
                        return;
                    }
                    setScreen(minecraft, vanilla);
                }
                case 34 -> {
                    check("game_rules_open_config_screen", describe(screen(minecraft)).endsWith(".ConfigScreen") && countRows(screen(minecraft), "ValueEntry") > 3,
                            describe(screen(minecraft)) + ", " + countRows(screen(minecraft), "ValueEntry") + " rules shown");
                    shot(minecraft, "q_game_rules");
                    // The first rule's switch, then Done: the rules go back to whoever asked, changed.
                    AbstractWidget editor = rowWidget(screen(minecraft), "ValueEntry", 0);
                    if (editor != null) press(editor);
                    press(buttonNamed(screen(minecraft), "gui.done"));
                }
                case 35 -> {
                    check("game_rules_are_handed_back", gameRulesDone, "done=" + gameRulesDone);
                    // The game's language screen, which opens as Glazed Menu's where this version has one.
                    try {
                        Class.forName("net.ixdarklord.glazedmenu.internal.game.GameLanguageScreen");
                    } catch (ClassNotFoundException e) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP languages (this version keeps the game's screen)");
                        finish(minecraft);
                        return;
                    }
                    Object languages = Minecraft.class.getMethod("getLanguageManager").invoke(minecraft);
                    setScreen(minecraft, (Screen) Class.forName("net.minecraft.client.gui.screens.options.LanguageSelectScreen")
                            .getConstructor(Screen.class, minecraft.options.getClass(), languages.getClass()).newInstance(null, minecraft.options, languages));
                }
                case 36 -> {
                    check("languages_open_glazed_screen", describe(screen(minecraft)).endsWith(".GameLanguageScreen")
                            && countRows(screen(minecraft), "LanguageRow") > 20, countRows(screen(minecraft), "LanguageRow") + " languages");
                    shot(minecraft, "r_languages");
                    search(screen(minecraft), "en_gb");
                }
                case 37 -> {
                    check("language_search_by_code", countRows(screen(minecraft), "LanguageRow") == 1, countRows(screen(minecraft), "LanguageRow") + " languages");
                    // The game's pack screen (over the resource packs), which opens as Glazed Menu's where this version has one.
                    Object repository;
                    Class<?> packScreen;
                    try {
                        Class.forName("net.ixdarklord.glazedmenu.internal.game.GamePacksScreen");
                        packScreen = Class.forName("net.minecraft.client.gui.screens.packs.PackSelectionScreen");
                        repository = Minecraft.class.getMethod("getResourcePackRepository").invoke(minecraft);
                    } catch (ClassNotFoundException e) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP packs (this version keeps the game's screen)");
                        finish(minecraft);
                        return;
                    }
                    java.util.function.Consumer<Object> output = chosen -> {
                        packsApplied = true;
                        try {
                            setScreen(minecraft, new TitleScreen());
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    };
                    setScreen(minecraft, (Screen) packScreen.getConstructors()[0].newInstance(repository, output,
                            Minecraft.class.getMethod("getResourcePackDirectory").invoke(minecraft), net.minecraft.network.chat.Component.literal("Resource Packs")));
                }
                case 38 -> {
                    check("packs_open_glazed_screen", describe(screen(minecraft)).endsWith(".GamePacksScreen") && countRows(screen(minecraft), "PackRow") >= 1,
                            countRows(screen(minecraft), "PackRow") + " packs");
                    shot(minecraft, "s_packs");
                    screen(minecraft).onClose();
                }
                case 39 -> {
                    check("packs_choice_is_applied", packsApplied, "applied=" + packsApplied);
                    // Iris's shader packs screen, which opens as Glazed Menu's where Iris is here and this version has one.
                    Screen iris;
                    try {
                        Class.forName("net.ixdarklord.glazedmenu.internal.game.GameShadersScreen");
                        iris = (Screen) Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen").getConstructor(Screen.class).newInstance((Object) null);
                    } catch (ClassNotFoundException e) {
                        GlazedMenu.LOGGER.info("AUTOTEST SKIP shaders (no Iris here, or no screen for it in this version)");
                        finish(minecraft);
                        return;
                    }
                    setScreen(minecraft, iris);
                }
                case 40 -> {
                    check("shaders_open_glazed_screen", describe(screen(minecraft)).endsWith(".GameShadersScreen"), describe(screen(minecraft)));
                    shot(minecraft, "t_shaders");
                    // The pack's settings are Iris's own screen, which opens as itself.
                    press(buttonNamed(screen(minecraft), "glazedmenu.shaders.settings"));
                }
                case 41 -> {
                }
                case 42 -> {
                    String shown = describe(screen(minecraft));
                    check("shader_settings_stay_iris", shown.endsWith(".ShaderPackScreen") || shown.endsWith(".GameShadersScreen"), shown);
                    // The options' Video tab, with Sodium's and Iris's cards leading it.
                    setScreen(minecraft, (Screen) Class.forName("net.minecraft.client.gui.screens.options.OptionsScreen")
                            .getConstructor(Screen.class, minecraft.options.getClass(), boolean.class).newInstance(null, minecraft.options, false));
                }
                case 43 -> {
                    java.lang.reflect.Method select = screen(minecraft).getClass().getDeclaredMethod("selectTab", int.class);
                    select.setAccessible(true);
                    select.invoke(screen(minecraft), 2);
                }
                case 44 -> shot(minecraft, "u_video_tab");
                default -> finish(minecraft);
            }
        } catch (Throwable e) {
            GlazedMenu.LOGGER.error("AUTOTEST FAIL step_{} threw", step - 1, e);
            failed++;
            finish(minecraft);
        }
    }

    // The title screen's Mods button (Glazed Menu's own, Mod Menu's or the loader's), pressed as a player would.
    private static void pressModsButton(Minecraft minecraft) throws ReflectiveOperationException {
        Screen title = screen(minecraft);
        AbstractWidget button = null;
        for (GuiEventListener child : title.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            String key = widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
            if (key != null && MODS_BUTTONS.contains(key) || widget.getMessage().getString().startsWith("Mods")) button = widget;
        }
        check("title_screen_has_mods_button", button != null, describe(title));
        if (button == null) {
            // Still try the rest: open the list directly.
            setScreen(minecraft, new GlazedModsScreen(title));
            return;
        }
        GlazedMenu.LOGGER.info("AUTOTEST pressing \"{}\" ({})", button.getMessage().getString(), button.getClass().getName());
        press(button);
    }

    // The search across every mod's configs, on the versions that have it.
    private static Screen searchScreen(String query) throws ReflectiveOperationException {
        try {
            return (Screen) ConfigSelectScreen.class.getMethod("search", Screen.class, String.class).invoke(null, null, query);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // The game's own key binds screen (which Glazed Menu's opens in place of), on the versions Glazed Menu has one for.
    private static Screen gameKeyBindsScreen(Minecraft minecraft) throws ReflectiveOperationException {
        try {
            Class.forName("net.ixdarklord.glazedmenu.internal.keys.GlazedKeysScreen");
        } catch (ClassNotFoundException e) {
            return null;
        }
        Class<?> screen;
        try {
            screen = Class.forName("net.minecraft.client.gui.screens.options.controls.KeyBindsScreen");
        } catch (ClassNotFoundException e) {
            screen = Class.forName("net.minecraft.client.gui.screens.controls.KeyBindsScreen");
        }
        return (Screen) screen.getConstructor(Screen.class, minecraft.options.getClass()).newInstance(null, minecraft.options);
    }

    // The editor of the first setting a search lists.
    private static AbstractWidget firstResultEditor(Screen screen) {
        return rowWidget(screen, "ValueEntry", 0);
    }

    // A widget (by its place in the row) of the first row of a kind, in whichever of the screen's lists has one.
    private static AbstractWidget rowWidget(Screen screen, String rowClass, int index) {
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof ContainerEventHandler list) || child instanceof AbstractWidget widget && !widget.visible) continue;
            for (GuiEventListener row : list.children()) {
                if (!row.getClass().getSimpleName().equals(rowClass) || !(row instanceof ContainerEventHandler entry)) continue;
                List<? extends GuiEventListener> parts = entry.children();
                return index < parts.size() && parts.get(index) instanceof AbstractWidget widget ? widget : null;
            }
        }
        return null;
    }

    private static String markedMod = "";
    private static Object gameRules;
    private static boolean gameRulesDone;
    private static boolean packsApplied;

    // The mod list's star or eye button, or null where this version's has none.
    private static AbstractWidget markButton(Screen screen, String field) throws ReflectiveOperationException {
        try {
            java.lang.reflect.Field button = screen.getClass().getDeclaredField(field);
            button.setAccessible(true);
            return (AbstractWidget) button.get(screen);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    // The id of the mod chosen in the mod list.
    private static Object markedModId(Screen screen) throws ReflectiveOperationException {
        java.lang.reflect.Field selected = screen.getClass().getDeclaredField("selected");
        selected.setAccessible(true);
        Object mod = selected.get(screen);
        return mod == null ? null : mod.getClass().getMethod("id").invoke(mod);
    }

    private static boolean isMarked(String check, String modId) throws ReflectiveOperationException {
        return (boolean) Class.forName("net.ixdarklord.glazedmenu.internal.modlist.ModMarks").getMethod(check, String.class).invoke(null, modId);
    }

    private static int countRows(Screen screen, String rowClass) {
        int count = 0;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof ContainerEventHandler list)) continue;
            for (GuiEventListener row : list.children()) {
                if (row.getClass().getSimpleName().equals(rowClass)) count++;
            }
        }
        return count;
    }

    // Types into the screen's search box.
    private static void search(Screen screen, String query) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box) box.setValue(query);
        }
    }

    // A key press, as the screen gets one (only on the versions whose screens take a KeyEvent).
    private static void pressKey(Screen screen, int key) throws ReflectiveOperationException {
        Class<?> event = Class.forName("net.minecraft.client.input.KeyEvent");
        Screen.class.getMethod("keyPressed", event).invoke(screen, event.getConstructor(int.class, int.class, int.class).newInstance(key, 0, 0));
    }

    private static AbstractWidget buttonNamed(Screen screen, String key) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.getMessage().getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals(key)) return widget;
        }
        throw new IllegalStateException("No button " + key + " on " + describe(screen));
    }

    private static boolean hasVisibleButton(Screen screen, String key) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible
                    && widget.getMessage().getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)) return true;
        }
        return false;
    }

    // Sets and saves one value of a config, as its screen's Save does.
    private static void setValue(Config config, String path, Object value) {
        ConfigSources.apply(config, java.util.Map.of(config.find(path).orElseThrow(), value));
    }

    private static Config ownConfig() {
        return ConfigSources.forMod(GlazedMenu.MOD_ID).get(0);
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) passed++;
        else failed++;
        GlazedMenu.LOGGER.info("AUTOTEST {} {} ({})", ok ? "PASS" : "FAIL", name, detail);
    }

    private static void finish(Minecraft minecraft) {
        GlazedMenu.LOGGER.info("AUTOTEST RESULT passed={} failed={}", passed, failed);
        minecraft.stop();
        // Some dev clients (26.2 and later) don't exit after a stop like this: leftover worker threads keep them open
        // until the shutdown watchdog reports a crash, or for good. The test is over, so the process ends either way.
        Thread exit = new Thread(() -> {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "Glazed Menu autotest exit");
        exit.setDaemon(true);
        exit.start();
    }

    private static String describe(Object object) {
        return object == null ? "null" : object.getClass().getName();
    }

    // --- What differs between Minecraft versions ---

    // The open screen: Minecraft.gui.screen() since 26.2, the Minecraft.screen field before.
    private static Screen screen(Minecraft minecraft) {
        try {
            Object gui = Minecraft.class.getField("gui").get(minecraft);
            return (Screen) gui.getClass().getMethod("screen").invoke(gui);
        } catch (ReflectiveOperationException e) {
            try {
                return (Screen) Minecraft.class.getField("screen").get(minecraft);
            } catch (ReflectiveOperationException inner) {
                throw new IllegalStateException(inner);
            }
        }
    }

    private static void setScreen(Minecraft minecraft, Screen screen) throws ReflectiveOperationException {
        try {
            Object gui = Minecraft.class.getField("gui").get(minecraft);
            gui.getClass().getMethod("setScreen", Screen.class).invoke(gui, screen);
        } catch (NoSuchMethodException e) {
            Minecraft.class.getMethod("setScreen", Screen.class).invoke(minecraft, screen);
        }
    }

    // A button's press: onPress() before 26.1, onPress(input) since (given an Enter key press).
    private static void press(AbstractWidget button) throws ReflectiveOperationException {
        for (Method method : button.getClass().getMethods()) {
            if (!method.getName().equals("onPress")) continue;
            if (method.getParameterCount() == 0) {
                method.invoke(button);
                return;
            }
            if (method.getParameterCount() == 1) {
                Constructor<?> key = Class.forName("net.minecraft.client.input.KeyEvent").getConstructor(int.class, int.class, int.class);
                method.invoke(button, key.newInstance(257, 0, 0));
                return;
            }
        }
        throw new NoSuchMethodException("onPress on " + button.getClass().getName());
    }

    // Something of a type reachable from an object: what one of its no-argument getters returns or one of its fields
    // holds, or (a level further) one of those objects' own. Finds the main render target wherever a version keeps it.
    private static Object find(Object root, Class<?> type, int depth) {
        for (Method method : root.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers()) || !type.isAssignableFrom(method.getReturnType())) continue;
            try {
                Object value = method.invoke(root);
                if (value != null) return value;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        for (java.lang.reflect.Field field : root.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            try {
                Object value = field.get(root);
                if (value == null) continue;
                if (type.isInstance(value)) return value;
                if (depth > 1 && field.getType().getName().startsWith("net.minecraft.client.")) {
                    Object deeper = find(value, type, depth - 1);
                    if (deeper != null) return deeper;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    // Screenshot.grab, whose arguments changed over the versions; a failed screenshot fails nothing.
    private static void shot(Minecraft minecraft, String name) {
        try {
            Class<?> screenshots = Class.forName("net.minecraft.client.Screenshot");
            for (Method method : screenshots.getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!method.getName().equals("grab") || !Modifier.isStatic(method.getModifiers()) || types.length < 4
                        || types[0] != File.class || types[1] != String.class) continue;
                Object[] arguments = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    if (types[i] == File.class) arguments[i] = minecraft.gameDirectory;
                    else if (types[i] == String.class) arguments[i] = "autotest_" + name + ".png";
                    else if (types[i] == int.class) arguments[i] = 1;
                    else if (types[i] == Consumer.class) arguments[i] = (Consumer<Object>) message -> {};
                    else arguments[i] = find(minecraft, types[i], 2);
                }
                method.invoke(null, arguments);
                return;
            }
            GlazedMenu.LOGGER.warn("AUTOTEST no screenshot method for {}", name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            GlazedMenu.LOGGER.warn("AUTOTEST screenshot {} failed: {}", name, e.toString());
        }
    }
}
