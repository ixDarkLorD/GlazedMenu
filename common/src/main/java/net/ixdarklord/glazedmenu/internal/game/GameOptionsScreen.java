package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.internal.compat.Compat;
import net.minecraft.util.Mth;
import net.ixdarklord.glazedmenu.internal.compat.CompatList;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigGroup;
import net.ixdarklord.glazedmenu.api.config.ConfigNode;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.api.config.type.BooleanType;
import net.ixdarklord.glazedmenu.api.config.type.NumberType;
import net.ixdarklord.glazedmenu.api.editor.ConfigEditors;
import net.ixdarklord.glazedmenu.api.editor.ValueEditor;
import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.ConfigEditSession;
import net.ixdarklord.glazedmenu.internal.gui.ConfigEntryList;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.ConfirmPopup;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.ixdarklord.glazedmenu.internal.style.ThemeEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.ixdarklord.glazedmenu.internal.compat.KeyEvent;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The game's options, in place of the game's own options screen: a ribbon of tabs across the top, each an icon over its
 * name (Quick, then each category of the options, then More), and under it the chosen tab's settings as cards in two
 * columns, each card a group of settings edited right there. A category the game splits up (Video: display, quality,
 * preferences) has a card for each part; one it doesn't has a card each for its switches, its sliders and its choices.
 * Quick holds the options most reached for and, beside them, every option changed from its default; More holds the
 * screens that go with the options (the controls and key binds, the language, the resource packs, the skin, and the
 * game's own screen for what only it has). The search lists the matching settings of every category, a card each.
 * <p>
 * Edits are kept until saved, as in the config screens: Save applies them and stays, Done applies them and closes.
 * <p>
 * Keys: Ctrl+F searches, Ctrl+S saves.
 */
public final class GameOptionsScreen extends StyledScreen {
    private static final Class<?> VANILLA = net.minecraft.client.gui.screens.OptionsScreen.class;
    private static final int KEY_F = 70;
    private static final int KEY_S = 83;
    private static final int RIBBON_HEIGHT = 34;
    private static final int TAB_MIN_WIDTH = 46;
    private static final int LINE_HEIGHT = 24;
    private static final int CARD_GAP = 6;
    private static final int CARD_PADDING = 7;
    private static final int CARD_TITLE = 14;
    private static final int LINK_HEIGHT = 30;
    // The options on the Quick tab, by the name the game gives each (1.20.1 has no graphics preset: its graphics mode).
    private static final List<String> QUICK = List.of("fov", "renderDistance", "guiScale", "volume_master", "framerateLimit", "graphicsMode");
    // The tab open last, kept while the game runs.
    private static String lastTab = "quick";

    private final @Nullable Screen parent;
    private final Config config;
    private final ConfigEditSession session;
    private final List<Tab> tabs = new ArrayList<>();
    private int selectedTab;
    private String query = "";
    private double ribbonScroll;
    private @Nullable Cards cards;
    private @Nullable StyledEditBox search;
    private @Nullable FlatButton doneButton;
    private @Nullable FlatButton saveButton;
    private @Nullable FlatButton resetButton;
    private List<ConfigValue<?>> shown = List.of();
    // How wide the cards' editors are, for the cards' width now.
    private int editorWidth = 96;

    private GameOptionsScreen(@Nullable Screen parent, Config config) {
        super(Component.translatable("options.title"), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.parent = parent;
        this.config = config;
        this.session = new ConfigEditSession(config);
        this.tabs.add(new Tab("quick", Component.translatableWithFallback("glazedmenu.options.tab.quick", "Quick"), ConfigIcons.STAR, GlazedBrand.identity(0xFFF2B93B), null));
        for (ConfigNode node : config.root().children()) {
            if (node instanceof ConfigGroup group && !group.isHidden() && group.values().anyMatch(this.session::isVisible)) {
                this.tabs.add(new Tab(group.key(), group.displayName(), iconOf(group.key()), colorOf(group.key()), group));
            }
        }
        this.tabs.add(new Tab("more", Component.translatableWithFallback("glazedmenu.options.tab.more", "More"), ConfigIcons.LIST, GlazedBrand.identity(0xFF9AA4B8), null));
        for (int i = 0; i < this.tabs.size(); i++) {
            if (this.tabs.get(i).key.equals(lastTab)) this.selectedTab = i;
        }
    }

    /** The screen to open instead: this one for the game's own options screen, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (screen == null || screen.getClass() != VANILLA || !GlazedSettings.replaceOptions()) return screen;
        for (Config config : ConfigSources.forMod("minecraft")) {
            if (config.name().equals("options")) return new GameOptionsScreen(Minecraft.getInstance().screen, config);
        }
        return screen;
    }

    private record Tab(String key, Component name, ConfigIcons.Icon icon, int color, @Nullable ConfigGroup group) {}

    private static ConfigIcons.Icon iconOf(String category) {
        return switch (category) {
            case "video" -> ConfigIcons.SCOPE_CLIENT;
            case "controls" -> ConfigIcons.KEYBOARD;
            case "mouse" -> ConfigIcons.MOUSE;
            case "chat" -> ConfigIcons.CHAT;
            case "font" -> ConfigIcons.FONT;
            case "sounds" -> ConfigIcons.SOUND;
            case "online" -> ConfigIcons.GLOBE;
            case "accessibility" -> ConfigIcons.EYE;
            case "general" -> ConfigIcons.SLIDERS;
            default -> ConfigIcons.CONFIGS;
        };
    }

    // Each category in a color of its own: its tab's icon, and its cards' edges.
    private static int colorOf(String category) {
        return switch (category) {
            case "general" -> GlazedBrand.identity(0xFF8B7CF6);
            case "video" -> GlazedBrand.identity(0xFF4DA3FF);
            case "controls" -> GlazedBrand.identity(0xFFF2A33B);
            case "mouse" -> GlazedBrand.identity(0xFF3CC8B4);
            case "chat" -> GlazedBrand.identity(0xFF5BC467);
            case "font" -> GlazedBrand.identity(0xFFE86BA8);
            case "sounds" -> GlazedBrand.identity(0xFFB07CF0);
            case "online" -> GlazedBrand.identity(0xFF4CC9E0);
            case "accessibility" -> GlazedBrand.identity(0xFFF2C94C);
            default -> GlazedBrand.identity(0xFF9AA4B8);
        };
    }

    // --- Layout ---

    private int ribbonY() {
        return this.topBarY() + this.topBarHeight() + GAP;
    }

    @Override
    protected int bodyTop() {
        return this.ribbonY() + RIBBON_HEIGHT + GAP;
    }

    @Override
    protected void init() {
        int searchWidth = Mth.clamp(this.width / 3, 100, 200);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18,
                Component.translatableWithFallback("glazedmenu.options.search", "Search options")));
        this.search.setPosition(this.frameLeft() + this.frameWidth() - 6 - 20 - TOGGLE_ROOM - searchWidth, this.topBarY() + 5);
        this.search.setPlaceholder(Component.translatableWithFallback("glazedmenu.options.search.hint", "Search options… (Ctrl+F)"));
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            this.rebuildCards(false);
        });
        this.addModeToggleAtEnd(6).setY(this.search.getY() - 1);

        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        this.cards = this.addRenderableWidget(new Cards(this.minecraft, this.frameWidth() - 8, bodyHeight - 8, bodyTop + 4));
        this.cards.updateSizeAndPosition(this.frameWidth() - 8, bodyHeight - 8, this.frameLeft() + 4, bodyTop + 4);

        int y = this.barWidgetY(this.bottomBarY());
        this.doneButton = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.done())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        this.doneButton.setPosition(this.frameLeft() + this.frameWidth() - 4 - 84, y);
        this.saveButton = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("glazedmenu.button.save_short", "Save"), 64,
                button -> this.saveHere()));
        this.saveButton.setPosition(this.doneButton.getX() - 4 - 64, y);
        this.resetButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.RESET, Component.translatableWithFallback("glazedmenu.button.reset.tooltip",
                "Sets the values shown here to their defaults"), button -> {
            this.session.resetToDefaults(this.shown);
            this.rebuildCards(true);
        }).style(FlatButton.Style.NORMAL));
        this.resetButton.setPosition(this.saveButton.getX() - 4 - 20, y);
        this.rebuildCards(true);
        this.updateButtons();
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    // --- The cards ---

    private List<ConfigValue<?>> valuesOf(ConfigGroup group) {
        return group.values().filter(this.session::isVisible).collect(Collectors.toList());
    }

    private boolean isChanged(ConfigValue<?> value) {
        return !this.session.isDefault(value) || this.session.isModified(value);
    }

    // The chosen tab's cards, or with a search the matching settings of every category, a card each.
    private void rebuildCards(boolean keepScroll) {
        if (this.cards == null) return;
        int columns = this.cards.columns();
        this.editorWidth = Mth.clamp((this.cards.getRowWidth() - CARD_GAP * (columns - 1)) / columns * 9 / 20, 70, 130);
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        List<Card> cards = new ArrayList<>();
        List<ConfigValue<?>> shown = new ArrayList<>();
        Tab tab = this.tabs.get(this.selectedTab);
        if (!query.isEmpty()) {
            for (Tab category : this.tabs) {
                if (category.group == null) continue;
                List<ConfigValue<?>> found = this.valuesOf(category.group).stream().filter(value -> ConfigEntryList.matches(value, query)).toList();
                if (!found.isEmpty()) cards.add(this.card(category.name, category.color, found));
                shown.addAll(found);
            }
            if (cards.isEmpty()) cards.add(new Card(Component.translatableWithFallback("glazedmenu.no_results", "No settings match"), GlazedBrand.identity(0xFF9AA4B8), List.of()));
        } else if (tab.group != null) {
            // Video, with the mods that take the game's video settings over: their own screens lead the tab.
            if (tab.key.equals("video")) cards.addAll(this.videoLinks());
            List<ConfigGroup> parts = tab.group.children().stream().filter(node -> node instanceof ConfigGroup part && !part.isHidden())
                    .map(node -> (ConfigGroup) node).toList();
            List<ConfigValue<?>> loose = tab.group.children().stream().filter(node -> node instanceof ConfigValue<?> value && this.session.isVisible(value))
                    .<ConfigValue<?>>map(node -> (ConfigValue<?>) node).toList();
            if (!parts.isEmpty()) {
                // As the game splits the category up.
                if (!loose.isEmpty()) cards.add(this.card(tab.name, tab.color, loose));
                for (ConfigGroup part : parts) {
                    List<ConfigValue<?>> values = this.valuesOf(part);
                    if (!values.isEmpty()) cards.add(this.card(part.displayName(), tab.color, values));
                }
            } else if (loose.size() <= 6) {
                cards.add(this.card(tab.name, tab.color, loose));
            } else {
                // By kind: what's switched on or off, what's slid or typed, what's chosen from a list.
                List<ConfigValue<?>> switches = loose.stream().filter(value -> value.type() instanceof BooleanType).toList();
                List<ConfigValue<?>> numbers = loose.stream().filter(value -> value.type() instanceof NumberType<?>).toList();
                List<ConfigValue<?>> choices = loose.stream().filter(value -> !(value.type() instanceof BooleanType) && !(value.type() instanceof NumberType<?>)).toList();
                if (!switches.isEmpty()) cards.add(this.card(Component.translatableWithFallback("glazedmenu.options.kind.switches", "Switches"), tab.color, switches));
                if (!numbers.isEmpty()) cards.add(this.card(Component.translatableWithFallback("glazedmenu.options.kind.sliders", "Sliders"), tab.color, numbers));
                if (!choices.isEmpty()) cards.add(this.card(Component.translatableWithFallback("glazedmenu.options.kind.choices", "Choices"), tab.color, choices));
            }
            shown.addAll(this.valuesOf(tab.group));
        } else if (tab.key.equals("quick")) {
            List<ConfigValue<?>> quick = new ArrayList<>();
            for (String name : QUICK) {
                this.config.values().filter(value -> value.path().endsWith("." + name) && this.session.isVisible(value)).findFirst().ifPresent(quick::add);
            }
            cards.add(this.card(Component.translatableWithFallback("glazedmenu.options.section.quick", "Quick Settings"), tab.color, quick));
            // Every other option that isn't at its default, wherever it is.
            List<ConfigValue<?>> changed = this.config.values().filter(value -> this.session.isVisible(value) && this.isChanged(value) && !quick.contains(value)).toList();
            Card changedCard = this.card(Component.translatableWithFallback("glazedmenu.options.section.changed", "Changed from Defaults"), ConfigStyle.colors().modified(), changed);
            cards.add(changed.isEmpty() ? new Card(Component.translatableWithFallback("glazedmenu.options.section.changed.none", "No other changes"),
                    GlazedBrand.identity(0xFF9AA4B8), List.of()) : changedCard);
            shown.addAll(quick);
            shown.addAll(changed);
        } else {
            cards.addAll(this.links());
        }
        this.shown = shown;
        double scroll = this.cards.scrollAmount();
        this.cards.setCards(cards);
        this.cards.setScrollAmount(keepScroll ? scroll : 0);
    }

    private Card card(Component title, int color, List<ConfigValue<?>> values) {
        List<Line> lines = new ArrayList<>();
        for (ConfigValue<?> value : values) lines.add(this.line(value));
        return new Card(title, color, lines);
    }

    private <T> Line line(ConfigValue<T> value) {
        return new Line(value, ConfigEditors.create(this.session.slot(value), this.editorWidth, 20), null);
    }

    // More: the screens that go with the options, each a card of one line that opens it.
    private List<Card> links() {
        Minecraft minecraft = this.minecraft;
        List<Card> cards = new ArrayList<>();
        cards.add(link(ConfigIcons.KEYBOARD, GlazedBrand.identity(0xFFF07A5A), Component.translatable("options.controls"),
                Component.translatableWithFallback("glazedmenu.options.card.keys", "%s key binds", minecraft.options.keyMappings.length),
                screen -> new ControlsScreen(screen, minecraft.options)));
        LanguageInfo language = minecraft.getLanguageManager().getLanguage(minecraft.getLanguageManager().getSelected());
        cards.add(link(ConfigIcons.GLOBE, GlazedBrand.identity(0xFF4DA3FF), Component.translatableWithFallback("glazedmenu.title.language", "Language"),
                language != null ? Component.literal(language.name()) : Component.literal(minecraft.getLanguageManager().getSelected()),
                screen -> new LanguageSelectScreen(screen, minecraft.options, minecraft.getLanguageManager())));
        // The resource packs: the game's screen for them (which opens as Glazed Menu's), applying the choice as the game's
        // options screen does and coming back here.
        cards.add(link(ConfigIcons.FOLDER, GlazedBrand.identity(0xFFF2A33B), Component.translatable("resourcePack.title"),
                Component.translatableWithFallback("glazedmenu.options.card.packs", "%s in use", minecraft.getResourcePackRepository().getSelectedIds().size()),
                screen -> new PackSelectionScreen(minecraft.getResourcePackRepository(), repository -> {
                    minecraft.options.updateResourcePacks(repository);
                    minecraft.setScreen(screen);
                }, minecraft.getResourcePackDirectory(), Component.translatable("resourcePack.title"))));
        cards.addAll(this.videoLinks());
        cards.add(link(ConfigIcons.PERSON, GlazedBrand.identity(0xFF5BC467), Component.translatable("options.skinCustomisation.title"),
                Component.translatableWithFallback("glazedmenu.options.card.skin", "The parts of your skin shown"),
                screen -> new SkinCustomizationScreen(screen, minecraft.options)));
        // The game's own screen, for what only it has; a class of its own here, so it isn't taken for the game's and replaced.
        cards.add(link(ConfigIcons.LIST, GlazedBrand.identity(0xFF9AA4B8), Component.translatableWithFallback("glazedmenu.options.card.classic", "Game's Own Screen"),
                Component.translatableWithFallback("glazedmenu.options.card.classic.note", "Difficulty, telemetry, credits"),
                screen -> new OptionsScreen(screen, minecraft.options) {}));
        return cards;
    }

    // Sodium's video settings and Iris's shader packs, where those mods are here: the game's own video options don't
    // hold what they add.
    private List<Card> videoLinks() {
        List<Card> cards = new ArrayList<>();
        if (ShaderBridge.hasSodium()) {
            cards.add(link(ConfigIcons.SCOPE_CLIENT, GlazedBrand.identity(0xFF4DA3FF), Component.translatableWithFallback("glazedmenu.options.card.sodium", "Sodium Video Settings"),
                    Component.translatableWithFallback("glazedmenu.options.card.sodium.note", "Sodium's own performance and quality settings"),
                    screen -> orStay(ShaderBridge.sodiumScreen(screen), screen)));
        }
        if (ShaderBridge.hasShaders()) {
            cards.add(link(ConfigIcons.SUN, GlazedBrand.identity(0xFFF2B93B), Component.translatableWithFallback("glazedmenu.shaders.title", "Shader Packs"),
                    !ShaderBridge.enabled() ? Component.translatableWithFallback("glazedmenu.shaders.off", "Shaders are off")
                            : ShaderBridge.chosenPack().<Component>map(Component::literal).orElseGet(() -> Component.translatableWithFallback("glazedmenu.shaders.no_pack", "No pack chosen")),
                    screen -> GlazedSettings.replaceShaders() ? new GameShadersScreen(screen) : orStay(ShaderBridge.irisScreen(screen), screen)));
        }
        return cards;
    }

    private static Screen orStay(@Nullable Screen opened, Screen current) {
        return opened != null ? opened : current;
    }

    private static Card link(ConfigIcons.Icon icon, int color, Component title, Component note, UnaryOperator<Screen> open) {
        return new Card(null, color, List.of(new Line(null, null, new Link(icon, title, note, open))));
    }

    private void selectTab(int index) {
        if (index == this.selectedTab && this.query.isEmpty()) return;
        this.selectedTab = index;
        lastTab = this.tabs.get(index).key;
        if (!this.query.isEmpty() && this.search != null) this.search.setValue("");
        else this.rebuildCards(false);
    }

    // --- The ribbon ---

    private int tabWidth(Tab tab) {
        return Math.max(TAB_MIN_WIDTH, this.font.width(tab.name) + 12);
    }

    private int ribbonWidth() {
        return this.tabs.stream().mapToInt(tab -> this.tabWidth(tab) + 2).sum() + 6;
    }

    private double maxRibbonScroll() {
        return Math.max(0, this.ribbonWidth() - this.frameWidth());
    }

    private boolean overRibbon(double mouseX, double mouseY) {
        return mouseX >= this.frameLeft() && mouseX < this.frameLeft() + this.frameWidth() && mouseY >= this.ribbonY() && mouseY < this.ribbonY() + RIBBON_HEIGHT;
    }

    // The tab under the mouse, or -1.
    private int tabAt(double mouseX, double mouseY) {
        if (!this.overRibbon(mouseX, mouseY)) return -1;
        int x = this.frameLeft() + 4 - (int) this.ribbonScroll;
        for (int i = 0; i < this.tabs.size(); i++) {
            int width = this.tabWidth(this.tabs.get(i));
            if (mouseX >= x && mouseX < x + width) return i;
            x += width + 2;
        }
        return -1;
    }

    private void extractRibbon(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int top = this.ribbonY();
        // The bar itself is a thin strip: the tabs are taller than it, and stand out above and below it.
        ConfigStyle.bar(graphics, this.frameLeft(), top + 7, this.frameWidth(), RIBBON_HEIGHT - 14);
        this.ribbonScroll = Mth.clamp(this.ribbonScroll, 0, this.maxRibbonScroll());
        int hovered = this.tabAt(mouseX, mouseY);
        // Room under the tabs for their shadows.
        graphics.enableScissor(this.frameLeft() + 1, top, this.frameLeft() + this.frameWidth() - 1, top + RIBBON_HEIGHT + 3);
        int x = this.frameLeft() + 4 - (int) this.ribbonScroll;
        for (int i = 0; i < this.tabs.size(); i++) {
            Tab tab = this.tabs.get(i);
            int width = this.tabWidth(tab);
            boolean chosen = i == this.selectedTab && this.query.isBlank();
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
            tab.icon.draw(graphics, x + (width - 14) / 2, top + 5, 14, chosen || lit ? tab.color : ConfigStyle.mix(tab.color, ConfigStyle.colors().textDim(), 0.45F));
            ConfigStyle.centeredText(graphics, this.font, tab.name, x + width / 2, top + 19, width - 4,
                    chosen ? ConfigStyle.colors().text() : lit ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            // How many of its settings wait to be saved.
            if (tab.group != null) {
                long unsaved = tab.group.values().filter(this.session::isModified).count();
                if (unsaved > 0) ConfigStyle.rect(graphics, x + width - 8, top + 5, 4, 4, ConfigStyle.colors().modified());
            }
            graphics.pose().popMatrix();
            x += width + 2;
        }
        graphics.disableScissor();
        // More tabs than fit: a fade of the bar's edge says there's more that way.
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
            this.selectTab(tab);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // --- Saving ---

    @Override
    public void tick() {
        super.tick();
        this.updateButtons();
    }

    private void updateButtons() {
        if (this.doneButton == null || this.saveButton == null || this.resetButton == null) return;
        boolean editable = this.session.access() == Access.LOCAL;
        int modified = this.session.modifiedCount();
        this.resetButton.active = editable && this.shown.stream().anyMatch(value -> !this.session.isDefault(value));
        this.saveButton.visible = modified > 0 || this.session.hasErrors();
        this.saveButton.active = modified > 0 && !this.session.hasErrors();
        this.doneButton.active = !this.session.hasErrors();
        this.doneButton.setTooltip(this.session.hasErrors()
                ? Tooltip.create(Component.translatableWithFallback("glazedmenu.button.save.errors", "Fix the invalid values first:")
                .append("\n").append(this.session.errors().stream().map(Component::getString).collect(Collectors.joining("\n"))))
                : modified > 0 ? Tooltip.create(Component.translatableWithFallback("glazedmenu.button.done.tooltip", "Save and close")) : null);
    }

    private void saveHere() {
        if (this.session.hasErrors() || this.session.modifiedCount() == 0) return;
        this.session.saveAndNotify();
        this.rebuildCards(true);
    }

    private void done() {
        if (this.session.hasErrors()) return;
        this.leave(this.parent);
        if (this.session.modifiedCount() > 0) this.session.saveAndNotify();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.key() == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        if (event.hasControlDownWithQuirk() && event.key() == KEY_S) {
            this.saveHere();
            return true;
        }
        return super.keyPressed(event);
    }

    // --- Drawing ---

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        this.extractRibbon(graphics, mouseX, mouseY);
        ConfigStyle.panel(graphics, this.frameLeft(), this.bodyTop(), this.frameWidth(), this.bodyBottom() - this.bodyTop());
        Tab tab = this.tabs.get(this.selectedTab);
        // The chosen tab's color along the panel's top edge.
        graphics.fill(this.frameLeft() + 1, this.bodyTop(), this.frameLeft() + this.frameWidth() - 1, this.bodyTop() + 2,
                ConfigStyle.withAlpha(this.query.isBlank() ? tab.color : ConfigStyle.accent(), 0xD0));

        int searchLeft = this.search != null ? this.search.getX() - 18 : this.width;
        this.extractTitle(graphics, this.title, Math.max(40, searchLeft - this.frameLeft() - 40), (g, x, y, size) -> {
            ConfigIcons.SLIDERS.draw(g, x, y, size, ConfigStyle.accent());
            return true;
        });
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }

        // What's unsaved or invalid, or what the tab holds, at the left of the bottom bar.
        int errors = this.session.errors().size();
        int modified = this.session.modifiedCount();
        long changed = this.shown.stream().filter(value -> !this.session.isDefault(value)).count();
        Component status = errors > 0 ? Component.translatableWithFallback("glazedmenu.status.errors", "%s invalid", errors)
                : modified > 0 ? Component.translatableWithFallback("glazedmenu.status.modified", "%s unsaved", modified)
                : this.shown.isEmpty() ? Component.translatableWithFallback("glazedmenu.options.status.more", "The screens that go with the options")
                : changed > 0 ? Component.translatableWithFallback("glazedmenu.options.status.changed", "%s settings · %s changed from their defaults", this.shown.size(), changed)
                : Component.translatableWithFallback("glazedmenu.options.status", "%s settings · all at their defaults", this.shown.size());
        int x = this.frameLeft() + 10;
        int right = this.resetButton != null ? this.resetButton.getX() - 8 : this.width;
        ConfigStyle.text(graphics, this.font, status, x, this.bottomBarY() + (BAR_HEIGHT - 8) / 2, right - x,
                errors > 0 ? ConfigStyle.colors().error() : modified > 0 ? ConfigStyle.colors().modified() : ConfigStyle.colors().textMuted());
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    // Unsaved changes are confirmed before they're dropped.
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

    // Back from another screen: the cards show what's set now.
    @Override
    public void added() {
        super.added();
        this.session.onConfigChanged();
        this.rebuildCards(true);
    }

    // --- The cards' list ---

    // A card: a title (none for a link's), and its lines.
    private record Card(@Nullable Component title, int color, List<Line> lines) {
        int height() {
            int lines = this.lines.stream().mapToInt(line -> line.link != null ? LINK_HEIGHT : LINE_HEIGHT).sum();
            return CARD_PADDING * 2 + (this.title != null ? CARD_TITLE : 0) + lines;
        }
    }

    // A line of a card: a setting with its editor, or a link to a screen.
    private record Line(@Nullable ConfigValue<?> value, @Nullable ValueEditor editor, @Nullable Link link) {}

    private record Link(ConfigIcons.Icon icon, Component title, Component note, UnaryOperator<Screen> open) {}

    // Every card in one entry, in columns of even width: each card goes under the shorter column so far, so the columns
    // fill evenly; the list scrolls the whole.
    private final class Cards extends CompatList<Board> {
        Cards(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 40);
        }

        @Override
        public int getRowWidth() {
            return this.width - 14;
        }

        int columns() {
            return this.getRowWidth() >= 330 ? 2 : 1;
        }

        void setCards(List<Card> cards) {
            this.clearEntries();
            Board board = new Board(cards, this.columns());
            this.addEntry(board, board.height);
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

    private final class Board extends CompatList.Entry<Board> {
        private final List<Card> cards;
        private final int columns;
        private final int[] column;
        private final int[] top;
        private final int height;
        private final List<AbstractWidget> editors = new ArrayList<>();

        Board(List<Card> cards, int columns) {
            this.cards = List.copyOf(cards);
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
                for (Line line : cards.get(i).lines) {
                    if (line.editor != null) this.editors.add(line.editor.widget());
                }
            }
            int tallest = 0;
            for (int value : filled) tallest = Math.max(tallest, value);
            this.height = Math.max(tallest, 20);
        }

        private int cardWidth() {
            return (this.getWidth() - CARD_GAP * (this.columns - 1)) / this.columns;
        }

        private int cardX(int index) {
            return this.getX() + this.column[index] * (this.cardWidth() + CARD_GAP);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GameOptionsScreen screen = GameOptionsScreen.this;
            Font font = screen.font;
            int width = this.cardWidth();
            for (int i = 0; i < this.cards.size(); i++) {
                Card card = this.cards.get(i);
                int x = this.cardX(i);
                int y = this.getY() + this.top[i];
                int height = card.height();
                boolean over = hovered && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
                boolean link = card.lines.size() == 1 && card.lines.get(0).link != null;
                ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.mix(ConfigStyle.colors().button(), card.color, link && over ? 0.20F : 0.07F));
                ConfigStyle.outline(graphics, x, y, width, height, ConfigStyle.withAlpha(card.color, link && over ? 0xE0 : 0x55));
                graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ConfigStyle.withAlpha(card.color, 0xC0));
                int lineY = y + CARD_PADDING;
                if (card.title != null) {
                    ConfigStyle.text(graphics, font, card.title.copy().withStyle(ChatFormatting.BOLD), x + CARD_PADDING + 2, lineY + 2, width - CARD_PADDING * 2 - 4,
                            ConfigStyle.mix(card.color, ConfigStyle.colors().text(), 0.45F));
                    lineY += CARD_TITLE;
                }
                for (Line line : card.lines) {
                    if (line.link != null) {
                        Link target = line.link;
                        int box = 24;
                        ConfigStyle.iconFrame(graphics, x + CARD_PADDING, lineY + (LINK_HEIGHT - box) / 2, box, box, ConfigStyle.withAlpha(card.color, over ? 0xFF : 0xD0));
                        target.icon.draw(graphics, x + CARD_PADDING + 4, lineY + (LINK_HEIGHT - box) / 2 + 4, box - 8, ConfigStyle.readableOn(card.color));
                        int textX = x + CARD_PADDING + box + 8;
                        int textWidth = x + width - 18 - textX;
                        ConfigStyle.text(graphics, font, target.title.copy().withStyle(ChatFormatting.BOLD), textX, lineY + 5, textWidth, ConfigStyle.colors().text());
                        ConfigStyle.text(graphics, font, target.note, textX, lineY + 16, textWidth, ConfigStyle.colors().textDim());
                        ConfigIcons.CHEVRON.draw(graphics, x + width - 15, lineY + (LINK_HEIGHT - ConfigIcons.SIZE) / 2, over ? card.color : ConfigStyle.colors().textMuted());
                        lineY += LINK_HEIGHT;
                        continue;
                    }
                    ConfigValue<?> value = line.value;
                    AbstractWidget widget = line.editor.widget();
                    boolean changed = !screen.session.isDefault(value);
                    boolean unsaved = screen.session.isModified(value);
                    boolean lineOver = over && mouseY >= lineY && mouseY < lineY + LINE_HEIGHT;
                    if (lineOver) ConfigStyle.rect(graphics, x + 2, lineY, width - 4, LINE_HEIGHT, ConfigStyle.colors().rowHover());
                    // A mark in the margin for a setting waiting to be saved, as the config screens mark values.
                    if (unsaved) ConfigStyle.rect(graphics, x + 2, lineY + 4, 2, LINE_HEIGHT - 8, ConfigStyle.colors().modified());
                    widget.setPosition(x + width - CARD_PADDING - widget.getWidth(), lineY + (LINE_HEIGHT - widget.getHeight()) / 2);
                    line.editor.setActive(screen.session.isEditable(value) && screen.session.isActive(value));
                    Compat.extractRenderState(widget, graphics, mouseX, mouseY, a);
                    int nameWidth = widget.getX() - x - CARD_PADDING - 8;
                    ConfigStyle.text(graphics, font, value.displayName(), x + CARD_PADDING + 2, lineY + (LINE_HEIGHT - 8) / 2, nameWidth,
                            unsaved ? ConfigStyle.colors().modified() : changed ? ConfigStyle.mix(ConfigStyle.colors().modified(), ConfigStyle.colors().text(), 0.5F)
                                    : ConfigStyle.colors().text());
                    if (lineOver && mouseX < widget.getX()) {
                        List<Component> tip = new ArrayList<>();
                        tip.add(value.displayName().copy().withStyle(ChatFormatting.BOLD));
                        value.type().describe().forEach(part -> tip.add(part.copy().withStyle(ChatFormatting.GRAY)));
                        tip.add(defaultOf(value).withStyle(ChatFormatting.GRAY));
                        graphics.setTooltipForNextFrame(font, tip.stream().flatMap(part -> font.split(part, 220).stream()).toList(), mouseX, mouseY);
                    }
                    lineY += LINE_HEIGHT;
                }
                ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.CARD, x, y, width, height, over, false, true);
            }
        }

        // A click on a link's card opens its screen; a setting's editor takes its own clicks.
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (super.mouseClicked(event, doubleClick)) return true;
            int width = this.cardWidth();
            for (int i = 0; i < this.cards.size(); i++) {
                Card card = this.cards.get(i);
                if (card.lines.size() != 1 || card.lines.get(0).link == null) continue;
                int x = this.cardX(i);
                int y = this.getY() + this.top[i];
                if (event.x() >= x && event.x() < x + width && event.y() >= y && event.y() < y + card.height()) {
                    GameOptionsScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    GameOptionsScreen.this.leave(card.lines.get(0).link.open.apply(GameOptionsScreen.this));
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.editors;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.editors;
        }
    }

    private static <T> net.minecraft.network.chat.MutableComponent defaultOf(ConfigValue<T> value) {
        return Component.translatableWithFallback("glazedmenu.info.default", "Default: %s", value.type().format(value.getDefault()));
    }
}
