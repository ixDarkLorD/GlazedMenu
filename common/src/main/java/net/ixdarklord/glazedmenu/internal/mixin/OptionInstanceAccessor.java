package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

// What the game keeps of an option to itself: its name, the value it starts at and how it names a value, for the game's
// options to be listed as a config (GameOptionsSource).
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor<T> {
    @Accessor("caption")
    Component glazedmenu$caption();

    @Accessor("initialValue")
    T glazedmenu$initialValue();

    @Accessor("toString")
    Function<T, Component> glazedmenu$toString();
}
