package net.ixdarklord.glazedmenu.internal.game;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.glazedmenu.internal.core.GlazedClient;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.ixdarklord.glazedmenu.internal.style.ToggleSwitch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
import org.joml.Matrix3x2fStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shader packs (Iris's), in Glazed Menu's look, in place of Iris's own list of them, and laid out as the packs
 * screen is. On the left, what's in use: whether shaders are on, the chosen pack, and a button to that pack's own
 * settings (which stay Iris's screen: each pack defines its own). On the right, the packs in Iris's folder as tiles; a
 * click chooses one. The search narrows the tiles; the folder button opens the folder, and shader packs dropped on the
 * window are copied there. Apply (or Done) tells Iris the choice, which loads the pack; Cancel leaves things as they
 * were.
 */
public final class GameShadersScreen extends StyledScreen {
    // SDL's codes since 26.3: a shortcut's letter is its keycode (KeyEvent.shortcutKey), any other key its scancode (KeyEvent.key).
    private static final int KEY_F = InputConstants.KEYCODE_F;
    private static final int TILE_MIN_WIDTH = 84;
    private static final int TILE_HEIGHT = 70;
    private static final int TILE_ICON = 30;
    private static final int TILE_GAP = 6;

    private final @Nullable Screen parent;
    private List<String> packs;
    // The player's choice, until applied.
    private @Nullable String chosen;
    private boolean enabled;
    private String query = "";
    private @Nullable Rows tiles;
    private @Nullable StyledEditBox search;
    private @Nullable FlatButton applyButton;
    private @Nullable FlatButton settingsButton;

    public GameShadersScreen(@Nullable Screen parent) {
        super(Component.translatableWithFallback("glazedmenu.shaders.title", "Shader Packs"), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.parent = parent;
        this.packs = ShaderBridge.packs();
        this.chosen = ShaderBridge.chosenPack().orElse(null);
        this.enabled = ShaderBridge.enabled();
    }

    /** The screen to open instead: this one for Iris's shader pack screen, unless it's opened for a pack's settings. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (screen == null || !screen.getClass().getName().equals(ShaderBridge.IRIS_SCREEN)) return screen;
        if (ShaderBridge.isPassing(screen) || !GlazedSettings.replaceShaders()) return screen;
        return new GameShadersScreen(Minecraft.getInstance().gui.screen());
    }

    private boolean changed() {
        return this.enabled != ShaderBridge.enabled() || !java.util.Objects.equals(this.chosen, ShaderBridge.chosenPack().orElse(null));
    }

    // --- Layout ---

    private int sideWidth() {
        return Math.clamp(this.frameWidth() * 2 / 5, 140, 230);
    }

    @Override
    protected void init() {
        int searchWidth = Math.clamp(this.width / 3, 100, 200);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18,
                Component.translatableWithFallback("glazedmenu.shaders.search", "Search shader packs")));
        this.search.setPosition(this.frameLeft() + this.frameWidth() - 6 - 20 - TOGGLE_ROOM - searchWidth, this.topBarY() + 5);
        this.search.setPlaceholder(Component.translatableWithFallback("glazedmenu.shaders.search.hint", "Search shader packs… (Ctrl+F)"));
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            this.fill();
        });
        this.addModeToggleAtEnd(6).setY(this.search.getY() - 1);

        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        int side = this.sideWidth();
        int tilesX = this.frameLeft() + side + GAP;
        int tilesWidth = this.frameLeft() + this.frameWidth() - tilesX;
        this.tiles = this.addRenderableWidget(new Rows(this.minecraft, tilesWidth - 8, bodyHeight - 22, bodyTop + 18));
        this.tiles.updateSizeAndPosition(tilesWidth - 8, bodyHeight - 22, tilesX + 4, bodyTop + 18);

        // The side pane: shaders on or off, and the chosen pack's own settings.
        ToggleSwitch toggle = this.addRenderableWidget(new ToggleSwitch(58, 16, () -> this.enabled, button -> this.enabled = !this.enabled));
        toggle.setPosition(this.frameLeft() + side - 10 - 58, bodyTop + 22);
        Component settings = Component.translatableWithFallback("glazedmenu.shaders.settings", "Pack Settings");
        this.settingsButton = this.addRenderableWidget(FlatButton.of(settings, side - 20, button -> this.openPackSettings()).withIcon(ConfigIcons.GEAR)
                .tooltip(Component.translatableWithFallback("glazedmenu.shaders.settings.tooltip", "The pack's own settings, in Iris's screen")));
        this.settingsButton.setPosition(this.frameLeft() + 10, this.bodyBottom() - 26);

        int y = this.barWidgetY(this.bottomBarY());
        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.done())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        done.setPosition(this.frameLeft() + this.frameWidth() - 4 - 84, y);
        Component apply = Component.translatableWithFallback("glazedmenu.shaders.apply", "Apply");
        this.applyButton = this.addRenderableWidget(FlatButton.of(apply, 60, button -> this.apply()));
        this.applyButton.setPosition(done.getX() - 4 - 60, y);
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, 60, button -> this.onClose()));
        cancel.setPosition(this.applyButton.getX() - 4 - 60, y);
        FlatButton folder = this.addRenderableWidget(FlatButton.icon(ConfigIcons.FOLDER,
                Component.translatableWithFallback("glazedmenu.shaders.folder", "Open the shader packs folder"), button -> {
                    Path path = ShaderBridge.folder();
                    if (path != null) Blaze3D.openPath(path);
                }));
        folder.setPosition(cancel.getX() - 8 - 20, y);
        FlatButton refresh = this.addRenderableWidget(FlatButton.icon(ConfigIcons.RESET,
                Component.translatableWithFallback("glazedmenu.shaders.refresh", "Look for new shader packs in the folder"), button -> this.refresh()));
        refresh.setPosition(folder.getX() - 4 - 20, y);
        this.statusRight = refresh.getX() - 8;
        this.fill();
    }

    private int statusRight;

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    private void refresh() {
        this.packs = ShaderBridge.packs();
        this.fill();
    }

    private void fill() {
        if (this.tiles == null) return;
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        List<String> found = this.packs.stream().filter(pack -> query.isEmpty() || pack.toLowerCase(Locale.ROOT).contains(query)).toList();
        List<Row> rows = new ArrayList<>();
        int rowWidth = this.tiles.getRowWidth();
        int columns = Math.max(1, (rowWidth + TILE_GAP) / (TILE_MIN_WIDTH + TILE_GAP));
        for (int i = 0; i < found.size(); i += columns) rows.add(new TileRow(found.subList(i, Math.min(found.size(), i + columns)), columns));
        if (rows.isEmpty()) {
            rows.add(new Notice(this.packs.isEmpty()
                    ? Component.translatableWithFallback("glazedmenu.shaders.none", "No shader packs yet: drop some here, or put them in the folder")
                    : Component.translatableWithFallback("glazedmenu.shaders.none.search", "No shader packs match")));
        }
        double scroll = this.tiles.scrollAmount();
        this.tiles.setRows(rows);
        this.tiles.setScrollAmount(scroll);
    }

    // --- Choosing ---

    @Override
    public void tick() {
        super.tick();
        if (this.applyButton != null) this.applyButton.active = this.changed();
        if (this.settingsButton != null) this.settingsButton.active = this.chosen != null && !this.changed();
    }

    private void apply() {
        if (!this.changed()) return;
        if (!ShaderBridge.apply(this.chosen, this.enabled)) {
            GlazedClient.toast(this.title, Component.translatableWithFallback("glazedmenu.shaders.failed", "Iris didn't take the choice; its own screen opens instead"));
            Screen iris = ShaderBridge.irisScreen(this.parent);
            if (iris != null) this.minecraft.gui.setScreen(iris);
        }
    }

    private void done() {
        this.apply();
        if (this.minecraft.gui.screen() == this) this.minecraft.gui.setScreen(this.parent);
    }

    // A pack's settings are the pack's own to define: Iris's screen shows them, for the pack in use.
    private void openPackSettings() {
        Screen iris = ShaderBridge.irisScreen(this);
        if (iris != null) this.leave(iris);
    }

    // Shader packs dropped on the window are copied into the folder; anything else is said not to be one.
    @Override
    public void onFilesDrop(List<Path> files) {
        int added = 0;
        for (Path file : files) if (ShaderBridge.addPack(file)) added++;
        GlazedClient.toast(this.title, added > 0 ? Component.translatableWithFallback("glazedmenu.shaders.added", "%s shader packs added", added)
                : Component.translatableWithFallback("glazedmenu.shaders.not_added", "That isn't a shader pack"));
        this.refresh();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.shortcutKey() == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        return super.keyPressed(event);
    }

    // --- Drawing ---

    // A pack's color, from its name: shader packs have no icons of their own.
    private static int colorOf(String pack) {
        int[] colors = {GlazedBrand.identity(0xFF8B7CF6), GlazedBrand.identity(0xFF4DA3FF), GlazedBrand.identity(0xFFF2A33B), GlazedBrand.identity(0xFF3CC8B4), GlazedBrand.identity(0xFF5BC467), GlazedBrand.identity(0xFFE86BA8), GlazedBrand.identity(0xFFB07CF0), GlazedBrand.identity(0xFF4CC9E0), GlazedBrand.identity(0xFFF07A5A)};
        return colors[Math.floorMod(pack.hashCode(), colors.length)];
    }

    // A pack's name as shown: without the archive's ending.
    private static String nameOf(String pack) {
        return pack.toLowerCase(Locale.ROOT).endsWith(".zip") ? pack.substring(0, pack.length() - 4) : pack;
    }

    private void mark(GuiGraphicsExtractor graphics, String pack, int x, int y, int size) {
        int color = colorOf(pack);
        ConfigStyle.rect(graphics, x, y, size, size, color);
        String name = nameOf(pack);
        Component letter = Component.literal(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
        float scale = Math.max(1, size / 14F);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + size / 2F, y + size / 2F);
        pose.scale(scale, scale);
        graphics.text(this.font, letter, -this.font.width(letter) / 2, -4, ConfigStyle.readableOn(color), false);
        pose.popMatrix();
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        int side = this.sideWidth();
        int tilesX = this.frameLeft() + side + GAP;
        int tilesWidth = this.frameLeft() + this.frameWidth() - tilesX;
        ConfigStyle.panel(graphics, this.frameLeft(), bodyTop, side, bodyHeight, false);
        ConfigStyle.panel(graphics, tilesX, bodyTop, tilesWidth, bodyHeight);
        GlazedBrand.fill(graphics, this.frameLeft() + 1, bodyTop, side - 2, 2, 0xD0);

        // The side pane: in use.
        int x = this.frameLeft() + 10;
        int accent = ConfigStyle.accent();
        ConfigStyle.text(graphics, this.font, Component.translatableWithFallback("glazedmenu.shaders.in_use", "In Use").withStyle(ChatFormatting.BOLD), x, bodyTop + 7, side - 20, accent);
        ConfigStyle.text(graphics, this.font, Component.translatableWithFallback("glazedmenu.shaders.enabled", "Shaders"), x, bodyTop + 26, side - 88,
                this.enabled ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
        graphics.fill(x, bodyTop + 44, this.frameLeft() + side - 10, bodyTop + 45, ConfigStyle.colors().panelBorder());
        int cardY = bodyTop + 52;
        if (this.chosen != null) {
            boolean pending = this.changed();
            this.mark(graphics, this.chosen, x, cardY, 28);
            ConfigStyle.text(graphics, this.font, Component.literal(nameOf(this.chosen)).withStyle(ChatFormatting.BOLD), x + 35, cardY + 4, side - 55,
                    this.enabled ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            Component state = pending ? Component.translatableWithFallback("glazedmenu.shaders.state.pending", "Apply to use it")
                    : this.enabled ? Component.translatableWithFallback("glazedmenu.shaders.state.on", "In use")
                    : Component.translatableWithFallback("glazedmenu.shaders.state.off", "Chosen, with shaders off");
            ConfigStyle.text(graphics, this.font, state, x + 35, cardY + 16, side - 55, pending ? ConfigStyle.colors().modified() : ConfigStyle.colors().textDim());
        } else {
            ConfigStyle.text(graphics, this.font, Component.translatableWithFallback("glazedmenu.shaders.no_pack", "No pack chosen"), x, cardY + 8, side - 20,
                    ConfigStyle.colors().textMuted());
        }
        ConfigStyle.text(graphics, this.font, Component.translatableWithFallback("glazedmenu.shaders.available", "Shader Packs").withStyle(ChatFormatting.BOLD),
                tilesX + 10, bodyTop + 7, tilesWidth - 60, ConfigStyle.colors().textDim());
        Component number = Component.literal(Integer.toString(this.packs.size()));
        int numberWidth = this.font.width(number) + 6;
        ConfigStyle.rect(graphics, tilesX + tilesWidth - numberWidth - 8, bodyTop + 5, numberWidth, 11, ConfigStyle.withAlpha(ConfigStyle.colors().textDim(), 0x30));
        graphics.text(this.font, number, tilesX + tilesWidth - numberWidth - 5, bodyTop + 7, ConfigStyle.colors().textDim(), false);

        int searchLeft = this.search != null ? this.search.getX() - 18 : this.width;
        this.extractTitle(graphics, this.title, Math.max(40, searchLeft - this.frameLeft() - 40), (g, ix, iy, size) -> {
            ConfigIcons.SUN.draw(g, ix, iy, size, ConfigStyle.accent());
            return true;
        });
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }
        Component status = this.changed() ? Component.translatableWithFallback("glazedmenu.shaders.status.pending", "Apply loads your choice")
                : Component.translatableWithFallback("glazedmenu.shaders.status", "Drop shader packs here to add them");
        ConfigStyle.text(graphics, this.font, status, x, this.bottomBarY() + (BAR_HEIGHT - 8) / 2, this.statusRight - x,
                this.changed() ? ConfigStyle.colors().modified() : ConfigStyle.colors().textMuted());
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    public void onClose() {
        this.leave(this.parent);
    }

    // --- The tiles ---

    private static final class Rows extends ContainerObjectSelectionList<Row> {
        Rows(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, TILE_HEIGHT + TILE_GAP);
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
            for (Row row : rows) this.addEntry(row, TILE_HEIGHT + TILE_GAP);
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
        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    // Shader packs, a few to a row: each a tile of its mark over its name; the chosen one is outlined and checked.
    private final class TileRow extends Row {
        private final List<String> packs;
        private final int columns;

        TileRow(List<String> packs, int columns) {
            this.packs = List.copyOf(packs);
            this.columns = columns;
        }

        private int tileWidth() {
            return (this.getWidth() - TILE_GAP * (this.columns - 1)) / this.columns;
        }

        private @Nullable String at(double mouseX, double mouseY) {
            int y = this.getY() + TILE_GAP / 2;
            if (mouseY < y || mouseY >= y + TILE_HEIGHT) return null;
            int width = this.tileWidth();
            for (int i = 0; i < this.packs.size(); i++) {
                int x = this.getX() + i * (width + TILE_GAP);
                if (mouseX >= x && mouseX < x + width) return this.packs.get(i);
            }
            return null;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GameShadersScreen screen = GameShadersScreen.this;
            Font font = screen.font;
            int width = this.tileWidth();
            int y = this.getY() + TILE_GAP / 2;
            String over = hovered ? this.at(mouseX, mouseY) : null;
            for (int i = 0; i < this.packs.size(); i++) {
                String pack = this.packs.get(i);
                int x = this.getX() + i * (width + TILE_GAP);
                boolean lit = pack.equals(over);
                boolean chosen = pack.equals(screen.chosen);
                int color = colorOf(pack);
                ConfigStyle.rect(graphics, x, y, width, TILE_HEIGHT, chosen ? ConfigStyle.mix(ConfigStyle.colors().button(), color, 0.22F)
                        : lit ? ConfigStyle.colors().buttonHover() : ConfigStyle.colors().button());
                ConfigStyle.outline(graphics, x, y, width, TILE_HEIGHT, chosen ? color : lit ? ConfigStyle.withAlpha(color, 0xC0) : ConfigStyle.colors().panelBorder());
                screen.mark(graphics, pack, x + (width - TILE_ICON) / 2, y + 8, TILE_ICON);
                if (chosen) ConfigIcons.CHECK.draw(graphics, x + width - 14, y + 4, 10, color);
                ConfigStyle.centeredText(graphics, font, Component.literal(nameOf(pack)).withStyle(chosen ? ChatFormatting.BOLD : ChatFormatting.RESET),
                        x + width / 2, y + TILE_ICON + 15, width - 8, chosen || lit ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            }
            if (over != null) graphics.setTooltipForNextFrame(font, Component.literal(over), mouseX, mouseY);
        }

        // A click chooses the pack, and turns shaders on: choosing one is asking for it.
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GameShadersScreen screen = GameShadersScreen.this;
            String pack = this.at(event.x(), event.y());
            if (pack == null) return false;
            screen.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            screen.chosen = pack;
            screen.enabled = true;
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
            Font font = GameShadersScreen.this.font;
            int y = this.getY() + 12;
            for (net.minecraft.util.FormattedCharSequence line : font.split(this.text, this.getWidth() - 16)) {
                graphics.text(font, line, this.getX() + (this.getWidth() - font.width(line)) / 2, y, ConfigStyle.colors().textMuted(), false);
                y += 10;
            }
        }
    }
}
