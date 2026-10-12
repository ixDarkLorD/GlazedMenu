package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

// What the game keeps to itself about one of its options, for listing the options as a config: the value it starts at
// (its default), and how it words each of its values (the names of an enum's constants on its button).
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor<T> {
    @Accessor("initialValue")
    T glazedmenu$initialValue();

    @Accessor("toString")
    Function<T, Component> glazedmenu$toStringFunction();
}
