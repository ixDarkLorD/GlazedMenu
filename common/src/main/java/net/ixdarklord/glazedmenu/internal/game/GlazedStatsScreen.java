package net.ixdarklord.glazedmenu.internal.game;

import net.minecraft.util.Mth;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The statistics, in place of the game's own screen: a ribbon of tabs (General, Blocks, Items, Mobs), four of the
 * numbers most looked for as cards (the time played, the distance traveled, the mobs killed, the deaths), and under
 * them the chosen tab as a table: a row for each thing counted, a column for each count, sorted by the column clicked.
 * The search narrows the rows by name.
 * <p>
 * The numbers are the server's: they're asked for as the screen opens, and the table follows as they arrive.
 */
public final class GlazedStatsScreen extends GameListScreen {
    private static final Class<?> VANILLA = net.minecraft.client.gui.screens.achievement.StatsScreen.class;
    private static final int LINE_HEIGHT = 18;
    private static final int CARD_HEIGHT = 28;
    private static final int COLUMNS_HEIGHT = 14;
    // The tab and each tab's order, kept while the game runs: the column sorted by (-1 for the names) and which way.
    private static int tab;
    private static final int[] SORT_COLUMN = {-1, 0, 1, 0};
    private static final boolean[] SORT_ASCENDING = {true, false, false, false};

    private final StatsCounter stats;
    private final List<Tab> tabs;
    // What the numbers added up to when the rows were last built: they're built again when it changes.
    private long seen = Long.MIN_VALUE;
    private int sinceCheck;

    private enum Kind {
        GENERAL,
        BLOCKS,
        ITEMS,
        MOBS
    }

    // A row of the table: what's counted, and its count in each column (with how it's written).
    private record Line(Component name, @Nullable ItemStack icon, int[] values, String[] shown) {}

    private GlazedStatsScreen(@Nullable Screen parent, StatsCounter stats) {
        super(Component.translatable("gui.stats"), parent, ConfigIcons.CHART);
        this.stats = stats;
        this.tabs = List.of(
                new Tab(Component.translatable("stat.generalButton"), ConfigIcons.LIST, null, GlazedBrand.identity(0xFF8B7CF6)),
                new Tab(Component.translatableWithFallback("glazedmenu.stats.tab.blocks", "Blocks"), ConfigIcons.GRID, null, GlazedBrand.identity(0xFFF2A33B)),
                new Tab(Component.translatable("stat.itemsButton"), ConfigIcons.EDIT, null, GlazedBrand.identity(0xFF4DA3FF)),
                new Tab(Component.translatable("stat.mobsButton"), ConfigIcons.PERSON, null, GlazedBrand.identity(0xFF5BC467)));
    }

    /** The screen to open instead: this one for the game's own statistics screen, over the screen that opened it. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof StatsScreen) || screen.getClass() != VANILLA || !GlazedSettings.replaceStats()) return screen;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return screen;
        return new GlazedStatsScreen(minecraft.screen, minecraft.player.getStats());
    }

    private static Kind kind() {
        return Kind.values()[Mth.clamp(tab, 0, Kind.values().length - 1)];
    }

    // --- The numbers ---

    // Opened: the server is asked for the numbers, as the game's own screen asks.
    @Override
    public void added() {
        super.added();
        // 1.20.1 calls this before the screen is given the game, the first time it opens.
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
    }

    private int custom(ResourceLocation id) {
        return this.stats.getValue(Stats.CUSTOM.get(id));
    }

    // The numbers of the General tab added up: cheap to work out, and different once the server's have arrived.
    private long signature() {
        long sum = 0;
        for (Stat<ResourceLocation> stat : Stats.CUSTOM) sum = sum * 31 + this.stats.getValue(stat);
        return sum;
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.sinceCheck < 5) return;
        this.sinceCheck = 0;
        long signature = this.signature();
        if (signature != this.seen) this.rebuildRows(true);
    }

    private int distance() {
        long centimeters = 0;
        for (Stat<ResourceLocation> stat : Stats.CUSTOM) {
            if (stat.getValue().getPath().endsWith("_one_cm")) centimeters += this.stats.getValue(stat);
        }
        return (int) Math.min(Integer.MAX_VALUE, centimeters);
    }

    private static List<Component> columns(Kind kind) {
        return switch (kind) {
            case GENERAL -> List.of(Component.translatableWithFallback("glazedmenu.stats.column.value", "Value"));
            case BLOCKS -> List.of(column("mined", "Mined"), column("crafted", "Crafted"), column("used", "Used"), column("picked_up", "Picked Up"), column("dropped", "Dropped"));
            case ITEMS -> List.of(column("crafted", "Crafted"), column("used", "Used"), column("broken", "Broken"), column("picked_up", "Picked Up"), column("dropped", "Dropped"));
            case MOBS -> List.of(column("killed", "Killed"), column("killed_by", "Killed By"));
        };
    }

    private static Component column(String key, String name) {
        return Component.translatableWithFallback("glazedmenu.stats.column." + key, name);
    }

    private static Component nameColumn(Kind kind) {
        return switch (kind) {
            case GENERAL -> Component.translatableWithFallback("glazedmenu.stats.column.statistic", "Statistic");
            case BLOCKS -> Component.translatableWithFallback("glazedmenu.stats.column.block", "Block");
            case ITEMS -> Component.translatableWithFallback("glazedmenu.stats.column.item", "Item");
            case MOBS -> Component.translatableWithFallback("glazedmenu.stats.column.mob", "Mob");
        };
    }

    private <T> void put(int[] values, String[] shown, int column, StatType<T> type, T key) {
        if (!type.contains(key)) return;
        Stat<T> stat = type.get(key);
        values[column] = this.stats.getValue(stat);
        shown[column] = stat.format(values[column]);
    }

    private List<Line> lines(Kind kind) {
        List<Line> lines = new ArrayList<>();
        switch (kind) {
            case GENERAL -> {
                for (Stat<ResourceLocation> stat : Stats.CUSTOM) {
                    int value = this.stats.getValue(stat);
                    lines.add(new Line(Component.translatable("stat." + stat.getValue().toString().replace(':', '.')), null, new int[]{value}, new String[]{stat.format(value)}));
                }
            }
            case BLOCKS, ITEMS -> {
                for (Item item : BuiltInRegistries.ITEM) {
                    boolean block = item instanceof BlockItem;
                    if (block != (kind == Kind.BLOCKS)) continue;
                    int[] values = new int[5];
                    String[] shown = new String[5];
                    if (item instanceof BlockItem blockItem) {
                        Block mined = blockItem.getBlock();
                        this.put(values, shown, 0, Stats.BLOCK_MINED, mined);
                        this.put(values, shown, 1, Stats.ITEM_CRAFTED, item);
                        this.put(values, shown, 2, Stats.ITEM_USED, item);
                    } else {
                        this.put(values, shown, 0, Stats.ITEM_CRAFTED, item);
                        this.put(values, shown, 1, Stats.ITEM_USED, item);
                        this.put(values, shown, 2, Stats.ITEM_BROKEN, item);
                    }
                    this.put(values, shown, 3, Stats.ITEM_PICKED_UP, item);
                    this.put(values, shown, 4, Stats.ITEM_DROPPED, item);
                    boolean counted = false;
                    for (int value : values) counted |= value > 0;
                    if (!counted) continue;
                    ItemStack stack = new ItemStack(item);
                    lines.add(new Line(stack.getHoverName(), stack, values, shown));
                }
            }
            case MOBS -> {
                for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                    int[] values = new int[2];
                    String[] shown = new String[2];
                    this.put(values, shown, 0, Stats.ENTITY_KILLED, type);
                    this.put(values, shown, 1, Stats.ENTITY_KILLED_BY, type);
                    if (values[0] > 0 || values[1] > 0) lines.add(new Line(type.getDescription(), null, values, shown));
                }
            }
        }
        return lines;
    }

    @Override
    protected List<Row> buildRows() {
        this.seen = this.signature();
        Kind kind = kind();
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        int column = SORT_COLUMN[tab];
        boolean ascending = SORT_ASCENDING[tab];
        Comparator<Line> byName = Comparator.comparing(line -> line.name.getString().toLowerCase(Locale.ROOT));
        Comparator<Line> order = column < 0 ? byName : Comparator.<Line>comparingInt(line -> line.values[column]).thenComparing(byName.reversed());
        if (!ascending) order = order.reversed();
        List<Row> rows = new ArrayList<>();
        this.lines(kind).stream().filter(line -> query.isEmpty() || line.name.getString().toLowerCase(Locale.ROOT).contains(query)).sorted(order)
                .forEach(line -> rows.add(new LineRow(line)));
        if (rows.isEmpty()) {
            rows.add(new Notice(query.isEmpty() ? ConfigIcons.CHART : ConfigIcons.SEARCH, query.isEmpty()
                    ? Component.translatableWithFallback("glazedmenu.stats.empty", "Nothing counted here yet")
                    : Component.translatableWithFallback("glazedmenu.stats.none", "No statistics match")));
        }
        return rows;
    }

    // --- Layout ---

    @Override
    protected int detailsWidth() {
        return 0;
    }

    // The cards give way in a short window.
    private boolean cards() {
        return this.height >= 270;
    }

    @Override
    protected int listInset() {
        return (this.cards() ? CARD_HEIGHT + 5 : 0) + COLUMNS_HEIGHT;
    }

    private int columnsY() {
        return this.listTop() + (this.cards() ? CARD_HEIGHT + 5 : 0);
    }

    @Override
    protected List<Tab> tabs() {
        return this.tabs;
    }

    @Override
    protected int chosenTab() {
        return tab;
    }

    @Override
    protected void chooseTab(int index) {
        tab = index;
        this.rebuildRows(false);
    }

    @Override
    protected Component searchName() {
        return Component.translatableWithFallback("glazedmenu.stats.search", "Search statistics");
    }

    @Override
    protected Component searchHint() {
        return Component.translatableWithFallback("glazedmenu.stats.search.hint", "Search statistics… (Ctrl+F)");
    }

    @Override
    protected void initButtons() {
        FlatButton doneButton = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.onClose())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        doneButton.setPosition(this.frameLeft() + this.frameWidth() - 4 - 84, this.barWidgetY(this.bottomBarY()));
    }

    @Override
    protected void onChosen() {}

    @Override
    protected void extractDetails(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY) {}

    // The columns: each as wide as the rest leaves it, within reason; the General tab's one column is wider.
    private int columnWidth(int columns) {
        return kind() == Kind.GENERAL ? 90 : Mth.clamp((this.rowWidth() - 100) / columns, 30, 60);
    }

    // The right edge of a column.
    private int columnRight(int column, int columns) {
        return this.rowLeft() + this.rowWidth() - 4 - (columns - 1 - column) * this.columnWidth(columns);
    }

    // A click on a column's name sorts by it, largest first, then smallest first; on the names' column, by name.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int top = this.columnsY();
        if (event.button() == 0 && event.y() >= top && event.y() < top + COLUMNS_HEIGHT && event.x() >= this.rowLeft() && event.x() < this.rowLeft() + this.rowWidth()) {
            int columns = columns(kind()).size();
            int clicked = -1;
            for (int i = 0; i < columns; i++) {
                if (event.x() > this.columnRight(i, columns) - this.columnWidth(columns) && event.x() <= this.columnRight(i, columns)) clicked = i;
            }
            if (clicked == SORT_COLUMN[tab]) {
                SORT_ASCENDING[tab] = !SORT_ASCENDING[tab];
            } else {
                SORT_COLUMN[tab] = clicked;
                SORT_ASCENDING[tab] = clicked < 0;
            }
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.rebuildRows(false);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // --- Drawing ---

    @Override
    protected Component subtitle() {
        return Component.translatableWithFallback("glazedmenu.stats.status", "%s played", StatFormatter.TIME.format(this.custom(Stats.PLAY_TIME)));
    }

    @Override
    protected void extractOverList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = this.rowLeft();
        int width = this.rowWidth();
        if (this.cards()) {
            Component[] names = {
                    Component.translatableWithFallback("glazedmenu.stats.card.played", "Time Played"),
                    Component.translatableWithFallback("glazedmenu.stats.card.distance", "Distance Traveled"),
                    Component.translatable("stat.minecraft.mob_kills"),
                    Component.translatable("stat.minecraft.deaths")};
            String[] values = {
                    StatFormatter.TIME.format(this.custom(Stats.PLAY_TIME)),
                    StatFormatter.DISTANCE.format(this.distance()),
                    StatFormatter.DEFAULT.format(this.custom(Stats.MOB_KILLS)),
                    StatFormatter.DEFAULT.format(this.custom(Stats.DEATHS))};
            int[] colors = {GlazedBrand.identity(0xFF8B7CF6), GlazedBrand.identity(0xFF3CC8B4), GlazedBrand.identity(0xFFF07A5A), GlazedBrand.identity(0xFF9AA4B8)};
            int each = (width - 4 * 3) / 4;
            int y = this.listTop();
            for (int i = 0; i < 4; i++) {
                int x = left + i * (each + 4);
                int cardWidth = i == 3 ? width - 3 * (each + 4) : each;
                ConfigStyle.rect(graphics, x, y, cardWidth, CARD_HEIGHT, ConfigStyle.mix(ConfigStyle.colors().button(), colors[i], 0.12F));
                ConfigStyle.outline(graphics, x, y, cardWidth, CARD_HEIGHT, ConfigStyle.withAlpha(colors[i], 0x60));
                graphics.fill(x + 1, y + 1, x + cardWidth - 1, y + 3, ConfigStyle.withAlpha(colors[i], 0xB0));
                ConfigStyle.text(graphics, this.font, names[i], x + 6, y + 6, cardWidth - 12, ConfigStyle.colors().textDim());
                ConfigStyle.text(graphics, this.font, Component.literal(values[i]).withStyle(ChatFormatting.BOLD), x + 6, y + 16, cardWidth - 12, ConfigStyle.colors().text());
            }
        }
        // The columns' names over the rows, the one sorted by in the accent with an arrow.
        Kind kind = kind();
        List<Component> columns = columns(kind);
        int y = this.columnsY();
        int textY = y + 2;
        int sorted = SORT_COLUMN[tab];
        boolean overColumns = mouseY >= y && mouseY < y + COLUMNS_HEIGHT;
        int columnWidth = this.columnWidth(columns.size());
        int namesRight = this.columnRight(0, columns.size()) - columnWidth;
        this.extractColumn(graphics, nameColumn(kind), left + 4, textY, namesRight - left - 8, false, sorted < 0, overColumns && mouseX >= left && mouseX < namesRight);
        for (int i = 0; i < columns.size(); i++) {
            int right = this.columnRight(i, columns.size());
            this.extractColumn(graphics, columns.get(i), right, textY, columnWidth - 4, true, sorted == i, overColumns && mouseX > right - columnWidth && mouseX <= right);
        }
        graphics.fill(left, y + COLUMNS_HEIGHT - 2, left + width, y + COLUMNS_HEIGHT - 1, ConfigStyle.colors().panelBorder());
        ConfigStyle.text(graphics, this.font, Component.translatableWithFallback("glazedmenu.stats.hint", "Click a column to sort by it"),
                this.frameLeft() + 10, this.bottomBarY() + (BAR_HEIGHT - 8) / 2, this.frameWidth() - 110, ConfigStyle.colors().textMuted());
    }

    // A column's name, from x rightward or ending at it; with an arrow when the rows are sorted by it.
    private void extractColumn(GuiGraphicsExtractor graphics, Component name, int x, int y, int width, boolean endAt, boolean sorted, boolean hovered) {
        int color = sorted ? ConfigStyle.accent() : hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim();
        int arrow = sorted ? 9 : 0;
        int textWidth = Math.min(this.font.width(name), Math.max(8, width - arrow));
        int textX = endAt ? x - textWidth - arrow : x;
        ConfigStyle.text(graphics, this.font, name, textX, y, textWidth, color);
        if (sorted) (SORT_ASCENDING[tab] ? ConfigIcons.UP : ConfigIcons.DOWN).draw(graphics, textX + textWidth + 1, y, 8, color);
    }

    // A row of the table: what's counted (an item with its picture), and its count under each column.
    private final class LineRow extends Row {
        private final Line line;

        LineRow(Line line) {
            this.line = line;
        }

        @Override
        protected int height() {
            return LINE_HEIGHT;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GlazedStatsScreen screen = GlazedStatsScreen.this;
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            if (hovered) ConfigStyle.rect(graphics, x, y, width, LINE_HEIGHT - 1, ConfigStyle.colors().rowHover());
            graphics.fill(x + 2, y + LINE_HEIGHT - 1, x + width - 2, y + LINE_HEIGHT, ConfigStyle.withAlpha(ConfigStyle.colors().panelBorder(), 0x70));
            int columns = this.line.values.length;
            int columnWidth = screen.columnWidth(columns);
            int textX = x + 4;
            if (this.line.icon != null) {
                graphics.fakeItem(this.line.icon, x + 2, y);
                textX = x + 22;
            }
            int textY = y + (LINE_HEIGHT - 9) / 2;
            int sorted = SORT_COLUMN[tab];
            ConfigStyle.text(graphics, screen.font, this.line.name, textX, textY, screen.columnRight(0, columns) - columnWidth - textX - 4, ConfigStyle.colors().text());
            for (int i = 0; i < columns; i++) {
                // A count of nothing, or one the game doesn't keep for this, as a dash.
                boolean none = this.line.shown[i] == null || this.line.values[i] == 0 && columns > 1;
                Component value = Component.literal(none ? "–" : this.line.shown[i]);
                if (i == sorted && !none) value = value.copy().withStyle(ChatFormatting.BOLD);
                int valueWidth = Math.min(screen.font.width(value), columnWidth - 4);
                ConfigStyle.text(graphics, screen.font, value, screen.columnRight(i, columns) - valueWidth, textY, valueWidth,
                        none ? ConfigStyle.colors().textMuted() : ConfigStyle.colors().text());
            }
        }
    }
}
