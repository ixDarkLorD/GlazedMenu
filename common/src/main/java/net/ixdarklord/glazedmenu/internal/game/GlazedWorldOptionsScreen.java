package net.ixdarklord.glazedmenu.internal.game;

import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.mixin.AbstractWidgetAccessor;
import net.ixdarklord.glazedmenu.internal.mixin.WorldOptionsScreenAccessor;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.LockIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.gui.screens.options.HasDifficultyReaction;
import net.minecraft.client.gui.screens.options.HasGamemasterPermissionReaction;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A world's options (the game's since 26.3), as a popup in place of the game's World Options screen: the world's game
 * modes, its difficulty and whether it takes commands in one card; whether it's open to others as cards to choose from;
 * and what the players who join may do, with the port they join at.
 * <p>
 * It's a face on the game's own screen, which it keeps: every setting here is one of the game's own widgets, read for
 * what it's set to, whether it's locked and why (the game's own tooltip), and pressed or typed into to change it, so
 * each depends on the others exactly as the game has it; nothing is changed until the game's own Apply Changes is pressed.
 */
public final class GlazedWorldOptionsScreen extends StyledPopup implements HasDifficultyReaction, HasGamemasterPermissionReaction {
    private static final String VANILLA = "net.minecraft.client.gui.screens.WorldOptionsScreen";
    // The settings of the world itself take the wider column: the four game modes stand side by side in it.
    private static final int COLUMN = 236;
    private static final int SIDE_COLUMN = 180;
    private static final int COLUMN_GAP = 10;
    private static final int HEADING = 13;
    private static final int CHOICES = 30;
    private static final int SWITCH = 20;
    private static final int CARDS = 26;
    // The face of the game's screen now open, so that screen coming back (from the game rules) comes back as its face.
    private static WeakReference<GlazedWorldOptionsScreen> current = new WeakReference<>(null);

    private final WorldOptionsScreen game;
    private final List<Part> parts = new ArrayList<>();
    private int bodyTop, bodyBottom, bodyHeight;
    private int scroll;
    private @Nullable EditBox gamePort;
    private @Nullable StyledEditBox portBox;
    private int portY;
    private @Nullable Button gameApply;
    private @Nullable FlatButton apply;

    private enum Kind {
        /** A name over the text of a part of the screen. */
        HEADING,
        /** A name over the values to choose from, the chosen one lit. */
        CHOICES,
        /** A name beside a switch. */
        SWITCH,
        /** Values to choose from as cards, each saying what it is. */
        CARDS,
        /** Buttons of the game's side by side. */
        BUTTONS,
        /** A name beside a box to type in, and under them what's wrong with what was typed. */
        PORT
    }

    // A part of the popup where it's laid out (y from the top of the body, before scrolling), and the game's widgets it shows.
    private record Part(Kind kind, Component name, List<AbstractWidget> widgets, int x, int y, int width, int height) {
        AbstractWidget widget() {
            return this.widgets.getFirst();
        }
    }

    private GlazedWorldOptionsScreen(@Nullable Screen parent, WorldOptionsScreen game) {
        super(parent, game.getTitle(), ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        this.game = game;
    }

    /** The screen to open instead: this one for the game's own World Options screen, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof WorldOptionsScreen game) || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceWorldOptions()) return screen;
        GlazedWorldOptionsScreen face = current.get();
        if (face != null && face.game == game) return face;
        // Over the screen it was asked from (the pause menu).
        face = new GlazedWorldOptionsScreen(Minecraft.getInstance().gui.screen(), game);
        current = new WeakReference<>(face);
        return face;
    }

    // --- The game's own screen ---

    private static @Nullable String key(Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    // The name of one of the game's settings: its buttons say "name: value".
    private static @Nullable Component settingName(AbstractWidget widget) {
        if (widget.getMessage().getContents() instanceof TranslatableContents contents && contents.getKey().equals("options.generic_value")
                && contents.getArgs().length > 0 && contents.getArgs()[0] instanceof Component name) return name;
        return null;
    }

    private static void press(AbstractWidget widget) {
        widget.mouseClicked(new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
    }

    private static void collect(List<? extends GuiEventListener> from, List<AbstractWidget> into, int depth) {
        for (GuiEventListener child : from) {
            if (child instanceof AbstractWidget widget) into.add(widget);
            if (depth < 6 && child instanceof ContainerEventHandler container) collect(container.children(), into, depth + 1);
        }
    }

    // What one of the game's settings may be set to, in the game's order.
    private static List<?> values(CycleButton<?> setting) {
        Object value = setting.getValue();
        if (value instanceof GameType) return List.of(GameType.values());
        if (value instanceof Difficulty) return List.of(Difficulty.values());
        if (value instanceof Boolean) return List.of(false, true);
        return List.of(value);
    }

    private static Component valueName(Object value) {
        if (value instanceof GameType mode) return mode.getShortDisplayName();
        if (value instanceof Difficulty difficulty) return difficulty.getDisplayName();
        if (value instanceof Boolean on) return on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        return Component.literal(String.valueOf(value));
    }

    // Sets one of the game's settings by pressing its own button until it says what's wanted: the game's own change, with
    // all that follows from it there.
    private void set(CycleButton<?> setting, Object wanted) {
        if (!setting.active || Objects.equals(setting.getValue(), wanted)) return;
        MouseButtonEvent click = new MouseButtonEvent(setting.getX() + 1, setting.getY() + 1, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        for (int i = 0; i < 8 && !Objects.equals(setting.getValue(), wanted); i++) setting.onPress(click);
        this.click();
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private @Nullable Tooltip tooltip(AbstractWidget widget) {
        return ((AbstractWidgetAccessor) widget).glazedmenu$tooltip().get();
    }

    // --- Layout ---

    @Override
    protected void initPopup() {
        // The game's screen is laid out too: its settings are what's read and pressed here.
        String typed = this.gamePort != null ? this.gamePort.getValue() : null;
        this.game.init(this.width, this.height);
        List<AbstractWidget> all = new ArrayList<>();
        collect(this.game.children(), all, 0);

        CycleButton<?> defaultMode = null, personalMode = null, difficulty = null, commands = null, open = null, guestCommands = null, forceMode = null;
        LockIconButton lock = null;
        Button rules = null, restrictions = null, cancel = null;
        this.gamePort = null;
        this.gameApply = null;
        for (AbstractWidget widget : all) {
            Component name = settingName(widget);
            String named = name == null ? key(widget.getMessage()) : key(name);
            if (widget instanceof CycleButton<?> setting && named != null) {
                switch (named) {
                    case "options.worldOptions.game_mode" -> defaultMode = setting;
                    case "options.worldOptions.personal_game_mode" -> personalMode = setting;
                    case "options.difficulty" -> difficulty = setting;
                    case "selectWorld.allowCommands" -> commands = setting;
                    case "menu.multiplayerOptions.lan" -> open = setting;
                    case "options.worldOptions.guest.command_access" -> guestCommands = setting;
                    case "options.worldOptions.guest.force_game_mode" -> forceMode = setting;
                    default -> {}
                }
            } else if (widget instanceof LockIconButton found) {
                lock = found;
            } else if (widget instanceof EditBox box) {
                this.gamePort = box;
            } else if (widget instanceof Button button && named != null) {
                switch (named) {
                    case "editGamerule.inGame.button" -> rules = button;
                    case "restrictions_screen.button" -> restrictions = button;
                    case "menu.multiplayerOptions.applyChanges" -> this.gameApply = button;
                    case "gui.cancel" -> cancel = button;
                    default -> {}
                }
            }
        }
        if (typed != null && this.gamePort != null && this.gamePort.active && !this.gamePort.getValue().equals(typed)) this.gamePort.setValue(typed);

        // Two columns where there's something of the world's opening to others to show and the window has the room.
        boolean others = open != null;
        boolean wide = others && this.width - 16 - PADDING * 2 >= COLUMN + COLUMN_GAP + SIDE_COLUMN;
        int column = Math.min(COLUMN, this.width - 16 - PADDING * 2);
        int side = wide ? SIDE_COLUMN : column;
        this.parts.clear();
        int y = 0;
        y = this.part(Kind.HEADING, Component.translatable("options.worldOptions.general.title"), List.of(), 0, y, column, HEADING);
        if (defaultMode != null) y = this.part(Kind.CHOICES, settingName(defaultMode), List.of(defaultMode), 0, y, column, CHOICES);
        if (personalMode != null) y = this.part(Kind.CHOICES, settingName(personalMode), List.of(personalMode), 0, y, column, CHOICES);
        if (difficulty != null) y = this.part(Kind.CHOICES, settingName(difficulty), lock != null ? List.of(difficulty, lock) : List.of(difficulty), 0, y, column, CHOICES);
        if (commands != null) y = this.part(Kind.SWITCH, settingName(commands), List.of(commands), 0, y, column, SWITCH);
        List<AbstractWidget> buttons = new ArrayList<>();
        if (rules != null) buttons.add(rules);
        if (restrictions != null) buttons.add(restrictions);
        if (!buttons.isEmpty()) y = this.part(Kind.BUTTONS, CommonComponents.EMPTY, buttons, 0, y + 2, column, 20);
        int leftHeight = y;
        int rightHeight = 0;
        if (others) {
            int x = wide ? column + COLUMN_GAP : 0;
            y = wide ? 0 : leftHeight + 6;
            y = this.part(Kind.HEADING, Component.translatable("options.worldOptions.multiplayer.title"), List.of(), x, y, side, HEADING);
            y = this.part(Kind.CARDS, CommonComponents.EMPTY, List.of(open), x, y, side, CARDS);
            // Named by Glazed Menu itself: the game has no name for these in 26.3 (its own went with 26.2's screen).
            y = this.part(Kind.HEADING, Component.translatableWithFallback("glazedmenu.world.others", "Settings for Other Players"), List.of(), x, y + 4, side, HEADING);
            if (guestCommands != null) y = this.part(Kind.SWITCH, settingName(guestCommands), List.of(guestCommands), x, y, side, SWITCH);
            if (forceMode != null) y = this.part(Kind.SWITCH, settingName(forceMode), List.of(forceMode), x, y, side, SWITCH);
            if (this.gamePort != null) {
                this.portY = y;
                y = this.part(Kind.PORT, Component.translatable("lanServer.port"), List.of(this.gamePort), x, y, side, SWITCH + 11);
            }
            rightHeight = y;
        }
        this.bodyHeight = Math.max(leftHeight, rightHeight);
        int bodyWidth = wide ? column + COLUMN_GAP + side : column;
        this.setPanel(bodyWidth + PADDING * 2, PADDING + TITLE_HEIGHT + this.bodyHeight + 6 + FOOTER + PADDING);
        this.bodyTop = this.contentTop();
        this.bodyBottom = this.footerTop() - 2;

        this.portBox = null;
        if (this.gamePort != null) {
            EditBox own = this.gamePort;
            this.portBox = this.addRenderableWidget(new StyledEditBox(this.font, 76, 16, Component.translatable("lanServer.port")));
            this.portBox.setMaxLength(5);
            this.portBox.setValue(own.getValue());
            this.portBox.setResponder(text -> {
                if (!own.getValue().equals(text)) own.setValue(text);
            });
        }

        int right = this.contentLeft() + this.contentWidth();
        Component applyName = this.gameApply != null ? this.gameApply.getMessage() : CommonComponents.GUI_DONE;
        int applyWidth = Math.max(84, this.font.width(applyName) + 30);
        Button ownCancel = cancel;
        this.apply = this.addRenderableWidget(FlatButton.of(applyName, applyWidth, button -> {
            if (this.gameApply != null) press(this.gameApply);
        }).style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        this.apply.setPosition(right - applyWidth, this.footerButtonY());
        FlatButton back = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, 70, button -> {
            if (ownCancel != null) press(ownCancel);
            else this.game.onClose();
        }));
        back.setPosition(right - applyWidth - 4 - 70, this.footerButtonY());
        this.follow();
    }

    private int part(Kind kind, @Nullable Component name, List<AbstractWidget> widgets, int x, int y, int width, int height) {
        this.parts.add(new Part(kind, name == null ? CommonComponents.EMPTY : name, widgets, x, y, width, height));
        return y + height;
    }

    // What's here follows the game's own: Apply Changes is on while the game's is, the port's box says what the game's does.
    private void follow() {
        if (this.apply != null && this.gameApply != null) this.apply.active = this.gameApply.active;
        if (this.portBox != null && this.gamePort != null) {
            // The game empties its box as the world is closed to others, and fills it as it's opened.
            if (!this.portBox.isFocused() && !this.gamePort.getValue().equals(this.portBox.getValue())) this.portBox.setValue(this.gamePort.getValue());
            this.portBox.setEditable(this.gamePort.active);
            this.portBox.active = this.gamePort.active;
            this.portBox.setInvalid(!((WorldOptionsScreenAccessor) this.game).glazedmenu$portValid());
            for (Part part : this.parts) {
                if (part.kind != Kind.PORT) continue;
                int y = this.bodyTop + part.y - this.scroll + 2;
                this.portBox.setPosition(this.contentLeft() + part.x + part.width - 76, y);
                this.portBox.visible = y >= this.bodyTop && y + 16 <= this.bodyBottom;
            }
        }
    }

    @Override
    public void added() {
        super.added();
        this.game.added();
    }

    @Override
    public void tick() {
        super.tick();
        this.follow();
    }

    // The game tells the screen that's open of what the server changed: it's passed on to its own.
    @Override
    public void onDifficultyChanged() {
        this.game.onDifficultyChanged();
    }

    @Override
    public void onGamemasterPermissionChanged(boolean hasGamemasterPermission) {
        this.game.onGamemasterPermissionChanged(hasGamemasterPermission);
    }

    @Override
    public void onClose() {
        this.game.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int most = Math.max(0, this.bodyHeight - (this.bodyBottom - this.bodyTop));
        if (scrollY != 0 && most > 0) {
            this.scroll = Math.clamp(this.scroll - (int) Math.signum(scrollY) * 14, 0, most);
            this.follow();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // The values of a setting in a row, each as wide as the others; a lock beside them takes the row's end.
    private int[] choiceLefts(Part part, int count) {
        int room = part.width - (part.widgets.size() > 1 ? 18 : 0);
        int[] lefts = new int[count + 1];
        for (int i = 0; i <= count; i++) lefts[i] = this.contentLeft() + part.x + Math.round(room * i / (float) count);
        return lefts;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && y >= this.bodyTop && y < this.bodyBottom) {
            for (Part part : this.parts) {
                int left = this.contentLeft() + part.x;
                int top = this.bodyTop + part.y - this.scroll;
                if (x < left || x >= left + part.width || y < top || y >= top + part.height) continue;
                switch (part.kind) {
                    case CHOICES -> {
                        if (y < top + 11 || y >= top + 27) break;
                        CycleButton<?> setting = (CycleButton<?>) part.widget();
                        List<?> values = values(setting);
                        int[] lefts = this.choiceLefts(part, values.size());
                        for (int i = 0; i < values.size(); i++) {
                            if (x >= lefts[i] && x < lefts[i + 1] - 2) this.set(setting, values.get(i));
                        }
                        if (part.widgets.size() > 1 && x >= left + part.width - 16 && part.widgets.get(1).active) {
                            // The game's lock: it's turned by the game's own button.
                            ((Button) part.widgets.get(1)).onPress(event);
                            this.click();
                        }
                    }
                    case SWITCH -> {
                        CycleButton<?> setting = (CycleButton<?>) part.widget();
                        if (x >= left + part.width - 60) this.set(setting, !Boolean.TRUE.equals(setting.getValue()));
                    }
                    case CARDS -> {
                        CycleButton<?> setting = (CycleButton<?>) part.widget();
                        int half = (part.width - 4) / 2;
                        this.set(setting, x >= left + half + 4);
                    }
                    case BUTTONS -> {
                        int each = (part.width - 4 * (part.widgets.size() - 1)) / part.widgets.size();
                        for (int i = 0; i < part.widgets.size(); i++) {
                            int buttonX = left + i * (each + 4);
                            if (x >= buttonX && x < buttonX + each && part.widgets.get(i).active) press(part.widgets.get(i));
                        }
                    }
                    default -> {}
                }
                if (part.kind != Kind.PORT) return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.SCOPE_WORLD;
    }

    // The world, and whether it's open to others now.
    @Override
    protected @Nullable Component titleNote() {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server != null) {
            boolean lan = server.getMultiplayerScope() == MinecraftServer.MultiplayerScope.LAN;
            return Component.literal(server.getWorldData().getLevelName() + " · ")
                    .append(CommonComponents.optionNameValue(Component.translatable("menu.multiplayerOptions.lan"), valueName(lan)));
        }
        ServerData joined = this.minecraft.getCurrentServer();
        return joined != null ? Component.literal(joined.name) : null;
    }

    // --- Drawing ---

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.scroll = Math.clamp(this.scroll, 0, Math.max(0, this.bodyHeight - (this.bodyBottom - this.bodyTop)));
        boolean inBody = mouseY >= this.bodyTop && mouseY < this.bodyBottom;
        graphics.enableScissor(this.contentLeft() - 1, this.bodyTop, this.contentLeft() + this.contentWidth() + 1, this.bodyBottom);
        for (Part part : this.parts) {
            int left = this.contentLeft() + part.x;
            int top = this.bodyTop + part.y - this.scroll;
            if (top + part.height <= this.bodyTop || top >= this.bodyBottom) continue;
            boolean hovered = inBody && mouseX >= left && mouseX < left + part.width && mouseY >= top && mouseY < top + part.height;
            this.extractPart(graphics, part, left, top, hovered ? mouseX : -1, hovered ? mouseY : -1);
        }
        graphics.disableScissor();
        int shown = this.bodyBottom - this.bodyTop;
        if (this.bodyHeight > shown) {
            int track = shown - 4;
            int thumb = Math.max(10, track * shown / this.bodyHeight);
            int thumbY = this.bodyTop + 2 + (track - thumb) * this.scroll / Math.max(1, this.bodyHeight - shown);
            int x = this.contentLeft() + this.contentWidth() + 3;
            graphics.fill(x, thumbY, x + 2, thumbY + thumb, ConfigStyle.withAlpha(ConfigStyle.colors().textMuted(), 0xB0));
        }
    }

    // Why a setting is as it is, in the game's own words, under the mouse.
    private void explain(GuiGraphicsExtractor graphics, AbstractWidget widget, int mouseX, int mouseY) {
        Tooltip said = this.tooltip(widget);
        if (said != null) graphics.setTooltipForNextFrame(said.toCharSequence(this.minecraft), mouseX, mouseY);
    }

    private void extractPart(GuiGraphicsExtractor graphics, Part part, int left, int top, int mouseX, int mouseY) {
        boolean hovered = mouseX >= 0;
        switch (part.kind) {
            case HEADING -> {
                ConfigStyle.text(graphics, this.font, part.name.copy().withStyle(ChatFormatting.BOLD), left, top + 1, part.width, ConfigStyle.accent());
                graphics.fill(left, top + 11, left + part.width, top + 12, ConfigStyle.withAlpha(ConfigStyle.colors().panelBorder(), 0xA0));
            }
            case CHOICES -> {
                CycleButton<?> setting = (CycleButton<?>) part.widget();
                boolean active = setting.active;
                ConfigStyle.text(graphics, this.font, part.name, left + 1, top + 2, part.width, active ? ConfigStyle.colors().textDim() : ConfigStyle.colors().textMuted());
                List<?> values = values(setting);
                int[] lefts = this.choiceLefts(part, values.size());
                for (int i = 0; i < values.size(); i++) {
                    int x = lefts[i];
                    int width = lefts[i + 1] - 2 - x;
                    boolean chosen = Objects.equals(setting.getValue(), values.get(i));
                    boolean over = active && hovered && mouseX >= x && mouseX < x + width && mouseY >= top + 11 && mouseY < top + 27;
                    int fill = chosen ? (active ? ConfigStyle.accent() : ConfigStyle.colors().buttonDisabled()) : over ? ConfigStyle.colors().buttonHover() : ConfigStyle.colors().button();
                    ConfigStyle.rect(graphics, x, top + 11, width, 16, fill);
                    ConfigStyle.outline(graphics, x, top + 11, width, 16, chosen && active ? ConfigStyle.mix(ConfigStyle.accent(), 0xFF000000, 0.35F) : ConfigStyle.colors().panelBorder());
                    int color = !active ? ConfigStyle.colors().textMuted() : chosen ? ConfigStyle.readableOn(ConfigStyle.accent()) : over ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim();
                    ConfigStyle.centeredText(graphics, this.font, valueName(values.get(i)), x + width / 2, top + 15, width - 4, color);
                }
                if (part.widgets.size() > 1 && part.widgets.get(1) instanceof LockIconButton lock) {
                    int x = left + part.width - 16;
                    boolean over = lock.active && hovered && mouseX >= x && mouseY >= top + 11 && mouseY < top + 27;
                    ConfigStyle.rect(graphics, x, top + 11, 16, 16, over ? ConfigStyle.colors().buttonHover() : ConfigStyle.colors().button());
                    ConfigStyle.outline(graphics, x, top + 11, 16, 16, lock.isLocked() ? ConfigStyle.withAlpha(ConfigStyle.colors().warning(), 0xC0) : ConfigStyle.colors().panelBorder());
                    ConfigIcons.LOCK.draw(graphics, x + 3, top + 14, 10, lock.isLocked() ? ConfigStyle.colors().warning() : lock.active ? ConfigStyle.colors().textDim() : ConfigStyle.colors().textMuted());
                    if (over) graphics.setTooltipForNextFrame(this.font, Component.translatable("difficulty.lock.title"), mouseX, mouseY);
                    else if (hovered) this.explain(graphics, setting, mouseX, mouseY);
                } else if (hovered) {
                    this.explain(graphics, setting, mouseX, mouseY);
                }
            }
            case SWITCH -> {
                CycleButton<?> setting = (CycleButton<?>) part.widget();
                boolean on = Boolean.TRUE.equals(setting.getValue());
                boolean active = setting.active;
                ConfigStyle.text(graphics, this.font, part.name, left + 1, top + 6, part.width - 64, active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted());
                int x = left + part.width - 58;
                int y = top + 4;
                boolean over = active && hovered && mouseX >= x - 2;
                int off = over ? ConfigStyle.mix(ConfigStyle.colors().toggleOff(), ConfigStyle.colors().text(), 0.12F) : ConfigStyle.colors().toggleOff();
                ConfigStyle.rect(graphics, x, y, 26, 12, !active ? ConfigStyle.colors().buttonDisabled() : on ? ConfigStyle.accent() : off);
                ConfigStyle.outline(graphics, x, y, 26, 12, active && on ? ConfigStyle.mix(ConfigStyle.accent(), 0xFF000000, 0.35F) : ConfigStyle.colors().panelBorder());
                ConfigStyle.iconFrame(graphics, x + 1 + (on ? 14 : 0), y + 1, 10, 10, active ? ConfigStyle.colors().knob() : ConfigStyle.colors().textMuted());
                ConfigStyle.text(graphics, this.font, valueName(on), x + 32, top + 6, 26, !active ? ConfigStyle.colors().textMuted() : on ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
                if (hovered) this.explain(graphics, setting, mouseX, mouseY);
            }
            case CARDS -> {
                CycleButton<?> setting = (CycleButton<?>) part.widget();
                boolean lan = Boolean.TRUE.equals(setting.getValue());
                MinecraftServer.MultiplayerScope[] scopes = {MinecraftServer.MultiplayerScope.OFF, MinecraftServer.MultiplayerScope.LAN};
                int half = (part.width - 4) / 2;
                for (int i = 0; i < scopes.length; i++) {
                    int x = left + i * (half + 4);
                    boolean chosen = lan == (i == 1);
                    boolean over = setting.active && hovered && mouseX >= x && mouseX < x + half;
                    int color = i == 1 ? ConfigStyle.colors().success() : ConfigStyle.colors().textDim();
                    ConfigStyle.rect(graphics, x, top + 2, half, CARDS - 6, ConfigStyle.mix(ConfigStyle.colors().button(), color, chosen ? 0.22F : over ? 0.12F : 0.04F));
                    ConfigStyle.outline(graphics, x, top + 2, half, CARDS - 6, chosen ? ConfigStyle.accent() : ConfigStyle.withAlpha(ConfigStyle.colors().panelBorder(), over ? 0xFF : 0xB0));
                    (i == 1 ? ConfigIcons.LAN : ConfigIcons.LOCK).draw(graphics, x + 6, top + 7, 10, chosen ? ConfigStyle.accent() : ConfigStyle.colors().textDim());
                    ConfigStyle.text(graphics, this.font, scopes[i].getDisplayName().copy().withStyle(ChatFormatting.BOLD), x + 21, top + 8, half - 25,
                            chosen ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim());
                    // What the choice means, in the game's own words, under the mouse.
                    if (hovered && mouseX >= x && mouseX < x + half) graphics.setTooltipForNextFrame(this.font, scopes[i].getTooltip(), mouseX, mouseY);
                }
            }
            case BUTTONS -> {
                int each = (part.width - 4 * (part.widgets.size() - 1)) / part.widgets.size();
                for (int i = 0; i < part.widgets.size(); i++) {
                    AbstractWidget button = part.widgets.get(i);
                    int x = left + i * (each + 4);
                    boolean over = hovered && mouseX >= x && mouseX < x + each;
                    boolean lit = over && button.active;
                    ConfigStyle.rect(graphics, x, top, each, part.height, !button.active ? ConfigStyle.colors().buttonDisabled() : lit ? ConfigStyle.colors().buttonHover() : ConfigStyle.colors().button());
                    ConfigStyle.outline(graphics, x, top, each, part.height, ConfigStyle.colors().panelBorder());
                    ConfigStyle.centeredText(graphics, this.font, Component.literal(button.getMessage().getString()), x + each / 2, top + 6, each - 6,
                            button.active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted());
                    if (over) this.explain(graphics, button, mouseX, mouseY);
                }
            }
            case PORT -> {
                EditBox box = (EditBox) part.widget();
                ConfigStyle.text(graphics, this.font, part.name, left + 1, top + 6, part.width - 82, box.active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted());
                // What's wrong with the port typed, as the game says it (its first line here, the rest under the mouse).
                if (!((WorldOptionsScreenAccessor) this.game).glazedmenu$portValid()) {
                    Tooltip said = this.tooltip(box);
                    List<FormattedCharSequence> lines = said == null ? List.of() : said.toCharSequence(this.minecraft);
                    if (!lines.isEmpty()) graphics.text(this.font, lines.getFirst(), left + 1, top + 21, ConfigStyle.colors().error(), false);
                }
                if (hovered) this.explain(graphics, box, mouseX, mouseY);
            }
        }
    }
}
