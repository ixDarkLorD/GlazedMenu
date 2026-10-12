package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.internal.mixin.ConfirmScreenAccessor;
import net.ixdarklord.glazedmenu.internal.compat.Compat;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.mixin.BackupConfirmScreenAccessor;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.ixdarklord.glazedmenu.internal.style.StyledScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.ixdarklord.glazedmenu.internal.compat.KeyEvent;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The game's warnings about a world, as a popup in place of its own screens: that a world uses experimental (or no
 * longer supported) settings as it's made, and the question of a backup before a world is opened that might not open
 * well (experimental or customized settings, one last played in a newer or an older version). The warning under its
 * title, the game's own choices under it, and the game's checkbox where it has one.
 * <p>
 * It's a face on the game's own screen, which it keeps: the choices press its own buttons, Escape is its own Escape,
 * and it's ticked for the buttons it holds back a moment.
 */
public final class GlazedWarningScreen extends StyledPopup {
    private static final int WIDTH = 300;
    private static final int LINE = 10;
    private static final int MAX_LINES = 9;
    private static final int KEY_ESCAPE = 256;
    // The face of the game's screen now open, so that screen coming back comes back as its face.
    private static WeakReference<GlazedWarningScreen> current = new WeakReference<>(null);

    private final Screen game;
    private final List<Button> gameButtons = new ArrayList<>();
    private final List<FlatButton> buttons = new ArrayList<>();
    private @Nullable Checkbox gameCheckbox;
    private List<FormattedCharSequence> lines = List.of();
    private boolean stacked;
    private int checkboxY;

    private GlazedWarningScreen(@Nullable Screen parent, Screen game) {
        super(parent, game.getTitle(), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.game = game;
    }

    /** The screen to open instead: this one for the game's own warnings about a world, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (screen == null || !GlazedSettings.replaceWarnings()) return screen;
        // 1.20.1 asks about a world of a newer version with a question of its own, from the list of worlds: it's one of these too.
        boolean warning = screen.getClass() == BackupConfirmScreen.class
                || screen.getClass() == ConfirmScreen.class && screen.getTitle().getContents() instanceof TranslatableContents title
                && (title.getKey().startsWith("selectWorld.warning.") || title.getKey().equals("selectWorld.versionQuestion"));
        if (!warning) return screen;
        GlazedWarningScreen face = current.get();
        if (face != null && face.game == screen) return face;
        // Over the screen it was asked from, where that's one of Glazed Menu's; over the world's picture otherwise.
        Screen open = Minecraft.getInstance().screen;
        face = new GlazedWarningScreen(open instanceof StyledScreen ? open : null, screen);
        current = new WeakReference<>(face);
        return face;
    }

    private static void press(AbstractWidget widget) {
        widget.mouseClicked(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, 0);
    }

    // What the game's screen says under its title.
    private Component message() {
        if (this.game instanceof BackupConfirmScreen) return ((BackupConfirmScreenAccessor) this.game).glazedmenu$description();
        // 1.20.1's question keeps its message to itself, not in a widget.
        return this.game instanceof ConfirmScreen ? ((ConfirmScreenAccessor) this.game).glazedmenu$message() : CommonComponents.EMPTY;
    }

    @Override
    protected void initPopup() {
        // The game's screen is laid out too: its text and its buttons are what's read and pressed here.
        Compat.init(this.game, this.width, this.height);
        this.gameButtons.clear();
        this.gameCheckbox = null;
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof Button button) this.gameButtons.add(button);
            else if (child instanceof Checkbox checkbox) this.gameCheckbox = checkbox;
        }
        // Wide enough for the title on its one line, where the window is; where it isn't, the title is said in full
        // over the warning.
        int titleWidth = this.font.width(this.title.copy().withStyle(ChatFormatting.BOLD)) + ConfigIcons.SIZE + 5;
        int wanted = Math.max(WIDTH, titleWidth + PADDING * 2 + 2);
        int inner = Math.min(wanted, this.width - 16) - PADDING * 2;
        this.lines = new ArrayList<>(this.font.split(this.message(), inner - 12));
        if (titleWidth > inner) this.lines.addAll(0, this.font.split(this.title.copy().withStyle(ChatFormatting.BOLD), inner - 12));
        if (this.lines.size() > MAX_LINES) this.lines = this.lines.subList(0, MAX_LINES);

        // The choices side by side where they fit (the first of them the one that goes on), one under another otherwise.
        int[] widths = new int[this.gameButtons.size()];
        int row = 0;
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.max(70, this.font.width(this.gameButtons.get(i).getMessage().getString()) + 18);
            row += widths[i] + (i == 0 ? 0 : 4);
        }
        this.stacked = row > inner;
        int choices = this.stacked ? widths.length * 23 - 3 : 20;
        int text = this.lines.size() * LINE + 10;
        int checkbox = this.gameCheckbox == null ? 0 : 18;
        this.setPanel(wanted, PADDING + TITLE_HEIGHT + text + 6 + checkbox + choices + 6 + PADDING);
        int left = this.contentLeft();
        int width = this.contentWidth();
        this.checkboxY = this.contentTop() + text + 6;

        this.buttons.clear();
        int y = this.contentBottom() - choices;
        int x = left + width;
        for (int i = 0; i < widths.length; i++) {
            Button target = this.gameButtons.get(i);
            // The label as plain text: the game hands it out dimmed while its button is still held back.
            FlatButton button = FlatButton.of(Component.literal(target.getMessage().getString()), this.stacked ? width : widths[i], pressed -> press(target));
            if (i == 0) button.style(FlatButton.Style.PRIMARY);
            if (this.stacked) {
                button.setPosition(left, y + i * 23);
            } else {
                // The one that goes on at the right, the others to its left in their order.
                x -= widths[i];
                button.setPosition(x, y);
                x -= 4;
            }
            this.buttons.add(this.addRenderableWidget(button));
        }
        this.follow();
    }

    // The buttons here are on while the game's own are (it holds them back a moment on some warnings).
    private void follow() {
        for (int i = 0; i < this.buttons.size(); i++) this.buttons.get(i).active = this.gameButtons.get(i).active;
    }

    @Override
    public void tick() {
        super.tick();
        this.game.tick();
        this.follow();
    }

    // Escape is the game's own: it answers no, or cancels.
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == KEY_ESCAPE) return this.game.keyPressed(event.key(), event.scancode(), event.modifiers());
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // A click beside the popup doesn't answer for the player.
    @Override
    public void onClose() {}

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Checkbox checkbox = this.gameCheckbox;
        if (checkbox != null && event.button() == 0 && event.x() >= this.contentLeft() && event.x() < this.contentLeft() + this.contentWidth()
                && event.y() >= this.checkboxY && event.y() < this.checkboxY + 14) {
            press(checkbox);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(this.title, this.message());
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.WARNING;
    }

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = this.contentLeft();
        int width = this.contentWidth();
        int y = this.contentTop();
        int height = this.lines.size() * LINE + 10;
        ConfigStyle.rect(graphics, left, y, width, height, ConfigStyle.withAlpha(ConfigStyle.colors().field(), 0x90));
        ConfigStyle.outline(graphics, left, y, width, height, ConfigStyle.withAlpha(0xFFE0B040, 0xA0));
        for (int i = 0; i < this.lines.size(); i++) {
            graphics.text(this.font, this.lines.get(i), left + 6, y + 5 + i * LINE, ConfigStyle.colors().text(), false);
        }
        Checkbox checkbox = this.gameCheckbox;
        if (checkbox != null) {
            boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= this.checkboxY && mouseY < this.checkboxY + 14;
            boolean on = checkbox.selected();
            ConfigStyle.rect(graphics, left + 1, this.checkboxY + 2, 10, 10, on ? ConfigStyle.accent() : ConfigStyle.colors().field());
            if (on) ConfigIcons.CHECK.draw(graphics, left + 2, this.checkboxY + 3, 8, 0xFFFFFFFF);
            else ConfigStyle.outline(graphics, left + 1, this.checkboxY + 2, 10, 10, ConfigStyle.colors().panelBorder());
            ConfigStyle.text(graphics, this.font, Component.literal(checkbox.getMessage().getString()), left + 16, this.checkboxY + 3, width - 18,
                    hovered ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
        }
    }
}
