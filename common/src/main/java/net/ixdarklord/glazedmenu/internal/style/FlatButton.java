package net.ixdarklord.glazedmenu.internal.style;

import net.ixdarklord.glazedmenu.internal.compat.CompatButton;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// A flat button in the config screens' style: plain, accent-filled (the main action), red (destructive), or ghost
// (just an icon until hovered). It can show an icon, text, or both.
public class FlatButton extends CompatButton {
    private Style style = Style.NORMAL;
    private @Nullable ConfigIcons.Icon icon;
    private int iconColor;

    public FlatButton(int width, int height, Component message, OnPress onPress) {
        super(0, 0, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static FlatButton of(Component message, int width, OnPress onPress) {
        return new FlatButton(width, 20, message, onPress);
    }

    /** An icon-only button; the tooltip names it. */
    public static FlatButton icon(ConfigIcons.Icon icon, Component name, OnPress onPress) {
        FlatButton button = new FlatButton(20, 20, CommonComponents.EMPTY, onPress);
        button.icon = icon;
        button.style = Style.GHOST;
        button.setTooltip(Tooltip.create(name));
        return button;
    }

    public FlatButton style(Style style) {
        this.style = style;
        return this;
    }

    public FlatButton withIcon(ConfigIcons.Icon icon) {
        this.icon = icon;
        return this;
    }

    /** A fixed icon color instead of the text color. */
    public FlatButton iconColor(int color) {
        this.iconColor = color;
        return this;
    }

    public FlatButton tooltip(Component tooltip) {
        this.setTooltip(Tooltip.create(tooltip));
        return this;
    }

    // The button, then the theme's effects over it.
    @Override
    protected final void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractButton(graphics, mouseX, mouseY, a);
        ThemeEffects.widget(graphics, this.effectKind(), this);
    }

    /** What theme effects are told this widget is. */
    protected ConfigEffect.WidgetKind effectKind() {
        return ConfigEffect.WidgetKind.BUTTON;
    }

    /** Draws the button itself. */
    protected void extractButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean hovered = this.active && this.isHoveredOrFocused();
        int accent = ConfigStyle.accent();
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();
        int foreground;
        switch (this.style) {
            case PRIMARY -> {
                int fill = !this.active ? ConfigStyle.colors().buttonDisabled() : hovered ? ConfigStyle.mix(accent, 0xFFFFFFFF, 0.18F) : accent;
                ConfigStyle.rect(graphics, x, y, width, height, fill);
                foreground = this.active ? ConfigStyle.readableOn(fill) : ConfigStyle.colors().textMuted();
            }
            case DANGER -> {
                ConfigStyle.rect(graphics, x, y, width, height, hovered ? ConfigStyle.withAlpha(ConfigStyle.colors().error(), 0x40) : ConfigStyle.colors().button());
                ConfigStyle.outline(graphics, x, y, width, height, hovered ? ConfigStyle.colors().error() : ConfigStyle.colors().panelBorder());
                foreground = this.active ? ConfigStyle.colors().error() : ConfigStyle.colors().textMuted();
            }
            case GHOST -> {
                if (hovered) ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x1F));
                foreground = !this.active ? ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0x90) : hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim();
            }
            default -> {
                ConfigStyle.rect(graphics, x, y, width, height, !this.active ? ConfigStyle.colors().buttonDisabled() : hovered ? ConfigStyle.colors().buttonHover() : ConfigStyle.colors().button());
                ConfigStyle.outline(graphics, x, y, width, height, hovered ? ConfigStyle.withAlpha(accent, 0xD0) : ConfigStyle.colors().panelBorder());
                foreground = this.active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted();
            }
        }

        Font font = Minecraft.getInstance().font;
        Component message = this.getMessage();
        boolean hasText = !message.getString().isEmpty();
        int iconColor = this.iconColor != 0 && this.active ? this.iconColor : foreground;
        if (this.icon != null && !hasText) {
            this.icon.drawCentered(graphics, x, y, width, height, iconColor);
        } else if (this.icon != null) {
            int textWidth = Math.min(font.width(message), width - this.icon.width() - 12);
            int left = x + (width - this.icon.width() - 4 - textWidth) / 2;
            this.icon.draw(graphics, left, y + (height - this.icon.height()) / 2, iconColor);
            ConfigStyle.text(graphics, font, message, left + this.icon.width() + 4, y + (height - 8) / 2, textWidth, foreground);
        } else {
            ConfigStyle.centeredText(graphics, font, message, x + width / 2, y + (height - 8) / 2, width - 8, foreground);
        }
    }

    public enum Style {
        NORMAL,
        PRIMARY,
        DANGER,
        GHOST
    }
}
