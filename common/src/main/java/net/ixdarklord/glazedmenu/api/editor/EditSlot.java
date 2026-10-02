package net.ixdarklord.glazedmenu.api.editor;

import net.ixdarklord.glazedmenu.api.config.type.ConfigType;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * What a {@link ValueEditor} edits: a config value, or an element of a list being edited. Values set here are
 * pending until the player saves.
 */
public interface EditSlot<T> {
    ConfigType<T> type();

    /** The pending value. */
    T get();

    /** Sets a new pending value; it's checked, and the slot reports an {@link #error()} when it isn't valid. */
    void set(T value);

    /** Marks input that isn't a value at all (text that doesn't parse), which blocks saving until it's fixed. */
    void setInvalid(Component error);

    /** Why the current input can't be saved, if it can't. */
    Optional<Component> error();

    /** The name to narrate. */
    Component name();
}
