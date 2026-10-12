package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.internal.compat.Compat;
import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.ixdarklord.glazedmenu.internal.compat.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The end of a connection, in place of the game's own screen: a card over the game's panorama, with
 * what happened as its title, the server it was (as it answers now, asked once), the reason as the game or the server
 * gives it (scrolled when it's long), and how long was played. Under them: the reason copied, the game's own way back,
 * and the server joined again.
 * <p>
 * It's a face on the game's own screen, which it keeps: the title and the reason are read from it, and the way back
 * (and whatever else the game offers there, like a report's folder) presses its own buttons.
 */
public final class GlazedDisconnectedScreen extends StyledPopup {
    private static final int WIDTH = 320;
    private static final int STRIP_HEIGHT = 34;
    private static final int LINE = 9;
    private static final int MAX_LINES = 7;
    // The thread asking the server how it is now: one is plenty, and it never keeps the game from closing.
    private static final ExecutorService ASKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Glazed Menu Disconnected Server");
        thread.setDaemon(true);
        return thread;
    });
    // The face of the game's screen now open, so that screen coming back comes back as its face.
    private static WeakReference<GlazedDisconnectedScreen> current = new WeakReference<>(null);

    private final DisconnectedScreen game;
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    // The server the connection was to, if it was to one (not a world of the player's own), and how it answers now.
    private final @Nullable ServerData server;
    private final @Nullable ServerData now;
    private final long playedMillis;
    private boolean asked;
    private Component reason = CommonComponents.EMPTY;
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;
    private int extraRows;
    private @Nullable FaviconTexture picture;
    private byte[] pictureBytes;
    private int copiedTicks;

    private GlazedDisconnectedScreen(DisconnectedScreen game) {
        super(null, game.getTitle(), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.game = game;
        this.server = LoadingLook.lastServer();
        this.playedMillis = LoadingLook.playedMillis();
        this.now = this.server == null ? null : new ServerData(this.server.name, this.server.ip, ServerData.Type.OTHER);
    }

    /** The screen to open instead: this one for the game's own disconnected screen, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof DisconnectedScreen game) || screen.getClass() != DisconnectedScreen.class || !GlazedSettings.replaceDisconnected()) return screen;
        GlazedDisconnectedScreen face = current.get();
        if (face != null && face.game == game) return face;
        face = new GlazedDisconnectedScreen(game);
        current = new WeakReference<>(face);
        return face;
    }

    // --- The game's own screen ---

    // Its buttons in their order: the last is the way back, any before it are extras (a report's link or folder).
    private List<Button> gameButtons() {
        List<Button> buttons = new ArrayList<>();
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof Button button) buttons.add(button);
        }
        return buttons;
    }

    private static void press(AbstractWidget widget) {
        Compat.mouseClicked(widget, new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(0, 0)), false);
    }

    private boolean canReconnect() {
        return this.server != null && this.minecraft.allowsMultiplayer() && ServerAddress.isValidAddress(this.server.ip);
    }

    private void reconnect() {
        if (this.server == null) return;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), this.minecraft, ServerAddress.parseString(this.server.ip), this.server, false, null);
    }

    private void copy() {
        this.minecraft.keyboardHandler.setClipboard(this.title.getString() + "\n" + this.reason.getString());
        this.copiedTicks = 40;
    }

    // --- The server now ---

    @Override
    public void tick() {
        super.tick();
        this.pinger.tick();
        if (this.copiedTicks > 0) this.copiedTicks--;
        ServerData server = this.now;
        if (server == null || this.asked) return;
        this.asked = true;
        Minecraft minecraft = this.minecraft;
        server.setState(ServerData.State.PINGING);
        server.motd = CommonComponents.EMPTY;
        server.status = CommonComponents.EMPTY;
        ASKER.submit(() -> {
            try {
                this.pinger.pingServer(server, () -> {}, () -> server.setState(server.protocol == SharedConstants.getCurrentVersion().getProtocolVersion()
                        ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE));
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
    public void removed() {
        super.removed();
        this.pinger.removeAll();
        if (this.picture != null) this.picture.close();
        this.picture = null;
        this.pictureBytes = null;
    }

    // --- Layout ---

    private int shownLines() {
        return Math.clamp(this.lines.size(), 2, MAX_LINES);
    }

    private int reasonTop() {
        return this.contentTop() + (this.server != null ? STRIP_HEIGHT + 6 : 0) + 11;
    }

    private int reasonHeight() {
        return this.shownLines() * LINE + 9;
    }

    @Override
    protected void initPopup() {
        // The game's screen is laid out too: its text and its buttons are what's read and pressed here.
        Compat.init(this.game, this.width, this.height);
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof MultiLineTextWidget text) this.reason = text.getMessage();
        }
        int inner = Math.min(WIDTH, this.width - 16) - PADDING * 2;
        this.lines = this.font.split(this.reason, inner - 14);
        List<Button> buttons = this.gameButtons();
        List<Button> extras = buttons.isEmpty() ? List.of() : buttons.subList(0, buttons.size() - 1);
        this.extraRows = extras.size();
        this.setPanel(WIDTH, PADDING + TITLE_HEIGHT + (this.server != null ? STRIP_HEIGHT + 6 : 0) + 11 + this.reasonHeight() + 6
                + this.extraRows * 23 + FOOTER + PADDING);
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.lines.size() - this.shownLines()));
        int left = this.contentLeft();
        int width = this.contentWidth();

        // What else the game offers here, each on a row of its own over the footer.
        int y = this.reasonTop() + this.reasonHeight() + 6;
        for (Button extra : extras) {
            FlatButton button = this.addRenderableWidget(FlatButton.of(extra.getMessage(), width, pressed -> press(extra)));
            button.setPosition(left, y);
            y += 23;
        }

        y = this.footerButtonY();
        FlatButton copy = this.addRenderableWidget(FlatButton.icon(ConfigIcons.COPY, Component.translatableWithFallback("glazedmenu.disconnected.copy", "Copy the reason"), button -> this.copy()));
        copy.setPosition(left, y);
        int right = left + width;
        if (this.canReconnect()) {
            Component label = Component.translatableWithFallback("glazedmenu.disconnected.reconnect", "Reconnect");
            int reconnectWidth = Math.max(84, this.font.width(label) + 30);
            FlatButton reconnect = this.addRenderableWidget(FlatButton.of(label, reconnectWidth, button -> this.reconnect())
                    .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.RESTART));
            reconnect.setPosition(right - reconnectWidth, y);
            right -= reconnectWidth + 4;
            this.setInitialFocus(reconnect);
        }
        if (!buttons.isEmpty()) {
            Button back = buttons.getLast();
            int backWidth = Math.min(right - (left + 24), Math.max(84, this.font.width(back.getMessage()) + 16));
            FlatButton button = this.addRenderableWidget(FlatButton.of(back.getMessage(), backWidth, pressed -> press(back)));
            button.setPosition(right - backWidth, y);
        }
    }

    // As the game's own: Escape doesn't leave it, and neither does a click beside the card.
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int most = Math.max(0, this.lines.size() - this.shownLines());
        if (most > 0 && scrollY != 0) {
            this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY), 0, most);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(this.title, this.reason);
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.WARNING;
    }

    @Override
    protected @Nullable Component titleNote() {
        if (this.copiedTicks > 0) return Component.translatableWithFallback("glazedmenu.disconnected.copied", "Copied");
        if (this.playedMillis < 60_000) return null;
        long minutes = this.playedMillis / 60_000;
        String time = minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min";
        return Component.translatableWithFallback("glazedmenu.disconnected.played", "Played %s", time);
    }

    // --- Drawing ---

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = this.contentLeft();
        int width = this.contentWidth();
        int y = this.contentTop();
        ServerData server = this.now;
        if (server != null) {
            this.extractServer(graphics, server, left, y, width);
            y += STRIP_HEIGHT + 6;
        }
        ConfigStyle.text(graphics, this.font, Component.translatableWithFallback("glazedmenu.disconnected.reason", "Reason").withStyle(ChatFormatting.BOLD),
                left + 1, y, width, ConfigStyle.colors().textDim());
        y += 11;
        int height = this.reasonHeight();
        ConfigStyle.rect(graphics, left, y, width, height, ConfigStyle.withAlpha(ConfigStyle.colors().field(), 0x90));
        ConfigStyle.outline(graphics, left, y, width, height, ConfigStyle.withAlpha(ConfigStyle.colors().error(), 0x90));
        int shown = this.shownLines();
        // A short reason sits in the middle of its box; a long one fills it and scrolls.
        int textY = y + 5 + Math.max(0, shown - this.lines.size()) * LINE / 2;
        for (int i = 0; i < shown && this.scroll + i < this.lines.size(); i++) {
            graphics.text(this.font, this.lines.get(this.scroll + i), left + 6, textY + i * LINE, ConfigStyle.colors().text(), false);
        }
        int most = this.lines.size() - shown;
        if (most > 0) {
            int track = height - 6;
            int thumb = Math.max(8, track * shown / this.lines.size());
            int thumbY = y + 3 + (track - thumb) * this.scroll / most;
            graphics.fill(left + width - 5, thumbY, left + width - 3, thumbY + thumb, ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0xB0));
        }
    }

    // The server it was: its picture, its name over its address, and how it answers now.
    private void extractServer(GuiGraphicsExtractor graphics, ServerData server, int left, int y, int width) {
        ConfigStyle.rect(graphics, left, y, width, STRIP_HEIGHT, ConfigStyle.withAlpha(ConfigStyle.colors().field(), 0x90));
        ConfigStyle.outline(graphics, left, y, width, STRIP_HEIGHT, ConfigStyle.colors().panelBorder());
        int size = 24;
        int pictureX = left + 5;
        int pictureY = y + (STRIP_HEIGHT - size) / 2;
        if (this.picture == null) this.picture = FaviconTexture.forServer(this.minecraft.getTextureManager(), "glazedmenu-disconnected");
        byte[] bytes = server.getIconBytes();
        if (bytes == null && this.server != null) bytes = this.server.getIconBytes();
        if (!Arrays.equals(bytes, this.pictureBytes)) {
            this.pictureBytes = bytes;
            if (bytes == null) {
                this.picture.clear();
            } else {
                try {
                    this.picture.upload(NativeImage.read(bytes));
                } catch (Throwable t) {
                    this.picture.clear();
                }
            }
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, this.picture.textureLocation(), pictureX, pictureY, 0, 0, size, size, size, size);
        ConfigStyle.pictureFrame(graphics, pictureX, pictureY, size, size);
        // A failed ping leaves the server "being asked" for good, with only its message saying so.
        if (server.state() == ServerData.State.PINGING && server.motd.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("multiplayer.status.cannot_connect")) server.setState(ServerData.State.UNREACHABLE);
        Component status = GlazedServersScreen.stateName(server);
        int right = left + width - 6;
        int tagWidth = this.font.width(status) + 8;
        ConfigStyle.badge(graphics, this.font, status, right - tagWidth, y + (STRIP_HEIGHT - 11) / 2, GlazedServersScreen.stateColor(server));
        GlazedServersScreen.extractSignal(graphics, server, right - tagWidth - 21, y + (STRIP_HEIGHT - 10) / 2, 0);
        int textRight = right - tagWidth - 27;
        int textX = pictureX + size + 7;
        boolean named = !server.name.isBlank() && !server.name.equals(server.ip);
        boolean answered = server.state() == ServerData.State.SUCCESSFUL || server.state() == ServerData.State.INCOMPATIBLE;
        Component under = answered && !server.status.getString().isBlank()
                ? Component.literal(server.ip + "  ·  ").append(server.status) : Component.literal(server.ip);
        if (named) {
            ConfigStyle.text(graphics, this.font, Component.literal(server.name).withStyle(ChatFormatting.BOLD), textX, y + 7, textRight - textX, ConfigStyle.colors().text());
            ConfigStyle.text(graphics, this.font, under, textX, y + 18, textRight - textX, ConfigStyle.colors().textMuted());
        } else {
            ConfigStyle.text(graphics, this.font, under, textX, y + (STRIP_HEIGHT - 8) / 2, textRight - textX, ConfigStyle.colors().text());
        }
    }
}
