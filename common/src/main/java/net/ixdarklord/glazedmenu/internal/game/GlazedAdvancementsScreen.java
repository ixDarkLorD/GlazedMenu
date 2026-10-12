package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The advancements, in place of the game's own screen: its tabs as a ribbon, each saying how many of its advancements
 * are done, and the chosen tab's advancements as a list, each set in under the one it comes after, with a bar where it
 * takes several steps. Beside the list, the chosen advancement: what it asks for, its steps ticked off as they're done,
 * and what it comes after and leads to. The list can be narrowed to the advancements done or under way, and a search
 * looks through every tab. Tree View opens the game's own screen.
 * <p>
 * Only the advancements the server has told the client of are listed, as on the game's screen.
 */
public final class GlazedAdvancementsScreen extends GameListScreen implements ClientAdvancements.Listener {
    private static final String VANILLA = "net.minecraft.client.gui.screens.advancements.AdvancementsScreen";
    private static final int PICTURE = 40;
    private static final int INDENT = 10;
    private static final int MAX_DEPTH = 5;
    // What the list shows, kept while the game runs.
    private static Filter filter = Filter.ALL;

    private final ClientAdvancements advancements;
    // The tabs' roots, in the order the game tells of them.
    private final Map<AdvancementHolder, AdvancementNode> roots = new LinkedHashMap<>();
    private final Map<AdvancementHolder, AdvancementProgress> progress = new HashMap<>();
    private final Map<AdvancementHolder, ItemStack> icons = new HashMap<>();
    private List<Tab> tabs = List.of();
    private List<AdvancementNode> tabRoots = List.of();
    private @Nullable AdvancementHolder chosenRoot;
    private int done;
    private int total;
    // The game told of a change: the tabs and the rows are worked out again on the next tick.
    private boolean dirty;
    private final List<FlatButton> filterButtons = new ArrayList<>();

    private enum Filter {
        ALL,
        IN_PROGRESS,
        DONE;

        Component label() {
            return switch (this) {
                case ALL -> Component.translatableWithFallback("glazedmenu.advancements.filter.all", "All");
                case IN_PROGRESS -> Component.translatableWithFallback("glazedmenu.advancements.filter.in_progress", "In Progress");
                case DONE -> Component.translatableWithFallback("glazedmenu.advancements.filter.done", "Done");
            };
        }
    }

    private record Listed(AdvancementNode node, int depth) {}

    /**
     * How many advancements the player has made, and how many there are to make (the ones that are shown as
     * advancements, not the recipes the game counts with them), for the pause screen's note.
     */
    static int[] counts(ClientAdvancements advancements) {
        int[] counts = new int[2];
        // In a world of the player's own, every advancement there is; on a server, the ones it has let the player
        // know of so far (it tells of an advancement only once the one before it is made).
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() != null) {
            for (AdvancementHolder holder : minecraft.getSingleplayerServer().getAdvancements().getAllAdvancements()) {
                if (holder.value().display().isPresent()) counts[1]++;
            }
        } else {
            for (AdvancementNode node : advancements.tree().nodes()) {
                if (node.advancement().display().isPresent()) counts[1]++;
            }
        }
        // The progress the game holds of every advancement the player has any in (it's there to read since 26.3).
        advancements.progress().forEach((holder, progress) -> {
            if (holder.value().display().isPresent() && progress.isDone()) counts[0]++;
        });
        return counts;
    }

    private GlazedAdvancementsScreen(@Nullable Screen parent, ClientAdvancements advancements) {
        super(Component.translatable("gui.advancements"), parent, ConfigIcons.TROPHY);
        this.advancements = advancements;
    }

    /** The screen to open instead: this one for the game's own advancements screen, over the screen that opened it. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof AdvancementsScreen) || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceAdvancements()) return screen;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return screen;
        return new GlazedAdvancementsScreen(minecraft.gui.screen(), minecraft.player.connection.getAdvancements());
    }

    // --- What the game tells of ---

    // 26.3 tells of a change as a whole: the roots and the progress are read again from what the client holds.
    @Override
    public void onAdvancementsUpdated() {
        this.roots.clear();
        for (AdvancementNode root : this.advancements.tree().roots()) {
            if (root.advancement().display().isPresent()) this.roots.put(root.holder(), root);
        }
        this.progress.clear();
        this.progress.putAll(this.advancements.progress());
        this.dirty = true;
    }

    @Override
    public void onAdvancementsCleared() {
        this.roots.clear();
        this.progress.clear();
        this.icons.clear();
        this.chosenRoot = null;
        this.dirty = true;
    }

    @Override
    public void onSelectedTabChanged(@Nullable AdvancementHolder selectedTab) {
        this.chosenRoot = selectedTab;
        this.dirty = true;
    }

    // Opened, or back from the game's own screen: the game tells of every advancement again, and of the tab last open.
    @Override
    public void added() {
        super.added();
        this.roots.clear();
        this.progress.clear();
        this.advancements.setListener(this);
        if ((this.chosenRoot == null || !this.roots.containsKey(this.chosenRoot)) && !this.roots.isEmpty()) {
            this.chosenRoot = this.roots.keySet().iterator().next();
        }
        this.advancements.setSelectedTab(this.chosenRoot, true);
        this.rebuildTabs();
        this.dirty = false;
    }

    @Override
    public void removed() {
        super.removed();
        this.advancements.setListener(null);
        ClientPacketListener connection = this.minecraft.getConnection();
        if (connection != null) connection.send(ServerboundSeenAdvancementsPacket.closedScreen());
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.dirty) return;
        this.dirty = false;
        boolean hadTabs = !this.tabs.isEmpty();
        this.rebuildTabs();
        // The ribbon coming or going moves everything under it.
        if (hadTabs != !this.tabs.isEmpty()) this.rebuildWidgets();
        else this.rebuildRows(true);
    }

    // --- The advancements ---

    private @Nullable DisplayInfo display(AdvancementNode node) {
        return node.advancement().display().orElse(null);
    }

    private boolean isDone(AdvancementNode node) {
        AdvancementProgress progress = this.progress.get(node.holder());
        return progress != null && progress.isDone();
    }

    private boolean isStarted(AdvancementNode node) {
        AdvancementProgress progress = this.progress.get(node.holder());
        return progress != null && progress.hasProgress();
    }

    // As the game's own screen shows them: a hidden one only once it's done.
    private boolean isShown(AdvancementNode node) {
        DisplayInfo display = this.display(node);
        return display != null && (!display.hidden() || this.isDone(node));
    }

    // A tab's advancements, each under the one it comes after.
    private List<Listed> listed(AdvancementNode root) {
        List<Listed> listed = new ArrayList<>();
        this.collect(root, 0, listed);
        return listed;
    }

    private void collect(AdvancementNode node, int depth, List<Listed> into) {
        boolean shown = this.isShown(node);
        if (shown) into.add(new Listed(node, depth));
        for (AdvancementNode child : node.children()) this.collect(child, shown ? depth + 1 : depth, into);
    }

    private ItemStack icon(AdvancementNode node) {
        return this.icons.computeIfAbsent(node.holder(), holder -> {
            DisplayInfo display = this.display(node);
            return display != null ? display.icon().create() : ItemStack.EMPTY;
        });
    }

    // Each tab in a color of its own: the game's five in theirs, any other in one picked by its name.
    private static int colorOf(AdvancementHolder root) {
        String path = root.id().getPath();
        if (path.startsWith("story/")) return GlazedBrand.identity(0xFF5BC467);
        if (path.startsWith("nether/")) return GlazedBrand.identity(0xFFF07A5A);
        if (path.startsWith("end/")) return GlazedBrand.identity(0xFFB07CF0);
        if (path.startsWith("adventure/")) return GlazedBrand.identity(0xFF4DA3FF);
        if (path.startsWith("husbandry/")) return GlazedBrand.identity(0xFFF2C94C);
        int[] colors = {GlazedBrand.identity(0xFF3CC8B4), GlazedBrand.identity(0xFFE86BA8), GlazedBrand.identity(0xFF4CC9E0), GlazedBrand.identity(0xFFF2A33B), GlazedBrand.identity(0xFF8B7CF6), GlazedBrand.identity(0xFF9AA4B8)};
        return colors[Math.floorMod(root.id().hashCode(), colors.length)];
    }

    private void rebuildTabs() {
        List<Tab> tabs = new ArrayList<>();
        List<AdvancementNode> tabRoots = new ArrayList<>();
        int done = 0;
        int total = 0;
        for (AdvancementNode root : this.roots.values()) {
            DisplayInfo display = this.display(root);
            if (display == null) continue;
            List<Listed> listed = this.listed(root);
            int tabDone = (int) listed.stream().filter(entry -> this.isDone(entry.node)).count();
            done += tabDone;
            total += listed.size();
            tabs.add(new Tab(display.title().copy().append(" " + tabDone + "/" + listed.size()), null, this.icon(root), colorOf(root.holder())));
            tabRoots.add(root);
        }
        this.tabs = tabs;
        this.tabRoots = tabRoots;
        this.done = done;
        this.total = total;
        if ((this.chosenRoot == null || !this.roots.containsKey(this.chosenRoot)) && !tabRoots.isEmpty()) this.chosenRoot = tabRoots.getFirst().holder();
    }

    @Override
    protected List<Tab> tabs() {
        return this.tabs;
    }

    @Override
    protected int chosenTab() {
        for (int i = 0; i < this.tabRoots.size(); i++) {
            if (this.tabRoots.get(i).holder().equals(this.chosenRoot)) return i;
        }
        return 0;
    }

    @Override
    protected void chooseTab(int index) {
        if (index < 0 || index >= this.tabRoots.size()) return;
        this.chosenRoot = this.tabRoots.get(index).holder();
        // The game remembers the tab, and tells the server of it.
        this.advancements.setSelectedTab(this.chosenRoot, true);
        this.dirty = false;
        if (!this.query.isEmpty() && this.search != null) this.search.setValue("");
        else this.rebuildRows(false);
    }

    private boolean passes(AdvancementNode node) {
        return switch (filter) {
            case ALL -> true;
            case DONE -> this.isDone(node);
            case IN_PROGRESS -> !this.isDone(node) && this.isStarted(node);
        };
    }

    private boolean matches(AdvancementNode node, String query) {
        DisplayInfo display = this.display(node);
        return display != null && (display.title().getString().toLowerCase(Locale.ROOT).contains(query)
                || display.description().getString().toLowerCase(Locale.ROOT).contains(query));
    }

    @Override
    protected List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            // A search looks through every tab, each under its name.
            for (AdvancementNode root : this.tabRoots) {
                List<Listed> found = this.listed(root).stream().filter(entry -> this.passes(entry.node) && this.matches(entry.node, query)).toList();
                if (found.isEmpty()) continue;
                rows.add(new Heading(this.display(root).title()));
                for (Listed entry : found) rows.add(new AdvancementRow(entry.node, 0));
            }
        } else {
            for (AdvancementNode root : this.tabRoots) {
                if (!root.holder().equals(this.chosenRoot)) continue;
                for (Listed entry : this.listed(root)) {
                    // Narrowed to some of them, they're no longer under one another.
                    if (this.passes(entry.node)) rows.add(new AdvancementRow(entry.node, filter == Filter.ALL ? entry.depth : 0));
                }
            }
        }
        if (rows.isEmpty()) {
            rows.add(new Notice(ConfigIcons.TROPHY, this.tabRoots.isEmpty()
                    ? Component.translatable("advancements.empty")
                    : Component.translatableWithFallback("glazedmenu.advancements.none", "No advancements match")));
        }
        return rows;
    }

    private @Nullable AdvancementNode chosenNode() {
        return this.chosen instanceof AdvancementHolder holder ? this.advancements.tree().get(holder) : null;
    }

    // --- Layout ---

    @Override
    protected Component searchName() {
        return Component.translatableWithFallback("glazedmenu.advancements.search", "Search advancements");
    }

    @Override
    protected Component searchHint() {
        return Component.translatableWithFallback("glazedmenu.advancements.search.hint", "Search advancements… (Ctrl+F)");
    }

    private void setFilter(Filter chosen) {
        filter = chosen;
        this.updateFilterButtons();
        this.rebuildRows(false);
    }

    private void updateFilterButtons() {
        if (this.filterButtons.size() == 1) {
            this.filterButtons.getFirst().setMessage(filter.label());
            return;
        }
        for (int i = 0; i < this.filterButtons.size(); i++) {
            this.filterButtons.get(i).style(Filter.values()[i] == filter ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL);
        }
    }

    @Override
    protected void initButtons() {
        // The top bar: what the list shows, left of the search. A button for each where they fit, else one that goes
        // from one to the next.
        this.filterButtons.clear();
        int y = this.topBarWidgetY(20);
        if (this.frameWidth() >= 520) {
            int x = this.headerRight;
            for (int i = Filter.values().length - 1; i >= 0; i--) {
                Filter value = Filter.values()[i];
                int width = this.font.width(value.label()) + 12;
                FlatButton button = this.addRenderableWidget(FlatButton.of(value.label(), width, pressed -> this.setFilter(value)));
                x -= width;
                button.setPosition(x, y);
                x -= 3;
                this.filterButtons.addFirst(button);
            }
            this.headerRight = x - 5;
        } else {
            int width = 12;
            for (Filter value : Filter.values()) width = Math.max(width, this.font.width(value.label()) + 12);
            FlatButton button = this.addRenderableWidget(FlatButton.of(filter.label(), width,
                    pressed -> this.setFilter(Filter.values()[(filter.ordinal() + 1) % Filter.values().length]))
                    .tooltip(Component.translatableWithFallback("glazedmenu.advancements.filter.tooltip", "Every advancement, the ones under way, or the ones done")));
            button.setPosition(this.headerRight - width, y);
            this.filterButtons.add(button);
            this.headerRight = button.getX() - 8;
        }
        this.updateFilterButtons();

        // The bottom bar: the game's own screen, and the way back.
        int barY = this.barWidgetY(this.bottomBarY());
        Component tree = Component.translatableWithFallback("glazedmenu.advancements.tree", "Tree View");
        int treeWidth = this.font.width(tree) + 28;
        // A class of its own here, so it isn't taken for the game's and replaced.
        FlatButton treeButton = this.addRenderableWidget(FlatButton.of(tree, treeWidth,
                button -> this.leave(new AdvancementsScreen(this.advancements, this) {})).withIcon(ConfigIcons.LIST)
                .tooltip(Component.translatableWithFallback("glazedmenu.advancements.tree.tooltip", "The game's own screen: the advancements as a tree")));
        treeButton.setPosition(this.frameLeft() + 4, barY);
        FlatButton doneButton = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.onClose())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        doneButton.setPosition(this.frameLeft() + this.frameWidth() - 4 - 84, barY);
    }

    @Override
    protected void onChosen() {}

    // The advancements key closes the screen, as it does the game's own (but not while it's being typed in the search).
    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = this.search != null && this.search.isFocused();
        if (!typing && this.minecraft.options.keyAdvancements.matches(event)) {
            this.minecraft.gui.setScreen(null);
            this.minecraft.mouseHandler.grabMouse();
            return true;
        }
        return super.keyPressed(event);
    }

    // --- Drawing ---

    @Override
    protected Component subtitle() {
        if (this.total == 0) return Component.translatable("advancements.empty");
        return Component.translatableWithFallback("glazedmenu.advancements.status", "%s of %s done · %s%%", this.done, this.total, this.done * 100 / this.total);
    }

    private static int typeColor(AdvancementType type) {
        return switch (type) {
            case CHALLENGE -> 0xFFB07CF0;
            case GOAL -> 0xFFF2C94C;
            case TASK -> ConfigStyle.colors().textDim();
        };
    }

    private static Component typeName(AdvancementType type) {
        return switch (type) {
            case CHALLENGE -> Component.translatableWithFallback("glazedmenu.advancements.type.challenge", "Challenge");
            case GOAL -> Component.translatableWithFallback("glazedmenu.advancements.type.goal", "Goal");
            case TASK -> Component.translatableWithFallback("glazedmenu.advancements.type.task", "Task");
        };
    }

    // A step's name as the game keeps it ("minecraft:cherry_grove"), made readable ("Cherry grove").
    private static String stepName(String criterion) {
        String name = criterion.substring(criterion.indexOf(':') + 1).replace('_', ' ').replace('/', ' ').trim();
        return name.isEmpty() ? criterion : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    protected void extractDetails(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        AdvancementNode node = this.chosenNode();
        DisplayInfo display = node != null ? this.display(node) : null;
        if (node == null || display == null) {
            ConfigStyle.centeredText(graphics, this.font, Component.translatableWithFallback("glazedmenu.advancements.pick", "Pick an advancement"),
                    x + width / 2, y + height / 2 - 4, width, ConfigStyle.colors().textMuted());
            return;
        }
        int bottom = y + height;
        AdvancementProgress progress = this.progress.get(node.holder());
        boolean done = this.isDone(node);
        boolean started = this.isStarted(node);
        // Its item, twice the size, on a box in its type's color (green once it's done).
        int boxColor = done ? ConfigStyle.colors().success() : typeColor(display.type());
        ConfigStyle.rect(graphics, x, y, PICTURE, PICTURE, ConfigStyle.mix(ConfigStyle.colors().button(), boxColor, 0.30F));
        ConfigStyle.outline(graphics, x, y, PICTURE, PICTURE, ConfigStyle.withAlpha(boxColor, 0xC0));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 4, y + 4);
        graphics.pose().scale(2, 2);
        graphics.fakeItem(this.icon(node), 0, 0);
        graphics.pose().popMatrix();

        int textX = x + PICTURE + 7;
        int textWidth = width - PICTURE - 7;
        int lineY = y + 2;
        for (FormattedCharSequence line : this.font.split(display.title().copy().withStyle(ChatFormatting.BOLD), textWidth)) {
            if (lineY > y + 14) break;
            graphics.text(this.font, line, textX, lineY, ConfigStyle.colors().text(), false);
            lineY += 10;
        }
        // Its type, and how far along it is, as tags.
        int tagY = y + PICTURE - 12;
        int tagX = textX + ConfigStyle.badge(graphics, this.font, typeName(display.type()), textX, tagY, typeColor(display.type())) + 4;
        Component state = done ? Component.translatableWithFallback("glazedmenu.advancements.state.done", "Done")
                : started ? Component.translatableWithFallback("glazedmenu.advancements.state.in_progress", "In progress")
                : Component.translatableWithFallback("glazedmenu.advancements.state.not_started", "Not started");
        if (tagX + this.font.width(state) + 8 <= x + width) {
            ConfigStyle.badge(graphics, this.font, state, tagX, tagY,
                    done ? ConfigStyle.colors().success() : started ? ConfigStyle.colors().warning() : ConfigStyle.colors().textMuted());
        }

        lineY = y + PICTURE + 7;
        for (FormattedCharSequence line : this.font.split(display.description(), width)) {
            if (lineY + 10 > bottom) return;
            graphics.text(this.font, line, x, lineY, ConfigStyle.colors().textDim(), false);
            lineY += 10;
        }
        lineY += 5;

        // What it comes after and leads to, kept at the pane's foot; the steps take the room between.
        List<Component> links = new ArrayList<>();
        List<Component> linkNames = new ArrayList<>();
        AdvancementNode parent = node.parent();
        DisplayInfo parentDisplay = parent != null ? this.display(parent) : null;
        if (parentDisplay != null) {
            linkNames.add(Component.translatableWithFallback("glazedmenu.advancements.after", "Comes after"));
            links.add(parentDisplay.title());
        }
        List<AdvancementNode> next = new ArrayList<>();
        for (AdvancementNode child : node.children()) {
            if (this.isShown(child)) next.add(child);
        }
        if (!next.isEmpty()) {
            linkNames.add(Component.translatableWithFallback("glazedmenu.advancements.leads_to", "Leads to"));
            Component first = this.display(next.getFirst()).title();
            links.add(next.size() == 1 ? first : first.copy().append(Component.translatableWithFallback("glazedmenu.advancements.more", " +%s", next.size() - 1)));
        }
        int linksTop = bottom - links.size() * 11;
        if (linksTop >= lineY) {
            for (int i = 0; i < links.size(); i++) this.detailLine(graphics, linkNames.get(i), links.get(i), x, linksTop + i * 11, width, ConfigStyle.colors().text());
            bottom = linksTop - 5;
        }

        // Its steps, where it takes several: the ones done ticked, then the ones left.
        if (progress == null || progress.getProgressText() == null) return;
        List<String> completed = new ArrayList<>();
        progress.getCompletedCriteria().forEach(completed::add);
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        if (lineY + 11 <= bottom) {
            lineY = this.detailLine(graphics, Component.translatableWithFallback("glazedmenu.advancements.steps", "Steps"), progress.getProgressText(), x, lineY, width,
                    done ? ConfigStyle.colors().success() : ConfigStyle.colors().text());
        }
        int steps = completed.size() + remaining.size();
        for (int i = 0; i < steps; i++) {
            boolean ticked = i < completed.size();
            // The last line that fits says how many more there are, where there are more.
            if (lineY + 10 > bottom - (i < steps - 1 ? 10 : 0) && i < steps - 1) {
                if (lineY + 10 <= bottom) {
                    ConfigStyle.text(graphics, this.font, Component.translatableWithFallback("glazedmenu.advancements.steps.more", "and %s more", steps - i),
                            x + 12, lineY, width - 12, ConfigStyle.colors().textMuted());
                }
                return;
            }
            if (lineY + 10 > bottom) return;
            String name = stepName(ticked ? completed.get(i) : remaining.get(i - completed.size()));
            if (ticked) ConfigIcons.CHECK.draw(graphics, x, lineY - 1, 9, ConfigStyle.colors().success());
            else ConfigStyle.outline(graphics, x + 1, lineY, 7, 7, ConfigStyle.colors().textMuted());
            ConfigStyle.text(graphics, this.font, Component.literal(name), x + 12, lineY, width - 12, ticked ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            lineY += 10;
        }
    }

    // An advancement: its item (ticked once it's done), its name over what it asks for, or over a bar where it takes
    // several steps and some are done; at the right its type, or how many of its steps are done.
    private final class AdvancementRow extends EntryRow {
        private final AdvancementNode node;
        private final int depth;

        AdvancementRow(AdvancementNode node, int depth) {
            this.node = node;
            this.depth = Math.min(depth, MAX_DEPTH);
        }

        @Override
        protected Object key() {
            return this.node.holder();
        }

        @Override
        protected int indent() {
            return this.depth * INDENT;
        }

        @Override
        protected void extractEntry(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean chosen, boolean hovered, int mouseX, int mouseY) {
            GlazedAdvancementsScreen screen = GlazedAdvancementsScreen.this;
            DisplayInfo display = screen.display(this.node);
            if (display == null) return;
            AdvancementProgress progress = screen.progress.get(this.node.holder());
            boolean done = screen.isDone(this.node);
            boolean started = screen.isStarted(this.node);
            int box = ROW_ICON;
            int boxY = y + (height - box) / 2;
            int boxColor = done ? ConfigStyle.colors().success() : typeColor(display.type());
            ConfigStyle.rect(graphics, x + 3, boxY, box, box, ConfigStyle.mix(ConfigStyle.colors().button(), boxColor, done ? 0.40F : 0.16F));
            ConfigStyle.outline(graphics, x + 3, boxY, box, box, ConfigStyle.withAlpha(boxColor, done ? 0xE0 : 0x70));
            graphics.fakeItem(screen.icon(this.node), x + 3 + (box - 16) / 2, boxY + (box - 16) / 2);

            int right = x + width - 6;
            Component steps = progress != null ? progress.getProgressText() : null;
            if (done) {
                right -= 10;
                ConfigIcons.CHECK.draw(graphics, right, y + (height - ConfigIcons.SIZE) / 2, ConfigStyle.colors().success());
            } else if (steps != null && started) {
                right -= screen.font.width(steps);
                graphics.text(screen.font, steps, right, y + (height - 8) / 2, ConfigStyle.colors().warning(), false);
            } else if (width >= 190) {
                Component type = typeName(display.type());
                right -= screen.font.width(type) + 8;
                ConfigStyle.badge(graphics, screen.font, type, right, y + (height - 11) / 2, typeColor(display.type()));
            }
            int textX = x + 3 + box + 6;
            int textWidth = right - 5 - textX;
            ConfigStyle.text(graphics, screen.font, display.title().copy().withStyle(ChatFormatting.BOLD), textX, y + 5, textWidth,
                    done || started ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
            if (!done && started && steps != null && progress != null) {
                int barWidth = Math.max(10, textWidth);
                graphics.fill(textX, y + 19, textX + barWidth, y + 22, ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0x60));
                graphics.fill(textX, y + 19, textX + Math.round(barWidth * progress.getPercent()), y + 22, ConfigStyle.accent());
            } else {
                ConfigStyle.text(graphics, screen.font, display.description(), textX, y + 16, textWidth, done ? ConfigStyle.colors().textDim() : ConfigStyle.colors().textMuted());
            }
        }
    }
}
