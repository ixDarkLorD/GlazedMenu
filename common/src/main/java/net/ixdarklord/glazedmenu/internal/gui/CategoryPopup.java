package net.ixdarklord.glazedmenu.internal.gui;

import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigNode;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.ConfigGroup;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.ConfirmPopup;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One category of a config in a small window: that group's settings (its nested groups as sections), or a single
 * setting, with Save and Cancel. It floats over whatever screen opened it, or over the game when opened from a key.
 * It edits like the full config screen: pending changes, validation, dependencies, and the server's values for a
 * synced config.
 */
public final class CategoryPopup extends StyledPopup {
    private static final int WIDTH = 340;
    private static final int KEY_S = 83;

    private final ConfigEditSession session;
    private final ConfigNode node;
    private @Nullable ConfigEntryList list;
    private @Nullable FlatButton saveButton;
    private @Nullable FlatButton resetButton;

    private CategoryPopup(@Nullable Screen parent, ConfigEditSession session, ConfigNode node, @Nullable ConfigTheme theme) {
        super(parent, node.parent() == null ? session.config().title() : node.displayName(),
                ThemeResources.resolve(session.config().modId(), theme != null ? theme : session.config().theme()));
        this.session = session;
        this.node = node;
    }

    /**
     * The category at a path in a config: a group's dotted (or slashed) path, a single setting's path, or empty for the
     * whole config.
     *
     * @throws IllegalArgumentException when nothing is at the path
     */
    public static CategoryPopup create(@Nullable Screen parent, Config config, String path) {
        return create(parent, config, path, null);
    }

    /** As {@link #create(Screen, Config, String)}, drawn with {@code theme} instead of the config's own. */
    public static CategoryPopup create(@Nullable Screen parent, Config config, String path, @Nullable ConfigTheme theme) {
        String dotted = path.replace('/', '.');
        ConfigNode node = config.root();
        if (!dotted.isEmpty()) {
            for (String key : dotted.split("\\.")) {
                ConfigNode child = node instanceof ConfigGroup group ? group.child(key) : null;
                if (child == null) throw new IllegalArgumentException("Config " + config.id() + " has no category " + path);
                node = child;
            }
        }
        return new CategoryPopup(parent, new ConfigEditSession(config), node, theme);
    }

    /** A mod's config by name (the path's first part), then the category in it; null when either is missing. */
    public static @Nullable CategoryPopup create(@Nullable Screen parent, String modId, String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        int slash = trimmed.indexOf('/');
        String name = slash < 0 ? trimmed : trimmed.substring(0, slash);
        String rest = slash < 0 ? "" : trimmed.substring(slash + 1);
        for (Config config : ConfigSources.forMod(modId)) {
            if (!config.name().equals(name)) continue;
            try {
                return create(parent, config, rest);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    /** Every category path of a mod's configs, as {@link #create(Screen, String, String)} takes them. */
    public static List<String> paths(String modId) {
        List<String> paths = new ArrayList<>();
        for (Config config : ConfigSources.forMod(modId)) {
            paths.add(config.name());
            collectPaths(config.root(), config.name(), paths);
        }
        return paths;
    }

    private static void collectPaths(ConfigGroup group, String prefix, List<String> paths) {
        for (ConfigNode child : group.children()) {
            if (child instanceof ConfigGroup subgroup && !subgroup.isHidden()) {
                String path = prefix + "/" + subgroup.key();
                paths.add(path);
                collectPaths(subgroup, path, paths);
            }
        }
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.forScope(this.session.config().scope());
    }

    // Which config the category belongs to, when it isn't the whole config.
    @Override
    protected @Nullable Component titleNote() {
        return this.node.parent() == null ? null : this.session.config().title();
    }

    @Override
    protected void initPopup() {
        List<ConfigEntryList.Entry> entries = this.entries();
        int listHeight = entries.stream().mapToInt(ConfigEntryList.Entry::preferredHeight).sum() + 6;
        int maxList = this.height - 40 - (PADDING * 2 + TITLE_HEIGHT + FOOTER);
        this.setPanel(WIDTH, PADDING + TITLE_HEIGHT + Math.min(listHeight, Math.max(60, maxList)) + FOOTER + PADDING);

        int listTop = this.contentTop();
        int listBottom = this.footerTop();
        this.list = this.addRenderableWidget(new ConfigEntryList(this.minecraft, this.contentWidth() + 8, listBottom - listTop, listTop));
        this.list.updateSizeAndPosition(this.contentWidth() + 8, listBottom - listTop, this.contentLeft() - 4, listTop);
        this.list.setEntries(entries);

        int buttonY = this.footerButtonY();
        int right = this.contentLeft() + this.contentWidth();
        this.saveButton = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 76, button -> this.save())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        this.saveButton.setPosition(right - 76, buttonY);
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, 64, button -> this.onClose()));
        cancel.setPosition(right - 76 - 4 - 64, buttonY);
        this.resetButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.RESET,
                Component.translatableWithFallback("glazedmenu.button.reset.tooltip", "Sets the values shown here to their defaults"), button -> {
                    this.session.resetToDefaults(this.values());
                    this.refreshValues();
                }));
        this.resetButton.setPosition(this.contentLeft(), buttonY);
        this.updateButtons();
    }

    // The same rows as the full screen: settings, and nested groups as sections.
    private List<ConfigEntryList.Entry> entries() {
        List<ConfigEntryList.Entry> entries = new ArrayList<>();
        switch (this.session.access()) {
            case REMOTE -> entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.access.remote",
                    "Changes apply to the server"), ConfigStyle.colors().warning(), ConfigIcons.WARNING));
            case READ_ONLY -> entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.access.read_only",
                    "The server's values; you can't change them"), ConfigStyle.colors().textDim(), ConfigIcons.LOCK));
            case UNAVAILABLE -> {
                entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.access.unavailable",
                        "This config belongs to a world; open it while playing"), ConfigStyle.colors().textDim(), ConfigIcons.LOCK));
                return entries;
            }
            case LOCAL -> {
            }
        }
        if (this.session.config().scope() == ConfigScope.STARTUP) {
            entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.access.startup",
                    "Changes apply after restarting the game"), ConfigStyle.colors().warning(), ConfigIcons.RESTART));
        }
        int notices = entries.size();
        int editorWidth = Math.clamp((this.contentWidth() - 14) * 2 / 5, 90, 150);
        if (this.node instanceof ConfigValue<?> value) {
            entries.add(new ConfigEntryList.ValueEntry<>(this.session, value, value.displayName(), editorWidth));
        } else if (this.node instanceof ConfigGroup group) {
            this.addGroup(entries, group, 0, editorWidth);
        }
        if (entries.size() == notices) {
            entries.add(new ConfigEntryList.InfoEntry(Component.translatableWithFallback("glazedmenu.empty", "Nothing to configure here"),
                    ConfigStyle.colors().textDim(), ConfigIcons.SEARCH));
        }
        return entries;
    }

    private void addGroup(List<ConfigEntryList.Entry> entries, ConfigGroup group, int depth, int editorWidth) {
        for (ConfigNode child : group.children()) {
            if (child instanceof ConfigValue<?> value && this.session.isVisible(value)) {
                entries.add(new ConfigEntryList.ValueEntry<>(this.session, value, value.displayName(), editorWidth));
            } else if (child instanceof ConfigGroup subgroup && !subgroup.isHidden()
                    && subgroup.values().anyMatch(value -> this.session.isVisible(value))) {
                entries.add(new ConfigEntryList.SectionEntry(subgroup, depth));
                this.addGroup(entries, subgroup, depth + 1, editorWidth);
            }
        }
    }

    private List<ConfigValue<?>> values() {
        if (this.node instanceof ConfigValue<?> value) return List.of(value);
        return ((ConfigGroup) this.node).values()
                .filter(this.session::isVisible).toList();
    }

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // A hairline above the buttons, and what's unsaved or invalid beside them.
        int lineY = this.footerTop() + 1;
        graphics.fill(this.contentLeft(), lineY, this.contentLeft() + this.contentWidth(), lineY + 1, ConfigStyle.colors().panelBorder());
        int errors = this.session.errors().size();
        int modified = this.session.modifiedCount();
        Component status = errors > 0 ? Component.translatableWithFallback("glazedmenu.status.errors", "%s invalid", errors)
                : modified > 0 ? Component.translatableWithFallback("glazedmenu.status.modified", "%s unsaved", modified) : null;
        if (status != null) {
            int x = this.contentLeft() + 26;
            int width = this.contentLeft() + this.contentWidth() - 76 - 4 - 64 - 8 - x;
            ConfigStyle.text(graphics, this.font, status, x, this.contentBottom() - 14, width,
                    errors > 0 ? ConfigStyle.colors().error() : ConfigStyle.colors().modified());
        }
    }

    @Override
    public void tick() {
        this.updateButtons();
    }

    private void updateButtons() {
        if (this.saveButton == null || this.resetButton == null) return;
        Access access = this.session.access();
        boolean editable = access == Access.LOCAL || access == Access.REMOTE;
        this.resetButton.active = editable;
        this.saveButton.active = !this.session.hasErrors();
        this.saveButton.setMessage(this.session.modifiedCount() > 0
                ? Component.translatableWithFallback("glazedmenu.button.save_short", "Save") : CommonComponents.GUI_DONE);
        this.saveButton.setTooltip(this.session.hasErrors()
                ? Tooltip.create(Component.translatableWithFallback("glazedmenu.button.save.errors", "Fix the invalid values first:")
                .append("\n").append(this.session.errors().stream().map(Component::getString).collect(Collectors.joining("\n"))))
                : null);
    }

    private void refreshValues() {
        if (this.list != null) this.list.refreshValues();
        this.updateButtons();
    }

    private void save() {
        if (this.session.hasErrors()) return;
        this.minecraft.setScreen(this.parent);
        if (this.session.modifiedCount() > 0) this.session.saveAndNotify();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDownWithQuirk() && event.key() == KEY_S) {
            if (this.saveButton != null && this.saveButton.active) this.save();
            return true;
        }
        return super.keyPressed(event);
    }

    // Unsaved changes are confirmed before they're dropped, however the popup is closed.
    @Override
    public void onClose() {
        if (this.session.modifiedCount() == 0 && !this.session.hasErrors()) {
            this.minecraft.setScreen(this.parent);
            return;
        }
        this.minecraft.setScreen(new ConfirmPopup(this, this.theme,
                Component.translatableWithFallback("glazedmenu.discard.title", "Discard changes?"),
                Component.translatableWithFallback("glazedmenu.discard.message", "%s unsaved changes will be lost.", this.session.modifiedCount()),
                Component.translatableWithFallback("glazedmenu.discard.yes", "Discard"), true,
                discard -> this.minecraft.setScreen(discard ? this.parent : this)));
    }

    /** The server's values changed while the popup was open. */
    public void onConfigSynced(Config config) {
        if (config != this.session.config()) return;
        this.session.onConfigChanged();
        this.refreshValues();
    }

    // Coming back from a list or the color picker.
    @Override
    public void added() {
        super.added();
        this.refreshValues();
    }
}
