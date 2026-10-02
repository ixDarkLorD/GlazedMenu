package net.ixdarklord.glazedmenu.internal.style;

import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// A text field in the config screens' style. It keeps the vanilla layout (text inset as if bordered) but draws its
// own field instead of the vanilla sprite, outlined in the accent while focused and in red while invalid.
public class StyledEditBox extends EditBox {
    // Text longer than the field glides to keep the cursor in view: the scroll (in pixels) eases toward its target.
    private static final float SCROLL_EASE_MILLIS = 60;
    private boolean drawingOwnFrame;
    private float scroll;
    private long lastFrame;
    private long focusedAt = Util.getMillis();
    // Vanilla keeps whether the field takes input private; tracked here too, for the text's color and the mouse cursor.
    private boolean editable = true;

    @Override
    public void setEditable(boolean editable) {
        this.editable = editable;
        super.setEditable(editable);
    }
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
        this.extractText(graphics);
        if (this.isHovered()) graphics.requestCursor(this.editable ? CursorTypes.IBEAM : CursorTypes.NOT_ALLOWED);
        if (this.placeholder != null && this.getValue().isEmpty() && !this.isFocused()) {
            ConfigStyle.text(graphics, Minecraft.getInstance().font, this.placeholder, this.getX() + 4, this.getY() + (this.getHeight() - 8) / 2,
                    this.getWidth() - 8, ConfigStyle.colors().textMuted());
        }
        if (super.isBordered()) ThemeEffects.widget(graphics, ConfigEffect.WidgetKind.TEXT_FIELD, this);
    }

    // --- The text, scrolled smoothly ---

    private Font font() {
        return Minecraft.getInstance().font;
    }

    private int textLeft() {
        return this.getX() + (super.isBordered() ? 4 : 0);
    }

    private int textTop() {
        return super.isBordered() ? this.getY() + (this.getHeight() - 8) / 2 : this.getY();
    }

    // Where the text should be scrolled to: as little as keeps the cursor in view, never past the text's end.
    private float targetScroll() {
        Font font = this.font();
        String value = this.getValue();
        int inner = this.getInnerWidth() - 2;
        int cursorX = font.width(value.substring(0, Math.min(value.length(), this.getCursorPosition())));
        float target = this.scroll;
        if (cursorX - target > inner) target = cursorX - inner;
        if (cursorX < target) target = cursorX;
        return Math.clamp(target, 0, Math.max(0, font.width(value) - inner));
    }

    private void extractText(GuiGraphicsExtractor graphics) {
        Font font = this.font();
        String value = this.getValue();
        long now = Util.getMillis();
        float elapsed = this.lastFrame == 0 ? 1000 : now - this.lastFrame;
        this.lastFrame = now;
        float target = this.targetScroll();
        this.scroll = GlazedSettings.transitions()
                ? target + (this.scroll - target) * (float) Math.exp(-Math.min(elapsed, 200) / SCROLL_EASE_MILLIS) : target;
        if (Math.abs(this.scroll - target) < 0.3F) this.scroll = target;
        int left = this.textLeft();
        int top = this.textTop();
        int inner = this.getInnerWidth();
        int color = this.editable ? (this.invalid ? ConfigStyle.colors().error() : ConfigStyle.colors().text()) : ConfigStyle.colors().textMuted();
        float x = left - this.scroll;
        int cursor = Math.min(value.length(), this.getCursorPosition());
        int highlight = Math.min(value.length(), this.highlightPosition());

        graphics.enableScissor(left - 1, this.getY(), left + inner + 1, this.getY() + this.getHeight());
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x - (int) x, 0);
        int base = (int) Math.floor(x);
        if (!value.isEmpty()) graphics.text(font, value, base, top, color, false);
        int cursorX = base + font.width(value.substring(0, cursor));
        if (highlight != cursor) {
            int highlightX = base + font.width(value.substring(0, highlight));
            graphics.textHighlight(Math.min(cursorX, highlightX), top - 1, Math.max(cursorX, highlightX) - 1, top + 10, true);
        }
        if (this.isFocused() && TextCursorUtils.isCursorVisible(now - this.focusedAt)) {
            if (cursor < value.length()) TextCursorUtils.extractInsertCursor(graphics, cursorX - 1, top, color, 10);
            else TextCursorUtils.extractAppendCursor(graphics, font, cursorX, top, color, false);
        }
        pose.popMatrix();
        graphics.disableScissor();
    }

    // The selection's other end (vanilla keeps it private).
    private int highlightPosition() {
        try {
            if (HIGHLIGHT == null) {
                HIGHLIGHT = EditBox.class.getDeclaredField("highlightPos");
                HIGHLIGHT.setAccessible(true);
            }
            return HIGHLIGHT.getInt(this);
        } catch (ReflectiveOperationException e) {
            return this.getCursorPosition();
        }
    }

    private static java.lang.reflect.@Nullable Field HIGHLIGHT;

    // The character under the mouse, by the smooth scroll.
    private int positionAt(double mouseX) {
        String value = this.getValue();
        float offset = (float) mouseX - this.textLeft() + this.scroll;
        if (offset <= 0) return 0;
        return this.font().plainSubstrByWidth(value, Math.round(offset)).length();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (doubleClick) {
            super.onClick(event, true);
            return;
        }
        this.moveCursorTo(this.positionAt(event.x()), event.hasShiftDown());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        this.moveCursorTo(this.positionAt(event.x()), true);
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused && !this.isFocused()) this.focusedAt = Util.getMillis();
        super.setFocused(focused);
    }
}
