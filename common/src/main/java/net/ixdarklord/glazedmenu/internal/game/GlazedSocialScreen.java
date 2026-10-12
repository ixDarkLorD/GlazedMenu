package net.ixdarklord.glazedmenu.internal.game;

import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.ixdarklord.glazedmenu.internal.mixin.PlayerEntryAccessor;
import net.ixdarklord.glazedmenu.internal.compat.Compat;
import net.minecraft.util.Mth;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.mixin.ChatComponentAccessor;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.social.PlayerEntry;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.SocialInteractionsPlayerList;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.ixdarklord.glazedmenu.internal.compat.KeyEvent;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The players of the server and what's done about them, as a popup in place of the game's Social Interactions screen:
 * tabs for everyone, the hidden and the blocked, a search, and a row for each player with their face, how they stand
 * (hidden, blocked, gone) or what they said last, and buttons to write to them, to hide or show their messages, and to
 * report them.
 * <p>
 * It's a face on the game's own screen, which it keeps: the game's list decides who is listed, its tabs and its search
 * box are pressed and typed into, and hiding and reporting press the buttons of the game's own rows, so each is done
 * exactly as the game does it (and a report comes back here).
 */
public final class GlazedSocialScreen extends StyledPopup {
    private static final int WIDTH = 300;
    private static final int ROW = 26;
    private static final int FACE = 18;
    private static final int TAB_HEIGHT = 16;
    private static final String[] TAB_KEYS = {"gui.socialInteractions.tab_all", "gui.socialInteractions.tab_hidden", "gui.socialInteractions.tab_blocked"};
    private static final String[] EMPTY_KEYS = {"gui.socialInteractions.search_empty", "gui.socialInteractions.empty_hidden", "gui.socialInteractions.empty_blocked"};
    // The face of the game's screen now open, so that screen coming back (after a report) comes back as its face.
    private static WeakReference<GlazedSocialScreen> current = new WeakReference<>(null);

    private final SocialInteractionsScreen game;
    private int tab;
    private String search = "";
    private int scroll;
    private int known = -1;
    private int listTop, listBottom;
    private @Nullable StyledEditBox searchBox;

    // A tab where it's drawn.
    private record TabBox(int index, int x, int width) {}

    private final List<TabBox> tabs = new ArrayList<>();

    private GlazedSocialScreen(@Nullable Screen parent, SocialInteractionsScreen game) {
        super(parent, game.getTitle(), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.game = game;
    }

    /** The screen to open instead: this one for the game's own Social Interactions screen, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof SocialInteractionsScreen game) || screen.getClass() != SocialInteractionsScreen.class || !GlazedSettings.replaceSocial()) return screen;
        GlazedSocialScreen face = current.get();
        if (face != null && face.game == game) return face;
        Screen open = Minecraft.getInstance().screen;
        face = new GlazedSocialScreen(open instanceof StyledScreen ? open : null, game);
        current = new WeakReference<>(face);
        return face;
    }

    // --- The game's own screen ---

    private static @Nullable String key(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    private @Nullable AbstractWidget gameButton(String name) {
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof AbstractWidget widget && !(child instanceof EditBox) && name.equals(key(widget))) return widget;
        }
        return null;
    }

    private static void press(AbstractWidget widget) {
        widget.mouseClicked(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, 0);
    }

    // The players the game's own list holds now (for its tab and its search), in its order.
    private List<PlayerEntry> players() {
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof SocialInteractionsPlayerList list) return list.children();
        }
        return List.of();
    }

    // The game's list is made anew for its tab: as a tab is chosen, and as players come and go.
    private void refresh() {
        AbstractWidget button = this.gameButton(TAB_KEYS[this.tab]);
        if (button != null) press(button);
        this.known = this.onlineCount();
    }

    private int onlineCount() {
        // (Asked before the screen is given the game, the first time it opens on 1.20.1.)
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? 0 : connection.getOnlinePlayers().size();
    }

    private void close() {
        this.game.onClose();
    }

    // --- Layout ---

    @Override
    protected void initPopup() {
        // The game's screen is laid out too: its list, its tabs and its search box are what's read and pressed here.
        Compat.init(this.game, this.width, this.height);
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof EditBox box && !box.getValue().equals(this.search)) box.setValue(this.search);
        }
        this.setPanel(WIDTH, Math.min(this.height - 20, 236));
        int left = this.contentLeft();
        int top = this.contentTop();
        int width = this.contentWidth();

        this.tabs.clear();
        int x = left;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int tabWidth = this.font.width(Component.translatable(TAB_KEYS[i])) + 14;
            this.tabs.add(new TabBox(i, x, tabWidth));
            x += tabWidth + 3;
        }
        int searchWidth = Math.max(60, left + width - x - 4);
        this.searchBox = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, TAB_HEIGHT, Component.translatable("gui.socialInteractions.search_hint")));
        this.searchBox.setMaxLength(16);
        this.searchBox.setValue(this.search);
        this.searchBox.setPlaceholder(Component.translatable("gui.socialInteractions.search_hint"));
        this.searchBox.setResponder(text -> {
            this.search = text;
            this.scroll = 0;
            for (GuiEventListener child : this.game.children()) {
                if (child instanceof EditBox box && !box.getValue().equals(text)) box.setValue(text);
            }
        });
        this.searchBox.setPosition(left + width - searchWidth, top);
        this.setInitialFocus(this.searchBox);
        this.listTop = top + TAB_HEIGHT + 6;
        this.listBottom = this.footerTop() - 2;

        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.close()));
        done.setPosition(left + width - 84, this.footerButtonY());
        this.refresh();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.onlineCount() != this.known) this.refresh();
    }

    @Override
    public void onClose() {
        this.close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // The key that opens it closes it too, as on the game's screen (not while a name is being typed).
    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((this.searchBox == null || !this.searchBox.isFocused()) && this.minecraft.options.keySocialInteractions.matches(event.key(), event.scancode())) {
            this.close();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int most = Math.max(0, this.players().size() - (this.listBottom - this.listTop) / ROW);
        if (scrollY != 0 && most > 0) {
            this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, most);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // The buttons of a row, from its right: report, hide or show, write to.
    private int buttonX(int index) {
        return this.contentLeft() + this.contentWidth() - 4 - 18 * (index + 1);
    }

    private boolean canWrite(PlayerEntry player) {
        return !player.isRemoved() && this.minecraft.player != null && !player.getPlayerId().equals(this.minecraft.player.getUUID());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        if (event.button() == 0) {
            int top = this.contentTop();
            if (y >= top && y < top + TAB_HEIGHT) {
                for (TabBox box : this.tabs) {
                    if (x >= box.x && x < box.x + box.width) {
                        this.tab = box.index;
                        this.scroll = 0;
                        this.refresh();
                        return true;
                    }
                }
            }
            if (y >= this.listTop && y < this.listBottom && x >= this.contentLeft() && x < this.contentLeft() + this.contentWidth()) {
                List<PlayerEntry> players = this.players();
                int index = this.scroll + ((int) y - this.listTop) / ROW;
                int rowY = this.listTop + (index - this.scroll) * ROW;
                if (index >= 0 && index < players.size() && y >= rowY + 4 && y < rowY + 20) {
                    PlayerEntry player = players.get(index);
                    List<? extends GuiEventListener> own = player.children();
                    // The game's own buttons of the row: hiding or showing first, then reporting; none for the player.
                    if (x >= this.buttonX(0) && x < this.buttonX(0) + 16 && own.size() > 1 && own.get(1) instanceof AbstractWidget report && report.active) {
                        press(report);
                        return true;
                    }
                    if (x >= this.buttonX(1) && x < this.buttonX(1) + 16 && !own.isEmpty() && own.get(0) instanceof AbstractWidget toggle) {
                        press(toggle);
                        return true;
                    }
                    if (x >= this.buttonX(2) && x < this.buttonX(2) + 16 && this.canWrite(player)) {
                        this.minecraft.setScreen(new ChatScreen("/msg " + player.getPlayerName() + " "));
                        return true;
                    }
                }
                return true;
            }
            // On the Blocked tab, what blocking is: the game's own link.
            if (this.tab == 2 && y >= this.footerButtonY() && y < this.footerButtonY() + 20 && x >= this.contentLeft() && x < this.contentLeft() + this.contentWidth() - 90) {
                AbstractWidget hint = this.gameButton("gui.socialInteractions.blocking_hint");
                if (hint != null) press(hint);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.PERSON;
    }

    // The server (or the world) and how many are on it.
    @Override
    protected @Nullable Component titleNote() {
        ServerData server = this.minecraft.getCurrentServer();
        String name = this.minecraft.isLocalServer() && this.minecraft.getSingleplayerServer() != null ? this.minecraft.getSingleplayerServer().getMotd()
                : server != null ? server.name : "";
        int count = this.onlineCount();
        return Component.translatable(count > 1 ? "gui.socialInteractions.server_label.multiple" : "gui.socialInteractions.server_label.single", name, count);
    }

    // --- Drawing ---

    // What a player said last, of the messages the chat still holds.
    private @Nullable String lastSaid(UUID id) {
        for (GuiMessage message : ((ChatComponentAccessor) this.minecraft.gui.getChat()).glazedmenu$allMessages()) {
            if (id.equals(ChatLink.senderOf(message))) return message.content().getString();
        }
        return null;
    }

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = this.contentLeft();
        int width = this.contentWidth();
        int top = this.contentTop();
        for (TabBox box : this.tabs) {
            boolean chosen = this.tab == box.index;
            boolean hovered = mouseX >= box.x && mouseX < box.x + box.width && mouseY >= top && mouseY < top + TAB_HEIGHT;
            if (chosen) {
                ConfigStyle.rect(graphics, box.x, top, box.width, TAB_HEIGHT, ConfigStyle.mix(ConfigStyle.accent(), 0xFF140C3A, 0.5F));
                GlazedBrand.fill(graphics, box.x + 1, top + 1, box.width - 2, TAB_HEIGHT - 2, 0xFF, 0.24F);
            } else {
                ConfigStyle.rect(graphics, box.x, top, box.width, TAB_HEIGHT, hovered ? ConfigStyle.colors().buttonHover() : ConfigStyle.colors().button());
            }
            ConfigStyle.outline(graphics, box.x, top, box.width, TAB_HEIGHT, chosen ? ConfigStyle.mix(ConfigStyle.accent(), 0xFF000000, 0.4F) : ConfigStyle.colors().panelBorder());
            graphics.text(this.font, Component.translatable(TAB_KEYS[box.index]), box.x + 7, top + 4, chosen ? 0xFFFFFFFF : hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim(), false);
        }

        List<PlayerEntry> players = this.players();
        int shown = (this.listBottom - this.listTop) / ROW;
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, players.size() - shown));
        if (players.isEmpty()) {
            String empty = !this.search.isEmpty() ? EMPTY_KEYS[0] : this.tab == 0 ? null : EMPTY_KEYS[this.tab];
            if (empty != null) {
                ConfigStyle.centeredText(graphics, this.font, Component.translatable(empty), left + width / 2, (this.listTop + this.listBottom) / 2 - 4, width - 8, ConfigStyle.colors().textMuted());
            }
        }
        PlayerSocialManager social = this.minecraft.getPlayerSocialManager();
        ClientPacketListener connection = this.minecraft.getConnection();
        for (int i = 0; i < shown && this.scroll + i < players.size(); i++) {
            PlayerEntry player = players.get(this.scroll + i);
            int y = this.listTop + i * ROW;
            UUID id = player.getPlayerId();
            boolean hidden = social.isHidden(id);
            boolean blocked = social.isBlocked(id);
            boolean gone = player.isRemoved();
            ConfigStyle.rect(graphics, left, y, width, ROW - 2, ConfigStyle.withAlpha(ConfigStyle.colors().field(), gone ? 0x50 : 0x90));
            ConfigStyle.outline(graphics, left, y, width, ROW - 2, ConfigStyle.withAlpha(ConfigStyle.colors().panelBorder(), 0xB0));
            int faceY = y + (ROW - 2 - FACE) / 2;
            // 1.20.1 draws a face untinted: one that's gone is dimmed through the drawing's own color.
            if (gone) graphics.raw().setColor(0.5F, 0.5F, 0.5F, 1);
            PlayerFaceRenderer.draw(graphics.raw(), ((PlayerEntryAccessor) player).glazedmenu$skinGetter().get(), left + 4, faceY, FACE);
            if (gone) graphics.raw().setColor(1, 1, 1, 1);
            ConfigStyle.pictureFrame(graphics, left + 4, faceY, FACE, FACE);

            List<? extends GuiEventListener> own = player.children();
            int textX = left + 4 + FACE + 6;
            int textRight = (own.isEmpty() ? left + width - 4 : this.buttonX(this.canWrite(player) ? 2 : 1)) - 4;
            // How the player stands, or what they said last; and how fast their connection is, beside the name.
            String status = blocked && gone ? "gui.socialInteractions.status_blocked_offline" : hidden && gone ? "gui.socialInteractions.status_hidden_offline"
                    : blocked ? "gui.socialInteractions.status_blocked" : hidden ? "gui.socialInteractions.status_hidden" : gone ? "gui.socialInteractions.status_offline" : null;
            String said = status == null ? this.lastSaid(id) : null;
            Component name = Component.literal(player.getPlayerName()).withStyle(ChatFormatting.BOLD);
            PlayerInfo info = connection == null || gone ? null : connection.getPlayerInfo(id);
            if (info != null && info.getLatency() > 0) {
                name = Component.empty().append(name).append(Component.literal("  " + info.getLatency() + " ms").withStyle(ChatFormatting.RESET));
            }
            boolean two = status != null || said != null;
            ConfigStyle.text(graphics, this.font, name, textX, y + (two ? 4 : 8), textRight - textX, gone ? ConfigStyle.colors().textDim() : ConfigStyle.colors().text());
            if (status != null) {
                ConfigStyle.text(graphics, this.font, Component.translatable(status), textX, y + 14, textRight - textX, hidden || blocked ? 0xFFE0B040 : ConfigStyle.colors().textMuted());
            } else if (said != null) {
                ConfigStyle.text(graphics, this.font, Component.literal(said), textX, y + 14, textRight - textX, ConfigStyle.colors().textMuted());
            }

            if (own.isEmpty()) continue;
            boolean reportable = own.size() > 1 && own.get(1) instanceof AbstractWidget report && report.active;
            this.rowButton(graphics, this.buttonX(0), y + 4, ConfigIcons.FLAG, reportable ? ConfigStyle.colors().error() : ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0x80), reportable, mouseX, mouseY,
                    Component.translatable(reportable ? "gui.socialInteractions.tooltip.report" : "gui.socialInteractions.tooltip.report.disabled"));
            this.rowButton(graphics, this.buttonX(1), y + 4, hidden ? ConfigIcons.EYE_OFF : ConfigIcons.EYE, hidden ? 0xFFE0B040 : ConfigStyle.colors().textDim(), true, mouseX, mouseY,
                    Component.translatable(hidden ? "gui.socialInteractions.tooltip.show" : "gui.socialInteractions.tooltip.hide"));
            if (this.canWrite(player)) {
                this.rowButton(graphics, this.buttonX(2), y + 4, ConfigIcons.CHAT, ConfigStyle.colors().textDim(), true, mouseX, mouseY,
                        Component.translatableWithFallback("glazedmenu.social.message", "Send a message"));
            }
        }
        if (players.size() > shown) {
            int track = this.listBottom - this.listTop - 4;
            int thumb = Math.max(10, track * shown / players.size());
            int thumbY = this.listTop + 2 + (track - thumb) * this.scroll / Math.max(1, players.size() - shown);
            graphics.fill(left + width + 3, thumbY, left + width + 5, thumbY + thumb, ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0xB0));
        }
        if (this.tab == 2) {
            boolean hovered = mouseY >= this.footerButtonY() && mouseY < this.footerButtonY() + 20 && mouseX >= left && mouseX < left + width - 90;
            ConfigStyle.text(graphics, this.font, Component.translatable("gui.socialInteractions.blocking_hint").withStyle(ChatFormatting.UNDERLINE), left + 1, this.footerButtonY() + 6,
                    width - 92, hovered ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }
    }

    private void rowButton(GuiGraphicsExtractor graphics, int x, int y, ConfigIcons.Icon icon, int color, boolean active, int mouseX, int mouseY, Component tooltip) {
        boolean hovered = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
        if (hovered && active) ConfigStyle.rect(graphics, x, y, 16, 16, ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x24));
        icon.draw(graphics, x + 3, y + 3, 10, hovered && active ? ConfigStyle.mix(color, 0xFFFFFFFF, 0.3F) : color);
        if (hovered) graphics.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
    }
}
