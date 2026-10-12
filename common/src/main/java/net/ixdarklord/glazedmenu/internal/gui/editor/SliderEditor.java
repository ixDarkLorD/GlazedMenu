package net.ixdarklord.glazedmenu.internal.gui.editor;

import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.ixdarklord.glazedmenu.api.editor.EditSlot;
import net.ixdarklord.glazedmenu.api.editor.ValueEditor;
import net.ixdarklord.glazedmenu.api.config.type.NumberType;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.ThemeEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

// A slider over a number's range. Decimal numbers snap to about a thousandth of the range.
public final class SliderEditor<N extends Number & Comparable<N>> implements ValueEditor {
    private final EditSlot<N> slot;
    private final NumberType<N> type;
    private final double min;
    private final double max;
    private final int decimals;
    private final Slider slider;

    public SliderEditor(EditSlot<N> slot, int width, int height) {
        this.slot = slot;
        this.type = (NumberType<N>) slot.type();
        this.min = this.type.min().doubleValue();
        this.max = this.type.max().doubleValue();
        this.decimals = this.type.isIntegral() ? 0 : Math.max(0, (int) Math.ceil(-Math.log10((this.max - this.min) / 1000)));
        this.slider = new Slider(width, height);
        this.refresh();
    }

    @Override
    public AbstractSliderButton widget() {
        return this.slider;
    }

    @Override
    public void refresh() {
        this.slider.setFromValue(this.slot.get());
    }

    private final class Slider extends AbstractSliderButton {
        Slider(int width, int height) {
            super(0, 0, width, height, Component.empty(), 0);
        }

        void setFromValue(N value) {
            double range = SliderEditor.this.max - SliderEditor.this.min;
            this.value = range <= 0 ? 0 : (value.doubleValue() - SliderEditor.this.min) / range;
            this.updateMessage();
        }

        private N current() {
            double raw = SliderEditor.this.min + this.value * (SliderEditor.this.max - SliderEditor.this.min);
            if (!SliderEditor.this.type.isIntegral()) {
                raw = BigDecimal.valueOf(raw).setScale(SliderEditor.this.decimals, RoundingMode.HALF_UP).doubleValue();
            }
            return SliderEditor.this.type.fromDouble(raw);
        }

        // A track sunk into the surface (outlined, shaded inside its top edge, with a line of light under it), filled in
        // the accent up to a knob raised off it (outlined, lit along its top and left, with a shadow), and the value
        // written above it.
        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            boolean hovered = this.active && this.isHoveredOrFocused();
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int trackY = y + this.getHeight() - 8;
            int knobX = x + (int) Math.round(this.value * (width - 8));
            int accent = this.active ? ConfigStyle.accent() : ConfigStyle.colors().textMuted();
            // The track: a capsule, its ends rounded, outlined and shaded inside its top edge.
            int edge = ConfigStyle.mix(ConfigStyle.colors().panelBorder(), 0xFF000000, 0.35F);
            capsule(graphics, x, trackY, width, 6, edge);
            capsule(graphics, x + 1, trackY + 1, width - 2, 4, ConfigStyle.colors().field());
            graphics.fill(x + 2, trackY + 1, x + width - 2, trackY + 2, 0x50000000);
            graphics.fill(x + 2, trackY + 6, x + width - 2, trackY + 7, 0x30FFFFFF);
            // The filled part, rounded at its start, with a line of light along its top.
            int filled = knobX + 4;
            if (filled > x + 3) {
                int fill = ConfigStyle.withAlpha(accent, hovered ? 0xFF : 0xD8);
                graphics.fill(x + 2, trackY + 1, filled, trackY + 5, fill);
                graphics.fill(x + 1, trackY + 2, x + 2, trackY + 4, fill);
                graphics.fill(x + 2, trackY + 1, filled, trackY + 2, 0x60FFFFFF);
                graphics.fill(x + 2, trackY + 4, filled, trackY + 5, 0x30000000);
            }
            // The knob, raised off the track: outlined, lit along its top and left, with a shadow.
            ConfigStyle.iconFrame(graphics, knobX, trackY - 3, 8, 11,
                    hovered ? ConfigStyle.colors().knob() : ConfigStyle.mix(ConfigStyle.colors().knob(), ConfigStyle.colors().textDim(), 0.2F));
            if (this.isFocused()) ConfigStyle.outline(graphics, knobX - 1, trackY - 4, 10, 13, accent);
            Font font = Minecraft.getInstance().font;
            ConfigStyle.text(graphics, font, this.getMessage(), x, y + 1, width, this.active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted());
            ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.SLIDER, this);
            this.handleCursor(graphics);
        }

        // A box with its ends rounded off: two pixels cut from each corner, then one.
        private static void capsule(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
            if (width < 6 || height < 4) {
                graphics.fill(x, y, x + width, y + height, color);
                return;
            }
            graphics.fill(x + 2, y, x + width - 2, y + height, color);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
            graphics.fill(x, y + 2, x + width, y + height - 2, color);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(SliderEditor.this.type.format(SliderEditor.this.slot.get())));
        }

        @Override
        protected void applyValue() {
            N value = this.current();
            if (!SliderEditor.this.type.equals(value, SliderEditor.this.slot.get())) SliderEditor.this.slot.set(value);
            this.updateMessage();
        }
    }
}
