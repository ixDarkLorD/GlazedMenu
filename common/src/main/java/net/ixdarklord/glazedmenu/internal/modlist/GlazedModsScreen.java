package net.ixdarklord.glazedmenu.internal.modlist;

import net.minecraft.util.Mth;
import net.ixdarklord.glazedmenu.internal.compat.CompatList;
import net.ixdarklord.glazedmenu.internal.compat.CompatWidget;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.compat.KeyEvent;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.ixdarklord.glazedmenu.internal.compat.RenderPipelines;
import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.ixdarklord.glazedmenu.internal.style.ThemeEffects;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedClient;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ModColors;
import net.ixdarklord.glazedmenu.internal.gui.style.ModIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Glazed Menu's mod list, in place of Mod Menu's and NeoForge's: every mod as a glass tile in a drawer on the left, and the
 * chosen one in a pane on the right whose banner glows in the mod's own color (taken from its icon), with its settings,
 * links, details, the mods it needs and the ones it bundles. Filters and search float above, with no bars.
 * <p>
 * Keys: arrows move between tiles, Enter opens the chosen mod's settings, Ctrl+F searches.
 */
public final class GlazedModsScreen extends StyledScreen {
    private static final int KEY_F = 70;
    private static final int KEY_ENTER = 257;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;
    private static final int KEY_DOWN = 264;
    private static final int KEY_UP = 265;
    private static final int HEADER = 24;
    private static final int CHIPS = 16;
    private static final int TILE_WIDTH = 50;
    private static final int TILE_HEIGHT = 58;
    private static final int TILE_GAP = 4;
    private static final int TILE_ICON = 28;
    private static final int ROW_HEIGHT = 30;
    private static final int ROW_ICON = 20;
    // The header's buttons sit in a glass dock at the right: the search, then three icon buttons.
    private static final int DOCK_BUTTONS = 3 * 22 + 4;
    private static final int HERO_HEIGHT = 70;
    private static final int HERO_ICON = 40;
    private static final float SELECT_MILLIS = 180;
    private static final ResourceLocation GRASS = new ResourceLocation("textures/block/grass_block_side.png");
    // Where the screen left off, for the next time it opens.
    private static Filter lastFilter = Filter.ALL;
    private static boolean lastDescending;
    private static @Nullable String lastSelected;
    // Where the drawer was scrolled, kept for the session: coming back from another screen continues from there.
    private static double lastScroll;
    // Groups (a mod and the mods it bundles) opened in the drawer, for the session.
    private static final java.util.Set<String> EXPANDED = new java.util.HashSet<>();

    private final @Nullable Screen parent;
    private final List<Chip> chips = new ArrayList<>();
    private final List<FlatButton> actions = new ArrayList<>();
    private Filter filter = lastFilter;
    private boolean descending = lastDescending;
    private String query = "";
    private List<ModEntry> shown = List.of();
    private @Nullable ModEntry selected;
    private long selectedAt;
    private @Nullable Drawer drawer;
    private @Nullable Details details;
    private @Nullable StyledEditBox search;
    private @Nullable FlatButton sortButton;
    private @Nullable FlatButton viewButton;

    public GlazedModsScreen(@Nullable Screen parent) {
        super(Component.translatableWithFallback("glazedmenu.mods.title", "Mods"), ConfigTheme.forMod(GlazedMenu.MOD_ID));
        this.parent = parent;
        if (lastSelected != null) this.selected = ModCatalog.get(lastSelected);
    }

    // How much the title and its mark are lifted toward white, to stand out over the backdrop.
    private static final float TITLE_LIFT = 0.35F;

    // Newer versions: green, on the update marks, tag and count.
    private static final int UPDATE_COLOR = 0xFF4CC96A;

    // Libraries listed under All as well: the game, and CoolCatLib's (Glazed Menu's companions).
    // The loader's own parts (and Java), listed under Libraries but not with the other mods unless the player asks.
    private static final java.util.Set<String> LOADER_PARTS = java.util.Set.of("java", "fabricloader", "mixinextras", "neoforge", "forge");

    private enum Filter {
        // Every mod a player installs, libraries included; not the modules bundled inside another mod, nor the loader's parts.
        ALL("glazedmenu.mods.filter.all", "All", mod -> !ModCatalog.isChild(mod) && (!LOADER_PARTS.contains(mod.id()) || GlazedSettings.showLibraries())),
        CONFIGURABLE("glazedmenu.mods.filter.configurable", "With Settings", mod -> ModCatalog.hasSettings(mod.id())),
        LIBRARIES("glazedmenu.mods.filter.libraries", "Libraries", mod -> mod.library() || ModCatalog.isChild(mod));

        final String key;
        final String fallback;
        final Predicate<ModEntry> test;

        Filter(String key, String fallback, Predicate<ModEntry> test) {
            this.key = key;
            this.fallback = fallback;
            this.test = test;
        }
    }

    // --- Layout ---

    private int headerY() {
        return MARGIN;
    }

    private int chipsY() {
        return this.headerY() + HEADER + 4;
    }

    @Override
    protected int bodyTop() {
        return this.chipsY() + CHIPS + 8;
    }

    @Override
    protected int bodyBottom() {
        return this.height - MARGIN;
    }

    // Tiles fit a few to a row; list rows need room for a name and a line of description.
    private int drawerWidth() {
        int available = this.frameWidth() - GAP;
        if (GlazedSettings.view() == GlazedSettings.View.LIST) return Mth.clamp(available * 45 / 100, 150, 280);
        int columns = Mth.clamp((available * 2 / 5 - 12) / (TILE_WIDTH + TILE_GAP), 2, 6);
        return columns * (TILE_WIDTH + TILE_GAP) - TILE_GAP + 16;
    }

    private int detailsX() {
        return this.frameLeft() + this.drawerWidth() + GAP;
    }

    private int detailsWidth() {
        return this.frameLeft() + this.frameWidth() - this.detailsX();
    }

    @Override
    protected void init() {
        int right = this.frameLeft() + this.frameWidth();
        FlatButton close = this.addRenderableWidget(FlatButton.icon(ConfigIcons.CLOSE,
                Component.translatableWithFallback("glazedmenu.mods.close", "Close"), button -> this.onClose()));
        close.setPosition(right - 22, this.headerY() + 2);
        FlatButton toggle = this.addModeToggle(right - 44);
        toggle.setY(this.headerY() + 2);
        FlatButton configs = this.addRenderableWidget(FlatButton.icon(ConfigIcons.CONFIGS,
                Component.translatableWithFallback("glazedmenu.mods.all_configs", "Mod Configs"),
                button -> this.leave(ConfigScreens.createModList(this))));
        configs.setPosition(right - 66, this.headerY() + 2);

        int searchWidth = Mth.clamp(this.width / 3, 110, 220);
        this.search = this.addRenderableWidget(new StyledEditBox(this.font, searchWidth, 18,
                Component.translatableWithFallback("glazedmenu.mods.search", "Search mods")));
        this.search.setPosition(right - DOCK_BUTTONS - 6 - searchWidth, this.headerY() + 3);
        this.search.setPlaceholder(Component.translatableWithFallback("glazedmenu.mods.search.hint", "Search mods… (Ctrl+F)"));
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            this.refilter(false);
        });

        // The filter chips, laid out as they're drawn; the sort switch at the right of their row.
        this.chips.clear();
        int x = this.frameLeft() + 2;
        for (Filter chip : Filter.values()) {
            int width = this.font.width(Component.translatableWithFallback(chip.key, chip.fallback)) + 30;
            this.chips.add(new Chip(chip, x, this.chipsY(), width));
            x += width + 4;
        }
        this.sortButton = this.addRenderableWidget(FlatButton.of(this.sortLabel(), 56, button -> {
            this.descending = !this.descending;
            lastDescending = this.descending;
            ((FlatButton) button).setMessage(this.sortLabel());
            ((FlatButton) button).withIcon(this.descending ? ConfigIcons.UP : ConfigIcons.DOWN);
            this.refilter(false);
        }).withIcon(this.descending ? ConfigIcons.UP : ConfigIcons.DOWN).tooltip(Component.translatableWithFallback("glazedmenu.mods.sort", "Sort order")));
        this.sortButton.setHeight(CHIPS);
        this.sortButton.setPosition(right - 56 - 4 - 20, this.chipsY());
        this.viewButton = this.addRenderableWidget(FlatButton.icon(this.viewIcon(), this.viewTooltip(), button -> {
            GlazedSettings.setView(GlazedSettings.view() == GlazedSettings.View.GRID ? GlazedSettings.View.LIST : GlazedSettings.View.GRID);
            this.rebuildWidgets();
        }).style(FlatButton.Style.NORMAL));
        this.viewButton.setHeight(CHIPS);
        this.viewButton.setPosition(right - 20, this.chipsY());
        // The mods folder, labelled, left of the sort switch; just its icon where the chips leave no room for the label.
        Component folderLabel = Component.translatableWithFallback("glazedmenu.mods.folder", "Mods Folder");
        Component folderTooltip = Component.translatableWithFallback("glazedmenu.mods.folder.tooltip", "Open the mods folder");
        int folderRight = right - 56 - 4 - 20 - 4;
        int folderWidth = this.font.width(folderLabel) + 26;
        FlatButton folder = folderRight - folderWidth >= x + 4
                ? FlatButton.of(folderLabel, folderWidth, button -> this.openModsFolder()).withIcon(ConfigIcons.FOLDER)
                : FlatButton.icon(ConfigIcons.FOLDER, folderTooltip, button -> this.openModsFolder()).style(FlatButton.Style.NORMAL);
        folder.tooltip(folderTooltip);
        folder.setHeight(CHIPS);
        folder.setPosition(folderRight - folder.getWidth(), this.chipsY());
        this.addRenderableWidget(folder);

        int bodyHeight = this.bodyBottom() - this.bodyTop();
        this.drawer = this.addRenderableWidget(new Drawer(this.minecraft, this.drawerWidth() - 4, bodyHeight - 8, this.bodyTop() + 4));
        this.drawer.updateSizeAndPosition(this.drawerWidth() - 4, bodyHeight - 8, this.frameLeft() + 2, this.bodyTop() + 4);
        this.details = this.addRenderableWidget(new Details(this.detailsX(), this.bodyTop(), this.detailsWidth(), bodyHeight));

        // The pane's actions, shown for whichever mod is chosen.
        this.actions.clear();
        this.actions.add(FlatButton.of(Component.translatableWithFallback("glazedmenu.mods.settings", "Settings"), 80, button -> this.openSettings())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.GEAR));
        this.actions.add(FlatButton.of(Component.translatableWithFallback("glazedmenu.mods.website", "Website"), 70, button -> this.openLink(this.selected == null ? null : this.selected.homepage())));
        this.actions.add(FlatButton.of(Component.translatableWithFallback("glazedmenu.mods.issues", "Issues"), 64, button -> this.openLink(this.selected == null ? null : this.selected.issues())));
        this.actions.add(FlatButton.of(Component.translatableWithFallback("glazedmenu.mods.source", "Source"), 64, button -> this.openLink(this.selected == null ? null : this.selected.sources())));
        this.actions.forEach(this::addRenderableWidget);
        this.refilter(true);
        if (this.drawer != null) this.drawer.setScrollAmount(lastScroll);
    }

    // Leaving for another screen (or rebuilding on a resize): remember where the drawer was.
    @Override
    public void removed() {
        if (this.drawer != null) lastScroll = this.drawer.scrollAmount();
        super.removed();
    }

    @Override
    protected void rebuildWidgets() {
        if (this.drawer != null) lastScroll = this.drawer.scrollAmount();
        super.rebuildWidgets();
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    public void onClose() {
        this.leave(this.parent);
    }

    // Opens the game's mods folder in the system's file browser, making it first if it's missing.
    private void openModsFolder() {
        java.nio.file.Path mods = this.minecraft.gameDirectory.toPath().resolve("mods");
        try {
            java.nio.file.Files.createDirectories(mods);
        } catch (java.io.IOException e) {
            GlazedMenu.LOGGER.warn("Couldn't make the mods folder {}", mods, e);
        }
        net.minecraft.Util.getPlatform().openFile(mods.toFile());
    }

    // The icon shows the view a click switches to.
    private ConfigIcons.Icon viewIcon() {
        return GlazedSettings.view() == GlazedSettings.View.GRID ? ConfigIcons.LIST : ConfigIcons.GRID;
    }

    private Component viewTooltip() {
        return GlazedSettings.view() == GlazedSettings.View.GRID
                ? Component.translatableWithFallback("glazedmenu.mods.view.list", "List view")
                : Component.translatableWithFallback("glazedmenu.mods.view.grid", "Grid view");
    }

    private Component sortLabel() {
        return this.descending ? Component.literal("Z–A") : Component.literal("A–Z");
    }

    // --- Filtering and choosing ---

    private boolean matches(ModEntry mod) {
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return true;
        return mod.name().toLowerCase(Locale.ROOT).contains(query) || mod.id().toLowerCase(Locale.ROOT).contains(query)
                || mod.description().toLowerCase(Locale.ROOT).contains(query)
                || mod.authors().stream().anyMatch(author -> author.toLowerCase(Locale.ROOT).contains(query));
    }

    private int count(Filter filter) {
        return (int) ModCatalog.all().stream().filter(filter.test).filter(this::matches).count();
    }

    // Under All, a mod's bundled mods (Fabric API's modules) are a group under it: hidden until it's opened, or shown when
    // the search finds them. The other filters list every mod on its own.
    private boolean grouped() {
        return this.filter == Filter.ALL;
    }

    /** The mods grouped under this one in the drawer. */
    private List<ModEntry> group(ModEntry mod) {
        return this.grouped() && !ModCatalog.isChild(mod) ? ModCatalog.children(mod.id()) : List.of();
    }

    private boolean isExpanded(ModEntry mod) {
        return EXPANDED.contains(mod.id());
    }

    private void toggle(ModEntry mod) {
        if (!EXPANDED.remove(mod.id())) EXPANDED.add(mod.id());
        this.refilter(true);
    }

    private void refilter(boolean keepScroll) {
        Comparator<ModEntry> byName = Comparator.comparing(mod -> mod.name().toLowerCase(Locale.ROOT));
        Comparator<ModEntry> order = Comparator.<ModEntry, Boolean>comparing(mod -> !mod.id().equals("minecraft"))
                .thenComparing(this.descending ? byName.reversed() : byName);
        List<ModEntry> shown = new ArrayList<>();
        for (ModEntry mod : ModCatalog.all().stream().filter(this.filter.test).sorted(order).toList()) {
            List<ModEntry> children = this.group(mod);
            List<ModEntry> found = this.query.isBlank() ? children : children.stream().filter(this::matches).toList();
            if (!this.matches(mod) && found.isEmpty()) continue;
            shown.add(mod);
            if (!found.isEmpty() && (this.isExpanded(mod) || !this.query.isBlank())) shown.addAll(found.stream().sorted(order).toList());
        }
        this.shown = shown;
        if (this.selected == null || !this.shown.contains(this.selected)) this.select(this.shown.isEmpty() ? null : this.shown.get(0), false);
        if (this.drawer != null) this.drawer.setMods(this.shown, keepScroll);
        this.layoutActions();
    }

    private void select(@Nullable ModEntry mod, boolean sound) {
        if (mod == this.selected) return;
        this.selected = mod;
        this.selectedAt = System.nanoTime();
        lastSelected = mod == null ? null : mod.id();
        if (sound) this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (this.details != null) this.details.reset();
        if (this.drawer != null && mod != null) this.drawer.reveal(mod);
        this.layoutActions();
    }

    // The update box: one line until opened, then the versions and the link too. Its state lasts the session.
    private static boolean updateOpen;
    private static final int UPDATE_LINE = 15;

    private static int updateBoxHeight(ModUpdates.Update update) {
        return updateOpen ? UPDATE_LINE + (isLink(update.url()) ? 22 : 12) : UPDATE_LINE;
    }

    // How far the actions and the description move down for the chosen mod's update box.
    private int updateSpace() {
        if (this.selected == null) return 0;
        return ModUpdates.of(this.selected.id()).map(update -> updateBoxHeight(update) + 6).orElse(0);
    }

    // Updates arrive in the background: the actions follow when they do.
    private int updatesSeen = -1;

    @Override
    public void tick() {
        super.tick();
        if (this.updatesSeen != ModUpdates.generation()) {
            this.updatesSeen = ModUpdates.generation();
            this.layoutActions();
        }
    }

    // The actions the chosen mod has, in a row under its banner.
    private void layoutActions() {
        if (this.actions.isEmpty()) return;
        ModEntry mod = this.selected;
        boolean[] available = {
                mod != null && ModCatalog.hasSettings(mod.id()),
                mod != null && isLink(mod.homepage()),
                mod != null && isLink(mod.issues()),
                mod != null && isLink(mod.sources())
        };
        int[] natural = {80, 70, 64, 64};
        int total = 0;
        int count = 0;
        for (int i = 0; i < natural.length; i++) {
            if (!available[i]) continue;
            total += natural[i];
            count++;
        }
        int room = this.detailsWidth() - 24 - Math.max(0, count - 1) * 4;
        float shrink = total > room ? room / (float) total : 1;
        int x = this.detailsX() + 12;
        int y = this.bodyTop() + HERO_HEIGHT + 6 + this.updateSpace();
        for (int i = 0; i < this.actions.size(); i++) {
            FlatButton button = this.actions.get(i);
            button.visible = available[i];
            if (!available[i]) continue;
            button.setWidth(Math.max(30, (int) (natural[i] * shrink)));
            button.setPosition(x, y);
            x += button.getWidth() + 4;
        }
    }

    private static boolean isLink(@Nullable String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    private void openSettings() {
        if (this.selected == null) return;
        Screen settings = ModCatalog.settings(this, this.selected.id());
        if (settings != null) this.leave(settings);
        else GlazedClient.toast(Component.literal(this.selected.name()), Component.translatableWithFallback("glazedmenu.mods.no_settings", "Its settings screen didn't open"));
    }

    private void openLink(@Nullable String url) {
        if (isLink(url)) ConfirmLinkScreen.confirmLinkNow(url, this, true);
    }

    // The mod's color: its theme's, its icon's, or the game's green (ModColors).
    private static int accentOf(String modId) {
        return ModColors.accent(modId);
    }

    // A mod with a newer version: a small green badge with an up arrow (at a tile's top right, at a row's right end).
    private static final int UPDATE_MARK = 9;

    private static boolean updateMark(GuiGraphicsExtractor graphics, ModEntry mod, int x, int y) {
        if (ModUpdates.of(mod.id()).isEmpty()) return false;
        int size = UPDATE_MARK;
        ConfigStyle.rect(graphics, x, y, size, size, UPDATE_COLOR);
        ConfigStyle.outline(graphics, x, y, size, size, ConfigStyle.mix(UPDATE_COLOR, 0xFF000000, 0.45F));
        ConfigIcons.UP.draw(graphics, x + 1, y + 1, size - 2, 0xFFFFFFFF);
        return true;
    }

    // A mod's icon in a square: its own, a grass block for the game, or its initial in its color.
    private static void icon(GuiGraphicsExtractor graphics, ModEntry mod, int accent, int x, int y, int size) {
        if (ModIcons.draw(graphics, mod.id(), ConfigTheme.forMod(mod.id()), x, y, size)) return;
        GuiGraphicsExtractor.Pose pose = graphics.pose();
        if (mod.id().equals("minecraft")) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GRASS, x, y, 0, 0, size, size, 16, 16, 16, 16);
            return;
        }
        Font font = Minecraft.getInstance().font;
        ConfigStyle.rect(graphics, x, y, size, size, ConfigStyle.withAlpha(accent, 0x40));
        ConfigStyle.outline(graphics, x, y, size, size, ConfigStyle.withAlpha(accent, 0x90));
        String letter = mod.name().isEmpty() ? "?" : mod.name().substring(0, 1).toUpperCase(Locale.ROOT);
        float scale = Math.max(1, size / 14F);
        pose.pushMatrix();
        pose.translate(x + size / 2F, y + size / 2F);
        pose.scale(scale, scale);
        graphics.text(font, Component.literal(letter).withStyle(ChatFormatting.BOLD), -font.width(letter) / 2, -4, accent, false);
        pose.popMatrix();
    }

    // --- Keys ---

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isLeaving()) return true;
        if (event.hasControlDownWithQuirk() && event.key() == KEY_F && this.search != null) {
            this.setFocused(this.search);
            return true;
        }
        boolean typing = this.search != null && this.search.isFocused();
        if (!typing && !this.shown.isEmpty() && this.drawer != null) {
            int index = this.selected == null ? -1 : this.shown.indexOf(this.selected);
            int columns = this.drawer.columns();
            int next = switch (event.key()) {
                case KEY_LEFT -> index - 1;
                case KEY_RIGHT -> index + 1;
                case KEY_UP -> index - columns;
                case KEY_DOWN -> index + columns;
                default -> Integer.MIN_VALUE;
            };
            if (next != Integer.MIN_VALUE) {
                this.select(this.shown.get(Mth.clamp(next, 0, this.shown.size() - 1)), false);
                return true;
            }
            if (event.key() == KEY_ENTER && this.selected != null && ModCatalog.hasSettings(this.selected.id())) {
                this.openSettings();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.isLeaving()) return true;
        for (Chip chip : this.chips) {
            if (chip.contains(event.x(), event.y())) {
                if (chip.filter != this.filter) {
                    this.filter = chip.filter;
                    lastFilter = chip.filter;
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    this.refilter(false);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    // --- Drawing ---

    // No bars: a floating header, the chips, and two glass panes.
    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = this.frameLeft();
        int top = this.headerY();
        // The title: a list mark and "Mods" in Glazed Menu's flowing gradient, and how many.
        int mark = HEADER;
        int markIcon = mark - 6;
        ConfigIcons.LIST.draw(graphics, left + (mark - markIcon) / 2, top + (mark - markIcon) / 2, markIcon, GlazedBrand.lift(GlazedBrand.now(0), TITLE_LIFT));
        // "Mods" at 1.5x, its capitals (7 pixels tall) centered on the tile; the count sits on the same baseline.
        float titleScale = 1.5F;
        float titleTop = top + (mark - 7 * titleScale) / 2;
        GuiGraphicsExtractor.Pose pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(left + mark + 3, titleTop);
        pose.scale(titleScale, titleScale);
        GlazedBrand.text(graphics, this.font, this.title.getString(), 0, 0, TITLE_LIFT);
        pose.popMatrix();
        int titleWidth = Math.round(this.font.width(this.title.copy().withStyle(ChatFormatting.BOLD)) * titleScale);
        long total = ModCatalog.all().stream().filter(mod -> !ModCatalog.isChild(mod)).count();
        Component count = Component.translatableWithFallback("glazedmenu.mods.count", "%s loaded", total);
        pose.pushMatrix();
        pose.translate(left + mark + 3 + titleWidth + 6, titleTop + 7 * titleScale - 7);
        graphics.text(this.font, count, 0, 0, ConfigStyle.colors().textDim(), false);
        pose.popMatrix();
        // How many mods have updates: a small green capsule (an arrow and the number) after the count, where it fits.
        long updates = ModUpdates.count();
        if (updates > 0 && this.search != null) {
            String number = Long.toString(updates);
            int capsuleX = left + mark + 3 + titleWidth + 6 + this.font.width(count) + 6;
            int capsuleY = Math.round(titleTop + 7 * titleScale - 7) - 2;
            int capsuleWidth = 13 + this.font.width(number) + 4;
            if (capsuleX + capsuleWidth <= this.search.getX() - 24) {
                ConfigStyle.rect(graphics, capsuleX, capsuleY, capsuleWidth, 11, ConfigStyle.withAlpha(UPDATE_COLOR, 0x38));
                ConfigStyle.outline(graphics, capsuleX, capsuleY, capsuleWidth, 11, ConfigStyle.withAlpha(UPDATE_COLOR, 0xB0));
                ConfigIcons.UP.draw(graphics, capsuleX + 2, capsuleY + 1, 9, UPDATE_COLOR);
                graphics.text(this.font, number, capsuleX + 13, capsuleY + 2, UPDATE_COLOR, false);
                if (mouseX >= capsuleX && mouseX < capsuleX + capsuleWidth && mouseY >= capsuleY && mouseY < capsuleY + 11) {
                    graphics.setTooltipForNextFrame(this.font, updates == 1
                            ? Component.translatableWithFallback("glazedmenu.mods.updates.one", "1 update")
                            : Component.translatableWithFallback("glazedmenu.mods.updates", "%s updates", updates), mouseX, mouseY);
                }
            }
        }
        // The dock: a glass bar behind the search and the header's buttons, with a hairline between them.
        if (this.search != null) {
            int right = this.frameLeft() + this.frameWidth();
            int dockLeft = this.search.getX() - 20;
            ConfigStyle.bar(graphics, dockLeft, top, right - dockLeft, mark);
            GlazedBrand.fill(graphics, dockLeft + 1, top + mark - 1, right - dockLeft - 2, 1, 0xB0);
            graphics.fill(right - DOCK_BUTTONS + 1, top + 5, right - DOCK_BUTTONS + 2, top + mark - 5, ConfigStyle.colors().panelBorder());
        }
        if (this.search != null) {
            ConfigIcons.SEARCH.draw(graphics, this.search.getX() - 14, this.search.getY() + 4, this.search.isFocused() ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
        }

        // Chips: the chosen one filled with the accent.
        for (Chip chip : this.chips) chip.extract(graphics, mouseX, mouseY);

        // The drawer, and the details pane.
        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        ConfigStyle.panel(graphics, left, bodyTop, this.drawerWidth(), bodyHeight);
        GlazedBrand.fill(graphics, left + 1, bodyTop, this.drawerWidth() - 2, 2, 0xD0);
        ConfigStyle.panel(graphics, this.detailsX(), bodyTop, this.detailsWidth(), bodyHeight);
        if (this.shown.isEmpty()) {
            Component none = Component.translatableWithFallback("glazedmenu.mods.none", "No mods match");
            ConfigIcons.SEARCH.draw(graphics, left + (this.drawerWidth() - this.font.width(none)) / 2 - 14, bodyTop + 19, ConfigStyle.colors().textMuted());
            ConfigStyle.centeredText(graphics, this.font, none, left + this.drawerWidth() / 2, bodyTop + 20, this.drawerWidth() - 30, ConfigStyle.colors().textMuted());
        }
    }

    // How far the chosen mod's banner has come in: 0 to 1.
    private float revealed() {
        float progress = Math.min(1, (System.nanoTime() - this.selectedAt) / 1_000_000F / SELECT_MILLIS);
        return 1 - (1 - progress) * (1 - progress);
    }

    private final class Chip {
        private final Filter filter;
        private final int x;
        private final int y;
        private final int width;

        Chip(Filter filter, int x, int y, int width) {
            this.filter = filter;
            this.x = x;
            this.y = y;
            this.width = width;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + CHIPS;
        }

        void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            Font font = GlazedModsScreen.this.font;
            boolean chosen = this.filter == GlazedModsScreen.this.filter;
            boolean hovered = this.contains(mouseX, mouseY);
            int accent = ConfigStyle.accent();
            // The chosen chip is filled with Glazed Menu's flowing gradient; the others with the button colors.
            if (chosen) {
                GlazedBrand.fill(graphics, this.x, this.y, this.width, CHIPS, 0xFF, 0.3F);
            } else {
                ConfigStyle.rect(graphics, this.x, this.y, this.width, CHIPS,
                        hovered ? ConfigStyle.colors().buttonHover() : ConfigStyle.withAlpha(ConfigStyle.colors().button(), 0xC0));
            }
            int middle = GlazedBrand.now(0.5F);
            // Its outline is a much brighter shade of the gradient, standing apart from the fill; the others' the panel's
            // border, or the accent under the mouse.
            ConfigStyle.outline(graphics, this.x, this.y, this.width, CHIPS, chosen ? ConfigStyle.mix(middle, 0xFFFFFFFF, 0.6F)
                    : hovered ? ConfigStyle.withAlpha(accent, 0xC0) : ConfigStyle.colors().panelBorder());
            int text = chosen ? 0xFFFFFFFF : ConfigStyle.colors().text();
            Component name = Component.translatableWithFallback(this.filter.key, this.filter.fallback);
            graphics.text(font, name, this.x + 7, this.y + 4, text, chosen);
            // The count in a small capsule at the end.
            String count = Integer.toString(GlazedModsScreen.this.count(this.filter));
            int countWidth = font.width(count) + 6;
            int countX = this.x + this.width - countWidth - 3;
            ConfigStyle.rect(graphics, countX, this.y + 3, countWidth, CHIPS - 6, chosen ? ConfigStyle.withAlpha(0xFF000000, 0x30) : ConfigStyle.withAlpha(accent, 0x30));
            graphics.text(font, count, countX + 3, this.y + 4, chosen ? text : ConfigStyle.colors().textDim(), false);
            ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.BUTTON, this.x, this.y, this.width, CHIPS, hovered, false, true);
        }
    }

    // --- The drawer: tiles in rows ---

    // The mods as tiles a few to a row, or one to a row; the view is the player's choice (GlazedSettings).
    private final class Drawer extends CompatList<Row> {
        Drawer(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, TILE_HEIGHT + TILE_GAP);
        }

        private boolean grid() {
            return GlazedSettings.view() == GlazedSettings.View.GRID;
        }

        int columns() {
            return this.grid() ? Math.max(1, (this.width - 12 + TILE_GAP) / (TILE_WIDTH + TILE_GAP)) : 1;
        }

        private int rowHeight() {
            return this.grid() ? TILE_HEIGHT + TILE_GAP : ROW_HEIGHT + 2;
        }

        void setMods(List<ModEntry> mods, boolean keepScroll) {
            double scroll = this.scrollAmount();
            this.clearEntries();
            if (this.grid()) {
                // Tiles fill rows; a group's mods start their own rows, in a framed tray under a header line.
                int columns = this.columns();
                List<Row> rows = new ArrayList<>();
                List<ModEntry> row = new ArrayList<>();
                @Nullable ModEntry tray = null;
                for (ModEntry mod : mods) {
                    ModEntry parent = GlazedModsScreen.this.trayOf(mod);
                    if (parent != tray || row.size() == columns) {
                        if (!row.isEmpty()) rows.add(new TileRow(List.copyOf(row), tray));
                        row.clear();
                        if (parent != null && parent != tray) rows.add(new TrayHeader(parent));
                        tray = parent;
                    }
                    row.add(mod);
                }
                if (!row.isEmpty()) rows.add(new TileRow(List.copyOf(row), tray));
                // The tray's last row closes its frame.
                for (int i = 0; i < rows.size(); i++) {
                    if (rows.get(i) instanceof TileRow tiles && tiles.tray != null
                            && (i + 1 == rows.size() || !(rows.get(i + 1) instanceof TileRow next) || next.tray != tiles.tray)) {
                        tiles.last = true;
                    }
                }
                for (Row entry : rows) this.addEntry(entry, this.heightOf(entry));
            } else {
                // A group's mods hang off a line from their parent; the last one's is where the line ends.
                for (int i = 0; i < mods.size(); i++) {
                    ModEntry tray = GlazedModsScreen.this.trayOf(mods.get(i));
                    boolean last = tray != null && (i + 1 == mods.size() || GlazedModsScreen.this.trayOf(mods.get(i + 1)) != tray);
                    this.addEntry(new ListRow(mods.get(i), tray, last), this.rowHeight());
                }
            }
            this.setScrollAmount(keepScroll ? scroll : 0);
            if (GlazedModsScreen.this.selected != null && !keepScroll) this.reveal(GlazedModsScreen.this.selected);
        }

        // A row's height: a group's header is short; a group's last row leaves room under its tray's frame, the same gap
        // as between rows.
        private int heightOf(Row row) {
            if (row instanceof TrayHeader) return TRAY_HEADER;
            if (row instanceof TileRow tiles && tiles.last) return this.rowHeight() + TRAY_INSET + TILE_GAP;
            return this.rowHeight();
        }

        // Scrolls so the mod's row is in view.
        void reveal(ModEntry mod) {
            int rowTop = -1;
            int height = this.rowHeight();
            int top = 0;
            for (Row row : this.children()) {
                if (row.holds(mod)) {
                    rowTop = top;
                    height = this.heightOf(row);
                }
                top += this.heightOf(row);
            }
            if (rowTop < 0) return;
            if (rowTop < this.scrollAmount()) this.setScrollAmount(rowTop);
            else if (rowTop + height > this.scrollAmount() + this.height) this.setScrollAmount(rowTop + height - this.height);
        }

        @Override
        public int getRowWidth() {
            return this.width - 8;
        }

        @Override
        public int getRowLeft() {
            return this.getX() + 4;
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
            int scrollTop = this.scrollBarY();
            int scrollHeight = this.scrollerHeight();
            graphics.fillGradient(this.scrollBarX(), scrollTop, this.scrollBarX() + 2, scrollTop + scrollHeight,
                    ConfigStyle.withAlpha(GlazedBrand.now(0), 0xC0), ConfigStyle.withAlpha(GlazedBrand.now(1), 0xC0));
        }
    }

    // The chosen mod's rail: a line in its color that breathes softly, its ends fading out smoothly rather than stopping
    // short. Each end is one gradient (turned on its side for a horizontal rail), so it fades below the pixel grid.
    private static final int RAIL_TAIL = 10;
    private static final float RAIL_BREATH_MILLIS = 2400;

    private static void selectRail(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean horizontal, int color) {
        float phase = (System.nanoTime() / 1_000_000L % (long) RAIL_BREATH_MILLIS) / RAIL_BREATH_MILLIS;
        float breath = 0.72F + 0.28F * (0.5F + 0.5F * (float) Math.sin(phase * Math.PI * 2));
        int solid = ConfigStyle.withAlpha(color, Math.round((color >>> 24) * breath));
        int clear = ConfigStyle.withAlpha(color, 0);
        int length = horizontal ? width : height;
        int tail = Math.min(RAIL_TAIL, length * 2 / 5);
        if (!horizontal) {
            graphics.fillGradient(x, y, x + width, y + tail, clear, solid);
            graphics.fill(x, y + tail, x + width, y + height - tail, solid);
            graphics.fillGradient(x, y + height - tail, x + width, y + height, solid, clear);
            return;
        }
        graphics.fill(x + tail, y, x + width - tail, y + height, solid);
        GuiGraphicsExtractor.Pose pose = graphics.pose();
        // Turned a quarter: the gradient's top-to-bottom runs left to right.
        for (int side = 0; side < 2; side++) {
            pose.pushMatrix();
            pose.translate(side == 0 ? x : x + width - tail, y + height);
            pose.rotate((float) -Math.PI / 2);
            graphics.fillGradient(0, 0, height, tail, side == 0 ? clear : solid, side == 0 ? solid : clear);
            pose.popMatrix();
        }
    }

    // The group a mod is shown in: its parent, when it's listed under it.
    private @Nullable ModEntry trayOf(ModEntry mod) {
        if (!this.grouped() || !ModCatalog.isChild(mod) || mod.parent() == null) return null;
        return ModCatalog.get(mod.parent());
    }

    // A group's mark: a chevron (down when open) and how many mods are in it.
    // Drawn at three quarters size, so it marks the mod without crowding it.
    private static final float GROUP_MARK_SCALE = 0.75F;
    private static final int GROUP_MARK_HEIGHT = Math.round(11 * GROUP_MARK_SCALE);

    private int groupMarkWidth(ModEntry mod) {
        return Math.round((this.font.width(Integer.toString(ModCatalog.children(mod.id()).size())) + 16) * GROUP_MARK_SCALE);
    }

    private void groupMark(GuiGraphicsExtractor graphics, ModEntry mod, int x, int y, int accent) {
        String text = Integer.toString(ModCatalog.children(mod.id()).size());
        int width = this.font.width(text) + 16;
        // The frame and chevron in the mod's color lifted toward the text color, so they don't sink into the tile.
        int bright = ConfigStyle.mix(accent, ConfigStyle.colors().text(), 0.45F);
        GuiGraphicsExtractor.Pose pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(GROUP_MARK_SCALE, GROUP_MARK_SCALE);
        // An opaque capsule, a dark shade of the mod's color, so the count reads over any icon or tile; white text.
        ConfigStyle.rect(graphics, 0, 0, width, 11, ConfigStyle.mix(0xFF000000, accent, 0.4F));
        ConfigStyle.outline(graphics, 0, 0, width, 11, bright);
        (this.isExpanded(mod) ? ConfigIcons.DOWN : ConfigIcons.CHEVRON).draw(graphics, 2, 1, 9, 0xFFFFFFFF);
        graphics.text(this.font, text, 12, 2, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    // Clicking a mod chooses it; clicking it again opens or closes its group, or opens its settings on a double click.
    private void clicked(ModEntry mod, boolean doubleClick) {
        // Every click clicks, not only the one that chooses the mod.
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (mod != this.selected) {
            this.select(mod, false);
            return;
        }
        // Clicking the chosen mod again: a group's opens or closes, another's settings open on a double click.
        if (!this.group(mod).isEmpty()) this.toggle(mod);
        else if (doubleClick && ModCatalog.hasSettings(mod.id())) this.openSettings();
    }

    private abstract static class Row extends CompatList.Entry<Row> {
        abstract boolean holds(ModEntry mod);

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    // One mod: its icon, name and version, and the first line of its description.
    private final class ListRow extends Row {
        private static final int INDENT = 12;
        private final ModEntry mod;
        private final @Nullable ModEntry tray;

        // The group's last mod: the line from the parent ends at it.
        private final boolean last;

        ListRow(ModEntry mod, @Nullable ModEntry tray, boolean last) {
            this.mod = mod;
            this.tray = tray;
            this.last = last;
        }

        @Override
        boolean holds(ModEntry mod) {
            return this.mod == mod;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GlazedModsScreen.this.font;
            ModEntry mod = this.mod;
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            // A group's mods hang off a line in their parent's color.
            if (this.tray != null) {
                int trayAccent = accentOf(this.tray.id());
                graphics.fill(x + 4, y - 2, x + 5, this.last ? y + ROW_HEIGHT / 2 + 1 : y + ROW_HEIGHT + 2, ConfigStyle.withAlpha(trayAccent, 0x90));
                graphics.fill(x + 5, y + ROW_HEIGHT / 2, x + INDENT - 1, y + ROW_HEIGHT / 2 + 1, ConfigStyle.withAlpha(trayAccent, 0x90));
                x += INDENT;
                width -= INDENT;
            }
            boolean chosen = mod == GlazedModsScreen.this.selected;
            int accent = accentOf(mod.id());
            ModColors.washBox(graphics, accent, x, y, width, ROW_HEIGHT, hovered, chosen, true);
            if (chosen) selectRail(graphics, x + 1, y + 3, 2, ROW_HEIGHT - 6, false, accent);
            icon(graphics, mod, accent, x + 6, y + (ROW_HEIGHT - ROW_ICON) / 2, ROW_ICON);
            int textX = x + 6 + ROW_ICON + 7;
            int dot = ModUpdates.of(mod.id()).isPresent() ? UPDATE_MARK + 5 : 0;
            boolean group = !GlazedModsScreen.this.group(mod).isEmpty();
            int mark = group ? GlazedModsScreen.this.groupMarkWidth(mod) + 4 : 0;
            int textWidth = x + width - 6 - dot - mark - textX;
            int nameColor = chosen || hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim();
            int versionColor = chosen ? accent : ConfigStyle.colors().textMuted();
            Component name = Component.literal(mod.name()).withStyle(ChatFormatting.BOLD);
            float scale = nameScale(font, name, textWidth);
            if (scale >= MIN_NAME_SCALE || mod.name().indexOf(' ') < 0) {
                // The name on the first line (shrunk a little if it needs to be) with its version after it, the
                // description under them.
                int nameWidth;
                if (scale >= MIN_NAME_SCALE && scale < 1) {
                    scaledText(graphics, font, name, textX, y + 6, scale, nameColor);
                    nameWidth = textWidth;
                } else {
                    nameWidth = Math.min(font.width(name), textWidth);
                    ConfigStyle.text(graphics, font, name, textX, y + 6, textWidth, nameColor);
                }
                if (nameWidth + 6 < textWidth) {
                    ConfigStyle.text(graphics, font, Component.literal(mod.version()), textX + nameWidth + 5, y + 6, textWidth - nameWidth - 5, versionColor);
                }
                String line = mod.description().lines().findFirst().orElse("");
                ConfigStyle.text(graphics, font, Component.literal(line.isBlank() ? mod.id() : line), textX, y + 17, textWidth,
                        ConfigStyle.mix(ConfigStyle.colors().textMuted(), ConfigStyle.colors().textDim(), 0.7F));
            } else {
                // A name too long for one line takes both (split between words), the version after it where it fits.
                int cut = boldBreak(font, mod.name(), textWidth);
                ConfigStyle.text(graphics, font, Component.literal(mod.name().substring(0, cut).strip()).withStyle(ChatFormatting.BOLD),
                        textX, y + 6, textWidth, nameColor);
                Component rest = Component.literal(mod.name().substring(cut).strip()).withStyle(ChatFormatting.BOLD);
                int restWidth = Math.min(font.width(rest), textWidth);
                ConfigStyle.text(graphics, font, rest, textX, y + 17, textWidth, nameColor);
                if (restWidth + 6 < textWidth) {
                    ConfigStyle.text(graphics, font, Component.literal(mod.version()), textX + restWidth + 5, y + 17, textWidth - restWidth - 5, versionColor);
                }
            }
            updateMark(graphics, mod, x + width - 5 - UPDATE_MARK, y + (ROW_HEIGHT - UPDATE_MARK) / 2);
            if (group) GlazedModsScreen.this.groupMark(graphics, mod, x + width - 6 - dot - GlazedModsScreen.this.groupMarkWidth(mod), y + (ROW_HEIGHT - GROUP_MARK_HEIGHT) / 2, accent);
            ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.CARD, x, y, width, ROW_HEIGHT, hovered, chosen, true);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GlazedModsScreen.this.clicked(this.mod, doubleClick);
            return true;
        }
    }

    // An open group's tray: a faint panel in its parent's color, framed, a few pixels around its tiles, under a header
    // line naming the group, how many mods are in it, and closing it when clicked.
    private static final int TRAY_HEADER = 18;
    private static final int TRAY_INSET = 4;

    private static void trayFill(GuiGraphicsExtractor graphics, ModEntry parent, int left, int top, int right, int bottom) {
        int accent = accentOf(parent.id());
        graphics.fill(left + 1, top, right - 1, bottom - 1, ConfigStyle.withAlpha(ConfigStyle.mix(ConfigStyle.colors().panel(), accent, 0.18F), 0x70));
    }

    private static int trayBorder(ModEntry parent) {
        return ConfigStyle.mix(ConfigStyle.colors().panelBorder(), accentOf(parent.id()), 0.45F);
    }

    private final class TrayHeader extends Row {
        private final ModEntry parent;

        TrayHeader(ModEntry parent) {
            this.parent = parent;
        }

        @Override
        boolean holds(ModEntry mod) {
            return false;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GlazedModsScreen.this.font;
            int accent = accentOf(this.parent.id());
            int left = this.getX() - TRAY_INSET;
            int right = this.getX() + this.getWidth() + TRAY_INSET - 2;
            int top = this.getY() + 4;
            int bottom = this.getY() + TRAY_HEADER;
            trayFill(graphics, this.parent, left, top, right, bottom);
            int border = trayBorder(this.parent);
            // The frame's top, with its corners cut, and its sides down into the first row.
            graphics.fill(left + 1, top, right - 1, top + 1, border);
            graphics.fill(left, top + 1, left + 1, bottom, border);
            graphics.fill(right - 1, top + 1, right, bottom, border);
            // The header line: an arrow, the group's name in its color, and how many mods it holds; lit under the mouse.
            if (hovered) graphics.fill(left + 1, top + 1, right - 1, bottom, ConfigStyle.withAlpha(accent, 0x18));
            int textY = top + 3;
            ConfigIcons.DOWN.draw(graphics, left + 4, textY - 1, 9, hovered ? 0xFFFFFFFF : accent);
            Component name = Component.literal(this.parent.name()).withStyle(ChatFormatting.BOLD);
            int count = ModCatalog.children(this.parent.id()).size();
            Component mods = Component.translatableWithFallback("glazedmenu.mods.group.count", "%s mods", count);
            int countWidth = font.width(mods);
            int nameRoom = right - 6 - countWidth - 6 - (left + 15);
            ConfigStyle.text(graphics, font, name, left + 15, textY, nameRoom, accent);
            graphics.text(font, mods, right - 6 - countWidth, textY, ConfigStyle.colors().textMuted(), false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GlazedModsScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            GlazedModsScreen.this.toggle(this.parent);
            return true;
        }
    }

    // A tile's name under its icon: one line when it fits (shrunk a little if that's enough), else split over two
    // between words, each cut short with an ellipsis if it still doesn't fit.
    private static void tileName(GuiGraphicsExtractor graphics, Font font, String name, int x, int y, int color) {
        int room = TILE_WIDTH - 6;
        int center = x + TILE_WIDTH / 2;
        Component text = Component.literal(name);
        float scale = nameScale(font, text, room);
        if (scale >= 1) {
            ConfigStyle.centeredText(graphics, font, text, center, y + TILE_HEIGHT - 15, room, color);
            return;
        }
        if (scale >= MIN_NAME_SCALE) {
            scaledText(graphics, font, text, center - Math.round(font.width(text) * scale / 2), y + TILE_HEIGHT - 15, scale, color);
            return;
        }
        int cut = firstLength(font, name, room);
        // The first line: whole words; a first word too long for it is cut short with an ellipsis.
        ConfigStyle.centeredText(graphics, font, Component.literal(name.substring(0, cut).strip()), center, y + TILE_HEIGHT - 19, room, color);
        ConfigStyle.centeredText(graphics, font, Component.literal(name.substring(cut).strip()), center, y + TILE_HEIGHT - 10, room, color);
    }

    // Names a little too long for their line shrink to fit, down to this; longer ones split over two lines.
    private static final float MIN_NAME_SCALE = 0.75F;

    // How much a name must shrink to fit its room: 1 when it fits, below MIN_NAME_SCALE when shrinking isn't enough.
    private static float nameScale(Font font, Component name, int room) {
        int width = font.width(name);
        return width <= room ? 1 : room / (float) width;
    }

    // Text at a scale, its baseline where full-size text at (x, y) would have it.
    private static void scaledText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, float scale, int color) {
        GuiGraphicsExtractor.Pose pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y + 7 * (1 - scale));
        pose.scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    // Where a bold name breaks between two lines: after the last whole word that fits the first, or after its first word.
    private static int boldBreak(Font font, String name, int room) {
        int cut = -1;
        for (int i = name.indexOf(' '); i > 0; i = name.indexOf(' ', i + 1)) {
            if (font.width(Component.literal(name.substring(0, i)).withStyle(ChatFormatting.BOLD)) > room) break;
            cut = i;
        }
        return cut > 0 ? cut : name.indexOf(' ');
    }

    // Where the name breaks between its lines: after the last whole word that fits the first line, or after its first
    // word when even that doesn't fit; inside the name only when it has no spaces at all.
    private static int firstLength(Font font, String name, int room) {
        int fit = font.plainSubstrByWidth(name, room).length();
        int space = name.lastIndexOf(' ', fit);
        if (space > 0) return space;
        int firstSpace = name.indexOf(' ');
        return firstSpace > 0 ? firstSpace : fit;
    }

    private final class TileRow extends Row {
        private final List<ModEntry> mods;
        private final @Nullable ModEntry tray;
        // The last row of a group's tray, which closes its frame.
        boolean last;

        TileRow(List<ModEntry> mods, @Nullable ModEntry tray) {
            this.mods = mods;
            this.tray = tray;
        }

        @Override
        boolean holds(ModEntry mod) {
            return this.mods.contains(mod);
        }

        private int tileX(int column) {
            return this.getX() + column * (TILE_WIDTH + TILE_GAP);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = GlazedModsScreen.this.font;
            if (this.tray != null) {
                // The group's tray: its sides through every row, closed under the last.
                int left = this.getX() - TRAY_INSET;
                int right = this.getX() + this.getWidth() + TRAY_INSET - 2;
                int top = this.getY() - TILE_GAP;
                int bottom = this.last ? this.getY() + TILE_HEIGHT + TRAY_INSET : this.getY() + TILE_HEIGHT;
                trayFill(graphics, this.tray, left, top, right, bottom);
                int border = trayBorder(this.tray);
                graphics.fill(left, top, left + 1, bottom - (this.last ? 1 : 0), border);
                graphics.fill(right - 1, top, right, bottom - (this.last ? 1 : 0), border);
                if (this.last) graphics.fill(left + 1, bottom - 1, right - 1, bottom, border);
            }
            for (int i = 0; i < this.mods.size(); i++) {
                ModEntry mod = this.mods.get(i);
                int x = this.tileX(i);
                int y = this.getY();
                boolean over = hovered && mouseX >= x && mouseX < x + TILE_WIDTH && mouseY >= y && mouseY < y + TILE_HEIGHT;
                boolean chosen = mod == GlazedModsScreen.this.selected;
                int accent = accentOf(mod.id());
                // Glass: alike at rest; the mod's color washes up from the bottom when hovered, and more when chosen; a sheen on top.
                ModColors.washBox(graphics, accent, x, y, TILE_WIDTH, TILE_HEIGHT, over, chosen, false);
                graphics.fillGradient(x + 1, y + 1, x + TILE_WIDTH - 1, y + TILE_HEIGHT / 2, ConfigStyle.withAlpha(0xFFFFFFFF, chosen ? 0x1A : 0x0C), 0);
                if (chosen) selectRail(graphics, x + 4, y + TILE_HEIGHT - 2, TILE_WIDTH - 8, 1, true, accent);

                icon(graphics, mod, accent, x + (TILE_WIDTH - TILE_ICON) / 2, y + 6, TILE_ICON);
                updateMark(graphics, mod, x + TILE_WIDTH - 3 - UPDATE_MARK, y + 3);
                tileName(graphics, font, mod.name(), x, y, chosen ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
                // A group's count in the tile's top left corner (its update mark takes the top right).
                if (!GlazedModsScreen.this.group(mod).isEmpty()) {
                    GlazedModsScreen.this.groupMark(graphics, mod, x + 3, y + 3, accent);
                }
                ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.CARD, x, y, TILE_WIDTH, TILE_HEIGHT, over, chosen, true);
                if (over) {
                    graphics.setTooltipForNextFrame(font, Component.literal(mod.name()).withStyle(ChatFormatting.BOLD)
                            .append(Component.literal("  " + mod.version()).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            for (int i = 0; i < this.mods.size(); i++) {
                int x = this.tileX(i);
                if (event.x() >= x && event.x() < x + TILE_WIDTH && event.y() >= this.getY() && event.y() < this.getY() + TILE_HEIGHT) {
                    GlazedModsScreen.this.clicked(this.mods.get(i), doubleClick);
                    return true;
                }
            }
            return false;
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

    // --- The details pane ---

    // The chosen mod: a banner in its color with its icon, name, version and authors; below the actions, a scrolling
    // description, then its details and the mods it needs and bundles, as chips that choose those mods.
    private final class Details extends CompatWidget {
        private static final int PADDING = 12;
        private final List<LinkChip> links = new ArrayList<>();
        // Auto-scroll: a long description drifts down by itself (after a moment, slowly), rests at the end, glides back
        // up and starts over. It waits while the mouse is over it, and stops for this mod once the player scrolls.
        private static final float AUTO_WAIT_MILLIS = 2500;
        private static final float AUTO_HOLD_MILLIS = 3000;
        private static final float AUTO_DOWN_SPEED = 8;
        private static final float AUTO_UP_SPEED = 90;
        // Scrolling by the wheel eases toward where it was sent, never past either end, in about this long.
        private static final float SMOOTH_MILLIS = 70;
        private static final int WHEEL_STEP = 20;
        private double scroll;
        // Where the wheel sent it: the scroll eases toward this.
        private double target;
        private int contentHeight;
        private int visibleHeight;
        private AutoPhase autoPhase = AutoPhase.WAIT;
        private long autoPhaseAt = System.nanoTime();
        private long lastFrame = System.nanoTime();
        private boolean manual;

        private enum AutoPhase {
            WAIT,
            DOWN,
            HOLD,
            UP
        }

        Details(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        void reset() {
            this.scroll = 0;
            this.target = 0;
            this.manual = false;
            this.autoPhase = AutoPhase.WAIT;
            this.autoPhaseAt = System.nanoTime();
        }

        private int maxScroll() {
            return Math.max(0, this.contentHeight - this.visibleHeight);
        }

        // Moves the scroll for this frame: easing toward where the wheel sent it, or else drifting by itself.
        private void scrollStep(boolean hovered) {
            long now = System.nanoTime();
            float seconds = Math.min(0.1F, (now - this.lastFrame) / 1_000_000_000F);
            this.lastFrame = now;
            int max = this.maxScroll();
            if (this.manual) {
                this.target = Mth.clamp(this.target, 0, max);
                if (!GlazedSettings.transitions()) {
                    this.scroll = this.target;
                } else {
                    this.scroll += (this.target - this.scroll) * (1 - Math.exp(-seconds * 1000 / SMOOTH_MILLIS));
                    if (Math.abs(this.target - this.scroll) < 0.1) this.scroll = this.target;
                }
                return;
            }
            this.autoScroll(hovered, now, seconds, max);
            this.target = this.scroll;
        }

        private void autoScroll(boolean hovered, long now, float seconds, int max) {
            if (max == 0 || !GlazedSettings.transitions()) return;
            // Hovering holds it where it is; the current wait starts over afterwards.
            if (hovered) {
                if (this.autoPhase != AutoPhase.DOWN && this.autoPhase != AutoPhase.UP) this.autoPhaseAt = now;
                return;
            }
            float inPhase = (now - this.autoPhaseAt) / 1_000_000F;
            switch (this.autoPhase) {
                case WAIT -> {
                    if (inPhase >= AUTO_WAIT_MILLIS) this.autoPhase(AutoPhase.DOWN, now);
                }
                case DOWN -> {
                    this.scroll = Math.min(max, this.scroll + AUTO_DOWN_SPEED * seconds);
                    if (this.scroll >= max) this.autoPhase(AutoPhase.HOLD, now);
                }
                case HOLD -> {
                    if (inPhase >= AUTO_HOLD_MILLIS) this.autoPhase(AutoPhase.UP, now);
                }
                case UP -> {
                    this.scroll = Math.max(0, this.scroll - AUTO_UP_SPEED * seconds);
                    if (this.scroll <= 0) this.autoPhase(AutoPhase.WAIT, now);
                }
            }
        }

        private void autoPhase(AutoPhase phase, long now) {
            this.autoPhase = phase;
            this.autoPhaseAt = now;
        }

        private int contentTop() {
            return this.getY() + HERO_HEIGHT + 36 + GlazedModsScreen.this.updateSpace();
        }

        private int contentBottom() {
            return this.getY() + this.getHeight() - 6;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            ModEntry mod = GlazedModsScreen.this.selected;
            Font font = GlazedModsScreen.this.font;
            if (mod == null) {
                ConfigStyle.centeredText(graphics, font, Component.translatableWithFallback("glazedmenu.mods.pick", "Pick a mod"),
                        this.getX() + this.getWidth() / 2, this.getY() + this.getHeight() / 2 - 4, this.getWidth() - 20, ConfigStyle.colors().textMuted());
                return;
            }
            int accent = accentOf(mod.id());
            float revealed = GlazedModsScreen.this.revealed();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();

            // The banner: the mod's color washing down from the top, a soft band of light, and its icon glowing.
            graphics.fillGradient(x + 1, y + 1, x + width - 1, y + HERO_HEIGHT, ConfigStyle.withAlpha(accent, Math.round(0x70 * revealed)), ConfigStyle.withAlpha(accent, 0));
            graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ConfigStyle.withAlpha(accent, Math.round(0xE0 * revealed)));
            int slide = Math.round((1 - revealed) * 10);
            int iconX = x + PADDING + slide;
            int iconY = y + 14;
            ConfigStyle.rect(graphics, iconX - 4, iconY - 4, HERO_ICON + 8, HERO_ICON + 8, ConfigStyle.withAlpha(accent, 0x28));
            ConfigStyle.outline(graphics, iconX - 4, iconY - 4, HERO_ICON + 8, HERO_ICON + 8, ConfigStyle.withAlpha(accent, 0xA0));
            icon(graphics, mod, accent, iconX, iconY, HERO_ICON);
            int textX = iconX + HERO_ICON + 12;
            int textWidth = x + width - PADDING - textX;
            // The name large, the version after it in the normal size on the same baseline, the authors below.
            float nameScale = 1.5F;
            Component version = Component.literal(mod.version());
            int versionWidth = Math.min(font.width(version), textWidth / 3);
            Component name = Component.literal(mod.name()).withStyle(ChatFormatting.BOLD);
            int nameRoom = Math.max(20, Math.round((textWidth - versionWidth - 6) / nameScale));
            FormattedCharSequence nameLine = ConfigStyle.ellipsize(font, name, nameRoom);
            int nameTop = y + 12;
            GuiGraphicsExtractor.Pose pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(textX, nameTop);
            pose.scale(nameScale, nameScale);
            graphics.text(font, nameLine, 0, 0, ConfigStyle.colors().text(), false);
            pose.popMatrix();
            float baseline = nameTop + 7 * nameScale;
            pose.pushMatrix();
            pose.translate(textX + Math.round(font.width(nameLine) * nameScale) + 6, baseline - 7);
            // The version and tags in the mod's color, lifted toward the text color to stand out.
            int bright = ConfigStyle.mix(accent, ConfigStyle.colors().text(), 0.4F);
            ConfigStyle.text(graphics, font, version, 0, 0, versionWidth, bright);
            pose.popMatrix();
            if (!mod.authors().isEmpty()) {
                Component authors = Component.translatableWithFallback("glazedmenu.mods.by", "by %s", String.join(", ", mod.authors()));
                ConfigStyle.text(graphics, font, authors, textX, nameTop + 17, textWidth, ConfigStyle.colors().textDim());
            }
            List<Component> tags = new ArrayList<>();
            // Minecraft sits with the libraries but is tagged as the game.
            if (mod.id().equals("minecraft")) tags.add(Component.translatableWithFallback("glazedmenu.mods.tag.game", "GAME"));
            else if (mod.library()) tags.add(Component.translatableWithFallback("glazedmenu.mods.tag.library", "LIBRARY"));
            if (mod.side() == ModEntry.Side.CLIENT) tags.add(Component.translatableWithFallback("glazedmenu.mods.tag.client", "CLIENT"));
            if (mod.side() == ModEntry.Side.SERVER) tags.add(Component.translatableWithFallback("glazedmenu.mods.tag.server", "SERVER"));
            int tagX = textX;
            for (Component tag : tags) {
                if (tagX + font.width(tag) + 8 > x + width - PADDING) break;
                tagX += ConfigStyle.badge(graphics, font, tag, tagX, y + 45, bright) + 4;
            }

            // A newer version: its box under the banner, above the actions.
            this.updateLink = null;
            this.updateHeader = null;
            java.util.Optional<ModUpdates.Update> newer = ModUpdates.of(mod.id());
            if (newer.isPresent()) this.updateBox(graphics, mod, newer.get(), x + PADDING, this.getY() + HERO_HEIGHT + 4, width - PADDING * 2, mouseX, mouseY);

            // The rest scrolls under the actions.
            int top = this.contentTop();
            int bottom = this.contentBottom();
            this.scrollStep(mouseX >= x && mouseX < x + width && mouseY >= top - 4 && mouseY < bottom);
            graphics.enableScissor(x + 1, top - 4, x + width - 1, bottom);
            this.links.clear();
            int cursor = top - (int) this.scroll;
            int inner = width - PADDING * 2;
            String description = mod.description().isBlank()
                    ? Component.translatableWithFallback("glazedmenu.mods.no_description", "No description.").getString() : mod.description();
            for (FormattedCharSequence wrapped : font.split(Component.literal(description), inner)) {
                graphics.text(font, wrapped, x + PADDING, cursor, ConfigStyle.colors().text(), false);
                cursor += 10;
            }
            cursor += 8;
            cursor = this.heading(graphics, Component.translatableWithFallback("glazedmenu.mods.details", "Details"), accent, cursor);
            cursor = this.row(graphics, Component.translatableWithFallback("glazedmenu.mods.id", "Mod ID"), Component.literal(mod.id()), cursor);
            if (mod.license() != null) cursor = this.row(graphics, Component.translatableWithFallback("glazedmenu.mods.license", "License"), Component.literal(mod.license()), cursor);
            if (!mod.contributors().isEmpty()) {
                cursor = this.row(graphics, Component.translatableWithFallback("glazedmenu.mods.contributors", "Thanks to"), Component.literal(String.join(", ", mod.contributors())), cursor);
            }
            List<ModEntry> requires = mod.dependencies().stream().filter(id -> !id.equals(mod.id())).map(ModCatalog::get)
                    .filter(dependency -> dependency != null && !dependency.id().equals("java")).toList();
            if (!requires.isEmpty()) {
                cursor += 4;
                cursor = this.heading(graphics, Component.translatableWithFallback("glazedmenu.mods.requires", "Needs"), accent, cursor);
                cursor = this.chips(graphics, requires, cursor, mouseX, mouseY);
            }
            List<ModEntry> bundles = ModCatalog.children(mod.id());
            if (!bundles.isEmpty()) {
                cursor += 4;
                cursor = this.heading(graphics, Component.translatableWithFallback("glazedmenu.mods.bundles", "Bundles"), accent, cursor);
                cursor = this.chips(graphics, bundles, cursor, mouseX, mouseY);
            }
            ModEntry parent = mod.parent() == null ? null : ModCatalog.get(mod.parent());
            if (parent != null) {
                cursor += 4;
                cursor = this.heading(graphics, Component.translatableWithFallback("glazedmenu.mods.bundled_in", "Comes with"), accent, cursor);
                cursor = this.chips(graphics, List.of(parent), cursor, mouseX, mouseY);
            }
            graphics.disableScissor();
            this.contentHeight = cursor + (int) this.scroll - top;
            int visible = bottom - top;
            this.visibleHeight = visible;
            this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, this.contentHeight - visible));
            if (this.contentHeight > visible) {
                int barHeight = Math.max(12, visible * visible / this.contentHeight);
                int barY = top + (int) ((visible - barHeight) * (this.scroll / (this.contentHeight - visible)));
                ConfigStyle.rect(graphics, x + width - 4, barY, 2, barHeight, ConfigStyle.withAlpha(accent, 0xA0));
            }
        }

        private int heading(GuiGraphicsExtractor graphics, Component text, int accent, int y) {
            Font font = GlazedModsScreen.this.font;
            Component bold = text.copy().withStyle(ChatFormatting.BOLD);
            graphics.text(font, bold, this.getX() + PADDING, y, accent, false);
            int lineX = this.getX() + PADDING + font.width(bold) + 6;
            graphics.fill(lineX, y + 4, this.getX() + this.getWidth() - PADDING, y + 5, ConfigStyle.withAlpha(accent, 0x40));
            return y + 14;
        }

        private int row(GuiGraphicsExtractor graphics, Component label, Component value, int y) {
            Font font = GlazedModsScreen.this.font;
            int labelWidth = 62;
            graphics.text(font, label, this.getX() + PADDING, y, ConfigStyle.colors().textMuted(), false);
            List<FormattedCharSequence> lines = font.split(value, this.getWidth() - PADDING * 2 - labelWidth);
            for (FormattedCharSequence line : lines) {
                graphics.text(font, line, this.getX() + PADDING + labelWidth, y, ConfigStyle.colors().text(), false);
                y += 10;
            }
            return y + 2;
        }

        // Mods as small chips with their icon, wrapping; clicking one chooses it.
        private int chips(GuiGraphicsExtractor graphics, List<ModEntry> mods, int y, int mouseX, int mouseY) {
            Font font = GlazedModsScreen.this.font;
            int x = this.getX() + PADDING;
            int right = this.getX() + this.getWidth() - PADDING;
            for (ModEntry mod : mods) {
                int width = Math.min(font.width(mod.name()) + 22, right - this.getX() - PADDING);
                if (x + width > right) {
                    x = this.getX() + PADDING;
                    y += 18;
                }
                boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 15
                        && mouseY >= this.contentTop() - 4 && mouseY < this.contentBottom();
                int accent = accentOf(mod.id());
                ConfigStyle.rect(graphics, x, y, width, 15, over ? ConfigStyle.withAlpha(accent, 0x50) : ConfigStyle.withAlpha(accent, 0x22));
                ConfigStyle.outline(graphics, x, y, width, 15, ConfigStyle.withAlpha(accent, over ? 0xE0 : 0x70));
                icon(graphics, mod, accent, x + 3, y + 2, 11);
                ConfigStyle.text(graphics, font, Component.literal(mod.name()), x + 17, y + 4, width - 20, ConfigStyle.colors().text());
                this.links.add(new LinkChip(mod, x, y, width));
                x += width + 4;
            }
            return y + 20;
        }

        // Only the scrolling part is the pane's: the action buttons over its banner get their own clicks (a screen hands
        // a click to the first widget under the mouse).
        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            if (!this.visible || mouseX < this.getX() || mouseX >= this.getX() + this.getWidth()) return false;
            if (mouseY >= this.contentTop() - 4 && mouseY < this.contentBottom()) return true;
            // The update box, above the actions.
            int space = GlazedModsScreen.this.updateSpace();
            return space > 0 && mouseY >= this.getY() + HERO_HEIGHT + 4 && mouseY < this.getY() + HERO_HEIGHT + 4 + space - 6;
        }

        // A newer version: a small green box. Its first line ("Update available", the new version, a chevron) opens and
        // closes it; open, it shows the installed version leading to the new one, and a link to its page.
        private void updateBox(GuiGraphicsExtractor graphics, ModEntry mod, ModUpdates.Update update, int x, int y, int width,
                               int mouseX, int mouseY) {
            Font font = GlazedModsScreen.this.font;
            boolean link = isLink(update.url());
            int height = updateBoxHeight(update);
            boolean headerHovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + UPDATE_LINE;
            ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.withAlpha(UPDATE_COLOR, headerHovered ? 0x30 : 0x22));
            ConfigStyle.outline(graphics, x, y, width, height, ConfigStyle.withAlpha(UPDATE_COLOR, headerHovered ? 0xC0 : 0x90));
            graphics.fill(x + 1, y + 3, x + 3, y + height - 3, UPDATE_COLOR);
            ConfigIcons.UP.draw(graphics, x + 8, y + 3, 9, UPDATE_COLOR);
            Component title = Component.translatableWithFallback("glazedmenu.mods.update.title", "Update available").withStyle(ChatFormatting.BOLD);
            graphics.text(font, title, x + 20, y + 4, UPDATE_COLOR, false);
            // The new version and the chevron at the right of the first line.
            (updateOpen ? ConfigIcons.DOWN : ConfigIcons.CHEVRON).draw(graphics, x + width - 13, y + 3, 9, headerHovered ? 0xFFFFFFFF : UPDATE_COLOR);
            Component version = Component.literal(update.version());
            int versionWidth = Math.min(font.width(version), width - font.width(title) - 50);
            if (versionWidth > 10) {
                ConfigStyle.text(graphics, font, version, x + width - 17 - versionWidth, y + 4, versionWidth, ConfigStyle.colors().textDim());
            }
            this.updateHeader = new int[]{x, y, width, UPDATE_LINE};
            if (!updateOpen) return;
            Component versions = Component.literal(mod.version()).withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("  →  ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(update.version()).withStyle(style -> style.withColor(UPDATE_COLOR & 0xFFFFFF)));
            ConfigStyle.text(graphics, font, versions, x + 20, y + UPDATE_LINE, width - 26, ConfigStyle.colors().text());
            if (link) {
                Component open = Component.translatableWithFallback("glazedmenu.mods.update.open", "Open its page");
                int openWidth = font.width(open) + 10;
                int openY = y + UPDATE_LINE + 11;
                boolean over = mouseX >= x + 20 && mouseX < x + 20 + openWidth && mouseY >= openY && mouseY < openY + 9;
                graphics.text(font, over ? open.copy().withStyle(ChatFormatting.UNDERLINE) : open, x + 20, openY, over ? 0xFFFFFFFF : ConfigStyle.colors().textDim(), false);
                ConfigIcons.CHEVRON.draw(graphics, x + 20 + font.width(open) + 2, openY, 8, over ? 0xFFFFFFFF : ConfigStyle.colors().textDim());
                this.updateLink = new int[]{x + 20, openY - 1, openWidth, 10};
                this.updateUrl = update.url();
            }
        }

        // Where the update box's first line and its link are drawn (x, y, width, height), for clicks.
        private int @Nullable [] updateHeader;
        private int @Nullable [] updateLink;
        private @Nullable String updateUrl;

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!this.isMouseOver(event.x(), event.y())) return false;
            int[] header = this.updateHeader;
            if (header != null && event.x() >= header[0] && event.x() < header[0] + header[2] && event.y() >= header[1] && event.y() < header[1] + header[3]) {
                updateOpen = !updateOpen;
                GlazedModsScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                GlazedModsScreen.this.layoutActions();
                return true;
            }
            int[] update = this.updateLink;
            if (update != null && event.x() >= update[0] && event.x() < update[0] + update[2] && event.y() >= update[1] && event.y() < update[1] + update[3]) {
                GlazedModsScreen.this.openLink(this.updateUrl);
                return true;
            }
            for (LinkChip link : this.links) {
                if (event.x() >= link.x && event.x() < link.x + link.width && event.y() >= link.y && event.y() < link.y + 15) {
                    GlazedModsScreen.this.select(link.mod, true);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!this.isMouseOver(mouseX, mouseY)) return false;
            // The wheel takes over from the drift; its target stays within the content, so it never overshoots the end.
            if (!this.manual) this.target = this.scroll;
            this.manual = true;
            this.target = Mth.clamp(this.target - scrollY * WHEEL_STEP, 0, this.maxScroll());
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            ModEntry mod = GlazedModsScreen.this.selected;
            if (mod != null) output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal(mod.name()));
        }

        private record LinkChip(ModEntry mod, int x, int y, int width) {}
    }
}
