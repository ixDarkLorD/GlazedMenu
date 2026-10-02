package net.ixdarklord.glazedmenu.api.editor;

import net.ixdarklord.glazedmenu.api.config.type.BooleanType;
import net.ixdarklord.glazedmenu.api.config.type.ColorType;
import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.ixdarklord.glazedmenu.api.config.type.EnumType;
import net.ixdarklord.glazedmenu.api.config.type.ListType;
import net.ixdarklord.glazedmenu.api.config.type.NumberType;
import net.ixdarklord.glazedmenu.internal.gui.editor.BooleanEditor;
import net.ixdarklord.glazedmenu.internal.gui.editor.ColorEditor;
import net.ixdarklord.glazedmenu.internal.gui.editor.EnumEditor;
import net.ixdarklord.glazedmenu.internal.gui.editor.ListEditor;
import net.ixdarklord.glazedmenu.internal.gui.editor.SliderEditor;
import net.ixdarklord.glazedmenu.internal.gui.editor.TextEditor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client only: which widget edits which {@link ConfigType} in the config screen. A mod with its own type registers
 * an editor for the type's class (or for one type instance); types without one get a text box using the type's
 * {@code parse}/{@code format}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConfigEditors {
    private static final Map<ConfigType<?>, ValueEditor.Factory<?>> BY_INSTANCE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ValueEditor.Factory<?>> BY_CLASS = new ConcurrentHashMap<>();

    static {
        register(BooleanType.class, (ValueEditor.Factory) (slot, width, height) -> new BooleanEditor(slot, width, height));
        register(EnumType.class, (ValueEditor.Factory) (slot, width, height) -> new EnumEditor(slot, width, height));
        register(ColorType.class, (ValueEditor.Factory) (slot, width, height) -> new ColorEditor(slot, width, height));
        register(ListType.class, (ValueEditor.Factory) (slot, width, height) -> new ListEditor(slot, width, height));
        register(NumberType.class, (ValueEditor.Factory) (slot, width, height) -> ((NumberType<?>) slot.type()).isSlider()
                ? new SliderEditor(slot, width, height)
                : new TextEditor<>(slot, width, height));
    }

    private ConfigEditors() {}

    /** The editor for every type of this class (and its subclasses, unless they have their own). */
    public static void register(Class<? extends ConfigType> typeClass, ValueEditor.Factory<?> factory) {
        BY_CLASS.put(typeClass, factory);
    }

    /** The editor for one type instance, ahead of its class's. */
    public static <T> void register(ConfigType<T> type, ValueEditor.Factory<T> factory) {
        BY_INSTANCE.put(type, factory);
    }

    /** An editor for the slot's type. */
    public static <T> ValueEditor create(EditSlot<T> slot, int width, int height) {
        ValueEditor.Factory<T> factory = (ValueEditor.Factory<T>) BY_INSTANCE.get(slot.type());
        for (Class<?> type = slot.type().getClass(); factory == null && type != null; type = type.getSuperclass()) {
            factory = (ValueEditor.Factory<T>) BY_CLASS.get(type);
        }
        return factory != null ? factory.create(slot, width, height) : new TextEditor<>(slot, width, height);
    }
}
