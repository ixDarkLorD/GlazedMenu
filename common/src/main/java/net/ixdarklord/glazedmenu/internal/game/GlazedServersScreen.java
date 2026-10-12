package net.ixdarklord.glazedmenu.internal.game;

import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.ConfirmPopup;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The servers, in place of the game's multiplayer screen: the list down the left (the favorites first, and the games on
 * your network under a heading of their own), searched by a server's name, address or message, and the chosen server's
 * details beside it: its picture and message, whether it answers and how fast, its version, its players, and what it
 * does about resource packs. Under them, the game's own actions: join, edit and delete, a new server, and a direct
 * connection.
 * <p>
 * Keys: F5 asks the servers again, Shift with the up or down arrow moves the chosen server in the list.
 */
public final class GlazedServersScreen extends GameListScreen {
    private static final String VANILLA = "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen";
    private static final int KEY_F5 = 294;
    private static final int KEY_DOWN = 264;
    private static final int KEY_UP = 265;
    private static final int PICTURE = 40;
    private static final AtomicInteger PINGERS = new AtomicInteger();
    // The threads asking the servers, as the game's own list keeps: a few at a time, never keeping the game from closing.
    private static final ThreadPoolExecutor PINGS = new ScheduledThreadPoolExecutor(5, task -> {
        Thread thread = new Thread(task, "Glazed Menu Server Pinger #" + PINGERS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private ServerList servers;
    private List<LanServer> lan = List.of();
    private LanServerDetection.@Nullable LanServerList lanList;
    private LanServerDetection.@Nullable LanServerDetector lanDetector;
    // The server being added, edited or joined directly, while the game's screen for it is open.
    private @Nullable ServerData editing;
    // Each server's picture, uploaded as the server sends it; closed as the screen is left.
    private final Map<ServerData, FaviconTexture> pictures = new IdentityHashMap<>();
    private final Map<ServerData, byte[]> pictureBytes = new IdentityHashMap<>();
    private @Nullable FlatButton joinButton;
    private @Nullable FlatButton editButton;
    private @Nullable FlatButton deleteButton;
    private @Nullable FlatButton favoriteButton;
    private @Nullable FlatButton upButton;
    private @Nullable FlatButton downButton;

    private GlazedServersScreen(@Nullable Screen parent) {
        super(Component.translatable("multiplayer.title"), parent, ConfigIcons.SERVER);
        this.servers = new ServerList(Minecraft.getInstance());
        this.servers.load();
    }

    /** The screen to open instead: this one for the game's own multiplayer screen, over the screen that opened it. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof JoinMultiplayerScreen) || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceServers()) return screen;
        Screen current = Minecraft.getInstance().gui.screen();
        // Asked for again from itself: the same list, asked again.
        if (current instanceof GlazedServersScreen servers) {
            servers.refresh();
            return servers;
        }
        return new GlazedServersScreen(current);
    }

    // --- The servers ---

    private List<ServerData> all() {
        List<ServerData> all = new ArrayList<>();
        for (int i = 0; i < this.servers.size(); i++) all.add(this.servers.get(i));
        return all;
    }

    private int indexOf(ServerData server) {
        for (int i = 0; i < this.servers.size(); i++) {
            if (this.servers.get(i) == server) return i;
        }
        return -1;
    }

    private @Nullable ServerData chosenServer() {
        return this.chosen instanceof ServerData server && this.indexOf(server) >= 0 ? server : null;
    }

    private @Nullable LanServer chosenLan() {
        return this.chosen instanceof LanServer server ? server : null;
    }

    // Reads the list again and asks every server again.
    private void refresh() {
        String address = this.chosen instanceof ServerData server ? server.ip : null;
        this.pinger.removeAll();
        this.closePictures();
        this.servers = new ServerList(this.minecraft);
        this.servers.load();
        this.chosen = address != null ? this.all().stream().filter(server -> server.ip.equals(address)).findFirst().orElse(null) : this.chosen;
        this.rebuildRows(true);
    }

    // Asks a server what it is, as the game's own list does; the answers land on the server's data.
    private void ping(ServerData server) {
        Minecraft minecraft = this.minecraft;
        ServerList servers = this.servers;
        server.setState(ServerData.State.PINGING);
        server.motd = CommonComponents.EMPTY;
        server.status = CommonComponents.EMPTY;
        PINGS.submit(() -> {
            try {
                this.pinger.pingServer(server, () -> minecraft.execute(servers::save),
                        () -> server.setState(server.protocol == SharedConstants.getCurrentVersion().protocolVersion()
                                ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE),
                        EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()));
            } catch (UnknownHostException e) {
                server.setState(ServerData.State.UNREACHABLE);
                server.motd = Component.translatable("multiplayer.status.cannot_resolve");
            } catch (Exception e) {
                server.setState(ServerData.State.UNREACHABLE);
                server.motd = Component.translatable("multiplayer.status.cannot_connect");
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        for (ServerData server : this.all()) {
            if (server.state() == ServerData.State.INITIAL) this.ping(server);
            // A failed ping leaves the server "being asked" for good, with only its message saying so.
            else if (server.state() == ServerData.State.PINGING && server.motd.getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals("multiplayer.status.cannot_connect")) server.setState(ServerData.State.UNREACHABLE);
        }
        if (this.lanList != null) {
            List<LanServer> found = this.lanList.takeDirtyServers();
            if (found != null) {
                this.lan = List.copyOf(found);
                this.rebuildRows(true);
            }
        }
        this.pinger.tick();
    }

    // Opened, or back from another screen: the network is listened to for games, and a server left unanswered when
    // the screen was last left is asked again.
    @Override
    public void added() {
        super.added();
        for (ServerData server : this.all()) {
            if (server.state() == ServerData.State.PINGING) server.setState(ServerData.State.INITIAL);
        }
        if (this.lanDetector == null) {
            this.lanList = new LanServerDetection.LanServerList();
            try {
                this.lanDetector = new LanServerDetection.LanServerDetector(this.lanList);
                this.lanDetector.start();
            } catch (Exception e) {
                GlazedMenu.LOGGER.warn("Unable to start LAN server detection: {}", e.getMessage());
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (this.lanDetector != null) {
            this.lanDetector.interrupt();
            this.lanDetector = null;
        }
        this.pinger.removeAll();
        this.closePictures();
    }

    private boolean matches(ServerData server, String query) {
        return query.isEmpty() || server.name.toLowerCase(Locale.ROOT).contains(query) || server.ip.toLowerCase(Locale.ROOT).contains(query)
                || server.motd.getString().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    protected List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        List<ServerData> found = this.all().stream().filter(server -> this.matches(server, query)).toList();
        List<ServerData> favorites = found.stream().filter(server -> GameMarks.isFavoriteServer(server.ip)).toList();
        if (!favorites.isEmpty()) {
            rows.add(new Heading(Component.translatableWithFallback("glazedmenu.list.favorites", "Favorites")));
            for (ServerData server : favorites) rows.add(new ServerRow(server));
            if (favorites.size() < found.size()) rows.add(new Heading(Component.translatableWithFallback("glazedmenu.servers.section.all", "Servers")));
        }
        for (ServerData server : found) {
            if (!favorites.contains(server)) rows.add(new ServerRow(server));
        }
        if (found.isEmpty()) {
            rows.add(this.servers.size() == 0 ? new Notice(ConfigIcons.SERVER, Component.translatableWithFallback("glazedmenu.servers.empty", "No servers yet"))
                    : new Notice(ConfigIcons.SEARCH, Component.translatableWithFallback("glazedmenu.servers.none", "No servers match")));
        }
        // The games on the network, apart from the servers.
        rows.add(new Heading(Component.translatableWithFallback("glazedmenu.servers.section.lan", "On Your Network")));
        List<LanServer> games = this.lan.stream().filter(game -> query.isEmpty() || game.getMotd().toLowerCase(Locale.ROOT).contains(query)).toList();
        for (LanServer game : games) rows.add(new LanRow(game));
        if (games.isEmpty()) rows.add(new Notice(ConfigIcons.LAN, Component.translatable("lanServer.scanning")));
        return rows;
    }

    // A server's picture, uploaded again whenever the server sends another; the game's own picture of an unknown server
    // for one without.
    private Identifier picture(ServerData server) {
        FaviconTexture picture = this.pictures.computeIfAbsent(server, data -> FaviconTexture.forServer(this.minecraft.getTextureManager(), data.ip));
        byte[] bytes = server.getIconBytes();
        if (!Arrays.equals(bytes, this.pictureBytes.get(server))) {
            boolean uploaded = true;
            if (bytes == null) {
                picture.clear();
            } else {
                try {
                    picture.upload(NativeImage.read(bytes));
                } catch (Throwable t) {
                    GlazedMenu.LOGGER.error("Invalid icon for server {} ({})", server.name, server.ip, t);
                    uploaded = false;
                }
            }
            if (uploaded) {
                this.pictureBytes.put(server, bytes);
            } else {
                server.setIconBytes(null);
                this.servers.save();
            }
        }
        return picture.textureLocation();
    }

    private void closePictures() {
        for (FaviconTexture picture : this.pictures.values()) {
            if (!picture.isClosed()) picture.close();
        }
        this.pictures.clear();
        this.pictureBytes.clear();
    }

    // How well a server answers, from 0 (it doesn't) to 5; -1 while it's being asked.
    private static int signal(ServerData server) {
        return switch (server.state()) {
            case INITIAL, PINGING -> -1;
            case UNREACHABLE -> 0;
            default -> server.ping < 150 ? 5 : server.ping < 300 ? 4 : server.ping < 600 ? 3 : server.ping < 1000 ? 2 : 1;
        };
    }

    static Component stateName(ServerData server) {
        return switch (server.state()) {
            case INITIAL, PINGING -> Component.translatable("multiplayer.status.pinging");
            case UNREACHABLE -> Component.translatableWithFallback("glazedmenu.servers.state.unreachable", "No connection");
            case INCOMPATIBLE -> Component.translatableWithFallback("glazedmenu.servers.state.incompatible", "Incompatible");
            case SUCCESSFUL -> Component.translatable("multiplayer.status.online");
        };
    }

    static int stateColor(ServerData server) {
        return switch (server.state()) {
            case INITIAL, PINGING -> ConfigStyle.colors().textDim();
            case UNREACHABLE, INCOMPATIBLE -> ConfigStyle.colors().error();
            case SUCCESSFUL -> ConfigStyle.colors().success();
        };
    }

    // Five bars rising to the right, as many lit as the server answers well; a sweep while it's being asked.
    static void extractSignal(GuiGraphicsExtractor graphics, ServerData server, int x, int y, int index) {
        int signal = signal(server);
        int lit = signal;
        int color = signal >= 4 ? ConfigStyle.colors().success() : signal == 3 ? ConfigStyle.colors().warning() : ConfigStyle.colors().error();
        if (signal < 0) {
            // Up and back down, each row a little out of step with the last.
            int step = (int) (Util.getMillis() / 100L + index * 2L & 7L);
            lit = step > 4 ? 8 - step : step;
            lit++;
            color = ConfigStyle.colors().textDim();
        }
        for (int i = 0; i < 5; i++) {
            int height = 2 + i * 2;
            graphics.fill(x + i * 3, y + 10 - height, x + i * 3 + 2, y + 10, i < lit ? color : ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0x60));
        }
    }

    // --- Layout ---

    @Override
    protected Component searchName() {
        return Component.translatableWithFallback("glazedmenu.servers.search", "Search servers");
    }

    @Override
    protected Component searchHint() {
        return Component.translatableWithFallback("glazedmenu.servers.search.hint", "Search servers… (Ctrl+F)");
    }

    @Override
    protected void initButtons() {
        // The top bar: asking the servers again, left of the search.
        FlatButton refresh = this.addRenderableWidget(FlatButton.icon(ConfigIcons.RESET, Component.translatable("selectServer.refresh").append(" (F5)"),
                button -> this.refresh()).style(FlatButton.Style.NORMAL));
        refresh.setPosition(this.headerRight - 20, this.topBarWidgetY(20));
        this.headerRight = refresh.getX() - 8;

        // The details pane: the star at its top, the server's actions along its bottom.
        int left = this.detailsLeft() + 8;
        int width = this.detailsWidth() - 16;
        int y = this.bodyBottom() - 8 - 20;
        this.favoriteButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.STAR_OUTLINE, CommonComponents.EMPTY, button -> {
            ServerData server = this.chosenServer();
            if (server == null) return;
            GameMarks.toggleFavoriteServer(server.ip);
            this.rebuildRows(true);
        }));
        this.favoriteButton.setPosition(left + width - 20, this.bodyTop() + 6);
        this.deleteButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.TRASH, Component.translatable("selectServer.delete"), button -> this.delete())
                .style(FlatButton.Style.DANGER));
        this.deleteButton.setPosition(left + width - 20, y);
        this.editButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.EDIT, Component.translatable("selectServer.edit"), button -> this.edit())
                .style(FlatButton.Style.NORMAL));
        this.editButton.setPosition(this.deleteButton.getX() - 4 - 20, y);
        int joinRight = this.editButton.getX() - 4;
        // Moving the server up or down the list, where the pane is wide enough for the buttons (Shift+arrows do it too).
        if (width >= 170) {
            this.downButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.DOWN,
                    Component.translatableWithFallback("glazedmenu.servers.move.down", "Move down (Shift+Down)"), button -> this.move(1)).style(FlatButton.Style.NORMAL));
            this.downButton.setPosition(joinRight - 20, y);
            this.upButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.UP,
                    Component.translatableWithFallback("glazedmenu.servers.move.up", "Move up (Shift+Up)"), button -> this.move(-1)).style(FlatButton.Style.NORMAL));
            this.upButton.setPosition(this.downButton.getX() - 4 - 20, y);
            joinRight = this.upButton.getX() - 4;
        } else {
            this.upButton = null;
            this.downButton = null;
        }
        this.joinButton = this.addRenderableWidget(FlatButton.of(Component.translatable("selectServer.select"), joinRight - left, button -> this.joinChosen())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.PLAY));
        this.joinButton.setPosition(left, y);

        // The bottom bar: a new server and a direct connection, and the way back.
        int barY = this.barWidgetY(this.bottomBarY());
        Component add = Component.translatable("selectServer.add");
        int addWidth = this.font.width(add) + 28;
        FlatButton addButton = this.addRenderableWidget(FlatButton.of(add, addWidth, button -> {
            this.editing = new ServerData("", "", ServerData.Type.OTHER);
            this.minecraft.gui.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.add.title"), this::addCallback, this.editing));
        }).withIcon(ConfigIcons.PLUS));
        addButton.setPosition(this.frameLeft() + 4, barY);
        Component direct = Component.translatable("selectServer.direct");
        int directWidth = this.font.width(direct) + 28;
        FlatButton directButton = this.addRenderableWidget(FlatButton.of(direct, directWidth, button -> {
            this.editing = new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
            this.minecraft.gui.setScreen(new DirectJoinServerScreen(this, this::directCallback, this.editing));
        }).withIcon(ConfigIcons.CONNECT));
        directButton.setPosition(addButton.getX() + addWidth + 4, barY);
        FlatButton back = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_BACK, 72, button -> this.onClose()));
        back.setPosition(this.frameLeft() + this.frameWidth() - 4 - 72, barY);
        // A narrow window: the direct connection gives way to the way back.
        directButton.visible = directButton.getX() + directWidth + 4 <= back.getX();
    }

    @Override
    protected void onChosen() {
        if (this.joinButton == null || this.editButton == null || this.deleteButton == null || this.favoriteButton == null) return;
        ServerData server = this.chosenServer();
        this.joinButton.visible = server != null || this.chosenLan() != null;
        this.editButton.visible = this.deleteButton.visible = this.favoriteButton.visible = server != null;
        if (this.upButton != null && this.downButton != null) {
            this.upButton.visible = this.downButton.visible = server != null;
            if (server != null) {
                int index = this.indexOf(server);
                this.upButton.active = index > 0;
                this.downButton.active = index < this.servers.size() - 1;
            }
        }
        if (server == null) return;
        boolean favorite = GameMarks.isFavoriteServer(server.ip);
        this.favoriteButton.withIcon(favorite ? ConfigIcons.STAR : ConfigIcons.STAR_OUTLINE);
        this.favoriteButton.iconColor(favorite ? 0xFFF2C94C : 0);
        this.favoriteButton.tooltip(favorite ? Component.translatableWithFallback("glazedmenu.list.favorite.remove", "Remove from favorites")
                : Component.translatableWithFallback("glazedmenu.list.favorite.add", "Add to favorites"));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Before the arrows move the choice: with Shift they move the chosen server.
        if (event.hasShiftDown() && (event.key() == KEY_UP || event.key() == KEY_DOWN) && this.chosenServer() != null) {
            this.move(event.key() == KEY_DOWN ? 1 : -1);
            return true;
        }
        if (super.keyPressed(event)) return true;
        if (event.key() == KEY_F5) {
            this.refresh();
            return true;
        }
        return false;
    }

    // --- The server's actions, as the game's own screen does them ---

    private void join(ServerData server) {
        LoadingLook.expect(LoadingLook.serverKey(server.ip), server.name);
        ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(server.ip), server, false, null);
    }

    private void joinChosen() {
        ServerData server = this.chosenServer();
        LanServer game = this.chosenLan();
        if (server != null) this.join(server);
        else if (game != null) this.join(new ServerData(game.getMotd(), game.getAddress(), ServerData.Type.LAN));
    }

    private void move(int by) {
        ServerData server = this.chosenServer();
        if (server == null) return;
        int index = this.indexOf(server);
        int target = index + by;
        if (target < 0 || target >= this.servers.size()) return;
        this.servers.swap(index, target);
        this.rebuildRows(true);
    }

    private void edit() {
        ServerData server = this.chosenServer();
        if (server == null) return;
        this.editing = new ServerData(server.name, server.ip, ServerData.Type.OTHER);
        this.editing.copyFrom(server);
        this.minecraft.gui.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.edit.title"), this::editCallback, this.editing));
    }

    private void delete() {
        ServerData server = this.chosenServer();
        if (server == null || server.name == null) return;
        this.minecraft.gui.setScreen(new ConfirmPopup(this, this.theme, Component.translatable("selectServer.deleteQuestion"),
                Component.translatable("selectServer.deleteWarning", server.name), Component.translatable("selectServer.deleteButton"), true, confirmed -> {
            if (confirmed) {
                this.servers.remove(server);
                this.servers.save();
                this.chosen = null;
            }
            this.minecraft.gui.setScreen(this);
        }));
    }

    private void editCallback(boolean result) {
        ServerData server = this.chosenServer();
        if (result && server != null && this.editing != null) {
            // A favorite stays one under its new address.
            boolean favorite = GameMarks.isFavoriteServer(server.ip);
            if (favorite && !server.ip.equals(this.editing.ip)) {
                GameMarks.toggleFavoriteServer(server.ip);
                if (!GameMarks.isFavoriteServer(this.editing.ip)) GameMarks.toggleFavoriteServer(this.editing.ip);
            }
            server.name = this.editing.name;
            server.ip = this.editing.ip;
            server.copyFrom(this.editing);
            this.servers.save();
            // Asked again, at what may be another address.
            server.setState(ServerData.State.INITIAL);
        }
        this.minecraft.gui.setScreen(this);
    }

    private void addCallback(boolean result) {
        if (result && this.editing != null) {
            ServerData hidden = this.servers.unhide(this.editing.ip);
            if (hidden != null) {
                hidden.copyNameIconFrom(this.editing);
                this.chosen = hidden;
            } else {
                this.servers.add(this.editing, false);
                this.chosen = this.editing;
            }
            this.servers.save();
        }
        this.minecraft.gui.setScreen(this);
    }

    private void directCallback(boolean result) {
        if (!result || this.editing == null) {
            this.minecraft.gui.setScreen(this);
            return;
        }
        ServerData known = this.servers.get(this.editing.ip);
        if (known == null) {
            this.servers.add(this.editing, true);
            this.servers.save();
            this.join(this.editing);
        } else {
            this.join(known);
        }
    }

    // --- Drawing ---

    @Override
    protected Component subtitle() {
        long online = this.all().stream().filter(server -> server.state() == ServerData.State.SUCCESSFUL || server.state() == ServerData.State.INCOMPATIBLE).count();
        Component servers = this.servers.size() == 1 ? Component.translatableWithFallback("glazedmenu.servers.status.one", "1 server · %s online", online)
                : Component.translatableWithFallback("glazedmenu.servers.status", "%s servers · %s online", this.servers.size(), online);
        return this.lan.isEmpty() ? servers
                : servers.copy().append(Component.translatableWithFallback("glazedmenu.servers.status.lan", " · %s on your network", this.lan.size()));
    }

    private Component address(String address) {
        return this.minecraft.options.hideServerAddress ? Component.translatable("selectServer.hiddenAddress") : Component.literal(address);
    }

    @Override
    protected void extractDetails(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        ServerData server = this.chosenServer();
        LanServer game = this.chosenLan();
        int bottom = y + height - 26;
        int textX = x + PICTURE + 7;
        if (game != null) {
            this.extractPicture(graphics, null, ConfigIcons.LAN, GlazedBrand.identity(0xFF5BC467), x, y, PICTURE);
            ConfigStyle.text(graphics, this.font, Component.translatable("lanServer.title").withStyle(ChatFormatting.BOLD), textX, y + 4, width - PICTURE - 7, ConfigStyle.colors().text());
            ConfigStyle.text(graphics, this.font, this.address(game.getAddress()), textX, y + 16, width - PICTURE - 7, ConfigStyle.colors().textMuted());
            int lineY = y + PICTURE + 8;
            for (FormattedCharSequence line : this.font.split(Component.literal(game.getMotd()), width)) {
                if (lineY + 10 > bottom) break;
                graphics.text(this.font, line, x, lineY, ConfigStyle.colors().textDim(), false);
                lineY += 10;
            }
            return;
        }
        if (server == null) {
            ConfigStyle.centeredText(graphics, this.font, Component.translatableWithFallback("glazedmenu.servers.pick", "Pick a server"),
                    x + width / 2, y + height / 2 - 4, width, ConfigStyle.colors().textMuted());
            return;
        }
        this.extractPicture(graphics, this.picture(server), ConfigIcons.SERVER, GlazedBrand.identity(0xFFB07CF0), x, y, PICTURE);
        // The name beside the picture (leaving the star its corner), the address under it.
        ConfigStyle.text(graphics, this.font, Component.literal(server.name).withStyle(ChatFormatting.BOLD), textX, y + 4, width - PICTURE - 7 - 20, ConfigStyle.colors().text());
        ConfigStyle.text(graphics, this.font, this.address(server.ip), textX, y + 16, width - PICTURE - 7, ConfigStyle.colors().textMuted());
        extractSignal(graphics, server, textX, y + 27, 0);
        if (server.state() == ServerData.State.SUCCESSFUL || server.state() == ServerData.State.INCOMPATIBLE) {
            ConfigStyle.text(graphics, this.font, Component.translatable("multiplayer.status.ping", server.ping), textX + 19, y + 29, width - PICTURE - 26, ConfigStyle.colors().textMuted());
        }

        int lineY = y + PICTURE + 8;
        // Whether it answers, and its version, as tags.
        int tagX = x + ConfigStyle.badge(graphics, this.font, stateName(server), x, lineY, stateColor(server)) + 4;
        boolean answered = server.state() == ServerData.State.SUCCESSFUL || server.state() == ServerData.State.INCOMPATIBLE;
        if (answered && tagX + this.font.width(server.version) + 8 <= x + width) {
            ConfigStyle.badge(graphics, this.font, server.version, tagX, lineY,
                    server.state() == ServerData.State.INCOMPATIBLE ? ConfigStyle.colors().error() : ConfigStyle.colors().textDim());
        }
        lineY += 17;

        // What the server says of itself: two lines of it, in a box.
        List<FormattedCharSequence> message = this.font.split(server.motd, width - 10);
        if (!message.isEmpty() && lineY + 26 <= bottom) {
            int lines = Math.min(2, message.size());
            ConfigStyle.rect(graphics, x, lineY, width, lines * 10 + 6, ConfigStyle.withAlpha(ConfigStyle.colors().field(), 0xA0));
            ConfigStyle.outline(graphics, x, lineY, width, lines * 10 + 6, ConfigStyle.colors().panelBorder());
            for (int i = 0; i < lines; i++) graphics.text(this.font, message.get(i), x + 5, lineY + 4 + i * 10, ConfigStyle.colors().textDim(), false);
            lineY += lines * 10 + 6 + 6;
        }
        if (answered && lineY + 11 <= bottom) {
            lineY = this.detailLine(graphics, Component.translatableWithFallback("glazedmenu.servers.detail.players", "Players"), server.status, x, lineY, width, ConfigStyle.colors().text());
        }
        if (lineY + 11 <= bottom) {
            lineY = this.detailLine(graphics, Component.translatable("manageServer.resourcePack"), server.getResourcePackStatus().getName(), x, lineY, width, ConfigStyle.colors().text());
        }
        // How long it's been played on, as Glazed Menu has counted it.
        String timesKey = LoadingLook.serverKey(server.ip);
        if (PlayTimes.total(timesKey) > 0) {
            if (lineY + 11 <= bottom) {
                lineY = this.detailLine(graphics, Component.translatableWithFallback("glazedmenu.detail.last_session", "Last session"), PlayTimes.format(PlayTimes.lastSession(timesKey)), x, lineY, width,
                        ConfigStyle.colors().text());
            }
            if (lineY + 11 <= bottom) {
                lineY = this.detailLine(graphics, Component.translatableWithFallback("glazedmenu.detail.total_time", "Time played"), PlayTimes.format(PlayTimes.total(timesKey)), x, lineY, width,
                        ConfigStyle.colors().text());
            }
        }
        // Who's there, as far as the server names them, in the room that's left.
        lineY += 3;
        for (Component player : server.playerList) {
            if (lineY + 10 > bottom) break;
            ConfigStyle.text(graphics, this.font, player, x, lineY, width, ConfigStyle.colors().textDim());
            lineY += 10;
        }
    }

    // A server: its picture, its name over what it says of itself, how many play there, and how well it answers.
    private final class ServerRow extends EntryRow {
        private final ServerData server;

        ServerRow(ServerData server) {
            this.server = server;
        }

        @Override
        protected Object key() {
            return this.server;
        }

        @Override
        protected void open() {
            GlazedServersScreen.this.join(this.server);
        }

        @Override
        protected void extractEntry(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean chosen, boolean hovered, int mouseX, int mouseY) {
            GlazedServersScreen screen = GlazedServersScreen.this;
            ServerData server = this.server;
            screen.extractPicture(graphics, screen.picture(server), ConfigIcons.SERVER, GlazedBrand.identity(0xFFB07CF0), x + 3, y + (height - ROW_ICON) / 2, ROW_ICON);
            int right = x + width - 6 - 15;
            int signalY = y + (height - 10) / 2;
            extractSignal(graphics, server, right, signalY, screen.indexOf(server));
            if (hovered && mouseX >= right - 2 && mouseX < right + 17) {
                List<Component> tip = new ArrayList<>();
                tip.add(server.state() == ServerData.State.SUCCESSFUL ? Component.translatable("multiplayer.status.ping", server.ping) : stateName(server));
                if (server.state() == ServerData.State.SUCCESSFUL || server.state() == ServerData.State.INCOMPATIBLE) tip.addAll(server.playerList);
                graphics.setTooltipForNextFrame(screen.font, tip.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
            }
            // How many play there; a server of another version says its version instead, in red.
            Component status = server.state() == ServerData.State.INCOMPATIBLE ? server.version.copy().withStyle(ChatFormatting.RED) : server.status;
            int statusWidth = Math.min(screen.font.width(status), width / 3);
            right -= 5 + statusWidth;
            ConfigStyle.text(graphics, screen.font, status, right, y + (height - 8) / 2, statusWidth, ConfigStyle.colors().textDim());
            if (GameMarks.isFavoriteServer(server.ip)) {
                right -= 14;
                ConfigIcons.STAR.draw(graphics, right, y + (height - ConfigIcons.SIZE) / 2, 0xFFF2C94C);
            }
            int textX = x + 3 + ROW_ICON + 6;
            int textWidth = right - 5 - textX;
            ConfigStyle.text(graphics, screen.font, Component.literal(server.name).withStyle(ChatFormatting.BOLD), textX, y + 5, textWidth, ConfigStyle.colors().text());
            List<FormattedCharSequence> message = screen.font.split(server.motd, Math.max(20, textWidth));
            if (!message.isEmpty()) {
                graphics.text(screen.font, message.getFirst(), textX, y + 16,
                        server.state() == ServerData.State.UNREACHABLE ? ConfigStyle.colors().error() : ConfigStyle.colors().textDim(), false);
            } else if (server.state() == ServerData.State.PINGING || server.state() == ServerData.State.INITIAL) {
                ConfigStyle.text(graphics, screen.font, Component.translatable("multiplayer.status.pinging"), textX, y + 16, textWidth, ConfigStyle.colors().textMuted());
            }
        }
    }

    // A game on the network: what its host calls it, and where it is.
    private final class LanRow extends EntryRow {
        private final LanServer game;

        LanRow(LanServer game) {
            this.game = game;
        }

        @Override
        protected Object key() {
            return this.game;
        }

        @Override
        protected void open() {
            GlazedServersScreen.this.join(new ServerData(this.game.getMotd(), this.game.getAddress(), ServerData.Type.LAN));
        }

        @Override
        protected void extractEntry(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean chosen, boolean hovered, int mouseX, int mouseY) {
            GlazedServersScreen screen = GlazedServersScreen.this;
            screen.extractPicture(graphics, null, ConfigIcons.LAN, GlazedBrand.identity(0xFF5BC467), x + 3, y + (height - ROW_ICON) / 2, ROW_ICON);
            int textX = x + 3 + ROW_ICON + 6;
            int textWidth = x + width - 6 - textX;
            ConfigStyle.text(graphics, screen.font, Component.literal(this.game.getMotd()).withStyle(ChatFormatting.BOLD), textX, y + 5, textWidth, ConfigStyle.colors().text());
            ConfigStyle.text(graphics, screen.font, Component.translatable("lanServer.title").append(" · ").append(screen.address(this.game.getAddress())),
                    textX, y + 16, textWidth, ConfigStyle.colors().textDim());
        }
    }
}
