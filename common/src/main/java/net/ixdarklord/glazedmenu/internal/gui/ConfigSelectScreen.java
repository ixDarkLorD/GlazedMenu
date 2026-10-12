package net.ixdarklord.glazedmenu.internal.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.glazedmenu.internal.config.ConfigProfiles;
import net.ixdarklord.glazedmenu.internal.core.GlazedClient;
import net.ixdarklord.glazedmenu.internal.core.Names;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.ixdarklord.glazedmenu.api.config.ConfigGroup;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.ConfirmPopup;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.gui.style.ModIcons;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.gui.style.ModColors;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.ixdarklord.glazedmenu.internal.style.ThemeEffects;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2fStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The main config screen: one mod's configs, or every mod's, as cards grouped by mod. Its search bar is the only one
 * that searches across configs: results are listed by config, each config a heading over its matching settings, which
 * are edited right there (Save or Done applies them, to every config at once); a heading opens its config filtered the
 * same way. (A config's own search only searches that config.)
 * <p>
 * Changed lists, the same way, every setting that isn't at its default (the search narrowing it): what the player has
 * changed, across every mod. Copy puts that list on the clipboard as text, and Reset sets the settings shown back to
 * their defaults.
 * <p>
 * Profiles (on the list of every mod's) are named sets of settings across mods: one is saved from the settings the list
 * shows, and using one puts its settings in as unsaved changes, listed to look over and save.
 * <p>
 * Keys: Ctrl+F searches, Ctrl+S saves.
 */
public final class ConfigSelectScreen extends StyledScreen {
    // SDL's codes since 26.3: a shortcut's letter is its keycode (KeyEvent.shortcutKey), any other key its scancode (KeyEvent.key).
    private static final int KEY_F = InputConstants.KEYCODE_F;
    private static final int KEY_S = 's';
    // A taller header than the other screens, so the mod's icon is large enough to recognize.
    private static final int HEADER_HEIGHT = 46;
    private static final int HEADER_ICON = 32;
    private static final Identifier MOD_LIST_ICON = GlazedMenu.rl("textures/gui/mod_list.png");
    // Cards are portrait, 1:1.3, sized to fit: as wide as a row allows up to the maximum, and never taller than the list.
    private static final int TILE_MIN_WIDTH = 84;
    private static final int TILE_MAX_WIDTH = 116;
    // The largest a card grows when a mod has only a few.
    private static final int MAX_FIT_WIDTH = 220;
    private static final float TILE_ASPECT = 1.3F;
    private static final int TILE_MIN_FIT_WIDTH = 70;
    private static final int TILE_GAP = 12;
    private static final int TOOLTIP_WIDTH = 220;
    // Room above and below a row of cards for a hovered one to grow into.
    private static final int TILE_ROW_PADDING = 6;
    // The artwork's opacity at rest and hovered: faint, so the card's label and icon lead.
    private static final float ART_OPACITY = 0.4F;
    private static final float ART_OPACITY_HOVERED = 0.65F;
    // The frame around a card's artwork, on all four sides.
    private static final int TILE_PADDING = 4;
    // How much a hovered tile grows, and how quickly (the time to get about two thirds of the way).
    private static final float TILE_GROW = 0.07F;
    private static final float TILE_EASE_MILLIS = 70;
    // The list of every mod's configs: cards at least this wide, this far apart; a header, then a line per config.
    private static final int CARD_MIN_WIDTH = 170;
    private static final int CARD_GAP = 8;
    private static final int CARD_HEADER = 28;
    private static final int CARD_LINE = 18;
    // Search results: no wider than reads well, and no more rows than a search needs (each has an editor to build).
    private static final int RESULTS_MAX_WIDTH = 560;
    private static final int MAX_RESULTS = 150;

    private final @Nullable Screen parent;
    private final @Nullable String modId;
    // The cards, and the search results in their place while there's a search.
    private @Nullable Rows rows;
    private @Nullable ConfigEntryList results;
    private @Nullable StyledEditBox search;
    private @Nullable FlatButton doneButton;
    private @Nullable FlatButton saveButton;
    // The edits made in the search results, by config; they outlast the search that found them.
    private final Map<Identifier, ConfigEditSession> sessions = new LinkedHashMap<>();
    private String query = "";
    private boolean focusSearch;
    // Listing only the settings that aren't at their defaults.
    private boolean changedOnly;
    private @Nullable FlatButton changedButton;
    private @Nullable FlatButton profilesButton;
    private @Nullable FlatButton copyButton;
    private @Nullable FlatButton resetButton;
    // The settings the list shows now, by the session editing them, for Copy and Reset.
    private final Map<ConfigEditSession, List<ConfigValue<?>>> listed = new LinkedHashMap<>();
    private double restoreScroll = -1;
    private int resultCount;
    private int resultConfigs;

    public ConfigSelectScreen(@Nullable Screen parent, @Nullable String modId) {
        super(modId == null
                ? Component.translatableWithFallback("glazedmenu.select.all", "Mod Configs")
                : Component.translatableWithFallback("glazedmenu.select.mod", "%s Configs", modName(modId)),
                // The list of every mod's in Glazed Menu's own colors, like its mod list.
                modId == null ? ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME) : ThemeResources.resolve(modId, ConfigTheme.forMod(modId)));
        this.parent = parent;
        this.modId = modId;
    }

    /** Every mod's configs with the search bar ready, searched for {@code query} when it isn't empty. */
    public static ConfigSelectScreen search(@Nullable Screen parent, String query) {
        ConfigSelectScreen screen = new ConfigSelectScreen(parent, null);
        screen.query = query;
        screen.focusSearch = true;
        return screen;
    }

    /** One mod's configs with the search bar ready, searched for {@code query} when it isn't empty. */
    public static ConfigSelectScreen search(@Nullable Screen parent, String modId, String query) {
        ConfigSelectScreen screen = new ConfigSelectScreen(parent, modId);
        screen.query = query;
        screen.focusSearch = true;
        return screen;
    }

    /** Every mod's configs, showing the settings that aren't at their defaults. */
    public static ConfigSelectScreen changed(@Nullable Screen parent) {
        ConfigSelectScreen screen = new ConfigSelectScreen(parent, null);
        screen.changedOnly = true;
        return screen;
    }

    @Override
    protected void init() {
        // Narrower on small windows, where the title, Profiles and Changed need the room.
        int searchWidth = Math.clamp(this.width / 4, 90, 200);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18,
                Component.translatableWithFallback("glazedmenu.search.all", "Search all configs")));
        // The search bar at the right, and the light/dark switch after it.
        this.search.setPosition(this.frameLeft() + this.frameWidth() - 8 - 20 - TOGGLE_ROOM - searchWidth, this.topBarWidgetY(18));
        this.search.setPlaceholder(Component.translatableWithFallback("glazedmenu.search.all.hint", "Search all configs… (Ctrl+F)"));
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            this.rebuildRows();
        });
        this.addModeToggleAtEnd(8).setY(this.search.getY() - 1);
        // Changed, before the search: what isn't at its default.
        Component changed = Component.translatableWithFallback("glazedmenu.changed", "Changed");
        int changedWidth = this.font.width(changed) + 14;
        this.changedButton = this.addRenderableWidget(FlatButton.of(changed, changedWidth, button -> {
            this.changedOnly = !this.changedOnly;
            this.rebuildRows();
            this.updateButtons();
        }).tooltip(Component.translatableWithFallback("glazedmenu.changed.tooltip", "Lists every setting that isn't at its default")));
        this.changedButton.setHeight(18);
        this.changedButton.setPosition(this.search.getX() - 22 - changedWidth, this.search.getY());
        // Profiles span mods, so only the list of every mod's has them.
        this.profilesButton = null;
        if (this.modId == null) {
            Component profiles = Component.translatableWithFallback("glazedmenu.profiles.title", "Profiles");
            int profilesWidth = this.font.width(profiles) + 14;
            this.profilesButton = this.addRenderableWidget(FlatButton.of(profiles, profilesWidth, button -> this.minecraft.gui.setScreen(
                    new ProfilesPopup(this, this.theme, this.listed.values().stream().flatMap(List::stream).toList(), this::useProfile)))
                    .tooltip(Component.translatableWithFallback("glazedmenu.profiles.tooltip", "Named sets of settings across mods, to save and switch between")));
            this.profilesButton.setHeight(18);
            this.profilesButton.setPosition(this.changedButton.getX() - 4 - profilesWidth, this.search.getY());
        }

        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        this.rows = new Rows(this.minecraft, this.frameWidth() - 8, bodyHeight - 8, bodyTop + 4);
        this.rows.updateSizeAndPosition(this.frameWidth() - 8, bodyHeight - 8, this.frameLeft() + 4, bodyTop + 4);
        int resultsWidth = Math.min(this.frameWidth() - 8, RESULTS_MAX_WIDTH);
        this.results = new ConfigEntryList(this.minecraft, resultsWidth, bodyHeight - 8, bodyTop + 4);
        this.results.updateSizeAndPosition(resultsWidth, bodyHeight - 8, this.frameLeft() + (this.frameWidth() - resultsWidth) / 2, bodyTop + 4);
        // Done saves what the search results changed and closes; Save (shown once something changed) saves and stays.
        this.doneButton = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 80, button -> this.done()).style(FlatButton.Style.PRIMARY));
        this.doneButton.setPosition(this.frameLeft() + this.frameWidth() - 84, this.barWidgetY(this.bottomBarY()));
        this.saveButton = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("glazedmenu.button.save_short", "Save"), 64,
                button -> this.saveHere()));
        this.saveButton.setPosition(this.doneButton.getX() - 4 - 64, this.doneButton.getY());
        // With Changed on: the list as text for the clipboard, and the settings shown back to their defaults.
        this.resetButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.RESET, Component.translatableWithFallback("glazedmenu.button.reset.tooltip",
                "Sets the values shown here to their defaults"), button -> {
            this.listed.forEach(ConfigEditSession::resetToDefaults);
            if (this.results != null) this.results.refreshValues();
            this.updateButtons();
        }).style(FlatButton.Style.NORMAL));
        this.resetButton.setPosition(this.saveButton.getX() - 4 - 20, this.doneButton.getY());
        Component copy = Component.translatableWithFallback("glazedmenu.changed.copy", "Copy");
        int copyWidth = this.font.width(copy) + 14;
        this.copyButton = this.addRenderableWidget(FlatButton.of(copy, copyWidth, button -> this.copyListed())
                .tooltip(Component.translatableWithFallback("glazedmenu.changed.copy.tooltip", "Copies the settings shown here as text, to share")));
        this.copyButton.setPosition(this.resetButton.getX() - 4 - copyWidth, this.doneButton.getY());
        this.rebuildRows();
        if (this.restoreScroll >= 0) {
            if (!this.query.isBlank() || this.changedOnly) this.results.setScrollAmount(this.restoreScroll);
            this.restoreScroll = -1;
        }
        this.updateButtons();
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    protected int topBarHeight() {
        return HEADER_HEIGHT;
    }

    @Override
    protected void setInitialFocus() {
        if (this.search != null && (this.focusSearch || !this.query.isEmpty())) this.setInitialFocus(this.search);
        else super.setInitialFocus();
    }

    private List<Config> configs() {
        return this.modId == null ? ConfigSources.all() : ConfigSources.forMod(this.modId);
    }

    private void rebuildRows() {
        if (this.rows == null || this.results == null) return;
        List<Row> entries = new ArrayList<>();
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        this.resultCount = 0;
        this.resultConfigs = 0;
        this.listed.clear();
        this.showResults(!query.isEmpty() || this.changedOnly);
        if (!query.isEmpty() || this.changedOnly) {
            this.results.setEntries(this.findSettings(query));
            return;
        }
        {
            // Each mod's configs as tiles, a few to a row; the list of every mod heads each mod's tiles with its name.
            Map<String, List<Config>> byMod = new LinkedHashMap<>();
            for (Config config : this.configs()) byMod.computeIfAbsent(config.modId(), id -> new ArrayList<>()).add(config);
            int rowWidth = this.rows.getRowWidth();
            int columns = Math.max(1, (rowWidth + TILE_GAP) / (TILE_MIN_WIDTH + TILE_GAP));
            int cardWidth = Math.min(TILE_MAX_WIDTH, (rowWidth - TILE_GAP * (columns - 1)) / columns);
            int cardHeight = Math.round(cardWidth * TILE_ASPECT);
            // Short enough for a row to fit whole, under its mod's name on the list of every mod.
            int maxHeight = this.rows.getHeight() - TILE_ROW_PADDING * 2 - 8 - (this.modId == null ? 22 : 0);
            if (cardHeight > maxHeight) {
                // Never so small the names don't fit; the list scrolls instead.
                cardWidth = Math.max(TILE_MIN_FIT_WIDTH, Math.round(maxHeight / TILE_ASPECT));
                cardHeight = Math.round(cardWidth * TILE_ASPECT);
            }
            int width = cardWidth;
            int height = cardHeight;
            if (this.modId == null) {
                this.buildModCards(byMod, entries);
                this.rows.setRows(entries);
                return;
            }
            // One mod's configs: cards as large as the panel allows, in the arrangement (columns x rows) that fits them
            // biggest, so a mod with one or two configs doesn't leave the panel mostly empty.
            List<Tile> tiles = byMod.values().stream().flatMap(List::stream).map(Tile::new).toList();
            int[] size = this.fitCards(tiles.size());
            int fitColumns = size[2];
            for (int i = 0; i < tiles.size(); i += fitColumns) {
                entries.add(new TileRow(tiles.subList(i, Math.min(tiles.size(), i + fitColumns)), size[0], size[1]));
            }
            // Mods whose configs Glazed Menu can't read, but which have a screen of their own: a card opening it.
            if (this.modId == null) {
                for (String mod : ConfigSources.nativeOnlyModIds()) {
                    UnaryOperator<Screen> screen = ConfigSources.nativeScreen(mod);
                    if (screen == null) continue;
                    entries.add(new ModHeading(mod));
                    entries.add(new TileRow(List.of(new Tile(mod, screen)), width, height));
                }
            }
            int used = entries.stream().mapToInt(Row::height).sum();
            int free = this.rows.getHeight() - used - 8;
            if (free > 1) entries.addFirst(new Spacer(free / 2));
        }
        this.rows.setRows(entries);
    }

    // The cards and the search results share the body; only the one in use is on the screen.
    private void showResults(boolean searching) {
        if (this.rows == null || this.results == null) return;
        this.removeWidget(searching ? this.rows : this.results);
        if (searching) {
            if (!this.children().contains(this.results)) this.addRenderableWidget(this.results);
        } else if (!this.children().contains(this.rows)) {
            this.addRenderableWidget(this.rows);
        }
    }

    // Each config with matches is a heading (opening it filtered the same way), then its matching settings, edited in
    // place; with Changed on, only the settings that aren't at their defaults (or were just edited here). Configs keep the edits made here while the search changes; one nothing was changed in is read afresh.
    private List<ConfigEntryList.Entry> findSettings(String query) {
        this.sessions.values().forEach(ConfigEditSession::onConfigChanged);
        this.sessions.values().removeIf(session -> session.modifiedCount() == 0 && !session.hasErrors());
        List<ConfigEntryList.Entry> entries = new ArrayList<>();
        int editorWidth = Math.clamp(this.results.getRowWidth() * 2 / 5, 90, 170);
        int shown = 0;
        for (Config listed : this.configs()) {
            ConfigEditSession session = this.sessions.containsKey(listed.id()) ? this.sessions.get(listed.id()) : new ConfigEditSession(listed);
            if (session.access() == Access.UNAVAILABLE) continue;
            Config config = session.config();
            List<ConfigValue<?>> found = config.values().filter(value -> session.isVisible(value)
                    && (query.isEmpty() || ConfigEntryList.matches(value, query))
                    && (!this.changedOnly || !session.isDefault(value) || session.isModified(value))).toList();
            if (found.isEmpty()) continue;
            this.resultCount += found.size();
            this.resultConfigs++;
            if (shown >= MAX_RESULTS) continue;
            this.sessions.put(config.id(), session);
            MutableComponent title = config.title().copy().withStyle(ChatFormatting.BOLD);
            if (this.modId == null) title = Component.literal(modName(config.modId()) + " › ").withStyle(ChatFormatting.GRAY).append(title);
            entries.add(new ConfigEntryList.LinkEntry(ConfigIcons.forScope(config.scope()), title,
                    Component.translatableWithFallback("glazedmenu.search.count", "%s found", found.size()), themeOf(config),
                    () -> this.leave(ConfigScreen.create(this, config, this.query.trim()))));
            List<ConfigValue<?>> rows = found.subList(0, Math.min(found.size(), MAX_RESULTS - shown));
            this.listed.put(session, rows);
            for (ConfigValue<?> value : rows) {
                entries.add(new ConfigEntryList.ValueEntry<>(session, value, ConfigEntryList.breadcrumb(config.root(), value), editorWidth));
                shown++;
            }
        }
        if (entries.isEmpty()) {
            entries.add(new ConfigEntryList.InfoEntry(this.changedOnly && query.isEmpty()
                    ? Component.translatableWithFallback("glazedmenu.changed.none", "Every setting is at its default")
                    : Component.translatableWithFallback("glazedmenu.no_results", "No settings match"), ConfigStyle.colors().textDim(), ConfigIcons.SEARCH));
        } else if (shown < this.resultCount) {
            entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.search.more",
                    "Showing the first %s of %s; type more to narrow it down", shown, this.resultCount), ConfigStyle.colors().textDim(), ConfigIcons.SEARCH));
        }
        return entries;
    }

    // The biggest card size (width, height, columns) for this many cards in the panel: every arrangement of columns is
    // tried, each card portrait (1:1.3), the whole no taller than most of the panel and no wider than the row; never
    // smaller than the usual cards, the list scrolling instead.
    private int[] fitCards(int count) {
        int rowWidth = this.rows.getRowWidth();
        int room = Math.round((this.rows.getHeight() - 8) * 0.85F);
        int[] best = {TILE_MIN_WIDTH, Math.round(TILE_MIN_WIDTH * TILE_ASPECT), Math.max(1, (rowWidth + TILE_GAP) / (TILE_MIN_WIDTH + TILE_GAP))};
        for (int columns = 1; columns <= Math.max(1, count); columns++) {
            int rowCount = (count + columns - 1) / columns;
            int byWidth = (rowWidth - TILE_GAP * (columns - 1)) / columns;
            int byHeight = Math.round((room / (float) rowCount - TILE_ROW_PADDING * 2) / TILE_ASPECT);
            int width = Math.min(Math.min(byWidth, byHeight), MAX_FIT_WIDTH);
            if (width > best[0]) best = new int[]{width, Math.round(width * TILE_ASPECT), columns};
        }
        return best;
    }

    // The list of every mod's: a card per mod (its icon, name and how many configs, then a line per config), in as many
    // columns as fit, each card under the shortest column so far, leaving no gaps. Mods Glazed Menu can't read but which have a screen of their own get a card
    // with a line opening it.
    private void buildModCards(Map<String, List<Config>> byMod, List<Row> entries) {
        List<ModCard> cards = new ArrayList<>();
        byMod.forEach((mod, configs) -> cards.add(new ModCard(mod, configs.stream().map(Tile::new).toList())));
        for (String mod : ConfigSources.nativeOnlyModIds()) {
            UnaryOperator<Screen> screen = ConfigSources.nativeScreen(mod);
            if (screen != null) cards.add(new ModCard(mod, List.of(new Tile(mod, screen))));
        }
        int rowWidth = this.rows.getRowWidth();
        int columns = Math.clamp((rowWidth + CARD_GAP) / (CARD_MIN_WIDTH + CARD_GAP), 1, 4);
        entries.add(new CardRow(cards, columns));
    }

    @Override
    protected void repositionElements() {
        if (this.results != null) this.restoreScroll = this.results.scrollAmount();
        this.rebuildWidgets();
    }

    // --- Saving what the search results changed ---

    private int modifiedCount() {
        return this.sessions.values().stream().mapToInt(ConfigEditSession::modifiedCount).sum();
    }

    private List<Component> errors() {
        return this.sessions.values().stream().flatMap(session -> session.errors().stream()).toList();
    }

    @Override
    public void tick() {
        super.tick();
        this.updateButtons();
    }

    // A profile's settings become unsaved changes here, and Changed lists them (among what else isn't at its default)
    // to look over before saving.
    private void useProfile(String name) {
        int set = 0;
        for (Map.Entry<Config, Map<ConfigValue<?>, Object>> entry : ConfigProfiles.resolve(name).entrySet()) {
            Identifier id = entry.getKey().id();
            ConfigEditSession session = this.sessions.containsKey(id) ? this.sessions.get(id) : new ConfigEditSession(entry.getKey());
            set += putIn(session, entry.getValue());
            if (session.modifiedCount() > 0) this.sessions.put(id, session);
        }
        this.changedOnly = true;
        this.rebuildRows();
        this.updateButtons();
        GlazedClient.toast(Component.literal(name), set > 0
                ? Component.translatableWithFallback("glazedmenu.profiles.used", "%s settings set; save to apply them", set)
                : Component.translatableWithFallback("glazedmenu.profiles.same", "Your settings already match it"));
    }

    // Sets a profile's values for one config as that session's pending changes; returns how many differ from now.
    private static int putIn(ConfigEditSession session, Map<ConfigValue<?>, Object> values) {
        Map<ConfigValue<?>, Object> editable = new LinkedHashMap<>();
        values.forEach((value, wanted) -> {
            // The session may hold another reading of the same config: its own value at that path is the one to set.
            ConfigValue<?> own = value.config() == session.config() ? value : session.config().find(value.path()).orElse(null);
            if (own != null && session.isVisible(own) && session.isEditable(own)) editable.put(own, wanted);
        });
        session.setAll(editable);
        return (int) editable.keySet().stream().filter(session::isModified).count();
    }

    /**
     * Uses a profile at once, without a screen: sets its settings and saves them, then tells the player.
     *
     * @return how many settings changed, or -1 when there's no profile of that name
     */
    public static int useProfileNow(String name) {
        if (!ConfigProfiles.names().contains(name)) return -1;
        int set = 0;
        RestartRequirement restart = RestartRequirement.NONE;
        for (Map.Entry<Config, Map<ConfigValue<?>, Object>> entry : ConfigProfiles.resolve(name).entrySet()) {
            ConfigEditSession session = new ConfigEditSession(entry.getKey());
            if (session.access() == Access.UNAVAILABLE) continue;
            set += putIn(session, entry.getValue());
            if (session.modifiedCount() > 0 && !session.hasErrors()) restart = restart.max(session.save());
        }
        GlazedClient.toast(Component.literal(name), switch (restart) {
            case GAME -> Component.translatableWithFallback("glazedmenu.toast.restart_game",
                    "Restart the game to apply every change").withStyle(ChatFormatting.GOLD);
            case WORLD -> Component.translatableWithFallback("glazedmenu.toast.restart_world",
                    "Rejoin the world to apply every change").withStyle(ChatFormatting.GOLD);
            case NONE -> set > 0 ? Component.translatableWithFallback("glazedmenu.profiles.applied", "%s settings set", set)
                    : Component.translatableWithFallback("glazedmenu.profiles.same", "Your settings already match it");
        });
        return set;
    }

    // The settings shown, as lines of text: where each is, its value, and the default it was changed from.
    private void copyListed() {
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (Map.Entry<ConfigEditSession, List<ConfigValue<?>>> entry : this.listed.entrySet()) {
            for (ConfigValue<?> value : entry.getValue()) {
                text.append(describe(value)).append('\n');
                count++;
            }
        }
        this.minecraft.keyboardHandler.setClipboard(text.toString());
        GlazedClient.toast(this.title, Component.translatableWithFallback("glazedmenu.changed.copied", "%s settings copied", count));
    }

    private static <T> String describe(ConfigValue<T> value) {
        return value.config().id() + " > " + value.path() + " = " + value.type().format(value.getStored())
                + "  (default: " + value.type().format(value.getDefault()) + ")";
    }

    private void updateButtons() {
        if (this.changedButton != null) this.changedButton.style(this.changedOnly ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL);
        if (this.copyButton != null && this.resetButton != null) {
            this.copyButton.visible = this.changedOnly;
            this.resetButton.visible = this.changedOnly;
            boolean any = this.listed.values().stream().anyMatch(values -> !values.isEmpty());
            this.copyButton.active = any;
            this.resetButton.active = any;
        }
        if (this.doneButton == null || this.saveButton == null) return;
        int modified = this.modifiedCount();
        List<Component> errors = this.errors();
        this.saveButton.visible = modified > 0 || !errors.isEmpty();
        this.saveButton.active = modified > 0 && errors.isEmpty();
        this.doneButton.active = errors.isEmpty();
        if (!errors.isEmpty()) {
            Tooltip fix = Tooltip.create(Component.translatableWithFallback("glazedmenu.button.save.errors", "Fix the invalid values first:")
                    .append("\n").append(errors.stream().map(Component::getString).collect(Collectors.joining("\n"))));
            this.doneButton.setTooltip(fix);
            this.saveButton.setTooltip(fix);
        } else {
            this.doneButton.setTooltip(modified > 0
                    ? Tooltip.create(Component.translatableWithFallback("glazedmenu.button.done.tooltip", "Save and close")) : null);
            this.saveButton.setTooltip(Tooltip.create(Component.translatableWithFallback("glazedmenu.button.save.tooltip", "Save (Ctrl+S)")));
        }
    }

    // Applies every config's pending values. One config tells of itself; several share one toast, with the strictest
    // restart any of them needs.
    private void saveAll() {
        List<ConfigEditSession> changed = this.sessions.values().stream().filter(session -> session.modifiedCount() > 0).toList();
        if (changed.size() == 1) {
            changed.getFirst().saveAndNotify();
            return;
        }
        if (changed.isEmpty()) return;
        RestartRequirement restart = RestartRequirement.NONE;
        for (ConfigEditSession session : changed) restart = restart.max(session.save());
        GlazedClient.toast(this.title, switch (restart) {
            case GAME -> Component.translatableWithFallback("glazedmenu.toast.restart_game",
                    "Restart the game to apply every change").withStyle(ChatFormatting.GOLD);
            case WORLD -> Component.translatableWithFallback("glazedmenu.toast.restart_world",
                    "Rejoin the world to apply every change").withStyle(ChatFormatting.GOLD);
            case NONE -> Component.translatableWithFallback("glazedmenu.toast.saved_many", "%s configs saved", changed.size());
        });
    }

    // Saves the changes and stays on the screen.
    private void saveHere() {
        if (!this.errors().isEmpty() || this.modifiedCount() == 0) return;
        this.saveAll();
        if (this.results != null) this.results.refreshValues();
        this.updateButtons();
    }

    private void done() {
        if (!this.errors().isEmpty()) return;
        this.leave(this.parent);
        this.saveAll();
    }

    /** The server's values changed while the screen was open. */
    public void onConfigSynced(Config config) {
        ConfigEditSession session = this.sessions.get(config.id());
        if (session == null || session.config() != config) return;
        session.onConfigChanged();
        if (this.results != null) this.results.refreshValues();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.shortcutKey() == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        if (event.hasControlDownWithQuirk() && event.shortcutKey() == KEY_S) {
            this.saveHere();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        ConfigStyle.panel(graphics, this.frameLeft(), this.bodyTop(), this.frameWidth(), this.bodyBottom() - this.bodyTop());
        // Behind every mod's cards the panel is dimmed, so the cards (in their mods' colors) stand out from it; search
        // results are read on the plain panel, like a config's page.
        if (this.modId == null && this.query.isBlank() && !this.changedOnly) {
            ConfigStyle.rect(graphics, this.frameLeft() + 1, this.bodyTop() + 1, this.frameWidth() - 2, this.bodyBottom() - this.bodyTop() - 2,
                    ConfigStyle.withAlpha(ConfigStyle.mode() == ConfigTheme.Mode.LIGHT ? 0xFF505868 : 0xFF000000, ConfigStyle.mode() == ConfigTheme.Mode.LIGHT ? 0x26 : 0x70));
        }
        // Glazed Menu's flowing gradient along the list's top edge, as on its mod list.
        if (this.modId == null) GlazedBrand.fill(graphics, this.frameLeft() + 1, this.bodyTop(), this.frameWidth() - 2, 2, 0xD0);
        int searchLeft = this.profilesButton != null ? this.profilesButton.getX() : this.changedButton != null ? this.changedButton.getX()
                : this.search != null ? this.search.getX() - 18 : this.width;
        this.extractHeader(graphics, searchLeft - 8);
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }
        // What's unsaved or invalid in the search results comes before what the search found.
        int errors = this.errors().size();
        int modified = this.modifiedCount();
        Component status = errors > 0 ? Component.translatableWithFallback("glazedmenu.status.errors", "%s invalid", errors)
                : modified > 0 ? Component.translatableWithFallback("glazedmenu.status.modified", "%s unsaved", modified)
                : this.changedOnly ? Component.translatableWithFallback("glazedmenu.changed.results", "%s changed settings in %s configs", this.resultCount, this.resultConfigs)
                : this.query.isBlank() ? Component.translatableWithFallback("glazedmenu.select.hint", "Choose a config to edit")
                : Component.translatableWithFallback("glazedmenu.search.results", "%s results in %s configs", this.resultCount, this.resultConfigs);
        ConfigStyle.text(graphics, this.font, status, this.frameLeft() + 10, this.bottomBarY() + (BAR_HEIGHT - 8) / 2,
                (this.changedOnly && this.copyButton != null ? this.copyButton.getX() - this.frameLeft() - 18 : this.frameWidth() - 110) - (errors > 0 || modified > 0 ? 68 : 0),
                errors > 0 ? ConfigStyle.colors().error() : modified > 0 ? ConfigStyle.colors().modified() : ConfigStyle.colors().textMuted());
    }

    // The mod's icon at the left, on its own, then its title over a line saying what's here.
    private void extractHeader(GuiGraphicsExtractor graphics, int right) {
        int iconX = this.frameLeft() + 8;
        int iconY = this.topBarY() + (HEADER_HEIGHT - HEADER_ICON) / 2;
        if (this.modId == null) {
            // The list of every mod's configs has its own icon, gears and a wrench (resource packs may replace it).
            int[] size = ConfigStyle.textureSize(MOD_LIST_ICON);
            graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_LIST_ICON, iconX, iconY, 0, 0, HEADER_ICON, HEADER_ICON, size[0], size[1], size[0], size[1]);
        } else {
            this.extractModIcon(graphics, this.modId, this.theme, iconX, iconY, HEADER_ICON);
        }
        int x = iconX + HEADER_ICON + 9;
        int width = Math.max(40, right - x);
        int textTop = this.topBarY() + (HEADER_HEIGHT - 21) / 2;
        if (this.modId == null) GlazedBrand.text(graphics, this.font, this.title.getString(), x, textTop);
        else ConfigStyle.text(graphics, this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x, textTop, width, ConfigStyle.colors().text());
        ConfigStyle.text(graphics, this.font, this.subtitle(), x, textTop + 13, width, ConfigStyle.colors().textMuted());
    }

    // The mod's icon, or for a mod without one its name's first letter in a tile (as the mod list shows it).
    private void extractModIcon(GuiGraphicsExtractor graphics, String modId, ConfigTheme theme, int x, int y, int size) {
        if (ModIcons.draw(graphics, modId, theme, x, y, size)) return;
        int accent = ConfigStyle.accentOf(theme);
        ConfigStyle.rect(graphics, x, y, size, size, ConfigStyle.withAlpha(accent, 0x40));
        ConfigStyle.outline(graphics, x, y, size, size, ConfigStyle.withAlpha(accent, 0x90));
        String name = Names.modName(modId);
        Component mark = Component.literal(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.BOLD);
        // The font's glyphs are 8 pixels tall; scaled to fill about two thirds of the square.
        float scale = Math.max(1, Math.round(size * 0.66F / 8F * 2) / 2F);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + size / 2F, y + size / 2F);
        pose.scale(scale, scale);
        graphics.text(this.font, mark, -this.font.width(mark) / 2, -4, accent, false);
        pose.popMatrix();
    }

    private Component subtitle() {
        if (this.modId == null) {
            return Component.translatableWithFallback("glazedmenu.select.all.count", "%s mods · %s configs",
                    ConfigSources.modIds().size(), ConfigSources.all().size());
        }
        int count = this.configs().size();
        return count == 1
                ? Component.translatableWithFallback("glazedmenu.select.mod.count.one", "1 config · %s", this.modId)
                : Component.translatableWithFallback("glazedmenu.select.mod.count", "%s configs · %s", count, this.modId);
    }

    @Override
    public void onClose() {
        int modified = this.modifiedCount();
        if (modified == 0 && this.errors().isEmpty()) {
            this.leave(this.parent);
            return;
        }
        this.minecraft.gui.setScreen(new ConfirmPopup(this, this.theme,
                Component.translatableWithFallback("glazedmenu.discard.title", "Discard changes?"),
                Component.translatableWithFallback("glazedmenu.discard.message", "%s unsaved changes will be lost.", modified),
                Component.translatableWithFallback("glazedmenu.discard.yes", "Discard"), true,
                discard -> this.minecraft.gui.setScreen(discard ? this.parent : this)));
    }

    private static String modName(String modId) {
        return Names.modName(modId);
    }

    private static ConfigTheme themeOf(Config config) {
        return ThemeResources.resolve(config.modId(), config.theme());
    }

    private final class Rows extends ContainerObjectSelectionList<Row> {
        Rows(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 36);
        }

        @Override
        public int getRowWidth() {
            return this.width - 14;
        }

        void setRows(List<Row> rows) {
            this.clearEntries();
            for (Row row : rows) this.addEntry(row, row.height());
            this.setScrollAmount(0);
        }

        @Override
        protected void extractListBackground(GuiGraphicsExtractor graphics) {}

        @Override
        protected void extractListSeparators(GuiGraphicsExtractor graphics) {}

        @Override
        protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!this.scrollable()) return;
            if (ConfigSelectScreen.this.modId == null) {
                int top = this.scrollBarY();
                graphics.fillGradient(this.scrollBarX(), top, this.scrollBarX() + 3, top + this.scrollerHeight(),
                        ConfigStyle.withAlpha(GlazedBrand.now(0), 0xC0), ConfigStyle.withAlpha(GlazedBrand.now(1), 0xC0));
                return;
            }
            ConfigStyle.rect(graphics, this.scrollBarX(), this.scrollBarY(), 3, this.scrollerHeight(), ConfigStyle.withAlpha(ConfigStyle.accent(), 0xB0));
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

    // A mod's name above its configs, with its icon.
    private final class ModHeading extends Row {
        private final String modId;
        private final ConfigTheme theme;

        ModHeading(String modId) {
            this.modId = modId;
            this.theme = ThemeResources.resolve(modId, ConfigTheme.forMod(modId));
        }

        @Override
        int height() {
            return 22;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int x = this.getX();
            int y = this.getY() + this.getHeight() - 13;
            int textX = x + 2;
            ConfigSelectScreen.this.extractModIcon(graphics, this.modId, this.theme, x + 2, y - 2, 12);
            textX += 16;
            ConfigStyle.text(graphics, font, Component.literal(modName(this.modId)).withStyle(ChatFormatting.BOLD), textX, y,
                    this.getWidth() - textX + x - 4, ConfigStyle.accentOf(this.theme));
        }
    }

    // A row of config cards, centered.
    private final class TileRow extends Row {
        private final List<Tile> tiles = new ArrayList<>();
        private final int cardWidth;
        private final int cardHeight;

        TileRow(List<Tile> tiles, int cardWidth, int cardHeight) {
            this.tiles.addAll(tiles);
            this.cardWidth = cardWidth;
            this.cardHeight = cardHeight;
        }

        @Override
        int height() {
            // Room around the cards for a hovered one to grow into.
            return this.cardHeight + TILE_ROW_PADDING * 2;
        }

        private void layout() {
            int count = this.tiles.size();
            int x = this.getX() + (this.getWidth() - (this.cardWidth * count + TILE_GAP * (count - 1))) / 2;
            int y = this.getY() + (this.getHeight() - this.cardHeight) / 2;
            for (Tile tile : this.tiles) {
                tile.x = x;
                tile.y = y;
                tile.width = this.cardWidth;
                tile.height = this.cardHeight;
                x += this.cardWidth + TILE_GAP;
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            this.layout();
            // The hovered tile is drawn last, so it grows over its neighbors.
            Tile top = null;
            for (Tile tile : this.tiles) {
                tile.animate(hovered && tile.contains(mouseX, mouseY));
                if (tile.grow > 0.001F && (top == null || tile.grow > top.grow)) top = tile;
            }
            for (Tile tile : this.tiles) if (tile != top) tile.extract(graphics);
            if (top != null) {
                top.extract(graphics);
                if (top.grow > 0.3F && top.contains(mouseX, mouseY)) {
                    graphics.setTooltipForNextFrame(ConfigSelectScreen.this.font, top.tooltip(), mouseX, mouseY);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            this.layout();
            for (Tile tile : this.tiles) {
                if (tile.contains(event.x(), event.y())) {
                    ConfigSelectScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ConfigSelectScreen.this.leave(tile.open(ConfigSelectScreen.this));
                    return true;
                }
            }
            return false;
        }
    }

    // Every mod card, in columns of even width: each card goes under the shortest column so far, in order, so the columns
    // fill evenly. One entry, as tall as the tallest column (the list scrolls it like any other).
    private final class CardRow extends Row {
        private final List<ModCard> cards;
        private final int columns;
        // Each card's column and its top, from the row's top.
        private final int[] column;
        private final int[] top;
        private final int height;

        CardRow(List<ModCard> cards, int columns) {
            this.cards = cards;
            this.columns = columns;
            this.column = new int[cards.size()];
            this.top = new int[cards.size()];
            int[] filled = new int[columns];
            for (int i = 0; i < cards.size(); i++) {
                int shortest = 0;
                for (int c = 1; c < columns; c++) if (filled[c] < filled[shortest]) shortest = c;
                this.column[i] = shortest;
                this.top[i] = filled[shortest] + CARD_GAP / 2;
                filled[shortest] += cards.get(i).height() + CARD_GAP;
            }
            int tallest = 0;
            for (int value : filled) tallest = Math.max(tallest, value);
            this.height = tallest;
        }

        @Override
        int height() {
            return this.height;
        }

        private void layout() {
            int width = (this.getWidth() - CARD_GAP * (this.columns - 1)) / this.columns;
            for (int i = 0; i < this.cards.size(); i++) {
                ModCard card = this.cards.get(i);
                card.x = this.getX() + this.column[i] * (width + CARD_GAP);
                card.y = this.getY() + this.top[i];
                card.width = width;
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            this.layout();
            for (ModCard card : this.cards) card.extract(graphics, hovered ? mouseX : -1, hovered ? mouseY : -1);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            this.layout();
            for (ModCard card : this.cards) {
                Tile line = card.lineAt(event.x(), event.y());
                if (line == null) continue;
                ConfigSelectScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                ConfigSelectScreen.this.leave(line.open(ConfigSelectScreen.this));
                return true;
            }
            return false;
        }
    }

    // One mod's configs: a panel with an accent line on top, the mod's icon, name and count, then a line per config
    // (its scope's icon and name, and an arrow; a lock when it can't be opened here). A line lights up under the mouse
    // and tells about its config.
    private final class ModCard {
        private final String modId;
        private final ConfigTheme theme;
        private final List<Tile> lines;
        int x;
        int y;
        int width;

        ModCard(String modId, List<Tile> lines) {
            this.modId = modId;
            this.theme = ThemeResources.resolve(modId, ConfigTheme.forMod(modId));
            this.lines = lines;
        }

        int height() {
            return CARD_HEADER + this.lines.size() * CARD_LINE + 4;
        }

        private int lineY(int index) {
            return this.y + CARD_HEADER + index * CARD_LINE;
        }

        @Nullable Tile lineAt(double mouseX, double mouseY) {
            if (mouseX < this.x + 3 || mouseX >= this.x + this.width - 3) return null;
            for (int i = 0; i < this.lines.size(); i++) {
                int top = this.lineY(i);
                if (mouseY >= top && mouseY < top + CARD_LINE - 2) return this.lines.get(i);
            }
            return null;
        }

        void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            Font font = ConfigSelectScreen.this.font;
            // The mod's color (its theme's or its icon's), tinting the card as the mod list tints its entries.
            int accent = ModColors.accent(this.modId);
            int height = this.height();
            boolean over = mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + height;
            ModColors.box(graphics, this.modId, accent, this.x, this.y, this.width, height, CARD_HEADER, over, false);
            graphics.fill(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + 3,
                    ConfigStyle.withAlpha(ModColors.hasOwn(this.modId) ? accent : GlazedBrand.accent(), 0xB0));

            // The mod: its icon, its name in its color, and how many configs.
            ConfigSelectScreen.this.extractModIcon(graphics, this.modId, this.theme, this.x + 7, this.y + 8, 14);
            Component count = Component.literal(Integer.toString(this.lines.size()));
            int countWidth = font.width(count) + 8;
            int countX = this.x + this.width - 7 - countWidth;
            ConfigStyle.rect(graphics, countX, this.y + 9, countWidth, 11, ConfigStyle.withAlpha(accent, 0x30));
            graphics.text(font, count, countX + 4, this.y + 11, ConfigStyle.colors().textDim(), false);
            ConfigStyle.text(graphics, font, Component.literal(modName(this.modId)).withStyle(ChatFormatting.BOLD), this.x + 26, this.y + 11,
                    countX - 6 - (this.x + 26), accent);

            // A line per config.
            Tile hoveredLine = this.lineAt(mouseX, mouseY);
            for (int i = 0; i < this.lines.size(); i++) {
                Tile line = this.lines.get(i);
                int top = this.lineY(i);
                int left = this.x + 4;
                int right = this.x + this.width - 4;
                boolean hovered = line == hoveredLine;
                boolean unavailable = line.unavailable();
                if (hovered) {
                    ConfigStyle.rect(graphics, left, top, right - left, CARD_LINE - 2, ConfigStyle.colors().rowHover());
                    ConfigStyle.rect(graphics, left, top + 3, 2, CARD_LINE - 8, accent);
                }
                int middle = top + (CARD_LINE - 2) / 2;
                int text = unavailable ? ConfigStyle.colors().textMuted() : hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim();
                line.icon().draw(graphics, left + 6, middle - ConfigIcons.SIZE / 2, unavailable ? ConfigStyle.colors().textMuted() : accent);
                ConfigStyle.text(graphics, font, line.label, left + 20, middle - 4, right - left - 40, text);
                (unavailable ? ConfigIcons.LOCK : ConfigIcons.CHEVRON).draw(graphics, right - 14, middle - ConfigIcons.SIZE / 2,
                        hovered && !unavailable ? accent : ConfigStyle.colors().textMuted());
                if (hovered) graphics.setTooltipForNextFrame(font, line.tooltip(), mouseX, mouseY);
            }
            ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.CARD, this.x, this.y, this.width, height, over, false, true);
        }
    }

    // Empty room above the tiles, centering them in the list.
    private static final class Spacer extends Row {
        private final int height;

        Spacer(int height) {
            this.height = height;
        }

        @Override
        int height() {
            return this.height;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {}
    }

    // One config as a navigation card: its artwork framed on all four sides, its scope's icon and a short name over the
    // artwork's foot; it grows and lights up while hovered. A mod's own config screen gets one too.
    private final class Tile {
        private final @Nullable Config config;
        private final @Nullable UnaryOperator<Screen> nativeScreen;
        private final ConfigTheme theme;
        private final Component label;
        private final Identifier artwork;
        int x;
        int y;
        int width;
        int height;
        // 0 at rest, 1 fully hovered, eased between.
        float grow;
        private long lastFrame;

        Tile(Config config) {
            this.config = config;
            this.nativeScreen = null;
            this.theme = themeOf(config);
            this.label = Component.translatableWithFallback("config." + config.modId() + "." + config.name() + ".label",
                    Names.prettify(config.name()));
            this.artwork = artworkOf(config);
        }

        Tile(String modId, UnaryOperator<Screen> nativeScreen) {
            this.config = null;
            this.nativeScreen = nativeScreen;
            this.theme = ThemeResources.resolve(modId, ConfigTheme.forMod(modId));
            this.label = Component.translatableWithFallback("glazedmenu.tile.native", "Settings");
            this.artwork = GlazedMenu.rl("textures/gui/cards/common.png");
        }

        Screen open(Screen parent) {
            if (this.config != null) return ConfigScreen.create(parent, this.config);
            Screen screen = this.nativeScreen.apply(parent);
            return screen != null ? screen : parent;
        }

        private ConfigIcons.Icon icon() {
            return this.config != null ? ConfigIcons.forScope(this.config.scope()) : ConfigIcons.LIST;
        }

        private boolean unavailable() {
            return this.config != null && ConfigSources.access(this.config) == Access.UNAVAILABLE;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }

        // What the config is: its title, what its scope means, what's in it, where it's stored, and anything to know
        // before opening it (a world's config, the server's values, or changes waiting for a restart).
        List<FormattedCharSequence> tooltip() {
            Font font = ConfigSelectScreen.this.font;
            List<FormattedCharSequence> lines = new ArrayList<>();
            if (this.config == null) {
                lines.add(this.label.copy().withStyle(ChatFormatting.BOLD).withColor(ConfigStyle.accentOf(this.theme) & 0xFFFFFF).getVisualOrderText());
                lines.addAll(font.split(Component.translatableWithFallback("glazedmenu.tile.native.tooltip",
                        "Opens the mod's own config screen").withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
                return lines;
            }
            lines.add(this.config.title().copy().withStyle(ChatFormatting.BOLD).withColor(ConfigStyle.accentOf(this.theme) & 0xFFFFFF).getVisualOrderText());
            lines.addAll(font.split(scopeDescription(this.config).copy().withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
            long settings = this.config.values().count();
            // The screen's tabs: each group, and "General" for settings outside any group.
            long categories = this.config.root().children().stream().filter(node -> node instanceof ConfigGroup group && !group.isHidden()).count()
                    + (this.config.root().children().stream().anyMatch(node -> node instanceof ConfigValue<?>) ? 1 : 0);
            lines.add(FormattedCharSequence.EMPTY);
            lines.add(Component.translatableWithFallback("glazedmenu.tile.contents", "%s settings in %s categories", settings, categories)
                    .withStyle(ChatFormatting.WHITE).getVisualOrderText());
            lines.add(Component.translatableWithFallback("glazedmenu.tile.file", "File: %s", ConfigSources.fileName(this.config))
                    .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
            Component note = switch (ConfigSources.access(this.config)) {
                case UNAVAILABLE -> Component.translatableWithFallback("glazedmenu.access.unavailable",
                        "This config belongs to a world; open it while playing").withStyle(ChatFormatting.RED);
                case READ_ONLY -> Component.translatableWithFallback("glazedmenu.tile.read_only",
                        "The server's values; you can view them but not change them").withStyle(ChatFormatting.GOLD);
                case REMOTE -> Component.translatableWithFallback("glazedmenu.tile.remote",
                        "You're editing the server's values").withStyle(ChatFormatting.GOLD);
                case LOCAL -> null;
            };
            if (note != null) lines.addAll(font.split(note, TOOLTIP_WIDTH));
            if (this.config.isRestartPending()) {
                lines.add(Component.translatableWithFallback("glazedmenu.tile.restart", "Some changes apply after a restart")
                        .withStyle(ChatFormatting.GOLD).getVisualOrderText());
            }
            return lines;
        }

        void animate(boolean hovered) {
            long now = System.nanoTime();
            float elapsed = this.lastFrame == 0 ? 0 : (now - this.lastFrame) / 1_000_000F;
            this.lastFrame = now;
            float target = hovered ? 1 : 0;
            this.grow = target + (this.grow - target) * (float) Math.exp(-Math.min(elapsed, 200) / TILE_EASE_MILLIS);
            if (Math.abs(this.grow - target) < 0.002F) this.grow = target;
        }

        void extract(GuiGraphicsExtractor graphics) {
            Font font = ConfigSelectScreen.this.font;
            boolean unavailable = this.unavailable();
            int accent = ConfigStyle.accentOf(this.theme);
            float eased = this.grow * this.grow * (3 - 2 * this.grow);
            float scale = 1 + TILE_GROW * eased;
            int width = this.width;
            int height = this.height;

            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(this.x + width / 2F, this.y + height / 2F);
            pose.scale(scale, scale);
            pose.translate(-width / 2F, -height / 2F);
            // A shadow that deepens as it lifts, the frame, and its border fading to the accent.
            int shadow = Math.round(2 + 4 * eased);
            ConfigStyle.rect(graphics, 2, shadow, width, height, ConfigStyle.withAlpha(0xFF000000, Math.round(0x38 + 0x48 * eased)));
            ConfigStyle.rect(graphics, 0, 0, width, height, blend(ConfigStyle.colors().button(), ConfigStyle.colors().buttonHover(), eased));
            ConfigStyle.outline(graphics, 0, 0, width, height, blend(ConfigStyle.colors().panelBorder(), accent, eased));

            // The artwork, inside the frame's padding; greyed out when the config can't be opened here.
            int artX = TILE_PADDING;
            int artY = TILE_PADDING;
            int artWidth = width - TILE_PADDING * 2;
            int artHeight = height - TILE_PADDING * 2;
            int[] size = ConfigStyle.textureSize(this.artwork);
            // Covers the area keeping its proportions, cropping the sides or the top and bottom.
            float cover = Math.max(artWidth / (float) size[0], artHeight / (float) size[1]);
            int regionWidth = Math.round(artWidth / cover);
            int regionHeight = Math.round(artHeight / cover);
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.artwork, artX, artY, (size[0] - regionWidth) / 2F, (size[1] - regionHeight) / 2F,
                    artWidth, artHeight, regionWidth, regionHeight, size[0], size[1],
                    ConfigStyle.withAlpha(unavailable ? 0xFF6A6A6A : 0xFFFFFFFF, Math.round(255 * (ART_OPACITY + (ART_OPACITY_HOVERED - ART_OPACITY) * eased))));
            // A dark fade over the artwork's foot, for the icon and name to sit on.
            int scrim = Math.min(artHeight, 46);
            graphics.fillGradient(artX, artY + artHeight - scrim, artX + artWidth, artY + artHeight, 0x00000000, 0xD0000000);
            graphics.fill(artX, artY, artX + artWidth, artY + 2, ConfigStyle.withAlpha(accent, Math.round(0x90 + 0x6F * eased)));

            int iconSize = 18;
            int labelY = artY + artHeight - 13;
            int iconY = labelY - iconSize - 5;
            int iconColor = unavailable ? 0xFFB0B0B0 : blend(0xFFFFFFFF, accent, 0.25F + 0.5F * eased);
            this.icon().draw(graphics, (width - iconSize) / 2 + 1, iconY + 1, iconSize, 0x90000000);
            this.icon().draw(graphics, (width - iconSize) / 2, iconY, iconSize, iconColor);
            if (unavailable) ConfigIcons.LOCK.draw(graphics, width - TILE_PADDING - ConfigIcons.SIZE - 4, TILE_PADDING + 5, 0xFFE0E0E0);
            // The config's kind as a tag, as the mod list tags its mods: on a dark backing so it reads over the artwork.
            if (this.config != null) {
                Component tag = Component.translatableWithFallback("glazedmenu.tag.scope." + this.config.scope().name().toLowerCase(Locale.ROOT),
                        this.config.scope().name());
                int tagX = TILE_PADDING + 4;
                int tagY = TILE_PADDING + 6;
                ConfigStyle.rect(graphics, tagX, tagY, font.width(tag) + 8, 11, 0xC0000000);
                ConfigStyle.badge(graphics, font, tag, tagX, tagY, unavailable ? 0xFFB0B0B0 : ConfigStyle.mix(accent, 0xFFFFFFFF, 0.35F));
            }
            Component label = this.label.copy().withStyle(ChatFormatting.BOLD);
            int textWidth = Math.min(font.width(label), artWidth - 6);
            ConfigStyle.text(graphics, font, label, (width - textWidth) / 2 + 1, labelY + 1, textWidth, 0x90000000);
            ConfigStyle.text(graphics, font, label, (width - textWidth) / 2, labelY, textWidth, unavailable ? 0xFFB0B0B0 : 0xFFFFFFFF);
            pose.popMatrix();
            int grownWidth = Math.round(width * scale);
            int grownHeight = Math.round(height * scale);
            ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.CARD, this.x - (grownWidth - width) / 2, this.y - (grownHeight - height) / 2,
                    grownWidth, grownHeight, this.grow > 0.5F, false, !unavailable);
        }
    }

    // A config's card artwork: the mod's own for that config if it ships one
    // (assets/<modid>/textures/gui/config/cards/<config name>.png), otherwise Glazed Menu's for its scope.
    private static Identifier artworkOf(Config config) {
        Identifier own = Identifier.fromNamespaceAndPath(config.modId(), "textures/gui/config/cards/" + config.name() + ".png");
        if (Minecraft.getInstance().getResourceManager().getResource(own).isPresent()) return own;
        return GlazedMenu.rl("textures/gui/cards/" + config.scope().name().toLowerCase(Locale.ROOT) + ".png");
    }

    private static Component scopeDescription(Config config) {
        return switch (config.scope()) {
            case CLIENT -> Component.translatableWithFallback("glazedmenu.scope.client", "Client settings, kept on this computer");
            case COMMON -> Component.translatableWithFallback("glazedmenu.scope.common", "Settings for both the game and servers");
            case SERVER -> Component.translatableWithFallback("glazedmenu.scope.server", "Server settings, sent to players");
            case WORLD -> Component.translatableWithFallback("glazedmenu.scope.world", "Settings stored in each world, sent to players");
            case STARTUP -> Component.translatableWithFallback("glazedmenu.scope.startup", "Settings read when the game starts; changes need a restart");
        };
    }

    private static int blend(int from, int to, float t) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = from >>> shift & 0xFF;
            int b = to >>> shift & 0xFF;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }
}
