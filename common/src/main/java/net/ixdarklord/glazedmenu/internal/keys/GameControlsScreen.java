package net.ixdarklord.glazedmenu.internal.keys;

import net.ixdarklord.glazedmenu.internal.compat.KeyCategories;
import net.ixdarklord.glazedmenu.internal.compat.Compat;
import net.ixdarklord.glazedmenu.internal.compat.CompatList;
import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigGroup;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.gui.ConfigEditSession;
import net.ixdarklord.glazedmenu.internal.gui.ConfigEntryList;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.ixdarklord.glazedmenu.internal.compat.KeyEvent;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The controls, in place of the game's own controls screen: a map of the keyboard (or of the mouse's buttons), each key
 * tinted where something is bound to it and red where key binds are in conflict over it; keys the map has no place for
 * (a numpad's, a mod's) follow in a row of their own. Picking a key lists what's bound to it, each with Rebind (then
 * press the new key, or click it on the map; Escape unbinds) and a reset; beside that list, the game's control settings
 * (or the mouse's), changed right there and saved as the screen closes. List opens every key bind as a list, with its
 * search, sorting and filters.
 */
public final class GameControlsScreen extends StyledScreen {
    // The keyboard's rows: each key's code (GLFW's) and its width in keys, fifteen keys' width to a row.
    private static final float[][][] KEYBOARD = {
            {{256, 1.154F}, {290, 1.154F}, {291, 1.154F}, {292, 1.154F}, {293, 1.154F}, {294, 1.154F}, {295, 1.154F}, {296, 1.154F}, {297, 1.154F}, {298, 1.154F}, {299, 1.154F}, {300, 1.154F}, {301, 1.152F}},
            {{96, 1}, {49, 1}, {50, 1}, {51, 1}, {52, 1}, {53, 1}, {54, 1}, {55, 1}, {56, 1}, {57, 1}, {48, 1}, {45, 1}, {61, 1}, {259, 2}},
            {{258, 1.5F}, {81, 1}, {87, 1}, {69, 1}, {82, 1}, {84, 1}, {89, 1}, {85, 1}, {73, 1}, {79, 1}, {80, 1}, {91, 1}, {93, 1}, {92, 1.5F}},
            {{280, 1.75F}, {65, 1}, {83, 1}, {68, 1}, {70, 1}, {71, 1}, {72, 1}, {74, 1}, {75, 1}, {76, 1}, {59, 1}, {39, 1}, {257, 2.25F}},
            {{340, 2.25F}, {90, 1}, {88, 1}, {67, 1}, {86, 1}, {66, 1}, {78, 1}, {77, 1}, {44, 1}, {46, 1}, {47, 1}, {344, 2.75F}},
            {{341, 1.5F}, {342, 1.5F}, {32, 6}, {346, 1.5F}, {345, 1.5F}, {263, 0.75F}, {265, 0.75F}, {264, 0.75F}, {262, 0.75F}}};
    // Short names for the keys whose own are too long for a key cap.
    private static final Map<Integer, String> SHORT = Map.ofEntries(Map.entry(256, "Esc"), Map.entry(259, "Bksp"), Map.entry(258, "Tab"),
            Map.entry(280, "Caps"), Map.entry(257, "Enter"), Map.entry(340, "Shift"), Map.entry(344, "Shift"), Map.entry(341, "Ctrl"), Map.entry(345, "Ctrl"),
            Map.entry(342, "Alt"), Map.entry(346, "Alt"), Map.entry(32, "Space"), Map.entry(263, "←"), Map.entry(265, "↑"), Map.entry(264, "↓"),
            Map.entry(262, "→"));
    private static final int KEY_GAP = 2;

    private final @Nullable Screen parent;
    private final Options options;
    private final List<KeyMapping> mappings;
    // The game's options as Glazed Menu lists them, for the settings beside the key binds; null when it doesn't.
    private final @Nullable ConfigEditSession session;
    private final Map<InputConstants.Key, List<KeyMapping>> byKey = new HashMap<>();
    private final Map<KeyMapping, List<KeyMapping>> conflicts = new HashMap<>();
    private final List<Cap> caps = new ArrayList<>();
    private boolean mouseView;
    private InputConstants.Key picked = InputConstants.UNKNOWN;
    private @Nullable KeyMapping capturing;
    private InputConstants.Key heldModifier = InputConstants.UNKNOWN;
    private boolean dirty;
    private int mapTop;
    private int mapHeight;
    private @Nullable Rows binds;
    private @Nullable ConfigEntryList settings;

    public GameControlsScreen(@Nullable Screen parent, Options options) {
        super(Component.translatable("controls.title"), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.parent = parent;
        this.options = options;
        KeyMapping[] sorted = options.keyMappings.clone();
        Arrays.sort(sorted);
        this.mappings = List.of(sorted);
        Config config = ConfigSources.forMod("minecraft").stream().filter(found -> found.name().equals("options")).findFirst().orElse(null);
        this.session = config != null ? new ConfigEditSession(config) : null;
    }

    // A key of the map: where it's drawn, and what it says.
    private record Cap(InputConstants.Key key, int x, int y, int width, int height, String label) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }
    }

    private static InputConstants.Key keyOf(KeyMapping mapping) {
        try {
            return InputConstants.getKey(mapping.saveString());
        } catch (RuntimeException e) {
            return InputConstants.UNKNOWN;
        }
    }

    private static Component name(KeyMapping mapping) {
        return Component.translatable(mapping.getName());
    }

    // --- Layout ---

    @Override
    protected void init() {
        // The views, at the right of the top bar: the keyboard's map, the mouse's, and every key bind as a list.
        int x = this.frameLeft() + this.frameWidth() - 6 - 20 - TOGGLE_ROOM;
        this.addModeToggleAtEnd(6);
        x = this.view(Component.translatableWithFallback("glazedmenu.controls.view.list", "List"), x, false,
                () -> this.leave(new GlazedKeysScreen(this, this.options)));
        x = this.view(Component.translatableWithFallback("glazedmenu.controls.view.mouse", "Mouse"), x, this.mouseView, () -> this.setView(true));
        this.view(Component.translatableWithFallback("glazedmenu.controls.view.keyboard", "Keyboard"), x, !this.mouseView, () -> this.setView(false));

        this.readKeys();
        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        this.mapTop = bodyTop;
        this.layoutCaps(bodyHeight);
        int lowerTop = bodyTop + this.mapHeight + GAP;
        int lowerHeight = Math.max(30, this.bodyBottom() - lowerTop);
        int half = (this.frameWidth() - GAP) / 2;
        this.binds = this.addRenderableWidget(new Rows(this.minecraft, half - 8, lowerHeight - 20, lowerTop + 16));
        this.binds.updateSizeAndPosition(half - 8, lowerHeight - 20, this.frameLeft() + 4, lowerTop + 16);
        this.settings = this.addRenderableWidget(new ConfigEntryList(this.minecraft, half - 8, lowerHeight - 20, lowerTop + 16));
        this.settings.updateSizeAndPosition(half - 8, lowerHeight - 20, this.frameLeft() + half + GAP + 4, lowerTop + 16);

        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 84, button -> this.onClose())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        done.setPosition(this.frameLeft() + this.frameWidth() - 4 - 84, this.barWidgetY(this.bottomBarY()));
        this.fillBinds();
        this.fillSettings();
    }

    private int view(Component name, int right, boolean chosen, Runnable press) {
        int width = this.font.width(name) + 14;
        FlatButton button = this.addRenderableWidget(FlatButton.of(name, width, pressed -> press.run()).style(chosen ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL));
        button.setHeight(18);
        button.setPosition(right - width, this.topBarY() + 5);
        return right - width - 4;
    }

    private void setView(boolean mouse) {
        if (this.mouseView == mouse) return;
        this.mouseView = mouse;
        this.picked = InputConstants.UNKNOWN;
        this.capturing = null;
        this.rebuildWidgets();
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    // Which key binds are on which key, and which are in conflict (as the game counts them: two on one key, once either
    // was changed from its default).
    private void readKeys() {
        this.byKey.clear();
        this.conflicts.clear();
        for (KeyMapping mapping : this.mappings) {
            if (mapping.isUnbound()) continue;
            this.byKey.computeIfAbsent(keyOf(mapping), key -> new ArrayList<>()).add(mapping);
            List<KeyMapping> same = this.mappings.stream()
                    .filter(other -> other != mapping && mapping.same(other) && (!mapping.isDefault() || !other.isDefault())).toList();
            if (!same.isEmpty()) this.conflicts.put(mapping, same);
        }
    }

    // Places the map's keys: the keyboard's rows (or the mouse's buttons), then a row of the bound keys they don't hold.
    private void layoutCaps(int bodyHeight) {
        this.caps.clear();
        Map<InputConstants.Key, String> extra = new LinkedHashMap<>();
        InputConstants.Type type = this.mouseView ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM;
        List<float[][]> rows = new ArrayList<>();
        if (this.mouseView) rows.add(new float[][]{{0, 3}, {2, 3}, {1, 3}, {3, 3}, {4, 3}});
        else rows.addAll(Arrays.asList(KEYBOARD));
        java.util.Set<InputConstants.Key> placed = new java.util.HashSet<>();
        for (float[][] row : rows) for (float[] key : row) placed.add(type.getOrCreate((int) key[0]));
        for (InputConstants.Key key : this.byKey.keySet()) {
            if (key.getType() == type && !placed.contains(key)) extra.put(key, key.getDisplayName().getString());
        }
        int rowCount = rows.size() + (extra.isEmpty() ? 0 : 1);
        // The keys grow with the window: as wide as fifteen of them fit across the frame (up to a comfortable size), and
        // as tall as the map's share of the height allows, never taller than a key is wide.
        float unit = Math.min((this.frameWidth() - 16) / 15F, 42);
        int keyHeight = this.mouseView ? 22 : Math.clamp((int) ((bodyHeight * 0.56F - 12) / 7), 11, Math.max(20, Math.round(unit * 0.8F)));
        int left = this.frameLeft() + (this.frameWidth() - Math.round(unit * 15)) / 2;
        int y = this.mapTop + 6;
        for (float[][] row : rows) {
            float x = left;
            for (float[] key : row) {
                InputConstants.Key input = type.getOrCreate((int) key[0]);
                int start = Math.round(x);
                int end = Math.round(x + key[1] * unit);
                String label = !this.mouseView && SHORT.containsKey((int) key[0]) ? SHORT.get((int) key[0]) : input.getDisplayName().getString();
                this.caps.add(new Cap(input, start, y, end - start - KEY_GAP, keyHeight, label));
                x += key[1] * unit;
            }
            y += keyHeight + KEY_GAP;
        }
        if (!extra.isEmpty()) {
            int x = left;
            for (Map.Entry<InputConstants.Key, String> key : extra.entrySet()) {
                int width = Math.max(Math.round(unit), this.font.width(key.getValue()) + 8);
                if (x + width > left + Math.round(unit * 15)) break;
                this.caps.add(new Cap(key.getKey(), x, y, width - KEY_GAP, keyHeight, key.getValue()));
                x += width;
            }
        }
        this.mapHeight = rowCount * (keyHeight + KEY_GAP) + 10;
    }

    // A key's name on its cap, whole: at the font's own size where it fits, and drawn smaller where the cap is too
    // narrow or too low for it (cut short only where even half size doesn't fit).
    private void extractCapLabel(GuiGraphicsExtractor graphics, Cap cap, int color) {
        int textWidth = this.font.width(cap.label);
        if (textWidth <= 0) return;
        float scale = Math.min(1, Math.min((cap.width - 3) / (float) textWidth, (cap.height - 2) / 8F));
        if (scale >= 1) {
            graphics.text(this.font, cap.label, cap.x + (cap.width - textWidth) / 2, cap.y + (cap.height - 8) / 2, color, false);
            return;
        }
        scale = Math.max(0.5F, scale);
        graphics.pose().pushMatrix();
        graphics.pose().translate(cap.x + cap.width / 2F, cap.y + cap.height / 2F);
        graphics.pose().scale(scale, scale);
        int room = Math.round((cap.width - 3) / scale);
        if (textWidth <= room) graphics.text(this.font, cap.label, -textWidth / 2, -4, color, false);
        else ConfigStyle.centeredText(graphics, this.font, Component.literal(cap.label), 0, -4, room, color);
        graphics.pose().popMatrix();
    }

    private @Nullable Cap capAt(double mouseX, double mouseY) {
        for (Cap cap : this.caps) if (cap.contains(mouseX, mouseY)) return cap;
        return null;
    }

    // What's bound to the picked key (or, with none picked, what's in conflict).
    private void fillBinds() {
        if (this.binds == null) return;
        List<Row> rows = new ArrayList<>();
        List<KeyMapping> shown = this.picked != InputConstants.UNKNOWN ? this.byKey.getOrDefault(this.picked, List.of()) : List.copyOf(this.conflicts.keySet());
        for (KeyMapping mapping : shown) rows.add(new BindRow(mapping));
        if (rows.isEmpty()) {
            rows.add(new Notice(this.picked != InputConstants.UNKNOWN
                    ? Component.translatableWithFallback("glazedmenu.controls.none.key", "Nothing is bound to this key")
                    : Component.translatableWithFallback("glazedmenu.controls.none", "Pick a key to see what's bound to it")));
        }
        this.binds.setRows(rows);
    }

    // The game's control settings (the mouse's, on its view), where Glazed Menu lists the game's options.
    private void fillSettings() {
        if (this.settings == null) return;
        List<ConfigEntryList.Entry> entries = new ArrayList<>();
        if (this.session != null && this.session.config().root().child(this.mouseView ? "mouse" : "controls") instanceof ConfigGroup group) {
            int editorWidth = Math.clamp(this.settings.getRowWidth() * 2 / 5, 70, 130);
            for (ConfigValue<?> value : group.values().filter(this.session::isVisible).toList()) {
                entries.add(new ConfigEntryList.ValueEntry<>(this.session, value, value.displayName(), editorWidth));
            }
        }
        if (entries.isEmpty()) {
            entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.controls.settings.none",
                    "List the game's options (Glazed Menu's settings) to change these here"), ConfigStyle.colors().textDim(), ConfigIcons.LOCK));
        }
        this.settings.setEntries(entries);
    }

    // --- Changing keys ---

    @Override
    public void tick() {
        super.tick();
        if (this.dirty) {
            this.dirty = false;
            double scroll = this.binds != null ? this.binds.scrollAmount() : 0;
            this.rebuildWidgets();
            if (this.binds != null) this.binds.setScrollAmount(scroll);
        }
    }

    private void keysChanged() {
        KeyMapping.resetMapping();
        this.dirty = true;
    }

    // The waiting key bind takes the key, and the map shows that key's binds.
    private void bind(InputConstants.Key modifier, InputConstants.Key key) {
        if (this.capturing == null) return;
        GlazedPlatform.get().bindKey(this.capturing, modifier, key);
        this.capturing = null;
        this.heldModifier = InputConstants.UNKNOWN;
        if (key != InputConstants.UNKNOWN && key.getType() == (this.mouseView ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM)) this.picked = key;
        this.keysChanged();
    }

    // A click on a key of the map picks it (or, while a key bind waits, gives it that key); any other click while one
    // waits gives it the mouse's button.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Cap cap = this.capAt(event.x(), event.y());
        if (this.capturing != null) {
            this.bind(this.heldModifier, cap != null ? cap.key : InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        if (cap != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.picked = cap.key.equals(this.picked) ? InputConstants.UNKNOWN : cap.key;
            this.fillBinds();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.capturing != null) {
            InputConstants.Key key = Compat.getKey(event);
            if (event.isEscape()) {
                this.bind(InputConstants.UNKNOWN, InputConstants.UNKNOWN);
            } else if (GlazedPlatform.get().isKeyModifier(key) && this.heldModifier == InputConstants.UNKNOWN) {
                // Held for the key that follows; on its own once it's released.
                this.heldModifier = key;
            } else {
                this.bind(this.heldModifier, key);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (this.capturing != null && this.heldModifier != InputConstants.UNKNOWN) {
            this.bind(InputConstants.UNKNOWN, this.heldModifier);
            return true;
        }
        return super.keyReleased(event);
    }

    // --- Drawing ---

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        ConfigStyle.panel(graphics, this.frameLeft(), this.mapTop, this.frameWidth(), this.mapHeight);
        GlazedBrand.fill(graphics, this.frameLeft() + 1, this.mapTop, this.frameWidth() - 2, 2, 0xD0);
        this.extractTitle(graphics, this.title, this.frameWidth() / 3, (g, x, y, size) -> {
            ConfigIcons.KEYBOARD.draw(g, x, y, size, ConfigStyle.accent());
            return true;
        });

        Cap hovered = this.capAt(mouseX, mouseY);
        int accent = ConfigStyle.accent();
        for (Cap cap : this.caps) {
            List<KeyMapping> bound = this.byKey.getOrDefault(cap.key, List.of());
            boolean conflict = bound.stream().anyMatch(this.conflicts::containsKey);
            boolean chosen = cap.key.equals(this.picked);
            int tint = conflict ? ConfigStyle.colors().error() : accent;
            int fill = chosen ? tint : !bound.isEmpty() ? ConfigStyle.mix(ConfigStyle.colors().button(), tint, conflict ? 0.45F : 0.32F) : ConfigStyle.colors().button();
            ConfigStyle.rect(graphics, cap.x, cap.y, cap.width, cap.height, fill);
            ConfigStyle.outline(graphics, cap.x, cap.y, cap.width, cap.height, cap == hovered ? ConfigStyle.withAlpha(tint, 0xE0)
                    : !bound.isEmpty() ? ConfigStyle.withAlpha(tint, 0x90) : ConfigStyle.colors().panelBorder());
            int color = chosen ? ConfigStyle.readableOn(tint) : bound.isEmpty() ? ConfigStyle.colors().textMuted() : ConfigStyle.colors().text();
            this.extractCapLabel(graphics, cap, color);
        }
        // What's on the key under the mouse.
        if (hovered != null && this.capturing == null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hovered.key.getDisplayName().copy().withStyle(ChatFormatting.BOLD));
            List<KeyMapping> bound = this.byKey.getOrDefault(hovered.key, List.of());
            if (bound.isEmpty()) tip.add(Component.translatableWithFallback("glazedmenu.controls.none.key", "Nothing is bound to this key").withStyle(ChatFormatting.GRAY));
            for (KeyMapping mapping : bound) tip.add(name(mapping).copy().withStyle(this.conflicts.containsKey(mapping) ? ChatFormatting.RED : ChatFormatting.GRAY));
            graphics.setTooltipForNextFrame(this.font, tip.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
        }

        // The two panes under the map: the picked key's binds, and the settings.
        int lowerTop = this.mapTop + this.mapHeight + GAP;
        int lowerHeight = Math.max(30, this.bodyBottom() - lowerTop);
        int half = (this.frameWidth() - GAP) / 2;
        int rightX = this.frameLeft() + half + GAP;
        ConfigStyle.panel(graphics, this.frameLeft(), lowerTop, half, lowerHeight, false);
        ConfigStyle.panel(graphics, rightX, lowerTop, this.frameLeft() + this.frameWidth() - rightX, lowerHeight, false);
        int count = this.picked != InputConstants.UNKNOWN ? this.byKey.getOrDefault(this.picked, List.of()).size() : this.conflicts.size();
        Component bindsTitle = this.picked != InputConstants.UNKNOWN
                ? Component.translatableWithFallback("glazedmenu.controls.on_key", "On %s · %s", this.picked.getDisplayName(), count)
                : Component.translatableWithFallback("glazedmenu.controls.conflicts", "In conflict · %s", count);
        boolean warn = this.picked != InputConstants.UNKNOWN ? this.byKey.getOrDefault(this.picked, List.of()).stream().anyMatch(this.conflicts::containsKey) : count > 0;
        ConfigStyle.text(graphics, this.font, bindsTitle.copy().withStyle(ChatFormatting.BOLD), this.frameLeft() + 10, lowerTop + 6, half - 20,
                warn ? ConfigStyle.colors().error() : accent);
        ConfigStyle.text(graphics, this.font, (this.mouseView ? Component.translatable("options.mouse_settings.title") : Component.translatableWithFallback(
                "glazedmenu.controls.settings", "Control Settings")).copy().withStyle(ChatFormatting.BOLD), rightX + 10, lowerTop + 6, half - 20, ConfigStyle.colors().textDim());

        Component status = this.capturing != null
                ? Component.translatableWithFallback("glazedmenu.controls.status.capturing", "Press a key for %s, or click one on the map · Escape unbinds it", name(this.capturing))
                : this.conflicts.isEmpty() ? Component.translatableWithFallback("glazedmenu.keys.status.count", "%s key binds", this.mappings.size())
                : Component.translatableWithFallback("glazedmenu.keys.status.conflicts", "%s key binds share a key", this.conflicts.size());
        ConfigStyle.text(graphics, this.font, status, this.frameLeft() + 10, this.bottomBarY() + (BAR_HEIGHT - 8) / 2, this.frameWidth() - 110,
                this.capturing != null ? accent : this.conflicts.isEmpty() ? ConfigStyle.colors().textMuted() : ConfigStyle.colors().error());
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    public void onClose() {
        this.leave(this.parent);
    }

    // The keys and the settings changed here are kept as the screen closes, as the game's are.
    @Override
    public void removed() {
        if (this.session != null && this.session.modifiedCount() > 0 && !this.session.hasErrors()) this.session.save();
        this.options.save();
        super.removed();
    }

    // Back from the list of key binds: the map shows what's bound now.
    @Override
    public void added() {
        super.added();
        this.dirty = true;
    }

    // --- The picked key's binds ---

    private static final class Rows extends CompatList<Row> {
        Rows(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 24);
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
            for (Row row : rows) this.addEntry(row, 24);
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

    private abstract static class Row extends CompatList.Entry<Row> {
        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    // A key bind on the picked key: its name (red in conflict), Rebind and a reset.
    private final class BindRow extends Row {
        private final KeyMapping mapping;
        private final FlatButton rebind;
        private final FlatButton reset;

        BindRow(KeyMapping mapping) {
            this.mapping = mapping;
            Component label = Component.translatableWithFallback("glazedmenu.controls.rebind", "Rebind");
            this.rebind = FlatButton.of(label, GameControlsScreen.this.font.width(label) + 12, button -> {
                GameControlsScreen.this.capturing = mapping;
                GameControlsScreen.this.heldModifier = InputConstants.UNKNOWN;
            });
            this.reset = FlatButton.icon(ConfigIcons.RESET, Component.translatable("controls.reset"), button -> {
                GlazedPlatform.get().resetKey(mapping);
                GameControlsScreen.this.keysChanged();
            });
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GameControlsScreen screen = GameControlsScreen.this;
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            boolean waiting = screen.capturing == this.mapping;
            boolean conflict = screen.conflicts.containsKey(this.mapping);
            if (hovered || waiting) ConfigStyle.rect(graphics, x, y + 1, width, height - 2, ConfigStyle.colors().rowHover());
            if (conflict || !this.mapping.isDefault()) {
                ConfigStyle.rect(graphics, x, y + 4, 2, height - 8, conflict ? ConfigStyle.colors().error() : ConfigStyle.colors().modified());
            }
            this.reset.active = !this.mapping.isDefault();
            this.reset.setPosition(x + width - 22, y + (height - 20) / 2);
            this.rebind.style(waiting ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL);
            this.rebind.setPosition(this.reset.getX() - 4 - this.rebind.getWidth(), y + (height - 20) / 2);
            Compat.extractRenderState(this.rebind, graphics, mouseX, mouseY, a);
            Compat.extractRenderState(this.reset, graphics, mouseX, mouseY, a);
            Component name = name(this.mapping).copy().append(Component.literal("  " + KeyCategories.label(this.mapping.getCategory()).getString())
                    .withStyle(style -> style.withColor(ConfigStyle.colors().textMuted() & 0xFFFFFF)));
            ConfigStyle.text(graphics, screen.font, name, x + 8, y + (height - 8) / 2, this.rebind.getX() - x - 14,
                    conflict ? ConfigStyle.colors().error() : ConfigStyle.colors().text());
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.rebind, this.reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.rebind, this.reset);
        }
    }

    private final class Notice extends Row {
        private final Component text;

        Notice(Component text) {
            this.text = text;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            ConfigStyle.centeredText(graphics, GameControlsScreen.this.font, this.text, this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2, this.getWidth() - 8, ConfigStyle.colors().textMuted());
        }
    }
}
