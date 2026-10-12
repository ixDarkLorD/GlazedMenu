package net.ixdarklord.glazedmenu.internal.game;

import net.minecraft.util.Mth;
import net.ixdarklord.glazedmenu.internal.compat.CompatList;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.ixdarklord.glazedmenu.internal.compat.KeyEvent;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * What the lists of worlds, servers, advancements and statistics share: a header saying what the list holds, a search,
 * the list down the left with the chosen entry's details beside it (where the screen has any), and a bar of actions
 * under them. A click chooses an entry, a second one (or Enter) opens it, and the arrow keys move the choice. A screen
 * with tabs gets a ribbon of them under the header, each an icon over its name.
 * <p>
 * Keys: Ctrl+F searches.
 */
abstract class GameListScreen extends StyledScreen {
    // A taller header than the other screens, as on the list of every mod's configs.
    private static final int HEADER_HEIGHT = 46;
    private static final int HEADER_ICON = 32;
    // An entry's box is three pixels shorter than its row, which leaves its picture three pixels on every side.
    protected static final int ROW_HEIGHT = 33;
    protected static final int ROW_ICON = 24;
    private static final int HEADING_HEIGHT = 16;
    private static final int RIBBON_HEIGHT = 34;
    private static final int TAB_MIN_WIDTH = 46;
    private static final int KEY_F = 70;
    private static final int KEY_ENTER = 257;
    private static final int KEY_NUMPAD_ENTER = 335;
    private static final int KEY_DOWN = 264;
    private static final int KEY_UP = 265;

    protected final @Nullable Screen parent;
    private final ConfigIcons.Icon icon;
    protected String query = "";
    protected @Nullable StyledEditBox search;
    private @Nullable Rows rows;
    private List<Row> shown = List.of();
    // What the chosen row stands for (a world's folder, a server), or null with none chosen.
    protected @Nullable Object chosen;
    // Where the header's text must end: the leftmost thing at the top bar's right.
    protected int headerRight;
    private double ribbonScroll;

    protected GameListScreen(Component title, @Nullable Screen parent, ConfigIcons.Icon icon) {
        super(title, ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.parent = parent;
        this.icon = icon;
    }

    // --- Layout ---

    private boolean tallHeader() {
        return this.height >= 230;
    }

    @Override
    protected int topBarHeight() {
        return this.tallHeader() ? HEADER_HEIGHT : BAR_HEIGHT;
    }

    /** How wide the details pane beside the list is; 0 for a screen without one. */
    protected int detailsWidth() {
        return Mth.clamp(this.frameWidth() * 38 / 100, 150, 230);
    }

    protected int detailsLeft() {
        return this.frameLeft() + this.frameWidth() - this.detailsWidth();
    }

    protected int listWidth() {
        return this.detailsWidth() > 0 ? this.frameWidth() - this.detailsWidth() - GAP : this.frameWidth();
    }

    /** The room kept above the rows, inside the list's panel, for what a screen draws there itself. */
    protected int listInset() {
        return 0;
    }

    /** A tab of the ribbon: its name under an icon (one of Glazed Menu's, or an item), in a color of its own. */
    protected record Tab(Component name, ConfigIcons.@Nullable Icon icon, @Nullable ItemStack stack, int color) {}

    /** The ribbon's tabs; none for a screen without a ribbon. Asked for often, so kept by the screen. */
    protected List<Tab> tabs() {
        return List.of();
    }

    protected int chosenTab() {
        return 0;
    }

    protected void chooseTab(int index) {}

    private int ribbonY() {
        return this.topBarY() + this.topBarHeight() + GAP;
    }

    @Override
    protected int bodyTop() {
        return this.tabs().isEmpty() ? super.bodyTop() : this.ribbonY() + RIBBON_HEIGHT + GAP;
    }

    /** The top of the room of {@link #listInset()}, inside the list's panel. */
    protected int listTop() {
        return this.bodyTop() + 6;
    }

    @Override
    protected void init() {
        if (this.hasSearch()) {
            int searchWidth = Mth.clamp(this.width / 4, 90, 170);
            this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18, this.searchName()));
            this.search.setPosition(this.frameLeft() + this.frameWidth() - 6 - 20 - TOGGLE_ROOM - searchWidth, this.topBarWidgetY(18));
            // The hint with its key where the box has room for it, else just what it searches.
            this.search.setPlaceholder(this.font.width(this.searchHint()) <= searchWidth - 10 ? this.searchHint() : this.searchName());
            this.search.setValue(this.query);
            this.search.setResponder(text -> {
                this.query = text;
                this.rebuildRows(false);
            });
            this.addModeToggleAtEnd(6);
            this.headerRight = this.search.getX() - 18;
        } else {
            this.search = null;
            this.headerRight = this.addModeToggle(this.frameLeft() + this.frameWidth() - 6 - 20).getX() - 6;
        }

        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        int inset = this.listInset();
        this.rows = this.addRenderableWidget(new Rows(this.minecraft, this.listWidth() - 8, bodyHeight - 10 - inset, bodyTop + 6 + inset));
        this.rows.updateSizeAndPosition(this.listWidth() - 8, bodyHeight - 10 - inset, this.frameLeft() + 4, bodyTop + 6 + inset);
        this.initButtons();
        this.rebuildRows(true);
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    /** Whether the top bar has a search; a screen without one lists everything it has. */
    protected boolean hasSearch() {
        return true;
    }

    protected abstract Component searchName();

    protected abstract Component searchHint();

    /** Adds the buttons: in the top bar (left of {@link #headerRight}, lowering it), the details pane and the bottom bar. */
    protected abstract void initButtons();

    /** The rows the search leaves, under their headings. */
    protected abstract List<Row> buildRows();

    /** What's under the title: what the list holds. */
    protected abstract Component subtitle();

    /** The chosen entry's details, in the pane beside the list (above its buttons); never asked of a screen without the pane. */
    protected abstract void extractDetails(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY);

    /** What a screen draws in the room of {@link #listInset()}, and anything else over its panels. */
    protected void extractOverList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

    /** How wide the rows are, for a screen laying columns out across them. */
    protected int rowWidth() {
        return this.rows != null ? this.rows.getRowWidth() : 0;
    }

    protected int rowLeft() {
        return this.rows != null ? this.rows.getRowLeft() : 0;
    }

    /** The choice changed (or what it stands for did): the buttons follow. */
    protected abstract void onChosen();

    // --- The rows ---

    /** Lists the rows again; {@code keepScroll} keeps the list where it was, else the chosen row is brought into view. */
    protected void rebuildRows(boolean keepScroll) {
        if (this.rows == null) return;
        List<Row> rows = this.buildRows();
        // A choice the list no longer holds is dropped.
        if (this.chosen != null && rows.stream().noneMatch(row -> Objects.equals(row.key(), this.chosen))) this.chosen = null;
        this.shown = rows;
        double scroll = this.rows.scrollAmount();
        this.rows.setRows(rows);
        if (keepScroll) this.rows.setScrollAmount(scroll);
        else this.reveal();
        this.onChosen();
    }

    protected void choose(@Nullable Object key) {
        this.chosen = key;
        this.onChosen();
    }

    // Scrolls the chosen row into view.
    private void reveal() {
        if (this.rows == null) return;
        int top = 0;
        for (Row row : this.shown) {
            if (this.chosen != null && Objects.equals(row.key(), this.chosen)) {
                if (top < this.rows.scrollAmount()) this.rows.setScrollAmount(top);
                else if (top + row.height() > this.rows.scrollAmount() + this.rows.getHeight()) this.rows.setScrollAmount(top + row.height() - this.rows.getHeight());
                return;
            }
            top += row.height();
        }
        this.rows.setScrollAmount(0);
    }

    // The choice moved a row up or down, past headings.
    private void moveChoice(int by) {
        List<Row> choices = this.shown.stream().filter(row -> row.key() != null).toList();
        if (choices.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < choices.size(); i++) {
            if (Objects.equals(choices.get(i).key(), this.chosen)) index = i;
        }
        this.choose(choices.get(Mth.clamp(index + by, 0, choices.size() - 1)).key());
        this.reveal();
    }

    private @Nullable Row chosenRow() {
        for (Row row : this.shown) {
            if (this.chosen != null && Objects.equals(row.key(), this.chosen)) return row;
        }
        return null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.key() == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        if (event.key() == KEY_UP || event.key() == KEY_DOWN) {
            this.moveChoice(event.key() == KEY_DOWN ? 1 : -1);
            return true;
        }
        // Enter opens the chosen entry, unless a button has the focus (then it presses that).
        if ((event.key() == KEY_ENTER || event.key() == KEY_NUMPAD_ENTER) && !(this.getFocused() instanceof FlatButton)) {
            Row row = this.chosenRow();
            if (row != null) {
                row.open();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    // --- The ribbon ---

    private int tabWidth(Tab tab) {
        return Math.max(TAB_MIN_WIDTH, this.font.width(tab.name) + 12);
    }

    private double maxRibbonScroll() {
        return Math.max(0, this.tabs().stream().mapToInt(tab -> this.tabWidth(tab) + 2).sum() + 6 - this.frameWidth());
    }

    private boolean overRibbon(double mouseX, double mouseY) {
        return !this.tabs().isEmpty() && mouseX >= this.frameLeft() && mouseX < this.frameLeft() + this.frameWidth()
                && mouseY >= this.ribbonY() && mouseY < this.ribbonY() + RIBBON_HEIGHT;
    }

    // The tab under the mouse, or -1.
    private int tabAt(double mouseX, double mouseY) {
        if (!this.overRibbon(mouseX, mouseY)) return -1;
        int x = this.frameLeft() + 4 - (int) this.ribbonScroll;
        List<Tab> tabs = this.tabs();
        for (int i = 0; i < tabs.size(); i++) {
            int width = this.tabWidth(tabs.get(i));
            if (mouseX >= x && mouseX < x + width) return i;
            x += width + 2;
        }
        return -1;
    }

    private void extractRibbon(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Tab> tabs = this.tabs();
        int top = this.ribbonY();
        // The bar itself is a thin strip: the tabs are taller than it, and stand out above and below it.
        ConfigStyle.bar(graphics, this.frameLeft(), top + 7, this.frameWidth(), RIBBON_HEIGHT - 14);
        this.ribbonScroll = Mth.clamp(this.ribbonScroll, 0, this.maxRibbonScroll());
        int hovered = this.tabAt(mouseX, mouseY);
        // Room under the tabs for their shadows.
        graphics.enableScissor(this.frameLeft() + 1, top, this.frameLeft() + this.frameWidth() - 1, top + RIBBON_HEIGHT + 3);
        int x = this.frameLeft() + 4 - (int) this.ribbonScroll;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int width = this.tabWidth(tab);
            boolean chosen = i == this.chosenTab();
            boolean lit = i == hovered;
            // A button of its own, with thickness: under its face (washed with its color, more where it's chosen or
            // under the mouse, and outlined in it) a lip in a deep shade of the color, as the side of a key. The chosen
            // one is pressed down onto its lip, and the one under the mouse a little.
            int base = ConfigStyle.colors().popup();
            int lip = 4;
            int press = chosen ? 3 : lit ? 1 : 0;
            int face = RIBBON_HEIGHT - lip;
            // Pressed, only a sliver of the lip is left showing, and it's fainter: a hint of an edge, not a second line.
            ConfigStyle.rect(graphics, x, top + lip, width, face,
                    ConfigStyle.withAlpha(ConfigStyle.mix(ConfigStyle.mix(base, tab.color, 0.45F), 0xFF000000, 0.5F), chosen ? 0x70 : 0xFF));
            // The face, and what's on it, moved down by how far it's pressed.
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, press);
            ConfigStyle.rect(graphics, x, top, width, face, ConfigStyle.mix(base, tab.color, chosen ? 0.34F : lit ? 0.18F : 0.07F));
            ConfigStyle.outline(graphics, x, top, width, face,
                    chosen ? tab.color : lit ? ConfigStyle.withAlpha(tab.color, 0xB0) : ConfigStyle.mix(ConfigStyle.colors().panelBorder(), tab.color, 0.25F));
            if (tab.stack != null) {
                graphics.fakeItem(tab.stack, x + (width - 16) / 2, top + 3);
            } else if (tab.icon != null) {
                tab.icon.draw(graphics, x + (width - 14) / 2, top + 5, 14, chosen || lit ? tab.color : ConfigStyle.mix(tab.color, ConfigStyle.colors().textDim(), 0.45F));
            }
            ConfigStyle.centeredText(graphics, this.font, tab.name, x + width / 2, top + 19, width - 4, chosen || lit ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            graphics.pose().popMatrix();
            x += width + 2;
        }
        graphics.disableScissor();
        // More tabs than fit: an arrow at the bar's edge says there's more that way.
        if (this.ribbonScroll > 0) {
            ConfigStyle.rect(graphics, this.frameLeft() + 1, top + 1, 12, RIBBON_HEIGHT - 2, ConfigStyle.colors().bar());
            ConfigIcons.CHEVRON_LEFT.draw(graphics, this.frameLeft() + 2, top + (RIBBON_HEIGHT - 10) / 2, 10, ConfigStyle.accent());
        }
        if (this.ribbonScroll < this.maxRibbonScroll()) {
            int edge = this.frameLeft() + this.frameWidth() - 13;
            ConfigStyle.rect(graphics, edge, top + 1, 12, RIBBON_HEIGHT - 2, ConfigStyle.colors().bar());
            ConfigIcons.CHEVRON.draw(graphics, edge + 1, top + (RIBBON_HEIGHT - 10) / 2, 10, ConfigStyle.accent());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.overRibbon(mouseX, mouseY)) {
            this.ribbonScroll = Mth.clamp(this.ribbonScroll - (scrollY != 0 ? scrollY : scrollX) * 24, 0, this.maxRibbonScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int tab = this.tabAt(event.x(), event.y());
        if (tab >= 0) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.chooseTab(tab);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void setInitialFocus() {
        if (this.search != null && !this.query.isEmpty()) this.setInitialFocus(this.search);
        else super.setInitialFocus();
    }

    // --- Drawing ---

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        int top = this.bodyTop();
        int height = this.bodyBottom() - top;
        List<Tab> tabs = this.tabs();
        if (!tabs.isEmpty()) this.extractRibbon(graphics, mouseX, mouseY);
        ConfigStyle.panel(graphics, this.frameLeft(), top, this.listWidth(), height);
        if (tabs.isEmpty()) {
            // Glazed Menu's flowing gradient along the top edge, as on its mod list.
            GlazedBrand.fill(graphics, this.frameLeft() + 1, top, this.listWidth() - 2, 2, 0xD0);
        } else {
            // The chosen tab's color along it.
            int color = tabs.get(Mth.clamp(this.chosenTab(), 0, tabs.size() - 1)).color;
            graphics.fill(this.frameLeft() + 1, top, this.frameLeft() + this.listWidth() - 1, top + 2, ConfigStyle.withAlpha(color, 0xD0));
        }
        if (this.detailsWidth() > 0) ConfigStyle.panel(graphics, this.detailsLeft(), top, this.detailsWidth(), height);

        if (this.tallHeader()) {
            // The header: a mark in the flowing gradient, the title in it too, and what the list holds under it.
            int iconX = this.frameLeft() + 8;
            int iconY = this.topBarY() + (HEADER_HEIGHT - HEADER_ICON) / 2;
            GlazedBrand.fill(graphics, iconX, iconY, HEADER_ICON, HEADER_ICON, 0xFF, 0.3F);
            ConfigStyle.outline(graphics, iconX, iconY, HEADER_ICON, HEADER_ICON, ConfigStyle.mix(GlazedBrand.now(0.5F), 0xFFFFFFFF, 0.5F));
            this.icon.draw(graphics, iconX + 7, iconY + 7, HEADER_ICON - 14, 0xFFFFFFFF);
            int textX = iconX + HEADER_ICON + 9;
            int textTop = this.topBarY() + (HEADER_HEIGHT - 21) / 2;
            GlazedBrand.text(graphics, this.font, this.title.getString(), textX, textTop);
            ConfigStyle.text(graphics, this.font, this.subtitle(), textX, textTop + 13, Math.max(20, this.headerRight - textX), ConfigStyle.colors().textMuted());
        } else {
            this.extractTitle(graphics, this.title, Math.max(40, this.headerRight - this.frameLeft() - 40), (g, x, y, size) -> {
                this.icon.draw(g, x, y, size, ConfigStyle.accent());
                return true;
            });
        }
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }
        if (this.detailsWidth() > 0) this.extractDetails(graphics, this.detailsLeft() + 8, top + 8, this.detailsWidth() - 16, height - 16, mouseX, mouseY);
        this.extractOverList(graphics, mouseX, mouseY);
    }

    /** A line of the details: its name dim at the left, its value at the right. Returns the y under it. */
    protected int detailLine(GuiGraphicsExtractor graphics, Component name, Component value, int x, int y, int width, int valueColor) {
        ConfigStyle.text(graphics, this.font, name, x, y, width, ConfigStyle.colors().textMuted());
        int room = Math.max(10, width - this.font.width(name) - 8);
        int valueWidth = Math.min(room, this.font.width(value));
        ConfigStyle.text(graphics, this.font, value, x + width - valueWidth, y, room, valueColor);
        return y + 11;
    }

    /** An entry's picture in a square, or a mark on its color where it has none. */
    protected void extractPicture(GuiGraphicsExtractor graphics, @Nullable ResourceLocation texture, ConfigIcons.Icon fallback, int color, int x, int y, int size) {
        if (texture != null) {
            graphics.blit(net.ixdarklord.glazedmenu.internal.compat.RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size, size, size);
            ConfigStyle.pictureFrame(graphics, x, y, size, size);
        } else {
            ConfigStyle.iconFrame(graphics, x, y, size, size, ConfigStyle.withAlpha(color, 0xD0));
            int mark = size * 5 / 8;
            fallback.draw(graphics, x + (size - mark) / 2, y + (size - mark) / 2, mark, ConfigStyle.readableOn(color));
        }
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    public void onClose() {
        this.leave(this.parent);
    }

    // --- The list ---

    private static final class Rows extends CompatList<Row> {
        Rows(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_HEIGHT);
        }

        @Override
        public int getRowWidth() {
            return this.width - 14;
        }

        @Override
        public int getRowLeft() {
            return this.getX() + 4;
        }

        @Override
        protected int scrollBarX() {
            return this.getRight() - 5;
        }

        void setRows(List<Row> rows) {
            this.clearEntries();
            for (Row row : rows) this.addEntry(row, row.height());
        }

        @Override
        protected void extractListBackground(GuiGraphicsExtractor graphics) {}

        @Override
        protected void extractListSeparators(GuiGraphicsExtractor graphics) {}

        @Override
        protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!this.scrollable()) return;
            int x = this.scrollBarX();
            ConfigStyle.rect(graphics, x, this.getY(), 3, this.getHeight(), ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x14));
            graphics.fillGradient(x, this.scrollBarY(), x + 3, this.scrollBarY() + this.scrollerHeight(),
                    ConfigStyle.withAlpha(GlazedBrand.now(0), 0xC0), ConfigStyle.withAlpha(GlazedBrand.now(1), 0xC0));
        }
    }

    /** A row of the list: an entry, or a heading or a notice (which choose nothing). */
    protected abstract static class Row extends CompatList.Entry<Row> {
        protected int height() {
            return ROW_HEIGHT;
        }

        /** What choosing the row chooses; null for a row that isn't an entry. */
        protected @Nullable Object key() {
            return null;
        }

        /** A second click, or Enter. */
        protected void open() {}

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** An entry's row: a click chooses it, a second one opens it. Its box is drawn here; what's in it by the subclass. */
    protected abstract class EntryRow extends Row {
        /** How far the entry's box is set in from the row's left, for an entry listed under another. */
        protected int indent() {
            return 0;
        }

        @Override
        public final void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int x = this.getX() + this.indent();
            int y = this.getY() + 1;
            int width = this.getWidth() - this.indent();
            int height = this.getHeight() - 3;
            boolean chosen = Objects.equals(this.key(), GameListScreen.this.chosen);
            int accent = ConfigStyle.accent();
            ConfigStyle.rect(graphics, x, y, width, height, chosen ? ConfigStyle.mix(ConfigStyle.colors().button(), accent, 0.22F)
                    : hovered ? ConfigStyle.colors().buttonHover() : ConfigStyle.withAlpha(ConfigStyle.colors().button(), 0xB0));
            ConfigStyle.outline(graphics, x, y, width, height, chosen ? ConfigStyle.withAlpha(accent, 0xE0) : ConfigStyle.colors().panelBorder());
            this.extractEntry(graphics, x, y, width, height, chosen, hovered, mouseX, mouseY);
        }

        protected abstract void extractEntry(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean chosen, boolean hovered, int mouseX, int mouseY);

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GameListScreen screen = GameListScreen.this;
            screen.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            boolean again = doubleClick && Objects.equals(this.key(), screen.chosen);
            screen.choose(this.key());
            if (again) this.open();
            return true;
        }
    }

    /** A section's name, bold and dim, with a rule to the edge. */
    protected static final class Heading extends Row {
        private final Component name;

        Heading(Component name) {
            this.name = name.copy().withStyle(ChatFormatting.BOLD);
        }

        @Override
        protected int height() {
            return HEADING_HEIGHT;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = Minecraft.getInstance().font;
            int x = this.getX() + 2;
            int y = this.getY() + this.getHeight() - 12;
            ConfigStyle.text(graphics, font, this.name, x, y, this.getWidth() - 20, ConfigStyle.colors().textDim());
            graphics.fill(x + font.width(this.name) + 6, y + 4, this.getX() + this.getWidth() - 2, y + 5, ConfigStyle.withAlpha(ConfigStyle.mix(ConfigStyle.colors().panelBorder(), ConfigStyle.colors().text(), 0.5F), 0xD0));
        }
    }

    /** A line of dim text in place of entries: nothing found, or still looking. */
    protected static final class Notice extends Row {
        private final ConfigIcons.Icon icon;
        private final Component text;

        Notice(ConfigIcons.Icon icon, Component text) {
            this.icon = icon;
            this.text = text;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = Minecraft.getInstance().font;
            int y = this.getY() + (this.getHeight() - 8) / 2;
            int textWidth = Math.min(font.width(this.text), this.getWidth() - 20);
            int x = this.getX() + (this.getWidth() - textWidth - 14) / 2;
            this.icon.draw(graphics, x, y - 1, ConfigStyle.colors().textMuted());
            ConfigStyle.text(graphics, font, this.text, x + 14, y, textWidth, ConfigStyle.colors().textMuted());
        }
    }
}
