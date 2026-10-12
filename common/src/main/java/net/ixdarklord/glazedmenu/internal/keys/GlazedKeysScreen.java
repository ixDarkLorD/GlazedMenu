package net.ixdarklord.glazedmenu.internal.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.core.Names;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.ConfirmPopup;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The key binds, in Glazed Menu's look, in place of the game's own screen: the title and search in the top bar; All,
 * Conflicts, Changed, Unbound and each category in a sidebar; the chosen one's key binds in the main panel, each with
 * its key (click it, then press the new one; Escape unbinds) and a reset; and what's in conflict with the actions in
 * the bottom bar. Narrow windows drop the sidebar and show everything. Keys are saved as the screen closes, like the
 * game's.
 * <p>
 * The search finds a key bind by its name, its key, its category or its mod; {@code @mod} lists one mod's, and
 * {@code =key} the ones bound to exactly that key (By Key types it for the key pressed next). The list is in the game's
 * order under each category's heading, or sorted by name or by key. Reset and Unbind act on the key binds shown: the
 * chosen category or filter, or what the search found.
 * <p>
 * Keys: Ctrl+F searches.
 */
public final class GlazedKeysScreen extends StyledScreen {
    private static final String VANILLA = "net.minecraft.client.gui.screens.options.controls.KeyBindsScreen";
    private static final String VANILLA_CONTROLS = "net.minecraft.client.gui.screens.options.controls.ControlsScreen";
    // SDL's codes since 26.3: a shortcut's letter is its keycode (KeyEvent.shortcutKey), any other key its scancode (KeyEvent.key).
    private static final int KEY_F = InputConstants.KEYCODE_F;
    private static final int SIDEBAR_MIN_SCREEN_WIDTH = 420;
    private static final int KEY_WIDTH = 110;
    // The order chosen last, kept while the game runs.
    private static Sort sort = Sort.CATEGORY;

    private final @Nullable Screen parent;
    private final Options options;
    // Every key bind, in the game's order: by category, then as each category lists them.
    private final List<KeyMapping> mappings;
    private final List<Tab> tabs = new ArrayList<>();
    // For each key bind, the others its key is also bound to.
    private final Map<KeyMapping, List<KeyMapping>> conflicts = new HashMap<>();
    private int selectedTab;
    private String query = "";
    private boolean sidebar;
    private int sidebarWidth;
    private double restoreScroll = -1;
    private int statusRight;
    // Set when a key changed: the rows are built again on the next tick, not while one of them handles a click.
    private boolean dirty;
    private @Nullable Rows list;
    private @Nullable StyledEditBox search;
    private @Nullable FlatButton resetButton;
    private @Nullable FlatButton unbindButton;
    private @Nullable FlatButton findKeyButton;
    // The key binds the list shows now, which Reset and Unbind act on.
    private List<KeyMapping> shown = List.of();
    // Waiting for a key to search for (By Key).
    private boolean findingKey;
    // The key bind waiting for its new key, and the modifier held so far (on loaders where a key bind can need one).
    private @Nullable KeyMapping capturing;
    private InputConstants.Key heldModifier = InputConstants.UNKNOWN;

    public GlazedKeysScreen(@Nullable Screen parent, Options options) {
        super(Component.translatable("controls.keybinds.title"), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.parent = parent;
        this.options = options;
        KeyMapping[] sorted = options.keyMappings.clone();
        Arrays.sort(sorted);
        this.mappings = List.of(sorted);
        this.tabs.add(new Tab(Kind.ALL, null));
        this.tabs.add(new Tab(Kind.CONFLICTS, null));
        this.tabs.add(new Tab(Kind.CHANGED, null));
        this.tabs.add(new Tab(Kind.UNBOUND, null));
        for (KeyMapping mapping : this.mappings) {
            if (this.tabs.stream().noneMatch(tab -> mapping.getCategory().equals(tab.category))) this.tabs.add(new Tab(Kind.CATEGORY, mapping.getCategory()));
        }
    }

    /**
     * The screen to open instead: this one for the game's own key binds screen, and the map of the keyboard
     * ({@link GameControlsScreen}) for the game's controls screen; each over the screen that opened it, and each only
     * while the player has it on.
     */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (screen == null) return null;
        Minecraft minecraft = Minecraft.getInstance();
        String name = screen.getClass().getName();
        if (name.equals(VANILLA) && GlazedSettings.replaceKeyBinds()) return new GlazedKeysScreen(minecraft.gui.screen(), minecraft.options);
        if (name.equals(VANILLA_CONTROLS) && GlazedSettings.replaceControls()) return new GameControlsScreen(minecraft.gui.screen(), minecraft.options);
        return screen;
    }

    private enum Kind {
        ALL,
        CONFLICTS,
        CHANGED,
        UNBOUND,
        CATEGORY
    }

    // How the list is ordered: the game's order under each category's heading, or one list by name or by key.
    private enum Sort {
        CATEGORY("glazedmenu.keys.sort.category", "Category"),
        NAME("glazedmenu.keys.sort.name", "Name"),
        KEY("glazedmenu.keys.sort.key", "Key");

        private final String key;
        private final String fallback;

        Sort(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        Component label() {
            return Component.translatableWithFallback("glazedmenu.keys.sort", "Sort: %s", Component.translatableWithFallback(this.key, this.fallback));
        }
    }

    private record Tab(Kind kind, @Nullable KeyMapping.Category category) {
        Component name() {
            return switch (this.kind) {
                case ALL -> Component.translatableWithFallback("glazedmenu.keys.tab.all", "All");
                case CONFLICTS -> Component.translatableWithFallback("glazedmenu.keys.tab.conflicts", "Conflicts");
                case CHANGED -> Component.translatableWithFallback("glazedmenu.keys.tab.changed", "Changed");
                case UNBOUND -> Component.translatableWithFallback("glazedmenu.keys.tab.unbound", "Unbound");
                case CATEGORY -> this.category.label();
            };
        }
    }

    // --- Layout ---

    @Override
    protected void init() {
        this.sidebar = this.width >= SIDEBAR_MIN_SCREEN_WIDTH;
        this.sidebarWidth = this.sidebar ? Math.clamp(this.width / 5, 100, 150) : 0;
        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;

        int searchWidth = Math.clamp(this.width / 4, 90, 180);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18,
                Component.translatableWithFallback("glazedmenu.keys.search", "Search key binds")));
        this.search.setPosition(this.frameLeft() + this.frameWidth() - 6 - 20 - TOGGLE_ROOM - searchWidth, this.topBarY() + 5);
        this.search.setPlaceholder(Component.translatableWithFallback("glazedmenu.keys.search.hint", "Name, key, @mod… (Ctrl+F)"));
        this.search.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatableWithFallback("glazedmenu.keys.search.tooltip",
                "Finds key binds by name, key, category or mod.\n@mod lists one mod's.\n=key lists the ones bound to exactly that key.")));
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            this.rebuildEntries();
            if (this.list != null) this.list.setScrollAmount(0);
        });
        this.addModeToggleAtEnd(6).setY(this.search.getY() - 1);
        // By Key: the next key pressed is searched for.
        Component byKey = Component.translatableWithFallback("glazedmenu.keys.find", "By Key");
        int byKeyWidth = this.font.width(byKey) + 14;
        this.findKeyButton = this.addRenderableWidget(FlatButton.of(byKey, byKeyWidth, button -> {
            this.findingKey = true;
            this.capturing = null;
        }).tooltip(Component.translatableWithFallback("glazedmenu.keys.find.tooltip", "Press a key to list the key binds on it")));
        this.findKeyButton.setHeight(18);
        this.findKeyButton.setPosition(this.search.getX() - 22 - byKeyWidth, this.search.getY());

        if (this.sidebar) {
            TabList tabList = this.addRenderableWidget(new TabList(this.minecraft, this.sidebarWidth - 8, bodyHeight - 8, bodyTop + 4));
            tabList.updateSizeAndPosition(this.sidebarWidth - 8, bodyHeight - 8, this.frameLeft() + 4, bodyTop + 4);
            List<TabEntry> entries = new ArrayList<>();
            for (int i = 0; i < this.tabs.size(); i++) entries.add(new TabEntry(i));
            tabList.replaceEntries(entries);
        }
        int contentX = this.contentX();
        int contentWidth = this.frameLeft() + this.frameWidth() - contentX;
        this.list = this.addRenderableWidget(new Rows(this.minecraft, contentWidth - 8, bodyHeight - 8, bodyTop + 4));
        this.list.updateSizeAndPosition(contentWidth - 8, bodyHeight - 8, contentX + 4, bodyTop + 4);

        int y = this.barWidgetY(this.bottomBarY());
        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.onClose())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        done.setPosition(this.frameLeft() + this.frameWidth() - 4 - 84, y);
        // Reset and Unbind act on the key binds shown; on narrow windows they're icons, leaving room for the status.
        boolean compact = this.width < 600;
        Component resetName = Component.translatableWithFallback("glazedmenu.button.reset", "Reset");
        Component resetTip = Component.translatableWithFallback("glazedmenu.keys.reset.tooltip", "Sets the key binds shown here to their defaults");
        this.resetButton = this.addRenderableWidget((compact ? FlatButton.icon(ConfigIcons.RESET, resetName, button -> this.confirmReset()).style(FlatButton.Style.NORMAL)
                : FlatButton.of(resetName, 64, button -> this.confirmReset()).withIcon(ConfigIcons.RESET)).tooltip(resetTip));
        this.resetButton.setPosition(done.getX() - 4 - this.resetButton.getWidth(), y);
        Component unbindName = Component.translatableWithFallback("glazedmenu.keys.unbind", "Unbind");
        Component unbindTip = Component.translatableWithFallback("glazedmenu.keys.unbind.tooltip", "Takes the key off every key bind shown here");
        this.unbindButton = this.addRenderableWidget((compact ? FlatButton.icon(ConfigIcons.CLOSE, unbindName, button -> this.confirmUnbind()).style(FlatButton.Style.NORMAL)
                : FlatButton.of(unbindName, 70, button -> this.confirmUnbind()).withIcon(ConfigIcons.CLOSE)).tooltip(unbindTip));
        this.unbindButton.setPosition(this.resetButton.getX() - 4 - this.unbindButton.getWidth(), y);
        int sortWidth = Arrays.stream(Sort.values()).mapToInt(order -> this.font.width(order.label())).max().orElse(60) + 12;
        FlatButton sortButton = this.addRenderableWidget(FlatButton.of(sort.label(), sortWidth, button -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            button.setMessage(sort.label());
            this.rebuildEntries();
            if (this.list != null) this.list.setScrollAmount(0);
        }).tooltip(Component.translatableWithFallback("glazedmenu.keys.sort.tooltip",
                "The game's order under each category, or one list by name or by key")));
        sortButton.setPosition(this.unbindButton.getX() - 10 - sortWidth, y);
        this.statusRight = sortButton.getX() - 8;

        this.rebuildEntries();
        if (this.restoreScroll >= 0) {
            this.list.setScrollAmount(this.restoreScroll);
            this.restoreScroll = -1;
        }
    }

    private int contentX() {
        return this.sidebar ? this.frameLeft() + this.sidebarWidth + GAP : this.frameLeft();
    }

    @Override
    protected void repositionElements() {
        if (this.list != null) this.restoreScroll = this.list.scrollAmount();
        this.rebuildWidgets();
    }

    // --- Drawing ---

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        if (this.sidebar) ConfigStyle.panel(graphics, this.frameLeft(), bodyTop, this.sidebarWidth, bodyHeight, false);
        int contentX = this.contentX();
        int contentWidth = this.frameLeft() + this.frameWidth() - contentX;
        ConfigStyle.panel(graphics, contentX, bodyTop, contentWidth, bodyHeight);
        // Glazed Menu's flowing gradient along the list's top edge, as on its mod list.
        GlazedBrand.fill(graphics, contentX + 1, bodyTop, contentWidth - 2, 2, 0xD0);

        int searchLeft = this.search != null ? this.search.getX() - 18 : this.width;
        if (this.findKeyButton != null) searchLeft = this.findKeyButton.getX();
        this.extractTitle(graphics, this.title, Math.max(40, searchLeft - this.frameLeft() - 40), (g, x, y, size) -> {
            ConfigIcons.KEYBOARD.draw(g, x, y, size, ConfigStyle.accent());
            return true;
        });
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }
        this.extractStatus(graphics);
    }

    // What needs attention, at the left of the bottom bar: the key waited for, the conflicts, or how many were changed.
    private void extractStatus(GuiGraphicsExtractor graphics) {
        int x = this.frameLeft() + 10;
        int y = this.bottomBarY() + (BAR_HEIGHT - 8) / 2;
        int width = this.statusRight - x;
        if (width < 20) return;
        long conflicting = this.conflicts.size();
        long changed = this.mappings.stream().filter(mapping -> !mapping.isDefault()).count();
        Component text;
        int color;
        if (this.findingKey) {
            text = Component.translatableWithFallback("glazedmenu.keys.status.finding", "Press the key to look for · Escape cancels");
            color = ConfigStyle.accent();
        } else if (this.capturing != null) {
            text = Component.translatableWithFallback("glazedmenu.keys.status.capturing", "Press a key for %s · Escape unbinds it", name(this.capturing));
            color = ConfigStyle.accent();
        } else if (!this.query.isBlank() || this.shown.size() != this.mappings.size()) {
            text = Component.translatableWithFallback("glazedmenu.keys.status.shown", "%s of %s key binds", this.shown.size(), this.mappings.size());
            color = ConfigStyle.colors().textMuted();
        } else if (conflicting > 0) {
            ConfigIcons.WARNING.draw(graphics, x, y - 1, ConfigStyle.colors().error());
            x += ConfigIcons.SIZE + 4;
            width -= ConfigIcons.SIZE + 4;
            text = Component.translatableWithFallback("glazedmenu.keys.status.conflicts", "%s key binds share a key", conflicting);
            color = ConfigStyle.colors().error();
        } else if (changed > 0) {
            text = Component.translatableWithFallback("glazedmenu.keys.status.changed", "%s changed from their defaults", changed);
            color = ConfigStyle.colors().modified();
        } else {
            text = Component.translatableWithFallback("glazedmenu.keys.status.count", "%s key binds", this.mappings.size());
            color = ConfigStyle.colors().textMuted();
        }
        ConfigStyle.text(graphics, this.font, text, x, y, width, color);
    }

    // --- Entries ---

    private static Component name(KeyMapping mapping) {
        return Component.translatable(mapping.getName());
    }

    // As the game counts them: two key binds on one key are in conflict once either was changed. Defaults that share a
    // key (A strafes, and reloads chunks with the debug key held) are meant to.
    private void findConflicts() {
        this.conflicts.clear();
        for (KeyMapping mapping : this.mappings) {
            if (mapping.isUnbound()) continue;
            List<KeyMapping> same = this.mappings.stream()
                    .filter(other -> other != mapping && mapping.same(other) && (!mapping.isDefault() || !other.isDefault())).toList();
            if (!same.isEmpty()) this.conflicts.put(mapping, same);
        }
    }

    private boolean inTab(KeyMapping mapping, Tab tab) {
        return switch (tab.kind) {
            case ALL -> true;
            case CONFLICTS -> this.conflicts.containsKey(mapping);
            case CHANGED -> !mapping.isDefault();
            case UNBOUND -> mapping.isUnbound();
            case CATEGORY -> mapping.getCategory().equals(tab.category);
        };
    }

    // The mod a key bind belongs to: its category's (the game's own categories are "minecraft").
    private static String modOf(KeyMapping mapping) {
        return mapping.getCategory().id().getNamespace();
    }

    // The name of the key itself, without the modifier a loader may hold with it.
    private static String keyName(KeyMapping mapping) {
        try {
            return InputConstants.getKey(mapping.saveString()).getDisplayName().getString();
        } catch (RuntimeException e) {
            return mapping.getTranslatedKeyMessage().getString();
        }
    }

    // "@mod": the mod's id or name; "=key": bound to exactly that key; otherwise anything a player might type: the key
    // bind's name, its key, its category or its mod.
    private static boolean matches(KeyMapping mapping, String query) {
        String mod = modOf(mapping);
        boolean ofMod = mod.contains(query.startsWith("@") ? query.substring(1) : query)
                || Names.modName(mod).toLowerCase(Locale.ROOT).contains(query.startsWith("@") ? query.substring(1) : query);
        if (query.startsWith("@")) return ofMod;
        if (query.startsWith("=")) return !mapping.isUnbound() && keyName(mapping).toLowerCase(Locale.ROOT).equals(query.substring(1).trim());
        return ofMod || name(mapping).getString().toLowerCase(Locale.ROOT).contains(query)
                || mapping.getTranslatedKeyMessage().getString().toLowerCase(Locale.ROOT).contains(query)
                || mapping.getCategory().label().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private long count(Tab tab) {
        return this.mappings.stream().filter(mapping -> this.inTab(mapping, tab)).count();
    }

    // The chosen tab's key binds (or, searching, every matching one), each category under its heading.
    private void rebuildEntries() {
        if (this.list == null) return;
        this.findConflicts();
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        Tab tab = this.sidebar && query.isEmpty() ? this.tabs.get(Math.min(this.selectedTab, this.tabs.size() - 1)) : this.tabs.getFirst();
        List<KeyMapping> found = new ArrayList<>(this.mappings.stream()
                .filter(mapping -> this.inTab(mapping, tab) && (query.isEmpty() || matches(mapping, query))).toList());
        // By name, or by key with the unbound ones last; the game's order otherwise (and between equals).
        if (sort == Sort.NAME) {
            found.sort(Comparator.comparing(mapping -> name(mapping).getString().toLowerCase(Locale.ROOT)));
        } else if (sort == Sort.KEY) {
            found.sort(Comparator.comparing(KeyMapping::isUnbound).thenComparing(mapping -> keyName(mapping).toLowerCase(Locale.ROOT)));
        }
        this.shown = List.copyOf(found);
        List<Row> rows = new ArrayList<>();
        KeyMapping.Category heading = null;
        for (KeyMapping mapping : found) {
            if (sort == Sort.CATEGORY && !mapping.getCategory().equals(heading)) {
                heading = mapping.getCategory();
                if (tab.kind != Kind.CATEGORY) rows.add(new Heading(heading));
            }
            // One list, without headings, says each key bind's category beside its name.
            rows.add(new KeyRow(mapping, sort != Sort.CATEGORY && tab.kind != Kind.CATEGORY));
        }
        if (rows.isEmpty()) {
            rows.add(new Notice(!query.isEmpty() ? Component.translatableWithFallback("glazedmenu.keys.none.search", "No key binds match")
                    : switch (tab.kind) {
                        case CONFLICTS -> Component.translatableWithFallback("glazedmenu.keys.none.conflicts", "No key binds share a key");
                        case UNBOUND -> Component.translatableWithFallback("glazedmenu.keys.none.unbound", "Every key bind has a key");
                        default -> Component.translatableWithFallback("glazedmenu.keys.none.changed", "Every key bind is at its default");
                    }));
        }
        double scroll = this.list.scrollAmount();
        this.list.setRows(rows);
        this.list.setScrollAmount(scroll);
    }

    private void selectTab(int index) {
        if (index == this.selectedTab && this.query.isEmpty()) return;
        this.selectedTab = index;
        if (!this.query.isEmpty() && this.search != null) this.search.setValue("");
        else this.rebuildEntries();
        if (this.list != null) this.list.setScrollAmount(0);
    }

    // --- Changing keys ---

    @Override
    public void tick() {
        super.tick();
        if (this.dirty) {
            this.dirty = false;
            this.rebuildEntries();
        }
        if (this.resetButton != null) this.resetButton.active = this.shown.stream().anyMatch(mapping -> !mapping.isDefault());
        if (this.unbindButton != null) this.unbindButton.active = this.shown.stream().anyMatch(mapping -> !mapping.isUnbound());
        if (this.findKeyButton != null) this.findKeyButton.style(this.findingKey ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL);
    }

    // The keys changed: the game looks its key binds up by key again, and the rows show what's in conflict now.
    private void keysChanged() {
        KeyMapping.resetMapping();
        this.dirty = true;
    }

    private void bind(InputConstants.Key modifier, InputConstants.Key key) {
        if (this.capturing == null) return;
        GlazedPlatform.get().bindKey(this.capturing, modifier, key);
        this.capturing = null;
        this.heldModifier = InputConstants.UNKNOWN;
        this.keysChanged();
    }

    // Reset and Unbind act on the key binds shown when they were pressed, once the player agrees.
    private void confirmReset() {
        List<KeyMapping> keys = this.shown.stream().filter(mapping -> !mapping.isDefault()).toList();
        if (keys.isEmpty()) return;
        this.minecraft.gui.setScreen(new ConfirmPopup(this, this.theme,
                Component.translatableWithFallback("glazedmenu.keys.reset.title", "Reset %s key binds?", keys.size()),
                Component.translatableWithFallback("glazedmenu.keys.reset.message", "The changed key binds shown here go back to their defaults."),
                Component.translatableWithFallback("glazedmenu.button.reset", "Reset"), true, reset -> {
                    if (reset) {
                        keys.forEach(GlazedPlatform.get()::resetKey);
                        this.keysChanged();
                    }
                    this.minecraft.gui.setScreen(this);
                }));
    }

    private void confirmUnbind() {
        List<KeyMapping> keys = this.shown.stream().filter(mapping -> !mapping.isUnbound()).toList();
        if (keys.isEmpty()) return;
        this.minecraft.gui.setScreen(new ConfirmPopup(this, this.theme,
                Component.translatableWithFallback("glazedmenu.keys.unbind.title", "Unbind %s key binds?", keys.size()),
                Component.translatableWithFallback("glazedmenu.keys.unbind.message", "The key binds shown here will have no key, until you give them one or reset them."),
                Component.translatableWithFallback("glazedmenu.keys.unbind", "Unbind"), true, unbind -> {
                    if (unbind) {
                        keys.forEach(mapping -> GlazedPlatform.get().bindKey(mapping, InputConstants.UNKNOWN, InputConstants.UNKNOWN));
                        this.keysChanged();
                    }
                    this.minecraft.gui.setScreen(this);
                }));
    }

    // By Key found its key: the search lists what's bound to it.
    private void findKey(InputConstants.Key key) {
        this.findingKey = false;
        if (this.search != null) this.search.setValue("=" + key.getDisplayName().getString());
    }

    // While a key bind waits for its key, a mouse button is a key like any other.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.findingKey) {
            this.findKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        if (this.capturing != null) {
            this.bind(this.heldModifier, InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.findingKey) {
            if (event.isEscape()) this.findingKey = false;
            else this.findKey(InputConstants.getKey(event));
            return true;
        }
        if (this.capturing != null) {
            InputConstants.Key key = InputConstants.getKey(event);
            if (event.isEscape()) {
                this.bind(InputConstants.UNKNOWN, InputConstants.UNKNOWN);
            } else if (GlazedPlatform.get().isKeyModifier(key) && this.heldModifier == InputConstants.UNKNOWN) {
                // Held for the key that follows; on its own once it's released.
                this.heldModifier = key;
            } else {
                this.bind(this.heldModifier, key);
            }
            return true;
        }
        if (event.hasControlDownWithQuirk() && event.shortcutKey() == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (this.capturing != null && this.heldModifier != InputConstants.UNKNOWN) {
            this.bind(InputConstants.UNKNOWN, this.heldModifier);
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    public void onClose() {
        this.leave(this.parent);
    }

    @Override
    public void removed() {
        this.options.save();
        super.removed();
    }

    // --- The key binds ---

    private static final class Rows extends ContainerObjectSelectionList<Row> {
        Rows(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 26);
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

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        abstract int height();

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    // A category's name in the accent, then a rule to the edge.
    private final class Heading extends Row {
        private final Component name;

        // A mod's category says whose it is, unless its name already does.
        Heading(KeyMapping.Category category) {
            String mod = category.id().getNamespace().equals("minecraft") ? "" : Names.modName(category.id().getNamespace());
            net.minecraft.network.chat.MutableComponent name = category.label().copy().withStyle(ChatFormatting.BOLD);
            if (!mod.isEmpty() && !name.getString().toLowerCase(Locale.ROOT).contains(mod.toLowerCase(Locale.ROOT))) {
                name.append(Component.literal(" · " + mod).withStyle(style -> style.withBold(false).withColor(ChatFormatting.GRAY)));
            }
            this.name = name;
        }

        @Override
        int height() {
            return 24;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GlazedKeysScreen.this.font;
            int x = this.getX() + 6;
            int y = this.getY() + this.getHeight() - 12;
            int accent = ConfigStyle.accent();
            int textWidth = Math.min(font.width(this.name), this.getWidth() - 40);
            ConfigIcons.CHEVRON.draw(graphics, x, y - 1, accent);
            ConfigStyle.text(graphics, font, this.name, x + 12, y, textWidth, accent);
            graphics.fill(x + 12 + textWidth + 6, y + 4, this.getX() + this.getWidth() - 4, y + 5, ConfigStyle.withAlpha(accent, 0x40));
        }
    }

    // One key bind: its name (and what else uses its key) on the left, its key and a reset button on the right.
    private final class KeyRow extends Row {
        private final KeyMapping mapping;
        private final Component name;
        private final @Nullable Component shared;
        // The key bind's category, said after its name where no heading says it.
        private final @Nullable Component where;
        private final FlatButton key;
        private final FlatButton reset;

        KeyRow(KeyMapping mapping, boolean sayCategory) {
            this.mapping = mapping;
            this.name = name(mapping);
            this.where = sayCategory ? mapping.getCategory().label() : null;
            List<KeyMapping> same = GlazedKeysScreen.this.conflicts.get(mapping);
            this.shared = same == null ? null : Component.translatableWithFallback("glazedmenu.keys.shared", "Also: %s",
                    same.stream().map(other -> name(other).getString()).collect(Collectors.joining(", ")));
            this.key = FlatButton.of(mapping.getTranslatedKeyMessage(), KEY_WIDTH, button -> {
                GlazedKeysScreen.this.capturing = mapping;
                GlazedKeysScreen.this.heldModifier = InputConstants.UNKNOWN;
            });
            this.reset = FlatButton.icon(ConfigIcons.RESET, Component.translatable("controls.reset"), button -> {
                GlazedPlatform.get().resetKey(mapping);
                GlazedKeysScreen.this.keysChanged();
            });
        }

        @Override
        int height() {
            return this.shared != null ? 34 : 26;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GlazedKeysScreen.this.font;
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            boolean waiting = GlazedKeysScreen.this.capturing == this.mapping;
            boolean changed = !this.mapping.isDefault();

            if (hovered || waiting) ConfigStyle.rect(graphics, x, y + 1, width, height - 2, ConfigStyle.colors().rowHover());
            // A mark in the margin for a key in conflict or changed from its default, as the config screens mark values.
            if (this.shared != null || changed) {
                ConfigStyle.rect(graphics, x, y + 4, 2, height - 8, this.shared != null ? ConfigStyle.colors().error() : ConfigStyle.colors().modified());
            }

            this.reset.active = changed;
            this.reset.setPosition(x + width - 22, y + (height - 20) / 2);
            this.key.setPosition(this.reset.getX() - 4 - KEY_WIDTH, y + (height - 20) / 2);
            this.key.setMessage(waiting ? Component.literal("> ").append(this.mapping.getTranslatedKeyMessage()).append(" <") : this.mapping.getTranslatedKeyMessage());
            this.key.style(waiting ? FlatButton.Style.PRIMARY : this.shared != null ? FlatButton.Style.DANGER : FlatButton.Style.NORMAL);
            this.key.extractRenderState(graphics, mouseX, mouseY, a);
            this.reset.extractRenderState(graphics, mouseX, mouseY, a);

            int textX = x + 10;
            int textWidth = this.key.getX() - textX - 8;
            int nameY = this.shared != null ? y + height / 2 - 10 : y + (height - 8) / 2;
            ConfigStyle.text(graphics, font, this.name, textX, nameY, textWidth,
                    this.mapping.isUnbound() ? ConfigStyle.colors().textDim() : changed ? ConfigStyle.colors().modified() : ConfigStyle.colors().text());
            int nameEnd = textX + font.width(this.name) + 8;
            if (this.where != null && nameEnd + 30 < textX + textWidth) {
                ConfigStyle.text(graphics, font, this.where, nameEnd, nameY, textX + textWidth - nameEnd, ConfigStyle.colors().textMuted());
            }
            if (this.shared != null) ConfigStyle.text(graphics, font, this.shared, textX, nameY + 12, textWidth, ConfigStyle.colors().error());
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.key, this.reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.key, this.reset);
        }
    }

    // Why the list is empty.
    private final class Notice extends Row {
        private final Component text;

        Notice(Component text) {
            this.text = text;
        }

        @Override
        int height() {
            return 30;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GlazedKeysScreen.this.font;
            int y = this.getY() + (this.getHeight() - 8) / 2;
            int textWidth = font.width(this.text) + 14;
            int x = this.getX() + (this.getWidth() - textWidth) / 2;
            ConfigIcons.SEARCH.draw(graphics, x, y - 1, ConfigStyle.colors().textMuted());
            graphics.text(font, this.text, x + 14, y, ConfigStyle.colors().textMuted(), false);
        }
    }

    // --- Sidebar ---

    private static final class TabList extends ContainerObjectSelectionList<TabEntry> {
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

    // A tab: highlighted with an accent bar when chosen, with how many key binds it holds (in red for the conflicts).
    private final class TabEntry extends ContainerObjectSelectionList.Entry<TabEntry> {
        private final int index;

        TabEntry(int index) {
            this.index = index;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GlazedKeysScreen screen = GlazedKeysScreen.this;
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
            long count = screen.count(tab);
            int countColor = count == 0 ? ConfigStyle.colors().textMuted() : switch (tab.kind) {
                case CONFLICTS -> ConfigStyle.colors().error();
                case CHANGED -> ConfigStyle.colors().modified();
                case UNBOUND -> ConfigStyle.colors().warning();
                default -> ConfigStyle.colors().textDim();
            };
            Component number = Component.literal(Long.toString(count));
            int countWidth = font.width(number) + 6;
            ConfigStyle.rect(graphics, x + width - countWidth - 4, y + (height - 11) / 2, countWidth, 11, ConfigStyle.withAlpha(countColor, 0x30));
            graphics.text(font, number, x + width - countWidth - 1, y + (height - 11) / 2 + 2, countColor, false);
            ConfigStyle.text(graphics, font, tab.name(), x + 9, y + (height - 8) / 2, width - 20 - countWidth,
                    selected || hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            // A rule under Unbound, between the filters and the categories.
            if (tab.kind == Kind.UNBOUND) graphics.fill(x + 6, y + height + 1, x + width - 6, y + height + 2, ConfigStyle.colors().panelBorder());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GlazedKeysScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            GlazedKeysScreen.this.selectTab(this.index);
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
