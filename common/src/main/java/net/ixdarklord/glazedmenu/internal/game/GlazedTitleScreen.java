package net.ixdarklord.glazedmenu.internal.game;

import com.mojang.authlib.minecraft.BanDetails;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.modlist.GlazedModsScreen;
import net.ixdarklord.glazedmenu.internal.modlist.ModCatalog;
import net.ixdarklord.glazedmenu.internal.modlist.ModUpdates;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
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
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The title screen, in place of the game's own, laid out one of two ways, as the player chose in Glazed Menu's settings:
 * <ul>
 * <li>Shelf: the logo at the top, in the middle, the panorama open under it, and a shelf along the bottom: Continue
 * (the world played last, opened at a click) beside Singleplayer, Multiplayer and Realms as cards, over a bar of the
 * rest.</li>
 * <li>Sidebar: a panel down the left side, the game's logo and splash over it, then the menu as a list (Singleplayer,
 * Multiplayer, Realms and the friends list, then the options, the mods, the language and accessibility), and Quit at
 * its foot; the panorama open beside it, with Continue at the bottom right.</li>
 * </ul>
 * <p>
 * It's a title screen to the game and to other mods (which ask whether one is open), so it extends the game's. The demo
 * keeps the game's own screen.
 */
public final class GlazedTitleScreen extends TitleScreen {
    private static final String VANILLA = "net.minecraft.client.gui.screens.TitleScreen";
    private static final int MARGIN = 10;
    private static final int GAP = 6;
    private static final int BAR_HEIGHT = 28;
    private static final int CARD_GAP = 4;
    private static final int CARD_TALL = 62;
    private static final int CARD_SHORT = 40;
    private static final int CONTINUE_SHORT = 34;
    // Where the game's own screen draws its logo and the Realms button's marks, which are drawn from there to here.
    private static final int LOGO_Y = 30;
    private static final float FADE_IN_MILLIS = 220;

    private final ConfigTheme theme = ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME);
    private final LogoRenderer logo;
    private @Nullable SplashRenderer splash;
    private @Nullable RealmsNotificationsScreen realms;
    private final List<Box> boxes = new ArrayList<>();
    // The widgets this screen placed, and where. Another mod's button for the game's title screen (put where the game's
    // own layout would have room for it) has no place on this one, and is taken off; and a mod making room for its
    // button moves the others (Mod Menu shifts them all up), so they're put back.
    private List<GuiEventListener> own = List.of();
    private final Map<AbstractWidget, int[]> placed = new IdentityHashMap<>();
    private @Nullable Box versionBox;
    private Component version = Component.empty();
    // Where the Realms button's marks go: the right end and the top of the room for them.
    private int realmsRight = -1;
    private int realmsTop;
    // Where the logo is drawn, and how large (the game draws it 256 wide).
    private int logoX = MARGIN;
    private int logoY = MARGIN;
    private float logoScale = 1;
    // The rule between the sidebar's two groups of rows; none on the shelf.
    private int ruleX;
    private int ruleY = -1;
    private int ruleWidth;
    private long openedAt;
    // The worlds, read off the main thread each time the screen opens, for how many there are and the one played last.
    private @Nullable CompletableFuture<List<LevelSummary>> pending;
    private int worlds = -1;
    private @Nullable LevelSummary last;
    private @Nullable FaviconTexture picture;
    private @Nullable String pictureOf;
    private int servers;

    private record Box(int x, int y, int width, int height, boolean panel, boolean accent) {}

    // The game's logo, kept from drawing itself in the middle of the screen (the screen draws it at the top left).
    private static final class Logo extends LogoRenderer {
        private boolean drawing;

        Logo() {
            super(false);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int width, float alpha, int heightOffset) {
            if (this.drawing) super.extractRenderState(graphics, width, alpha, heightOffset);
        }
    }

    private GlazedTitleScreen(Logo logo) {
        super(false, logo);
        this.logo = logo;
    }

    /** The screen to open instead: this one for the game's own title screen, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof TitleScreen) || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceTitle()) return screen;
        if (Minecraft.getInstance().isDemo()) return screen;
        return new GlazedTitleScreen(new Logo());
    }

    // --- The worlds and the servers ---

    private static CompletableFuture<List<LevelSummary>> loadLevels() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            LevelStorageSource.LevelCandidates candidates = minecraft.getLevelSource().findLevelCandidates();
            return minecraft.getLevelSource().loadLevelSummaries(candidates).exceptionally(throwable -> List.of());
        } catch (LevelStorageException e) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    // Opened, or back from another screen: the worlds are read again, and the servers counted.
    @Override
    public void added() {
        super.added();
        ConfigStyle.use(this.theme);
        this.openedAt = System.nanoTime();
        this.pending = loadLevels();
        ServerList list = new ServerList(this.minecraft);
        list.load();
        this.servers = list.size();
        if (this.realms != null) this.realms.added();
    }

    @Override
    public void removed() {
        super.removed();
        if (this.realms != null) this.realms.removed();
        this.closePicture();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.realms != null) this.realms.tick();
        this.keepLayout();
        if (this.pending == null || !this.pending.isDone()) return;
        List<LevelSummary> levels;
        try {
            levels = this.pending.getNow(List.of());
        } catch (CompletionException | CancellationException e) {
            levels = List.of();
        }
        this.pending = null;
        LevelSummary last = levels.stream()
                .filter(level -> level.primaryActionActive() && !(level instanceof LevelSummary.SymlinkLevelSummary) && !(level instanceof LevelSummary.CorruptedLevelSummary))
                .max(Comparator.comparingLong(LevelSummary::getLastPlayed)).orElse(null);
        boolean changed = levels.size() != this.worlds || (last == null) != (this.last == null)
                || last != null && this.last != null && !last.getLevelId().equals(this.last.getLevelId());
        this.worlds = levels.size();
        this.last = last;
        // Continue coming or going (or naming another world) lays the shelf out again.
        if (changed) this.rebuildWidgets();
    }

    private void play() {
        LevelSummary level = this.last;
        if (level == null || !level.primaryActionActive()) return;
        if (level instanceof LevelSummary.SymlinkLevelSummary) {
            this.minecraft.gui.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.gui.setScreen(this)));
        } else {
            LoadingLook.expect(LoadingLook.worldKey(level.getLevelId()), level.getLevelName());
            this.minecraft.createWorldOpenFlows().openWorld(level.getLevelId(), () -> this.minecraft.gui.setScreen(this));
        }
    }

    // The last world's picture, read the first time it's asked for; null for a world without one.
    private @Nullable Identifier picture(LevelSummary level) {
        if (this.picture == null || !level.getLevelId().equals(this.pictureOf)) {
            this.closePicture();
            this.picture = FaviconTexture.forWorld(this.minecraft.getTextureManager(), level.getLevelId());
            this.pictureOf = level.getLevelId();
            Path file = level.getIcon();
            if (file != null && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                try (InputStream stream = Files.newInputStream(file)) {
                    this.picture.upload(NativeImage.read(stream));
                } catch (Throwable t) {
                    GlazedMenu.LOGGER.error("Invalid icon for world {}", level.getLevelId(), t);
                }
            }
        }
        Identifier texture = this.picture.textureLocation();
        return texture.getPath().startsWith("worlds/") ? texture : null;
    }

    private void closePicture() {
        if (this.picture != null && !this.picture.isClosed()) this.picture.close();
        this.picture = null;
        this.pictureOf = null;
    }

    // As the game's own title screen says why multiplayer can't be played; null when it can.
    private @Nullable Component multiplayerDisabledReason() {
        if (this.minecraft.allowsMultiplayer()) return null;
        if (this.minecraft.isNameBanned()) return Component.translatable("title.multiplayer.disabled.banned.name");
        BanDetails ban = this.minecraft.multiplayerBan();
        if (ban != null) {
            return ban.expires() != null ? Component.translatable("title.multiplayer.disabled.banned.temporary")
                    : Component.translatable("title.multiplayer.disabled.banned.permanent");
        }
        return Component.translatable("title.multiplayer.disabled");
    }

    // --- Layout ---

    @Override
    protected void init() {
        ConfigStyle.use(this.theme);
        if (this.splash == null) this.splash = this.minecraft.gui.splashManager().getSplash();
        this.boxes.clear();
        this.realmsRight = -1;
        this.ruleY = -1;
        int left = MARGIN;
        int frame = this.width - MARGIN * 2;
        // The sidebar, where the window has room for it beside something of the panorama.
        boolean sidebar = GlazedSettings.titleLayout() == GlazedSettings.TitleLayout.SIDEBAR && this.width >= 300;
        int side = Math.clamp(this.width * 3 / 10, 150, 200);

        // The version at the top right: the game's, the loader's name and how many mods there are.
        long mods = ModCatalog.all().stream().filter(mod -> !mod.library() && !ModCatalog.isChild(mod)).count();
        String loader = GlazedPlatform.get().loaderName();
        String text = "Minecraft " + SharedConstants.getCurrentVersion().name();
        if (!loader.isEmpty()) text += " · " + Character.toUpperCase(loader.charAt(0)) + loader.substring(1);
        this.version = Component.literal(text + " · ").append(mods == 1 ? Component.translatableWithFallback("glazedmenu.title.mod", "1 mod") : Component.translatableWithFallback("glazedmenu.title.mods", "%s mods", mods));
        int versionWidth = this.font.width(this.version) + 14;
        // Beside the sidebar, or beside the logo in the middle of the screen (and the splash off its right end), where
        // the window is wide enough for both, else not at all.
        int taken = sidebar ? left + side + GAP : this.width / 2 + 128 + 48;
        boolean beside = taken + versionWidth <= left + frame;
        // Always at the top right (beside the sidebar it needs the room). Where the logo in the middle would run under
        // it, the logo is put a line lower instead.
        this.versionBox = (sidebar ? beside : versionWidth <= frame) ? this.box(left + frame - versionWidth, MARGIN, versionWidth, 18, false, false) : null;

        if (sidebar) {
            this.initSidebar(left, side);
        } else {
            // The logo in the middle of the screen's width, as the game has it.
            this.logoX = this.width / 2 - 128;
            this.logoY = this.versionBox != null && !beside ? MARGIN + 18 + 4 : MARGIN;
            this.logoScale = 1;
            int barY = this.height - MARGIN - BAR_HEIGHT;
            this.box(left, barY, frame, BAR_HEIGHT, false, false);
            int shelfTop = this.initShelf(left, frame, barY - GAP);
            this.initBar(left, frame, barY + (BAR_HEIGHT - 20) / 2, shelfTop);
        }

        if (this.realms == null) this.realms = new RealmsNotificationsScreen();
        this.realms.init(this.width, this.height);
        this.own = List.copyOf(this.children());
        this.placed.clear();
        for (GuiEventListener child : this.own) {
            if (child instanceof AbstractWidget widget) this.placed.put(widget, new int[]{widget.getX(), widget.getY()});
        }
    }

    // Whatever was added since the screen was laid out isn't its own, and its own widgets stay where it put them.
    private void keepLayout() {
        if (this.children().size() != this.own.size()) {
            for (GuiEventListener child : List.copyOf(this.children())) {
                if (!this.own.contains(child)) this.removeWidget(child);
            }
        }
        this.placed.forEach((widget, at) -> {
            if (widget.getX() != at[0] || widget.getY() != at[1]) widget.setPosition(at[0], at[1]);
        });
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

    // The sidebar: the logo over the menu as a list, Quit and the dark or light switch at its
    // foot; beside it, Continue and the credits at the bottom right.
    private void initSidebar(int left, int side) {
        this.logoScale = (side - 16) / 256F;
        // The logo stands on the panorama itself, over the menu's panel: the room kept for it has no frame.
        int logoBox = Math.round(51 * this.logoScale) + 14;
        this.logoX = left + 8;
        this.logoY = MARGIN + 8;

        int barY = this.height - MARGIN - BAR_HEIGHT;
        this.box(left, barY, side, BAR_HEIGHT, false, false);
        Component quit = Component.translatable("menu.quit");
        FlatButton quitButton = this.addRenderableWidget(FlatButton.of(quit, side - 8 - 24, button -> this.minecraft.stop()).style(FlatButton.Style.DANGER).withIcon(ConfigIcons.EXIT));
        quitButton.setPosition(left + 4, barY + (BAR_HEIGHT - 20) / 2);
        FlatButton toggle = this.addRenderableWidget(StyledScreen.modeToggle());
        toggle.setPosition(left + side - 4 - 20, barY + (BAR_HEIGHT - 20) / 2);

        int panelY = MARGIN + logoBox + GAP;
        int panelBottom = barY - GAP;
        this.box(left, panelY, side, panelBottom - panelY, true, false);
        int inner = side - 12;
        // Eight rows and a rule between the fourth and the fifth share the panel, within reason.
        int rowHeight = Math.clamp((panelBottom - panelY - 12 - 7 - 7 * 2) / 8, 13, 22);
        int x = left + 6;
        int y = panelY + 6;
        Component disabled = this.multiplayerDisabledReason();
        Component worlds = this.worlds < 0 ? Component.translatableWithFallback("glazedmenu.title.worlds.loading", "Reading…")
                : this.worlds == 1 ? Component.translatableWithFallback("glazedmenu.title.worlds.one", "1 world")
                : Component.translatableWithFallback("glazedmenu.title.worlds", "%s worlds", this.worlds);
        Component servers = this.servers == 1 ? Component.translatableWithFallback("glazedmenu.title.servers.one", "1 server")
                : Component.translatableWithFallback("glazedmenu.title.servers", "%s servers", this.servers);
        this.addRenderableWidget(new SideRow(inner, rowHeight, ConfigIcons.GLOBE, GlazedBrand.identity(0xFF5BC467), Component.translatable("menu.singleplayer"), worlds, false, true,
                () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this)))).setPosition(x, y);
        y += rowHeight + 2;
        SideRow multi = this.addRenderableWidget(new SideRow(inner, rowHeight, ConfigIcons.SERVER, GlazedBrand.identity(0xFFB07CF0), Component.translatable("menu.multiplayer"), servers, false, true,
                () -> this.minecraft.gui.setScreen(this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))));
        multi.setPosition(x, y);
        y += rowHeight + 2;
        // Realms: its row's end is left to the game's marks (an invite, news).
        SideRow realmsRow = this.addRenderableWidget(new SideRow(inner, rowHeight, ConfigIcons.LAN, GlazedBrand.identity(0xFF4CC9E0), Component.translatableWithFallback("glazedmenu.title.realms.name", "Realms"), null, false, true,
                () -> this.minecraft.gui.setScreen(new RealmsMainScreen(this))));
        realmsRow.setPosition(x, y);
        this.realmsRight = x + inner - 2;
        this.realmsTop = y + (rowHeight - 14) / 2 - 1;
        if (disabled != null) {
            multi.active = false;
            multi.setTooltip(Tooltip.create(disabled));
            realmsRow.active = false;
            realmsRow.setTooltip(Tooltip.create(disabled));
        }
        y += rowHeight + 2;
        // The friends list, which the game's title screen has since 26.2: off where the game turns its own button off,
        // and saying how many ask to be friends.
        int requests = this.minecraft.getPlayerSocialManager().isFriendListEnabled() ? this.minecraft.getPlayerSocialManager().getIncomingRequests().size() : 0;
        SideRow friends = this.addRenderableWidget(new SideRow(inner, rowHeight, ConfigIcons.PERSON, GlazedBrand.identity(0xFFF07A5A), Component.translatable("gui.friends.open"),
                requests > 0 ? Component.translatableWithFallback("glazedmenu.pause.note.friends.requests", "%s requests", requests) : null, requests > 0, false,
                this::openFriends));
        friends.setPosition(x, y);
        friends.active = this.friendsAvailable();
        y += rowHeight + 2;
        this.ruleX = x;
        this.ruleY = y + 2;
        this.ruleWidth = inner;
        y += 7;

        long updates = ModUpdates.count();
        var language = this.minecraft.getLanguageManager().getLanguage(this.minecraft.getLanguageManager().getSelected());
        this.addRenderableWidget(new SideRow(inner, rowHeight, ConfigIcons.SLIDERS, GlazedBrand.identity(0xFF8B7CF6), Component.translatable("menu.options"), null, false, false,
                () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false)))).setPosition(x, y);
        y += rowHeight + 2;
        this.addRenderableWidget(new SideRow(inner, rowHeight, ConfigIcons.GRID, GlazedBrand.identity(0xFF3CC8B4), Component.translatableWithFallback("glazedmenu.mods.button", "Mods"),
                updates > 0 ? Component.translatableWithFallback("glazedmenu.pause.note.mods.updates", "%s updates", updates) : null, updates > 0, false,
                () -> this.minecraft.gui.setScreen(new GlazedModsScreen(this)))).setPosition(x, y);
        y += rowHeight + 2;
        this.addRenderableWidget(new SideRow(inner, rowHeight, ConfigIcons.GLOBE, GlazedBrand.identity(0xFF4DA3FF), Component.translatable("options.language.title"),
                language != null ? Component.literal(language.name()) : null, false, false,
                () -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())))).setPosition(x, y);
        y += rowHeight + 2;
        this.addRenderableWidget(new SideRow(inner, rowHeight, ConfigIcons.EYE, GlazedBrand.identity(0xFFF2C94C), Component.translatableWithFallback("glazedmenu.title.accessibility", "Accessibility"), null, false, false,
                () -> this.minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)))).setPosition(x, y);

        // Beside the sidebar: the credits in the bottom right corner, and Continue over them.
        int room = this.width - MARGIN - (left + side + GAP);
        Component credits = Component.translatable("title.credits");
        if (this.font.width(credits) + 10 > room) credits = Component.translatableWithFallback("glazedmenu.title.credits", "© Mojang AB");
        int creditsWidth = this.font.width(credits) + 10;
        int creditsY = this.height - MARGIN - 12;
        if (creditsWidth <= room) {
            FlatButton creditsButton = this.addRenderableWidget(new Credits(creditsWidth, 12, credits, true));
            creditsButton.setPosition(this.width - MARGIN - creditsWidth, creditsY);
        }
        if (this.last != null && room >= 150) {
            int cardHeight = this.height >= 300 ? CARD_TALL : CONTINUE_SHORT;
            int cardWidth = Math.min(250, room);
            this.addRenderableWidget(new Card(cardWidth, cardHeight, this.last)).setPosition(this.width - MARGIN - cardWidth, creditsY - 6 - cardHeight);
        }
    }

    // The play shelf: Continue and the three cards in one row where the window is wide, else Continue over them.
    private int initShelf(int left, int frame, int bottom) {
        int inner = frame - 12;
        boolean wide = inner >= 500;
        boolean hasLast = this.last != null;
        int cardHeight = wide ? CARD_TALL : CARD_SHORT;
        int height = 12 + cardHeight + (!wide && hasLast ? CONTINUE_SHORT + CARD_GAP : 0);
        int top = bottom - height;
        this.box(left, top, frame, height, true, true);
        int x = left + 6;
        int y = top + 6;
        int cardsWidth = inner;
        if (hasLast) {
            if (wide) {
                int continueWidth = Math.round((inner - CARD_GAP * 3) * 1.7F / 4.7F);
                this.addRenderableWidget(new Card(continueWidth, cardHeight, this.last)).setPosition(x, y);
                x += continueWidth + CARD_GAP;
                cardsWidth = inner - continueWidth - CARD_GAP;
            } else {
                this.addRenderableWidget(new Card(inner, CONTINUE_SHORT, this.last)).setPosition(x, y);
                y += CONTINUE_SHORT + CARD_GAP;
            }
        }
        int each = (cardsWidth - CARD_GAP * 2) / 3;
        Component disabled = this.multiplayerDisabledReason();
        Card single = this.addRenderableWidget(new Card(each, cardHeight, ConfigIcons.GLOBE, GlazedBrand.identity(0xFF5BC467), Component.translatable("menu.singleplayer"),
                this.worlds < 0 ? Component.translatableWithFallback("glazedmenu.title.worlds.loading", "Reading…")
                        : this.worlds == 1 ? Component.translatableWithFallback("glazedmenu.title.worlds.one", "1 world")
                        : Component.translatableWithFallback("glazedmenu.title.worlds", "%s worlds", this.worlds),
                () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));
        single.setPosition(x, y);
        Card multi = this.addRenderableWidget(new Card(each, cardHeight, ConfigIcons.SERVER, GlazedBrand.identity(0xFFB07CF0), Component.translatable("menu.multiplayer"),
                this.servers == 1 ? Component.translatableWithFallback("glazedmenu.title.servers.one", "1 server")
                        : Component.translatableWithFallback("glazedmenu.title.servers", "%s servers", this.servers),
                () -> this.minecraft.gui.setScreen(this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))));
        multi.setPosition(x + each + CARD_GAP, y);
        // The last card takes what the division left over.
        Card realmsCard = this.addRenderableWidget(new Card(cardsWidth - 2 * (each + CARD_GAP), cardHeight, ConfigIcons.LAN, GlazedBrand.identity(0xFF4CC9E0), Component.translatableWithFallback("glazedmenu.title.realms.name", "Realms"),
                Component.translatableWithFallback("glazedmenu.title.realms", "Play online"), () -> this.minecraft.gui.setScreen(new RealmsMainScreen(this))));
        realmsCard.setPosition(x + 2 * (each + CARD_GAP), y);
        this.realmsRight = realmsCard.getX() + realmsCard.getWidth() - 1;
        this.realmsTop = y + 4;
        if (disabled != null) {
            multi.active = false;
            multi.setTooltip(Tooltip.create(disabled));
            realmsCard.active = false;
            realmsCard.setTooltip(Tooltip.create(disabled));
        }
        return top;
    }

    // The bar under the shelf: the options and the mods by name at its left, then the language, accessibility and the
    // friends list (by name where there's room, else as icons); at its right Quit by name, the dark or light switch
    // before it, and before that the credits, which take the room that's left: the game's own line, whole, written smaller where it's short.
    private void initBar(int left, int frame, int y, int shelfTop) {
        Component quit = Component.translatable("menu.quit");
        int quitWidth = this.font.width(quit) + 28;
        FlatButton quitButton = this.addRenderableWidget(FlatButton.of(quit, quitWidth, button -> this.minecraft.stop()).style(FlatButton.Style.DANGER).withIcon(ConfigIcons.EXIT));
        quitButton.setPosition(left + frame - 4 - quitWidth, y);
        FlatButton toggle = this.addRenderableWidget(StyledScreen.modeToggle());
        toggle.setPosition(quitButton.getX() - 4 - 20, y);

        long updates = ModUpdates.count();
        Component modsName = Component.translatableWithFallback("glazedmenu.mods.button", "Mods");
        if (updates > 0) modsName = modsName.copy().append(Component.literal(" " + updates).withStyle(style -> style.withColor(ConfigStyle.colors().modified() & 0xFFFFFF)));
        Component[] names = {Component.translatable("menu.options"), modsName, Component.translatable("options.language.title"), Component.translatable("options.accessibility"),
                Component.translatable("gui.friends.open")};
        ConfigIcons.Icon[] icons = {ConfigIcons.SLIDERS, ConfigIcons.GRID, ConfigIcons.GLOBE, ConfigIcons.EYE, ConfigIcons.PERSON};
        int[] colors = {GlazedBrand.identity(0xFFB49CFF), GlazedBrand.identity(0xFF3CC8B4), GlazedBrand.identity(0xFF4DA3FF), GlazedBrand.identity(0xFFF2C94C), GlazedBrand.identity(0xFFF07A5A)};
        Runnable[] opens = {
                () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false)),
                () -> this.minecraft.gui.setScreen(new GlazedModsScreen(this)),
                () -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())),
                () -> this.minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)),
                this::openFriends};
        Component credits = Component.translatable("title.credits");
        int creditsWidth = this.font.width(credits);
        // The credits end this far from the switch, and start this far from the buttons.
        int creditsRight = toggle.getX() - CREDITS_GAP;
        // All of them named where the credits still fit at their full size beside them; else the first two.
        int labelled = 2;
        int allNamed = (names.length - 1) * 4;
        for (Component name : names) allNamed += this.font.width(name) + 26;
        if (left + 4 + allNamed + CREDITS_GAP + creditsWidth <= creditsRight) labelled = names.length;
        // The friends list (the last of them, an icon here) is left out where it would push the credits off the bar.
        int count = names.length;
        if (labelled < count) {
            int iconsEnd = left + 4;
            for (int i = 0; i < count; i++) iconsEnd += (i < labelled ? this.font.width(names[i]) + 26 : 20) + 4;
            if ((creditsRight - (iconsEnd - 4 + CREDITS_GAP)) / (float) Math.max(1, creditsWidth) < 0.45F) count--;
        }
        int x = left + 4;
        for (int i = 0; i < count; i++) {
            Runnable open = opens[i];
            FlatButton button = i < labelled ? FlatButton.of(names[i], this.font.width(names[i]) + 26, pressed -> open.run()).withIcon(icons[i])
                    : FlatButton.icon(icons[i], names[i], pressed -> open.run()).style(FlatButton.Style.NORMAL);
            this.addRenderableWidget(button.iconColor(colors[i])).setPosition(x, y);
            // The friends list is the last of them.
            if (i == names.length - 1) button.active = this.friendsAvailable();
            x += button.getWidth() + 4;
        }
        // The credits in what's left, written as small as it takes to fit (within reason).
        int room = creditsRight - (x - 4 + CREDITS_GAP);
        float scale = Math.min(1, room / (float) Math.max(1, creditsWidth));
        if (scale >= 0.45F) {
            int width = Math.max(1, (int) Math.ceil(creditsWidth * scale));
            this.addRenderableWidget(new Credits(width, 20, credits, false, scale)).setPosition(creditsRight - width, y);
        }
    }

    // As the game's own Friends button: not in a demo.
    private boolean friendsAvailable() {
        return !this.minecraft.isDemo();
    }

    private void openFriends() {
        OnlineOptionsScreen.confirmFriendsListEnabled(this.minecraft, () -> this.minecraft.gui.setScreen(new FriendsOverlayScreen(this)), this);
    }

    // How far the credits keep from what's on either side of them in the shelf's bar.
    private static final int CREDITS_GAP = 6;

    // --- Drawing ---

    // How visible the page is as it opens: a smooth ease-in-out, as the other screens fade in.
    private float fadeAlpha() {
        if (!GlazedSettings.transitions()) return 1;
        float progress = Math.min(1, (System.nanoTime() - this.openedAt) / 1_000_000F / FADE_IN_MILLIS);
        return progress * progress * progress * (progress * (progress * 6 - 15) + 10);
    }

    // The panorama, clear (it's the title screen's picture, not a backdrop to read over).
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        this.extractPanorama(graphics, a);
        ThemeEffects.background(graphics, this.width, this.height, mouseX, mouseY, a);
        GuiFade.set(this.fadeAlpha());
        for (Box box : this.boxes) {
            if (box.panel) ConfigStyle.panel(graphics, box.x, box.y, box.width, box.height);
            else ConfigStyle.bar(graphics, box.x, box.y, box.width, box.height);
            // Glazed Menu's flowing gradient along the top edge, as on its mod list.
            if (box.accent) GlazedBrand.fill(graphics, box.x + 1, box.y, box.width - 2, 2, 0xD0);
        }
        if (this.versionBox != null) {
            // White on its strip (the theme's text color on the light look, where white wouldn't read).
            graphics.text(this.font, this.version, this.versionBox.x + 7, this.versionBox.y + 5,
                    ConfigStyle.mode() == ConfigTheme.Mode.DARK ? 0xFFFFFFFF : ConfigStyle.colors().text(), false);
        }
        if (this.ruleY >= 0) graphics.fill(this.ruleX, this.ruleY, this.ruleX + this.ruleWidth, this.ruleY + 1, ConfigStyle.colors().panelBorder());
        GuiFade.reset();
    }

    // The widgets, then the game's logo and splash at the top left, and the Realms button's marks on the Realms card.
    // (The game's own drawing of the title screen isn't asked for: it puts the logo and the version in its own places.)
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        float fade = this.fadeAlpha();
        GuiFade.set(fade);
        this.keepLayout();
        for (GuiEventListener widget : this.own) {
            if (widget instanceof Renderable renderable) renderable.extractRenderState(graphics, mouseX, mouseY, a);
        }
        GuiFade.reset();

        // From where the game draws them (the logo centered, thirty pixels down) to their place here, at their size here.
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.logoX, this.logoY);
        graphics.pose().scale(this.logoScale, this.logoScale);
        graphics.pose().translate(-(this.width / 2 - 128), -LOGO_Y);
        if (this.logo instanceof Logo logo) logo.drawing = true;
        this.logo.extractRenderState(graphics, this.width, fade, LOGO_Y);
        if (this.logo instanceof Logo logo) logo.drawing = false;
        if (this.splash != null && !this.minecraft.options.hideSplashTexts().get()) this.splash.extractRenderState(graphics, this.width, this.font, fade);
        graphics.pose().popMatrix();

        // The game draws the marks at the right end of its own Realms button; they're moved to Realms' place here.
        if (this.realms != null && this.realmsRight >= 0 && fade >= 1) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(this.realmsRight - (this.width / 2 + 100), this.realmsTop - (this.height / 4 + 48 + 48 + 2));
            this.realms.extractRenderState(graphics, mouseX, mouseY, a);
            graphics.pose().popMatrix();
        }
        ThemeEffects.foreground(graphics, this.width, this.height, mouseX, mouseY, a);
    }

    private static String lastPlayed(LevelSummary level) {
        long time = level.getLastPlayed();
        if (time == -1L) return "";
        return WorldSelectionList.DATE_FORMAT.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()));
    }

    // The credits line, opening the game's credits: white, as the game writes it. Over the panorama it has a shadow; in
    // the shelf's bar it's white on the dark look and the theme's text color on the light one (white wouldn't read there).
    private final class Credits extends FlatButton {
        private final boolean overPanorama;
        // How large the line is written: smaller than 1 where the bar is short of room for it.
        private final float scale;

        Credits(int width, int height, Component text, boolean overPanorama) {
            this(width, height, text, overPanorama, 1);
        }

        Credits(int width, int height, Component text, boolean overPanorama, float scale) {
            super(width, height, text, button -> GlazedTitleScreen.this.minecraft.gui.setScreen(new CreditsAndAttributionScreen(GlazedTitleScreen.this)));
            this.overPanorama = overPanorama;
            this.scale = scale;
        }

        @Override
        protected void extractButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font font = GlazedTitleScreen.this.font;
            int color = this.overPanorama || ConfigStyle.mode() == ConfigTheme.Mode.DARK ? 0xFFFFFFFF : ConfigStyle.colors().text();
            int textWidth = font.width(this.getMessage());
            // Ending at the button's right edge, at the size it's written.
            float x = this.getX() + this.getWidth() - textWidth * this.scale;
            float y = this.getY() + (this.getHeight() - 8 * this.scale) / 2;
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(this.scale, this.scale);
            graphics.text(font, this.getMessage(), 0, 0, color, this.overPanorama);
            // Underlined under the mouse, as a link.
            if (this.isHoveredOrFocused()) graphics.fill(0, 9, textWidth, 10, color);
            graphics.pose().popMatrix();
        }
    }

    // --- The sidebar's rows ---

    // A row of the sidebar: an edge and an icon in its color, its name, and at its end a note (marked, as a tag) or an
    // arrow. The three ways to play are in bold.
    private final class SideRow extends FlatButton {
        private final ConfigIcons.Icon icon;
        private final int color;
        private final @Nullable Component note;
        private final boolean marked;
        private final boolean bold;

        SideRow(int width, int height, ConfigIcons.Icon icon, int color, Component label, @Nullable Component note, boolean marked, boolean bold, Runnable open) {
            super(width, height, label, button -> open.run());
            this.icon = icon;
            this.color = color;
            this.note = note;
            this.marked = marked;
            this.bold = bold;
        }

        @Override
        protected void extractButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font font = GlazedTitleScreen.this.font;
            boolean lit = this.active && this.isHoveredOrFocused();
            int color = this.active ? this.color : ConfigStyle.colors().textMuted();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.mix(ConfigStyle.colors().button(), color, lit ? 0.24F : 0.06F));
            ConfigStyle.outline(graphics, x, y, width, height, lit ? ConfigStyle.withAlpha(color, 0xE0) : ConfigStyle.colors().panelBorder());
            graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ConfigStyle.withAlpha(color, lit ? 0xFF : 0xC0));
            this.icon.draw(graphics, x + 7, y + (height - 10) / 2, color);
            int textX = x + 21;
            int textY = y + (height - 8) / 2;
            int right = x + width - 5;
            Component label = this.bold ? this.getMessage().copy().withStyle(ChatFormatting.BOLD) : this.getMessage();
            int labelColor = this.active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted();
            if (this.note != null && this.marked && height >= 13 && textX + font.width(label) + 6 + font.width(this.note) + 8 <= right) {
                int tagX = right - font.width(this.note) - 8;
                ConfigStyle.badge(graphics, font, this.note, tagX, y + (height - 11) / 2, ConfigStyle.colors().modified());
                right = tagX - 4;
            } else if (this.note != null && textX + font.width(label) + 8 + font.width(this.note) <= right) {
                int noteX = right - font.width(this.note);
                graphics.text(font, this.note, noteX, textY, this.marked ? ConfigStyle.colors().modified() : ConfigStyle.colors().textMuted(), false);
                right = noteX - 4;
            } else if (this.note == null && this.color != GlazedBrand.identity(0xFF4CC9E0)) {
                right -= 9;
                ConfigIcons.CHEVRON.draw(graphics, right, y + (height - 9) / 2, 9, lit ? color : ConfigStyle.colors().textMuted());
                right -= 3;
            }
            ConfigStyle.text(graphics, font, label, textX, textY, right - textX, labelColor);
        }
    }

    // --- The cards ---

    // A card of the shelf: Singleplayer, Multiplayer or Realms in its own color, or Continue with the last world's
    // picture and name.
    private final class Card extends FlatButton {
        private final ConfigIcons.Icon icon;
        private final int color;
        private final Component note;
        private final @Nullable LevelSummary level;

        Card(int width, int height, ConfigIcons.Icon icon, int color, Component title, Component note, Runnable open) {
            super(width, height, title, button -> open.run());
            this.icon = icon;
            this.color = color;
            this.note = note;
            this.level = null;
        }

        // Continue.
        Card(int width, int height, LevelSummary level) {
            super(width, height, Component.translatableWithFallback("glazedmenu.title.continue", "Continue"), button -> GlazedTitleScreen.this.play());
            this.icon = ConfigIcons.PLAY;
            this.color = ConfigStyle.accent();
            this.level = level;
            Component mode = level.isHardcore() ? Component.translatable("gameMode.hardcore") : Component.translatable("gameMode." + level.getGameMode().getName());
            String played = lastPlayed(level);
            this.note = played.isEmpty() ? mode : mode.copy().append(" · " + played);
        }

        @Override
        protected ConfigEffect.WidgetKind effectKind() {
            return ConfigEffect.WidgetKind.CARD;
        }

        @Override
        protected void extractButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font font = GlazedTitleScreen.this.font;
            boolean lit = this.active && this.isHoveredOrFocused();
            int color = this.active ? this.color : ConfigStyle.colors().textMuted();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            int titleColor = this.active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted();
            // The card washed with its color, more under the mouse, and its color along the top edge.
            ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.mix(ConfigStyle.colors().button(), color, lit ? 0.26F : 0.12F));
            ConfigStyle.outline(graphics, x, y, width, height, ConfigStyle.withAlpha(color, lit ? 0xE0 : 0x60));
            graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ConfigStyle.withAlpha(color, lit ? 0xFF : 0xB0));
            if (this.level != null) {
                this.extractContinue(graphics, font, x, y + 2, width, height - 2, lit);
            } else if (height >= CARD_TALL) {
                // Tall: the icon at the top, the name and the note at the foot.
                this.icon.draw(graphics, x + 8, y + 9, 16, color);
                ConfigStyle.text(graphics, font, this.getMessage().copy().withStyle(ChatFormatting.BOLD), x + 8, y + height - 25, width - 16, titleColor);
                ConfigStyle.text(graphics, font, this.note, x + 8, y + height - 14, width - 16, ConfigStyle.colors().textDim());
            } else {
                // Short: the icon in a box at the left, the name over the note beside it.
                int box = Math.min(24, height - 14);
                int boxY = y + 2 + (height - 2 - box) / 2;
                boolean boxed = width >= 110;
                if (boxed) {
                    ConfigStyle.iconFrame(graphics, x + 6, boxY, box, box, ConfigStyle.withAlpha(color, lit ? 0xFF : 0xD0));
                    this.icon.draw(graphics, x + 6 + (box - 14) / 2, boxY + (box - 14) / 2, 14, ConfigStyle.readableOn(color));
                }
                int textX = boxed ? x + 6 + box + 6 : x + 6;
                int textWidth = x + width - 6 - textX;
                int textY = y + 2 + (height - 2 - 19) / 2;
                ConfigStyle.text(graphics, font, this.getMessage().copy().withStyle(ChatFormatting.BOLD), textX, textY, textWidth, titleColor);
                ConfigStyle.text(graphics, font, this.note, textX, textY + 11, textWidth, ConfigStyle.colors().textDim());
            }
        }

        // The last world: its picture, "Continue" over its name and what it is, and Play at the right.
        private void extractContinue(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, boolean lit) {
            GlazedTitleScreen screen = GlazedTitleScreen.this;
            boolean tall = height >= CARD_TALL - 2;
            int size = tall ? 44 : 24;
            int pictureY = y + (height - size) / 2;
            Identifier texture = screen.picture(this.level);
            if (texture != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + 7, pictureY, 0, 0, size, size, size, size);
                ConfigStyle.pictureFrame(graphics, x + 7, pictureY, size, size);
            } else {
                ConfigStyle.iconFrame(graphics, x + 7, pictureY, size, size, ConfigStyle.withAlpha(GlazedBrand.identity(0xFF5BC467), 0xD0));
                ConfigIcons.GLOBE.draw(graphics, x + 7 + size / 4, pictureY + size / 4, size / 2, ConfigStyle.readableOn(GlazedBrand.identity(0xFF5BC467)));
            }
            // Play, as the filled button it is on the sketch; an arrow alone where the card is narrow.
            Component play = Component.translatableWithFallback("glazedmenu.worlds.play", "Play");
            int pillWidth = width >= 230 ? font.width(play) + 26 : 20;
            int pillHeight = tall ? 24 : 20;
            int pillX = x + width - 7 - pillWidth;
            int pillY = y + (height - pillHeight) / 2;
            int ink;
            if (ConfigStyle.isGlazed()) {
                // As the screens' main buttons are drawn: the flowing gradient (lighter under the mouse) on a deep base,
                // outlined, and with the outline the rim of light, the shade and the shadow.
                ConfigStyle.rect(graphics, pillX, pillY, pillWidth, pillHeight, ConfigStyle.mix(this.color, 0xFF140C3A, 0.5F));
                GlazedBrand.fill(graphics, pillX + 1, pillY + 1, pillWidth - 2, pillHeight - 2, 0xFF, lit ? 0.06F : 0.24F);
                ConfigStyle.outline(graphics, pillX, pillY, pillWidth, pillHeight, ConfigStyle.mix(GlazedBrand.now(0.5F), 0xFF000000, lit ? 0.3F : 0.48F));
                ink = 0xFFFFFFFF;
            } else {
                int fill = lit ? ConfigStyle.mix(this.color, 0xFFFFFFFF, 0.18F) : this.color;
                ConfigStyle.rect(graphics, pillX, pillY, pillWidth, pillHeight, fill);
                ConfigStyle.outline(graphics, pillX, pillY, pillWidth, pillHeight, ConfigStyle.mix(fill, 0xFF000000, 0.38F));
                ink = ConfigStyle.readableOn(fill);
            }
            if (pillWidth > 20) {
                ConfigIcons.PLAY.draw(graphics, pillX + 7, pillY + (pillHeight - 10) / 2, ink);
                graphics.text(font, play, pillX + 20, pillY + (pillHeight - 8) / 2, ink, false);
            } else {
                ConfigIcons.PLAY.drawCentered(graphics, pillX, pillY, pillWidth, pillHeight, ink);
            }
            int textX = x + 7 + size + 8;
            int textWidth = pillX - 6 - textX;
            Component name = Component.literal(this.level.getLevelName()).withStyle(ChatFormatting.BOLD);
            if (tall) {
                int textY = y + (height - 30) / 2;
                ConfigStyle.text(graphics, font, this.getMessage(), textX, textY, textWidth, ConfigStyle.colors().textDim());
                ConfigStyle.text(graphics, font, name, textX, textY + 11, textWidth, ConfigStyle.colors().text());
                ConfigStyle.text(graphics, font, this.note, textX, textY + 22, textWidth, ConfigStyle.colors().textDim());
            } else {
                int textY = y + (height - 19) / 2;
                ConfigStyle.text(graphics, font, this.getMessage().copy().append(" · ").append(name), textX, textY, textWidth, ConfigStyle.colors().text());
                ConfigStyle.text(graphics, font, this.note, textX, textY + 11, textWidth, ConfigStyle.colors().textDim());
            }
        }
    }
}
