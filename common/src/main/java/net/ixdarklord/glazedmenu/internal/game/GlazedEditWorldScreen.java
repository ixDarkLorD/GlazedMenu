package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.internal.mixin.EditWorldScreenAccessor;
import net.ixdarklord.glazedmenu.internal.compat.Compat;
import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.ixdarklord.glazedmenu.internal.compat.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.Util;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Editing a world, in place of the game's own screen: the world's name, and what the game lets be done with the world
 * as cards under headings (its files, its backups, its upkeep); beside them the world itself (its picture, game mode
 * and difficulty, its version and its size), over Save and Cancel.
 * <p>
 * It's a face on the game's own screen, which it keeps: the name is typed into that screen's own box, and each card
 * presses that screen's own button, so a backup, an optimizing or a renaming is done exactly as the game does it. The
 * game's screen is a button away, for what a mod adds to it.
 */
public final class GlazedEditWorldScreen extends GameListScreen {
    private static final Class<?> VANILLA = net.minecraft.client.gui.screens.worldselection.EditWorldScreen.class;
    private static final int PICTURE = 36;
    // 1.20.1's list of worlds keeps its way of writing a date to itself: this is the same one.
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
    // The face of the game's screen now open, so that screen coming back (from the question before optimizing) comes
    // back as its face.
    private static WeakReference<GlazedEditWorldScreen> current = new WeakReference<>(null);
    // The world about to be edited, told by Glazed Menu's list of worlds as it opens the game's screen for it.
    private static @Nullable LevelSummary expected;

    private final EditWorldScreen game;
    // The world, where the list of worlds told of it; the game's screen keeps it to itself.
    private final @Nullable LevelSummary level;
    private String name;
    // Whether the name is the one to keep: 1.20.1's screen has no box to read it from before it's laid out.
    private boolean named;
    private @Nullable FaviconTexture picture;
    private @Nullable CompletableFuture<Long> size;
    private @Nullable FlatButton saveButton;
    // The player asked for the game's own screen: it's left alone from then on.
    private boolean gameShown;

    private record Action(ConfigIcons.Icon icon, int color, Component title, @Nullable Component note, String button) {}

    private GlazedEditWorldScreen(@Nullable Screen parent, EditWorldScreen game, @Nullable LevelSummary level) {
        super(Component.translatable("selectWorld.edit.title"), parent, ConfigIcons.EDIT);
        this.game = game;
        this.level = level;
        this.name = level != null ? level.getLevelName() : "";
    }

    /** Says which world the game's screen is about to be opened for, so the face can show the world. */
    public static void expect(LevelSummary level) {
        expected = level;
    }

    /** The screen to open instead: this one for the game's own screen for editing a world, while the player has it on. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof EditWorldScreen game) || screen.getClass() != VANILLA) return screen;
        LevelSummary level = expected;
        expected = null;
        if (!GlazedSettings.replaceEditWorld()) return screen;
        GlazedEditWorldScreen face = current.get();
        if (face != null && face.game == game) return face.gameShown ? screen : face;
        face = new GlazedEditWorldScreen(Minecraft.getInstance().screen, game, level);
        current = new WeakReference<>(face);
        return face;
    }

    // --- The game's own screen ---

    private static @Nullable String key(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    private @Nullable EditBox gameNameBox() {
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof EditBox box) return box;
        }
        return null;
    }

    private @Nullable AbstractWidget gameButton(String name) {
        for (GuiEventListener child : this.game.children()) {
            if (child instanceof AbstractWidget widget && name.equals(key(widget))) return widget;
        }
        return null;
    }

    // Presses one of the game's screen's buttons, by the name the game gives it.
    private void pressGame(String name) {
        // 1.20.1's own Backup button also closes its screen: the backup is made as that button makes it, and the
        // screen stays, as on newer versions.
        if (name.equals("selectWorld.edit.backup")) {
            EditWorldScreen.makeBackupAndShowToast(((EditWorldScreenAccessor) this.game).glazedmenu$levelAccess());
            return;
        }
        AbstractWidget widget = this.gameButton(name);
        if (widget instanceof AbstractButton button && button.active) button.onPress();
    }

    private void setName(String name) {
        this.name = name;
        // Into the game's own box, which the game's Save reads (and which turns Save off for a blank name).
        EditBox box = this.gameNameBox();
        if (box != null && !box.getValue().equals(name)) box.setValue(name);
        this.updateSave();
    }

    private void updateSave() {
        AbstractWidget save = this.gameButton("selectWorld.edit.save");
        if (this.saveButton != null) this.saveButton.active = save == null || save.active;
    }

    // --- Layout ---

    @Override
    protected boolean hasSearch() {
        return false;
    }

    @Override
    protected boolean hasBottomBar() {
        return false;
    }

    @Override
    protected Component searchName() {
        return Component.empty();
    }

    @Override
    protected Component searchHint() {
        return Component.empty();
    }

    @Override
    protected void initButtons() {
        // The game's screen is laid out too: its buttons are pressed for what it does itself.
        Compat.init(this.game, this.width, this.height);
        // 1.20.1's screen makes its box anew each time it's laid out, with the world's name as it's saved: what was
        // typed here is put back into it.
        EditBox box = this.gameNameBox();
        if (box != null) {
            if (!this.named) {
                this.name = box.getValue();
                this.named = true;
            } else if (!box.getValue().equals(this.name)) {
                box.setValue(this.name);
            }
        }

        FlatButton own = this.addRenderableWidget(FlatButton.icon(ConfigIcons.LIST,
                Component.translatableWithFallback("glazedmenu.edit.game_screen", "The game's own screen"), button -> {
                    this.gameShown = true;
                    this.minecraft.setScreen(this.game);
                }));
        own.setPosition(this.headerRight - 20, this.topBarWidgetY(20));
        this.headerRight = own.getX() - 8;

        // The pane beside the actions: Save over Cancel, at its foot.
        int left = this.detailsLeft() + 8;
        int width = this.detailsWidth() - 16;
        int bottom = this.bodyBottom() - 8;
        FlatButton cancel = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_CANCEL, width, button -> this.game.onClose()));
        cancel.setPosition(left, bottom - 20);
        this.saveButton = this.addRenderableWidget(new FlatButton(width, 24, Component.translatable("selectWorld.edit.save"),
                button -> this.pressGame("selectWorld.edit.save")).style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        this.saveButton.setPosition(left, cancel.getY() - 4 - 24);
        this.updateSave();
    }

    @Override
    protected void onChosen() {}

    @Override
    public void onClose() {
        this.game.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        if (this.picture != null) Compat.close(this.picture);
        this.picture = null;
    }

    @Override
    protected List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        StyledEditBox box = new StyledEditBox(this.font, 100, 18, Component.translatable("selectWorld.enterName"));
        box.setValue(this.name);
        box.setResponder(this::setName);
        rows.add(new NameRow(box));
        rows.add(new Heading(Component.translatableWithFallback("glazedmenu.edit.section.files", "Files")));
        rows.add(new ActionsRow(
                new Action(ConfigIcons.FOLDER, GlazedBrand.identity(0xFFF2A33B), Component.translatable("selectWorld.edit.openFolder"), null, "selectWorld.edit.openFolder"),
                new Action(ConfigIcons.RESET, GlazedBrand.identity(0xFF4DA3FF), Component.translatable("selectWorld.edit.resetIcon"), null, "selectWorld.edit.resetIcon")));
        rows.add(new Heading(Component.translatableWithFallback("glazedmenu.edit.section.backups", "Backups")));
        rows.add(new ActionsRow(
                new Action(ConfigIcons.COPY, GlazedBrand.identity(0xFF5BC467), Component.translatable("selectWorld.edit.backup"), null, "selectWorld.edit.backup"),
                new Action(ConfigIcons.FOLDER, GlazedBrand.identity(0xFF3CC8B4), Component.translatable("selectWorld.edit.backupFolder"), null, "selectWorld.edit.backupFolder")));
        rows.add(new Heading(Component.translatableWithFallback("glazedmenu.edit.section.upkeep", "Maintenance")));
        rows.add(new ActionsRow(
                new Action(ConfigIcons.RESTART, GlazedBrand.identity(0xFFB07CF0), Component.translatable("selectWorld.edit.optimize"),
                        Component.translatableWithFallback("glazedmenu.edit.optimize.note", "Upgrades every chunk to this version"), "selectWorld.edit.optimize"), null));
        return rows;
    }

    // --- The world ---

    private @Nullable ResourceLocation picture(LevelSummary level) {
        if (this.picture == null) {
            this.picture = FaviconTexture.forWorld(this.minecraft.getTextureManager(), level.getLevelId());
            Path file = level.getIcon();
            boolean read = false;
            if (file != null && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                try (InputStream stream = Files.newInputStream(file)) {
                    this.picture.upload(NativeImage.read(stream));
                    read = true;
                } catch (Throwable t) {
                    GlazedMenu.LOGGER.error("Invalid icon for world {}", level.getLevelId(), t);
                }
            }
            if (!read) this.picture.clear();
        }
        ResourceLocation texture = this.picture.textureLocation();
        return texture.getPath().startsWith("worlds/") ? texture : null;
    }

    // The world's size on disk: asked for once, added up off the main thread.
    private @Nullable String size(LevelSummary level) {
        if (this.size == null) {
            Path folder = this.minecraft.getLevelSource().getBaseDir().resolve(level.getLevelId());
            this.size = CompletableFuture.supplyAsync(() -> {
                try (Stream<Path> files = Files.walk(folder)) {
                    return files.filter(Files::isRegularFile).mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException e) {
                            return 0;
                        }
                    }).sum();
                } catch (IOException | RuntimeException e) {
                    return -1L;
                }
            }, Util.ioPool());
        }
        Long bytes = this.size.getNow(null);
        if (bytes == null || bytes < 0) return null;
        if (bytes >= 1L << 30) return String.format(Locale.ROOT, "%.1f GB", bytes / (double) (1L << 30));
        if (bytes >= 1L << 20) return String.format(Locale.ROOT, "%.0f MB", bytes / (double) (1L << 20));
        return String.format(Locale.ROOT, "%.0f KB", Math.max(1, bytes / 1024.0));
    }

    // --- Drawing ---

    @Override
    protected Component subtitle() {
        return this.level != null ? Component.literal(this.name + " · saves/" + this.level.getLevelId()) : Component.literal(this.name);
    }

    @Override
    protected void extractDetails(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        int bottom = y + height - 52;
        LevelSummary level = this.level;
        Component name = Component.literal(this.name).withStyle(ChatFormatting.BOLD);
        if (level == null) {
            // Opened without Glazed Menu's list of worlds: only what's typed is known.
            ConfigStyle.text(graphics, this.font, name, x, y, width, ConfigStyle.colors().text());
            return;
        }
        this.extractPicture(graphics, this.picture(level), ConfigIcons.GLOBE, GlazedBrand.identity(0xFF5BC467), x, y, PICTURE);
        int textX = x + PICTURE + 7;
        int textWidth = width - PICTURE - 7;
        ConfigStyle.text(graphics, this.font, name, textX, y + 3, textWidth, ConfigStyle.colors().text());
        ConfigStyle.text(graphics, this.font, Component.literal(level.getLevelId()), textX, y + 14, textWidth, ConfigStyle.colors().textMuted());
        long played = level.getLastPlayed();
        if (played != -1L) {
            String when = DATE_FORMAT.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(played), ZoneId.systemDefault()));
            ConfigStyle.text(graphics, this.font, Component.literal(when), textX, y + 25, textWidth, ConfigStyle.colors().textMuted());
        }
        int lineY = y + PICTURE + 7;
        if (lineY + 11 > bottom) return;
        Component mode = level.isHardcore() ? Component.translatable("gameMode.hardcore") : Component.translatable("gameMode." + level.getGameMode().getName());
        int tagX = x + ConfigStyle.badge(graphics, this.font, mode, x, lineY, level.isHardcore() ? ConfigStyle.colors().error() : ConfigStyle.colors().success()) + 4;
        Component difficulty = level.getSettings().difficulty().getDisplayName();
        if (tagX + this.font.width(difficulty) + 8 <= x + width) ConfigStyle.badge(graphics, this.font, difficulty, tagX, lineY, ConfigStyle.colors().warning());
        lineY += 17;
        String size = this.size(level);
        Component[] names = {
                Component.translatableWithFallback("glazedmenu.worlds.detail.version", "Version"),
                Component.translatableWithFallback("glazedmenu.worlds.detail.size", "Size"),
                Component.translatableWithFallback("glazedmenu.create.commands", "Commands")};
        Component[] values = {
                level.getWorldVersionName(),
                Component.literal(size != null ? size : "…"),
                level.hasCheats() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF};
        for (int i = 0; i < names.length; i++) {
            if (lineY + 11 > bottom) break;
            lineY = this.detailLine(graphics, names[i], values[i], x, lineY, width, ConfigStyle.colors().text());
        }
    }

    // --- The rows ---

    // The world's name: a label over the box it's typed in.
    private final class NameRow extends Row {
        private final StyledEditBox box;

        NameRow(StyledEditBox box) {
            this.box = box;
        }

        @Override
        protected int height() {
            return 13 + 18 + 5;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.box);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.box);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            ConfigStyle.text(graphics, GlazedEditWorldScreen.this.font, Component.translatable("selectWorld.enterName").withStyle(ChatFormatting.BOLD),
                    this.getX() + 1, this.getY() + 2, this.getWidth(), ConfigStyle.colors().textDim());
            this.box.setWidth(this.getWidth() - 2);
            this.box.setPosition(this.getX() + 1, this.getY() + 13);
            Compat.extractRenderState(this.box, graphics, mouseX, mouseY, a);
        }
    }

    // One or two of the game's actions side by side, each a card in its own color pressing the game's own button; greyed
    // where the game's button is (no icon to reset).
    private final class ActionsRow extends Row {
        private final List<Action> actions = new ArrayList<>();

        ActionsRow(Action first, @Nullable Action second) {
            this.actions.add(first);
            if (second != null) this.actions.add(second);
        }

        private int cardWidth() {
            return (this.getWidth() - 4 * (this.actions.size() - 1)) / this.actions.size();
        }

        private int at(double mouseX, double mouseY) {
            if (mouseY < this.getY() + 1 || mouseY >= this.getY() + this.getHeight() - 2) return -1;
            for (int i = 0; i < this.actions.size(); i++) {
                int x = this.getX() + i * (this.cardWidth() + 4);
                if (mouseX >= x && mouseX < x + this.cardWidth()) return i;
            }
            return -1;
        }

        private boolean active(Action action) {
            AbstractWidget button = GlazedEditWorldScreen.this.gameButton(action.button);
            return button != null && button.active;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            GlazedEditWorldScreen screen = GlazedEditWorldScreen.this;
            Font font = screen.font;
            int over = hovered ? this.at(mouseX, mouseY) : -1;
            int width = this.cardWidth();
            int y = this.getY() + 1;
            int height = this.getHeight() - 3;
            for (int i = 0; i < this.actions.size(); i++) {
                Action action = this.actions.get(i);
                boolean active = this.active(action);
                boolean lit = active && i == over;
                int color = active ? action.color : ConfigStyle.colors().textMuted();
                int x = this.getX() + i * (width + 4);
                ConfigStyle.rect(graphics, x, y, width, height, ConfigStyle.mix(ConfigStyle.colors().button(), color, lit ? 0.24F : 0.08F));
                ConfigStyle.outline(graphics, x, y, width, height, ConfigStyle.withAlpha(color, lit ? 0xE0 : 0x60));
                graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ConfigStyle.withAlpha(color, lit ? 0xFF : 0xB0));
                int box = height - 10;
                ConfigStyle.iconFrame(graphics, x + 5, y + 6, box, box, ConfigStyle.withAlpha(color, lit ? 0xFF : 0xD0));
                action.icon.draw(graphics, x + 5 + (box - 12) / 2, y + 6 + (box - 12) / 2, 12, ConfigStyle.readableOn(color));
                int textX = x + 5 + box + 6;
                int textWidth = x + width - 6 - textX;
                // The backup's card says how much there is to back up.
                Component note = action.note;
                if (note == null && action.button.equals("selectWorld.edit.backup") && screen.level != null) {
                    String size = screen.size(screen.level);
                    if (size != null) note = Component.literal(size);
                }
                Component title = action.title.copy().withStyle(ChatFormatting.BOLD);
                int titleColor = active ? ConfigStyle.colors().text() : ConfigStyle.colors().textMuted();
                if (note != null) {
                    ConfigStyle.text(graphics, font, title, textX, y + 6, textWidth, titleColor);
                    ConfigStyle.text(graphics, font, note, textX, y + 17, textWidth, ConfigStyle.colors().textDim());
                } else {
                    ConfigStyle.text(graphics, font, title, textX, y + 2 + (height - 2 - 8) / 2, textWidth, titleColor);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            GlazedEditWorldScreen screen = GlazedEditWorldScreen.this;
            int index = this.at(event.x(), event.y());
            if (index < 0 || !this.active(this.actions.get(index))) return false;
            screen.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            screen.pressGame(this.actions.get(index).button);
            return true;
        }
    }
}
