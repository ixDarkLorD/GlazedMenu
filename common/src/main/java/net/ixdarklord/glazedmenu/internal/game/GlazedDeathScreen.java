package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.mixin.DeathScreenAccessor;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.GuiFade;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The death screen, in place of the game's own: over the world going red, a line is drawn across the screen and opens
 * into a band with the title and how the player died on its left and the game's two ways on (respawning, leaving) on
 * its right; under the band, the score, the level lost, how long the player lasted and where they died (copied by a
 * click, to find the way back to what was dropped). Each part arrives in its turn, with a sound to it, and the buttons
 * can be pressed once the bar along the band's bottom has filled. As the player respawns, the band closes and the red
 * fades off the world. With the interface's transitions turned off, it's all there at once.
 * <p>
 * It's the game's own screen under another face (it extends it, so whatever asks whether the death screen is open is
 * answered as before): the game's buttons are made and kept, off the screen, and the ones drawn here press them.
 */
public final class GlazedDeathScreen extends DeathScreen {
    private static final String VANILLA = "net.minecraft.client.gui.screens.DeathScreen";
    private static final int BAND_HEIGHT = 66;
    private static final int BUTTON_WIDTH = 116;
    private static final int CHIP_HEIGHT = 20;
    private static final int RED = 0xFFFF5F5F;
    // When each part arrives, in milliseconds since the screen opened.
    private static final long LINE_AT = 300, LINE_FOR = 450;
    private static final long OPEN_AT = 750, OPEN_FOR = 400;
    private static final long TITLE_AT = 1100, SIDES_AT = 1250, PART_FOR = 400;
    private static final long CHIPS_AT = 1550, CHIP_GAP = 100, CHIP_FOR = 350, COUNT_FOR = 700;
    private static final long LOCK_FOR = 1000;
    private static final float CLOSE_MILLIS = 450;

    // Since when the player has been alive (the joining, or the last respawn), to say how long they lasted.
    private static WeakReference<Object> session = new WeakReference<>(null);
    private static long aliveSince;
    private static boolean wasDead;
    // The closing of the band over the world, after the screen is gone: when it began (0 while there's none) and where
    // the band was.
    private static long closeStart;
    private static boolean closeSounded;

    private final ConfigTheme theme = ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME);
    private final @Nullable Component cause;
    private final LocalPlayer player;
    private final long openedAt = System.nanoTime();
    private final boolean animated = GlazedSettings.transitions();
    private final int score;
    private final int level;
    private final long survivedMillis;
    private final String place;
    private final Component placeText;
    private final List<Chip> chips = new ArrayList<>();
    private @Nullable Button gameRespawn;
    private @Nullable Button gameTitle;
    private @Nullable FlatButton respawn;
    private @Nullable FlatButton toTitle;
    private @Nullable FlatButton copy;
    private List<FormattedCharSequence> causeLines = List.of();
    private int cues;
    private int copiedTicks;

    // A detail under the band: a name and what it says, or the button copying the place (which has neither).
    private record Chip(@Nullable Component label, @Nullable String value, int count, int x, int y, int width) {}

    private GlazedDeathScreen(@Nullable Component cause, boolean hardcore, LocalPlayer player) {
        super(cause, hardcore, player);
        this.cause = cause;
        this.player = player;
        this.score = player.getScore();
        this.level = player.experienceLevel;
        this.survivedMillis = aliveSince == 0 ? 0 : Util.getMillis() - aliveSince;
        BlockPos at = player.blockPosition();
        this.place = at.getX() + " " + at.getY() + " " + at.getZ();
        this.placeText = Component.literal(at.getX() + ", " + at.getY() + ", " + at.getZ() + "  ·  " + dimensionName(player));
    }

    /** The screen to open instead: this one for the game's own death screen, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof DeathScreen) || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceDeath()) return screen;
        DeathScreenAccessor game = (DeathScreenAccessor) screen;
        return new GlazedDeathScreen(game.glazedmenu$causeOfDeath(), game.glazedmenu$hardcore(), game.glazedmenu$player());
    }

    /** Called as each frame's world has been drawn (GameRendererMixin): keeps since when the player has been alive. */
    public static void frame() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        Object connection = minecraft.getConnection();
        if (session.get() != connection) {
            session = new WeakReference<>(connection);
            aliveSince = Util.getMillis();
            wasDead = false;
        }
        boolean dead = player.isDeadOrDying();
        if (wasDead && !dead) aliveSince = Util.getMillis();
        wasDead = dead;
    }

    // "the_nether" of "minecraft:the_nether" as "The Nether".
    private static String dimensionName(LocalPlayer player) {
        String path = player.level().dimension().identifier().getPath();
        StringBuilder name = new StringBuilder();
        for (String word : path.substring(path.lastIndexOf('/') + 1).split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return name.toString();
    }

    private static String duration(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + " s";
        long minutes = seconds / 60;
        return minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min";
    }

    // --- Time ---

    private long elapsed() {
        return (System.nanoTime() - this.openedAt) / 1_000_000;
    }

    // How far a part has arrived, 0 to 1 and slowing into place; all there with the transitions turned off.
    private float arrived(long at, long length) {
        if (!this.animated) return 1;
        float t = Math.clamp((this.elapsed() - at) / (float) length, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // When the buttons can be pressed: once the game allows it (a second after opening), and not before the bar has filled.
    private long lockStart() {
        return this.animated ? SIDES_AT : 0;
    }

    private void cue(int index, long at, SoundEvent sound, float pitch, float volume) {
        if ((this.cues & 1 << index) != 0 || this.elapsed() < at) return;
        this.cues |= 1 << index;
        if (GlazedSettings.deathSounds()) this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // --- Layout ---

    private int bandTop() {
        return Math.max(8, (this.height - BAND_HEIGHT) / 2 - 16);
    }

    private int contentWidth() {
        return Math.min(this.width - 24, 420);
    }

    private int contentLeft() {
        return (this.width - this.contentWidth()) / 2;
    }

    private static void press(AbstractWidget widget) {
        widget.mouseClicked(new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(0, 0)), false);
    }

    @Override
    protected void init() {
        // The game's own buttons are made (first to respawn, then to leave) and taken off the screen: it goes on turning
        // them on and off itself, and the ones here follow them and press them.
        // Their labels are taken as plain text: the game hands them out dimmed while its buttons are still locked.
        super.init();
        List<Button> game = new ArrayList<>();
        for (GuiEventListener child : List.copyOf(this.children())) {
            if (child instanceof Button button && !(child instanceof FlatButton)) {
                game.add(button);
                this.removeWidget(button);
            }
        }
        this.gameRespawn = game.isEmpty() ? null : game.getFirst();
        this.gameTitle = game.size() < 2 ? null : game.get(1);

        int left = this.contentLeft();
        int width = this.contentWidth();
        int top = this.bandTop() + 9;
        int buttonX = left + width - BUTTON_WIDTH;
        this.respawn = null;
        this.toTitle = null;
        if (this.gameRespawn != null) {
            Button target = this.gameRespawn;
            this.respawn = this.addRenderableWidget(FlatButton.of(Component.literal(target.getMessage().getString()), BUTTON_WIDTH, button -> press(target))
                    .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.RESET));
            this.respawn.setPosition(buttonX, top);
        }
        if (this.gameTitle != null) {
            Button target = this.gameTitle;
            this.toTitle = this.addRenderableWidget(FlatButton.of(Component.literal(target.getMessage().getString()), BUTTON_WIDTH, button -> press(target)).style(FlatButton.Style.DANGER));
            this.toTitle.setPosition(buttonX, top + 23);
        }
        this.causeLines = this.cause == null ? List.of() : this.font.split(this.cause, Math.max(40, buttonX - 10 - (left + 40)));
        if (this.causeLines.size() > 2) this.causeLines = this.causeLines.subList(0, 2);

        // The details under the band, in rows centered under it.
        List<Chip> wanted = new ArrayList<>();
        wanted.add(this.chip(Component.translatableWithFallback("glazedmenu.death.score", "Score"), String.format(Locale.ROOT, "%,d", this.score), this.score));
        wanted.add(this.chip(Component.translatableWithFallback("glazedmenu.death.level", "Level lost"), Integer.toString(this.level), this.level));
        if (this.survivedMillis >= 1000) {
            wanted.add(this.chip(Component.translatableWithFallback("glazedmenu.death.survived", "Survived"), duration(this.survivedMillis), -1));
        }
        wanted.add(new Chip(null, null, -1, 0, 0, this.font.width(this.placeText) + 30));
        this.chips.clear();
        int y = this.bandTop() + BAND_HEIGHT + 8;
        int from = 0;
        while (from < wanted.size()) {
            int to = from;
            int rowWidth = 0;
            while (to < wanted.size() && (to == from || rowWidth + 5 + wanted.get(to).width <= this.width - 16)) {
                rowWidth += (to == from ? 0 : 5) + wanted.get(to).width;
                to++;
            }
            int x = (this.width - rowWidth) / 2;
            for (int i = from; i < to; i++) {
                Chip chip = wanted.get(i);
                this.chips.add(new Chip(chip.label, chip.value, chip.count, x, y, chip.width));
                x += chip.width + 5;
            }
            y += CHIP_HEIGHT + 5;
            from = to;
        }
        Chip place = this.chips.getLast();
        this.copy = this.addRenderableWidget(FlatButton.of(this.placeText, place.width, button -> this.copyPlace()).withIcon(ConfigIcons.COPY)
                .tooltip(Component.translatableWithFallback("glazedmenu.death.copy", "Copy the coordinates")));
        this.copy.setPosition(place.x, place.y);
        this.follow();
    }

    private Chip chip(Component label, String value, int count) {
        return new Chip(label, value, count, 0, 0, this.font.width(label) + 5 + this.font.width(value) + 14);
    }

    private void copyPlace() {
        this.minecraft.keyboardHandler.setClipboard(this.place);
        this.copiedTicks = 30;
        if (this.copy != null) this.copy.setMessage(Component.translatableWithFallback("glazedmenu.death.copied", "Copied"));
    }

    // The buttons here are on while the game's own are, and not before their bar has filled.
    private void follow() {
        boolean filled = this.elapsed() >= this.lockStart() + LOCK_FOR;
        if (this.respawn != null && this.gameRespawn != null) this.respawn.active = this.gameRespawn.active && filled;
        if (this.toTitle != null && this.gameTitle != null) this.toTitle.active = this.gameTitle.active && filled;
        if (this.copy != null) this.copy.active = !this.animated || this.elapsed() >= CHIPS_AT + CHIP_GAP * (this.chips.size() - 1);
    }

    @Override
    public void tick() {
        super.tick();
        this.follow();
        if (this.copiedTicks > 0 && --this.copiedTicks == 0 && this.copy != null) this.copy.setMessage(this.placeText);
    }

    // The band closes over the world once the screen is gone (the player respawned), where the transitions are on.
    @Override
    public void removed() {
        super.removed();
        if (this.animated && this.minecraft.level != null && this.elapsed() >= OPEN_AT + OPEN_FOR) {
            closeStart = System.nanoTime();
            closeSounded = false;
        }
    }

    @Override
    public Component getNarrationMessage() {
        return this.cause == null ? this.title : CommonComponents.joinForNarration(this.title, this.cause);
    }

    // --- Drawing ---

    // The game's own red over the world, coming on in a moment.
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        tint(graphics, this.width, this.height, this.animated ? Math.clamp(this.elapsed() / 600F, 0, 1) : 1);
    }

    private static void tint(GuiGraphicsExtractor graphics, int width, int height, float shown) {
        graphics.fillGradient(0, 0, width, height, ConfigStyle.withAlpha(0xFF500000, Math.round(0x60 * shown)), ConfigStyle.withAlpha(0xFF803030, Math.round(0xA0 * shown)));
    }

    // The band at a stage of its opening: a line growing from the middle (0 to 1), then opening to its height (0 to 1).
    private static void band(GuiGraphicsExtractor graphics, int width, int top, float drawn, float opened) {
        if (drawn <= 0) return;
        int half = Math.round(width / 2F * drawn);
        int height = Math.round(2 + (BAND_HEIGHT - 2) * opened);
        int y = top + (BAND_HEIGHT - height) / 2;
        int x = width / 2 - half;
        int edge = ConfigStyle.withAlpha(ConfigStyle.colors().error(), 0xE0);
        if (height > 2) {
            int body = ConfigStyle.colors().popup();
            graphics.fillGradient(x, y, x + half * 2, y + height, ConfigStyle.withAlpha(ConfigStyle.mix(body, 0xFF802020, 0.22F), 0xEC), ConfigStyle.withAlpha(body, 0xEC));
            graphics.fill(x, y + 1, x + half * 2, y + 2, 0x20FFFFFF);
            graphics.fill(x, y + height, x + half * 2, y + height + 3, 0x38000000);
        }
        graphics.fill(x, y, x + half * 2, y + 1, edge);
        graphics.fill(x, y + height - 1, x + half * 2, y + height, edge);
    }

    private void part(GuiGraphicsExtractor graphics, float arrived, float fromX, float fromY, Runnable draw) {
        if (arrived <= 0) return;
        GuiFade.set(arrived);
        graphics.pose().pushMatrix();
        graphics.pose().translate(fromX * (1 - arrived), fromY * (1 - arrived));
        try {
            draw.run();
        } finally {
            graphics.pose().popMatrix();
            GuiFade.reset();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ConfigStyle.use(this.theme);
        this.follow();
        long lockEnd = this.lockStart() + LOCK_FOR;
        this.cue(0, 0, SoundEvents.BELL_BLOCK, 0.5F, 0.55F);
        if (this.animated) {
            this.cue(1, LINE_AT, SoundEvents.UI_TOAST_IN, 0.6F, 0.7F);
            this.cue(2, OPEN_AT, SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.6F);
            for (int i = 0; i < this.chips.size() && i < 4; i++) {
                this.cue(3 + i, CHIPS_AT + CHIP_GAP * i, SoundEvents.NOTE_BLOCK_HAT.value(), 1 + 0.12F * i, 0.35F);
            }
        }
        this.cue(7, lockEnd, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 0.5F);

        int top = this.bandTop();
        float opened = this.arrived(OPEN_AT, OPEN_FOR);
        band(graphics, this.width, top, this.arrived(LINE_AT, LINE_FOR), opened);
        if (opened < 1) return;
        int left = this.contentLeft();
        int width = this.contentWidth();
        int inner = top + 9;

        // On the left: the skull and the title, then how the player died.
        this.part(graphics, this.arrived(TITLE_AT, PART_FOR), -18, 0, () -> {
            ConfigStyle.iconFrame(graphics, left, inner + 5, 30, 30, ConfigStyle.withAlpha(0xFFC83C3C, 0xD8));
            ConfigIcons.SKULL.draw(graphics, left + 6, inner + 11, 18, 0xFFFFFFFF);
            graphics.pose().pushMatrix();
            graphics.pose().translate(left + 39, inner + (this.causeLines.isEmpty() ? 12 : this.causeLines.size() == 1 ? 6 : 1));
            graphics.pose().scale(2, 2);
            graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), 0, 0, RED, true);
            graphics.pose().popMatrix();
        });
        this.part(graphics, this.arrived(SIDES_AT, PART_FOR), -18, 0, () -> {
            int y = inner + (this.causeLines.size() == 1 ? 27 : 21);
            for (FormattedCharSequence line : this.causeLines) {
                graphics.text(this.font, line, left + 40, y, ConfigStyle.colors().text(), true);
                y += 10;
            }
        });

        // On the right: the ways on, and along the bottom the bar that fills before they can be taken.
        float sides = this.arrived(SIDES_AT, PART_FOR);
        this.part(graphics, sides, 18, 0, () -> {
            if (this.respawn != null) this.respawn.extractRenderState(graphics, mouseX, mouseY, a);
            if (this.toTitle != null) this.toTitle.extractRenderState(graphics, mouseX, mouseY, a);
        });
        this.part(graphics, sides, 0, 0, () -> {
            int barY = top + BAND_HEIGHT - 8;
            float filled = Math.clamp((this.elapsed() - this.lockStart()) / (float) LOCK_FOR, 0, 1);
            ConfigStyle.rect(graphics, left, barY, width, 3, ConfigStyle.withAlpha(ConfigStyle.colors().field(), 0xC0));
            GlazedBrand.fill(graphics, left, barY, Math.round(width * filled), 3, 0xFF);
        });

        // Under the band: the details, one after another, the numbers counting up as they come.
        for (int i = 0; i < this.chips.size(); i++) {
            Chip chip = this.chips.get(i);
            long at = CHIPS_AT + CHIP_GAP * i;
            this.part(graphics, this.arrived(at, CHIP_FOR), 0, 10, () -> {
                if (chip.label == null) {
                    if (this.copy != null) this.copy.extractRenderState(graphics, mouseX, mouseY, a);
                    return;
                }
                ConfigStyle.rect(graphics, chip.x, chip.y, chip.width, CHIP_HEIGHT, ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xD8));
                ConfigStyle.outline(graphics, chip.x, chip.y, chip.width, CHIP_HEIGHT, ConfigStyle.colors().panelBorder());
                String value = chip.value;
                if (chip.count > 0 && this.animated) {
                    float counted = this.arrived(at, COUNT_FOR);
                    if (counted < 1) value = String.format(Locale.ROOT, "%,d", Math.round(chip.count * counted));
                }
                graphics.text(this.font, chip.label, chip.x + 7, chip.y + 6, ConfigStyle.colors().textMuted(), false);
                graphics.text(this.font, Component.literal(value).withStyle(ChatFormatting.BOLD), chip.x + chip.width - 7 - this.font.width(chip.value) - 1, chip.y + 6,
                        ConfigStyle.colors().text(), false);
            });
        }
    }

    /**
     * The end of a death screen, over the game and its HUD (GuiMixin) once the player has respawned: the band closes to
     * its line, the line draws back, and the red fades off the world. A screen opening over the game ends it.
     */
    public static void overWorld(GuiGraphicsExtractor graphics) {
        if (closeStart == 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        float progress = (System.nanoTime() - closeStart) / 1_000_000F / CLOSE_MILLIS;
        if (progress >= 1 || minecraft.screen != null || minecraft.level == null) {
            closeStart = 0;
            return;
        }
        if (!closeSounded) {
            closeSounded = true;
            if (GlazedSettings.deathSounds()) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_OUT, 0.8F, 0.7F));
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        ConfigStyle.use(ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        tint(graphics, width, height, 1 - progress * progress);
        float closing = Math.clamp(progress / 0.5F, 0, 1);
        float drawing = Math.clamp((progress - 0.5F) / 0.5F, 0, 1);
        band(graphics, width, Math.max(8, (height - BAND_HEIGHT) / 2 - 16), 1 - drawing * drawing, 1 - closing * closing * (3 - 2 * closing));
    }
}
