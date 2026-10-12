package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.game.WorkingLook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The blank screen 1.20.1 shows for a moment between two steps of a world's opening (it clears the world before with
// one, and says nothing on it): while a wait is under way it's drawn as the wait is (WorkingLook), so the wait is one
// screen from its first word to the world. Any other use of the screen is left as the game draws it.
@Mixin(ProgressScreen.class)
public abstract class ProgressScreenMixin extends Screen {
    private ProgressScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/ProgressScreen;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void glazedmenu$look(GuiGraphics raw, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (!WorkingLook.active()) return;
        if (WorkingLook.extractUnderWay(GuiGraphicsExtractor.of(raw), this.font, this.width, this.height)) info.cancel();
    }
}
