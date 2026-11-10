package net.ixdarklord.glazedmenu.internal.gui.setup;

import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.GuiFade;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * The setup, over the title screen the first time the game runs with Glazed Menu: a welcome, the look (color scheme,
 * dark or light), a page for each of the screens a player sees all the time, with a picture of it in Glazed Menu's look
 * and in the game's own to choose between (and its layouts, for the title and pause screens), and last every screen
 * Glazed Menu can replace, each a small picture with its switch. A modpack can ship it turned off ({@code firstRunSetup} in
 * {@code glazedmenu.json}) with its own choices; {@code /glazedmenu setup} opens it again.
 *
 * <p>The pictures are the screens themselves, drawn live and scaled down (LivePreview), made the first time they're
 * shown and let go of when the setup closes; a screen that needs a world is drawn from boxes instead (SetupPreview).
 * The look's picture is the mod list, which follows the colors chosen. The animations (pages sliding in, a picture
 * wiping from one choice to the other) are a few
 * eased numbers per frame; with animations turned off, all of it holds still.
 */
public final class SetupScreen extends StyledScreen {
    private static final int KEY_ENTER = 257;
    private static final int KEY_NUMPAD_ENTER = 335;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;
    private static final int KEY_DOWN = 264;
    private static final int KEY_UP = 265;

    private static final float STEP_MILLIS = 300;
    private static final float WIPE_MILLIS = 560;
    // The welcome's showcase: each screen as the game draws it, then wiped into Glazed Menu's look.
    private static final float SHOWCASE_MILLIS = 4200;
    private static final float SHOWCASE_WIPE_FROM = 0.3F;
    private static final float SHOWCASE_WIPE_TO = 0.5F;
    private static final int RAIL_HEIGHT = 3;
    private static final int OPTION_GAP = 4;
    private static final int SWATCH = 18;
    // The pictures' shape: 16 by 9, as most windows are.
    private static final float PHOTO_RATIO = 16 / 9F;
    private static final int CARD_GAP = 6;
    private static final int CARD_LABEL = 17;
    // Looked for once each time the game runs, at the first title screen.
    private static boolean checked;

    private final @Nullable Screen parent;
    private final List<Page> pages = new ArrayList<>();
    private final List<Page> screens = new ArrayList<>();
    private final List<Page> featured = new ArrayList<>();
    private final LivePreview live = new LivePreview(this);
    private final long openedAt = System.nanoTime();
    private final float[] hover = new float[64];
    private int index;
    private int direction = 1;
    private long stepAt = System.nanoTime();
    private Start start = Start.WALK;
    // Whether the welcome's choice set every screen at once: the look is still shown, then the last page.
    private boolean quick;
    private int wipeFrom = -1;
    private int wipeTo;
    private long wipeAt;
    // Each card's own wipe on the last page, when its switch flips.
    private int[] cardFrom = new int[0];
    private long[] cardWipeAt = new long[0];

    // Laid out in init, for drawing and clicks alike.
    private List<FormattedCharSequence> description = List.of();
    private Component hint = Component.empty();
    private Component count = Component.empty();
    private int textLeft;
    private int textWidth;
    private int columnTop;
    private int optionsTop;
    // The text column scrolls where its choices run past the panel's foot (a short window): where it's laid out unscrolled,
    // how far it's scrolled and can be, and the part of it that shows.
    private int baseColumnTop;
    private int baseOptionsTop;
    private double columnScroll;
    private int columnMax;
    private int columnClipTop;
    private int columnClipBottom;
    private final List<FlatButton> columnButtons = new ArrayList<>();
    private int optionHeight;
    private boolean optionNotes;
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private int areaX;
    private int areaWidth;
    private int gridTop;
    private int gridHeight;
    private int cardColumns;
    private int cardWidth;
    private int cardHeight;
    private int photoHeight;
    private int contentHeight;
    private double scroll;

    public SetupScreen(@Nullable Screen parent) {
        super(Component.translatableWithFallback("glazedmenu.setup.title", "Glazed Menu Setup"), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.parent = parent;
        this.buildPages();
        this.cardFrom = new int[this.screens.size()];
        this.cardWipeAt = new long[this.screens.size()];
    }

    /** Every client tick: opens the setup over the first title screen, while it's turned on. */
    public static void tick(Minecraft minecraft) {
        if (checked || !(minecraft.gui.screen() instanceof TitleScreen) || minecraft.gui.overlay() != null) return;
        checked = true;
        // Not in the dev client's smoke test, which starts from the title screen.
        if (GlazedSettings.firstRunSetup() && !Boolean.getBoolean("glazedmenu.autotest")) minecraft.gui.setScreen(new SetupScreen(minecraft.gui.screen()));
    }

    // --- The pages ---

    private enum Kind {
        WELCOME,
        LOOK,
        SCREEN,
        DONE
    }

    /** How the welcome starts: through the screens, or with all of them set at once. */
    private enum Start {
        WALK,
        ALL,
        NONE
    }

    /** A choice on a page; {@code variant} is the picture it shows (0 the game's own, 1 and on Glazed Menu's layouts). */
    private record Option(Component name, @Nullable Component note, int variant, BooleanSupplier chosen, Runnable choose) {}

    /** Draws a screen from boxes, for one that can't be drawn live (it needs a world). */
    @FunctionalInterface
    private interface Preview {
        void draw(SetupPreview.Mock mock, int variant);
    }

    /** A page, or (for the screens) a card on the last page too; {@code shot} names its screen for the live preview. */
    private static final class Page {
        final Kind kind;
        final GlazedSettings.@Nullable Switch toggle;
        final ConfigIcons.Icon icon;
        final Component group;
        final Component title;
        final Component boldTitle;
        final Component boldGroup;
        final Component description;
        final List<Option> options;
        final Preview preview;
        final @Nullable String shot;

        Page(Kind kind, GlazedSettings.@Nullable Switch toggle, ConfigIcons.Icon icon, Component group, Component title, Component description,
             List<Option> options, Preview preview, @Nullable String shot) {
            this.kind = kind;
            this.toggle = toggle;
            this.icon = icon;
            this.group = group;
            this.title = title;
            this.boldTitle = title.copy().withStyle(ChatFormatting.BOLD);
            this.boldGroup = group.copy().withStyle(ChatFormatting.BOLD);
            this.description = description;
            this.options = options;
            this.preview = preview;
            this.shot = shot;
        }
    }

    private static Component text(String key, String fallback) {
        return Component.translatableWithFallback("glazedmenu.setup." + key, fallback);
    }

    private void buildPages() {
        this.pages.add(new Page(Kind.WELCOME, null, ConfigIcons.STAR, text("group.welcome", "Welcome to"),
                text("welcome.title", "Welcome to Glazed Menu"),
                text("welcome.description", "Glazed Menu gives your mods a glassy mod list and config screens, and can redraw many of the game's own screens to match. Let's choose which: first the screens you see all the time, each with a preview, then all of them at a glance."),
                List.of(new Option(text("welcome.walk", "Choose screen by screen"), text("welcome.walk.note", "A short look at the main ones, with previews"), 1,
                                () -> this.start == Start.WALK, () -> this.start = Start.WALK),
                        new Option(text("welcome.all", "Use all of Glazed Menu's screens"), text("welcome.all.note", "Every screen in Glazed Menu's look"), 1,
                                () -> this.start == Start.ALL, () -> this.start = Start.ALL),
                        new Option(text("welcome.none", "Keep the game's own screens"), text("welcome.none.note", "Only the mod list and config screens are Glazed Menu's"), 0,
                                () -> this.start == Start.NONE, () -> this.start = Start.NONE)),
                (mock, variant) -> {}, null));
        this.pages.add(new Page(Kind.LOOK, null, ConfigIcons.SUN, text("group.look", "Look"), text("look.title", "Colors"),
                text("look.description", "The colors of the mod list, every config screen and Glazed Menu's screens. Pick a scheme, dark or light, and whether the waves in the panels move; the preview follows."),
                List.of(), (mock, variant) -> SetupPreview.configScreen(mock), null));

        Component always = text("group.always", "Seen all the time");
        Component worlds = text("group.worlds", "Worlds");
        Component servers = text("group.servers", "Servers");
        Component playing = text("group.playing", "Playing");
        Component options = text("group.options", "Options");

        // The screens seen all the time, each with a page of its own.
        this.screen("replaceTitle", "title", true, ConfigIcons.STAR, always, "Title screen",
                "The first screen of the game: Continue straight into the world played last, and the rest of the menu around the panorama.",
                (mock, variant) -> {
                    switch (variant) {
                        case 0 -> SetupPreview.vanillaTitle(mock);
                        case 2 -> SetupPreview.titleSidebar(mock);
                        default -> SetupPreview.titleShelf(mock);
                    }
                },
                layout(text("layout.shelf", "Shelf"), text("layout.shelf.note", "Cards along the bottom, the logo above"),
                        () -> GlazedSettings.titleLayout() == GlazedSettings.TitleLayout.SHELF, () -> GlazedSettings.setTitleLayout(GlazedSettings.TitleLayout.SHELF)),
                layout(text("layout.sidebar", "Sidebar"), text("layout.sidebar.title.note", "The menu as a list down the left side"),
                        () -> GlazedSettings.titleLayout() == GlazedSettings.TitleLayout.SIDEBAR, () -> GlazedSettings.setTitleLayout(GlazedSettings.TitleLayout.SIDEBAR)));
        this.screen("replacePause", "pause", true, ConfigIcons.PAUSE, always, "Pause menu",
                "Back to Game, the screens a pause leads to as tiles, a few quick settings, and Save and Quit set apart.",
                (mock, variant) -> {
                    switch (variant) {
                        case 0 -> SetupPreview.vanillaPause(mock);
                        case 2 -> SetupPreview.pauseCards(mock);
                        case 3 -> SetupPreview.pauseSidebar(mock);
                        default -> SetupPreview.pauseDock(mock);
                    }
                },
                layout(text("layout.dock", "Dock"), text("layout.dock.note", "A bar at the top, tiles along the bottom"),
                        () -> GlazedSettings.pauseLayout() == GlazedSettings.PauseLayout.DOCK, () -> GlazedSettings.setPauseLayout(GlazedSettings.PauseLayout.DOCK)),
                layout(text("layout.cards", "Cards"), text("layout.cards.note", "A frame of cards in the middle"),
                        () -> GlazedSettings.pauseLayout() == GlazedSettings.PauseLayout.CARDS, () -> GlazedSettings.setPauseLayout(GlazedSettings.PauseLayout.CARDS)),
                layout(text("layout.sidebar", "Sidebar"), text("layout.sidebar.pause.note", "A list down the left, the world beside it"),
                        () -> GlazedSettings.pauseLayout() == GlazedSettings.PauseLayout.SIDEBAR, () -> GlazedSettings.setPauseLayout(GlazedSettings.PauseLayout.SIDEBAR)));
        this.screen("replaceModList", "mod_list", true, ConfigIcons.LIST, always, "Mod list",
                "Where Mod Menu's or the loader's list of mods would open: grid or list, filters, search, each mod's details and settings, and update notices.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaKeyList(mock);
                    else SetupPreview.modList(mock);
                });
        this.screen("replaceOptions", "options", true, ConfigIcons.SLIDERS, always, "Options",
                "The game's options as a ribbon of tabs, each tab's settings as cards to change right there. The game's own screen stays under More.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaOptions(mock);
                    else SetupPreview.options(mock);
                });
        this.screen("replaceWorlds", "worlds", true, ConfigIcons.PLAY, always, "Singleplayer",
                "Your worlds with a search and sorting, and the chosen world's picture and details beside the list.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaWorlds(mock, false);
                    else SetupPreview.worlds(mock, false);
                });
        this.screen("replaceServers", "servers", true, ConfigIcons.SERVER, always, "Multiplayer",
                "Your servers with a search, the games on your network apart from them, and the chosen server's details beside the list.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaWorlds(mock, true);
                    else SetupPreview.worlds(mock, true);
                });
        this.screen("replaceChat", "chat", true, ConfigIcons.CHAT, always, "Chat",
                "Every message on a row of its own with the sender's face, tabs for players, the system and mentions, and the line in a bar at the bottom.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaChat(mock);
                    else SetupPreview.chat(mock);
                });
        this.screen("replaceLoading", "loading", true, ConfigIcons.RESTART, always, "Loading",
                "The screens while a world or server is joined or left: the world's icon in a ring of the steps, each step named and timed.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaLoading(mock);
                    else SetupPreview.loading(mock);
                });

        // The rest: on the last page only.
        this.screen("replaceCreateWorld", "create_world", false, ConfigIcons.PLUS, worlds, "Create world",
                "The three tabs as a ribbon, the game mode as cards, and what the world will be made with beside them.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaCreateWorld(mock);
                    else SetupPreview.createWorld(mock);
                });
        this.screen("replaceEditWorld", "edit_world", false, ConfigIcons.EDIT, worlds, "Edit world",
                "The world's name, its files, backups and upkeep as cards, and the world itself beside them.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaEditWorld(mock);
                    else SetupPreview.editWorld(mock);
                });
        this.screen("replaceGameRules", "game_rules", false, ConfigIcons.CONFIGS, worlds, "Game rules",
                "A new world's game rules in Glazed Menu's config screen: categories, a search, and the rules you changed marked.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaGameRules(mock);
                    else SetupPreview.gameRules(mock);
                });
        this.screen("replaceWarnings", "warnings", false, ConfigIcons.WARNING, worlds, "World warnings",
                "The game's questions before a world opens (experimental settings, a backup first) as a popup.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaMessage(mock, 3);
                    else SetupPreview.warnings(mock);
                });
        this.screen("replaceEditServer", "edit_server", false, ConfigIcons.EDIT, servers, "Add or edit server",
                "The server's name and address beside the server itself, as it answers at the address you type.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaForm(mock, 2);
                    else SetupPreview.editServer(mock);
                });
        this.screen("replaceDirectConnect", "direct_connect", false, ConfigIcons.CONNECT, servers, "Direct connection",
                "A popup for an address, with the server at it shown as it answers, before you join.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaForm(mock, 1);
                    else SetupPreview.directConnect(mock);
                });
        this.screen("replaceDisconnected", "disconnected", false, ConfigIcons.WARNING, servers, "Disconnected",
                "What happened and why, the server as it answers now, and buttons to copy the reason and to reconnect.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaMessage(mock, 2);
                    else SetupPreview.disconnected(mock);
                });
        this.screen("replaceDeath", "death", false, ConfigIcons.SKULL, playing, "Death screen",
                "A band across the reddened world with how you died and the ways on, and your score and how long you lasted under it.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaDeath(mock);
                    else SetupPreview.death(mock);
                });
        this.screen("replaceAdvancements", "advancements", false, ConfigIcons.TROPHY, playing, "Advancements",
                "The tabs as a ribbon and the advancements as a list with their progress and a search. The game's tree stays a button away.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaAdvancements(mock);
                    else SetupPreview.advancements(mock);
                });
        this.screen("replaceStats", "stats", false, ConfigIcons.CHART, playing, "Statistics",
                "Tabs for general numbers, blocks, items and mobs, each a table sorted by the column you click, with a search.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaStats(mock);
                    else SetupPreview.stats(mock);
                });
        this.screen("replaceSocial", "social", false, ConfigIcons.PERSON, playing, "Social interactions",
                "The players with their faces and what they said last, tabs for the hidden and the blocked, and buttons to message, hide or report.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaSocial(mock);
                    else SetupPreview.social(mock);
                });
        this.screen("replaceControls", "controls", false, ConfigIcons.MOUSE, options, "Controls",
                "A map of the keyboard and mouse: keys tinted where something is bound, red where binds are in conflict.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaOptions(mock);
                    else SetupPreview.controls(mock);
                });
        this.screen("replaceKeyBinds", "key_binds", false, ConfigIcons.KEYBOARD, options, "Key binds",
                "Categories, a search by name, key or mod, and the key binds in conflict or changed.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaKeyList(mock);
                    else SetupPreview.keyBinds(mock);
                });
        this.screen("replacePacks", "packs", false, ConfigIcons.FOLDER, options, "Packs",
                "Resource packs (and a new world's data packs): those in use as a stack to reorder, beside the rest as tiles.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaPacks(mock);
                    else SetupPreview.packs(mock);
                });
        this.screen("replaceLanguage", "language", false, ConfigIcons.GLOBE, options, "Language",
                "A search by name, region or code, with the language in use marked.",
                (mock, variant) -> {
                    if (variant == 0) SetupPreview.vanillaLanguage(mock);
                    else SetupPreview.language(mock);
                });
        if (GlazedPlatform.get().isModLoaded("iris")) {
            this.screen("replaceShaders", "shaders", false, ConfigIcons.EYE, options, "Shader packs",
                    "Iris's shader packs as tiles with the chosen pack's details. A pack's own settings stay in Iris's screen.",
                    (mock, variant) -> {
                        if (variant == 0) SetupPreview.vanillaShaders(mock);
                        else SetupPreview.shaders(mock);
                    });
        }

        this.pages.add(new Page(Kind.DONE, null, ConfigIcons.CHECK, text("group.done", "Every screen"), text("done.title", "All the screens"),
                text("done.description", "Every screen Glazed Menu can redraw, in the look it will have. Click one to switch it between Glazed Menu's and the game's own. Change any later in Glazed Menu's settings, or with /glazedmenu setup."),
                List.of(), (mock, variant) -> {}, null));
    }

    /** A layout of Glazed Menu's screen to choose, as one of its page's options. */
    private record Layout(Component name, Component note, BooleanSupplier current, Runnable use) {}

    private static Layout layout(Component name, Component note, BooleanSupplier current, Runnable use) {
        return new Layout(name, note, current, use);
    }

    // A screen: Glazed Menu's (in each of its layouts) or the game's own. Featured ones get a page of their own too.
    private void screen(String key, String shot, boolean featured, ConfigIcons.Icon icon, Component group, String title, String description, Preview preview,
                        Layout... layouts) {
        GlazedSettings.Switch toggle = GlazedSettings.screenSwitch(key);
        List<Option> options = new ArrayList<>();
        if (layouts.length == 0) {
            options.add(new Option(text("option.glazed", "Glazed Menu's"), text("option.glazed.note", "As in the preview"), 1, toggle.get(), () -> toggle.set().accept(true)));
        }
        for (int i = 0; i < layouts.length; i++) {
            Layout layout = layouts[i];
            options.add(new Option(Component.translatableWithFallback("glazedmenu.setup.option.glazed.layout", "Glazed Menu's: %s", layout.name()), layout.note(), i + 1,
                    () -> toggle.get().getAsBoolean() && layout.current().getAsBoolean(), () -> {
                        toggle.set().accept(true);
                        layout.use().run();
                    }));
        }
        options.add(new Option(text("option.game", "The game's own"), text("option.game.note", "The screen as the game draws it"), 0,
                () -> !toggle.get().getAsBoolean(), () -> toggle.set().accept(false)));
        Page page = new Page(Kind.SCREEN, toggle, icon, group, text("page." + key + ".title", title), text("page." + key + ".description", description), options,
                preview, shot);
        this.screens.add(page);
        if (featured) {
            this.pages.add(page);
            this.featured.add(page);
        }
    }

    private Page page() {
        return this.pages.get(this.index);
    }

    // The variant a page shows now: its chosen option's.
    private static int variant(Page page) {
        for (Option option : page.options) if (option.chosen().getAsBoolean()) return option.variant();
        return 0;
    }

    // --- Moving between pages ---

    private void go(int target, int direction) {
        if (target == this.index || target < 0 || target >= this.pages.size()) return;
        this.index = target;
        this.direction = direction;
        this.stepAt = System.nanoTime();
        this.wipeFrom = -1;
        this.scroll = 0;
        this.columnScroll = 0;
        this.rebuildWidgets();
    }

    private void next() {
        switch (this.page().kind) {
            case WELCOME -> {
                this.quick = this.start != Start.WALK;
                if (this.quick) this.setAll(this.start == Start.ALL);
                this.go(this.index + 1, 1);
            }
            case LOOK -> this.go(this.quick ? this.pages.size() - 1 : this.index + 1, 1);
            case SCREEN -> this.go(this.index + 1, 1);
            case DONE -> this.finish();
        }
    }

    private void back() {
        if (this.page().kind == Kind.DONE && this.quick) this.go(1, -1);
        else this.go(this.index - 1, -1);
    }

    // Every screen at once, each card wiping into its new look.
    private void setAll(boolean glazed) {
        long now = System.nanoTime();
        for (int i = 0; i < this.screens.size(); i++) {
            Page screen = this.screens.get(i);
            int before = variant(screen);
            screen.toggle.set().accept(glazed);
            this.startCardWipe(i, before, now);
        }
    }

    private void startCardWipe(int card, int before, long now) {
        if (before == variant(this.screens.get(card)) || !GlazedSettings.transitions()) return;
        this.cardFrom[card] = before;
        this.cardWipeAt[card] = now;
    }

    // Saves the choices, turns the setup off, and goes on: to a fresh title screen (Glazed Menu's or the game's, as chosen).
    private void finish() {
        GlazedSettings.finishSetup();
        this.leave(this.parent instanceof TitleScreen ? new TitleScreen() : this.parent);
    }

    @Override
    public void onClose() {
        this.finish();
    }

    // The previews are only needed here: they're let go of once the setup closes.
    @Override
    public void removed() {
        super.removed();
        this.live.clear();
    }

    @Override
    public void tick() {
        super.tick();
        this.live.tick();
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    private void choose(Option option) {
        int before = variant(this.page());
        option.choose().run();
        int after = variant(this.page());
        if (before != after && GlazedSettings.transitions()) {
            this.wipeFrom = before;
            this.wipeTo = after;
            this.wipeAt = System.nanoTime();
        }
        this.click();
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // --- Layout ---

    @Override
    protected void init() {
        Page page = this.page();
        int top = this.bodyTop() + RAIL_HEIGHT + 8;
        int bottom = this.bodyBottom() - 4;
        int left = this.frameLeft() + 6;
        int width = this.frameWidth() - 12;
        this.textLeft = left;
        int head = page.kind == Kind.WELCOME ? 32 : 25;
        // The text column, as narrow as lets it fit in the body's height (a short window wants it wider, in fewer lines).
        int total = 0;
        for (float share : new float[]{0.36F, 0.44F, 0.52F}) {
            this.textWidth = Math.clamp(Math.round(width * share), 140, Math.round(220 * share / 0.36F));
            this.areaX = left + this.textWidth + 16;
            this.areaWidth = left + width - this.areaX;
            if (this.areaWidth < 110) {
                // Too narrow for a picture beside the text: the text takes the width.
                this.textWidth = width;
                this.areaWidth = 0;
            }
            this.description = this.font.split(page.description, this.textWidth);
            int text = this.description.size() * 10 + 6;
            total = head + text + this.columnContent(page, bottom - top - head - text);
            if (total <= bottom - top || this.areaWidth == 0) break;
        }
        this.baseColumnTop = top + Math.max(0, (bottom - top - total) / 2);
        this.baseOptionsTop = this.baseColumnTop + head + this.description.size() * 10 + 6;
        this.columnClipTop = this.bodyTop() + RAIL_HEIGHT + 5;
        this.columnClipBottom = this.bodyBottom() - 1;
        // The last page's own grid scrolls instead, so its column never does.
        this.columnMax = page.kind == Kind.DONE ? 0 : Math.max(0, total - (bottom - top));
        this.columnScroll = Math.clamp(this.columnScroll, 0, this.columnMax);
        this.columnTop = this.baseColumnTop;
        this.optionsTop = this.baseOptionsTop;
        this.columnButtons.clear();

        // The picture: as large as fits, 16 by 9, with room under it for its caption.
        int room = bottom - top - 26;
        this.previewWidth = Math.min(this.areaWidth, Math.round(room * PHOTO_RATIO));
        this.previewHeight = Math.round(this.previewWidth / PHOTO_RATIO);
        this.previewX = this.areaX + (this.areaWidth - this.previewWidth) / 2;
        this.previewY = top + (room - this.previewHeight) / 2;
        if (page.kind == Kind.DONE || this.areaWidth == 0) this.previewWidth = 0;

        // The last page's cards, one per screen, in a grid that scrolls: beside the text, or under it with no room beside.
        if (page.kind == Kind.DONE) {
            if (this.areaWidth == 0) {
                this.areaX = this.textLeft;
                this.areaWidth = this.textWidth;
                this.gridTop = this.optionsTop + 46;
            } else {
                this.gridTop = top;
            }
            this.gridHeight = Math.max(40, bottom - this.gridTop);
            this.cardColumns = Math.clamp((this.areaWidth + CARD_GAP) / 124, 2, 5);
            this.cardWidth = (this.areaWidth - (this.cardColumns - 1) * CARD_GAP) / this.cardColumns;
            this.photoHeight = Math.round((this.cardWidth - 4) / PHOTO_RATIO);
            this.cardHeight = this.photoHeight + 4 + CARD_LABEL;
            int rows = (this.screens.size() + this.cardColumns - 1) / this.cardColumns;
            this.contentHeight = rows * (this.cardHeight + CARD_GAP) - CARD_GAP;
            this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.contentHeight - this.gridHeight));
        }

        this.count = Component.translatableWithFallback("glazedmenu.setup.count", "%s of %s", this.index + 1, this.pages.size());
        this.hint = page.kind == Kind.DONE ? text("hint.done", "Scroll for more · click a screen to switch it") : text("hint", "← → to move, ↑ ↓ to choose");

        int barY = this.barWidgetY(this.bottomBarY());
        FlatButton back = this.addRenderableWidget(FlatButton.of(text("back", "Back"), 70, button -> this.back()).withIcon(ConfigIcons.CHEVRON_LEFT));
        back.setPosition(this.frameLeft() + 4, barY);
        back.visible = this.index > 0;
        Component nextLabel = switch (page.kind) {
            case WELCOME -> text("start", "Let's go");
            case DONE -> text("finish", "Finish");
            default -> text("next", "Next");
        };
        FlatButton next = this.addRenderableWidget(FlatButton.of(nextLabel, Math.max(80, this.font.width(nextLabel) + 34), button -> this.next())
                .style(FlatButton.Style.PRIMARY).withIcon(page.kind == Kind.DONE ? ConfigIcons.CHECK : ConfigIcons.CHEVRON));
        next.setPosition(this.frameLeft() + this.frameWidth() - 4 - next.getWidth(), barY);
        if (page.kind == Kind.DONE) {
            // Every screen at once: Glazed Menu's, or the game's own.
            int half = (this.textWidth - 4) / 2;
            FlatButton all = this.addRenderableWidget(FlatButton.of(text("done.all", "All Glazed"), half, button -> this.setAll(true)).withIcon(ConfigIcons.STAR));
            all.setPosition(this.textLeft, this.optionsTop + 18);
            this.columnButtons.add(all);
            FlatButton none = this.addRenderableWidget(FlatButton.of(text("done.none", "All the game's"), half, button -> this.setAll(false)).withIcon(ConfigIcons.RESET));
            none.setPosition(this.textLeft + half + 4, this.optionsTop + 18);
            this.columnButtons.add(none);
        } else {
            Component skip = text("skip", "Skip");
            FlatButton skipButton = this.addRenderableWidget(FlatButton.of(skip, this.font.width(skip) + 16, button -> {
                        if (this.page().kind == Kind.WELCOME) this.quick = true;
                        this.go(this.pages.size() - 1, 1);
                    }).style(FlatButton.Style.GHOST)
                    .tooltip(text("skip.tooltip", "Go straight to the list of every screen")));
            skipButton.setPosition(this.frameLeft() + this.frameWidth() - 6 - skipButton.getWidth(), this.topBarWidgetY(20));
        }
        this.applyColumnScroll();
    }

    // Moves the column (its text, its choices, and any buttons in it) up by how far it's scrolled.
    private void applyColumnScroll() {
        int by = (int) Math.round(this.columnScroll);
        this.columnTop = this.baseColumnTop - by;
        int moved = this.baseOptionsTop - by - this.optionsTop;
        this.optionsTop = this.baseOptionsTop - by;
        for (FlatButton button : this.columnButtons) {
            button.setY(button.getY() + moved);
            button.visible = button.getY() >= this.columnClipTop && button.getY() + button.getHeight() <= this.columnClipBottom;
        }
    }

    // Whether a point is inside the column's showing part, where its choices can be clicked.
    private boolean inColumn(double mouseX, double mouseY) {
        return mouseX >= this.frameLeft() && mouseX < this.textLeft + this.textWidth + 8 && mouseY >= this.columnClipTop && mouseY < this.columnClipBottom;
    }

    // How tall what follows the description is: the options (with their notes when there's room), or the look's choices.
    private int columnContent(Page page, int room) {
        return switch (page.kind) {
            case LOOK -> 12 + this.swatchRows() * (SWATCH + 5) + 8 + 12 + 18 + 20 + 18;
            case DONE -> 12 + 6 + 20;
            default -> {
                int count = page.options.size();
                this.optionNotes = count * (26 + OPTION_GAP) <= room;
                this.optionHeight = this.optionNotes ? 26 : 15;
                yield count * (this.optionHeight + OPTION_GAP);
            }
        };
    }

    private int swatchColumns() {
        return Math.max(1, (this.textWidth + 5) / (SWATCH + 5));
    }

    private int swatchRows() {
        int count = GlazedBrand.Scheme.values().length;
        return (count + this.swatchColumns() - 1) / this.swatchColumns();
    }

    // --- Drawing ---

    // How far into the page's arrival: 0 as it starts to slide in, 1 once in place.
    private float arrival() {
        if (!GlazedSettings.transitions()) return 1;
        float progress = Math.min(1, (System.nanoTime() - this.stepAt) / 1_000_000F / STEP_MILLIS);
        return 1 - (1 - progress) * (1 - progress) * (1 - progress);
    }

    private float seconds() {
        return GlazedSettings.transitions() ? (System.nanoTime() - this.openedAt) / 1_000_000_000F : 0;
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        Page page = this.page();
        this.extractTitle(graphics, this.title, this.frameWidth() / 2);
        int countRight = this.frameLeft() + this.frameWidth() - (page.kind == Kind.DONE ? 10 : 14 + this.font.width("Skip") + 8);
        graphics.text(this.font, this.count, countRight - this.font.width(this.count), this.topBarY() + (BAR_HEIGHT - 8) / 2, ConfigStyle.colors().textMuted(), false);
        this.extractRail(graphics);
        int hintWidth = this.font.width(this.hint);
        if (hintWidth < this.frameWidth() - 220) {
            graphics.text(this.font, this.hint, this.frameLeft() + (this.frameWidth() - hintWidth) / 2, this.bottomBarY() + (BAR_HEIGHT - 8) / 2,
                    ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0xA0), false);
        }

        // The text column's glass, which stays put while the page slides over it.
        int panelTop = this.bodyTop() + RAIL_HEIGHT + 4;
        int panelWidth = this.textLeft - this.frameLeft() + this.textWidth + 8;
        ConfigStyle.panel(graphics, this.frameLeft(), panelTop, panelWidth, this.bodyBottom() - panelTop);
        // A deeper tint inside it, so its text reads whatever the waves and the panorama behind it are doing.
        ConfigStyle.rect(graphics, this.frameLeft() + 1, panelTop + 1, panelWidth - 2, this.bodyBottom() - panelTop - 2,
                ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xA8));

        // The page slides in from the side it was reached from, the picture a little further than the text.
        float arrival = this.arrival();
        float fade = GuiFade.get();
        GuiFade.set(fade * arrival);
        float shift = (1 - arrival) * 18 * this.direction;
        graphics.pose().pushMatrix();
        graphics.pose().translate(shift, 0);
        // Clipped to the panel, so a column taller than it scrolls rather than running past its foot.
        graphics.enableScissor(this.frameLeft() + 1, this.columnClipTop, this.textLeft + this.textWidth + 7, this.columnClipBottom);
        this.extractColumn(graphics, page, mouseX, mouseY);
        graphics.disableScissor();
        if (this.columnMax > 0) {
            // A scroll bar down the panel's right edge.
            int barX = this.textLeft + this.textWidth + 4;
            int span = this.columnClipBottom - this.columnClipTop - 4;
            int thumb = Math.max(14, span * span / (span + this.columnMax));
            int thumbY = this.columnClipTop + 2 + (int) ((span - thumb) * (this.columnScroll / this.columnMax));
            graphics.fill(barX, this.columnClipTop + 2, barX + 2, this.columnClipTop + 2 + span, ConfigStyle.withAlpha(ConfigStyle.colors().toggleOff(), 0x80));
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumb, ConfigStyle.accent());
        }
        graphics.pose().popMatrix();
        graphics.pose().pushMatrix();
        graphics.pose().translate(shift * 1.8F, 0);
        switch (page.kind) {
            case WELCOME -> this.extractShowcase(graphics);
            case DONE -> this.extractCards(graphics, mouseX, mouseY);
            default -> this.extractPicture(graphics, page);
        }
        graphics.pose().popMatrix();
        GuiFade.set(fade);
    }

    // The steps along the top: those passed in the flowing gradient, the one here filling as it arrives.
    private void extractRail(GuiGraphicsExtractor graphics) {
        int count = this.pages.size();
        int left = this.frameLeft();
        int width = this.frameWidth();
        int y = this.bodyTop();
        float segment = (width - (count - 1) * 2F) / count;
        float arrival = this.arrival();
        for (int i = 0; i < count; i++) {
            int x0 = left + Math.round(i * (segment + 2));
            int x1 = left + Math.round(i * (segment + 2) + segment);
            graphics.fill(x0, y, x1, y + RAIL_HEIGHT, ConfigStyle.withAlpha(ConfigStyle.colors().toggleOff(), 0xB0));
            int end = i < this.index ? x1 : i == this.index ? x0 + Math.round((x1 - x0) * arrival) : x0;
            if (end > x0) graphics.fill(x0, y, end, y + RAIL_HEIGHT, GlazedBrand.now(i / (float) count));
        }
    }

    private void extractColumn(GuiGraphicsExtractor graphics, Page page, int mouseX, int mouseY) {
        int x = this.textLeft;
        int y = this.columnTop;
        // The group, as a small tag, then the page's title.
        if (page.kind == Kind.WELCOME) {
            graphics.text(this.font, page.group, x, y, ConfigStyle.colors().textDim(), false);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y + 12);
            graphics.pose().scale(2, 2);
            GlazedBrand.text(graphics, this.font, "Glazed Menu", 0, 0, 0.1F);
            graphics.pose().popMatrix();
            y += 32;
        } else {
            page.icon.draw(graphics, x, y - 1, this.groupColor());
            // The group in the accent lifted toward white (toward ink on the light look), so it stands off the tinted panel.
            ConfigStyle.titleText(graphics, this.font, page.boldGroup, x + 14, y, this.textWidth - 14, this.groupColor());
            ConfigStyle.titleText(graphics, this.font, page.boldTitle, x, y + 13, this.textWidth, ConfigStyle.colors().text());
            y += 25;
        }
        for (FormattedCharSequence line : this.description) {
            // Full text on the dark look; the softer shade on the light one, where the full one is near black.
            boolean dark = ConfigStyle.mode() == ConfigTheme.Mode.DARK;
            graphics.text(this.font, line, x, y, dark ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim(), dark);
            y += 10;
        }
        switch (page.kind) {
            case LOOK -> this.extractLook(graphics, mouseX, mouseY);
            case DONE -> {
                int on = 0;
                for (Page screen : this.screens) if (screen.toggle.get().getAsBoolean()) on++;
                Component tally = Component.translatableWithFallback("glazedmenu.setup.done.tally", "%s of %s in Glazed Menu's look", on, this.screens.size());
                this.pill(graphics, tally, x, this.optionsTop - 1, ConfigStyle.accent());
            }
            default -> this.extractOptions(graphics, page, mouseX, mouseY);
        }
    }

    // The group's color: the accent lifted toward white on the dark look, and only a little deepened on the light one.
    private int groupColor() {
        return ConfigStyle.mode() == ConfigTheme.Mode.DARK ? GlazedBrand.lift(ConfigStyle.accent(), 0.45F)
                : ConfigStyle.mix(ConfigStyle.accent(), 0xFF000000, 0.15F);
    }

    private void extractOptions(GuiGraphicsExtractor graphics, Page page, int mouseX, int mouseY) {
        List<Option> options = page.options;
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            int x = this.textLeft;
            int y = this.optionsTop + i * (this.optionHeight + OPTION_GAP);
            int width = this.textWidth;
            int height = this.optionHeight;
            boolean hovered = this.inColumn(mouseX, mouseY) && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            float glow = this.ease(i, hovered);
            boolean chosen = option.chosen().getAsBoolean();
            int accent = ConfigStyle.accent();
            int fill = chosen ? ConfigStyle.withAlpha(accent, 0x38 + Math.round(glow * 0x18))
                    : ConfigStyle.mix(ConfigStyle.colors().button(), ConfigStyle.colors().buttonHover(), glow);
            ConfigStyle.rect(graphics, x, y, width, height, fill);
            ConfigStyle.outline(graphics, x, y, width, height, chosen ? accent : ConfigStyle.mix(ConfigStyle.colors().panelBorder(), accent, glow * 0.7F));
            if (chosen) GlazedBrand.fill(graphics, x + 1, y + 2, 2, height - 4, 0xFF);
            // The radio: a ring, filled when chosen.
            int dot = 8;
            int dotX = x + 8;
            int dotY = y + (height - dot) / 2;
            ConfigStyle.outline(graphics, dotX, dotY, dot, dot, chosen ? accent : ConfigStyle.colors().textMuted());
            if (chosen) graphics.fill(dotX + 2, dotY + 2, dotX + dot - 2, dotY + dot - 2, accent);
            int textX = dotX + dot + 7;
            int textWidth = x + width - 6 - textX;
            if (this.optionNotes && option.note() != null) {
                ConfigStyle.text(graphics, this.font, option.name(), textX, y + 4, textWidth, ConfigStyle.colors().text());
                ConfigStyle.text(graphics, this.font, option.note(), textX, y + 15, textWidth,
                        ConfigStyle.mode() == ConfigTheme.Mode.DARK ? ConfigStyle.colors().textDim() : ConfigStyle.colors().textMuted());
            } else {
                ConfigStyle.text(graphics, this.font, option.name(), textX, y + (height - 8) / 2, textWidth, ConfigStyle.colors().text());
            }
        }
    }

    // A hover that eases in and out rather than snapping, one per thing that can be hovered.
    private float ease(int slot, boolean hovered) {
        float target = hovered ? 1 : 0;
        if (slot >= this.hover.length) return target;
        if (!GlazedSettings.transitions()) return this.hover[slot] = target;
        this.hover[slot] += (target - this.hover[slot]) * 0.3F;
        if (Math.abs(target - this.hover[slot]) < 0.01F) this.hover[slot] = target;
        return this.hover[slot];
    }

    // The look's choices: the color schemes as swatches, then dark or light.
    private void extractLook(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.textLeft;
        int y = this.optionsTop;
        graphics.text(this.font, text("look.scheme", "Color scheme").copy().withStyle(ChatFormatting.BOLD), x, y, ConfigStyle.colors().text(), false);
        Component name = schemeName(GlazedSettings.colorScheme());
        graphics.text(this.font, name, x + this.textWidth - this.font.width(name), y, ConfigStyle.accent(), false);
        GlazedBrand.Scheme[] schemes = GlazedBrand.Scheme.values();
        for (int i = 0; i < schemes.length; i++) {
            int boxX = this.swatchX(i);
            int boxY = this.swatchY(i);
            boolean hovered = this.inColumn(mouseX, mouseY) && mouseX >= boxX && mouseX < boxX + SWATCH && mouseY >= boxY && mouseY < boxY + SWATCH;
            float glow = this.ease(8 + i, hovered);
            boolean chosen = schemes[i] == GlazedSettings.colorScheme();
            int lift = Math.round(glow * 2);
            int color = schemes[i].swatch();
            if (chosen) ConfigStyle.outline(graphics, boxX - 2, boxY - 2 - lift, SWATCH + 4, SWATCH + 4, ConfigStyle.colors().text());
            ConfigStyle.rect(graphics, boxX, boxY - lift, SWATCH, SWATCH, color);
            graphics.fill(boxX + 2, boxY - lift + 2, boxX + SWATCH - 2, boxY - lift + SWATCH / 2, ConfigStyle.withAlpha(0xFFFFFFFF, 0x30));
            if (chosen) ConfigIcons.CHECK.drawCentered(graphics, boxX, boxY - lift, SWATCH, SWATCH, ConfigStyle.readableOn(color));
        }
        this.extractChoice(graphics, mouseX, mouseY, this.modeY(), text("look.mode", "Mode"), 16,
                GlazedSettings.themeMode() == ConfigTheme.Mode.DARK ? 0 : 1,
                text("look.dark", "Dark"), ConfigIcons.MOON, text("look.light", "Light"), ConfigIcons.SUN);
        this.extractChoice(graphics, mouseX, mouseY, this.wavesY(), text("look.waves", "Background waves"), 18,
                GlazedSettings.animatedWaves() ? 0 : 1,
                text("look.waves.moving", "Moving"), ConfigIcons.PLAY, text("look.waves.still", "Still"), ConfigIcons.PAUSE);
    }

    // A choice of two, as two chips under its name; {@code chosen} is the one lit.
    private void extractChoice(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y, Component name, int hoverSlot, int chosen,
                               Component first, ConfigIcons.Icon firstIcon, Component second, ConfigIcons.Icon secondIcon) {
        graphics.text(this.font, name.copy().withStyle(ChatFormatting.BOLD), this.textLeft, y - 12, ConfigStyle.colors().text(), false);
        for (int i = 0; i < 2; i++) {
            int chipX = this.textLeft + i * (this.modeWidth() + 4);
            boolean hovered = this.inColumn(mouseX, mouseY) && mouseX >= chipX && mouseX < chipX + this.modeWidth() && mouseY >= y && mouseY < y + 18;
            float glow = this.ease(hoverSlot + i, hovered);
            boolean lit = chosen == i;
            int accent = ConfigStyle.accent();
            ConfigStyle.rect(graphics, chipX, y, this.modeWidth(), 18, lit ? ConfigStyle.withAlpha(accent, 0x48)
                    : ConfigStyle.mix(ConfigStyle.colors().button(), ConfigStyle.colors().buttonHover(), glow));
            ConfigStyle.outline(graphics, chipX, y, this.modeWidth(), 18, lit ? accent : ConfigStyle.mix(ConfigStyle.colors().panelBorder(), accent, glow * 0.7F));
            Component label = i == 0 ? first : second;
            int labelWidth = this.font.width(label) + 14;
            int labelX = chipX + (this.modeWidth() - labelWidth) / 2;
            (i == 0 ? firstIcon : secondIcon).draw(graphics, labelX, y + 4, lit ? accent : ConfigStyle.colors().textDim());
            graphics.text(this.font, label, labelX + 14, y + 5, ConfigStyle.colors().text(), false);
        }
    }

    // The chip of a choice of two under the mouse: 0, 1, or -1 for neither.
    private int choiceAt(double mouseX, double mouseY, int y) {
        for (int i = 0; i < 2; i++) {
            int chipX = this.textLeft + i * (this.modeWidth() + 4);
            if (mouseX >= chipX && mouseX < chipX + this.modeWidth() && mouseY >= y && mouseY < y + 18) return i;
        }
        return -1;
    }

    private int swatchX(int i) {
        return this.textLeft + i % this.swatchColumns() * (SWATCH + 5) + 2;
    }

    private int swatchY(int i) {
        return this.optionsTop + 14 + i / this.swatchColumns() * (SWATCH + 5);
    }

    private int modeY() {
        return this.optionsTop + 12 + this.swatchRows() * (SWATCH + 5) + 20;
    }

    private int wavesY() {
        return this.modeY() + 18 + 20;
    }

    private int modeWidth() {
        return (this.textWidth - 4) / 2;
    }

    private static Component schemeName(GlazedBrand.Scheme scheme) {
        String name = scheme.name().charAt(0) + scheme.name().substring(1).toLowerCase(Locale.ROOT);
        return Component.translatableWithFallback("glazedmenu.setup.scheme." + scheme.name().toLowerCase(Locale.ROOT), name);
    }

    // A screen's picture of a variant: the screen itself, drawn live, or (for one needing a world) drawn from boxes. The
    // look's page shows the mod list, which follows the colors chosen.
    private void extractShot(GuiGraphicsExtractor graphics, Page page, int variant, int x, int y, int width, int height) {
        String shot = page.kind == Kind.LOOK ? "mod_list" : page.shot;
        int shown = page.kind == Kind.LOOK ? 1 : variant;
        if (shot != null && this.live.draw(graphics, shot, shown, x, y, width, height, 1)) {
            // The setup's own colors again, after the screen's.
            ConfigStyle.use(this.theme);
            return;
        }
        SetupPreview.Mock mock = new SetupPreview.Mock(graphics, x, y, width, height, this.seconds());
        page.preview.draw(mock, variant);
        mock.flush();
    }

    // A picture that wipes from one variant to another (from the left, with a bright seam), or shows one when {@code to} is -1.
    private void extractWipe(GuiGraphicsExtractor graphics, Page page, int from, int to, float wipe, int x, int y, int width, int height, boolean small) {
        graphics.enableScissor(x, y, x + width, y + height);
        this.extractShot(graphics, page, from, x, y, width, height);
        if (to >= 0) {
            int edge = x + Math.round(width * wipe);
            graphics.enableScissor(x, y, edge, y + height);
            this.extractShot(graphics, page, to, x, y, width, height);
            graphics.disableScissor();
            int glow = GlazedBrand.lift(ConfigStyle.accent(), 0.4F);
            int spread = small ? 3 : 6;
            for (int i = 0; i < spread; i++) graphics.fill(edge - 1 - i, y, edge - i, y + height, ConfigStyle.withAlpha(glow, 0xC0 - i * 0xC0 / spread));
            graphics.fill(edge, y, edge + 1, y + height, 0xFFFFFFFF);
        }
        graphics.disableScissor();
    }

    // The frame of a page's large picture: glass round a screen, with a soft sheen across its top.
    private void extractFrame(GuiGraphicsExtractor graphics, Page page, int from, int to, float wipe) {
        int x = this.previewX;
        int y = this.previewY;
        int width = this.previewWidth;
        int height = this.previewHeight;
        ConfigStyle.rect(graphics, x - 3 + 4, y - 3 + 5, width + 6, height + 6, 0x55000000);
        ConfigStyle.rect(graphics, x - 3, y - 3, width + 6, height + 6, ConfigStyle.colors().panel());
        ConfigStyle.outline(graphics, x - 3, y - 3, width + 6, height + 6, ConfigStyle.colors().panelBorder());
        this.extractWipe(graphics, page, from, to, wipe, x, y, width, height, false);
        graphics.fillGradient(x, y, x + width, y + height / 3, 0x14FFFFFF, 0x00FFFFFF);
    }

    // The page's picture, wiping from the old choice to the new one when it changes, with its caption.
    private void extractPicture(GuiGraphicsExtractor graphics, Page page) {
        if (this.previewWidth == 0) return;
        int variant = variant(page);
        float wipe = 1;
        if (this.wipeFrom >= 0) {
            wipe = Math.min(1, (System.nanoTime() - this.wipeAt) / 1_000_000F / WIPE_MILLIS);
            if (wipe >= 1) this.wipeFrom = -1;
        }
        float eased = wipe * wipe * (3 - 2 * wipe);
        this.extractFrame(graphics, page, this.wipeFrom >= 0 ? this.wipeFrom : variant, this.wipeFrom >= 0 ? this.wipeTo : -1, eased);
        Component caption = page.kind == Kind.LOOK ? text("look.caption", "Every config screen, in your colors") : this.optionName(page, variant);
        this.extractCaption(graphics, caption, variant > 0 || page.kind == Kind.LOOK);
    }

    private Component optionName(Page page, int variant) {
        for (Option option : page.options) if (option.variant() == variant) return option.name();
        return Component.empty();
    }

    private void extractCaption(GuiGraphicsExtractor graphics, Component caption, boolean glazed) {
        int y = this.previewY + this.previewHeight + 11;
        int width = this.font.width(caption) + 12;
        this.pill(graphics, caption, this.previewX + (this.previewWidth - width) / 2, y, glazed ? ConfigStyle.accent() : ConfigStyle.colors().textMuted());
    }

    // A label on a pill: tinted and edged in its color, its text in the text color so it reads on any scheme.
    private void pill(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        int width = this.font.width(text) + 12;
        ConfigStyle.rect(graphics, x, y, width, 13, ConfigStyle.mix(ConfigStyle.colors().popup(), color, 0.3F));
        ConfigStyle.outline(graphics, x, y, width, 13, ConfigStyle.withAlpha(color, 0xC0));
        graphics.fill(x + 3, y + 5, x + 6, y + 8, color);
        graphics.text(this.font, text, x + 9, y + 3, ConfigStyle.colors().text(), false);
    }

    // The welcome's picture: one main screen after another, each wiped from the game's look into Glazed Menu's.
    private void extractShowcase(GuiGraphicsExtractor graphics) {
        if (this.previewWidth == 0 || this.featured.isEmpty()) return;
        float millis = this.seconds() * 1000;
        int which = (int) (millis / SHOWCASE_MILLIS) % this.featured.size();
        float part = millis % SHOWCASE_MILLIS / SHOWCASE_MILLIS;
        float wipe = Math.clamp((part - SHOWCASE_WIPE_FROM) / (SHOWCASE_WIPE_TO - SHOWCASE_WIPE_FROM), 0, 1);
        if (!GlazedSettings.transitions()) wipe = 1;
        wipe = wipe * wipe * (3 - 2 * wipe);
        Page page = this.featured.get(which);
        this.extractFrame(graphics, page, 0, wipe > 0 ? 1 : -1, wipe);
        Component caption = wipe < 1 ? Component.translatableWithFallback("glazedmenu.setup.showcase.game", "%s: the game's", page.title)
                : Component.translatableWithFallback("glazedmenu.setup.showcase.glazed", "%s: Glazed Menu's", page.title);
        this.extractCaption(graphics, caption, wipe >= 1);
    }

    // The last page: a card for every screen, its small picture in the look it will have, its name and its switch.
    // Only the cards in view are drawn, and only their screens made.
    private void extractCards(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = this.areaX;
        int top = this.gridTop;
        ConfigStyle.panel(graphics, left - 6, top - 6, this.areaWidth + 12, this.gridHeight + 12);
        graphics.enableScissor(left - 1, top, left + this.areaWidth + 1, top + this.gridHeight);
        boolean inGrid = mouseY >= top && mouseY < top + this.gridHeight;
        long now = System.nanoTime();
        for (int i = 0; i < this.screens.size(); i++) {
            int x = this.cardX(i);
            int y = this.cardY(i);
            if (y + this.cardHeight < top || y > top + this.gridHeight) continue;
            Page page = this.screens.get(i);
            boolean hovered = inGrid && mouseX >= x && mouseX < x + this.cardWidth && mouseY >= y && mouseY < y + this.cardHeight;
            float glow = this.ease(20 + i, hovered);
            boolean on = page.toggle.get().getAsBoolean();
            int accent = ConfigStyle.accent();
            ConfigStyle.rect(graphics, x, y, this.cardWidth, this.cardHeight, on ? ConfigStyle.withAlpha(accent, 0x30 + Math.round(glow * 0x20))
                    : ConfigStyle.mix(ConfigStyle.colors().button(), ConfigStyle.colors().buttonHover(), glow));
            ConfigStyle.outline(graphics, x, y, this.cardWidth, this.cardHeight,
                    on ? ConfigStyle.withAlpha(accent, 0xC0) : ConfigStyle.mix(ConfigStyle.colors().panelBorder(), accent, glow * 0.6F));
            int variant = variant(page);
            float wipe = 1;
            if (this.cardWipeAt[i] != 0) {
                wipe = Math.min(1, (now - this.cardWipeAt[i]) / 1_000_000F / WIPE_MILLIS);
                if (wipe >= 1) this.cardWipeAt[i] = 0;
            }
            boolean wiping = this.cardWipeAt[i] != 0;
            this.extractWipe(graphics, page, wiping ? this.cardFrom[i] : variant, wiping ? variant : -1, wipe * wipe * (3 - 2 * wipe),
                    x + 2, y + 2, this.cardWidth - 4, this.photoHeight, true);
            // The game's own look is dimmed a little, so the screens in Glazed Menu's stand out.
            if (!on) graphics.fill(x + 2, y + 2, x + this.cardWidth - 2, y + 2 + this.photoHeight, 0x30000000);
            int labelY = y + 2 + this.photoHeight + (CARD_LABEL - 8) / 2 + 1;
            page.icon.draw(graphics, x + 4, labelY - 1, on ? accent : ConfigStyle.colors().textMuted());
            // A small switch at the right end, sliding with the choice.
            int trackX = x + this.cardWidth - 18;
            int trackY = labelY;
            ConfigStyle.rect(graphics, trackX, trackY, 14, 7, on ? accent : ConfigStyle.colors().toggleOff());
            graphics.fill(on ? trackX + 8 : trackX + 1, trackY + 1, on ? trackX + 13 : trackX + 6, trackY + 6, ConfigStyle.colors().knob());
            ConfigStyle.text(graphics, this.font, page.title, x + 17, labelY, this.cardWidth - 17 - 22,
                    on ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted());
        }
        graphics.disableScissor();
        // A scroll bar, when the cards run past the bottom.
        if (this.contentHeight > this.gridHeight) {
            int barX = left + this.areaWidth + 2;
            int thumb = Math.max(16, this.gridHeight * this.gridHeight / this.contentHeight);
            int thumbY = top + (int) ((this.gridHeight - thumb) * (this.scroll / (this.contentHeight - this.gridHeight)));
            graphics.fill(barX, top, barX + 2, top + this.gridHeight, ConfigStyle.withAlpha(ConfigStyle.colors().toggleOff(), 0x80));
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumb, ConfigStyle.accent());
        }
    }

    private int cardX(int i) {
        return this.areaX + i % this.cardColumns * (this.cardWidth + CARD_GAP);
    }

    private int cardY(int i) {
        return this.gridTop + i / this.cardColumns * (this.cardHeight + CARD_GAP) - (int) Math.round(this.scroll);
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.isLeaving()) return true;
        if (super.mouseClicked(event, doubleClick)) return true;
        double mouseX = event.x();
        double mouseY = event.y();
        Page page = this.page();
        // A step along the top goes straight to it.
        int railY = this.bodyTop();
        if (mouseY >= railY - 3 && mouseY < railY + RAIL_HEIGHT + 4 && mouseX >= this.frameLeft() && mouseX < this.frameLeft() + this.frameWidth()) {
            int step = (int) ((mouseX - this.frameLeft()) / (this.frameWidth() / (double) this.pages.size()));
            step = Math.clamp(step, 0, this.pages.size() - 1);
            if (step != this.index) {
                this.click();
                this.go(step, step > this.index ? 1 : -1);
            }
            return true;
        }
        boolean inColumn = this.inColumn(mouseX, mouseY);
        switch (page.kind) {
            case LOOK -> {
                if (!inColumn) return false;
                GlazedBrand.Scheme[] schemes = GlazedBrand.Scheme.values();
                for (int i = 0; i < schemes.length; i++) {
                    int boxX = this.swatchX(i);
                    int boxY = this.swatchY(i);
                    if (mouseX >= boxX && mouseX < boxX + SWATCH && mouseY >= boxY && mouseY < boxY + SWATCH) {
                        GlazedSettings.setColorScheme(schemes[i]);
                        this.click();
                        return true;
                    }
                }
                int mode = this.choiceAt(mouseX, mouseY, this.modeY());
                if (mode >= 0) {
                    GlazedSettings.setThemeMode(mode == 0 ? ConfigTheme.Mode.DARK : ConfigTheme.Mode.LIGHT);
                    this.click();
                    return true;
                }
                int waves = this.choiceAt(mouseX, mouseY, this.wavesY());
                if (waves >= 0) {
                    GlazedSettings.setAnimatedWaves(waves == 0);
                    this.click();
                    return true;
                }
            }
            case DONE -> {
                if (mouseY < this.gridTop || mouseY >= this.gridTop + this.gridHeight) return false;
                for (int i = 0; i < this.screens.size(); i++) {
                    int x = this.cardX(i);
                    int y = this.cardY(i);
                    if (mouseX >= x && mouseX < x + this.cardWidth && mouseY >= y && mouseY < y + this.cardHeight) {
                        GlazedSettings.Switch toggle = this.screens.get(i).toggle;
                        int before = variant(this.screens.get(i));
                        toggle.set().accept(!toggle.get().getAsBoolean());
                        this.startCardWipe(i, before, System.nanoTime());
                        this.click();
                        return true;
                    }
                }
            }
            default -> {
                if (!inColumn) return false;
                List<Option> options = page.options;
                for (int i = 0; i < options.size(); i++) {
                    int y = this.optionsTop + i * (this.optionHeight + OPTION_GAP);
                    if (mouseX >= this.textLeft && mouseX < this.textLeft + this.textWidth && mouseY >= y && mouseY < y + this.optionHeight) {
                        this.choose(options.get(i));
                        if (doubleClick) this.next();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.columnMax > 0 && this.inColumn(mouseX, mouseY)) {
            this.columnScroll = Math.clamp(this.columnScroll - scrollY * 20, 0, this.columnMax);
            this.applyColumnScroll();
            return true;
        }
        if (this.page().kind == Kind.DONE && this.contentHeight > this.gridHeight) {
            this.scroll = Math.clamp(this.scroll - scrollY * (this.cardHeight + CARD_GAP) / 2.0, 0, this.contentHeight - this.gridHeight);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isLeaving()) return true;
        switch (event.key()) {
            case KEY_LEFT -> {
                this.back();
                return true;
            }
            case KEY_RIGHT -> {
                if (this.page().kind != Kind.DONE) this.next();
                return true;
            }
            case KEY_UP, KEY_DOWN -> {
                List<Option> options = this.page().options;
                if (options.isEmpty()) break;
                int chosen = 0;
                for (int i = 0; i < options.size(); i++) if (options.get(i).chosen().getAsBoolean()) chosen = i;
                int moved = Math.clamp(chosen + (event.key() == KEY_DOWN ? 1 : -1), 0, options.size() - 1);
                if (moved != chosen) this.choose(options.get(moved));
                return true;
            }
            case KEY_ENTER, KEY_NUMPAD_ENTER -> {
                if (!(this.getFocused() instanceof FlatButton)) {
                    this.next();
                    return true;
                }
            }
            default -> {}
        }
        return super.keyPressed(event);
    }
}
