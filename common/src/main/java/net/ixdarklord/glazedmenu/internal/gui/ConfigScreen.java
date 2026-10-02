package net.ixdarklord.glazedmenu.internal.gui;

import net.minecraft.util.Mth;
import net.ixdarklord.glazedmenu.internal.compat.CompatList;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.compat.KeyEvent;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigNode;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.ConfigGroup;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.ConfirmPopup;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * A config's screen: the title, badges and search in the top bar; categories (the root's settings and each top-level
 * group) in a sidebar; the chosen category's settings, with nested groups as sections, in the main panel; and the
 * unsaved-changes status with the actions in the bottom bar. Narrow windows drop the sidebar and show everything.
 * <p>
 * Keys: Ctrl+Z / Ctrl+Y undo and redo, Ctrl+S saves, Ctrl+F searches.
 */
public final class ConfigScreen extends StyledScreen {
    private static final int KEY_F = 70;
    private static final int KEY_S = 83;
    private static final int KEY_Y = 89;
    private static final int KEY_Z = 90;
    private static final int SIDEBAR_MIN_SCREEN_WIDTH = 420;

    private final @Nullable Screen parent;
    private final ConfigEditSession session;
    private final List<Tab> tabs = new ArrayList<>();
    private int selectedTab;
    private String query = "";
    private boolean sidebar;
    private int sidebarWidth;
    private double restoreScroll = -1;
    private @Nullable ConfigEntryList list;
    private @Nullable StyledEditBox search;
    // Done saves and closes; Save saves and stays.
    private @Nullable FlatButton saveButton;
    private @Nullable FlatButton saveHereButton;
    private @Nullable FlatButton undoButton;
    private @Nullable FlatButton redoButton;
    private @Nullable FlatButton resetButton;
    private int statusRight;

    private ConfigScreen(@Nullable Screen parent, ConfigEditSession session) {
        super(session.config().title(), ThemeResources.resolve(session.config().modId(), session.config().theme()));
        this.parent = parent;
        this.session = session;
        ConfigGroup root = session.config().root();
        boolean rootHasValues = root.children().stream().anyMatch(node -> node instanceof ConfigValue<?> value && session.isVisible(value));
        if (rootHasValues) this.tabs.add(new Tab(Component.translatableWithFallback("glazedmenu.tab.general", "General"), root, true));
        for (ConfigNode node : root.children()) {
            if (node instanceof ConfigGroup group && !group.isHidden() && group.values().anyMatch(value -> session.isVisible(value))) {
                this.tabs.add(new Tab(group.displayName(), group, false));
            }
        }
    }

    public static ConfigScreen create(@Nullable Screen parent, Config config) {
        return new ConfigScreen(parent, new ConfigEditSession(config));
    }

    /** Opens the config filtered by a search, as the main screen's search results do. */
    public static ConfigScreen create(@Nullable Screen parent, Config config, String query) {
        ConfigScreen screen = new ConfigScreen(parent, new ConfigEditSession(config));
        screen.query = query;
        return screen;
    }

    private record Tab(Component name, ConfigGroup group, boolean rootOnly) {}

    // --- Layout ---

    @Override
    protected void init() {
        this.sidebar = this.tabs.size() > 1 && this.width >= SIDEBAR_MIN_SCREEN_WIDTH;
        this.sidebarWidth = this.sidebar ? Mth.clamp(this.width / 5, 100, 150) : 0;
        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;

        int searchWidth = Mth.clamp(this.width / 4, 90, 180);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18, Component.translatableWithFallback("glazedmenu.search", "Search")));
        // The search bar, then the light/dark switch at the right end, a hairline between them.
        this.search.setPosition(this.frameLeft() + this.frameWidth() - 6 - 20 - TOGGLE_ROOM - searchWidth, this.topBarY() + 5);
        // Only this config: searching across configs is the main screen's.
        this.search.setPlaceholder(Component.translatableWithFallback("glazedmenu.search.hint", "Search this config… (Ctrl+F)"));
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            this.rebuildEntries();
        });
        this.addModeToggleAtEnd(6).setY(this.search.getY() - 1);

        if (this.sidebar) {
            TabList tabList = this.addRenderableWidget(new TabList(this.minecraft, this.sidebarWidth - 8, bodyHeight - 8, bodyTop + 4));
            tabList.updateSizeAndPosition(this.sidebarWidth - 8, bodyHeight - 8, this.frameLeft() + 4, bodyTop + 4);
            List<TabEntry> entries = new ArrayList<>();
            for (int i = 0; i < this.tabs.size(); i++) entries.add(new TabEntry(i));
            tabList.replaceEntries(entries);
        }
        int contentX = this.contentX();
        int contentWidth = this.frameLeft() + this.frameWidth() - contentX;
        this.list = this.addRenderableWidget(new ConfigEntryList(this.minecraft, contentWidth - 8, bodyHeight - 8, bodyTop + 4));
        this.list.updateSizeAndPosition(contentWidth - 8, bodyHeight - 8, contentX + 4, bodyTop + 4);

        // The bottom bar's actions, right to left.
        int x = this.frameLeft() + this.frameWidth() - 4;
        int y = this.barWidgetY(this.bottomBarY());
        this.saveButton = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.save())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        x = place(this.saveButton, x, y);
        this.saveHereButton = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("glazedmenu.button.save_short", "Save"), 64,
                button -> this.saveHere()));
        x = place(this.saveHereButton, x, y);
        x = place(this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, 64, button -> this.onClose())), x, y);
        x -= 6;
        this.redoButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.REDO, Component.translatableWithFallback("glazedmenu.button.redo", "Redo (Ctrl+Y)"), button -> this.redo()));
        x = place(this.redoButton, x, y);
        this.undoButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.UNDO, Component.translatableWithFallback("glazedmenu.button.undo", "Undo (Ctrl+Z)"), button -> this.undo()));
        x = place(this.undoButton, x, y);
        x -= 6;
        // On narrow windows these become icons, leaving room for the status.
        boolean compact = this.width < 600;
        Component resetName = Component.translatableWithFallback("glazedmenu.button.reset", "Reset");
        Button.OnPress reset = button -> {
            this.session.resetToDefaults(this.shownValues());
            this.refreshValues();
        };
        this.resetButton = this.addRenderableWidget((compact ? FlatButton.icon(ConfigIcons.RESET, resetName, reset).style(FlatButton.Style.NORMAL)
                : FlatButton.of(resetName, 64, reset).withIcon(ConfigIcons.RESET))
                .tooltip(Component.translatableWithFallback("glazedmenu.button.reset.tooltip", "Sets the values shown here to their defaults")));
        x = place(this.resetButton, x, y);
        if (!this.session.config().presets().isEmpty()) {
            Component presetsName = Component.translatableWithFallback("glazedmenu.button.presets", "Presets");
            Button.OnPress presets = button -> this.minecraft.setScreen(new PresetScreen(this, this.session));
            x = place(this.addRenderableWidget(compact ? FlatButton.icon(ConfigIcons.LIST, presetsName, presets).style(FlatButton.Style.NORMAL)
                    : FlatButton.of(presetsName, 70, presets).withIcon(ConfigIcons.LIST)), x, y);
        }
        this.statusRight = x - 8;

        this.rebuildEntries();
        if (this.restoreScroll >= 0) {
            this.list.setScrollAmount(this.restoreScroll);
            this.restoreScroll = -1;
        }
        this.updateButtons();
    }

    private static int place(FlatButton button, int right, int y) {
        button.setPosition(right - button.getWidth(), y);
        return right - button.getWidth() - 4;
    }

    private int contentX() {
        return this.sidebar ? this.frameLeft() + this.sidebarWidth + GAP : this.frameLeft();
    }

    @Override
    protected void repositionElements() {
        if (this.list != null) this.restoreScroll = this.list.scrollAmount();
        this.rebuildWidgets();
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
        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        // The category sidebar without sun rays, so its tabs read plainly.
        if (this.sidebar) ConfigStyle.panel(graphics, this.frameLeft(), bodyTop, this.sidebarWidth, bodyHeight, false);
        ConfigStyle.panel(graphics, this.contentX(), bodyTop, this.frameLeft() + this.frameWidth() - this.contentX(), bodyHeight);

        // Title and badges, as far as the search box allows.
        int searchLeft = this.search != null ? this.search.getX() - 18 : this.width;
        ConfigIcons.Icon scopeIcon = ConfigIcons.forScope(this.session.config().scope());
        int textEnd = this.extractTitle(graphics, this.title, Math.max(40, searchLeft - this.frameLeft() - 120), (g, x, y, size) -> {
            scopeIcon.draw(g, x, y, size, ConfigStyle.accent());
            return true;
        });
        int badgeX = textEnd + 8;
        int badgeY = this.topBarY() + (BAR_HEIGHT - 11) / 2;
        for (Badge badge : this.badges()) {
            if (badgeX + this.font.width(badge.text) + 8 > searchLeft - 4) break;
            badgeX += ConfigStyle.badge(graphics, this.font, badge.text, badgeX, badgeY, badge.color) + 4;
        }
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }

        this.extractStatus(graphics);
    }

    private record Badge(Component text, int color) {}

    private List<Badge> badges() {
        List<Badge> badges = new ArrayList<>();
        ConfigScope scope = this.session.config().scope();
        badges.add(new Badge(Component.literal(scope.name()), ConfigStyle.accent()));
        switch (this.session.access()) {
            case REMOTE -> badges.add(new Badge(Component.translatableWithFallback("glazedmenu.badge.remote", "REMOTE"), ConfigStyle.colors().warning()));
            case READ_ONLY -> badges.add(new Badge(Component.translatableWithFallback("glazedmenu.badge.read_only", "READ ONLY"), ConfigStyle.colors().textDim()));
            default -> {
            }
        }
        if (this.session.config().isRestartPending()) {
            badges.add(new Badge(Component.translatableWithFallback("glazedmenu.badge.restart", "RESTART PENDING"), ConfigStyle.colors().modified()));
        }
        return badges;
    }

    // What's unsaved or wrong, at the left of the bottom bar.
    private void extractStatus(GuiGraphicsExtractor graphics) {
        int x = this.frameLeft() + 10;
        int y = this.bottomBarY() + (BAR_HEIGHT - 8) / 2;
        int errors = this.session.errors().size();
        int modified = this.session.modifiedCount();
        ConfigIcons.Icon icon;
        Component text;
        int color;
        if (errors > 0) {
            icon = ConfigIcons.WARNING;
            text = Component.translatableWithFallback("glazedmenu.status.errors", "%s invalid", errors);
            color = ConfigStyle.colors().error();
        } else if (modified > 0) {
            icon = null;
            text = Component.translatableWithFallback("glazedmenu.status.modified", "%s unsaved", modified);
            color = ConfigStyle.colors().modified();
        } else {
            icon = null;
            text = switch (this.session.access()) {
                case REMOTE -> Component.translatableWithFallback("glazedmenu.status.remote", "Editing the server's values");
                case READ_ONLY -> Component.translatableWithFallback("glazedmenu.status.read_only", "Read only");
                case UNAVAILABLE -> Component.translatableWithFallback("glazedmenu.status.unavailable", "Not available here");
                case LOCAL -> Component.translatableWithFallback("glazedmenu.status.saved", "No unsaved changes");
            };
            color = ConfigStyle.colors().textMuted();
        }
        int width = this.statusRight - x;
        if (width < 20) return;
        if (icon != null) {
            icon.draw(graphics, x, y - 1, color);
            x += ConfigIcons.SIZE + 4;
            width -= ConfigIcons.SIZE + 4;
        } else if (modified > 0) {
            ConfigStyle.rect(graphics, x, y + 1, 6, 6, color);
            x += 10;
            width -= 10;
        }
        ConfigStyle.text(graphics, this.font, text, x, y, width, color);
    }

    // --- Entries ---

    private void rebuildEntries() {
        if (this.list == null) return;
        List<ConfigEntryList.Entry> entries = new ArrayList<>();
        Config config = this.session.config();
        switch (this.session.access()) {
            case REMOTE -> entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.access.remote",
                    "Changes apply to the server"), ConfigStyle.colors().warning(), ConfigIcons.WARNING));
            case READ_ONLY -> entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.access.read_only",
                    "The server's values; you can't change them"), ConfigStyle.colors().textDim(), ConfigIcons.LOCK));
            case UNAVAILABLE -> {
                entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.access.unavailable",
                        "This config belongs to a world; open it while playing"), ConfigStyle.colors().textDim(), ConfigIcons.LOCK));
                this.list.setEntries(entries);
                return;
            }
            case LOCAL -> {
            }
        }
        if (config.scope() == ConfigScope.STARTUP) {
            entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.access.startup",
                    "Changes apply after restarting the game"), ConfigStyle.colors().warning(), ConfigIcons.RESTART));
        }
        int notices = entries.size();

        int editorWidth = Mth.clamp(this.list.getRowWidth() * 2 / 5, 90, 170);
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            config.root().values().map(value -> (ConfigValue<?>) value)
                    .filter(this.session::isVisible)
                    .filter(value -> matches(value, query))
                    .forEach(value -> entries.add(new ConfigEntryList.ValueEntry<>(this.session, value, ConfigEntryList.breadcrumb(config.root(), value), editorWidth)));
        } else if (this.sidebar) {
            Tab tab = this.tabs.get(Math.min(this.selectedTab, this.tabs.size() - 1));
            this.addGroup(entries, tab.group, 0, !tab.rootOnly, editorWidth);
        } else {
            this.addGroup(entries, config.root(), 0, true, editorWidth);
        }
        if (entries.size() == notices) {
            entries.add(new ConfigEntryList.InfoEntry(query.isEmpty()
                    ? Component.translatableWithFallback("glazedmenu.empty", "Nothing to configure here")
                    : Component.translatableWithFallback("glazedmenu.no_results", "No settings match"), ConfigStyle.colors().textDim(), ConfigIcons.SEARCH));
        }
        this.list.setEntries(entries);
    }

    // A group's settings in order, with its nested groups as indented sections.
    private void addGroup(List<ConfigEntryList.Entry> entries, ConfigGroup group, int depth, boolean subgroups, int editorWidth) {
        for (ConfigNode node : group.children()) {
            if (node instanceof ConfigValue<?> value && this.session.isVisible(value)) {
                entries.add(new ConfigEntryList.ValueEntry<>(this.session, value, value.displayName(), editorWidth));
            } else if (subgroups && node instanceof ConfigGroup subgroup && !subgroup.isHidden()
                    && subgroup.values().anyMatch(value -> this.session.isVisible(value))) {
                entries.add(new ConfigEntryList.SectionEntry(subgroup, depth));
                this.addGroup(entries, subgroup, depth + 1, true, editorWidth);
            }
        }
    }

    private static boolean matches(ConfigValue<?> value, String query) {
        return value.displayName().getString().toLowerCase(Locale.ROOT).contains(query)
                || value.path().toLowerCase(Locale.ROOT).contains(query)
                || value.comment().stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains(query));
    }

    // The values the page shows: the chosen category, or the search results.
    private List<ConfigValue<?>> shownValues() {
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        ConfigGroup root = this.session.config().root();
        if (query.isEmpty() && this.sidebar) {
            Tab tab = this.tabs.get(Math.min(this.selectedTab, this.tabs.size() - 1));
            if (tab.rootOnly) {
                return root.children().stream().filter(node -> node instanceof ConfigValue<?>)
                        .<ConfigValue<?>>map(node -> (ConfigValue<?>) node).filter(this.session::isVisible).toList();
            }
            return tab.group.values().filter(this.session::isVisible).toList();
        }
        return root.values()
                .filter(this.session::isVisible)
                .filter(value -> query.isEmpty() || matches(value, query))
                .toList();
    }

    private void selectTab(int index) {
        if (index == this.selectedTab && this.query.isEmpty()) return;
        this.selectedTab = index;
        if (!this.query.isEmpty() && this.search != null) this.search.setValue("");
        else this.rebuildEntries();
    }

    // --- Actions ---

    private void refreshValues() {
        if (this.list != null) this.list.refreshValues();
        this.updateButtons();
    }

    private void undo() {
        this.session.undo();
        this.refreshValues();
    }

    private void redo() {
        this.session.redo();
        this.refreshValues();
    }

    @Override
    public void tick() {
        super.tick();
        this.updateButtons();
    }

    private void updateButtons() {
        if (this.saveButton == null || this.saveHereButton == null || this.undoButton == null || this.redoButton == null || this.resetButton == null) return;
        Access access = this.session.access();
        boolean editable = access == Access.LOCAL || access == Access.REMOTE;
        int modified = this.session.modifiedCount();
        this.undoButton.active = editable && this.session.canUndo();
        this.redoButton.active = editable && this.session.canRedo();
        this.resetButton.active = editable;
        this.saveButton.active = !this.session.hasErrors();
        // Save works once there's something to save; neither saves invalid values.
        this.saveHereButton.active = editable && modified > 0 && !this.session.hasErrors();
        if (this.session.hasErrors()) {
            Component errors = Component.translatableWithFallback("glazedmenu.button.save.errors", "Fix the invalid values first:")
                    .append("\n").append(this.session.errors().stream().map(Component::getString).collect(Collectors.joining("\n")));
            this.saveButton.setTooltip(Tooltip.create(errors));
            this.saveHereButton.setTooltip(Tooltip.create(errors));
        } else {
            this.saveButton.setTooltip(Tooltip.create(modified > 0
                    ? Component.translatableWithFallback("glazedmenu.button.done.tooltip", "Save and close")
                    : Component.translatableWithFallback("glazedmenu.button.done.close", "Close")));
            this.saveHereButton.setTooltip(Tooltip.create(Component.translatableWithFallback("glazedmenu.button.save.tooltip", "Save (Ctrl+S)")));
        }
    }

    // Saves the changes and stays on the screen.
    private void saveHere() {
        if (this.session.hasErrors() || this.session.modifiedCount() == 0) return;
        this.session.saveAndNotify();
        this.refreshValues();
        this.updateButtons();
    }

    private void save() {
        if (this.session.hasErrors()) return;
        if (this.session.modifiedCount() == 0) {
            this.leave(this.parent);
            return;
        }
        this.leave(this.parent);
        this.session.saveAndNotify();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk()) {
            switch (event.key()) {
                case KEY_Z -> {
                    if (event.hasShiftDown()) this.redo();
                    else this.undo();
                    return true;
                }
                case KEY_Y -> {
                    this.redo();
                    return true;
                }
                case KEY_S -> {
                    if (this.saveHereButton != null && this.saveHereButton.active) this.saveHere();
                    return true;
                }
                case KEY_F -> {
                    if (this.search != null) this.setFocused(this.search);
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    public void onClose() {
        if (this.session.modifiedCount() == 0 && !this.session.hasErrors()) {
            this.leave(this.parent);
            return;
        }
        this.minecraft.setScreen(new ConfirmPopup(this, this.theme,
                Component.translatableWithFallback("glazedmenu.discard.title", "Discard changes?"),
                Component.translatableWithFallback("glazedmenu.discard.message", "%s unsaved changes will be lost.", this.session.modifiedCount()),
                Component.translatableWithFallback("glazedmenu.discard.yes", "Discard"), true,
                discard -> this.minecraft.setScreen(discard ? this.parent : this)));
    }

    /** The server's values changed while this screen was open. */
    public void onConfigSynced(Config config) {
        if (config != this.session.config()) return;
        this.session.onConfigChanged();
        this.refreshValues();
    }

    // Coming back from a list, the presets or a picker: their edits show here too.
    @Override
    public void added() {
        super.added();
        this.refreshValues();
    }

    // --- Sidebar ---

    private final class TabList extends CompatList<TabEntry> {
        TabList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 24);
        }

        @Override
        public int getRowWidth() {
            return this.width - 6;
        }

        @Override
        public int getRowLeft() {
            return this.getX();
        }

        @Override
        protected int scrollBarX() {
            return this.getRight() - 3;
        }

        @Override
        protected void extractListBackground(GuiGraphicsExtractor graphics) {}

        @Override
        protected void extractListSeparators(GuiGraphicsExtractor graphics) {}

        @Override
        protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!this.scrollable()) return;
            ConfigStyle.rect(graphics, this.scrollBarX(), this.scrollBarY(), 2, this.scrollerHeight(), ConfigStyle.withAlpha(ConfigStyle.accent(), 0x90));
        }
    }

    // A category: highlighted with an accent bar when chosen, with a count of its unsaved changes.
    private final class TabEntry extends CompatList.Entry<TabEntry> {
        private final int index;

        TabEntry(int index) {
            this.index = index;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            ConfigScreen screen = ConfigScreen.this;
            Font font = screen.font;
            Tab tab = screen.tabs.get(this.index);
            boolean selected = this.index == screen.selectedTab && screen.query.isEmpty();
            int x = this.getX();
            int y = this.getY() + 1;
            int width = this.getWidth();
            int height = this.getHeight() - 2;
            if (selected) {
                ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x2A));
                ConfigStyle.rect(graphics, x, y + 3, 2, height - 6, ConfigStyle.accent());
            } else if (hovered) {
                ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.colors().rowHover());
            }
            long modified = (tab.rootOnly
                    ? tab.group.children().stream().filter(node -> node instanceof ConfigValue<?> value && screen.session.isModified(value))
                    : tab.group.values().filter(value -> screen.session.isModified(value))).count();
            int textWidth = width - 16;
            if (modified > 0) {
                Component count = Component.literal(Long.toString(modified));
                int countWidth = font.width(count) + 6;
                ConfigStyle.rect(graphics, x + width - countWidth - 4, y + (height - 11) / 2, countWidth, 11, ConfigStyle.withAlpha(ConfigStyle.colors().modified(), 0x40));
                graphics.text(font, count, x + width - countWidth - 1, y + (height - 11) / 2 + 2, ConfigStyle.colors().modified(), false);
                textWidth -= countWidth + 4;
            }
            ConfigStyle.text(graphics, font, tab.name, x + 9, y + (height - 8) / 2, textWidth, selected ? ConfigStyle.colors().text() : hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            if (hovered && !tab.rootOnly && !tab.group.comment().isEmpty()) {
                graphics.setTooltipForNextFrame(font, font.split(Component.literal(String.join("\n", tab.group.comment())), 220), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            ConfigScreen.this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            ConfigScreen.this.selectTab(this.index);
            return true;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}
