package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.api.editor.ConfigEditors;
import net.ixdarklord.glazedmenu.api.editor.ValueEditor;
import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.ConfigEditSession;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.modlist.GlazedModsScreen;
import net.ixdarklord.glazedmenu.internal.modlist.ModCatalog;
import net.ixdarklord.glazedmenu.internal.modlist.ModUpdates;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.DropdownScreen;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.GuiFade;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.ixdarklord.glazedmenu.internal.style.ThemeEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Dialogs;
import net.minecraft.tags.DialogTags;
import net.minecraft.util.CommonLinks;
import net.minecraft.world.timeline.Timelines;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The pause screen, in place of the game's own: Back to Game, the screens a pause leads to (advancements, statistics,
 * the options, the mods, the world's options, player reporting, the friends list, feedback, and what a server adds), a few quick settings
 * applied as they're changed, and Save and Quit set apart from the rest. It's laid out one of three ways, as the
 * player chose in Glazed Menu's settings:
 * <ul>
 * <li>Dock: a bar across the top and a dock of tiles along the bottom, the world open between them;</li>
 * <li>Cards: a frame in the middle of the screen, its buttons as cards;</li>
 * <li>Sidebar: a panel down the left side, its buttons as a list, with the world's status beside it.</li>
 * </ul>
 * It's a pause screen to the game (which asks, here and there, whether one is open), so it extends the game's.
 */
public final class GlazedPauseScreen extends PauseScreen {
    private static final String VANILLA = "net.minecraft.client.gui.screens.PauseScreen";
    private static final Identifier DRAFT_REPORT_SPRITE = Identifier.withDefaultNamespace("icon/draft_report");
    private static final int MARGIN = 10;
    private static final int GAP = 6;
    private static final int BAR_HEIGHT = 28;
    // A taller header than a plain bar, as on the list of every mod's configs.
    private static final int HEADER_HEIGHT = 46;
    private static final int HEADER_ICON = 32;
    private static final int TILE_GAP = 4;
    private static final int TILE_MIN_WIDTH = 78;
    private static final int TILE_HEIGHT = 44;
    private static final int TILE_SHORT = 28;
    private static final int CARD_HEIGHT = 30;
    private static final int QUICK_HEIGHT = 24;
    private static final int QUICK_MIN_WIDTH = 112;
    private static final float FADE_IN_MILLIS = 220;
    // The quick settings, by the name the game gives each option.
    private static final List<String> QUICK = List.of("fov", "renderDistance", "guiScale", "volume_master");
    private static final List<String> QUICK_FEW = List.of("volume_master", "fov");
    // How long a quick setting waits before it's applied and saved, in ticks: the next tick, so what a slider being
    // dragged changes (the field of view, the volume) is seen and heard as it moves.
    private static final int SAVE_DELAY = 1;

    private final ConfigTheme theme = ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME);
    // The game's options, for the quick settings; none while the player keeps them out of Glazed Menu's configs.
    private final @Nullable Config options;
    private final @Nullable ConfigEditSession session;
    private final List<Box> boxes = new ArrayList<>();
    private final List<QuickCell> quick = new ArrayList<>();
    private GlazedSettings.PauseLayout layout = GlazedSettings.PauseLayout.DOCK;
    private @Nullable Box header;
    private boolean headerTall;
    private int headerTextRight;
    private @Nullable Box statusCard;
    private @Nullable Box tagsBar;
    private @Nullable FlatButton quitButton;
    private int hintY = -1;
    private int quickHeadingY = -1;
    private int unsavedFor;
    private boolean askedStats;
    private String notes = "";
    private int noteTicks;
    private int unsavedCount;
    private long openedAt;
    // A link picked from the feedback menu, opened on the next tick (once the menu has closed).
    private @Nullable URI pendingLink;

    private GlazedPauseScreen() {
        super(true);
        Config options = null;
        if (GlazedSettings.gameOptions()) {
            for (Config config : ConfigSources.forMod("minecraft")) {
                if (config.name().equals("options")) options = config;
            }
        }
        this.options = options;
        this.session = options != null ? new ConfigEditSession(options) : null;
    }

    /** The screen to open instead: this one for the game's own pause menu, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof PauseScreen pause) || !pause.showsPauseMenu() || !screen.getClass().getName().equals(VANILLA)) return screen;
        if (!GlazedSettings.replacePause() || Minecraft.getInstance().player == null) return screen;
        return new GlazedPauseScreen();
    }

    // --- What it holds ---

    // A button of the pause screen: where it leads, in a color of its own.
    // A button of the game's that's there but off (the friends list where there's none to open, reporting with no one to
    // report) is here and off too, saying why where the game does.
    private record Entry(ConfigIcons.Icon icon, int color, Component label, Component note, boolean marked, Consumer<AbstractWidget> open, boolean active,
                         @Nullable Component tooltip) {
        Entry(ConfigIcons.Icon icon, int color, Component label, Component note, boolean marked, Consumer<AbstractWidget> open) {
            this(icon, color, label, note, marked, open, true, null);
        }
    }

    private record Link(Component name, URI uri) {}

    private record Box(int x, int y, int width, int height, boolean panel, boolean accent) {}

    private record QuickCell(ConfigValue<?> value, ValueEditor editor, int x, int y, int width) {}

    private enum Form {
        /** An icon over its name, as in a dock. */
        TILE,
        /** An icon in a box beside its name and a note. */
        CARD,
        /** A line of a list. */
        ROW
    }

    // How many advancements are made of how many there are; the plain words before the game has told of any.
    private Component advancementsNote() {
        int[] counts = this.minecraft.player == null ? new int[2] : GlazedAdvancementsScreen.counts(this.minecraft.player.connection.getAdvancements());
        return counts[1] == 0 ? Component.translatableWithFallback("glazedmenu.pause.note.advancements", "Goals and progress")
                : Component.translatableWithFallback("glazedmenu.pause.note.advancements.count", "%s / %s", counts[0], counts[1]);
    }

    // The mobs killed, as the statistics last told by the server have them (asked for as this opens); short, to fit
    // beside the name in every layout.
    private Component statsNote() {
        if (this.minecraft.player == null) return Component.translatableWithFallback("glazedmenu.pause.note.stats", "Blocks, items, mobs");
        int kills = this.minecraft.player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
        return Component.translatableWithFallback("glazedmenu.pause.note.stats.count", "%s kills", kills);
    }

    // The time played: this session, and in all (the world's own count of it, where that's more than Glazed Menu's).
    private @Nullable Component[] times() {
        String key = LoadingLook.playingKey(this.minecraft);
        if (key == null) return null;
        if (this.minecraft.hasSingleplayerServer() && this.minecraft.player != null) {
            PlayTimes.atLeast(key, this.minecraft.player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) * 50L);
        }
        return new Component[]{PlayTimes.format(PlayTimes.session()), PlayTimes.format(PlayTimes.total(key))};
    }

    private List<Entry> entries() {
        Minecraft minecraft = this.minecraft;
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(ConfigIcons.TROPHY, GlazedBrand.identity(0xFFF2C94C), Component.translatable("gui.advancements"),
                this.advancementsNote(), false,
                widget -> minecraft.gui.setScreen(new AdvancementsScreen(minecraft.player.connection.getAdvancements(), this))));
        entries.add(new Entry(ConfigIcons.CHART, GlazedBrand.identity(0xFF4DA3FF), Component.translatable("gui.stats"),
                this.statsNote(), false,
                widget -> minecraft.gui.setScreen(new StatsScreen(this, minecraft.player.getStats()))));

        long changed = this.options != null && this.session != null
                ? this.options.values().filter(value -> this.session.isVisible(value) && !this.session.isDefault(value)).count() : 0;
        entries.add(new Entry(ConfigIcons.SLIDERS, GlazedBrand.identity(0xFF8B7CF6), Component.translatable("menu.options"),
                changed > 0 ? Component.translatableWithFallback("glazedmenu.pause.note.options.changed", "%s changed", changed)
                        : Component.translatableWithFallback("glazedmenu.pause.note.options", "The game's settings"), false,
                widget -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options))));

        long mods = ModCatalog.all().stream().filter(mod -> !mod.library() && !ModCatalog.isChild(mod)).count();
        long updates = ModUpdates.count();
        entries.add(new Entry(ConfigIcons.GRID, GlazedBrand.identity(0xFF3CC8B4), Component.translatableWithFallback("glazedmenu.mods.button", "Mods"),
                updates > 0 ? Component.translatableWithFallback("glazedmenu.pause.note.mods.updates", "%s updates", updates)
                        : Component.translatableWithFallback("glazedmenu.pause.note.mods", "%s installed", mods), updates > 0,
                widget -> minecraft.gui.setScreen(new GlazedModsScreen(this))));

        // Since 26.3 a world is opened to LAN from its World Options (with its difficulty and game rules), under the game's
        // own name for them; the players are reported from here, as the game has it: not while alone in a world of one's own.
        if (minecraft.level != null) {
            entries.add(new Entry(ConfigIcons.LAN, GlazedBrand.identity(0xFF5BC467), Component.translatable("options.worldOptions.button"),
                    Component.translatableWithFallback("glazedmenu.pause.note.world", "Difficulty, rules, LAN"), false,
                    widget -> minecraft.gui.setScreen(new WorldOptionsScreen(this, minecraft.level))));
        }
        boolean alone = minecraft.getSingleplayerServer() != null
                && minecraft.player.connection.getSeenPlayers().keySet().stream().allMatch(id -> id.equals(minecraft.player.getUUID()));
        entries.add(new Entry(ConfigIcons.PERSON, GlazedBrand.identity(0xFFE86BA8), Component.translatable("menu.playerReporting"),
                Component.translatableWithFallback("glazedmenu.pause.note.reporting", "The players here"), false,
                widget -> minecraft.gui.setScreen(new SocialInteractionsScreen(this)), !alone, alone ? Component.translatable("menu.playerReporting.no_players") : null));
        // The friends list (the game's since 26.2), which the game asks to turn on first where it's off: off where the
        // game's own button is (a demo, a game that isn't signed in), and saying how many ask to be friends.
        int requests = minecraft.getPlayerSocialManager().isFriendListEnabled() ? minecraft.getPlayerSocialManager().getIncomingRequests().size() : 0;
        entries.add(new Entry(ConfigIcons.PERSON, GlazedBrand.identity(0xFFF07A5A), Component.translatable("gui.friends.open"),
                requests > 0 ? Component.translatableWithFallback("glazedmenu.pause.note.friends.requests", "%s requests", requests)
                        : Component.translatableWithFallback("glazedmenu.pause.note.friends", "Play together"), requests > 0,
                widget -> OnlineOptionsScreen.confirmFriendsListEnabled(minecraft, () -> minecraft.gui.setScreen(new FriendsOverlayScreen(this)), this),
                !minecraft.isDemo() && !minecraft.isOfflineDeveloperMode(), null));

        // Named by Glazed Menu itself: the game dropped its own name for this (menu.feedback) in 26.3.
        entries.add(new Entry(ConfigIcons.CHAT, GlazedBrand.identity(0xFF9AA4B8), Component.translatableWithFallback("glazedmenu.pause.feedback", "Feedback"),
                Component.translatableWithFallback("glazedmenu.pause.note.feedback", "Ideas and bugs"), false, this::openFeedback));
        // What the server adds: one of its dialogs, a list of them, or its links.
        this.customAdditions().ifPresent(dialog -> entries.add(new Entry(ConfigIcons.GLOBE, GlazedBrand.identity(0xFF4CC9E0), dialog.value().common().computeExternalTitle(),
                Component.translatableWithFallback("glazedmenu.pause.note.server", "From the server"), false,
                widget -> minecraft.player.connection.showDialog(dialog, this))));
        return entries;
    }

    // As the game's pause screen finds them.
    private Optional<? extends Holder<Dialog>> customAdditions() {
        Registry<Dialog> dialogs = this.minecraft.player.connection.registryAccess().lookupOrThrow(Registries.DIALOG);
        Optional<? extends HolderSet<Dialog>> additions = dialogs.get(DialogTags.PAUSE_SCREEN_ADDITIONS);
        if (additions.isPresent() && additions.get().size() > 0) {
            return additions.get().size() == 1 ? Optional.of(additions.get().get(0)) : dialogs.get(Dialogs.CUSTOM_OPTIONS);
        }
        ServerLinks links = this.minecraft.player.connection.serverLinks();
        return !links.isEmpty() ? dialogs.get(Dialogs.SERVER_LINKS) : Optional.empty();
    }

    // The game's two feedback links, as a menu dropping from the button.
    private void openFeedback(AbstractWidget widget) {
        List<Link> links = new ArrayList<>();
        links.add(new Link(Component.translatable("menu.sendFeedback"),
                SharedConstants.getCurrentVersion().stable() ? CommonLinks.RELEASE_FEEDBACK : CommonLinks.SNAPSHOT_FEEDBACK));
        if (!SharedConstants.getCurrentVersion().dataVersion().isSideSeries()) {
            links.add(new Link(Component.translatable("menu.reportBugs"), CommonLinks.SNAPSHOT_BUGS_FEEDBACK));
        }
        this.minecraft.gui.setScreen(new DropdownScreen<>(this, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), links, links.getFirst(),
                Link::name, link -> null, link -> this.pendingLink = link.uri));
    }

    private void backToGame() {
        this.saveQuick();
        this.minecraft.gui.setScreen(null);
        this.minecraft.mouseHandler.grabMouse();
    }

    private void quit(AbstractWidget button) {
        this.saveQuick();
        button.active = false;
        this.minecraft.getReportingContext().draftReportHandled(this.minecraft, this,
                () -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
    }

    // --- Layout ---

    @Override
    protected void init() {
        ConfigStyle.use(this.theme);
        this.boxes.clear();
        this.quick.clear();
        this.header = null;
        this.statusCard = null;
        this.tagsBar = null;
        this.hintY = -1;
        this.quickHeadingY = -1;
        this.layout = GlazedSettings.pauseLayout();
        List<Entry> entries = this.entries();
        // Back to Game first, so it's the first the Tab key reaches.
        FlatButton back = this.addRenderableWidget(new FlatButton(150, 24, Component.translatable("menu.returnToGame"), button -> this.backToGame())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.PLAY));
        Component quitLabel = CommonComponents.disconnectButtonLabel(this.minecraft.isLocalServer());
        this.quitButton = FlatButton.of(quitLabel, this.font.width(quitLabel) + 28, this::quit).style(FlatButton.Style.DANGER).withIcon(ConfigIcons.EXIT);
        switch (this.layout) {
            case CARDS -> this.initCards(entries, back);
            case SIDEBAR -> this.initSidebar(entries, back);
            default -> this.initDock(entries, back);
        }
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    private Box box(int x, int y, int width, int height, boolean panel, boolean accent) {
        Box box = new Box(x, y, width, height, panel, accent);
        this.boxes.add(box);
        return box;
    }

    // A bar across the top, Back to Game alone in the middle, and a dock along the bottom: the quick settings over the tiles.
    private void initDock(List<Entry> entries, FlatButton back) {
        int left = MARGIN;
        int frame = this.width - MARGIN * 2;
        this.headerTall = this.height >= 230;
        int headerHeight = this.headerTall ? HEADER_HEIGHT : BAR_HEIGHT;
        this.header = this.box(left, MARGIN, frame, headerHeight, false, false);

        int inner = frame - 10;
        int count = entries.size();
        int columns = Math.clamp((inner + TILE_GAP) / (TILE_MIN_WIDTH + TILE_GAP), 1, count);
        int rows = (count + columns - 1) / columns;
        columns = (count + rows - 1) / rows;
        List<ConfigValue<?>> values = this.quickValues(QUICK);
        int quickColumns = values.isEmpty() ? 1
                : (inner - TILE_GAP * (values.size() - 1)) / values.size() >= QUICK_MIN_WIDTH ? values.size() : Math.min(2, values.size());
        int quickRows = (values.size() + quickColumns - 1) / quickColumns;
        // What's left under the top bar, keeping room for Back to Game: shorter tiles first, then no quick settings.
        int room = this.height - MARGIN * 2 - headerHeight - GAP * 2 - 44;
        int tileHeight = TILE_HEIGHT;
        if (dockHeight(rows, tileHeight, quickRows) > room) tileHeight = TILE_SHORT;
        if (dockHeight(rows, tileHeight, quickRows) > room) quickRows = 0;
        int dockHeight = dockHeight(rows, tileHeight, quickRows);
        int dockY = this.height - MARGIN - dockHeight;
        this.box(left, dockY, frame, dockHeight, true, true);

        int y = dockY + 6;
        if (quickRows > 0) y = this.placeQuick(values, left + 5, y, inner, quickColumns);
        this.placeGrid(entries, Form.TILE, left + 5, y, inner, columns, tileHeight, TILE_GAP);

        int top = MARGIN + headerHeight + GAP;
        int bottom = dockY - GAP;
        boolean hint = bottom - top >= 50;
        int panelWidth = 160;
        int panelHeight = 10 + 24 + (hint ? 12 : 0);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = top + Math.max(0, (bottom - top - panelHeight) / 2);
        this.box(panelX, panelY, panelWidth, panelHeight, false, false);
        back.setPosition(panelX + 5, panelY + 5);
        if (hint) this.hintY = panelY + 5 + 24 + 3;

        int widgetY = MARGIN + (headerHeight - 20) / 2;
        this.quitButton.setPosition(left + frame - 6 - this.quitButton.getWidth(), widgetY);
        this.addRenderableWidget(this.quitButton);
        FlatButton toggle = this.addRenderableWidget(StyledScreen.modeToggle());
        toggle.setPosition(this.quitButton.getX() - 4 - 20, widgetY);
        this.headerTextRight = toggle.getX() - 6;
    }

    private static int dockHeight(int rows, int tileHeight, int quickRows) {
        return 12 + rows * tileHeight + (rows - 1) * TILE_GAP + quickRows * (QUICK_HEIGHT + TILE_GAP);
    }

    // A frame in the middle: a header, a panel of Back to Game, the cards and two quick settings, and a bar with Save and Quit.
    private void initCards(List<Entry> entries, FlatButton back) {
        List<ConfigValue<?>> values = this.quickValues(QUICK_FEW);
        int count = entries.size();
        // The roomiest arrangement that fits: two columns of cards, then three in a wider frame, then without the
        // quick settings' heading, without the quick settings, and with a plain bar for a header.
        int[][] arrangements = {{2, 1, 1, 1}, {3, 1, 1, 1}, {3, 0, 1, 1}, {3, 0, 0, 1}, {3, 0, 0, 0}};
        int columns = 2;
        int rows = 1;
        boolean heading = false;
        boolean quick = false;
        int frame = 0;
        int body = 0;
        int total = 0;
        for (int[] arrangement : arrangements) {
            columns = Math.min(arrangement[0], count);
            heading = arrangement[1] == 1;
            quick = arrangement[2] == 1 && !values.isEmpty();
            this.headerTall = arrangement[3] == 1;
            rows = (count + columns - 1) / columns;
            frame = Math.min(this.width - MARGIN * 2, columns <= 2 ? Math.clamp(this.width * 3 / 5, 250, 340) : 400);
            body = 6 + 24 + 6 + rows * CARD_HEIGHT + (rows - 1) * TILE_GAP + (quick ? (heading ? 18 : 6) + QUICK_HEIGHT : 0) + 6;
            total = (this.headerTall ? HEADER_HEIGHT : BAR_HEIGHT) + GAP + body + GAP + BAR_HEIGHT;
            if (total <= this.height - 12) break;
        }
        int headerHeight = this.headerTall ? HEADER_HEIGHT : BAR_HEIGHT;
        int left = (this.width - frame) / 2;
        int top = Math.max(4, (this.height - total) / 2);
        this.header = this.box(left, top, frame, headerHeight, false, false);
        int bodyY = top + headerHeight + GAP;
        this.box(left, bodyY, frame, body, true, true);
        int barY = bodyY + body + GAP;
        this.tagsBar = this.box(left, barY, frame, BAR_HEIGHT, false, false);

        int inner = frame - 12;
        back.setWidth(inner);
        back.setPosition(left + 6, bodyY + 6);
        int y = this.placeGrid(entries, Form.CARD, left + 6, bodyY + 36, inner, columns, CARD_HEIGHT, TILE_GAP);
        if (quick) {
            if (heading) {
                this.quickHeadingY = y + 2;
                y += 14;
            } else {
                y += 2;
            }
            this.placeQuick(values, left + 6, y, inner, Math.min(2, values.size()));
        }

        FlatButton toggle = this.addRenderableWidget(StyledScreen.modeToggle());
        toggle.setPosition(left + frame - 6 - 20, top + (headerHeight - 20) / 2);
        this.headerTextRight = toggle.getX() - 6;
        this.quitButton.setPosition(left + frame - 4 - this.quitButton.getWidth(), barY + (BAR_HEIGHT - 20) / 2);
        this.addRenderableWidget(this.quitButton);
    }

    // A panel down the left side: a header, Back to Game over the list, and Save and Quit in its bottom bar. Beside it,
    // where there's room, the world's status at the top and two quick settings at the bottom.
    private void initSidebar(List<Entry> entries, FlatButton back) {
        int left = MARGIN;
        int side = Math.min(Math.clamp(this.width * 2 / 5, 150, 190), this.width - MARGIN * 2);
        this.headerTall = this.height >= 300;
        int headerHeight = this.headerTall ? HEADER_HEIGHT : BAR_HEIGHT;
        this.header = this.box(left, MARGIN, side, headerHeight, false, false);
        int barY = this.height - MARGIN - BAR_HEIGHT;
        this.box(left, barY, side, BAR_HEIGHT, false, false);
        int bodyY = MARGIN + headerHeight + GAP;
        int bodyBottom = barY - GAP;
        this.box(left, bodyY, side, bodyBottom - bodyY, true, true);

        int inner = side - 12;
        back.setWidth(inner);
        back.setHeight(22);
        back.setPosition(left + 6, bodyY + 6);
        // The rows share what's left of the panel, within reason. Where the window is too short for them at their
        // least (eight rows or more at the smallest window), Back to Game is lower and the rows are thinner and closer,
        // so the last of them stays inside the panel.
        int rowGap = 2;
        int rowsTop = bodyY + 6 + 22 + 5;
        int rowHeight = (bodyBottom - 6 - rowsTop + rowGap) / entries.size() - rowGap;
        if (rowHeight < 14) {
            back.setHeight(18);
            rowGap = 1;
            rowsTop = bodyY + 6 + 18 + 4;
            rowHeight = (bodyBottom - 6 - rowsTop + rowGap) / entries.size() - rowGap;
        }
        rowHeight = Math.clamp(rowHeight, 11, 22);
        this.placeGrid(entries, Form.ROW, left + 6, rowsTop, inner, 1, rowHeight, rowGap);

        FlatButton toggle = this.addRenderableWidget(StyledScreen.modeToggle());
        toggle.setPosition(left + side - 4 - 20, MARGIN + (headerHeight - 20) / 2);
        this.headerTextRight = toggle.getX() - 4;
        this.quitButton.setWidth(side - 8);
        this.quitButton.setPosition(left + 4, barY + (BAR_HEIGHT - 20) / 2);
        this.addRenderableWidget(this.quitButton);

        int room = this.width - MARGIN - (left + side + GAP);
        int statusBottom = MARGIN;
        if (room >= 120) {
            int width = Math.min(170, room);
            this.statusCard = this.box(this.width - MARGIN - width, MARGIN, width, 8 + 10 + 4 + 11 + 5 + 5 * 11 + 4, true, false);
            statusBottom = this.statusCard.y + this.statusCard.height;
        }
        List<ConfigValue<?>> values = this.quickValues(QUICK_FEW);
        if (!values.isEmpty() && room >= 130) {
            // Side by side where they fit, else one over the other.
            boolean wide = room >= values.size() * (QUICK_MIN_WIDTH + 12) + 10;
            int columns = wide ? values.size() : 1;
            int rows = (values.size() + columns - 1) / columns;
            int width = wide ? Math.min(room, values.size() * 150 + 10) : Math.min(room, 180);
            int height = 10 + rows * QUICK_HEIGHT + (rows - 1) * TILE_GAP;
            int y = this.height - MARGIN - height;
            if (y >= statusBottom + GAP) {
                this.box(this.width - MARGIN - width, y, width, height, false, false);
                this.placeQuick(values, this.width - MARGIN - width + 5, y + 5, width - 10, columns);
            }
        }
    }

    // The entries' buttons in rows of so many columns, each row filled to the width; returns the y under them.
    private int placeGrid(List<Entry> entries, Form form, int x, int y, int width, int columns, int height, int gap) {
        for (int first = 0; first < entries.size(); first += columns) {
            int count = Math.min(columns, entries.size() - first);
            int each = (width - gap * (count - 1)) / count;
            for (int i = 0; i < count; i++) {
                // The last of a row takes what the division left over.
                int buttonWidth = i == count - 1 ? width - i * (each + gap) : each;
                EntryButton button = this.addRenderableWidget(new EntryButton(entries.get(first + i), form, buttonWidth, height));
                button.setPosition(x + i * (each + gap), y);
            }
            y += height + gap;
        }
        return y;
    }

    private List<ConfigValue<?>> quickValues(List<String> names) {
        List<ConfigValue<?>> values = new ArrayList<>();
        if (this.options == null || this.session == null) return values;
        for (String name : names) {
            this.options.values().filter(value -> value.path().endsWith("." + name) && this.session.isVisible(value)).findFirst().ifPresent(values::add);
        }
        return values;
    }

    // The quick settings' cells in rows of so many columns, each its name and its editor; returns the y under them.
    private int placeQuick(List<ConfigValue<?>> values, int x, int y, int width, int columns) {
        int each = (width - TILE_GAP * (columns - 1)) / columns;
        int editorWidth = Math.clamp(each / 2, 56, 110);
        for (int i = 0; i < values.size(); i++) {
            int cellX = x + i % columns * (each + TILE_GAP);
            int cellY = y + i / columns * (QUICK_HEIGHT + TILE_GAP);
            ValueEditor editor = this.editor(values.get(i), editorWidth);
            AbstractWidget widget = this.addRenderableWidget(editor.widget());
            widget.setPosition(cellX + each - 3 - widget.getWidth(), cellY + (QUICK_HEIGHT - widget.getHeight()) / 2);
            this.quick.add(new QuickCell(values.get(i), editor, cellX, cellY, each));
        }
        return y + (values.size() + columns - 1) / columns * (QUICK_HEIGHT + TILE_GAP);
    }

    private <T> ValueEditor editor(ConfigValue<T> value, int width) {
        return ConfigEditors.create(this.session.slot(value), width, 20);
    }

    // --- The quick settings' edits ---

    // A quick setting is applied once it has rested a moment, so a slider being dragged isn't saved at every step.
    @Override
    public void tick() {
        super.tick();
        if (this.pendingLink != null) {
            URI link = this.pendingLink;
            this.pendingLink = null;
            ConfirmLinkScreen.confirmLinkNow(this, link);
            return;
        }
        // The statistics are asked of the server as the pause opens; the notes that count are made anew as they change.
        if (!this.askedStats) {
            this.askedStats = true;
            ClientPacketListener connection = this.minecraft.getConnection();
            if (connection != null) connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
            this.notes = this.advancementsNote().getString() + this.statsNote().getString();
        } else if (++this.noteTicks % 20 == 0) {
            String now = this.advancementsNote().getString() + this.statsNote().getString();
            if (!now.equals(this.notes)) {
                this.notes = now;
                this.rebuildWidgets();
                return;
            }
        }
        if (this.session == null) return;
        int unsaved = this.session.modifiedCount();
        if (unsaved == 0 || this.session.hasErrors()) {
            this.unsavedFor = 0;
            this.unsavedCount = 0;
            return;
        }
        this.unsavedFor = unsaved == this.unsavedCount ? this.unsavedFor + 1 : 0;
        this.unsavedCount = unsaved;
        if (this.unsavedFor >= SAVE_DELAY) this.saveQuick();
    }

    private void saveQuick() {
        if (this.session == null || this.session.modifiedCount() == 0 || this.session.hasErrors()) return;
        this.session.save();
        this.unsavedFor = 0;
        this.unsavedCount = 0;
    }

    // Leaving for another screen: a quick setting just changed isn't lost.
    @Override
    public void removed() {
        this.saveQuick();
        super.removed();
    }

    // Opened, or back from another screen: the quick settings show what's set now.
    @Override
    public void added() {
        super.added();
        ConfigStyle.use(this.theme);
        this.openedAt = System.nanoTime();
        if (this.session != null) {
            this.session.onConfigChanged();
            for (QuickCell cell : this.quick) cell.editor.refresh();
        }
    }

    // --- Drawing ---

    // How visible the page is as it opens: a smooth ease-in-out, as the other screens fade in.
    private float fadeAlpha() {
        if (!GlazedSettings.transitions()) return 1;
        float progress = Math.min(1, (System.nanoTime() - this.openedAt) / 1_000_000F / FADE_IN_MILLIS);
        return progress * progress * progress * (progress * (progress * 6 - 15) + 10);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        // Not under one of the game's own screens that draw this one behind them (its friends list, and the question that
        // list asks): those blur for themselves, and the game blurs once a frame.
        Screen open = this.minecraft.gui.screen();
        if (open == this || open != null && open.getClass().getName().startsWith("net.ixdarklord.glazedmenu.")) ConfigStyle.background(graphics, this.width, this.height, () -> {});
        ThemeEffects.background(graphics, this.width, this.height, mouseX, mouseY, a);
        GuiFade.set(this.fadeAlpha());
        this.extractPanels(graphics);
        GuiFade.reset();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        GuiFade.set(this.fadeAlpha());
        super.extractRenderState(graphics, mouseX, mouseY, a);
        // A report left unfinished, marked on the button that would drop it (as on the game's pause screen).
        if (this.quitButton != null && this.minecraft.getReportingContext().hasDraftReport()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DRAFT_REPORT_SPRITE, this.quitButton.getX() + this.quitButton.getWidth() - 17, this.quitButton.getY() + 3, 15, 15);
        }
        GuiFade.reset();
        ThemeEffects.foreground(graphics, this.width, this.height, mouseX, mouseY, a);
    }

    private void extractPanels(GuiGraphicsExtractor graphics) {
        for (Box box : this.boxes) {
            if (box.panel) ConfigStyle.panel(graphics, box.x, box.y, box.width, box.height);
            else ConfigStyle.bar(graphics, box.x, box.y, box.width, box.height);
            // Glazed Menu's flowing gradient along the top edge, as on its mod list.
            if (box.accent) GlazedBrand.fill(graphics, box.x + 1, box.y, box.width - 2, 2, 0xD0);
        }
        this.extractHeader(graphics);
        if (this.hintY >= 0) {
            ConfigStyle.centeredText(graphics, this.font, Component.translatableWithFallback("glazedmenu.pause.hint", "or press Esc"),
                    this.width / 2, this.hintY, 150, ConfigStyle.colors().textMuted());
            // The time played, on a plate of its own under the panel.
            Component[] times = this.times();
            if (times != null) {
                Component line = Component.translatableWithFallback("glazedmenu.pause.times", "Session %s · Total %s", times[0], times[1]);
                // As wide as the dock under it at most; a line longer than that is drawn smaller, whole.
                int most = Math.min(this.width - 16, 300);
                int plate = Math.min(most, this.font.width(line) + 14);
                int plateY = this.hintY + 17;
                ConfigStyle.rect(graphics, (this.width - plate) / 2, plateY, plate, 13, ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xC8));
                this.extractFitted(graphics, line, this.width / 2, plateY + 6, plate - 8, ConfigStyle.colors().textDim());
            }
        }
        if (this.quickHeadingY >= 0 && !this.quick.isEmpty()) {
            QuickCell first = this.quick.getFirst();
            Component name = Component.translatableWithFallback("glazedmenu.options.section.quick", "Quick Settings").copy().withStyle(ChatFormatting.BOLD);
            int x = first.x + 2;
            int right = this.quick.getLast().x + this.quick.getLast().width - 2;
            ConfigStyle.text(graphics, this.font, name, x, this.quickHeadingY, right - x, ConfigStyle.colors().textDim());
            graphics.fill(Math.min(right, x + this.font.width(name) + 6), this.quickHeadingY + 4, right, this.quickHeadingY + 5, ConfigStyle.colors().panelBorder());
        }
        for (QuickCell cell : this.quick) this.extractQuickCell(graphics, cell);
        if (this.tagsBar != null) this.extractTags(graphics, this.tagsBar.x + 8, this.tagsBar.y + (this.tagsBar.height - 11) / 2, false);
        if (this.statusCard != null) this.extractStatus(graphics, this.statusCard);
    }

    // The header: a mark in the flowing gradient, the title in it too, and the world under it; in a plain bar, just the
    // mark and the title.
    private void extractHeader(GuiGraphicsExtractor graphics) {
        Box header = this.header;
        if (header == null) return;
        if (!this.headerTall) {
            ConfigIcons.PAUSE.draw(graphics, header.x + 8, header.y + (header.height - 12) / 2, 12, ConfigStyle.accent());
            ConfigStyle.text(graphics, this.font, this.title.copy().withStyle(ChatFormatting.BOLD), header.x + 25, header.y + (header.height - 8) / 2,
                    this.headerTextRight - header.x - 25, ConfigStyle.colors().text());
            return;
        }
        int iconX = header.x + 8;
        int iconY = header.y + (header.height - HEADER_ICON) / 2;
        GlazedBrand.fill(graphics, iconX, iconY, HEADER_ICON, HEADER_ICON, 0xFF, 0.3F);
        ConfigStyle.outline(graphics, iconX, iconY, HEADER_ICON, HEADER_ICON, ConfigStyle.mix(GlazedBrand.now(0.5F), 0xFFFFFFFF, 0.5F));
        ConfigIcons.PAUSE.draw(graphics, iconX + 8, iconY + 8, HEADER_ICON - 16, 0xFFFFFFFF);
        int textX = iconX + HEADER_ICON + 9;
        int textTop = header.y + (header.height - 21) / 2;
        int textWidth = this.headerTextRight - textX;
        int titleWidth = GlazedBrand.text(graphics, this.font, this.title.getString(), textX, textTop);
        Component subtitle = this.subtitle();
        this.extractFittedLeft(graphics, subtitle, textX, textTop + 13, textWidth, ConfigStyle.colors().textMuted());
        // The game mode and the difficulty at the bar's far end, where the text leaves them room.
        if (this.layout == GlazedSettings.PauseLayout.DOCK) {
            int used = textX + Math.max(titleWidth, Math.min(textWidth, this.font.width(subtitle)));
            int tags = this.tagsWidth();
            if (used + 12 + tags <= this.headerTextRight) this.extractTags(graphics, this.headerTextRight - 4, header.y + (header.height - 11) / 2, true);
        }
    }

    private @Nullable String worldName() {
        if (this.minecraft.hasSingleplayerServer()) return this.minecraft.getSingleplayerServer().getWorldData().getLevelName();
        ServerData server = this.minecraft.getCurrentServer();
        return server != null ? server.name : null;
    }

    private @Nullable Component day() {
        ClientLevel level = this.minecraft.level;
        if (level == null) return null;
        return level.registryAccess().get(Timelines.OVERWORLD_DAY)
                .map(timeline -> (Component) Component.translatableWithFallback("glazedmenu.pause.day", "Day %s", timeline.value().getPeriodCount(level.clockManager())))
                .orElse(null);
    }

    // The world's name and its day; beside the sidebar the day is on the status card.
    private Component subtitle() {
        String name = this.worldName();
        Component day = this.layout == GlazedSettings.PauseLayout.SIDEBAR ? null : this.day();
        Component line = name == null ? (day != null ? day : Component.empty()) : day != null ? Component.literal(name + " · ").append(day) : Component.literal(name);
        // The time played, after them; beside the sidebar it's on the status card.
        // (Under Back to Game where that has its hint; in the header's line otherwise.)
        Component[] times = this.layout == GlazedSettings.PauseLayout.SIDEBAR || this.hintY >= 0 ? null : this.times();
        if (times == null) return line;
        return Component.empty().append(line).append(line.getString().isEmpty() ? "" : " · ")
                .append(Component.translatableWithFallback("glazedmenu.pause.times", "Session %s · Total %s", times[0], times[1]));
    }

    private List<Component> tags() {
        List<Component> tags = new ArrayList<>();
        if (this.minecraft.gameMode != null) tags.add(this.minecraft.gameMode.getPlayerMode().getShortDisplayName());
        if (this.minecraft.level != null) tags.add(this.minecraft.level.getLevelData().getDifficulty().getDisplayName());
        return tags;
    }

    private int tagsWidth() {
        return this.tags().stream().mapToInt(tag -> this.font.width(tag) + 8 + 4).sum() - 4;
    }

    // The game mode and the difficulty as tags, from x rightward, or ending at it.
    private void extractTags(GuiGraphicsExtractor graphics, int x, int y, boolean endAt) {
        List<Component> tags = this.tags();
        if (endAt) x -= this.tagsWidth();
        for (int i = 0; i < tags.size(); i++) {
            int color = i == 0 ? ConfigStyle.colors().success() : ConfigStyle.colors().warning();
            x += ConfigStyle.badge(graphics, this.font, tags.get(i), x, y, color) + 4;
        }
    }

    // Beside the sidebar: the world's name, its game mode and difficulty, and where and when the player is.
    private void extractStatus(GuiGraphicsExtractor graphics, Box card) {
        int x = card.x + 8;
        int y = card.y + 8;
        int width = card.width - 16;
        String name = this.worldName();
        if (name != null) ConfigStyle.text(graphics, this.font, Component.literal(name).withStyle(ChatFormatting.BOLD), x, y, width, ConfigStyle.colors().text());
        y += 14;
        this.extractTags(graphics, x, y, false);
        y += 16;
        Component day = this.day();
        if (day != null) y = this.statusLine(graphics, Component.translatableWithFallback("glazedmenu.pause.status.time", "Time"), day, x, y, width);
        if (this.minecraft.player != null && this.minecraft.level != null) {
            BlockPos position = this.minecraft.player.blockPosition();
            y = this.statusLine(graphics, Component.translatableWithFallback("glazedmenu.pause.status.position", "Position"),
                    Component.literal(position.getX() + " " + position.getY() + " " + position.getZ()), x, y, width);
            Component biome = this.minecraft.level.getBiome(position).unwrapKey()
                    .map(key -> (Component) Component.translatable("biome." + key.identifier().getNamespace() + "." + key.identifier().getPath().replace('/', '.')))
                    .orElse(null);
            if (biome != null) y = this.statusLine(graphics, Component.translatableWithFallback("glazedmenu.pause.status.biome", "Biome"), biome, x, y, width);
        }
        Component[] times = this.times();
        if (times != null) {
            y = this.statusLine(graphics, Component.translatableWithFallback("glazedmenu.pause.status.session", "Session"), times[0], x, y, width);
            this.statusLine(graphics, Component.translatableWithFallback("glazedmenu.pause.status.total", "Total"), times[1], x, y, width);
        }
    }

    private int statusLine(GuiGraphicsExtractor graphics, Component name, Component value, int x, int y, int width) {
        ConfigStyle.text(graphics, this.font, name, x, y, width, ConfigStyle.colors().textMuted());
        int room = width - this.font.width(name) - 8;
        int valueWidth = this.font.width(value);
        if (valueWidth <= room) {
            ConfigStyle.text(graphics, this.font, value, x + width - valueWidth, y, room, ConfigStyle.colors().text());
        } else {
            float scale = Math.max(0.6F, room / (float) valueWidth);
            int shown = Math.min(room, Math.round(valueWidth * scale));
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + width - shown, y + 4 - 4 * scale);
            graphics.pose().scale(scale, scale);
            ConfigStyle.text(graphics, this.font, value, 0, 0, Math.round(room / scale), ConfigStyle.colors().text());
            graphics.pose().popMatrix();
        }
        return y + 11;
    }

    private void extractQuickCell(GuiGraphicsExtractor graphics, QuickCell cell) {
        boolean changed = !this.session.isDefault(cell.value);
        ConfigStyle.rect(graphics, cell.x, cell.y, cell.width, QUICK_HEIGHT, ConfigStyle.withAlpha(ConfigStyle.colors().button(), 0xB0));
        ConfigStyle.outline(graphics, cell.x, cell.y, cell.width, QUICK_HEIGHT, ConfigStyle.colors().panelBorder());
        // An option that isn't at its default, marked as the config screens mark one.
        if (changed) graphics.fill(cell.x + 1, cell.y + 1, cell.x + 3, cell.y + QUICK_HEIGHT - 1, ConfigStyle.colors().modified());
        AbstractWidget widget = cell.editor.widget();
        cell.editor.setActive(this.session.isEditable(cell.value) && this.session.isActive(cell.value));
        this.extractQuickName(graphics, cell.value.displayName(), cell.x + 8, cell.y, widget.getX() - cell.x - 13,
                changed ? ConfigStyle.colors().modified() : ConfigStyle.colors().text());
    }

    // A line of text centered on a point, whole: at the font's own size where it fits the room, and drawn smaller
    // where it doesn't (cut short only where even 60% doesn't fit).
    private void extractFitted(GuiGraphicsExtractor graphics, Component text, int centerX, int centerY, int room, int color) {
        int textWidth = this.font.width(text);
        if (textWidth <= room) {
            ConfigStyle.centeredText(graphics, this.font, text, centerX, centerY - 4, room, color);
            return;
        }
        float scale = Math.max(0.6F, room / (float) textWidth);
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        ConfigStyle.centeredText(graphics, this.font, text, 0, -4, Math.round(room / scale), color);
        graphics.pose().popMatrix();
    }

    // The same from a left edge, for the header's line under the title.
    private void extractFittedLeft(GuiGraphicsExtractor graphics, Component text, int x, int y, int room, int color) {
        int textWidth = this.font.width(text);
        if (textWidth <= room || room <= 0) {
            ConfigStyle.text(graphics, this.font, text, x, y, room, color);
            return;
        }
        float scale = Math.max(0.6F, room / (float) textWidth);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + 4 - 4 * scale);
        graphics.pose().scale(scale, scale);
        ConfigStyle.text(graphics, this.font, text, 0, 0, Math.round(room / scale), color);
        graphics.pose().popMatrix();
    }

    // A quick setting's name, whole: on one line where it fits, on two where its words allow, and smaller where it
    // still doesn't (cut short only as the last resort).
    private void extractQuickName(GuiGraphicsExtractor graphics, Component name, int x, int cellY, int room, int color) {
        if (room <= 0) return;
        if (this.font.width(name) <= room) {
            graphics.text(this.font, name, x, cellY + (QUICK_HEIGHT - 8) / 2, color, false);
            return;
        }
        var lines = this.font.split(name, room);
        if (lines.size() == 2) {
            graphics.text(this.font, lines.get(0), x, cellY + 3, color, false);
            graphics.text(this.font, lines.get(1), x, cellY + 13, color, false);
            return;
        }
        float scale = Math.max(0.7F, room / (float) this.font.width(name));
        int scaledRoom = Math.round(room / scale);
        var small = this.font.split(name, scaledRoom);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, cellY);
        graphics.pose().scale(scale, scale);
        if (small.size() <= 1) {
            ConfigStyle.text(graphics, this.font, name, 0, Math.round((QUICK_HEIGHT / scale - 8) / 2), scaledRoom, color);
        } else {
            int top = Math.round((QUICK_HEIGHT / scale - 18) / 2);
            graphics.text(this.font, small.get(0), 0, top, color, false);
            if (small.size() == 2) graphics.text(this.font, small.get(1), 0, top + 10, color, false);
            else ConfigStyle.text(graphics, this.font, Component.literal(name.getString().substring(Math.min(name.getString().length(), this.font.plainSubstrByWidth(name.getString(), scaledRoom).length())).trim()), 0, top + 10, scaledRoom, color);
        }
        graphics.pose().popMatrix();
    }

    // --- The buttons ---

    // An entry's button, drawn in the entry's color in one of the layouts' forms.
    private final class EntryButton extends FlatButton {
        private final Entry entry;
        private final Form form;

        EntryButton(Entry entry, Form form, int width, int height) {
            super(width, height, entry.label, button -> entry.open.accept(button));
            this.entry = entry;
            this.form = form;
            this.active = entry.active;
            if (entry.tooltip != null) this.setTooltip(Tooltip.create(entry.tooltip));
        }

        @Override
        protected ConfigEffect.WidgetKind effectKind() {
            return ConfigEffect.WidgetKind.CARD;
        }

        @Override
        protected void extractButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font font = GlazedPauseScreen.this.font;
            boolean lit = this.active && this.isHoveredOrFocused();
            int color = this.active ? this.entry.color : ConfigStyle.colors().textMuted();
            int textColor = this.active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            int noteColor = this.entry.marked ? ConfigStyle.colors().modified() : ConfigStyle.colors().textDim();
            if (this.form == Form.ROW) {
                ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.mix(ConfigStyle.colors().button(), color, lit ? 0.22F : 0.06F));
                ConfigStyle.outline(graphics, x, y, width, height, lit ? ConfigStyle.withAlpha(color, 0xE0) : ConfigStyle.colors().panelBorder());
                graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ConfigStyle.withAlpha(color, lit ? 0xFF : 0xC0));
                this.entry.icon.draw(graphics, x + 7, y + (height - 10) / 2, color);
                int textX = x + 21;
                int textY = y + (height - 8) / 2;
                // The note at the row's end where the name leaves it room, else an arrow.
                int noteWidth = font.width(this.entry.note);
                if (textX + font.width(this.entry.label) + 8 + noteWidth <= x + width - 6) {
                    ConfigStyle.text(graphics, font, this.entry.label, textX, textY, width, textColor);
                    ConfigStyle.text(graphics, font, this.entry.note, x + width - 6 - noteWidth, textY, noteWidth, this.entry.marked ? noteColor : ConfigStyle.colors().textMuted());
                } else {
                    ConfigStyle.text(graphics, font, this.entry.label, textX, textY, x + width - 16 - textX, textColor);
                    ConfigIcons.CHEVRON.draw(graphics, x + width - 14, y + (height - ConfigIcons.SIZE) / 2, lit ? color : ConfigStyle.colors().textMuted());
                }
                return;
            }
            // The button washed with its color, more under the mouse, and its color along the top edge.
            ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.mix(ConfigStyle.colors().button(), color, lit ? 0.24F : 0.10F));
            ConfigStyle.outline(graphics, x, y, width, height, ConfigStyle.withAlpha(color, lit ? 0xE0 : 0x60));
            graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ConfigStyle.withAlpha(color, lit ? 0xFF : 0xB0));
            if (this.form == Form.CARD) {
                int box = height - 10;
                int boxY = y + 2 + (height - 2 - box) / 2;
                ConfigStyle.iconFrame(graphics, x + 6, boxY, box, box, ConfigStyle.withAlpha(color, lit ? 0xFF : 0xD0));
                this.entry.icon.draw(graphics, x + 6 + 4, boxY + 4, box - 8, ConfigStyle.readableOn(color));
                int textX = x + 6 + box + 6;
                int textWidth = x + width - 6 - textX;
                ConfigStyle.text(graphics, font, this.entry.label.copy().withStyle(ChatFormatting.BOLD), textX, boxY + 1, textWidth, textColor);
                ConfigStyle.text(graphics, font, this.entry.note, textX, boxY + 11, textWidth, noteColor);
            } else if (height >= TILE_HEIGHT) {
                this.entry.icon.draw(graphics, x + (width - 14) / 2, y + 7, 14, color);
                ConfigStyle.centeredText(graphics, font, this.entry.label, x + width / 2, y + 23, width - 6, textColor);
                ConfigStyle.centeredText(graphics, font, this.entry.note, x + width / 2, y + 33, width - 6, noteColor);
            } else {
                // A short tile: the icon and the name on one line, and after them a note that counts (the
                // advancements made, the mods' updates); the line is drawn smaller where the tile is too narrow for it.
                int noteWidth = font.width(this.entry.note);
                boolean counted = this.entry.note.getString().matches(".*\\d.*");
                int full = 14 + font.width(this.entry.label) + (counted ? 8 + noteWidth : 0);
                float scale = Math.clamp((width - 12) / (float) full, 0.7F, 1);
                int room = Math.round((width - 12) / scale);
                int textWidth = Math.min(font.width(this.entry.label), room - 14 - (counted ? 8 + noteWidth : 0));
                int shown = 14 + textWidth + (counted ? 8 + noteWidth : 0);
                graphics.pose().pushMatrix();
                graphics.pose().translate(x + width / 2F, y + 1 + height / 2F);
                graphics.pose().scale(scale, scale);
                int left = -shown / 2;
                this.entry.icon.draw(graphics, left, -5, color);
                ConfigStyle.text(graphics, font, this.entry.label, left + 14, -4, textWidth, textColor);
                if (counted) ConfigStyle.text(graphics, font, this.entry.note, left + 14 + textWidth + 8, -4, noteWidth, noteColor);
                graphics.pose().popMatrix();
            }
        }
    }
}
