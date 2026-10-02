package net.ixdarklord.glazedmenu.internal.gui;

import net.minecraft.util.Mth;
import net.ixdarklord.glazedmenu.internal.compat.CompatWidget;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.type.ConfigTypes;
import net.ixdarklord.glazedmenu.api.config.type.ValidationResult;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * An HSV color picker in a popup over the screen it was opened from: a saturation/brightness square, a hue bar, an
 * alpha bar (for colors with alpha), a hex field, the 16 dye colors, and the old and new color side by side, with
 * Cancel and Done in the footer below them. "Done" hands the ARGB color to the callback.
 */
public final class ColorPickerScreen extends StyledPopup {
    private static final int SQUARE = 116;
    private static final int BAR = 12;
    private static final int GAP = 6;
    private static final int SIDE_WIDTH = 112;
    private static final int SWATCH = 12;
    private static final int[] DYES = {
            0xFFF9FFFE, 0xFFF9801D, 0xFFC74EBD, 0xFF3AB3DA, 0xFFFED83D, 0xFF80C71F, 0xFFF38BAA, 0xFF474F52,
            0xFF9D9D97, 0xFF169C9C, 0xFF8932B8, 0xFF3C44AA, 0xFF835432, 0xFF5E7C16, 0xFFB02E26, 0xFF1D1D21
    };

    private final boolean alpha;
    private final int original;
    private final IntConsumer onDone;
    private float hue;
    private float saturation;
    private float brightness;
    private float opacity;
    private @Nullable StyledEditBox hex;
    private boolean updatingHex;
    private int left;
    private int top;
    private int sideX;

    public ColorPickerScreen(@Nullable Screen parent, Component title, int color, boolean alpha, IntConsumer onDone) {
        super(parent, title, themeFor(parent));
        this.alpha = alpha;
        this.original = alpha ? color : color | 0xFF000000;
        this.onDone = onDone;
        this.setColor(this.original);
    }

    // The theme of the config screen it's opened from.
    private static ConfigTheme themeFor(@Nullable Screen parent) {
        return parent instanceof StyledScreen styled ? styled.theme() : ConfigStyle.theme();
    }

    @Override
    protected void initPopup() {
        int pickerWidth = SQUARE + GAP + BAR + (this.alpha ? GAP + BAR : 0);
        this.setPanel(PADDING + pickerWidth + GAP * 2 + SIDE_WIDTH + PADDING, PADDING + TITLE_HEIGHT + GAP + SQUARE + GAP + FOOTER + PADDING);
        this.left = this.contentLeft();
        // Centered in the body, between the title bar and the footer.
        this.top = this.contentTop() + (this.footerTop() - this.contentTop() - SQUARE) / 2;
        this.sideX = this.left + pickerWidth + GAP * 2;

        this.addRenderableWidget(new PickerArea(this.left, this.top, pickerWidth, SQUARE));

        this.hex = this.addRenderableWidget(new StyledEditBox(this.font, SIDE_WIDTH, 18, Component.literal("Hex")));
        this.hex.setPosition(this.sideX, this.top + 40);
        this.hex.setMaxLength(11);
        this.hex.setResponder(this::onHexEdited);
        this.updateHex();

        this.addRenderableWidget(new Palette(this.sideX + 1, this.top + 64));

        // In the footer, right-aligned like the config popups' buttons.
        int buttonY = this.footerButtonY();
        int right = this.contentLeft() + this.contentWidth();
        FlatButton done = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_DONE, 76, button -> {
            this.onDone.accept(this.color());
            this.onClose();
        }).style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        done.setPosition(right - 76, buttonY);
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, 64, button -> this.onClose()));
        cancel.setPosition(right - 76 - 4 - 64, buttonY);
    }

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // A hairline above the footer, as in the config popups.
        int lineY = this.footerTop() + 1;
        graphics.fill(this.contentLeft(), lineY, this.contentLeft() + this.contentWidth(), lineY + 1, ConfigStyle.colors().panelBorder());
        // Old and new color, side by side.
        int half = SIDE_WIDTH / 2;
        checkerboard(graphics, this.sideX, this.top, SIDE_WIDTH, 22);
        graphics.fill(this.sideX, this.top, this.sideX + half, this.top + 22, this.original);
        graphics.fill(this.sideX + half, this.top, this.sideX + SIDE_WIDTH, this.top + 22, this.color());
        ConfigStyle.outline(graphics, this.sideX - 1, this.top - 1, SIDE_WIDTH + 2, 24, ConfigStyle.colors().panelBorder());
        int color = this.color();
        String channels = String.format(Locale.ROOT, "R%d G%d B%d", color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF)
                + (this.alpha ? String.format(Locale.ROOT, " A%d", color >>> 24) : "");
        ConfigStyle.text(graphics, this.font, Component.literal(channels), this.sideX, this.top + 27, SIDE_WIDTH, ConfigStyle.colors().textDim());
    }

    private void onHexEdited(String text) {
        if (this.updatingHex || this.hex == null) return;
        ValidationResult<Integer> result = (this.alpha ? ConfigTypes.COLOR_ALPHA : ConfigTypes.COLOR).parse(text);
        this.hex.setInvalid(!result.isOk());
        this.hex.setTextColor(result.isOk() ? ConfigStyle.colors().text() : ConfigStyle.colors().error());
        if (result.isOk()) this.setColor(result.value());
    }

    private void updateHex() {
        if (this.hex == null) return;
        this.updatingHex = true;
        this.hex.setValue((this.alpha ? ConfigTypes.COLOR_ALPHA : ConfigTypes.COLOR).format(this.color()));
        this.hex.setTextColor(ConfigStyle.colors().text());
        this.hex.setInvalid(false);
        this.updatingHex = false;
    }

    private int color() {
        int rgb = hsvToRgb(this.hue, this.saturation, this.brightness);
        int alphaByte = this.alpha ? Math.round(this.opacity * 255) : 0xFF;
        return alphaByte << 24 | rgb;
    }

    // A grey or black color has no hue of its own, so the hue picked before stays.
    private void setColor(int argb) {
        float r = (argb >> 16 & 0xFF) / 255F;
        float g = (argb >> 8 & 0xFF) / 255F;
        float b = (argb & 0xFF) / 255F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        this.brightness = max;
        this.saturation = max == 0 ? 0 : delta / max;
        if (delta > 0) {
            float h;
            if (max == r) h = ((g - b) / delta) % 6;
            else if (max == g) h = (b - r) / delta + 2;
            else h = (r - g) / delta + 4;
            this.hue = ((h / 6) % 1 + 1) % 1;
        }
        this.opacity = this.alpha ? (argb >>> 24) / 255F : 1;
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float h = (hue % 1 + 1) % 1 * 6;
        int sector = (int) Math.floor(h) % 6;
        float f = h - (float) Math.floor(h);
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);
        float r, g, b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    private static void checkerboard(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFFFFFFFF);
        for (int cy = 0; cy < height; cy += 4) {
            for (int cx = (cy / 4 % 2) * 4; cx < width; cx += 8) {
                graphics.fill(x + cx, y + cy, x + Math.min(cx + 4, width), y + Math.min(cy + 4, height), 0xFFC0C0C0);
            }
        }
    }

    // The square, the hue bar and the alpha bar; a press picks in whichever part it lands, and dragging stays there.
    private final class PickerArea extends CompatWidget {
        private static final int NONE = 0;
        private static final int SQUARE_PART = 1;
        private static final int HUE_PART = 2;
        private static final int ALPHA_PART = 3;
        private int dragging = NONE;

        PickerArea(int x, int y, int width, int height) {
            super(x, y, width, height, Component.translatableWithFallback("glazedmenu.color.picker", "Color picker"));
        }

        private int hueX() {
            return this.getX() + SQUARE + GAP;
        }

        private int alphaX() {
            return this.hueX() + BAR + GAP;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            ColorPickerScreen screen = ColorPickerScreen.this;
            int x = this.getX();
            int y = this.getY();
            // Saturation grows to the right, brightness to the top: one vertical gradient per column.
            for (int column = 0; column < SQUARE; column++) {
                int top = 0xFF000000 | hsvToRgb(screen.hue, column / (float) (SQUARE - 1), 1);
                graphics.fillGradient(x + column, y, x + column + 1, y + SQUARE, top, 0xFF000000);
            }
            ConfigStyle.outline(graphics, x - 1, y - 1, SQUARE + 2, SQUARE + 2, ConfigStyle.colors().panelBorder());
            int cursorX = x + Math.round(screen.saturation * (SQUARE - 1));
            int cursorY = y + Math.round((1 - screen.brightness) * (SQUARE - 1));
            ConfigStyle.outline(graphics, cursorX - 4, cursorY - 4, 9, 9, 0xFF000000);
            ConfigStyle.outline(graphics, cursorX - 3, cursorY - 3, 7, 7, 0xFFFFFFFF);

            int hueX = this.hueX();
            for (int row = 0; row < SQUARE; row++) {
                graphics.fill(hueX, y + row, hueX + BAR, y + row + 1, 0xFF000000 | hsvToRgb(row / (float) SQUARE, 1, 1));
            }
            ConfigStyle.outline(graphics, hueX - 1, y - 1, BAR + 2, SQUARE + 2, ConfigStyle.colors().panelBorder());
            marker(graphics, hueX, y + Math.round(screen.hue * (SQUARE - 1)));

            if (screen.alpha) {
                int alphaX = this.alphaX();
                int rgb = hsvToRgb(screen.hue, screen.saturation, screen.brightness);
                checkerboard(graphics, alphaX, y, BAR, SQUARE);
                graphics.fillGradient(alphaX, y, alphaX + BAR, y + SQUARE, 0xFF000000 | rgb, rgb);
                ConfigStyle.outline(graphics, alphaX - 1, y - 1, BAR + 2, SQUARE + 2, ConfigStyle.colors().panelBorder());
                marker(graphics, alphaX, y + Math.round((1 - screen.opacity) * (SQUARE - 1)));
            }
        }

        private static void marker(GuiGraphicsExtractor graphics, int x, int y) {
            ConfigStyle.rect(graphics, x - 2, y - 2, BAR + 4, 5, 0xFF000000);
            ConfigStyle.rect(graphics, x - 1, y - 1, BAR + 2, 3, 0xFFFFFFFF);
        }

        private int partAt(double mouseX) {
            if (mouseX < this.getX() + SQUARE) return SQUARE_PART;
            if (mouseX >= this.hueX() && mouseX < this.hueX() + BAR) return HUE_PART;
            if (ColorPickerScreen.this.alpha && mouseX >= this.alphaX() && mouseX < this.alphaX() + BAR) return ALPHA_PART;
            return NONE;
        }

        private void pick(double mouseX, double mouseY) {
            ColorPickerScreen screen = ColorPickerScreen.this;
            float fx = (float) Mth.clamp((mouseX - this.getX()) / (SQUARE - 1), 0, 1);
            float fy = (float) Mth.clamp((mouseY - this.getY()) / (SQUARE - 1), 0, 1);
            switch (this.dragging) {
                case SQUARE_PART -> {
                    screen.saturation = fx;
                    screen.brightness = 1 - fy;
                }
                case HUE_PART -> screen.hue = Math.min(fy, 0.9999F);
                case ALPHA_PART -> screen.opacity = 1 - fy;
                default -> {
                    return;
                }
            }
            screen.updateHex();
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            this.dragging = this.partAt(event.x());
            this.pick(event.x(), event.y());
        }

        @Override
        protected void onDrag(MouseButtonEvent event, double dx, double dy) {
            this.pick(event.x(), event.y());
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            this.dragging = NONE;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    // The 16 dye colors, two rows of eight.
    private final class Palette extends CompatWidget {
        Palette(int x, int y) {
            super(x, y, 8 * (SWATCH + 2), 2 * (SWATCH + 2), Component.translatableWithFallback("glazedmenu.color.palette", "Dye colors"));
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            for (int i = 0; i < DYES.length; i++) {
                int x = this.getX() + i % 8 * (SWATCH + 2);
                int y = this.getY() + i / 8 * (SWATCH + 2);
                boolean hovered = mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH;
                ConfigStyle.rect(graphics, x, y, SWATCH, SWATCH, DYES[i]);
                ConfigStyle.outline(graphics, x - 1, y - 1, SWATCH + 2, SWATCH + 2, hovered ? ConfigStyle.accent() : 0xFF000000);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            int column = (int) ((event.x() - this.getX()) / (SWATCH + 2));
            int row = (int) ((event.y() - this.getY()) / (SWATCH + 2));
            int index = row * 8 + column;
            if (column < 0 || column >= 8 || index < 0 || index >= DYES.length) return;
            ColorPickerScreen screen = ColorPickerScreen.this;
            int alphaByte = screen.alpha ? Math.round(screen.opacity * 255) << 24 : 0xFF000000;
            screen.setColor(DYES[index] & 0xFFFFFF | alphaByte);
            screen.updateHex();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
