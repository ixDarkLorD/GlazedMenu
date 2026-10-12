package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.ConfigScreen;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.FontOptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The languages, in Glazed Menu's look, in place of the game's own screen: a search (by a language's name, its region
 * or its code), the languages one to a row with the one in use marked, and the font settings a button away. Choosing a
 * language and pressing Done (or double-clicking it, or Enter) switches to it, reloading the game's text as the game's
 * screen does; Cancel leaves the language as it was.
 * <p>
 * Keys: Ctrl+F searches, the arrows move through the list, Enter switches.
 */
public final class GameLanguageScreen extends StyledScreen {
    private static final String VANILLA = "net.minecraft.client.gui.screens.options.LanguageSelectScreen";
    private static final int KEY_F = 70;
    private static final int KEY_ENTER = 257;
    private static final int KEY_DOWN = 264;
    private static final int KEY_UP = 265;
    private static final int ROW_HEIGHT = 22;

    private final @Nullable Screen parent;
    private final Options options;
    private final LanguageManager languages;
    // The language chosen in the list; the one in use until another is picked.
    private String chosen;
    private String query = "";
    private List<String> shown = List.of();
    private @Nullable Rows rows;
    private @Nullable StyledEditBox search;
    private @Nullable FlatButton doneButton;

    public GameLanguageScreen(@Nullable Screen parent, Options options, LanguageManager languages) {
        super(Component.translatable("options.language.title"), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.parent = parent;
        this.options = options;
        this.languages = languages;
        this.chosen = languages.getSelected();
    }

    /** The screen to open instead: this one for the game's own language screen, over the screen that opened it. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (screen == null || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceLanguage()) return screen;
        Minecraft minecraft = Minecraft.getInstance();
        return new GameLanguageScreen(minecraft.gui.screen(), minecraft.options, minecraft.getLanguageManager());
    }

    // --- Layout ---

    @Override
    protected void init() {
        int searchWidth = Math.clamp(this.width / 3, 100, 200);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18,
                Component.translatableWithFallback("glazedmenu.language.search", "Search languages")));
        this.search.setPosition(this.frameLeft() + this.frameWidth() - 6 - 20 - TOGGLE_ROOM - searchWidth, this.topBarY() + 5);
        this.search.setPlaceholder(Component.translatableWithFallback("glazedmenu.language.search.hint", "Search languages… (Ctrl+F)"));
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            this.rebuildRows(false);
        });
        this.addModeToggleAtEnd(6).setY(this.search.getY() - 1);

        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        int listWidth = Math.min(this.frameWidth() - 8, 420);
        this.rows = this.addRenderableWidget(new Rows(this.minecraft, listWidth, bodyHeight - 8, bodyTop + 4));
        this.rows.updateSizeAndPosition(listWidth, bodyHeight - 8, this.frameLeft() + (this.frameWidth() - listWidth) / 2, bodyTop + 4);

        int y = this.barWidgetY(this.bottomBarY());
        this.doneButton = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.done())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        this.doneButton.setPosition(this.frameLeft() + this.frameWidth() - 4 - 84, y);
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, 64, button -> this.onClose()));
        cancel.setPosition(this.doneButton.getX() - 4 - 64, y);
        // The font settings: the game's options at Font where Glazed Menu lists them, the game's own screen otherwise.
        Component font = Component.translatable("options.font");
        int fontWidth = this.font.width(font) + 26;
        FlatButton fontButton = this.addRenderableWidget(FlatButton.of(font, fontWidth, button -> this.leave(this.fontSettings())).withIcon(ConfigIcons.FONT));
        fontButton.setPosition(cancel.getX() - 10 - fontWidth, y);
        this.statusRight = fontButton.getX() - 8;
        this.rebuildRows(true);
    }

    private int statusRight;

    private Screen fontSettings() {
        for (Config config : ConfigSources.forMod("minecraft")) {
            if (config.name().equals("options") && config.find("font.forceUnicodeFont").isPresent()) return ConfigScreen.createAt(this, config, "font", null, null);
        }
        return new FontOptionsScreen(this, this.options);
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    @Override
    protected void setInitialFocus() {
        if (this.search != null && !this.query.isEmpty()) this.setInitialFocus(this.search);
        else super.setInitialFocus();
    }

    // The languages a search finds (all of them without one), in the game's order; the chosen one is brought into view.
    private void rebuildRows(boolean reveal) {
        if (this.rows == null) return;
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        List<String> shown = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, LanguageInfo> entry : this.languages.getLanguages().entrySet()) {
            LanguageInfo info = entry.getValue();
            if (!query.isEmpty() && !entry.getKey().toLowerCase(Locale.ROOT).contains(query) && !info.name().toLowerCase(Locale.ROOT).contains(query)
                    && !info.region().toLowerCase(Locale.ROOT).contains(query)) continue;
            shown.add(entry.getKey());
            rows.add(new LanguageRow(entry.getKey(), info));
        }
        if (rows.isEmpty()) rows.add(new Notice());
        this.shown = shown;
        this.rows.setRows(rows);
        int index = shown.indexOf(this.chosen);
        this.rows.setScrollAmount(reveal && index >= 0 ? Math.max(0, index * ROW_HEIGHT - this.rows.getHeight() / 2 + ROW_HEIGHT / 2) : 0);
    }

    // --- Choosing ---

    private void choose(String code) {
        this.chosen = code;
    }

    // Switches to the chosen language, as the game's screen does: the manager and the options take it, and the game's
    // resources (its text among them) are loaded again.
    private void done() {
        if (!this.chosen.equals(this.languages.getSelected()) && this.languages.getLanguages().containsKey(this.chosen)) {
            this.languages.setSelected(this.chosen);
            this.options.languageCode = this.chosen;
            this.options.save();
            this.minecraft.reloadResourcePacks();
        }
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.key() == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        boolean typing = this.search != null && this.search.isFocused();
        if ((event.key() == KEY_UP || event.key() == KEY_DOWN) && !this.shown.isEmpty() && this.rows != null) {
            int index = this.shown.indexOf(this.chosen);
            int next = Math.clamp(index + (event.key() == KEY_DOWN ? 1 : -1), 0, this.shown.size() - 1);
            this.choose(this.shown.get(next));
            // Kept in view as it moves.
            double top = next * ROW_HEIGHT;
            if (top < this.rows.scrollAmount()) this.rows.setScrollAmount(top);
            else if (top + ROW_HEIGHT > this.rows.scrollAmount() + this.rows.getHeight()) this.rows.setScrollAmount(top + ROW_HEIGHT - this.rows.getHeight());
            return true;
        }
        if (event.key() == KEY_ENTER && (!typing || this.shown.contains(this.chosen))) {
            this.done();
            return true;
        }
        return super.keyPressed(event);
    }

    // --- Drawing ---

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        ConfigStyle.panel(graphics, this.frameLeft(), this.bodyTop(), this.frameWidth(), this.bodyBottom() - this.bodyTop());
        GlazedBrand.fill(graphics, this.frameLeft() + 1, this.bodyTop(), this.frameWidth() - 2, 2, 0xD0);
        int searchLeft = this.search != null ? this.search.getX() - 18 : this.width;
        this.extractTitle(graphics, this.title, Math.max(40, searchLeft - this.frameLeft() - 40), (g, x, y, size) -> {
            ConfigIcons.GLOBE.draw(g, x, y, size, ConfigStyle.accent());
            return true;
        });
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }
        // What Done will do, at the left of the bottom bar.
        boolean switching = !this.chosen.equals(this.languages.getSelected());
        LanguageInfo info = this.languages.getLanguage(this.chosen);
        Component status = switching && info != null
                ? Component.translatableWithFallback("glazedmenu.language.status.switch", "Done switches to %s", info.name())
                : Component.translatableWithFallback("glazedmenu.language.status", "%s languages", this.languages.getLanguages().size());
        int x = this.frameLeft() + 10;
        ConfigStyle.text(graphics, this.font, status, x, this.bottomBarY() + (BAR_HEIGHT - 8) / 2, this.statusRight - x,
                switching ? ConfigStyle.colors().modified() : ConfigStyle.colors().textMuted());
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    public void onClose() {
        this.leave(this.parent);
    }

    // --- The languages ---

    private static final class Rows extends ContainerObjectSelectionList<Row> {
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
            for (Row row : rows) this.addEntry(row, ROW_HEIGHT);
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
        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    // A language: its name, then its region dim, and its code at the right; a check marks the one in use, and an accent
    // bar the one chosen.
    private final class LanguageRow extends Row {
        private final String code;
        private final LanguageInfo info;

        LanguageRow(String code, LanguageInfo info) {
            this.code = code;
            this.info = info;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GameLanguageScreen screen = GameLanguageScreen.this;
            Font font = screen.font;
            int x = this.getX();
            int y = this.getY() + 1;
            int width = this.getWidth();
            int height = this.getHeight() - 2;
            boolean chosen = this.code.equals(screen.chosen);
            boolean inUse = this.code.equals(screen.languages.getSelected());
            int accent = ConfigStyle.accent();
            if (chosen) {
                ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.withAlpha(accent, 0x2A));
                ConfigStyle.rect(graphics, x, y + 3, 2, height - 6, accent);
            } else if (hovered) {
                ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.colors().rowHover());
            }
            Component code = Component.literal(this.code);
            int codeWidth = font.width(code);
            int right = x + width - 8;
            graphics.text(font, code, right - codeWidth, y + (height - 8) / 2, ConfigStyle.colors().textMuted(), false);
            if (inUse) ConfigIcons.CHECK.draw(graphics, right - codeWidth - 16, y + (height - ConfigIcons.SIZE) / 2, accent);
            Component name = Component.literal(this.info.name()).withStyle(chosen || inUse ? ChatFormatting.BOLD : ChatFormatting.RESET)
                    .append(Component.literal("  " + this.info.region()).withStyle(style -> style.withBold(false).withColor(ConfigStyle.colors().textDim() & 0xFFFFFF)));
            ConfigStyle.text(graphics, font, name, x + 10, y + (height - 8) / 2, right - codeWidth - 22 - (x + 10),
                    chosen || hovered || inUse ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
        }

        // A click chooses; a second click on the same row switches.
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GameLanguageScreen screen = GameLanguageScreen.this;
            screen.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (doubleClick && this.code.equals(screen.chosen)) screen.done();
            else screen.choose(this.code);
            return true;
        }
    }

    private final class Notice extends Row {
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GameLanguageScreen.this.font;
            Component text = Component.translatableWithFallback("glazedmenu.language.none", "No languages match");
            int y = this.getY() + (this.getHeight() - 8) / 2;
            int textWidth = font.width(text) + 14;
            int x = this.getX() + (this.getWidth() - textWidth) / 2;
            ConfigIcons.SEARCH.draw(graphics, x, y - 1, ConfigStyle.colors().textMuted());
            graphics.text(font, text, x + 14, y, ConfigStyle.colors().textMuted(), false);
        }
    }
}
