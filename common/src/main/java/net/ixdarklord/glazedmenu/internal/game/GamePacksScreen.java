package net.ixdarklord.glazedmenu.internal.game;

import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.glazedmenu.internal.core.GlazedClient;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.ConfirmPopup;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The packs (resource packs from the options, data packs when making a world), in Glazed Menu's look, in place of the
 * game's own screen. On the left, the packs in use as a stack, the highest first (it overrides the ones below): a pack
 * is dragged to another place in the stack, or moved with its arrows, and taken out with its cross (a lock, where the
 * game requires it). On the right, the packs to choose from as tiles, each its icon over its name, with a warning where
 * it was made for another version of the game; a click on a tile puts the pack in use. The search narrows the tiles;
 * the folder button opens the packs' folder, and files dropped on the window are copied there. Done (or Escape)
 * applies the choice, as the game's screen does.
 * <p>
 * The choosing itself is the game's own ({@link PackSelectionModel}): this screen only shows it differently.
 */
public final class GamePacksScreen extends StyledScreen {
    private static final String VANILLA = "net.minecraft.client.gui.screens.packs.PackSelectionScreen";
    private static final Identifier DEFAULT_ICON = Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");
    private static final int KEY_F = 70;
    private static final int ROW_HEIGHT = 36;
    private static final int ROW_ICON = 28;
    private static final int ACTION = 14;
    private static final int TILE_MIN_WIDTH = 84;
    private static final int TILE_HEIGHT = 76;
    private static final int TILE_ICON = 36;
    private static final int TILE_GAP = 6;

    private final @Nullable Screen parent;
    private final Path packDir;
    private final PackSelectionModel model;
    private final Map<String, Identifier> icons = new HashMap<>();
    private String query = "";
    // Set when the packs changed: the lists are filled again on the next tick, not while a row handles a click.
    private boolean dirty;
    private boolean applied;
    private @Nullable Rows available;
    private @Nullable Rows selected;
    private @Nullable StyledEditBox search;
    private int availableCount;
    private int selectedCount;
    // The pack in use being dragged to another place in the stack, and the pack the mouse is over.
    private @Nullable String dragging;
    private @Nullable String hoveredInUse;

    private GamePacksScreen(@Nullable Screen parent, Component title, PackRepository repository, Consumer<PackRepository> output, Path packDir) {
        super(title, ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.parent = parent;
        this.packDir = packDir;
        this.model = new PackSelectionModel(entry -> this.dirty = true, this::iconOf, repository, output);
    }

    /**
     * The screen to open instead: this one for the game's own pack selection screen, choosing among the same packs and
     * handing the choice to the same place.
     */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (screen == null || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replacePacks()) return screen;
        try {
            Object model = field(PackSelectionScreen.class, screen, "model");
            PackRepository repository = field(PackSelectionModel.class, model, "repository");
            Consumer<PackRepository> output = field(PackSelectionModel.class, model, "output");
            Path packDir = field(PackSelectionScreen.class, screen, "packDir");
            return new GamePacksScreen(Minecraft.getInstance().gui.screen(), screen.getTitle(), repository, output, packDir);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            GlazedMenu.LOGGER.warn("Couldn't read the game's pack screen; it opens as it is", e);
            return screen;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Class<?> owner, Object object, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(object);
    }

    // --- Layout ---

    @Override
    protected void init() {
        int searchWidth = Math.clamp(this.width / 3, 100, 200);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18,
                Component.translatableWithFallback("glazedmenu.packs.search", "Search packs")));
        this.search.setPosition(this.frameLeft() + this.frameWidth() - 6 - 20 - TOGGLE_ROOM - searchWidth, this.topBarY() + 5);
        this.search.setPlaceholder(Component.translatableWithFallback("glazedmenu.packs.search.hint", "Search packs… (Ctrl+F)"));
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            this.fill();
        });
        this.addModeToggleAtEnd(6).setY(this.search.getY() - 1);

        int bodyTop = this.bodyTop() + 16;
        int bodyHeight = this.bodyBottom() - bodyTop;
        int stackWidth = this.stackWidth();
        int tilesX = this.frameLeft() + stackWidth + GAP;
        int tilesWidth = this.frameLeft() + this.frameWidth() - tilesX;
        this.selected = this.addRenderableWidget(new Rows(this.minecraft, stackWidth - 8, bodyHeight - 6, bodyTop + 2));
        this.selected.updateSizeAndPosition(stackWidth - 8, bodyHeight - 6, this.frameLeft() + 4, bodyTop + 2);
        this.available = this.addRenderableWidget(new Rows(this.minecraft, tilesWidth - 8, bodyHeight - 6, bodyTop + 2));
        this.available.updateSizeAndPosition(tilesWidth - 8, bodyHeight - 6, tilesX + 4, bodyTop + 2);

        int y = this.barWidgetY(this.bottomBarY());
        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.onClose())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        done.setPosition(this.frameLeft() + this.frameWidth() - 4 - 84, y);
        Component folder = Component.translatable("pack.openFolder");
        int folderWidth = this.font.width(folder) + 26;
        FlatButton folderButton = this.addRenderableWidget(FlatButton.of(folder, folderWidth, button -> Util.getPlatform().openPath(this.packDir))
                .withIcon(ConfigIcons.FOLDER).tooltip(Component.translatable("pack.folderInfo")));
        folderButton.setPosition(done.getX() - 4 - folderWidth, y);
        FlatButton refresh = this.addRenderableWidget(FlatButton.icon(ConfigIcons.RESET,
                Component.translatableWithFallback("glazedmenu.packs.refresh", "Look for new packs in the folder"), button -> this.refresh()));
        refresh.setPosition(folderButton.getX() - 4 - 20, y);
        this.statusRight = refresh.getX() - 8;
        this.fill();
    }

    private int statusRight;

    // The stack of packs in use: a narrower pane than the tiles'.
    private int stackWidth() {
        return Math.clamp(this.frameWidth() * 2 / 5, 140, 230);
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    // Both lists, from the game's model, narrowed by the search.
    private void fill() {
        if (this.available == null || this.selected == null) return;
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        List<PackSelectionModel.Entry> available = this.model.getUnselected().toList();
        List<PackSelectionModel.Entry> selected = this.model.getSelected().toList();
        this.availableCount = available.size();
        this.selectedCount = selected.size();
        // The stack is always whole (its order is the point); the search narrows the tiles.
        List<Row> stack = new ArrayList<>();
        for (PackSelectionModel.Entry entry : selected) stack.add(new PackRow(entry));
        if (stack.isEmpty()) stack.add(new Notice(Component.translatableWithFallback("glazedmenu.packs.none.selected", "No packs in use")));
        this.set(this.selected, stack);

        List<PackSelectionModel.Entry> found = available.stream().filter(entry -> query.isEmpty()
                || entry.getTitle().getString().toLowerCase(Locale.ROOT).contains(query)
                || entry.getDescription().getString().toLowerCase(Locale.ROOT).contains(query)
                || entry.getId().toLowerCase(Locale.ROOT).contains(query)).toList();
        List<Row> tiles = new ArrayList<>();
        int rowWidth = this.available.getRowWidth();
        int columns = Math.max(1, (rowWidth + TILE_GAP) / (TILE_MIN_WIDTH + TILE_GAP));
        for (int i = 0; i < found.size(); i += columns) tiles.add(new TileRow(found.subList(i, Math.min(found.size(), i + columns)), columns));
        if (tiles.isEmpty()) {
            tiles.add(new Notice(query.isEmpty() ? Component.translatableWithFallback("glazedmenu.packs.none.available", "No more packs to add")
                    : Component.translatableWithFallback("glazedmenu.packs.none.search", "No packs match")));
        }
        this.set(this.available, tiles);
    }

    private void set(Rows list, List<Row> rows) {
        double scroll = list.scrollAmount();
        list.setRows(rows);
        list.setScrollAmount(scroll);
    }

    // Moves a pack in use to another's place in the stack, a step at a time, as far as the game lets it go.
    private void moveTo(String id, String targetId) {
        for (int step = 0; step < 64; step++) {
            List<PackSelectionModel.Entry> stack = this.model.getSelected().toList();
            int from = -1;
            int to = -1;
            for (int i = 0; i < stack.size(); i++) {
                if (stack.get(i).getId().equals(id)) from = i;
                if (stack.get(i).getId().equals(targetId)) to = i;
            }
            if (from < 0 || to < 0 || from == to) return;
            PackSelectionModel.Entry entry = stack.get(from);
            if (from > to && entry.canMoveUp()) entry.moveUp();
            else if (from < to && entry.canMoveDown()) entry.moveDown();
            else return;
        }
    }

    // A pack let go of over another takes that one's place.
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging != null) {
            if (this.hoveredInUse != null && !this.hoveredInUse.equals(this.dragging)) {
                this.moveTo(this.dragging, this.hoveredInUse);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            this.dragging = null;
            this.dirty = true;
        }
        return super.mouseReleased(event);
    }

    // A pack's own icon (its pack.png), loaded once; the game's question mark for a pack without one.
    private Identifier iconOf(Pack pack) {
        return this.icons.computeIfAbsent(pack.getId(), id -> {
            try (PackResources resources = pack.open()) {
                IoSupplier<InputStream> icon = resources.getRootResource("pack.png");
                if (icon == null) return DEFAULT_ICON;
                Identifier texture = GlazedMenu.rl("pack_icon/" + Integer.toHexString(id.hashCode()) + "_" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
                try (InputStream stream = icon.get()) {
                    NativeImage image = NativeImage.read(stream);
                    Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(texture::toString, image));
                    return texture;
                }
            } catch (Exception e) {
                GlazedMenu.LOGGER.debug("Couldn't load the icon of the pack {}", id, e);
                return DEFAULT_ICON;
            }
        });
    }

    // --- Changing the packs ---

    @Override
    public void tick() {
        super.tick();
        if (this.dirty) {
            this.dirty = false;
            this.fill();
        }
    }

    private void refresh() {
        this.model.findNewPacks();
        this.dirty = true;
    }

    // A pack made for another version of the game is put in use only once the player agrees, as the game asks.
    private void select(PackSelectionModel.Entry entry) {
        if (!entry.canSelect()) return;
        if (entry.getCompatibility().isCompatible()) {
            entry.select();
            return;
        }
        this.minecraft.gui.setScreen(new ConfirmPopup(this, this.theme, Component.translatable("pack.incompatible.confirm.title"),
                entry.getCompatibility().getConfirmation(), CommonComponents.GUI_YES, false, use -> {
                    if (use) entry.select();
                    this.minecraft.gui.setScreen(this);
                }));
    }

    // Files dropped on the window are copied into the packs' folder (the game's own copying), once the player agrees.
    @Override
    public void onFilesDrop(List<Path> files) {
        String names = files.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", "));
        this.minecraft.gui.setScreen(new ConfirmPopup(this, this.theme, Component.translatable("pack.dropConfirm"), Component.literal(names),
                CommonComponents.GUI_YES, false, copy -> {
                    this.minecraft.gui.setScreen(this);
                    if (!copy) return;
                    try {
                        Method copyPacks = PackSelectionScreen.class.getDeclaredMethod("copyPacks", Minecraft.class, List.class, Path.class);
                        copyPacks.setAccessible(true);
                        copyPacks.invoke(null, this.minecraft, files, this.packDir);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        GlazedMenu.LOGGER.warn("Couldn't copy the dropped packs", e);
                        GlazedClient.toast(this.title, Component.translatable("pack.copyFailure"));
                    }
                    this.refresh();
                }));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.key() == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        return super.keyPressed(event);
    }

    // --- Drawing ---

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        int stackWidth = this.stackWidth();
        int tilesX = this.frameLeft() + stackWidth + GAP;
        int tilesWidth = this.frameLeft() + this.frameWidth() - tilesX;
        this.hoveredInUse = null;
        ConfigStyle.panel(graphics, this.frameLeft(), bodyTop, stackWidth, bodyHeight, false);
        ConfigStyle.panel(graphics, tilesX, bodyTop, tilesWidth, bodyHeight);
        // The stack of packs in use carries Glazed Menu's gradient along its top edge.
        GlazedBrand.fill(graphics, this.frameLeft() + 1, bodyTop, stackWidth - 2, 2, 0xD0);
        this.paneTitle(graphics, Component.translatable("pack.selected.title"), this.selectedCount, this.frameLeft(), bodyTop, stackWidth, true);
        this.paneTitle(graphics, Component.translatable("pack.available.title"), this.availableCount, tilesX, bodyTop, tilesWidth, false);

        int searchLeft = this.search != null ? this.search.getX() - 18 : this.width;
        this.extractTitle(graphics, this.title, Math.max(40, searchLeft - this.frameLeft() - 40), (g, x, y, size) -> {
            ConfigIcons.FOLDER.draw(g, x, y, size, ConfigStyle.accent());
            return true;
        });
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }
        int x = this.frameLeft() + 10;
        ConfigStyle.text(graphics, this.font, this.dragging != null
                        ? Component.translatableWithFallback("glazedmenu.packs.dragging", "Let go over the pack whose place it takes")
                        : Component.translatableWithFallback("glazedmenu.packs.hint", "Top overrides those below · drag to reorder · drop files to add"), x, this.bottomBarY() + (BAR_HEIGHT - 8) / 2, this.statusRight - x,
                ConfigStyle.colors().textMuted());
    }

    // A pane's name and how many packs it holds, above its list.
    private void paneTitle(GuiGraphicsExtractor graphics, Component name, int count, int x, int y, int width, boolean accent) {
        Component title = name.copy().withStyle(ChatFormatting.BOLD);
        int color = accent ? ConfigStyle.accent() : ConfigStyle.colors().textDim();
        ConfigStyle.text(graphics, this.font, title, x + 10, y + 7, width - 50, color);
        Component number = Component.literal(Integer.toString(count));
        int numberWidth = this.font.width(number) + 6;
        ConfigStyle.rect(graphics, x + width - numberWidth - 8, y + 5, numberWidth, 11, ConfigStyle.withAlpha(color, 0x30));
        graphics.text(this.font, number, x + width - numberWidth - 5, y + 7, color, false);
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    // Leaving applies the choice, as the game's screen does: where the choice goes says which screen comes next.
    @Override
    public void onClose() {
        if (this.applied) return;
        this.applied = true;
        this.model.commit();
        // Where the choice went opened no screen of its own: back to the one before.
        if (this.minecraft.gui.screen() == this) this.minecraft.gui.setScreen(this.parent);
    }

    // --- The packs ---

    private static final class Rows extends ContainerObjectSelectionList<Row> {
        Rows(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_HEIGHT);
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        @Override
        public int getRowLeft() {
            return this.getX() + 3;
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
            ConfigStyle.rect(graphics, this.scrollBarX(), this.scrollBarY(), 3, this.scrollerHeight(), ConfigStyle.withAlpha(ConfigStyle.accent(), 0xB0));
        }
    }

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        int height() {
            return ROW_HEIGHT;
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

    // The packs to choose from, a few to a row: each a tile of its icon over its name and where it's from (or what's
    // wrong with it); a click puts it in use.
    private final class TileRow extends Row {
        private final List<PackSelectionModel.Entry> tiles;
        private final int columns;

        TileRow(List<PackSelectionModel.Entry> tiles, int columns) {
            this.tiles = List.copyOf(tiles);
            this.columns = columns;
        }

        @Override
        int height() {
            return TILE_HEIGHT + TILE_GAP;
        }

        private int tileWidth() {
            return (this.getWidth() - TILE_GAP * (this.columns - 1)) / this.columns;
        }

        private PackSelectionModel.@Nullable Entry at(double mouseX, double mouseY) {
            int y = this.getY() + TILE_GAP / 2;
            if (mouseY < y || mouseY >= y + TILE_HEIGHT) return null;
            int width = this.tileWidth();
            for (int i = 0; i < this.tiles.size(); i++) {
                int x = this.getX() + i * (width + TILE_GAP);
                if (mouseX >= x && mouseX < x + width) return this.tiles.get(i);
            }
            return null;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GamePacksScreen.this.font;
            int width = this.tileWidth();
            int y = this.getY() + TILE_GAP / 2;
            PackSelectionModel.Entry over = hovered ? this.at(mouseX, mouseY) : null;
            for (int i = 0; i < this.tiles.size(); i++) {
                PackSelectionModel.Entry entry = this.tiles.get(i);
                int x = this.getX() + i * (width + TILE_GAP);
                boolean lit = entry == over;
                boolean compatible = entry.getCompatibility().isCompatible();
                int edge = compatible ? ConfigStyle.accent() : ConfigStyle.colors().warning();
                ConfigStyle.rect(graphics, x, y, width, TILE_HEIGHT, lit ? ConfigStyle.colors().buttonHover() : ConfigStyle.colors().button());
                ConfigStyle.outline(graphics, x, y, width, TILE_HEIGHT, lit ? ConfigStyle.withAlpha(edge, 0xE0) : compatible ? ConfigStyle.colors().panelBorder() : ConfigStyle.withAlpha(edge, 0x90));
                graphics.blit(RenderPipelines.GUI_TEXTURED, entry.getIconTexture(), x + (width - TILE_ICON) / 2, y + 7, 0, 0, TILE_ICON, TILE_ICON, 64, 64, 64, 64);
                if (lit) {
                    // What a click does, over the icon.
                    ConfigStyle.rect(graphics, x + (width - TILE_ICON) / 2, y + 7, TILE_ICON, TILE_ICON, 0x90000000);
                    ConfigIcons.PLUS.draw(graphics, x + (width - 16) / 2, y + 7 + (TILE_ICON - 16) / 2, 16, 0xFFFFFFFF);
                }
                Component title = entry.getTitle().copy().withStyle(ChatFormatting.BOLD);
                ConfigStyle.centeredText(graphics, font, title, x + width / 2, y + TILE_ICON + 12, width - 8,
                        compatible ? ConfigStyle.colors().text() : ConfigStyle.colors().warning());
                Component note = compatible ? entry.getPackSource().decorate(Component.empty()).getString().isBlank()
                        ? Component.translatableWithFallback("glazedmenu.packs.tile.add", "Click to use") : entry.getPackSource().decorate(Component.empty())
                        : entry.getCompatibility().getDescription();
                ConfigStyle.centeredText(graphics, font, Component.literal(note.getString().lines().findFirst().orElse("").trim()), x + width / 2, y + TILE_ICON + 24, width - 8,
                        compatible ? ConfigStyle.colors().textDim() : ConfigStyle.colors().warning());
            }
            if (over != null) graphics.setTooltipForNextFrame(font, font.split(over.getExtendedDescription(), 220), mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            PackSelectionModel.Entry entry = this.at(event.x(), event.y());
            if (entry == null || !entry.canSelect()) return false;
            GamePacksScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            GamePacksScreen.this.select(entry);
            GamePacksScreen.this.dirty = true;
            return true;
        }
    }

    // One pack in use: its icon, its name over its description (or what's wrong with it), and at the right arrows to
    // move it and a cross to take it out; a lock where the game requires it. Dragged, it goes where it's let go.
    private final class PackRow extends Row {
        private final PackSelectionModel.Entry entry;

        PackRow(PackSelectionModel.Entry entry) {
            this.entry = entry;
        }

        // The actions' places, right to left: 0 is the rightmost.
        private int actionX(int index) {
            return this.getX() + this.getWidth() - 6 - (index + 1) * (ACTION + 2);
        }

        private boolean over(int index, double mouseX, double mouseY) {
            int x = this.actionX(index);
            int y = this.getY() + (this.getHeight() - ACTION) / 2;
            return mouseX >= x && mouseX < x + ACTION && mouseY >= y && mouseY < y + ACTION;
        }

        private void action(GuiGraphicsExtractor graphics, ConfigIcons.Icon icon, int index, boolean enabled, int mouseX, int mouseY) {
            int x = this.actionX(index);
            int y = this.getY() + (this.getHeight() - ACTION) / 2;
            boolean over = enabled && this.over(index, mouseX, mouseY);
            if (over) ConfigStyle.rect(graphics, x, y, ACTION, ACTION, ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x26));
            icon.draw(graphics, x + 2, y + 2, ACTION - 4, !enabled ? ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0x70)
                    : over ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GamePacksScreen.this.font;
            int x = this.getX();
            int y = this.getY() + 1;
            int width = this.getWidth();
            int height = this.getHeight() - 2;
            boolean inUse = this.entry.isSelected();
            boolean compatible = this.entry.getCompatibility().isCompatible();
            int accent = ConfigStyle.accent();
            GamePacksScreen screen = GamePacksScreen.this;
            boolean dragged = this.entry.getId().equals(screen.dragging);
            if (hovered) screen.hoveredInUse = this.entry.getId();
            ConfigStyle.rect(graphics, x, y, width, height, dragged ? ConfigStyle.withAlpha(accent, 0x38)
                    : hovered ? ConfigStyle.colors().rowHover() : ConfigStyle.withAlpha(ConfigStyle.colors().button(), 0x70));
            if (inUse) ConfigStyle.rect(graphics, x, y + 3, 2, height - 6, compatible ? accent : ConfigStyle.colors().warning());
            // Where a dragged pack lands: this one's place.
            if (screen.dragging != null && hovered && !dragged) ConfigStyle.outline(graphics, x, y, width, height, accent);
            int iconY = y + (height - ROW_ICON) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, this.entry.getIconTexture(), x + 6, iconY, 0, 0, ROW_ICON, ROW_ICON, 64, 64, 64, 64);

            int actions;
            if (!inUse) {
                actions = 1;
                this.action(graphics, ConfigIcons.PLUS, 0, this.entry.canSelect(), mouseX, mouseY);
            } else if (!this.entry.canUnselect()) {
                actions = 1;
                ConfigIcons.LOCK.draw(graphics, this.actionX(0) + 2, y + (height - ACTION) / 2 + 2, ACTION - 4, ConfigStyle.colors().textMuted());
            } else {
                actions = 3;
                this.action(graphics, ConfigIcons.CLOSE, 0, true, mouseX, mouseY);
                this.action(graphics, ConfigIcons.DOWN, 1, this.entry.canMoveDown(), mouseX, mouseY);
                this.action(graphics, ConfigIcons.UP, 2, this.entry.canMoveUp(), mouseX, mouseY);
            }
            int textX = x + 6 + ROW_ICON + 7;
            int textWidth = this.actionX(actions - 1) - 4 - textX;
            ConfigStyle.text(graphics, font, this.entry.getTitle().copy().withStyle(ChatFormatting.BOLD), textX, y + 7, textWidth,
                    compatible ? ConfigStyle.colors().text() : ConfigStyle.colors().warning());
            Component note = compatible ? this.entry.getDescription() : this.entry.getCompatibility().getDescription();
            ConfigStyle.text(graphics, font, Component.literal(note.getString().lines().findFirst().orElse("")), textX, y + 19, textWidth,
                    compatible ? ConfigStyle.colors().textDim() : ConfigStyle.colors().warning());
            if (hovered && mouseX < this.actionX(actions - 1)) {
                graphics.setTooltipForNextFrame(font, font.split(this.entry.getExtendedDescription(), 220), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GamePacksScreen screen = GamePacksScreen.this;
            boolean inUse = this.entry.isSelected();
            boolean acted = true;
            if (!inUse) {
                // Anywhere on an available pack puts it in use.
                if (this.entry.canSelect()) screen.select(this.entry);
                else acted = false;
            } else if (!this.entry.canUnselect()) {
                acted = false;
            } else if (this.over(0, event.x(), event.y())) {
                this.entry.unselect();
            } else if (this.over(1, event.x(), event.y()) && this.entry.canMoveDown()) {
                this.entry.moveDown();
            } else if (this.over(2, event.x(), event.y()) && this.entry.canMoveUp()) {
                this.entry.moveUp();
            } else {
                // Anywhere else on a pack that can move picks it up, to drag.
                if (this.entry.canMoveUp() || this.entry.canMoveDown()) screen.dragging = this.entry.getId();
                acted = false;
            }
            if (acted) {
                screen.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                screen.dirty = true;
            }
            return true;
        }
    }

    private final class Notice extends Row {
        private final Component text;

        Notice(Component text) {
            this.text = text;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            ConfigStyle.centeredText(graphics, GamePacksScreen.this.font, this.text, this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2, this.getWidth() - 8, ConfigStyle.colors().textMuted());
        }
    }
}
