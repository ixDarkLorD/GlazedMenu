package net.ixdarklord.glazedmenu.internal.gui.editor;

import net.ixdarklord.glazedmenu.api.editor.EditSlot;
import net.ixdarklord.glazedmenu.api.editor.ValueEditor;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.ListType;
import net.ixdarklord.glazedmenu.internal.gui.ListEditScreen;
import net.minecraft.client.Minecraft;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

// A button opening the list's own screen, labelled with its size and a preview of its elements.
public final class ListEditor<E> implements ValueEditor {
    private static final int PREVIEW_ELEMENTS = 8;

    private final EditSlot<List<E>> slot;
    private final FlatButton button;

    public ListEditor(EditSlot<List<E>> slot, int width, int height) {
        this.slot = slot;
        this.button = new FlatButton(width, height, CommonComponents.EMPTY, pressed -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gui.setScreen(new ListEditScreen<>(minecraft.gui.screen(), slot));
        });
        this.refresh();
    }

    @Override
    public FlatButton widget() {
        return this.button;
    }

    @Override
    public void refresh() {
        List<E> list = this.slot.get();
        this.button.setMessage(Component.translatableWithFallback("glazedmenu.list.edit", "Edit… (%s)", list.size()));
        if (list.isEmpty()) {
            this.button.setTooltip(Tooltip.create(Component.translatableWithFallback("glazedmenu.list.empty", "Empty")));
            return;
        }
        StringBuilder preview = new StringBuilder();
        ConfigType<E> type = ((ListType<E>) this.slot.type()).elementType();
        for (int i = 0; i < Math.min(list.size(), PREVIEW_ELEMENTS); i++) {
            if (i > 0) preview.append('\n');
            preview.append("• ").append(type.format(list.get(i)));
        }
        if (list.size() > PREVIEW_ELEMENTS) preview.append("\n…");
        this.button.setTooltip(Tooltip.create(Component.literal(preview.toString())));
    }
}
