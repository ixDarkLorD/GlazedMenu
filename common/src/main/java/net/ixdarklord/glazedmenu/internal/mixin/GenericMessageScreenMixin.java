package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.game.WorkingLook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The game's one-line waits as a world is opened, made or left ("Reading world data", "Preparing for world creation",
// "Saving world"), in Glazed Menu's look: the world's icon in a ring of the wait's steps, and the steps named under it
// (WorkingLook). The screen stays the game's own; only what it draws is replaced.
@Mixin(GenericMessageScreen.class)
public abstract class GenericMessageScreenMixin extends Screen {
    private GenericMessageScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    private @Nullable FocusableTextWidget textWidget;

    // The game's own line isn't drawn over it (the screen has no drawing of its own to take the place of, only its
    // background and this one widget).
    @Inject(method = "init", at = @At("TAIL"))
    private void glazedmenu$hideLine(CallbackInfo info) {
        if (WorkingLook.active() && this.textWidget != null) this.textWidget.visible = false;
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$look(GuiGraphics raw, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (!WorkingLook.active()) return;
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        WorkingLook.background(graphics, this.width, this.height, () -> this.renderPanorama(raw, a));
        WorkingLook.extract(graphics, this.font, this.width, this.height, this.title);
        info.cancel();
    }
}
