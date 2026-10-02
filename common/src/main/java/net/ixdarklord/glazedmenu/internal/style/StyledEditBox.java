package net.ixdarklord.glazedmenu.internal.style;

import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// A text field in the config screens' style. It keeps the vanilla layout (text inset as if bordered) but draws its
// own field instead of the vanilla sprite, outlined in the accent while focused and in red while invalid.
public class StyledEditBox extends EditBox {
    private boolean drawingOwnFrame;
    private boolean invalid;
    private @Nullable Component placeholder;

    public StyledEditBox(Font font, int width, int height, Component narration) {
        super(font, width, height, narration);
        // Flat text: a shadow smudges on light schemes.
        this.setTextShadow(false);
    }

    /** Text shown while the field is empty and unfocused, drawn in the scheme's muted color. */
    public void setPlaceholder(@Nullable Component placeholder) {
        this.placeholder = placeholder;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    // While drawing, vanilla must not draw its sprite; everywhere else it lays out as bordered.
    @Override
    public boolean isBordered() {
        return !this.drawingOwnFrame && super.isBordered();
    }

    @Override
    public int getInnerWidth() {
        return super.isBordered() ? this.width - 8 : this.width;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (!this.isVisible()) return;
        if (super.isBordered()) {
            int border = this.invalid ? ConfigStyle.colors().error()
                    : this.isFocused() ? ConfigStyle.accent()
                    : this.active && this.isHovered() ? ConfigStyle.mix(ConfigStyle.colors().fieldBorder(), ConfigStyle.accent(), 0.4F)
                    : ConfigStyle.colors().fieldBorder();
            ConfigStyle.rect(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.active ? ConfigStyle.colors().field() : ConfigStyle.colors().buttonDisabled());
            ConfigStyle.outline(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), border);
        }
        // The scheme can change between screens, so the text takes its colors as it draws.
        this.setTextColor(this.invalid ? ConfigStyle.colors().error() : ConfigStyle.colors().text());
        this.setTextColorUneditable(ConfigStyle.colors().textMuted());
        this.drawingOwnFrame = true;
        try {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        } finally {
            this.drawingOwnFrame = false;
        }
        if (this.placeholder != null && this.getValue().isEmpty() && !this.isFocused()) {
            ConfigStyle.text(graphics, Minecraft.getInstance().font, this.placeholder, this.getX() + 4, this.getY() + (this.getHeight() - 8) / 2,
                    this.getWidth() - 8, ConfigStyle.colors().textMuted());
        }
        if (super.isBordered()) ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.TEXT_FIELD, this);
    }
}
