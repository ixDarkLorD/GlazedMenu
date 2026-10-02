package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.style.GuiFade;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Fades what's drawn while a Glazed Menu screen fades in or out. 1.21.1 tints everything the GUI draws by the shader
// color, which GuiFade sets; widgets that set their own tint (vanilla buttons do) keep the fade through here.
@Mixin(GuiGraphics.class)
public abstract class GuiFadeMixin {
    @ModifyVariable(method = "setColor", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float glazedmenu$fade(float alpha) {
        return alpha * GuiFade.alpha();
    }
}
