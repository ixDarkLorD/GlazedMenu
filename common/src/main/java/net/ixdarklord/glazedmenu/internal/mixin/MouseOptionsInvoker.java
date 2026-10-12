package net.ixdarklord.glazedmenu.internal.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// The options this screen of the game's lists, for the game's options to be grouped as its own screens group them
// (GameOptionsSource).
@Mixin(MouseSettingsScreen.class)
public interface MouseOptionsInvoker {
    @Invoker("options")
    static OptionInstance<?>[] glazedmenu$options(Options options) {
        throw new AssertionError();
    }
}
