package net.ixdarklord.glazedmenu.internal.game;

import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.mixin.AbstractWidgetAccessor;
import net.ixdarklord.glazedmenu.internal.mixin.FriendsOverlayScreenAccessor;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.LoadingDotsWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.PlayerFaceWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.PrivacyConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.CommonLinks;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's friends and the requests to be friends, as a popup in place of the game's friends list (the game's since
 * 26.2): the player's own profile name to hand out, tabs for the friends and the requests, a box to ask someone to be a
 * friend, and a row for each friend (their face, their name and whether they're playing) or request, with what the game
 * lets be done about it.
 * <p>
 * It's a face on the game's own screen, which it keeps: the game's lists decide who is listed and say what state the
 * service is in, its tabs are chosen and its box typed into, and asking, accepting, declining, canceling and removing
 * press the buttons of the game's own rows, so each is done exactly as the game does it (a friend is removed after the
 * game's own question).
 */
public final class GlazedFriendsScreen extends StyledPopup {
    private static final String VANILLA = "net.minecraft.client.gui.screens.friends.FriendsOverlayScreen";
    private static final int WIDTH = 320;
    private static final int ROW = 30;
    private static final int HEADING = 14;
    private static final int TAB_HEIGHT = 16;
    // The face of the game's screen now open, so that screen coming back (after its question) comes back as its face.
    private static WeakReference<GlazedFriendsScreen> current = new WeakReference<>(null);

    private final FriendsOverlayScreen game;
    // The game tells its own screen of a change only while that screen is the one open; here it's told through this.
    private final Runnable listUpdated = this::onListUpdated;
    private final List<TabBox> tabs = new ArrayList<>();
    private int tab;
    private String typed = "";
    private int scroll;
    private int ticks;
    private int listTop, listBottom;
    private int profileX, profileWidth;
    private @Nullable StyledEditBox nameBox;
    private @Nullable FlatButton send;

    // A tab where it's drawn.
    private record TabBox(int index, int x, int width) {}

    // A row of the game's own list: its face, its name, how the friend stands (a friend's row has that), and its buttons.
    private record Row(PlayerFaceWidget face, Component name, @Nullable Component status, List<Button> buttons) {
        boolean incoming() {
            return this.buttons.stream().anyMatch(button -> "gui.friends.accept".equals(key(button)));
        }

        boolean outgoing() {
            return this.buttons.stream().anyMatch(button -> "gui.friends.cancel_request".equals(key(button)));
        }
    }

    // What the list shows, top to bottom: a heading or a row.
    private record Item(@Nullable Component heading, @Nullable Row row) {
        int height() {
            return this.row != null ? ROW : HEADING;
        }
    }

    private GlazedFriendsScreen(@Nullable Screen parent, FriendsOverlayScreen game) {
        super(parent, game.getTitle(), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.game = game;
    }

    /** The screen to open instead: this one for the game's own friends list, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof FriendsOverlayScreen game) || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceFriends()) return screen;
        GlazedFriendsScreen face = current.get();
        if (face != null && face.game == game) return face;
        // Over the screen the game's own would be drawn over (the title screen, the pause menu).
        face = new GlazedFriendsScreen(((FriendsOverlayScreenAccessor) game).glazedmenu$backgroundScreen(), game);
        current = new WeakReference<>(face);
        return face;
    }

    // --- The game's own screen ---

    private static @Nullable String key(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    private static void press(AbstractWidget widget) {
        widget.mouseClicked(new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
    }

    // Every widget of the game's screen, those inside its lists and rows too, in its order.
    private List<AbstractWidget> gameWidgets() {
        List<AbstractWidget> found = new ArrayList<>();
        collect(this.game.children(), found, 0);
        return found;
    }

    private static void collect(List<? extends GuiEventListener> from, List<AbstractWidget> into, int depth) {
        for (GuiEventListener child : from) {
            if (child instanceof AbstractWidget widget) into.add(widget);
            if (depth < 6 && child instanceof ContainerEventHandler container) collect(container.children(), into, depth + 1);
        }
    }

    private <T> @Nullable T gameWidget(Class<T> type) {
        for (AbstractWidget widget : this.gameWidgets()) {
            if (type.isInstance(widget)) return type.cast(widget);
        }
        return null;
    }

    // The game's box for a profile name and its button to ask (they're its own only while its Friends tab is the one shown).
    private @Nullable EditBox gameBox() {
        return this.gameWidget(EditBox.class);
    }

    private @Nullable Button gameSend() {
        for (AbstractWidget widget : this.gameWidgets()) {
            if (widget instanceof Button button && "gui.friends.send_request".equals(key(button))) return button;
        }
        return null;
    }

    // The rows of the game's list now shown: each of its entries holds a face.
    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (AbstractWidget widget : this.gameWidgets()) {
            if (!(widget instanceof ContainerEventHandler entry)) continue;
            PlayerFaceWidget face = null;
            Component name = null;
            Component status = null;
            List<Button> buttons = new ArrayList<>();
            for (GuiEventListener child : entry.children()) {
                if (child instanceof PlayerFaceWidget found) face = found;
                else if (child instanceof StringWidget text && name == null) name = text.getMessage();
                else if (child instanceof StringWidget text) status = text.getMessage();
                else if (child instanceof Button button) buttons.add(button);
            }
            if (face != null && name != null) rows.add(new Row(face, name, status, buttons));
        }
        return rows;
    }

    // The rows under the game's own headings, where it has them (the requests received, the requests sent).
    private List<Item> items() {
        List<Item> items = new ArrayList<>();
        boolean received = false;
        boolean sent = false;
        for (Row row : this.rows()) {
            if (row.incoming() && !received) {
                received = true;
                items.add(new Item(Component.translatable("gui.friends.pending.received"), null));
            } else if (row.outgoing() && !sent) {
                sent = true;
                items.add(new Item(Component.translatable("gui.friends.pending.sent"), null));
            }
            items.add(new Item(null, row));
        }
        return items;
    }

    // What the game says in place of a list: that it's loading, that there's nothing, or what went wrong.
    private @Nullable Component message() {
        for (AbstractWidget widget : this.gameWidgets()) {
            if (widget instanceof LoadingDotsWidget) return widget.getMessage();
            if (widget instanceof MultiLineTextWidget text) {
                Component said = text.getMessage();
                // The link the game ends its empty list with is at this popup's foot: the list says the rest.
                if (said.getContents() instanceof TranslatableContents contents && contents.getKey().equals("gui.friends.empty_state")) {
                    return Component.literal(said.getString().split("\n\n")[0]);
                }
                return said;
            }
        }
        return null;
    }

    private void chooseTab(int index) {
        this.tab = index;
        this.scroll = 0;
        TabNavigationBar bar = this.gameWidget(TabNavigationBar.class);
        if (bar != null) bar.selectTab(index, false);
        // Whatever was typed stays in the game's box, which the game makes anew with its tab.
        EditBox box = this.gameBox();
        if (box != null && !box.getValue().equals(this.typed)) box.setValue(this.typed);
        this.follow();
    }

    private void onListUpdated() {
        if (this.minecraft.gui.screen() == this && this.minecraft.getPlayerSocialManager().isFriendListEnabled()) this.game.refreshLists();
    }

    private void close() {
        this.game.onClose();
    }

    private void copyName() {
        PlainTextButton own = this.gameWidget(PlainTextButton.class);
        if (own != null) press(own);
        else this.minecraft.keyboardHandler.setClipboard(this.minecraft.getUser().getName());
    }

    // --- Layout ---

    @Override
    protected void initPopup() {
        // The game's screen is laid out too: its lists, its tabs and its box are what's read, chosen and typed into here.
        this.game.init(this.width, this.height);
        this.setPanel(WIDTH, Math.min(this.height - 20, 244));
        int left = this.contentLeft();
        int top = this.contentTop();
        int width = this.contentWidth();

        this.tabs.clear();
        int half = (width - 3) / 2;
        this.tabs.add(new TabBox(0, left, half));
        this.tabs.add(new TabBox(1, left + half + 3, width - half - 3));

        int boxY = top + 12 + TAB_HEIGHT + 5;
        Component ask = Component.translatable("gui.friends.send_request");
        int sendWidth = Math.min(this.font.width(ask) + 28, width / 2);
        this.nameBox = this.addRenderableWidget(new StyledEditBox(this.font, width - sendWidth - 4, 18, Component.translatable("gui.friends.enter_nickname")));
        this.nameBox.setMaxLength(32);
        this.nameBox.setPlaceholder(Component.translatable("gui.friends.enter_nickname"));
        this.nameBox.setValue(this.typed);
        this.nameBox.setResponder(text -> {
            this.typed = text;
            EditBox box = this.gameBox();
            if (box != null && !box.getValue().equals(text)) box.setValue(text);
        });
        this.nameBox.setPosition(left, boxY);
        this.send = this.addRenderableWidget(new FlatButton(sendWidth, 18, ask, button -> {
            Button own = this.gameSend();
            if (own != null) press(own);
        }).style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.SEND));
        this.send.setPosition(left + width - sendWidth, boxY);
        // The game's own link has a line of its own over the footer.
        this.listBottom = this.footerTop() - 13;

        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.close()));
        done.setPosition(left + width - 84, this.footerButtonY());
        this.chooseTab(this.tab);
    }

    // The box and the button here are as the game's own are: on while the game's take a name, off while it sends one.
    private void follow() {
        if (this.nameBox == null || this.send == null) return;
        boolean friends = this.tab == 0;
        EditBox box = friends ? this.gameBox() : null;
        Button own = friends ? this.gameSend() : null;
        this.nameBox.visible = friends;
        this.send.visible = friends;
        this.listTop = this.contentTop() + 12 + TAB_HEIGHT + 5 + (friends ? 18 + 5 : 0);
        if (box != null) {
            // The game empties its box once a request is sent.
            if (!box.getValue().equals(this.nameBox.getValue())) this.nameBox.setValue(box.getValue());
            this.nameBox.setEditable(box.active);
            this.nameBox.active = box.active;
        }
        if (own != null) {
            this.send.active = own.active;
            // Why it's off, or what it does, in the game's own words.
            this.send.setTooltip(((AbstractWidgetAccessor) own).glazedmenu$tooltip().get());
        }
    }

    @Override
    public void added() {
        super.added();
        this.minecraft.getPlayerSocialManager().addFriendListUpdateListener(this.listUpdated);
    }

    @Override
    public void removed() {
        this.minecraft.getPlayerSocialManager().removeFriendListUpdateListener(this.listUpdated);
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        // The game's own tick asks how the friends stand; what it's told reaches its rows from here.
        this.game.tick();
        if (++this.ticks % 20 == 0) this.game.applyPresenceUpdate();
        this.follow();
    }

    @Override
    public void onClose() {
        this.close();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = this.nameBox != null && this.nameBox.isFocused();
        if (typing && event.isConfirmation() && this.send != null && this.send.active) {
            Button own = this.gameSend();
            if (own != null) press(own);
            return true;
        }
        // The key that opens it closes it too, as on the game's screen (not while a name is being typed).
        if (!typing && this.minecraft.options.keyFriends.matches(event)) {
            this.close();
            return true;
        }
        return super.keyPressed(event);
    }

    private int contentHeight(List<Item> items) {
        int height = 0;
        for (Item item : items) height += item.height();
        return height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int most = Math.max(0, this.contentHeight(this.items()) - (this.listBottom - this.listTop));
        if (scrollY != 0 && most > 0) {
            this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY) * 15, 0, most);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Whether a row's button is the one that takes a friend off the list: an icon here, the others say what they do.
    private static boolean removes(Button button) {
        return "gui.friends.unfriend".equals(key(button));
    }

    private int buttonWidth(Button button) {
        return removes(button) ? 18 : this.font.width(button.getMessage().getString()) + 12;
    }

    // Where a row's buttons are, from its right end, in the game's order.
    private int[] buttonLefts(Row row) {
        int[] lefts = new int[row.buttons.size()];
        int x = this.contentLeft() + this.contentWidth() - 5;
        for (int i = lefts.length - 1; i >= 0; i--) {
            x -= this.buttonWidth(row.buttons.get(i));
            lefts[i] = x;
            x -= 3;
        }
        return lefts;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int top = this.contentTop();
            if (y >= top - 1 && y < top + 10 && x >= this.profileX && x < this.profileX + this.profileWidth) {
                this.copyName();
                return true;
            }
            int tabsY = top + 12;
            if (y >= tabsY && y < tabsY + TAB_HEIGHT) {
                for (TabBox box : this.tabs) {
                    if (x >= box.x && x < box.x + box.width) {
                        this.chooseTab(box.index);
                        return true;
                    }
                }
            }
            if (y >= this.listTop && y < this.listBottom && x >= this.contentLeft() && x < this.contentLeft() + this.contentWidth()) {
                int itemY = this.listTop - this.scroll;
                for (Item item : this.items()) {
                    Row row = item.row;
                    if (row != null && y >= itemY + 6 && y < itemY + 6 + 16) {
                        int[] lefts = this.buttonLefts(row);
                        for (int i = 0; i < lefts.length; i++) {
                            Button button = row.buttons.get(i);
                            if (x >= lefts[i] && x < lefts[i] + this.buttonWidth(button) && button.active) {
                                press(button);
                                return true;
                            }
                        }
                    }
                    itemY += item.height();
                }
                return true;
            }
            // The game's own link, about staying safe online.
            if (y >= this.listBottom + 2 && y < this.listBottom + 13 && x >= this.contentLeft() && x < this.contentLeft() + this.contentWidth()) {
                PrivacyConfirmLinkScreen.confirmLinkNow(this, CommonLinks.PRIVACY_AND_ONLINE_SETTINGS);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.PERSON;
    }

    // --- Drawing ---

    private Component tabLabel(int index) {
        PlayerSocialManager social = this.minecraft.getPlayerSocialManager();
        boolean known = social.getFriendListState() == RemoteFriendListUpdateHandler.State.SUCCESS;
        if (index == 0) {
            Component friends = Component.translatable("gui.friends.tab_friends");
            return known ? friends.copy().append(" (" + social.getFriends().size() + ")") : friends;
        }
        // The game's own tab says how many ask.
        int seen = 0;
        for (AbstractWidget widget : this.gameWidgets()) {
            if (widget instanceof TabButton && seen++ == 1) return widget.getMessage();
        }
        return Component.translatable("gui.friends.requests_count", known ? social.getIncomingRequests().size() : 0);
    }

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = this.contentLeft();
        int width = this.contentWidth();
        int top = this.contentTop();

        // The player's own profile name, to hand out: a click copies it.
        Component label = Component.translatable("gui.friends.my_profile_name");
        String name = this.minecraft.getUser().getName();
        ConfigStyle.text(graphics, this.font, label, left, top, width, ConfigStyle.colors().textMuted());
        this.profileX = left + this.font.width(label);
        this.profileWidth = this.font.width(name) + 14;
        boolean overName = mouseY >= top - 1 && mouseY < top + 10 && mouseX >= this.profileX && mouseX < this.profileX + this.profileWidth;
        graphics.text(this.font, name, this.profileX, top, overName ? ConfigStyle.accent() : ConfigStyle.colors().text(), false);
        ConfigIcons.COPY.draw(graphics, this.profileX + this.font.width(name) + 4, top - 1, 9, overName ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        if (overName) graphics.setTooltipForNextFrame(this.font, Component.translatable("gui.friends.copy_to_clipboard"), mouseX, mouseY);

        int tabsY = top + 12;
        for (TabBox box : this.tabs) {
            boolean chosen = this.tab == box.index;
            boolean hovered = mouseX >= box.x && mouseX < box.x + box.width && mouseY >= tabsY && mouseY < tabsY + TAB_HEIGHT;
            if (chosen) {
                ConfigStyle.rect(graphics, box.x, tabsY, box.width, TAB_HEIGHT, ConfigStyle.mix(ConfigStyle.accent(), 0xFF140C3A, 0.5F));
                GlazedBrand.fill(graphics, box.x + 1, tabsY + 1, box.width - 2, TAB_HEIGHT - 2, 0xFF, 0.24F);
            } else {
                ConfigStyle.rect(graphics, box.x, tabsY, box.width, TAB_HEIGHT, hovered ? ConfigStyle.colors().buttonHover() : ConfigStyle.colors().button());
            }
            ConfigStyle.outline(graphics, box.x, tabsY, box.width, TAB_HEIGHT, chosen ? ConfigStyle.mix(ConfigStyle.accent(), 0xFF000000, 0.4F) : ConfigStyle.colors().panelBorder());
            ConfigStyle.centeredText(graphics, this.font, this.tabLabel(box.index), box.x + box.width / 2, tabsY + 4, box.width - 8,
                    chosen ? 0xFFFFFFFF : hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
        }

        List<Item> items = this.items();
        int shown = this.listBottom - this.listTop;
        int all = this.contentHeight(items);
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, all - shown));
        if (items.isEmpty()) {
            Component said = this.message();
            if (said != null) {
                List<FormattedCharSequence> lines = this.font.split(said, width - 24);
                int y = this.listTop + Math.max(4, (shown - lines.size() * 10) / 2);
                for (FormattedCharSequence line : lines) {
                    if (y + 9 <= this.listBottom) graphics.text(this.font, line, left + (width - this.font.width(line)) / 2, y, ConfigStyle.colors().textMuted(), false);
                    y += 10;
                }
            }
        }
        graphics.enableScissor(left - 1, this.listTop, left + width + 1, this.listBottom);
        boolean inList = mouseY >= this.listTop && mouseY < this.listBottom;
        int y = this.listTop - this.scroll;
        for (Item item : items) {
            if (y + item.height() > this.listTop && y < this.listBottom) {
                if (item.row != null) this.extractRow(graphics, item.row, left, y, width, inList ? mouseX : -1, inList ? mouseY : -1, a);
                else ConfigStyle.text(graphics, this.font, item.heading.copy().withStyle(ChatFormatting.BOLD), left + 1, y + 4, width, ConfigStyle.colors().textDim());
            }
            y += item.height();
        }
        graphics.disableScissor();
        if (all > shown) {
            int track = shown - 4;
            int thumb = Math.max(10, track * shown / all);
            int thumbY = this.listTop + 2 + (track - thumb) * this.scroll / Math.max(1, all - shown);
            graphics.fill(left + width + 3, thumbY, left + width + 5, thumbY + thumb, ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0xB0));
        }

        // The game's own link, under the list.
        boolean overLink = mouseY >= this.listBottom + 2 && mouseY < this.listBottom + 13 && mouseX >= left && mouseX < left + width;
        Component link = Component.translatable("gui.friends.empty_state.link").withStyle(ChatFormatting.UNDERLINE);
        ConfigStyle.centeredText(graphics, this.font, Component.translatable("gui.friends.manage_account_footer", link), left + width / 2, this.listBottom + 4, width,
                overLink ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
    }

    private void extractRow(GuiGraphicsExtractor graphics, Row row, int left, int y, int width, int mouseX, int mouseY, float a) {
        ConfigStyle.rect(graphics, left, y, width, ROW - 2, ConfigStyle.withAlpha(ConfigStyle.colors().field(), 0x90));
        ConfigStyle.outline(graphics, left, y, width, ROW - 2, ConfigStyle.withAlpha(ConfigStyle.colors().panelBorder(), 0xB0));
        // The game's own face of the player, where this row has it.
        int faceX = left + 3;
        int faceY = y + (ROW - 2 - row.face.getHeight()) / 2;
        row.face.setPosition(faceX, faceY);
        row.face.extractRenderState(graphics, -1, -1, a);
        ConfigStyle.pictureFrame(graphics, faceX, faceY, row.face.getWidth(), row.face.getHeight());

        int[] lefts = this.buttonLefts(row);
        int textX = faceX + row.face.getWidth() + 6;
        int textRight = (lefts.length == 0 ? left + width - 4 : lefts[0]) - 4;
        Component name = row.name.copy().withStyle(ChatFormatting.BOLD);
        if (row.status != null) {
            // Playing or not, in the game's wording: off the game's own gray, on its green.
            boolean offline = row.status.getContents() instanceof TranslatableContents contents && contents.getKey().equals("gui.friends.presence.status.offline");
            ConfigStyle.text(graphics, this.font, name, textX, y + 5, textRight - textX, ConfigStyle.colors().text());
            ConfigStyle.text(graphics, this.font, Component.literal(row.status.getString()), textX, y + 16, textRight - textX,
                    offline ? ConfigStyle.colors().textMuted() : ConfigStyle.colors().success());
        } else {
            ConfigStyle.text(graphics, this.font, name, textX, y + 10, textRight - textX, ConfigStyle.colors().text());
        }

        for (int i = 0; i < lefts.length; i++) {
            Button button = row.buttons.get(i);
            int buttonWidth = this.buttonWidth(button);
            int buttonY = y + 6;
            boolean hovered = mouseX >= lefts[i] && mouseX < lefts[i] + buttonWidth && mouseY >= buttonY && mouseY < buttonY + 16;
            boolean lit = hovered && button.active;
            String key = key(button);
            boolean accepts = "gui.friends.accept".equals(key);
            int color = !button.active ? ConfigStyle.colors().textMuted() : accepts ? ConfigStyle.colors().success() : ConfigStyle.colors().error();
            if (removes(button)) {
                if (lit) ConfigStyle.rect(graphics, lefts[i], buttonY, buttonWidth, 16, ConfigStyle.withAlpha(ConfigStyle.colors().error(), 0x40));
                ConfigIcons.TRASH.draw(graphics, lefts[i] + 4, buttonY + 3, 10, lit ? ConfigStyle.colors().error() : button.active ? ConfigStyle.colors().textDim() : ConfigStyle.colors().textMuted());
                if (hovered) graphics.setTooltipForNextFrame(this.font, button.getMessage(), mouseX, mouseY);
            } else {
                ConfigStyle.rect(graphics, lefts[i], buttonY, buttonWidth, 16, ConfigStyle.mix(ConfigStyle.colors().button(), color, lit ? 0.34F : accepts ? 0.18F : 0.06F));
                ConfigStyle.outline(graphics, lefts[i], buttonY, buttonWidth, 16, ConfigStyle.withAlpha(color, lit ? 0xE0 : 0x70));
                ConfigStyle.centeredText(graphics, this.font, Component.literal(button.getMessage().getString()), lefts[i] + buttonWidth / 2, buttonY + 4, buttonWidth - 4,
                        button.active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted());
            }
        }
    }
}
