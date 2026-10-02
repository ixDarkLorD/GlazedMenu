package net.ixdarklord.glazedmenu.internal.gui;

import net.ixdarklord.glazedmenu.internal.compat.Compat;
import net.ixdarklord.glazedmenu.internal.compat.CompatList;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.api.editor.ConfigEditors;
import net.ixdarklord.glazedmenu.api.editor.EditSlot;
import net.ixdarklord.glazedmenu.api.editor.ValueEditor;
import net.ixdarklord.glazedmenu.api.config.type.BooleanType;
import net.ixdarklord.glazedmenu.api.config.type.ColorType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.EnumType;
import net.ixdarklord.glazedmenu.api.config.type.IdentifierType;
import net.ixdarklord.glazedmenu.api.config.type.ListType;
import net.ixdarklord.glazedmenu.api.config.type.NumberType;
import net.ixdarklord.glazedmenu.api.config.type.StringType;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Edits a list's elements, each with the editor of the element type, in the config screens' frame. "Done" hands the
 * whole list back to the slot it came from, as one pending change.
 */
public final class ListEditScreen<E> extends StyledScreen {
    private static final List<String> FALLBACK_INPUTS = List.of("", "0", "false", "{}", "[]", "\"\"");

    private final @Nullable Screen parent;
    private final EditSlot<List<E>> slot;
    private final ListType<E> type;
    private final List<Element> elements = new ArrayList<>();
    private @Nullable ElementList list;
    private @Nullable FlatButton addButton;
    private @Nullable FlatButton doneButton;
    private int statusRight;

    public ListEditScreen(@Nullable Screen parent, EditSlot<List<E>> slot) {
        super(slot.name(), parent instanceof StyledScreen styled ? styled.theme() : ConfigStyle.theme());
        this.parent = parent;
        this.slot = slot;
        this.type = (ListType<E>) slot.type();
        for (E value : slot.get()) this.elements.add(new Element(value));
    }

    @Override
    protected void init() {
        int bodyTop = this.bodyTop();
        int bodyHeight = this.bodyBottom() - bodyTop;
        this.list = this.addRenderableWidget(new ElementList(this.minecraft, this.frameWidth() - 8, bodyHeight - 8, bodyTop + 4));
        this.list.updateSizeAndPosition(this.frameWidth() - 8, bodyHeight - 8, this.frameLeft() + 4, bodyTop + 4);

        int x = this.frameLeft() + this.frameWidth() - 4;
        int y = this.barWidgetY(this.bottomBarY());
        this.doneButton = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 76, button -> this.done())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        this.doneButton.setPosition(x - 76, y);
        x -= 80;
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, 64, button -> this.onClose()));
        cancel.setPosition(x - 64, y);
        x -= 74;
        this.addButton = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("glazedmenu.list.add_short", "Add"), 60,
                button -> this.add()).withIcon(ConfigIcons.PLUS));
        this.addButton.setPosition(x - 60, y);
        this.statusRight = x - 68;
        this.addModeToggle(this.frameLeft() + this.frameWidth() - 24);
        this.rebuildRows();
    }

    @Override
    protected void repositionElements() {
        this.rebuildWidgets();
    }

    @Override
    public void added() {
        super.added();
        if (this.list != null) this.list.children().forEach(row -> row.editor.refresh());
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractPanels(graphics, mouseX, mouseY, a);
        ConfigStyle.panel(graphics, this.frameLeft(), this.bodyTop(), this.frameWidth(), this.bodyBottom() - this.bodyTop());
        int end = this.extractTitle(graphics, this.title, this.frameWidth() - 120);
        Component size = Component.translatableWithFallback("glazedmenu.list.count", "Items: %s", this.elements.size());
        ConfigStyle.badge(graphics, this.font, size, end + 8, this.topBarY() + (BAR_HEIGHT - 11) / 2, ConfigStyle.accent());

        Component problem = this.problem();
        int y = this.bottomBarY() + (BAR_HEIGHT - 8) / 2;
        int x = this.frameLeft() + 10;
        if (problem != null) {
            ConfigIcons.WARNING.draw(graphics, x, y - 1, ConfigStyle.colors().error());
            ConfigStyle.text(graphics, this.font, problem, x + 14, y, this.statusRight - x - 14, ConfigStyle.colors().error());
        } else {
            for (Component line : this.type.describe()) {
                ConfigStyle.text(graphics, this.font, line, x, y, this.statusRight - x, ConfigStyle.colors().textMuted());
                break;
            }
        }
    }

    private void rebuildRows() {
        if (this.list == null) return;
        double scroll = this.list.scrollAmount();
        List<Row> rows = new ArrayList<>();
        int editorWidth = this.list.getRowWidth() - 30 - 3 * 22 - 8;
        for (int i = 0; i < this.elements.size(); i++) rows.add(new Row(i, this.elements.get(i), editorWidth));
        this.list.replaceEntries(rows);
        this.list.setScrollAmount(scroll);
    }

    private void add() {
        E value = this.elements.isEmpty() ? this.defaultElement() : this.elements.get(this.elements.size() - 1).value;
        if (value == null) return;
        Element element = new Element(value);
        ValidationResult<E> result = this.type.elementType().validate(value);
        if (!result.isOk()) element.error = result.message().orElse(Component.empty());
        this.elements.add(element);
        this.rebuildRows();
        if (this.list != null) this.list.setScrollAmount(this.list.maxScrollAmount());
    }

    private void move(int index, int offset) {
        int target = index + offset;
        if (target < 0 || target >= this.elements.size()) return;
        this.elements.add(target, this.elements.remove(index));
        this.rebuildRows();
    }

    private void remove(int index) {
        this.elements.remove(index);
        this.rebuildRows();
    }

    private @Nullable Component problem() {
        for (int i = 0; i < this.elements.size(); i++) {
            Component error = this.elements.get(i).error;
            if (error != null) return Component.translatableWithFallback("glazedmenu.error.element", "Element %s: %s", i + 1, error);
        }
        ValidationResult<List<E>> result = this.type.validate(this.values());
        return result.isOk() ? null : result.message().orElse(Component.empty());
    }

    private List<E> values() {
        return this.elements.stream().map(element -> element.value).toList();
    }

    private void done() {
        if (this.problem() != null) return;
        this.slot.set(this.values());
        this.leave(this.parent);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.doneButton == null || this.addButton == null) return;
        this.doneButton.active = this.problem() == null;
        this.addButton.active = this.elements.size() < this.type.maxSize() && (!this.elements.isEmpty() || this.defaultElement() != null);
    }

    @Override
    protected @Nullable Screen parentScreen() {
        return this.parent;
    }

    @Override
    public void onClose() {
        this.leave(this.parent);
    }

    // A starting value for the first element of an empty list.
    @SuppressWarnings("unchecked")
    private @Nullable E defaultElement() {
        ConfigType<E> elementType = this.type.elementType();
        Object value;
        if (elementType instanceof BooleanType) value = false;
        else if (elementType instanceof NumberType<?> number) value = number.fromDouble(0);
        else if (elementType instanceof StringType) value = "";
        else if (elementType instanceof EnumType<?> enumType) value = enumType.constants().get(0);
        else if (elementType instanceof ColorType) value = 0xFFFFFFFF;
        else if (elementType instanceof IdentifierType) value = new ResourceLocation("stone");
        else if (elementType instanceof ListType<?>) value = List.of();
        else value = null;
        if (value != null) return (E) value;
        for (String input : FALLBACK_INPUTS) {
            ValidationResult<E> result = elementType.parse(input);
            if (result.hasValue()) return result.value();
        }
        return null;
    }

    private final class Element {
        private E value;
        private @Nullable Component error;

        Element(E value) {
            this.value = value;
        }

        EditSlot<E> slot() {
            return new EditSlot<>() {
                @Override
                public ConfigType<E> type() {
                    return ListEditScreen.this.type.elementType();
                }

                @Override
                public E get() {
                    return Element.this.value;
                }

                @Override
                public void set(E value) {
                    ValidationResult<E> result = this.type().validate(value);
                    if (result.isOk()) {
                        Element.this.value = value;
                        Element.this.error = null;
                    } else {
                        Element.this.error = result.message().orElse(Component.empty());
                    }
                }

                @Override
                public void setInvalid(Component error) {
                    Element.this.error = error;
                }

                @Override
                public Optional<Component> error() {
                    return Optional.ofNullable(Element.this.error);
                }

                @Override
                public Component name() {
                    return ListEditScreen.this.slot.name();
                }
            };
        }
    }

    private final class ElementList extends CompatList<Row> {
        ElementList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 26);
        }

        @Override
        public int getRowWidth() {
            return Math.min(this.width - 14, 460);
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

    // An element: its number, its editor, and move/remove buttons.
    private final class Row extends CompatList.Entry<Row> {
        private final int index;
        private final Element element;
        private final ValueEditor editor;
        private final FlatButton up;
        private final FlatButton down;
        private final FlatButton remove;

        Row(int index, Element element, int editorWidth) {
            this.index = index;
            this.element = element;
            this.editor = ConfigEditors.create(element.slot(), Math.max(60, editorWidth), 20);
            this.up = FlatButton.icon(ConfigIcons.UP, Component.translatableWithFallback("glazedmenu.list.up", "Move up"), button -> ListEditScreen.this.move(index, -1));
            this.down = FlatButton.icon(ConfigIcons.DOWN, Component.translatableWithFallback("glazedmenu.list.down", "Move down"), button -> ListEditScreen.this.move(index, 1));
            this.remove = FlatButton.icon(ConfigIcons.CLOSE, Component.translatableWithFallback("glazedmenu.list.remove", "Remove"), button -> ListEditScreen.this.remove(index))
                    .iconColor(ConfigStyle.colors().error());
            this.up.active = index > 0;
            this.down.active = index < ListEditScreen.this.elements.size() - 1;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ListEditScreen.this.font;
            int x = this.getX();
            int y = this.getY();
            int right = x + this.getWidth();
            int middle = y + (this.getHeight() - 20) / 2;
            if (hovered) ConfigStyle.rect(graphics, x, y + 1, this.getWidth(), this.getHeight() - 2, ConfigStyle.colors().rowHover());
            boolean invalid = this.element.error != null;
            if (invalid) ConfigStyle.rect(graphics, x, y + 4, 2, this.getHeight() - 8, ConfigStyle.colors().error());
            Component number = Component.literal(String.format(Locale.ROOT, "%02d", this.index + 1)).withStyle(ChatFormatting.BOLD);
            graphics.text(font, number, x + 8, y + (this.getHeight() - 8) / 2, invalid ? ConfigStyle.colors().error() : ConfigStyle.colors().textMuted(), false);

            this.remove.setPosition(right - 22, middle);
            this.down.setPosition(right - 44, middle);
            this.up.setPosition(right - 66, middle);
            this.editor.widget().setPosition(right - 72 - this.editor.widget().getWidth(), middle);
            Compat.extractRenderState(this.editor.widget(), graphics, mouseX, mouseY, a);
            Compat.extractRenderState(this.up, graphics, mouseX, mouseY, a);
            Compat.extractRenderState(this.down, graphics, mouseX, mouseY, a);
            Compat.extractRenderState(this.remove, graphics, mouseX, mouseY, a);
            if (invalid && this.editor.widget().isMouseOver(mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(font, font.split(this.element.error.copy().withStyle(ChatFormatting.RED), 240), mouseX, mouseY);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.editor.widget(), this.up, this.down, this.remove);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.editor.widget(), this.up, this.down, this.remove);
        }
    }
}
