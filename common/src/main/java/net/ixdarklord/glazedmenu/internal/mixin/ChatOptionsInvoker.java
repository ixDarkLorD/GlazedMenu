package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// The options this screen of the game's lists, for the game's options to be grouped as its own screens group them
// (GameOptionsSource).
@Mixin(ChatOptionsScreen.class)
public interface ChatOptionsInvoker {
    @Invoker("options")
    static OptionInstance<?>[] glazedmenu$options(Options options) {
        throw new AssertionError();
    }
}
