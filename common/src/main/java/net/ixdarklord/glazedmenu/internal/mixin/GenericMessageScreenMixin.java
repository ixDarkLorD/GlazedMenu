package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.game.WorkingLook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The game's one-line waits as a world is opened, made or left ("Reading world data", "Preparing for world creation",
// "Saving world"), in Glazed Menu's look: the world's icon in a ring of the wait's steps, and the steps named under it
// (WorkingLook). The screen stays the game's own (1.20.1's GenericDirtMessageScreen); only what it draws is replaced.
@Mixin(GenericDirtMessageScreen.class)
public abstract class GenericMessageScreenMixin extends Screen {
    private GenericMessageScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$look(GuiGraphics raw, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (!WorkingLook.active()) return;
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        WorkingLook.background(graphics, this.width, this.height);
        WorkingLook.extract(graphics, this.font, this.width, this.height, this.title);
        info.cancel();
    }
}
