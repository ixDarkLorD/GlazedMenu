package net.ixdarklord.glazedmenu.internal.style;

import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// A full-screen config screen: the theme's background, a top bar (title), a body, and a bottom bar (actions).
// Subclasses place widgets with the frame's coordinates and draw static content in extractPanels.
public abstract class StyledScreen extends Screen {
    protected static final int MARGIN = 10;
    protected static final int BAR_HEIGHT = 28;
    protected static final int GAP = 6;
    protected static final int TITLE_ICON = 16;

    // Page transitions: a page's panels and widgets fade in as it opens and out as it leaves (through leave()). The
    // background stays still.
    private static final float FADE_IN_MILLIS = 220;
    private static final float FADE_OUT_MILLIS = 160;
    private static @Nullable Screen lastRemoved;

    protected final ConfigTheme theme;
    private long openedAt;
    private boolean fadingIn;
    private long leavingAt = -1;
    private @Nullable Screen leavingTo;

    protected StyledScreen(Component title, ConfigTheme theme) {
        super(title);
        this.theme = theme;
    }

    public ConfigTheme theme() {
        return this.theme;
    }

    protected int topBarY() {
        return MARGIN;
    }

    /** The top bar's height; a screen with a larger header makes it taller. */
    protected int topBarHeight() {
        return BAR_HEIGHT;
    }

    protected int bodyTop() {
        return this.topBarY() + this.topBarHeight() + GAP;
    }

    protected int bottomBarY() {
        return this.height - MARGIN - BAR_HEIGHT;
    }

    protected int bodyBottom() {
        return this.bottomBarY() - GAP;
    }

    protected int frameLeft() {
        return MARGIN;
    }

    protected int frameWidth() {
        return this.width - MARGIN * 2;
    }

    /** The y of a 20-pixel widget centered in a bar. */
    protected int barWidgetY(int barY) {
        return barY + (BAR_HEIGHT - 20) / 2;
    }

    /** The y of a widget of this height centered in the top bar. */
    protected int topBarWidgetY(int widgetHeight) {
        return this.topBarY() + (this.topBarHeight() - widgetHeight) / 2;
    }

    /** Goes to another screen, fading this page out first; a popup or dropdown over it opens at once. */
    public void leave(@Nullable Screen next) {
        if (next instanceof Overlay || !GlazedSettings.transitions()) {
            this.minecraft.setScreen(next);
            return;
        }
        if (this.leavingAt < 0) {
            this.leavingAt = System.nanoTime();
            this.leavingTo = next;
        }
    }

    /** Whether the page is fading out; it takes no input meanwhile. */
    protected boolean isLeaving() {
        return this.leavingAt >= 0;
    }

    // Once faded out, the next screen opens (on a tick, not while drawing).
    @Override
    public void tick() {
        if (this.leavingAt >= 0 && (System.nanoTime() - this.leavingAt) / 1_000_000F >= FADE_OUT_MILLIS) {
            this.leavingAt = -1;
            this.minecraft.setScreen(this.leavingTo);
        }
    }

    // How visible a fading page is: rising as it opens, falling as it leaves.
    private float fadeAlpha() {
        float in = 1 - this.transitionLeft();
        if (this.leavingAt < 0) return in;
        float out = 1 - Math.min(1, (System.nanoTime() - this.leavingAt) / 1_000_000F / FADE_OUT_MILLIS);
        return Math.min(in, out * out);
    }

    /** The screen this one returns to. */
    protected @Nullable Screen parentScreen() {
        return null;
    }

    @Override
    public void added() {
        ConfigStyle.use(this.theme);
        Screen previous = lastRemoved;
        lastRemoved = null;
        // Leaving through a popup or dropdown (like "Discard changes?") counts as leaving the page under it.
        while (previous instanceof Overlay overlay && overlay.overlayParent() != this) previous = overlay.overlayParent();
        // Back from a popup or dropdown over this page, it never left, so it doesn't fade in again.
        this.fadingIn = GlazedSettings.transitions() && !(previous instanceof Overlay overlay && overlay.overlayParent() == this);
        this.leavingAt = -1;
        this.openedAt = System.nanoTime();
    }

    @Override
    public void removed() {
        noteRemoved(this);
    }

    /** Remembers the screen that just closed, for the next page's transition. */
    static void noteRemoved(Screen screen) {
        lastRemoved = screen;
    }

    // From 1 as the page opens to 0 once it's in place: a smooth ease-in-out, so it neither jumps nor crawls.
    private float transitionLeft() {
        if (!this.fadingIn) return 0;
        float progress = Math.min(1, (System.nanoTime() - this.openedAt) / 1_000_000F / FADE_IN_MILLIS);
        if (progress >= 1) {
            this.fadingIn = false;
            return 0;
        }
        float eased = progress * progress * progress * (progress * (progress * 6 - 15) + 10);
        return 1 - eased;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        ConfigStyle.background(graphics, this.width, this.height, () -> this.extractPanorama(graphics, a));
        ThemeEffects.background(graphics, this.width, this.height, mouseX, mouseY, a);
        GuiFade.set(this.fadeAlpha());
        this.extractPanels(graphics, mouseX, mouseY, a);
        GuiFade.reset();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        GuiFade.set(this.fadeAlpha());
        super.extractRenderState(graphics, mouseX, mouseY, a);
        GuiFade.reset();
        ThemeEffects.foreground(graphics, this.width, this.height, mouseX, mouseY, a);
    }

    /** Draws the bars, panels and fixed text under the widgets. */
    protected void extractPanels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.bar(graphics, this.frameLeft(), this.topBarY(), this.frameWidth(), this.topBarHeight());
        ConfigStyle.bar(graphics, this.frameLeft(), this.bottomBarY(), this.frameWidth(), BAR_HEIGHT);
        if (this.dividedToggle != null && this.dividedToggle.visible) {
            int x = this.dividedToggle.getX() - TOGGLE_DIVIDER;
            int top = this.dividedToggle.getY() + 1;
            graphics.fill(x, top, x + 1, top + this.dividedToggle.getHeight() - 2, ConfigStyle.colors().panelBorder());
        }
    }

    // The room before the switch at the end: the hairline sits so the gaps on its two sides look equal (the switch's icon
    // sits 5 pixels inside its button).
    protected static final int TOGGLE_ROOM = 10;
    private static final int TOGGLE_DIVIDER = 3;

    // The switch placed by addModeToggleAtEnd, with a hairline before it.
    private @Nullable FlatButton dividedToggle;

    /**
     * The sun/moon switch at the right end of the top bar ({@code margin} from the frame's edge), with a hairline between
     * it and what's left of it (the search bar), as on the mod list. Returns it; leave {@link #TOGGLE_ROOM} pixels before it.
     */
    protected FlatButton addModeToggleAtEnd(int margin) {
        this.dividedToggle = this.addModeToggle(this.frameLeft() + this.frameWidth() - margin - 20);
        return this.dividedToggle;
    }

    /** The sun/moon button switching every config screen between dark and light, at the given spot in the top bar. */
    protected FlatButton addModeToggle(int x) {
        FlatButton toggle = this.addRenderableWidget(FlatButton.icon(modeIcon(), modeTooltip(), button -> {
            GlazedSettings.toggleThemeMode();
            FlatButton self = (FlatButton) button;
            self.withIcon(modeIcon());
            self.tooltip(modeTooltip());
        }));
        toggle.setPosition(x, this.topBarWidgetY(20));
        return toggle;
    }

    // The icon shows the mode a click switches to.
    private static ConfigIcons.Icon modeIcon() {
        return ConfigStyle.mode() == ConfigTheme.Mode.DARK ? ConfigIcons.SUN : ConfigIcons.MOON;
    }

    private static Component modeTooltip() {
        return ConfigStyle.mode() == ConfigTheme.Mode.DARK
                ? Component.translatableWithFallback("glazedmenu.style.mode.light", "Light mode")
                : Component.translatableWithFallback("glazedmenu.style.mode.dark", "Dark mode");
    }

    /** The title in the top bar, with an accent mark before it; returns where the text ends. */
    protected int extractTitle(GuiGraphicsExtractor graphics, Component title, int maxWidth) {
        return this.extractTitle(graphics, title, maxWidth, null);
    }

    /** The title with an icon at the far left of the bar, before the accent mark. */
    protected int extractTitle(GuiGraphicsExtractor graphics, Component title, int maxWidth, @Nullable TitleIcon icon) {
        int x = this.frameLeft() + 10;
        int y = this.topBarY() + (BAR_HEIGHT - 8) / 2;
        if (icon != null && icon.draw(graphics, this.frameLeft() + 7, this.topBarY() + (BAR_HEIGHT - TITLE_ICON) / 2, TITLE_ICON)) {
            x += TITLE_ICON + 5;
            maxWidth -= TITLE_ICON + 5;
        }
        ConfigStyle.rect(graphics, x - 4, y - 1, 2, 10, ConfigStyle.accent());
        Component bold = title.copy().withStyle(ChatFormatting.BOLD);
        ConfigStyle.text(graphics, this.font, bold, x + 2, y, maxWidth, ConfigStyle.colors().text());
        return x + 2 + Math.min(this.font.width(bold), maxWidth);
    }

    /** Draws an icon in a square; returns whether there was one. */
    @FunctionalInterface
    protected interface TitleIcon {
        boolean draw(GuiGraphicsExtractor graphics, int x, int y, int size);
    }
}
