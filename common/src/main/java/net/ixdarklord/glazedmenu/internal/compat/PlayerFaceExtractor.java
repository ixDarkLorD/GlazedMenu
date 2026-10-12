package net.ixdarklord.glazedmenu.internal.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.PlayerSkin;

// A player's face, tinted (Minecraft 26.1's PlayerFaceExtractor; 1.21.1's PlayerFaceRenderer draws it untinted).
public final class PlayerFaceExtractor {
    private PlayerFaceExtractor() {}

    public static void extractRenderState(GuiGraphicsExtractor graphics, PlayerSkin skin, int x, int y, int size) {
        extractRenderState(graphics, skin, x, y, size, 0xFFFFFFFF);
    }

    public static void extractRenderState(GuiGraphicsExtractor graphics, PlayerSkin skin, int x, int y, int size, int color) {
        if ((color >>> 24) == 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.raw().setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
        PlayerFaceRenderer.draw(graphics.raw(), skin, x, y, size);
        graphics.raw().setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }
}
