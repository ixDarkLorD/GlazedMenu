package net.ixdarklord.glazedmenu.internal.game;

import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Connecting to a server by its address, in place of the game's own screen: a popup over the list of servers with the
 * address (the one typed last, as the game remembers it), and under it the server at that address as it answers (asked
 * once the typing has rested a moment), so a wrong address shows before the joining. Under them, Cancel and Join.
 * <p>
 * It's a face on the game's own screen, which it keeps: the address is typed into that screen's own box, and Join and
 * Cancel press its own buttons, so the joining is done exactly as the game does it.
 */
public final class GlazedDirectConnectScreen extends StyledPopup {
    private static final String VANILLA = "net.minecraft.client.gui.screens.DirectJoinServerScreen";
    private static final int WIDTH = 300;
    private static final int STRIP_HEIGHT = 34;
    private static final int KEY_ENTER = 257;
    private static final int KEY_NUMPAD_ENTER = 335;
    // How long the address rests before the server at it is asked, in ticks.
    private static final int ASK_DELAY = 16;
    // The thread asking the server being typed: one is plenty, and it never keeps the game from closing.
    private static final ExecutorService ASKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Glazed Menu Direct Connection Preview");
        thread.setDaemon(true);
        return thread;
    });
    // The face of the game's screen now open, so that screen coming back comes back as its face.
    private static WeakReference<GlazedDirectConnectScreen> current = new WeakReference<>(null);

    private final DirectJoinServerScreen game;
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private String address = "";
    private boolean read;
    // The server at the address typed, as it answers; null before one is asked.
    private @Nullable ServerData preview;
    private int sinceTyped;
    private @Nullable FaviconTexture picture;
    private byte[] pictureBytes;
    private @Nullable FlatButton joinButton;

    private GlazedDirectConnectScreen(@Nullable Screen parent, DirectJoinServerScreen game) {
        super(parent, game.getTitle(), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.game = game;
    }

    /** The screen to open instead: this one for the game's own Direct Connection screen, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof DirectJoinServerScreen game) || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceDirectConnect()) return screen;
        GlazedDirectConnectScreen face = current.get();
        if (face != null && face.game == game) return face;
        face = new GlazedDirectConnectScreen(Minecraft.getInstance().gui.screen(), game);
        current = new WeakReference<>(face);
        return face;
    }

    // --- The game's own screen ---

    private static @Nullable String key(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    private @Nullable EditBox gameBox() {
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof EditBox box) return box;
        }
        return null;
    }

    private @Nullable AbstractWidget gameButton(String name) {
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof AbstractWidget widget && name.equals(key(widget))) return widget;
        }
        return null;
    }

    private static void press(AbstractWidget widget) {
        widget.mouseClicked(new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(0, 0)), false);
    }

    // What's typed here, into the game's own box (which its Join reads, and which turns Join off for a bad address).
    private void pushToGame() {
        EditBox box = this.gameBox();
        if (box != null && !box.getValue().equals(this.address)) box.setValue(this.address);
        AbstractWidget join = this.gameButton("selectServer.select");
        if (this.joinButton != null) this.joinButton.active = join != null && join.active;
    }

    private void join() {
        this.pushToGame();
        AbstractWidget join = this.gameButton("selectServer.select");
        if (join == null || !join.active) return;
        // The address is remembered for the next time, as the game's own screen remembers it as it closes.
        this.minecraft.options.lastMpIp = this.address;
        this.minecraft.options.save();
        LoadingLook.expect(LoadingLook.serverKey(this.address), null);
        press(join);
    }

    private void cancel() {
        this.minecraft.options.lastMpIp = this.address;
        this.minecraft.options.save();
        AbstractWidget cancel = this.gameButton("gui.cancel");
        if (cancel != null) press(cancel);
        else this.game.onClose();
    }

    // --- The server at the address ---

    @Override
    public void tick() {
        super.tick();
        this.pinger.tick();
        if (this.sinceTyped < 0 || ++this.sinceTyped < ASK_DELAY) return;
        // Rested: the server at the address is asked, once.
        this.sinceTyped = -1;
        this.pinger.removeAll();
        if (this.address.isBlank() || !ServerAddress.isValidAddress(this.address)) {
            this.preview = null;
            return;
        }
        ServerData server = new ServerData(this.address, this.address, ServerData.Type.OTHER);
        this.preview = server;
        Minecraft minecraft = this.minecraft;
        server.setState(ServerData.State.PINGING);
        server.motd = CommonComponents.EMPTY;
        server.status = CommonComponents.EMPTY;
        ASKER.submit(() -> {
            try {
                this.pinger.pingServer(server, () -> {}, () -> server.setState(server.protocol == SharedConstants.getCurrentVersion().protocolVersion()
                        ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE), EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()));
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
        if (this.picture != null && !this.picture.isClosed()) this.picture.close();
        this.picture = null;
        this.pictureBytes = null;
    }

    // --- Layout ---

    @Override
    protected void initPopup() {
        // The game's screen is laid out too (which makes its box anew, with the address typed last): what's typed here
        // goes back into it.
        this.game.init(this.width, this.height);
        if (!this.read) {
            EditBox box = this.gameBox();
            if (box != null) this.address = box.getValue();
            this.read = true;
        }
        this.setPanel(WIDTH, PADDING + TITLE_HEIGHT + 11 + 18 + 6 + STRIP_HEIGHT + 6 + FOOTER + PADDING);
        int left = this.contentLeft();
        int top = this.contentTop();
        StyledEditBox addressBox = this.addRenderableWidget(new StyledEditBox(this.font, this.contentWidth(), 18, Component.translatable("manageServer.enterIp")));
        addressBox.setMaxLength(128);
        addressBox.setValue(this.address);
        addressBox.setResponder(text -> {
            this.address = text;
            this.pushToGame();
            this.sinceTyped = 0;
        });
        addressBox.setPosition(left, top + 11);
        this.setInitialFocus(addressBox);

        int y = this.footerButtonY();
        int buttonWidth = Math.min(120, (this.contentWidth() - 4) / 2);
        this.joinButton = this.addRenderableWidget(FlatButton.of(Component.translatable("selectServer.select"), buttonWidth, button -> this.join())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.PLAY));
        this.joinButton.setPosition(this.contentLeft() + this.contentWidth() - buttonWidth, y);
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, buttonWidth, button -> this.cancel()));
        cancel.setPosition(this.joinButton.getX() - 4 - buttonWidth, y);
        this.pushToGame();
    }

    // Enter joins, as on the game's own screen.
    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == KEY_ENTER || event.key() == KEY_NUMPAD_ENTER) && this.joinButton != null && this.joinButton.active && !(this.getFocused() instanceof FlatButton)) {
            this.join();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.cancel();
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.CONNECT;
    }

    // --- Drawing ---

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = this.contentLeft();
        int top = this.contentTop();
        int width = this.contentWidth();
        ConfigStyle.text(graphics, this.font, Component.translatable("manageServer.enterIp").withStyle(ChatFormatting.BOLD), left + 1, top, width, ConfigStyle.colors().textDim());

        // The server at the address: its picture, what it says of itself, and how it answers.
        int y = top + 11 + 18 + 6;
        ConfigStyle.rect(graphics, left, y, width, STRIP_HEIGHT, ConfigStyle.withAlpha(ConfigStyle.colors().field(), 0x90));
        ConfigStyle.outline(graphics, left, y, width, STRIP_HEIGHT, ConfigStyle.colors().panelBorder());
        ServerData server = this.preview;
        int size = 24;
        int pictureX = left + 5;
        int pictureY = y + (STRIP_HEIGHT - size) / 2;
        if (server != null) {
            if (this.picture == null) this.picture = FaviconTexture.forServer(this.minecraft.getTextureManager(), "glazedmenu-direct");
            byte[] bytes = server.getIconBytes();
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
        } else {
            ConfigStyle.iconFrame(graphics, pictureX, pictureY, size, size, ConfigStyle.withAlpha(GlazedBrand.identity(0xFFB07CF0), 0xD0));
            ConfigIcons.SERVER.draw(graphics, pictureX + 6, pictureY + 6, 12, ConfigStyle.readableOn(GlazedBrand.identity(0xFFB07CF0)));
        }
        boolean invalid = !this.address.isBlank() && !ServerAddress.isValidAddress(this.address);
        Component status = server != null ? GlazedServersScreen.stateName(server)
                : invalid ? Component.translatableWithFallback("glazedmenu.server.invalid", "Not a valid address")
                : this.address.isBlank() ? Component.translatableWithFallback("glazedmenu.server.enter", "Type the server's address")
                : Component.translatable("multiplayer.status.pinging");
        int statusColor = server != null ? GlazedServersScreen.stateColor(server) : invalid ? ConfigStyle.colors().error() : ConfigStyle.colors().textDim();
        int right = left + width - 6;
        // Nothing to ask a server of yet: what's wanted is said in the strip itself, beside the picture.
        if (server == null && (invalid || this.address.isBlank())) {
            ConfigStyle.text(graphics, this.font, status, pictureX + size + 7, y + (STRIP_HEIGHT - 8) / 2, right - (pictureX + size + 7), statusColor);
            return;
        }
        int tagWidth = this.font.width(status) + 8;
        ConfigStyle.badge(graphics, this.font, status, right - tagWidth, y + (STRIP_HEIGHT - 11) / 2, statusColor);
        int textRight = right - tagWidth - 6;
        if (server != null) {
            GlazedServersScreen.extractSignal(graphics, server, textRight - 15, y + (STRIP_HEIGHT - 10) / 2, 0);
            textRight -= 21;
        }
        int textX = pictureX + size + 7;
        // What the server says of itself over how many play there; before it answers, just the address.
        boolean answered = server != null && (server.state() == ServerData.State.SUCCESSFUL || server.state() == ServerData.State.INCOMPATIBLE);
        if (answered) {
            var message = this.font.split(server.motd, Math.max(20, textRight - textX));
            if (!message.isEmpty()) graphics.text(this.font, message.getFirst(), textX, y + 7, ConfigStyle.colors().textDim(), false);
            ConfigStyle.text(graphics, this.font, server.status, textX, y + 18, textRight - textX, ConfigStyle.colors().textMuted());
        } else {
            ConfigStyle.text(graphics, this.font, Component.literal(this.address), textX, y + (STRIP_HEIGHT - 8) / 2, textRight - textX, ConfigStyle.colors().textMuted());
        }
    }
}
