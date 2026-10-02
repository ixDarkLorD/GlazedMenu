package net.ixdarklord.glazedmenu.internal.gui;

import net.ixdarklord.glazedmenu.internal.compat.Compat;
import net.ixdarklord.glazedmenu.internal.compat.CompatList;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.source.Values;
import net.ixdarklord.glazedmenu.api.config.ConfigDependency;
import net.ixdarklord.glazedmenu.api.config.ConfigNode;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.ixdarklord.glazedmenu.api.editor.ConfigEditors;
import net.ixdarklord.glazedmenu.api.editor.ValueEditor;
import net.ixdarklord.glazedmenu.api.config.ConfigGroup;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// The rows of a config page: settings (name, description, editor, reset), section headers and notices. The list
// draws no background of its own; it sits on the screen's panel.
public final class ConfigEntryList extends CompatList<ConfigEntryList.Entry> {
    private static final int TOOLTIP_WIDTH = 260;
    private static final int STATUS_ICON = 11;

    public ConfigEntryList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, 26);
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

    public void setEntries(List<Entry> entries) {
        this.clearEntries();
        for (Entry entry : entries) this.addEntry(entry, entry.preferredHeight());
        this.setScrollAmount(0);
    }

    /** Shows the pending values again, after undo, a reset or the server's values arriving. */
    public void refreshValues() {
        this.children().forEach(Entry::refresh);
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
        ConfigStyle.rect(graphics, x, this.scrollBarY(), 3, this.scrollerHeight(), ConfigStyle.withAlpha(ConfigStyle.accent(), 0xB0));
    }

    public abstract static class Entry extends CompatList.Entry<Entry> {
        abstract int preferredHeight();

        void refresh() {}
    }

    /** One setting: its name and a line of description on the left, the editor and a reset button on the right. */
    public static final class ValueEntry<T> extends Entry {
        private final ConfigEditSession session;
        private final ConfigValue<T> value;
        private final Component label;
        private final @Nullable Component description;
        private final ValueEditor editor;
        private final FlatButton reset;

        public ValueEntry(ConfigEditSession session, ConfigValue<T> value, Component label, int editorWidth) {
            this.session = session;
            this.value = value;
            this.label = label;
            this.description = description(value);
            this.editor = ConfigEditors.create(session.slot(value), editorWidth, 20);
            this.reset = FlatButton.icon(ConfigIcons.RESET, Component.translatableWithFallback("glazedmenu.button.reset_value", "Reset to default"), button -> {
                session.set(value, value.getDefault());
                this.refresh();
            });
        }

        // The first line of the tooltip translation, or of the comment.
        private static @Nullable Component description(ConfigValue<?> value) {
            String tooltipKey = value.translationKey() + ".tooltip";
            if (Language.getInstance().has(tooltipKey)) {
                return Component.literal(Component.translatable(tooltipKey).getString().lines().findFirst().orElse(""));
            }
            return value.comment().isEmpty() ? null : Component.literal(value.comment().get(0));
        }

        @Override
        int preferredHeight() {
            return this.description != null ? 34 : 26;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = Minecraft.getInstance().font;
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            boolean editable = this.session.isEditable(this.value);
            boolean active = this.session.isActive(this.value);
            Optional<Component> error = this.session.error(this.value);
            boolean modified = this.session.isModified(this.value);

            if (hovered) ConfigStyle.rect(graphics, x, y + 1, width, height - 2, ConfigStyle.colors().rowHover());
            // A mark in the margin for changed or invalid values, like a diff.
            if (error.isPresent() || modified) {
                ConfigStyle.rect(graphics, x, y + 4, 2, height - 8, error.isPresent() ? ConfigStyle.colors().error() : ConfigStyle.colors().modified());
            }

            AbstractWidget widget = this.editor.widget();
            this.editor.setActive(editable && active);
            this.reset.active = editable && !this.session.isDefault(this.value);
            this.reset.setPosition(x + width - 22, y + (height - 20) / 2);
            widget.setPosition(this.reset.getX() - 4 - widget.getWidth(), y + (height - widget.getHeight()) / 2);
            Compat.extractRenderState(widget, graphics, mouseX, mouseY, a);
            Compat.extractRenderState(this.reset, graphics, mouseX, mouseY, a);

            int textX = x + 10;
            int labelWidth = widget.getX() - textX - 8;
            int nameY = this.description != null ? y + height / 2 - 10 : y + (height - 8) / 2;
            int nameColor = error.isPresent() ? ConfigStyle.colors().error() : !editable || !active ? ConfigStyle.colors().textMuted() : modified ? ConfigStyle.colors().modified() : ConfigStyle.colors().text();

            // Status icons after the name take their space first, so a long name is shortened rather than hiding them.
            boolean restart = this.value.restartRequirement() != RestartRequirement.NONE;
            boolean serverOnly = this.isServerOnly();
            int iconsWidth = ((restart ? 1 : 0) + (serverOnly ? 1 : 0)) * (STATUS_ICON + 3);
            FormattedCharSequence name = ConfigStyle.ellipsize(font, this.label, Math.max(10, labelWidth - iconsWidth));
            graphics.text(font, name, textX, nameY, nameColor, false);
            int iconX = textX + font.width(name) + 4;
            int iconY = nameY + 4 - STATUS_ICON / 2;
            if (restart) {
                ConfigIcons.RESTART.draw(graphics, iconX, iconY, STATUS_ICON, this.value.isRestartPending() ? ConfigStyle.colors().modified() : ConfigStyle.colors().warning());
                iconX += STATUS_ICON + 3;
            }
            if (serverOnly) ConfigIcons.LOCK.draw(graphics, iconX, iconY, STATUS_ICON, ConfigStyle.colors().textDim());
            if (this.description != null) {
                ConfigStyle.text(graphics, font, this.description, textX, nameY + 12, labelWidth, !editable || !active ? ConfigStyle.colors().textMuted() : ConfigStyle.colors().textDim());
            }

            if (hovered && mouseX >= x && mouseX < textX + labelWidth && mouseY >= y && mouseY < y + height) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (Component line : this.tooltip(error)) lines.addAll(font.split(line, TOOLTIP_WIDTH));
                graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }

        private boolean isServerOnly() {
            return this.value.config().scope().isSynced() && !this.value.isSynced();
        }

        private List<Component> tooltip(Optional<Component> error) {
            List<Component> lines = new ArrayList<>();
            lines.add(this.value.displayName().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
            String tooltipKey = this.value.translationKey() + ".tooltip";
            if (Language.getInstance().has(tooltipKey)) {
                lines.add(Component.translatable(tooltipKey));
            } else {
                this.value.comment().forEach(line -> lines.add(Component.literal(line)));
            }
            this.value.type().describe().forEach(line -> lines.add(line.copy().withStyle(ChatFormatting.GRAY)));
            lines.add(Component.translatableWithFallback("glazedmenu.info.default", "Default: %s", this.value.type().format(this.value.getDefault()))
                    .withStyle(ChatFormatting.GRAY));
            switch (this.value.restartRequirement()) {
                case GAME -> lines.add(Component.translatableWithFallback("glazedmenu.info.restart_game", "Requires restarting the game").withStyle(ChatFormatting.GOLD));
                case WORLD -> lines.add(Component.translatableWithFallback("glazedmenu.info.restart_world", "Requires rejoining the world").withStyle(ChatFormatting.GOLD));
                case NONE -> {
                }
            }
            if (this.value.isRestartPending()) {
                lines.add(Component.translatableWithFallback("glazedmenu.info.active", "In use until restart: %s", this.value.type().format(this.value.get()))
                        .withStyle(ChatFormatting.GOLD));
            }
            Component sync = Values.syncNote(this.value);
            if (sync != null) lines.add(sync.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            if (!this.session.isActive(this.value)) {
                ConfigDependency<?> dependency = this.value.dependency().orElseThrow();
                lines.add(Component.translatableWithFallback("glazedmenu.info.depends", "Depends on %s", dependency.source().displayName())
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            error.ifPresent(message -> lines.add(message.copy().withStyle(ChatFormatting.RED)));
            lines.add(Component.literal(this.value.config().id() + " › " + this.value.path()).withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }

        @Override
        void refresh() {
            this.editor.refresh();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.editor.widget(), this.reset);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.editor.widget(), this.reset);
        }
    }

    /** A nested group's heading: its name in the accent, then a rule to the edge; deeper groups are indented. */
    public static final class SectionEntry extends Entry {
        private final ConfigGroup group;
        private final int depth;

        public SectionEntry(ConfigGroup group, int depth) {
            this.group = group;
            this.depth = depth;
        }

        @Override
        int preferredHeight() {
            return 24;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = Minecraft.getInstance().font;
            int x = this.getX() + 6 + this.depth * 10;
            int y = this.getY() + this.getHeight() - 12;
            int accent = ConfigStyle.accent();
            Component name = this.group.displayName().copy().withStyle(ChatFormatting.BOLD);
            int textWidth = Math.min(font.width(name), this.getWidth() - (x - this.getX()) - 24);
            ConfigIcons.CHEVRON.draw(graphics, x, y - 1, accent);
            ConfigStyle.text(graphics, font, name, x + 12, y, textWidth, accent);
            int lineX = x + 12 + textWidth + 6;
            graphics.fill(lineX, y + 4, this.getX() + this.getWidth() - 4, y + 5, ConfigStyle.withAlpha(accent, 0x40));
            if (hovered && !this.group.comment().isEmpty() && mouseY >= y - 2) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (String line : this.group.comment()) lines.addAll(font.split(Component.literal(line), TOOLTIP_WIDTH));
                graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
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

    /** A notice card: where edits go, why nothing is shown... */
    public static final class InfoEntry extends Entry {
        private final Component text;
        private final int color;
        private final @Nullable ConfigIcons.Icon icon;

        public InfoEntry(Component text, int color) {
            this(text, color, null);
        }

        public InfoEntry(Component text, int color, @Nullable ConfigIcons.Icon icon) {
            this.text = text;
            this.color = color;
            this.icon = icon;
        }

        @Override
        int preferredHeight() {
            return 26;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = Minecraft.getInstance().font;
            int x = this.getX();
            int y = this.getY() + 2;
            int width = this.getWidth();
            int height = this.getHeight() - 4;
            ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.withAlpha(this.color, 0x1C));
            ConfigStyle.rect(graphics, x, y, 2, height, this.color);
            int textX = x + 10;
            if (this.icon != null) {
                this.icon.draw(graphics, textX, y + (height - ConfigIcons.SIZE) / 2, this.color);
                textX += ConfigIcons.SIZE + 6;
            }
            ConfigStyle.text(graphics, font, this.text, textX, y + (height - 8) / 2, x + width - textX - 6, this.color);
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

    /** Where a node sits below a group: {@code Rendering › Particles}. */
    static Component breadcrumb(ConfigGroup from, ConfigNode node) {
        List<Component> names = new ArrayList<>();
        for (ConfigNode parent = node.parent(); parent != null && parent != from; parent = parent.parent()) {
            names.add(0, parent.displayName());
        }
        MutableComponent text = Component.empty();
        for (Component name : names) text.append(name.copy().withStyle(ChatFormatting.GRAY)).append(Component.literal(" › ").withStyle(ChatFormatting.DARK_GRAY));
        return text.append(node.displayName());
    }

    static FormattedCharSequence ellipsize(Font font, Component text, int width) {
        return ConfigStyle.ellipsize(font, text, width);
    }
}
