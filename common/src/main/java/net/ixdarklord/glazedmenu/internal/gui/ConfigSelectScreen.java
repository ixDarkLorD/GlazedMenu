package net.ixdarklord.glazedmenu.internal.gui;

import net.ixdarklord.glazedmenu.internal.compat.CompatList;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.compat.KeyEvent;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.ixdarklord.glazedmenu.internal.compat.RenderPipelines;
import net.ixdarklord.glazedmenu.internal.core.Names;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.api.config.ConfigNode;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.ixdarklord.glazedmenu.api.config.ConfigGroup;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The main config screen: one mod's configs, or every mod's, as cards grouped by mod. Its search bar is the only one
 * that searches across configs: results are listed by config, each config a category of its matching settings, and
 * opening one shows that config filtered to it. (A config's own search only searches that config.)
 */
public final class ConfigSelectScreen extends StyledScreen {
    private static final int KEY_F = 70;
    // A taller header than the other screens, so the mod's icon is large enough to recognize.
    private static final int HEADER_HEIGHT = 46;
    private static final int HEADER_ICON = 32;
    private static final ResourceLocation MOD_LIST_ICON = GlazedMenu.rl("textures/gui/mod_list.png");
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

    private final @Nullable Screen parent;
    private final @Nullable String modId;
    private @Nullable Rows rows;
    private @Nullable StyledEditBox search;
    private String query = "";
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

    @Override
    protected void init() {
        int searchWidth = Math.clamp(this.width / 3, 100, 200);
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

        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        this.rows = this.addRenderableWidget(new Rows(this.minecraft, this.frameWidth() - 8, bodyHeight - 8, bodyTop + 4));
        this.rows.updateSizeAndPosition(this.frameWidth() - 8, bodyHeight - 8, this.frameLeft() + 4, bodyTop + 4);
        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 80, button -> this.onClose()).style(FlatButton.Style.PRIMARY));
        done.setPosition(this.frameLeft() + this.frameWidth() - 84, this.barWidgetY(this.bottomBarY()));
        this.rebuildRows();
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
        if (this.search != null && !this.query.isEmpty()) this.setInitialFocus(this.search);
        else super.setInitialFocus();
    }

    private List<Config> configs() {
        return this.modId == null ? ConfigSources.all() : ConfigSources.forMod(this.modId);
    }

    private void rebuildRows() {
        if (this.rows == null) return;
        List<Row> entries = new ArrayList<>();
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        this.resultCount = 0;
        this.resultConfigs = 0;
        if (query.isEmpty()) {
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
                this.rows.wide = true;
                this.buildModCards(byMod, entries);
                this.rows.setRows(entries);
                return;
            }
            // One mod's configs: cards as large as the panel allows, in the arrangement (columns x rows) that fits them
            // biggest, so a mod with one or two configs doesn't leave the panel mostly empty.
            this.rows.wide = true;
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
        } else {
            this.rows.wide = false;
            // Each config with matches is a category: its header, then its matching settings.
            for (Config config : this.configs()) {
                ConfigEditSession session = new ConfigEditSession(config);
                if (session.access() == Access.UNAVAILABLE) continue;
                List<Row> results = new ArrayList<>();
                for (ConfigValue<?> value : config.values().toList()) {
                    if (session.isVisible(value) && matches(config, value, query)) results.add(new Result(config, value));
                }
                if (results.isEmpty()) continue;
                entries.add(new CategoryHeader(config, results.size()));
                entries.addAll(results);
                this.resultCount += results.size();
                this.resultConfigs++;
            }
            if (entries.isEmpty()) entries.add(new NoResults());
        }
        this.rows.setRows(entries);
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

    // Anything a player might type: the name, the key or path, the comment, a group's name, or the value itself.
    private static boolean matches(Config config, ConfigValue<?> value, String query) {
        if (value.displayName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (value.path().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (value.comment().stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains(query))) return true;
        for (ConfigNode group = value.parent(); group != null && group.parent() != null; group = group.parent()) {
            if (group.displayName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return format(value).toLowerCase(Locale.ROOT).contains(query);
    }

    private static <T> String format(ConfigValue<T> value) {
        return value.type().format(value.getStored());
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.key() == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        ConfigStyle.panel(graphics, this.frameLeft(), this.bodyTop(), this.frameWidth(), this.bodyBottom() - this.bodyTop());
        // Behind every mod's cards the panel is dimmed, so the cards (in their mods' colors) stand out from it.
        if (this.modId == null) {
            ConfigStyle.rect(graphics, this.frameLeft() + 1, this.bodyTop() + 1, this.frameWidth() - 2, this.bodyBottom() - this.bodyTop() - 2,
                    ConfigStyle.withAlpha(ConfigStyle.mode() == ConfigTheme.Mode.LIGHT ? 0xFF505868 : 0xFF000000, ConfigStyle.mode() == ConfigTheme.Mode.LIGHT ? 0x26 : 0x70));
        }
        // Glazed Menu's flowing gradient along the list's top edge, as on its mod list.
        if (this.modId == null) GlazedBrand.fill(graphics, this.frameLeft() + 1, this.bodyTop(), this.frameWidth() - 2, 2, 0xD0);
        int searchLeft = this.search != null ? this.search.getX() - 18 : this.width;
        this.extractHeader(graphics, searchLeft - 8);
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }
        Component status = this.query.isBlank()
                ? Component.translatableWithFallback("glazedmenu.select.hint", "Choose a config to edit")
                : Component.translatableWithFallback("glazedmenu.search.results", "%s results in %s configs", this.resultCount, this.resultConfigs);
        ConfigStyle.text(graphics, this.font, status, this.frameLeft() + 10, this.bottomBarY() + (BAR_HEIGHT - 8) / 2, this.frameWidth() - 110,
                ConfigStyle.colors().textMuted());
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
        GuiGraphicsExtractor.Pose pose = graphics.pose();
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
        this.leave(this.parent);
    }

    private static String modName(String modId) {
        return Names.modName(modId);
    }

    private static ConfigTheme themeOf(Config config) {
        return ThemeResources.resolve(config.modId(), config.theme());
    }

    private final class Rows extends CompatList<Row> {
        // The cards of every mod use the whole width; tiles and search results stay narrower, easier to read.
        boolean wide;

        Rows(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 36);
        }

        @Override
        public int getRowWidth() {
            return this.wide ? this.width - 14 : Math.min(this.width - 14, 460);
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

    private abstract static class Row extends CompatList.Entry<Row> {
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
        private final ResourceLocation artwork;
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

            GuiGraphicsExtractor.Pose pose = graphics.pose();
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
    private static ResourceLocation artworkOf(Config config) {
        ResourceLocation own = ResourceLocation.fromNamespaceAndPath(config.modId(), "textures/gui/config/cards/" + config.name() + ".png");
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

    // A config as a search category: its icon, title, mod and how many settings matched.
    private final class CategoryHeader extends Row {
        private final Config config;
        private final ConfigTheme theme;
        private final int count;

        CategoryHeader(Config config, int count) {
            this.config = config;
            this.theme = themeOf(config);
            this.count = count;
        }

        @Override
        int height() {
            return 26;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int x = this.getX();
            int y = this.getY() + this.getHeight() - 16;
            int accent = ConfigStyle.accentOf(this.theme);
            ConfigIcons.forScope(this.config.scope()).draw(graphics, x + 2, y - 1, 12, accent);
            MutableComponent title = this.config.title().copy().withStyle(ChatFormatting.BOLD);
            if (ConfigSelectScreen.this.modId == null) {
                title = Component.literal(modName(this.config.modId()) + " › ").withStyle(ChatFormatting.GRAY).append(this.config.title().copy().withStyle(ChatFormatting.BOLD));
            }
            Component count = Component.translatableWithFallback("glazedmenu.search.count", "%s found", this.count);
            int countWidth = font.width(count);
            ConfigStyle.text(graphics, font, title, x + 18, y + 1, this.getWidth() - countWidth - 30, accent);
            graphics.text(font, count, x + this.getWidth() - countWidth - 4, y + 1, ConfigStyle.colors().textMuted(), false);
            graphics.fill(x + 2, y + 13, x + this.getWidth() - 2, y + 14, ConfigStyle.withAlpha(accent, 0x50));
        }
    }

    // A matching setting: its name, where it sits, and its value; opening it shows its config filtered to it.
    private final class Result extends Row {
        private final Config config;
        private final ConfigValue<?> value;
        private final ConfigTheme theme;
        private final Component location;

        Result(Config config, ConfigValue<?> value) {
            this.config = config;
            this.value = value;
            this.theme = themeOf(config);
            MutableComponent location = Component.empty();
            List<Component> groups = new ArrayList<>();
            for (ConfigNode group = value.parent(); group != null && group.parent() != null; group = group.parent()) groups.addFirst(group.displayName());
            for (int i = 0; i < groups.size(); i++) {
                if (i > 0) location.append(" › ");
                location.append(groups.get(i));
            }
            if (groups.isEmpty()) {
                location.append(value.comment().isEmpty()
                        ? Component.translatableWithFallback("glazedmenu.tab.general", "General")
                        : Component.literal(value.comment().getFirst()));
            }
            this.location = location;
        }

        @Override
        int height() {
            return 30;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int x = this.getX() + 8;
            int y = this.getY() + 1;
            int width = this.getWidth() - 8;
            int height = this.getHeight() - 2;
            int accent = ConfigStyle.accentOf(this.theme);
            if (hovered) {
                ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.colors().rowHover());
                ConfigStyle.rect(graphics, x, y + 4, 2, height - 8, accent);
            }
            Component value = Component.literal(format(this.value));
            int valueWidth = Math.min(font.width(value), width / 3);
            int textWidth = width - valueWidth - 40;
            ConfigStyle.text(graphics, font, this.value.displayName(), x + 10, y + 5, textWidth, ConfigStyle.colors().text());
            ConfigStyle.text(graphics, font, this.location, x + 10, y + 17, textWidth, ConfigStyle.colors().textDim());
            ConfigStyle.text(graphics, font, value, x + width - valueWidth - 22, y + (height - 8) / 2, valueWidth, hovered ? accent : ConfigStyle.colors().textDim());
            ConfigIcons.CHEVRON.draw(graphics, x + width - 14, y + (height - ConfigIcons.SIZE) / 2, hovered ? accent : ConfigStyle.colors().textMuted());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            ConfigSelectScreen.this.leave(ConfigScreen.create(ConfigSelectScreen.this, this.config, this.value.path()));
            return true;
        }
    }

    private final class NoResults extends Row {
        @Override
        int height() {
            return 30;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConfigSelectScreen.this.font;
            int y = this.getY() + (this.getHeight() - 8) / 2;
            Component text = Component.translatableWithFallback("glazedmenu.no_results", "No settings match");
            int textWidth = font.width(text) + 14;
            int x = this.getX() + (this.getWidth() - textWidth) / 2;
            ConfigIcons.SEARCH.draw(graphics, x, y - 1, ConfigStyle.colors().textMuted());
            graphics.text(font, text, x + 14, y, ConfigStyle.colors().textMuted(), false);
        }
    }
}
