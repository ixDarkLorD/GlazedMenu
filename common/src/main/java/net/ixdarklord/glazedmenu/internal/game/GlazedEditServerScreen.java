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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
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
 * Adding or editing a server, in place of the game's own screen: a popup over the list of servers with the server's
 * name and address, and what's done about its resource packs as three cards; beside them the server as the list will
 * show it, asked for at the address typed (once the typing has rested a moment), so a wrong address shows before it's
 * saved. Under them, Cancel and Done.
 * <p>
 * It's a face on the game's own screen, which it keeps: the name and the address are typed into that screen's own boxes,
 * the resource packs' choice is made on its own button, and Done and Cancel press its own, so the server is saved
 * exactly as the game saves it. The game's screen is a button away, for what a mod adds to it.
 */
public final class GlazedEditServerScreen extends StyledPopup {
    private static final String VANILLA = "net.minecraft.client.gui.screens.ManageServerScreen";
    private static final int WIDTH = 400;
    private static final int COLUMN_GAP = 8;
    // The left column: two boxes under their names, then the three cards under theirs.
    private static final int FIELD = 35;
    private static final int PACKS_TOP = FIELD * 2 + 11;
    private static final int PACKS_HEIGHT = 32;
    private static final int COLUMN_HEIGHT = PACKS_TOP + PACKS_HEIGHT;
    // The preview under the left column, where the popup is too narrow for it beside.
    private static final int STRIP_HEIGHT = 34;
    // How long the address rests before the server at it is asked, in ticks.
    private static final int ASK_DELAY = 16;
    // The thread asking the server being typed: one is plenty, and it never keeps the game from closing.
    private static final ExecutorService ASKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Glazed Menu Server Preview");
        thread.setDaemon(true);
        return thread;
    });
    // The face of the game's screen now open, so that screen coming back comes back as its face.
    private static WeakReference<GlazedEditServerScreen> current = new WeakReference<>(null);

    private final ManageServerScreen game;
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private String name = "";
    private String address = "";
    private boolean read;
    // The server at the address typed, as it answers; null before one is asked.
    private @Nullable ServerData preview;
    private int sinceTyped;
    private @Nullable FaviconTexture picture;
    private byte[] pictureBytes;
    private @Nullable FlatButton doneButton;
    private boolean beside;
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private int leftWidth;
    // The player asked for the game's own screen: it's left alone from then on.
    private boolean gameShown;

    private GlazedEditServerScreen(@Nullable Screen parent, ManageServerScreen game) {
        super(parent, game.getTitle(), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.game = game;
    }

    /** The screen to open instead: this one for the game's own screen for a server's name and address, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof ManageServerScreen game) || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceEditServer()) return screen;
        GlazedEditServerScreen face = current.get();
        if (face != null && face.game == game) return face.gameShown ? screen : face;
        face = new GlazedEditServerScreen(Minecraft.getInstance().gui.screen(), game);
        current = new WeakReference<>(face);
        return face;
    }

    // --- The game's own screen ---

    private static @Nullable String key(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    // The game's two boxes, in the order it makes them: the name, then the address.
    private List<EditBox> gameBoxes() {
        List<EditBox> boxes = new ArrayList<>();
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof EditBox box) boxes.add(box);
        }
        return boxes;
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

    // The game's button for the resource packs, which goes from one choice to the next at a press.
    @SuppressWarnings("unchecked")
    private @Nullable CycleButton<ServerData.ServerPackStatus> packsButton() {
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof CycleButton<?> cycle && cycle.getValue() instanceof ServerData.ServerPackStatus) return (CycleButton<ServerData.ServerPackStatus>) cycle;
        }
        return null;
    }

    private ServerData.ServerPackStatus packs() {
        CycleButton<ServerData.ServerPackStatus> button = this.packsButton();
        return button != null ? button.getValue() : ServerData.ServerPackStatus.PROMPT;
    }

    // Pressed until it shows the choice (it has three).
    private void setPacks(ServerData.ServerPackStatus status) {
        CycleButton<ServerData.ServerPackStatus> button = this.packsButton();
        for (int i = 0; button != null && button.getValue() != status && i < ServerData.ServerPackStatus.values().length; i++) press(button);
    }

    // What's typed here, into the game's own boxes (which its Done reads, and which turn Done off for a bad address).
    private void pushToGame() {
        List<EditBox> boxes = this.gameBoxes();
        if (boxes.size() < 2) return;
        if (!boxes.get(0).getValue().equals(this.name)) boxes.get(0).setValue(this.name);
        if (!boxes.get(1).getValue().equals(this.address)) boxes.get(1).setValue(this.address);
        AbstractWidget done = this.gameButton("gui.done");
        if (this.doneButton != null) this.doneButton.active = done == null || done.active;
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
        ServerData server = new ServerData(this.name, this.address, ServerData.Type.OTHER);
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

    // A failed ping leaves the server "being asked" for good, with only its message saying so.
    private ServerData.State state(ServerData server) {
        if (server.state() == ServerData.State.PINGING && server.motd.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("multiplayer.status.cannot_connect")) server.setState(ServerData.State.UNREACHABLE);
        return server.state();
    }

    private Identifier picture(ServerData server) {
        if (this.picture == null) this.picture = FaviconTexture.forServer(this.minecraft.getTextureManager(), "glazedmenu-preview");
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
        return this.picture.textureLocation();
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
        // The game's screen is laid out too (which makes its boxes anew, as they first were): what's typed here goes
        // back into them.
        this.game.init(this.width, this.height);
        if (!this.read) {
            List<EditBox> boxes = this.gameBoxes();
            if (boxes.size() >= 2) {
                this.name = boxes.get(0).getValue();
                this.address = boxes.get(1).getValue();
            }
            this.read = true;
        }
        // The server beside the boxes where the popup is wide enough for both, else as a strip under them.
        this.setPanel(WIDTH, 0);
        this.beside = this.contentWidth() >= 330;
        int content = COLUMN_HEIGHT + (this.beside ? 0 : 6 + STRIP_HEIGHT);
        this.setPanel(WIDTH, PADDING + TITLE_HEIGHT + content + 6 + FOOTER + PADDING);
        int left = this.contentLeft();
        int top = this.contentTop();
        this.leftWidth = this.beside ? (this.contentWidth() - COLUMN_GAP) * 54 / 100 : this.contentWidth();
        if (this.beside) {
            this.previewX = left + this.leftWidth + COLUMN_GAP;
            this.previewY = top;
            this.previewWidth = this.contentWidth() - this.leftWidth - COLUMN_GAP;
            this.previewHeight = COLUMN_HEIGHT;
        } else {
            this.previewX = left;
            this.previewY = top + COLUMN_HEIGHT + 6;
            this.previewWidth = this.contentWidth();
            this.previewHeight = STRIP_HEIGHT;
        }

        StyledEditBox nameBox = this.addRenderableWidget(new StyledEditBox(this.font, this.leftWidth, 18, Component.translatable("manageServer.enterName")));
        nameBox.setPlaceholder(Component.translatable("selectServer.defaultName"));
        nameBox.setValue(this.name);
        nameBox.setResponder(text -> {
            this.name = text;
            this.pushToGame();
        });
        nameBox.setPosition(left, top + 11);
        StyledEditBox addressBox = this.addRenderableWidget(new StyledEditBox(this.font, this.leftWidth, 18, Component.translatable("manageServer.enterIp")));
        addressBox.setMaxLength(128);
        addressBox.setValue(this.address);
        addressBox.setResponder(text -> {
            this.address = text;
            this.pushToGame();
            this.sinceTyped = 0;
        });
        addressBox.setPosition(left, top + FIELD + 11);
        this.setInitialFocus(this.name.isEmpty() ? nameBox : addressBox);

        ServerData.ServerPackStatus[] choices = {ServerData.ServerPackStatus.PROMPT, ServerData.ServerPackStatus.ENABLED, ServerData.ServerPackStatus.DISABLED};
        int[] colors = {GlazedBrand.identity(0xFF4CC9E0), GlazedBrand.identity(0xFF5BC467), GlazedBrand.identity(0xFFF07A5A)};
        int each = (this.leftWidth - 4 * 2) / 3;
        for (int i = 0; i < choices.length; i++) {
            int cardWidth = i == 2 ? this.leftWidth - 2 * (each + 4) : each;
            this.addRenderableWidget(new PackCard(cardWidth, PACKS_HEIGHT, choices[i], colors[i])).setPosition(left + i * (each + 4), top + PACKS_TOP);
        }

        FlatButton own = this.addRenderableWidget(FlatButton.icon(ConfigIcons.LIST,
                Component.translatableWithFallback("glazedmenu.edit.game_screen", "The game's own screen"), button -> {
                    this.gameShown = true;
                    this.minecraft.gui.setScreen(this.game);
                }));
        own.setPosition(this.contentLeft() + this.contentWidth() - 20, this.panelY + 6);

        int y = this.footerButtonY();
        int buttonWidth = Math.min(110, (this.contentWidth() - 4) / 2);
        this.doneButton = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, buttonWidth, button -> {
            this.pushToGame();
            AbstractWidget done = this.gameButton("gui.done");
            if (done != null && done.active) press(done);
        }).style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        this.doneButton.setPosition(this.contentLeft() + this.contentWidth() - buttonWidth, y);
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, buttonWidth, button -> this.cancel()));
        cancel.setPosition(this.doneButton.getX() - 4 - buttonWidth, y);
        this.pushToGame();
    }

    private void cancel() {
        AbstractWidget cancel = this.gameButton("gui.cancel");
        if (cancel != null) press(cancel);
        else this.game.onClose();
    }

    @Override
    public void onClose() {
        this.cancel();
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.SERVER;
    }

    // --- Drawing ---

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = this.contentLeft();
        int top = this.contentTop();
        ConfigStyle.text(graphics, this.font, Component.translatable("manageServer.enterName").withStyle(ChatFormatting.BOLD), left + 1, top, this.leftWidth, ConfigStyle.colors().textDim());
        ConfigStyle.text(graphics, this.font, Component.translatable("manageServer.enterIp").withStyle(ChatFormatting.BOLD), left + 1, top + FIELD, this.leftWidth, ConfigStyle.colors().textDim());
        ConfigStyle.text(graphics, this.font, Component.translatable("manageServer.resourcePack").withStyle(ChatFormatting.BOLD), left + 1, top + FIELD * 2, this.leftWidth,
                ConfigStyle.colors().textDim());
        this.extractPreview(graphics, this.previewX, this.previewY, this.previewWidth, this.previewHeight);
    }

    // The server as the list will show it, and what it answers at the address typed.
    private void extractPreview(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.withAlpha(ConfigStyle.colors().field(), 0x90));
        ConfigStyle.outline(graphics, x, y, width, height, ConfigStyle.colors().panelBorder());
        ServerData server = this.preview;
        int size = this.beside ? 32 : 24;
        int pictureX = x + 6;
        int pictureY = y + (this.beside ? 7 : (height - size) / 2);
        if (server != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.picture(server), pictureX, pictureY, 0, 0, size, size, size, size);
            ConfigStyle.pictureFrame(graphics, pictureX, pictureY, size, size);
        } else {
            ConfigStyle.iconFrame(graphics, pictureX, pictureY, size, size, ConfigStyle.withAlpha(GlazedBrand.identity(0xFFB07CF0), 0xD0));
            ConfigIcons.SERVER.draw(graphics, pictureX + size / 4, pictureY + size / 4, size / 2, ConfigStyle.readableOn(GlazedBrand.identity(0xFFB07CF0)));
        }
        int textX = pictureX + size + 7;
        int right = x + width - 6;
        Component name = this.name.isBlank() ? Component.translatable("selectServer.defaultName") : Component.literal(this.name);
        ConfigStyle.text(graphics, this.font, name.copy().withStyle(ChatFormatting.BOLD), textX, pictureY + 1, right - textX, ConfigStyle.colors().text());
        ConfigStyle.text(graphics, this.font, Component.literal(this.address), textX, pictureY + 12, right - textX, ConfigStyle.colors().textMuted());
        // How it answers: said in one tag in the strip, and with its version, message and players beside the boxes.
        ServerData.State state = server != null ? this.state(server) : null;
        boolean invalid = !this.address.isBlank() && !ServerAddress.isValidAddress(this.address);
        Component status = server != null ? GlazedServersScreen.stateName(server)
                : invalid ? Component.translatableWithFallback("glazedmenu.server.invalid", "Not a valid address")
                : this.address.isBlank() ? Component.translatableWithFallback("glazedmenu.server.enter", "Type the server's address")
                : Component.translatable("multiplayer.status.pinging");
        int statusColor = server != null ? GlazedServersScreen.stateColor(server) : invalid ? ConfigStyle.colors().error() : ConfigStyle.colors().textDim();
        if (!this.beside) {
            int tagWidth = this.font.width(status) + 8;
            ConfigStyle.badge(graphics, this.font, status, right - tagWidth, y + (height - 11) / 2, statusColor);
            if (server != null) GlazedServersScreen.extractSignal(graphics, server, right - tagWidth - 20, y + (height - 10) / 2, 0);
            return;
        }
        if (server != null) GlazedServersScreen.extractSignal(graphics, server, textX, pictureY + 23, 0);
        boolean answered = state == ServerData.State.SUCCESSFUL || state == ServerData.State.INCOMPATIBLE;
        if (answered) {
            ConfigStyle.text(graphics, this.font, Component.translatable("multiplayer.status.ping", server.ping), textX + 19, pictureY + 24, right - textX - 19, ConfigStyle.colors().textMuted());
        }
        int lineY = y + 7 + size + 7;
        int tagX = x + 6 + ConfigStyle.badge(graphics, this.font, status, x + 6, lineY, statusColor) + 4;
        if (answered && tagX + this.font.width(server.version) + 8 <= right) {
            ConfigStyle.badge(graphics, this.font, server.version, tagX, lineY, state == ServerData.State.INCOMPATIBLE ? ConfigStyle.colors().error() : ConfigStyle.colors().textDim());
        }
        lineY += 16;
        if (server == null) return;
        int bottom = y + height - 5;
        // What the server says of itself, then how many play there, in the room that's left.
        List<FormattedCharSequence> message = this.font.split(server.motd, width - 12);
        for (int i = 0; i < Math.min(2, message.size()) && lineY + 10 <= bottom - (answered ? 11 : 0); i++) {
            graphics.text(this.font, message.get(i), x + 6, lineY, ConfigStyle.colors().textDim(), false);
            lineY += 10;
        }
        if (answered && lineY + 10 <= bottom) {
            Component players = Component.translatableWithFallback("glazedmenu.servers.detail.players", "Players");
            ConfigStyle.text(graphics, this.font, players, x + 6, bottom - 9, width - 12, ConfigStyle.colors().textMuted());
            int statusWidth = Math.min(this.font.width(server.status), width - 12 - this.font.width(players) - 6);
            ConfigStyle.text(graphics, this.font, server.status, right - statusWidth, bottom - 9, statusWidth, ConfigStyle.colors().text());
        }
    }

    // One of the three things done about a server's resource packs: its name over what it means, lit in its color while
    // it's the choice.
    private final class PackCard extends FlatButton {
        private final ServerData.ServerPackStatus status;
        private final int color;

        PackCard(int width, int height, ServerData.ServerPackStatus status, int color) {
            super(width, height, status.getName(), button -> GlazedEditServerScreen.this.setPacks(status));
            this.status = status;
            this.color = color;
        }

        private Component note() {
            return switch (this.status) {
                case PROMPT -> Component.translatableWithFallback("glazedmenu.server.packs.prompt", "Ask each time");
                case ENABLED -> Component.translatableWithFallback("glazedmenu.server.packs.enabled", "Always accept");
                case DISABLED -> Component.translatableWithFallback("glazedmenu.server.packs.disabled", "Never accept");
            };
        }

        @Override
        protected void extractButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Font font = GlazedEditServerScreen.this.font;
            boolean chosen = GlazedEditServerScreen.this.packs() == this.status;
            boolean lit = this.isHoveredOrFocused();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.mix(ConfigStyle.colors().button(), this.color, chosen ? 0.28F : lit ? 0.14F : 0.04F));
            ConfigStyle.outline(graphics, x, y, width, height,
                    chosen ? ConfigStyle.withAlpha(this.color, 0xE0) : lit ? ConfigStyle.withAlpha(this.color, 0x90) : ConfigStyle.colors().panelBorder());
            graphics.fill(x + 2, y + 1, x + width - 2, y + 3, ConfigStyle.withAlpha(this.color, chosen ? 0xFF : 0x60));
            ConfigStyle.text(graphics, font, this.getMessage().copy().withStyle(ChatFormatting.BOLD), x + 6, y + 7, width - 10,
                    chosen ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            ConfigStyle.text(graphics, font, this.note(), x + 6, y + 18, width - 10, ConfigStyle.colors().textMuted());
        }
    }
}
