package net.ixdarklord.glazedmenu.internal.game;

import java.util.function.BiFunction;
import net.ixdarklord.glazedmenu.internal.mixin.EditBoxAccessor;
import net.ixdarklord.glazedmenu.internal.compat.CompatChatScreen;
import net.ixdarklord.glazedmenu.internal.compat.Compat;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.mixin.ChatComponentAccessor;
import net.ixdarklord.glazedmenu.internal.mixin.ChatScreenAccessor;
import net.ixdarklord.glazedmenu.internal.mixin.CommandSuggestionsAccessor;
import net.ixdarklord.glazedmenu.internal.mixin.SuggestionsListAccessor;
import net.ixdarklord.glazedmenu.internal.net.ChatPayloads;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.GuiFade;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.ixdarklord.glazedmenu.internal.compat.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.compat.TextAlignment;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.ixdarklord.glazedmenu.internal.compat.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.ixdarklord.glazedmenu.internal.compat.KeyEvent;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The chat, in place of the game's own screen: every message on a row of glass of its own at the lower left (a
 * player's with their face, one naming the player marked), tabs choosing which are shown, a row of things to do in one
 * click (showing the item held, the player's place, the commands sent last), and the line being written in a bar at the
 * bottom, with who else is writing over it. The game's suggestions open over the bar, in a popup of Glazed Menu's.
 * Each part arrives in its turn as the chat opens, and a message as it comes.
 * <p>
 * It's the game's own screen under another face (it extends it, so whatever asks whether the chat is open is answered
 * as before): the line is the game's own box, moved into the bar, and sending, the history on the arrows and the
 * suggestions are done by the game as ever.
 */
public final class GlazedChatScreen extends CompatChatScreen {
    private static final int PILL_HEIGHT = 22;
    private static final int TAB_HEIGHT = 14;
    private static final int LINE = 9;
    private static final int HEAD = 10;
    private static final int KEY_ESCAPE = 256, KEY_ENTER = 257, KEY_BACKSPACE = 259, KEY_PAGE_UP = 266, KEY_PAGE_DOWN = 267;
    // The tab last chosen is kept while the game runs.
    private static Tab tab = Tab.ALL;

    private enum Tab {
        ALL("All"), PLAYERS("Players"), SYSTEM("System"), MENTIONS("Mentions");

        private final String fallback;

        Tab(String fallback) {
            this.fallback = fallback;
        }

        Component label() {
            return Component.translatableWithFallback("glazedmenu.chat.tab." + this.name().toLowerCase(Locale.ROOT), this.fallback);
        }
    }

    // A message as a row: its lines, who sent it (when that's known), and what it is.
    private record Row(GuiMessage message, List<FormattedCharSequence> lines, int textWidth, @Nullable UUID sender, boolean player, boolean mention, boolean advancement) {}

    // A row where it's drawn this frame.
    private record Placed(Row row, int x, int y, int width, int height, float shown) {}

    private record TabBox(Tab tab, int x, int width) {}

    // A thing done in one click, from the menu the bar's icon opens.
    private record Action(Component label, ConfigIcons.Icon icon, Runnable run) {}

    private final ConfigTheme theme = ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME);
    private final boolean animated = GlazedSettings.transitions();
    private final long openedAt = System.nanoTime();
    private final Map<GuiMessage, Row> rows = new IdentityHashMap<>();
    // When a message that came with the chat open was first drawn, for it to arrive in its own time.
    private final Map<GuiMessage, Long> arrivals = new IdentityHashMap<>();
    private final List<Action> actions = new ArrayList<>();
    private boolean actionsOpen;
    private final List<TabBox> tabs = new ArrayList<>();
    private long rowsAt = this.openedAt;
    private @Nullable GuiMessage newestAtOpen;
    private boolean seenFirstFrame;
    private int scroll;
    private int pillX, pillY, pillWidth, chipsY, tabsY;
    private @Nullable FlatButton send;
    private @Nullable FlatButton gear;
    private boolean menuOpen;
    // Telling the server the player is typing: what was last said, and when the line last changed.
    private String lastValue = "";
    private int ticks;
    private int lastEdit = -1000;
    private int lastSent = -1000;
    private boolean saidTyping;

    // 1.21.1 has no drafts of the chat's line, so the screen keeps one itself, while the game runs: the line left
    // unsent as the chat was last closed, offered again (grayed) the next time it opens.
    private static @Nullable String draft;

    // Whether the line shown is the draft, as it was left: Backspace clears it whole, and any change makes it the
    // player's line again.
    private boolean isDraft;
    private boolean sent;

    private GlazedChatScreen(String initial, boolean isDraft) {
        super(initial);
        this.isDraft = isDraft;
    }

    /** The screen to open instead: this one for the game's own chat screen, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof ChatScreen) || screen.getClass() != ChatScreen.class || !GlazedSettings.replaceChat()) return screen;
        String initial = ((ChatScreenAccessor) screen).glazedmenu$initial();
        // The draft, where the chat is opened plain; opened for a command, only a draft that is one.
        String kept = draft;
        if (kept != null && (initial.isEmpty() || initial.equals("/") && kept.startsWith("/"))) return new GlazedChatScreen(kept, true);
        return new GlazedChatScreen(initial, false);
    }

    private CommandSuggestions suggestions() {
        return ((ChatScreenAccessor) (Object) this).glazedmenu$commandSuggestions();
    }

    private List<GuiMessage> messages() {
        return ((ChatComponentAccessor) this.minecraft.gui.getChat()).glazedmenu$allMessages();
    }

    // --- Time ---

    private long elapsed() {
        return (System.nanoTime() - this.openedAt) / 1_000_000;
    }

    private static float ease(float t) {
        t = Math.clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // How far a part has arrived since the chat opened, 0 to 1; all there with the transitions turned off.
    private float arrived(long at, long length) {
        return this.animated ? ease((this.elapsed() - at) / (float) length) : 1;
    }

    // --- Layout ---

    @Override
    protected void init() {
        super.init();
        // The game's suggestions stay, without the black the game fills behind them: the popup here is drawn instead.
        ((CommandSuggestionsAccessor) this.suggestions()).glazedmenu$setFillColor(0);
        this.pillWidth = Math.clamp(Math.round(this.width * 0.78F), Math.min(220, this.width - 16), Math.max(220, this.width - 16));
        this.pillWidth = Math.min(this.pillWidth, this.width - 16);
        this.pillX = (this.width - this.pillWidth) / 2;
        this.pillY = this.height - 6 - PILL_HEIGHT;
        // Nothing stands between the messages and the bar (the quick actions are in the menu of the bar's icon).
        this.chipsY = this.pillY - 1;
        this.tabsY = this.chipsY - 4 - TAB_HEIGHT;

        // The game's own box for the line, into the bar: between the icon and the counter.
        int right = this.pillX + this.pillWidth - 4;
        this.send = this.addRenderableWidget(new FlatButton(20, 16, CommonComponents.EMPTY, button -> this.keyPressed(new KeyEvent(KEY_ENTER, 0, 0)))
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.SEND).tooltip(Component.translatableWithFallback("glazedmenu.chat.send", "Send")));
        this.send.setPosition(right - 20, this.pillY + 3);
        this.gear = this.addRenderableWidget(new FlatButton(16, 16, CommonComponents.EMPTY, button -> {
                    this.menuOpen = !this.menuOpen;
                    this.actionsOpen = false;
                })
                .style(FlatButton.Style.GHOST).withIcon(ConfigIcons.GEAR).tooltip(Component.translatableWithFallback("glazedmenu.chat.options", "Chat options")));
        this.gear.setPosition(right - 20 - 3 - 16, this.pillY + 3);
        int counter = this.font.width("256 / 256") + 6;
        this.input.setX(this.pillX + 25);
        this.input.setY(this.pillY + 7);
        this.input.setWidth(this.gear.getX() - counter - (this.pillX + 25) - 2);
        this.input.setHint(Component.translatableWithFallback("glazedmenu.chat.hint", "Say something, or type / for a command").withStyle(ChatFormatting.DARK_GRAY));
        // The draft is written gray and slanted, as the game writes one; anything else as the game's suggestions color it.
        BiFunction<String, Integer, FormattedCharSequence> colored = ((EditBoxAccessor) this.input).glazedmenu$formatter();
        this.input.setFormatter((text, offset) -> this.isDraft
                ? FormattedCharSequence.forward(text, Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true)) : colored.apply(text, offset));
        this.suggestions().updateCommandInfo();

        this.initChips();
        this.tabs.clear();
        int x = 6;
        for (Tab each : Tab.values()) {
            int width = this.font.width(each.label()) + 12 + (each == Tab.MENTIONS ? 10 : 0);
            this.tabs.add(new TabBox(each, x, width));
            x += width + 3;
        }
        this.rows.clear();
        if (!this.seenFirstFrame) {
            List<GuiMessage> messages = this.messages();
            this.newestAtOpen = messages.isEmpty() ? null : messages.getFirst();
            this.lastValue = this.input.getValue();
        }
    }

    // The things to do in one click, in the menu the bar's own icon opens: the item held shown to everyone, the
    // player's place written into the line, and the commands sent last put back into it.
    private void initChips() {
        this.actions.clear();
        ItemStack held = this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getMainHandItem();
        if (GlazedSettings.chatShowcase() && !held.isEmpty()) {
            String name = held.getHoverName().getString();
            Component label = Component.translatableWithFallback("glazedmenu.chat.show", "Show %s", name.length() > 22 ? name.substring(0, 21) + "…" : name);
            this.actions.add(new Action(label, ConfigIcons.EYE, () -> {
                if (ChatLink.supports(ChatPayloads.Showcase.TYPE)) {
                    ChatLink.send(new ChatPayloads.Showcase());
                } else {
                    // A server without Glazed Menu can't put the item itself into the chat: its name goes into the line.
                    this.input.insertText("[" + name + (held.getCount() > 1 ? " x" + held.getCount() : "") + "]");
                }
            }));
        }
        if (this.minecraft.player != null) {
            this.actions.add(new Action(Component.translatableWithFallback("glazedmenu.chat.place", "Share my coordinates"), ConfigIcons.GLOBE, () -> {
                BlockPos at = this.minecraft.player.blockPosition();
                this.input.insertText(at.getX() + ", " + at.getY() + ", " + at.getZ());
            }));
        }
        var recent = this.minecraft.gui.getChat().getRecentChat();
        List<String> commands = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0 && commands.size() < 3; i--) {
            String line = recent.get(i);
            if (line.startsWith("/") && !commands.contains(line)) commands.add(line);
        }
        for (String command : commands) {
            this.actions.add(new Action(Component.literal(command.length() > 28 ? command.substring(0, 27) + "…" : command), ConfigIcons.UNDO, () -> this.input.setValue(command)));
        }
    }

    private int actionsWidth() {
        int width = 120;
        for (Action action : this.actions) width = Math.max(width, this.font.width(action.label) + 28);
        return Math.min(width, this.width - 12);
    }

    private int actionsHeight() {
        return 4 + this.actions.size() * 14 + 4;
    }

    private int actionsY() {
        return this.pillY - 4 - this.actionsHeight();
    }

    private boolean overIcon(double x, double y) {
        return x >= this.pillX + 3 && x < this.pillX + 19 && y >= this.pillY + 3 && y < this.pillY + 19;
    }

    // The quick actions, over the bar's icon.
    private void extractActions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.nextStratum();
        int x = this.pillX;
        int y = this.actionsY();
        int width = this.actionsWidth();
        int height = this.actionsHeight();
        ConfigStyle.rect(graphics, x + 2, y + 3, width, height, 0x60000000);
        ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xF6));
        ConfigStyle.outline(graphics, x, y, width, height, ConfigStyle.colors().panelBorder());
        for (int i = 0; i < this.actions.size(); i++) {
            Action action = this.actions.get(i);
            int lineY = y + 4 + i * 14;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= lineY && mouseY < lineY + 14;
            if (hovered) graphics.fill(x + 2, lineY, x + width - 2, lineY + 14, ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x1C));
            action.icon.draw(graphics, x + 6, lineY + 3, 8, hovered ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
            ConfigStyle.text(graphics, this.font, action.label, x + 19, lineY + 3, width - 24, hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
        }
    }

    // --- Messages ---

    private Row row(GuiMessage message) {
        return this.rows.computeIfAbsent(message, each -> makeRow(this.minecraft, this.font, this.width, each));
    }

    private static Row makeRow(Minecraft minecraft, Font font, int screenWidth, GuiMessage message) {
        int most = Math.max(80, Math.min(300, screenWidth - 16 - HEAD - 16));
        List<FormattedCharSequence> lines = ComponentRenderUtils.wrapComponents(message.content(), most, font);
        int textWidth = 0;
        for (FormattedCharSequence line : lines) textWidth = Math.max(textWidth, font.width(line));
        String text = message.content().getString();
        UUID sender = ChatLink.senderOf(message);
        // 1.21.1's messages don't say where they came from: a player's is one whose sender was noted as it came.
        boolean player = sender != null;
        ClientPacketListener connection = minecraft.getConnection();
        if (sender == null && connection != null) {
            // A server that sends its players' chat as its own: the sender is the player named at the start of the
            // line, and it's taken as a player's message where the name is followed as a chat line's is.
            String start = text.length() > 48 ? text.substring(0, 48) : text;
            int best = Integer.MAX_VALUE;
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                String name = info.getProfile().getName();
                int at = name.isEmpty() ? -1 : start.indexOf(name);
                if (at < 0 || at >= best) continue;
                int end = at + name.length();
                boolean wholeBefore = at == 0 || !Character.isLetterOrDigit(start.charAt(at - 1)) && start.charAt(at - 1) != '_';
                boolean wholeAfter = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end)) && text.charAt(end) != '_';
                if (!wholeBefore || !wholeAfter) continue;
                // Only a rank or a mark may stand before the sender's name ("[Admin] Name:"), not words: a name further
                // into the line is someone being spoken of.
                String before = start.substring(0, at).replaceAll("\\[[^\\]]*\\]|\\([^)]*\\)", "");
                if (before.chars().anyMatch(Character::isLetterOrDigit)) continue;
                best = at;
                sender = info.getProfile().getId();
                String after = text.substring(end, Math.min(text.length(), end + 4));
                player = after.startsWith(">") || after.startsWith(":") || after.startsWith("]") || after.contains(":") || after.contains("»") || after.contains(">");
            }
        }
        // Another player's message naming the player. (What the game or a command says of the player isn't one: 1.21.1's
        // messages don't say where they came from, so only a line known to be a player's counts.)
        boolean mention = false;
        if (minecraft.player != null && player && sender != null && !minecraft.player.getUUID().equals(sender)) {
            String own = minecraft.player.getGameProfile().getName().toLowerCase(Locale.ROOT);
            mention = !own.isEmpty() && text.toLowerCase(Locale.ROOT).contains(own);
        }
        boolean advancement = message.content().getContents() instanceof TranslatableContents contents && contents.getKey().startsWith("chat.type.advancement");
        return new Row(message, lines, textWidth, sender, player, mention, advancement);
    }

    private boolean inTab(Row row) {
        return switch (tab) {
            case ALL -> true;
            case PLAYERS -> row.player;
            case SYSTEM -> !row.player;
            case MENTIONS -> row.mention;
        };
    }

    private List<Row> shownRows() {
        List<Row> shown = new ArrayList<>();
        for (GuiMessage message : this.messages()) {
            Row row = this.row(message);
            if (this.inTab(row)) shown.add(row);
        }
        return shown;
    }

    // Who else is typing takes a line of its own over the buttons, while someone is.
    private boolean typingShown() {
        return GlazedSettings.chatTyping() && !ChatLink.typingNames(this.minecraft).isEmpty();
    }

    // The rows where they're drawn now: from the newest at the bottom up, as many as fit under the tabs, which sit on
    // top of the topmost of them.
    private List<Placed> place() {
        List<Row> shown = this.shownRows();
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, shown.size() - 1));
        List<Placed> placed = new ArrayList<>();
        long now = System.nanoTime();
        int y = this.chipsY - 4 - (this.typingShown() ? TAB_HEIGHT + 2 : 0);
        boolean pastOpen = this.newestAtOpen == null;
        List<GuiMessage> all = this.messages();
        int newAtFront = this.newestAtOpen == null ? all.size() : all.indexOf(this.newestAtOpen);
        for (int i = this.scroll; i < shown.size(); i++) {
            Row row = shown.get(i);
            int height = row.lines.size() * LINE + 5;
            y -= height;
            if (y < 4 + TAB_HEIGHT + 3) {
                y += height;
                break;
            }
            int width = 5 + lead(row) + row.textWidth + 6;
            float shownNow;
            int index = all.indexOf(row.message);
            if (!this.animated) {
                shownNow = 1;
            } else if (newAtFront >= 0 && index >= 0 && index < newAtFront && !pastOpen || this.arrivals.containsKey(row.message)) {
                // Came with the chat open: it arrives when it's first drawn.
                long first = this.arrivals.computeIfAbsent(row.message, message -> now);
                shownNow = ease((now - first) / 1_000_000F / 220);
            } else {
                // There as the chat opened (or as the tab was chosen): the newest first, one after another.
                shownNow = ease(((now - this.rowsAt) / 1_000_000F - 140 - 22 * (i - this.scroll)) / 200);
            }
            placed.add(new Placed(row, 6, y, width, height, shownNow));
            y -= 2;
        }
        this.tabsY = (placed.isEmpty() ? y : placed.getLast().y) - 3 - TAB_HEIGHT;
        return placed;
    }

    // The room before a row's text: for the trophy of an advancement and for the sender's face, where it has them.
    private static int lead(Row row) {
        return (row.advancement ? HEAD + 3 : 0) + (row.sender != null ? HEAD + 4 : row.advancement ? 1 : 0);
    }

    // A row's box: its glass, its border (marked where it names the player), and the sender's face.
    private static void box(GuiGraphicsExtractor graphics, @Nullable ClientPacketListener connection, Row row, int x, int y, int width, int height) {
        int body = ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xCC);
        if (row.advancement) body = ConfigStyle.withAlpha(ConfigStyle.mix(ConfigStyle.colors().popup(), 0xFFE0B040, 0.25F), 0xCC);
        ConfigStyle.rect(graphics, x, y, width, height, body);
        int edge = row.mention ? ConfigStyle.accent() : row.advancement ? ConfigStyle.withAlpha(0xFFE0B040, 0xB0) : ConfigStyle.withAlpha(ConfigStyle.colors().panelBorder(), 0xB0);
        // The border follows the row's rounded corners; a row naming the player has a thicker left edge inside it.
        ConfigStyle.outline(graphics, x, y, width, height, edge);
        if (row.mention) graphics.fill(x + 1, y + 2, x + 2, y + height - 2, edge);
        int headY = y + 2;
        // An advancement has its trophy first, then the face of the player who made it.
        int headX = x + 4;
        if (row.advancement) {
            ConfigIcons.TROPHY.draw(graphics, headX, headY, HEAD, 0xFFE0B040);
            headX += HEAD + 3;
        }
        if (row.sender != null) {
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(row.sender);
            if (info != null) {
                PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), headX, headY, HEAD, GuiFade.apply(0xFFFFFFFF));
            } else {
                ConfigIcons.PERSON.draw(graphics, headX, headY, HEAD, ConfigStyle.colors().textMuted());
            }
        }
    }

    // --- Over the game, with the chat closed ---

    private static final Map<GuiMessage, Row> hudRows = new IdentityHashMap<>();
    private static int hudWidth;

    /**
     * The chat as it's seen over the game while it isn't open (ChatComponentMixin), in the same rows: the newest
     * messages for the ten seconds the game shows them, fading out at the end of those as the game's own lines do.
     */
    public static void extractHud(GuiGraphicsExtractor graphics, Font font, int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        ConfigStyle.use(ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        int width = graphics.guiWidth();
        if (width != hudWidth || hudRows.size() > 400) {
            hudRows.clear();
            hudWidth = width;
        }
        float opacity = minecraft.options.chatOpacity().get().floatValue() * 0.9F + 0.1F;
        ClientPacketListener connection = minecraft.getConnection();
        ActiveTextCollector collector = graphics.textRenderer();
        int y = graphics.guiHeight() - 40;
        int count = 0;
        for (GuiMessage message : ((ChatComponentAccessor) minecraft.gui.getChat()).glazedmenu$allMessages()) {
            // The game's own measure of a line's time: full for nine seconds, gone by the tenth. Older ones are gone too.
            double left = Math.clamp((1 - (ticks - message.addedTime()) / 200.0) * 10, 0, 1);
            if (left <= 0.01 || ++count > 10) break;
            Row row = hudRows.computeIfAbsent(message, each -> makeRow(minecraft, font, width, each));
            int height = row.lines.size() * LINE + 5;
            y -= height;
            if (y < 4) break;
            float shown = (float) (left * left) * opacity;
            // A message slides in as it comes.
            float arrived = GlazedSettings.transitions() ? ease((ticks - message.addedTime() + 1) / 4F) : 1;
            int x = 6 - Math.round(10 * (1 - arrived));
            GuiFade.set(shown * arrived);
            box(graphics, connection, row, x, y, 5 + lead(row) + row.textWidth + 6, height);
            GuiFade.reset();
            ActiveTextCollector.Parameters parameters = collector.defaultParameters().withOpacity(shown * arrived);
            int textY = y + 3;
            for (FormattedCharSequence line : row.lines) {
                collector.accept(TextAlignment.LEFT, x + 5 + lead(row), textY, parameters, line);
                textY += LINE;
            }
            y -= 2;
        }
    }

    // The text of the rows, into whatever takes it: the screen as it's drawn, or the finder of what was clicked.
    private void visitText(ActiveTextCollector collector, List<Placed> placed) {
        for (Placed each : placed) {
            if (each.shown <= 0.02F) continue;
            int x = each.x + 5 + lead(each.row) - Math.round(10 * (1 - each.shown));
            ActiveTextCollector.Parameters parameters = collector.defaultParameters().withOpacity(each.shown);
            int y = each.y + 3;
            for (FormattedCharSequence line : each.row.lines) {
                collector.accept(TextAlignment.LEFT, x, y, parameters, line);
                y += LINE;
            }
        }
    }

    // --- Input ---

    @Override
    public void tick() {
        super.tick();
        this.ticks++;
        String value = this.input.getValue();
        if (!value.equals(this.lastValue)) {
            this.lastValue = value;
            this.lastEdit = this.ticks;
            this.isDraft = false;
        }
        // The others are told the player is typing while a message (not a command) is being written, and again every
        // few seconds of it; and that they stopped, once the line is empty or has rested.
        boolean typing = GlazedSettings.chatTyping() && !GlazedSettings.chatHideTyping() && !value.isBlank() && !value.startsWith("/")
                && this.ticks - this.lastEdit < 80 && ChatLink.supports(ChatPayloads.Typing.TYPE);
        if (typing != this.saidTyping || typing && this.ticks - this.lastSent >= 50) {
            if (typing || this.saidTyping) ChatLink.send(new ChatPayloads.Typing(typing));
            this.saidTyping = typing;
            this.lastSent = this.ticks;
        }
    }

    @Override
    public void removed() {
        super.removed();
        // The line as it is, for the screen coming back (from the chat's settings), and as the draft: kept where it was
        // left unsent, gone once it's sent or emptied.
        String line = this.input.getValue();
        ((ChatScreenAccessor) (Object) this).glazedmenu$setInitial(line);
        draft = this.sent || line.isBlank() ? null : line;
        if (this.saidTyping) {
            ChatLink.send(new ChatPayloads.Typing(false));
            this.saidTyping = false;
        }
    }

    // How far the game's suggestions (which open from the bottom of the screen) are lifted, to open over the bar.
    private int lift() {
        return this.height - 12 - (this.pillY - 3);
    }

    @Override
    public void handleChatInput(String message, boolean addToRecentChat) {
        this.sent = true;
        super.handleChatInput(message, addToRecentChat);
    }

    // A change to the line (typed, pasted, taken from the history) makes a draft the player's own line.
    @Override
    public boolean charTyped(char character, int modifiers) {
        boolean typed = super.charTyped(character, modifiers);
        if (!this.input.getValue().equals(this.lastValue)) this.isDraft = false;
        return typed;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Backspace on the draft, untouched, clears it whole (as the game does with its drafts).
        if (this.isDraft && event.key() == KEY_BACKSPACE) {
            this.input.setValue("");
            this.isDraft = false;
            draft = null;
            return true;
        }
        if ((this.menuOpen || this.actionsOpen) && event.key() == KEY_ESCAPE) {
            this.menuOpen = false;
            this.actionsOpen = false;
            return true;
        }
        if (event.key() == KEY_PAGE_UP || event.key() == KEY_PAGE_DOWN) {
            this.scroll += event.key() == KEY_PAGE_UP ? 6 : -6;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollY = Math.clamp(scrollY, -1.0, 1.0);
        if (this.suggestions().mouseScrolled(scrollY)) return true;
        if (scrollY != 0) this.scroll += (int) Math.signum(scrollY) * (hasShiftDown() ? 1 : 3);
        return true;
    }

    private int menuWidth() {
        return 176;
    }

    private int menuHeight() {
        return 4 + 3 * 14 + 3 + 14 + 4;
    }

    private int menuX() {
        return Math.min(this.width - 6 - this.menuWidth(), this.pillX + this.pillWidth - this.menuWidth());
    }

    private int menuY() {
        return this.pillY - 4 - this.menuHeight();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        if (this.actionsOpen) {
            int index = ((int) y - this.actionsY() - 4) / 14;
            if (x >= this.pillX && x < this.pillX + this.actionsWidth() && y >= this.actionsY() && y < this.actionsY() + this.actionsHeight()) {
                if (event.button() == 0 && y >= this.actionsY() + 4 && index >= 0 && index < this.actions.size()) {
                    this.actionsOpen = false;
                    this.actions.get(index).run.run();
                }
                return true;
            }
            if (!this.overIcon(x, y)) this.actionsOpen = false;
        }
        if (event.button() == 0 && this.overIcon(x, y) && !this.actions.isEmpty()) {
            this.actionsOpen = !this.actionsOpen;
            this.menuOpen = false;
            return true;
        }
        if (this.menuOpen) {
            if (x >= this.menuX() && x < this.menuX() + this.menuWidth() && y >= this.menuY() && y < this.menuY() + this.menuHeight()) {
                int line = ((int) y - this.menuY() - 4) / 14;
                if (event.button() == 0) {
                    switch (line) {
                        case 0 -> GlazedSettings.setChatHideTyping(!GlazedSettings.chatHideTyping());
                        case 1 -> GlazedSettings.setChatTyping(!GlazedSettings.chatTyping());
                        case 2 -> {
                            GlazedSettings.setChatShowcase(!GlazedSettings.chatShowcase());
                            this.rebuildWidgets();
                        }
                        default -> {
                            if ((int) y - this.menuY() >= 4 + 3 * 14 + 3) this.minecraft.setScreen(new ChatOptionsScreen(this, this.minecraft.options));
                        }
                    }
                }
                return true;
            }
            if (this.gear == null || !this.gear.isMouseOver(x, y)) this.menuOpen = false;
        }
        // The suggestions are drawn lifted over the bar: a click is looked for where they're seen.
        if (this.suggestions().mouseClicked(x, y + this.lift(), event.button())) return true;
        for (var child : this.children()) {
            if (Compat.mouseClicked(child, event, doubleClick)) {
                this.setFocused(this.input);
                if (event.button() == 0 && child == this.input) this.setDragging(true);
                return true;
            }
        }
        if (event.button() == 0) {
            if (y >= this.tabsY && y < this.tabsY + TAB_HEIGHT) {
                for (TabBox box : this.tabs) {
                    if (x >= box.x && x < box.x + box.width) {
                        if (tab != box.tab) {
                            tab = box.tab;
                            this.scroll = 0;
                            this.rowsAt = System.nanoTime();
                            this.arrivals.clear();
                        }
                        return true;
                    }
                }
            }
            // What was clicked in a message (a link, a name, a command to run), done as the game does it.
            ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(this.font, (int) x, (int) y)
                    .includeInsertions(hasShiftDown());
            this.visitText(finder, this.place());
            Style clicked = finder.result();
            if (clicked != null) {
                ClickEvent click = clicked.getClickEvent();
                if (hasShiftDown() && clicked.getInsertion() != null) {
                    this.insertText(clicked.getInsertion(), false);
                    return true;
                }
                if (click != null && this.handleComponentClicked(clicked)) {
                    ((ChatScreenAccessor) (Object) this).glazedmenu$setInitial(this.input.getValue());
                    return true;
                }
            }
        }
        return true;
    }

    // --- Drawing ---

    private void part(GuiGraphicsExtractor graphics, float arrived, float fromY, Runnable draw) {
        if (arrived <= 0) return;
        GuiFade.set(arrived);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, fromY * (1 - arrived));
        try {
            draw.run();
        } finally {
            graphics.pose().popMatrix();
            GuiFade.reset();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        this.seenFirstFrame = true;
        boolean command = this.input.getValue().startsWith("/");

        // The messages, each on a row of its own.
        List<Placed> placed = this.place();
        ClientPacketListener connection = this.minecraft.getConnection();
        for (Placed each : placed) {
            if (each.shown <= 0.02F) continue;
            GuiFade.set(each.shown);
            int x = each.x - Math.round(10 * (1 - each.shown));
            box(graphics, connection, each.row, x, each.y, each.width, each.height);
        }
        GuiFade.reset();
        this.visitText(graphics.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR), placed);

        // The tabs, and who else is writing.
        int mentions = 0;
        for (GuiMessage message : this.messages()) {
            if (this.row(message).mention) mentions++;
        }
        for (int i = 0; i < this.tabs.size(); i++) {
            TabBox box = this.tabs.get(i);
            int count = mentions;
            this.part(graphics, this.arrived(110 + 30L * i, 200), 8, () -> {
                boolean chosen = tab == box.tab;
                boolean hovered = mouseX >= box.x && mouseX < box.x + box.width && mouseY >= this.tabsY && mouseY < this.tabsY + TAB_HEIGHT;
                if (chosen) {
                    // The gradient inside the rounded box, so none of it stands out past the corners.
                    ConfigStyle.rect(graphics, box.x, this.tabsY, box.width, TAB_HEIGHT, ConfigStyle.mix(ConfigStyle.accent(), 0xFF140C3A, 0.5F));
                    GlazedBrand.fill(graphics, box.x + 1, this.tabsY + 1, box.width - 2, TAB_HEIGHT - 2, 0xFF, 0.24F);
                } else ConfigStyle.rect(graphics, box.x, this.tabsY, box.width, TAB_HEIGHT, ConfigStyle.withAlpha(ConfigStyle.colors().popup(), hovered ? 0xE8 : 0xC8));
                ConfigStyle.outline(graphics, box.x, this.tabsY, box.width, TAB_HEIGHT, chosen ? ConfigStyle.mix(ConfigStyle.accent(), 0xFF000000, 0.4F) : ConfigStyle.colors().panelBorder());
                int color = chosen ? 0xFFFFFFFF : hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim();
                graphics.text(this.font, box.tab.label(), box.x + 6, this.tabsY + 3, color, false);
                if (box.tab == Tab.MENTIONS && count > 0) {
                    String number = count > 9 ? "9+" : Integer.toString(count);
                    graphics.text(this.font, number, box.x + box.width - 5 - this.font.width(number), this.tabsY + 3, chosen ? 0xFFFFFFFF : ConfigStyle.accent(), false);
                }
            });
        }
        if (GlazedSettings.chatTyping()) {
            List<String> names = ChatLink.typingNames(this.minecraft);
            if (!names.isEmpty()) {
                String dots = ".".repeat(1 + (int) (System.currentTimeMillis() / 350 % 3));
                Component text = names.size() == 1 ? Component.translatableWithFallback("glazedmenu.chat.typing.one", "%s is typing", names.getFirst())
                        : names.size() == 2 ? Component.translatableWithFallback("glazedmenu.chat.typing.two", "%s and %s are typing", names.get(0), names.get(1))
                        : Component.translatableWithFallback("glazedmenu.chat.typing.many", "Several players are typing");
                Component line = Component.empty().append(text).append(dots);
                int x = 6;
                int y = this.chipsY - 2 - TAB_HEIGHT;
                int width = this.font.width(text) + this.font.width("...") + 10;
                ConfigStyle.rect(graphics, x, y, width, TAB_HEIGHT, ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xC8));
                graphics.text(this.font, line, x + 5, y + 3, ConfigStyle.colors().textDim(), false);
            }
        }

        // The bar: what kind of line it is, the line, how long it is, the options and Send.
        this.part(graphics, this.arrived(0, 220), 14, () -> {
            ConfigStyle.bar(graphics, this.pillX, this.pillY, this.pillWidth, PILL_HEIGHT);
            if (command) ConfigStyle.outline(graphics, this.pillX, this.pillY, this.pillWidth, PILL_HEIGHT, ConfigStyle.accent());
            boolean lit = this.actionsOpen || this.overIcon(mouseX, mouseY);
            ConfigStyle.iconFrame(graphics, this.pillX + 3, this.pillY + 3, 16, 16, ConfigStyle.withAlpha(ConfigStyle.mix(command ? ConfigStyle.accent() : GlazedBrand.identity(0xFF6E7CF0), 0xFFFFFFFF, lit ? 0.25F : 0), 0xD8));
            (command ? ConfigIcons.CODE : ConfigIcons.CHAT).draw(graphics, this.pillX + 6, this.pillY + 6, 10, 0xFFFFFFFF);
            Compat.extractRenderState(this.input, graphics, mouseX, mouseY, a);
            String count = this.input.getValue().length() + " / 256";
            if (this.gear != null) {
                graphics.text(this.font, count, this.gear.getX() - 5 - this.font.width(count), this.pillY + 7,
                        this.input.getValue().length() >= 240 ? ConfigStyle.colors().error() : ConfigStyle.colors().textMuted(), false);
                Compat.extractRenderState(this.gear, graphics, mouseX, mouseY, a);
            }
            if (this.send != null) Compat.extractRenderState(this.send, graphics, mouseX, mouseY, a);
        });

        this.extractSuggestions(graphics, mouseX, mouseY);
        if (this.menuOpen) this.extractMenu(graphics, mouseX, mouseY);
        if (this.actionsOpen) this.extractActions(graphics, mouseX, mouseY);
    }

    // The game's suggestions (or the usage of the command being written), lifted to open over the bar, on a popup of
    // Glazed Menu's with the one chosen marked.
    private void extractSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        CommandSuggestions suggestions = this.suggestions();
        CommandSuggestionsAccessor access = (CommandSuggestionsAccessor) suggestions;
        int lift = this.lift();
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, -lift);
        Object list = access.glazedmenu$suggestions();
        if (list != null) {
            SuggestionsListAccessor shown = (SuggestionsListAccessor) list;
            Rect2i rect = shown.glazedmenu$rect();
            ConfigStyle.rect(graphics, rect.getX() - 3, rect.getY() - 3, rect.getWidth() + 6, rect.getHeight() + 6, 0x50000000);
            ConfigStyle.rect(graphics, rect.getX() - 2, rect.getY() - 2, rect.getWidth() + 4, rect.getHeight() + 4, ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xF4));
            ConfigStyle.outline(graphics, rect.getX() - 2, rect.getY() - 2, rect.getWidth() + 4, rect.getHeight() + 4, ConfigStyle.withAlpha(ConfigStyle.accent(), 0xC0));
            int chosen = shown.glazedmenu$current() - shown.glazedmenu$offset();
            if (chosen >= 0 && chosen * 12 < rect.getHeight()) {
                graphics.fill(rect.getX(), rect.getY() + chosen * 12, rect.getX() + rect.getWidth(), rect.getY() + chosen * 12 + 12, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x58));
            }
        } else if (!access.glazedmenu$commandUsage().isEmpty()) {
            int lines = access.glazedmenu$commandUsage().size();
            int x = access.glazedmenu$commandUsagePosition() - 3;
            int y = this.height - 27 - 12 * (lines - 1) - 2;
            int width = access.glazedmenu$commandUsageWidth() + 6;
            ConfigStyle.rect(graphics, x, y, width, lines * 12 + 3, ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xF4));
            ConfigStyle.outline(graphics, x, y, width, lines * 12 + 3, ConfigStyle.colors().panelBorder());
        }
        suggestions.render(graphics.raw(), mouseX, mouseY + lift);
        graphics.pose().popMatrix();
    }

    // The chat's options, over the gear: whether the others are told the player is typing, whether who is typing is
    // shown, whether the item held can be shown, and the game's own chat settings.
    private void extractMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.nextStratum();
        int x = this.menuX();
        int y = this.menuY();
        int width = this.menuWidth();
        int height = this.menuHeight();
        ConfigStyle.rect(graphics, x + 2, y + 3, width, height, 0x60000000);
        ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xF6));
        ConfigStyle.outline(graphics, x, y, width, height, ConfigStyle.colors().panelBorder());
        Component[] labels = {
                Component.translatableWithFallback("glazedmenu.chat.menu.hide", "Hide that I'm typing"),
                Component.translatableWithFallback("glazedmenu.chat.menu.typing", "Show who is typing"),
                Component.translatableWithFallback("glazedmenu.chat.menu.showcase", "Show item button")};
        boolean[] on = {GlazedSettings.chatHideTyping(), GlazedSettings.chatTyping(), GlazedSettings.chatShowcase()};
        for (int i = 0; i < labels.length; i++) {
            int lineY = y + 4 + i * 14;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= lineY && mouseY < lineY + 14;
            if (hovered) graphics.fill(x + 2, lineY, x + width - 2, lineY + 14, ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x1C));
            int box = on[i] ? ConfigStyle.accent() : ConfigStyle.colors().field();
            ConfigStyle.rect(graphics, x + 6, lineY + 3, 8, 8, box);
            if (on[i]) ConfigIcons.CHECK.draw(graphics, x + 6, lineY + 3, 8, 0xFFFFFFFF);
            else ConfigStyle.outline(graphics, x + 6, lineY + 3, 8, 8, ConfigStyle.colors().panelBorder());
            ConfigStyle.text(graphics, this.font, labels[i], x + 19, lineY + 3, width - 24, hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
        }
        int lineY = y + 4 + 3 * 14 + 3;
        graphics.fill(x + 4, lineY - 2, x + width - 4, lineY - 1, ConfigStyle.withAlpha(ConfigStyle.colors().panelBorder(), 0xA0));
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= lineY && mouseY < lineY + 14;
        if (hovered) graphics.fill(x + 2, lineY, x + width - 2, lineY + 14, ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x1C));
        ConfigIcons.GEAR.draw(graphics, x + 6, lineY + 3, 8, ConfigStyle.colors().textDim());
        ConfigStyle.text(graphics, this.font, Component.translatable("options.chat"), x + 19, lineY + 3, width - 24, hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
    }
}
