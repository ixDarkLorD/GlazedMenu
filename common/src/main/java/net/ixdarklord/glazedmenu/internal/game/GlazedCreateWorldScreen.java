package net.ixdarklord.glazedmenu.internal.game;

import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.DropdownScreen;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.ToggleSwitch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Making a world, in place of the game's own screen: the game's three tabs as a ribbon (Game, World, More), the chosen
 * tab's settings down the left (the game mode as cards, the difficulty as a row of choices), and beside them what the
 * world will be made with, whichever tab is open, over Create and Cancel.
 * <p>
 * It's a face on the game's own screen, which it keeps: the settings are that screen's, and what's more than a setting
 * (making the world, the game rules, the experiments, the data packs, customizing a world type) is done by pressing
 * that screen's own buttons, so it's done exactly as the game does it. The game's screen is a button away, for what a
 * mod adds to it.
 */
public final class GlazedCreateWorldScreen extends GameListScreen {
    private static final String VANILLA = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen";
    private static final int GAME = 0;
    private static final int WORLD = 1;
    private static final int MORE = 2;
    private static final int LABEL_HEIGHT = 13;
    private static final int MODE_HEIGHT = 44;
    // The face of the game's screen now open, so that screen coming back (from its data packs, its experiments) comes
    // back as its face.
    private static WeakReference<GlazedCreateWorldScreen> current = new WeakReference<>(null);

    private final CreateWorldScreen game;
    private final WorldCreationUiState state;
    private final List<Tab> tabs;
    private int tab;
    // The player asked for the game's own screen: it's left alone from then on.
    private boolean gameShown;

    private GlazedCreateWorldScreen(@Nullable Screen parent, CreateWorldScreen game) {
        super(Component.translatable("selectWorld.create"), parent, ConfigIcons.PLUS);
        this.game = game;
        this.state = game.getUiState();
        this.tabs = List.of(
                new Tab(Component.translatable("createWorld.tab.game.title"), ConfigIcons.PLAY, null, GlazedBrand.identity(0xFF5BC467)),
                new Tab(Component.translatable("createWorld.tab.world.title"), ConfigIcons.GLOBE, null, GlazedBrand.identity(0xFF4DA3FF)),
                new Tab(Component.translatable("createWorld.tab.more.title"), ConfigIcons.SLIDERS, null, GlazedBrand.identity(0xFFF2A33B)));
    }

    /** The screen to open instead: this one for the game's own screen for making a world, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof CreateWorldScreen game) || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceCreateWorld()) return screen;
        GlazedCreateWorldScreen face = current.get();
        if (face != null && face.game == game) return face.gameShown ? screen : face;
        face = new GlazedCreateWorldScreen(Minecraft.getInstance().gui.screen(), game);
        current = new WeakReference<>(face);
        return face;
    }

    // --- The game's own screen ---

    private static @Nullable String key(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    private void showGameTab(int index) {
        // Choosing a tab changes the screen's widgets, so they're looked through as they were.
        for (GuiEventListener child : List.copyOf(this.game.children())) {
            if (child instanceof TabNavigationBar bar) bar.selectTab(index, false);
        }
    }

    // Presses one of the game's screen's buttons, by the name the game gives it, on the tab that holds it.
    private void pressGame(int tab, String name) {
        this.showGameTab(tab);
        for (GuiEventListener child : List.copyOf(this.game.children())) {
            if (child instanceof AbstractWidget widget && name.equals(key(widget)) && widget.active) {
                widget.mouseClicked(new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                return;
            }
        }
    }

    private boolean gameHas(int tab, String name) {
        this.showGameTab(tab);
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof AbstractWidget widget && name.equals(key(widget)) && widget.active) return true;
        }
        return false;
    }

    // The game's own screen, with what was typed here in its boxes (its other controls follow the settings themselves).
    private void showGame() {
        this.gameShown = true;
        this.showGameTab(WORLD);
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof EditBox box && !box.getValue().equals(this.state.getSeed())) box.setValue(this.state.getSeed());
        }
        this.showGameTab(GAME);
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof EditBox box && !box.getValue().equals(this.state.getName())) box.setValue(this.state.getName());
        }
        this.minecraft.gui.setScreen(this.game);
    }

    // --- Layout ---

    @Override
    protected boolean hasSearch() {
        return false;
    }

    @Override
    protected boolean hasBottomBar() {
        return false;
    }

    @Override
    protected Component searchName() {
        return Component.empty();
    }

    @Override
    protected Component searchHint() {
        return Component.empty();
    }

    @Override
    protected List<Tab> tabs() {
        return this.tabs;
    }

    @Override
    protected int chosenTab() {
        return this.tab;
    }

    @Override
    protected void chooseTab(int index) {
        this.tab = index;
        this.rebuildRows(false);
    }

    @Override
    protected void initButtons() {
        // The game's screen is laid out too: its buttons are pressed for what it does itself.
        this.game.init(this.width, this.height);

        FlatButton own = this.addRenderableWidget(FlatButton.icon(ConfigIcons.LIST,
                Component.translatableWithFallback("glazedmenu.create.game_screen", "The game's own screen, with what's set here"), button -> this.showGame()));
        own.setPosition(this.headerRight - 20, this.topBarWidgetY(20));
        this.headerRight = own.getX() - 8;

        // The pane beside the settings: Create over Cancel, at its foot.
        int left = this.detailsLeft() + 8;
        int width = this.detailsWidth() - 16;
        int bottom = this.bodyBottom() - 8;
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, width, button -> this.game.popScreen()));
        cancel.setPosition(left, bottom - 20);
        FlatButton create = this.addRenderableWidget(new FlatButton(width, 24, Component.translatable("selectWorld.create"),
                button -> this.pressGame(GAME, "selectWorld.create")).style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        create.setPosition(left, cancel.getY() - 4 - 24);
    }

    @Override
    protected void onChosen() {}

    @Override
    public void onClose() {
        this.game.popScreen();
    }

    @Override
    protected List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        WorldCreationUiState state = this.state;
        switch (this.tab) {
            case WORLD -> {
                rows.add(new TypeRow());
                StyledEditBox seed = new StyledEditBox(this.font, 100, 18, Component.translatable("selectWorld.enterSeed"));
                seed.setPlaceholder(Component.translatable("selectWorld.seedInfo"));
                seed.setValue(state.getSeed());
                seed.setResponder(state::setSeed);
                rows.add(new FieldRow(Component.translatable("selectWorld.enterSeed"), seed));
                rows.add(new SwitchRow(Component.translatable("selectWorld.mapFeatures"), Component.translatable("selectWorld.mapFeatures.info"),
                        state::isGenerateStructures, () -> state.setGenerateStructures(!state.isGenerateStructures()), () -> !state.isDebug()));
                rows.add(new SwitchRow(Component.translatable("selectWorld.bonusItems"), null,
                        state::isBonusChest, () -> state.setBonusChest(!state.isBonusChest()), () -> !state.isDebug() && !state.isHardcore()));
            }
            case MORE -> {
                rows.add(new LinkRow(ConfigIcons.SLIDERS, GlazedBrand.identity(0xFFF2A33B), Component.translatable("selectWorld.gameRules"),
                        Component.translatableWithFallback("glazedmenu.create.rules.note", "What the world allows"), "selectWorld.gameRules"));
                rows.add(new LinkRow(ConfigIcons.WARNING, GlazedBrand.identity(0xFFE86BA8), Component.translatable("selectWorld.experiments"),
                        Component.translatableWithFallback("glazedmenu.create.experiments.note", "Features still being made"), "selectWorld.experiments"));
                rows.add(new LinkRow(ConfigIcons.FOLDER, GlazedBrand.identity(0xFF4CC9E0), Component.translatable("selectWorld.dataPacks"),
                        Component.translatableWithFallback("glazedmenu.create.packs.note", "%s in use", state.getSettings().dataConfiguration().dataPacks().getEnabled().size()),
                        "selectWorld.dataPacks"));
            }
            default -> {
                StyledEditBox name = new StyledEditBox(this.font, 100, 18, Component.translatable("selectWorld.enterName"));
                name.setValue(state.getName());
                name.setResponder(state::setName);
                rows.add(new FieldRow(Component.translatable("selectWorld.enterName"), name));
                rows.add(new ModesRow());
                rows.add(new DifficultyRow());
                rows.add(new SwitchRow(Component.translatable("selectWorld.allowCommands"), Component.translatable("selectWorld.allowCommands.info"),
                        state::isAllowCommands, () -> state.setAllowCommands(!state.isAllowCommands()), () -> !state.isDebug() && !state.isHardcore()));
            }
        }
        return rows;
    }

    // --- Drawing ---

    @Override
    protected Component subtitle() {
        return Component.literal(this.state.getName() + " · ").append(this.state.getGameMode().displayName).append(" · ").append(this.state.getDifficulty().getDisplayName());
    }

    private static Component onOff(boolean on) {
        return on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    // What the world will be made with, whichever tab is open.
    @Override
    protected void extractDetails(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        WorldCreationUiState state = this.state;
        int bottom = y + height - 52;
        String name = state.getName().isBlank() ? state.getTargetFolder() : state.getName();
        ConfigStyle.text(graphics, this.font, Component.literal(name).withStyle(ChatFormatting.BOLD), x, y, width, ConfigStyle.colors().text());
        ConfigStyle.text(graphics, this.font, Component.literal("saves/" + state.getTargetFolder()), x, y + 11, width, ConfigStyle.colors().textMuted());
        int lineY = y + 25;
        int tagX = x + ConfigStyle.badge(graphics, this.font, state.getGameMode().displayName, x, lineY,
                state.isHardcore() ? ConfigStyle.colors().error() : ConfigStyle.colors().success()) + 4;
        Component difficulty = state.getDifficulty().getDisplayName();
        if (tagX + this.font.width(difficulty) + 8 <= x + width) ConfigStyle.badge(graphics, this.font, difficulty, tagX, lineY, ConfigStyle.colors().warning());
        lineY += 18;
        Component[] names = {
                Component.translatable("selectWorld.mapType"),
                Component.translatableWithFallback("glazedmenu.create.seed", "Seed"),
                Component.translatableWithFallback("glazedmenu.create.structures", "Structures"),
                Component.translatable("selectWorld.bonusItems"),
                Component.translatableWithFallback("glazedmenu.create.commands", "Commands"),
                Component.translatable("selectWorld.dataPacks")};
        Component[] values = {
                state.getWorldType().describePreset(),
                state.getSeed().isBlank() ? Component.translatableWithFallback("glazedmenu.create.seed.random", "Random") : Component.literal(state.getSeed()),
                onOff(state.isGenerateStructures()),
                onOff(state.isBonusChest()),
                onOff(state.isAllowCommands()),
                Component.literal(Integer.toString(state.getSettings().dataConfiguration().dataPacks().getEnabled().size()))};
        for (int i = 0; i < names.length; i++) {
            if (lineY + 11 > bottom) break;
            lineY = this.detailLine(graphics, names[i], values[i], x, lineY, width, ConfigStyle.colors().text());
        }
    }

    // --- The rows ---

    // A row holding widgets of its own: they're placed and drawn by the row, and take their own clicks and keys.
    private abstract class ControlRow extends Row {
        protected final List<AbstractWidget> widgets = new ArrayList<>();

        @Override
        public List<? extends GuiEventListener> children() {
            return this.widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.widgets;
        }

        protected void label(GuiGraphicsExtractor graphics, Component name, int x, int y, int width) {
            ConfigStyle.text(graphics, GlazedCreateWorldScreen.this.font, name.copy().withStyle(ChatFormatting.BOLD), x, y, width, ConfigStyle.colors().textDim());
        }
    }

    // A name over a box to type in.
    private final class FieldRow extends ControlRow {
        private final Component name;
        private final StyledEditBox box;

        FieldRow(Component name, StyledEditBox box) {
            this.name = name;
            this.box = box;
            this.widgets.add(box);
        }

        @Override
        protected int height() {
            return LABEL_HEIGHT + 18 + 7;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            this.label(graphics, this.name, this.getX() + 1, this.getY() + 2, this.getWidth());
            this.box.setWidth(this.getWidth() - 2);
            this.box.setPosition(this.getX() + 1, this.getY() + LABEL_HEIGHT);
            this.box.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    // The game mode: a card for each, the chosen one lit in its color.
    private final class ModesRow extends ControlRow {
        private static final WorldCreationUiState.SelectedGameMode[] MODES = {
                WorldCreationUiState.SelectedGameMode.SURVIVAL, WorldCreationUiState.SelectedGameMode.HARDCORE, WorldCreationUiState.SelectedGameMode.CREATIVE};
        private static final int[] COLORS = {0xFF5BC467, 0xFFF07A5A, 0xFF4DA3FF};
        private static final ConfigIcons.Icon[] ICONS = {ConfigIcons.PERSON, ConfigIcons.WARNING, ConfigIcons.EDIT};

        @Override
        protected int height() {
            return LABEL_HEIGHT + MODE_HEIGHT + 7;
        }

        private int cardWidth() {
            return (this.getWidth() - 2 - 4 * 2) / 3;
        }

        private int at(double mouseX, double mouseY) {
            int top = this.getY() + LABEL_HEIGHT;
            if (mouseY < top || mouseY >= top + MODE_HEIGHT) return -1;
            for (int i = 0; i < MODES.length; i++) {
                int x = this.getX() + 1 + i * (this.cardWidth() + 4);
                if (mouseX >= x && mouseX < x + this.cardWidth()) return i;
            }
            return -1;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GlazedCreateWorldScreen screen = GlazedCreateWorldScreen.this;
            Font font = screen.font;
            this.label(graphics, Component.translatable("selectWorld.gameMode"), this.getX() + 1, this.getY() + 2, this.getWidth());
            boolean fixed = screen.state.isDebug();
            int over = hovered && !fixed ? this.at(mouseX, mouseY) : -1;
            int top = this.getY() + LABEL_HEIGHT;
            int width = this.cardWidth();
            for (int i = 0; i < MODES.length; i++) {
                WorldCreationUiState.SelectedGameMode mode = MODES[i];
                boolean chosen = screen.state.getGameMode() == mode;
                int color = fixed ? ConfigStyle.colors().textMuted() : GlazedBrand.identity(COLORS[i]);
                int x = this.getX() + 1 + i * (width + 4);
                ConfigStyle.rect(graphics, x, top, width, MODE_HEIGHT, ConfigStyle.mix(ConfigStyle.colors().button(), color, chosen ? 0.26F : i == over ? 0.14F : 0.04F));
                ConfigStyle.outline(graphics, x, top, width, MODE_HEIGHT, chosen ? ConfigStyle.withAlpha(color, 0xE0) : i == over ? ConfigStyle.withAlpha(color, 0x90) : ConfigStyle.colors().panelBorder());
                graphics.fill(x + 1, top + 1, x + width - 1, top + 3, ConfigStyle.withAlpha(color, chosen ? 0xFF : 0x60));
                // The icon gives way to the name on a narrow card.
                boolean icon = width >= 100;
                if (icon) ICONS[i].draw(graphics, x + 6, top + 7, 12, chosen ? color : ConfigStyle.mix(color, ConfigStyle.colors().textDim(), 0.5F));
                ConfigStyle.text(graphics, font, mode.displayName.copy().withStyle(ChatFormatting.BOLD), icon ? x + 22 : x + 6, top + 9, icon ? width - 28 : width - 10,
                        chosen ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
                // What the mode is, in the game's words: as much of it as two lines hold.
                var lines = font.split(mode.getInfo(), width - 12);
                for (int line = 0; line < Math.min(2, lines.size()); line++) {
                    graphics.text(font, lines.get(line), x + 6, top + 23 + line * 9, ConfigStyle.colors().textMuted(), false);
                }
                if (i == over && lines.size() > 2) graphics.setTooltipForNextFrame(font, font.split(mode.getInfo(), 200), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GlazedCreateWorldScreen screen = GlazedCreateWorldScreen.this;
            int index = this.at(event.x(), event.y());
            if (index < 0 || screen.state.isDebug()) return false;
            screen.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            screen.state.setGameMode(MODES[index]);
            return true;
        }
    }

    // The difficulty: its four choices side by side, the chosen one filled. Hardcore is always hard.
    private final class DifficultyRow extends ControlRow {
        private static final Difficulty[] CHOICES = {Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};

        @Override
        protected int height() {
            return LABEL_HEIGHT + 20 + 7;
        }

        private int at(double mouseX, double mouseY) {
            int top = this.getY() + LABEL_HEIGHT;
            if (mouseY < top || mouseY >= top + 20 || mouseX < this.getX() + 1) return -1;
            int index = (int) ((mouseX - this.getX() - 1) * CHOICES.length / (this.getWidth() - 2));
            return index < CHOICES.length ? index : -1;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GlazedCreateWorldScreen screen = GlazedCreateWorldScreen.this;
            this.label(graphics, Component.translatable("options.difficulty"), this.getX() + 1, this.getY() + 2, this.getWidth());
            boolean fixed = screen.state.isHardcore();
            int over = hovered && !fixed ? this.at(mouseX, mouseY) : -1;
            int top = this.getY() + LABEL_HEIGHT;
            int left = this.getX() + 1;
            int width = this.getWidth() - 2;
            for (int i = 0; i < CHOICES.length; i++) {
                int x = left + width * i / CHOICES.length;
                int segment = left + width * (i + 1) / CHOICES.length - x;
                boolean chosen = screen.state.getDifficulty() == CHOICES[i];
                int fill = chosen ? (fixed ? ConfigStyle.colors().buttonDisabled() : ConfigStyle.accent()) : i == over ? ConfigStyle.colors().buttonHover() : ConfigStyle.colors().button();
                graphics.fill(x, top, x + segment, top + 20, fill);
                ConfigStyle.outline(graphics, x, top, segment + (i < CHOICES.length - 1 ? 1 : 0), 20, ConfigStyle.colors().panelBorder());
                ConfigStyle.centeredText(graphics, screen.font, CHOICES[i].getDisplayName(), x + segment / 2, top + 6, segment - 4,
                        chosen && !fixed ? ConfigStyle.readableOn(fill) : fixed ? ConfigStyle.colors().textMuted() : ConfigStyle.colors().text());
                if (i == over) graphics.setTooltipForNextFrame(screen.font, screen.font.split(CHOICES[i].getInfo(), 200), mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GlazedCreateWorldScreen screen = GlazedCreateWorldScreen.this;
            int index = this.at(event.x(), event.y());
            if (index < 0 || screen.state.isHardcore()) return false;
            screen.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            screen.state.setDifficulty(CHOICES[index]);
            return true;
        }
    }

    // A setting that's on or off: its name (what it does as a tooltip), and a switch at the row's end.
    private final class SwitchRow extends ControlRow {
        private final Component name;
        private final @Nullable Component info;
        private final BooleanSupplier active;
        private final ToggleSwitch toggle;

        SwitchRow(Component name, @Nullable Component info, BooleanSupplier value, Runnable flip, BooleanSupplier active) {
            this.name = name;
            this.info = info;
            this.active = active;
            this.toggle = new ToggleSwitch(58, 16, value, button -> flip.run());
            this.widgets.add(this.toggle);
        }

        @Override
        protected int height() {
            return 24;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GlazedCreateWorldScreen screen = GlazedCreateWorldScreen.this;
            boolean active = this.active.getAsBoolean();
            if (hovered) ConfigStyle.rect(graphics, this.getX(), this.getY() + 1, this.getWidth(), 22, ConfigStyle.colors().rowHover());
            this.toggle.active = active;
            this.toggle.setPosition(this.getX() + this.getWidth() - 4 - this.toggle.getWidth(), this.getY() + 4);
            this.toggle.extractRenderState(graphics, mouseX, mouseY, a);
            ConfigStyle.text(graphics, screen.font, this.name, this.getX() + 4, this.getY() + 8, this.toggle.getX() - this.getX() - 10,
                    active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted());
            if (hovered && this.info != null && mouseX < this.toggle.getX()) {
                graphics.setTooltipForNextFrame(screen.font, screen.font.split(this.info, 200), mouseX, mouseY);
            }
        }
    }

    // The world type: its name, a button dropping the types down, and Customize where the type has something to.
    private final class TypeRow extends ControlRow {
        private final FlatButton choose;
        private final @Nullable FlatButton customize;

        TypeRow() {
            GlazedCreateWorldScreen screen = GlazedCreateWorldScreen.this;
            WorldCreationUiState state = screen.state;
            this.choose = FlatButton.of(state.getWorldType().describePreset(), 110, button -> {
                List<WorldCreationUiState.WorldTypeEntry> types = new ArrayList<>(state.getNormalPresetList());
                if (!types.contains(state.getWorldType())) types.add(state.getWorldType());
                screen.minecraft.gui.setScreen(new DropdownScreen<>(screen, button.getX(), button.getY(), button.getWidth(), button.getHeight(), types, state.getWorldType(),
                        WorldCreationUiState.WorldTypeEntry::describePreset,
                        type -> type.isAmplified() ? Component.translatable("generator.minecraft.amplified.info") : null, state::setWorldType));
            });
            this.choose.active = state.getWorldType().preset() != null;
            this.widgets.add(this.choose);
            if (!state.isDebug() && state.getPresetEditor() != null && screen.gameHas(WORLD, "selectWorld.customizeType")) {
                Component customize = Component.translatable("selectWorld.customizeType");
                this.customize = FlatButton.of(customize, screen.font.width(customize) + 14, button -> screen.pressGame(WORLD, "selectWorld.customizeType"));
                this.widgets.add(this.customize);
            } else {
                this.customize = null;
            }
        }

        @Override
        protected int height() {
            return 27;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GlazedCreateWorldScreen screen = GlazedCreateWorldScreen.this;
            int right = this.getX() + this.getWidth() - 1;
            if (this.customize != null) {
                this.customize.setPosition(right - this.customize.getWidth(), this.getY() + 2);
                this.customize.extractRenderState(graphics, mouseX, mouseY, a);
                right = this.customize.getX() - 4;
            }
            this.choose.setWidth(Math.clamp(this.getWidth() / 2, 70, 150));
            this.choose.setPosition(right - this.choose.getWidth(), this.getY() + 2);
            this.choose.extractRenderState(graphics, mouseX, mouseY, a);
            ConfigStyle.text(graphics, screen.font, Component.translatable("selectWorld.mapType").withStyle(ChatFormatting.BOLD), this.getX() + 1, this.getY() + 8,
                    this.choose.getX() - this.getX() - 8, ConfigStyle.colors().textDim());
        }
    }

    // One of the game's own screens that go with making a world, opened by the game's own button for it.
    private final class LinkRow extends Row {
        private final ConfigIcons.Icon icon;
        private final int color;
        private final Component title;
        private final Component note;
        private final String button;

        LinkRow(ConfigIcons.Icon icon, int color, Component title, Component note, String button) {
            this.icon = icon;
            this.color = color;
            this.title = title;
            this.note = note;
            this.button = button;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GlazedCreateWorldScreen.this.font;
            int x = this.getX();
            int y = this.getY() + 1;
            int width = this.getWidth();
            int height = this.getHeight() - 3;
            ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.mix(ConfigStyle.colors().button(), this.color, hovered ? 0.22F : 0.08F));
            ConfigStyle.outline(graphics, x, y, width, height, ConfigStyle.withAlpha(this.color, hovered ? 0xE0 : 0x60));
            graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ConfigStyle.withAlpha(this.color, hovered ? 0xFF : 0xB0));
            int box = height - 8;
            ConfigStyle.iconFrame(graphics, x + 4, y + 5, box, box - 1, ConfigStyle.withAlpha(this.color, hovered ? 0xFF : 0xD0));
            this.icon.draw(graphics, x + 4 + (box - 12) / 2, y + 5 + (box - 13) / 2, 12, ConfigStyle.readableOn(this.color));
            int textX = x + 4 + box + 7;
            int textWidth = x + width - 18 - textX;
            ConfigStyle.text(graphics, font, this.title.copy().withStyle(ChatFormatting.BOLD), textX, y + 6, textWidth, ConfigStyle.colors().text());
            ConfigStyle.text(graphics, font, this.note, textX, y + 17, textWidth, ConfigStyle.colors().textDim());
            ConfigIcons.CHEVRON.draw(graphics, x + width - 15, y + (height - ConfigIcons.SIZE) / 2, hovered ? this.color : ConfigStyle.colors().textMuted());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GlazedCreateWorldScreen screen = GlazedCreateWorldScreen.this;
            screen.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            screen.pressGame(MORE, this.button);
            return true;
        }
    }
}
