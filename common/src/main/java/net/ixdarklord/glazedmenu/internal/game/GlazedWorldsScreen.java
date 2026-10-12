package net.ixdarklord.glazedmenu.internal.game;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.ConfirmPopup;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ErrorScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.validation.ContentValidationException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * The worlds, in place of the game's singleplayer screen: the list down the left (the favorites first), searched by a
 * world's name or folder and sorted by when it was last played or by name, and the chosen world's details beside it:
 * its picture, game mode and difficulty, its version, when it was last played, its size on disk, and what stands in the
 * way of playing it. Under them, the game's own actions: play, edit, re-create and delete, and a new world.
 */
public final class GlazedWorldsScreen extends GameListScreen {
    private static final String VANILLA = "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen";
    private static final int KEY_F5 = 294;
    private static final int PICTURE = 40;
    // The order last chosen, kept while the game runs.
    private static Sort sort = Sort.LAST_PLAYED;

    private CompletableFuture<List<LevelSummary>> pending;
    // The worlds once they've been read; null while they're being read.
    private @Nullable List<LevelSummary> levels;
    // Each world's picture, read as its row is first drawn; closed as the screen is left.
    private final Map<String, FaviconTexture> pictures = new HashMap<>();
    // Each world's size on disk, added up off the main thread once it's first chosen.
    private final Map<String, CompletableFuture<Long>> sizes = new HashMap<>();
    private @Nullable FlatButton sortButton;
    private @Nullable FlatButton playButton;
    private @Nullable FlatButton editButton;
    private @Nullable FlatButton recreateButton;
    private @Nullable FlatButton deleteButton;
    private @Nullable FlatButton favoriteButton;

    private enum Sort {
        LAST_PLAYED,
        NAME;

        Component label() {
            return this == NAME ? Component.translatableWithFallback("glazedmenu.worlds.sort.name", "By Name")
                    : Component.translatableWithFallback("glazedmenu.worlds.sort.last_played", "Last Played");
        }
    }

    private GlazedWorldsScreen(@Nullable Screen parent) {
        super(Component.translatable("selectWorld.title"), parent, ConfigIcons.GLOBE);
        this.pending = this.loadLevels();
    }

    /** The screen to open instead: this one for the game's own singleplayer screen, over the screen that opened it. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (!(screen instanceof SelectWorldScreen) || !screen.getClass().getName().equals(VANILLA) || !GlazedSettings.replaceWorlds()) return screen;
        Screen current = Minecraft.getInstance().screen;
        // Asked for again from itself (a flow of the game's that goes "back to the list"): the same list, read again.
        if (current instanceof GlazedWorldsScreen worlds) {
            worlds.reload();
            return worlds;
        }
        return new GlazedWorldsScreen(current);
    }

    // --- The worlds ---

    // As the game's own list reads them.
    private CompletableFuture<List<LevelSummary>> loadLevels() {
        Minecraft minecraft = Minecraft.getInstance();
        LevelStorageSource.LevelCandidates candidates;
        try {
            candidates = minecraft.getLevelSource().findLevelCandidates();
        } catch (LevelStorageException e) {
            GlazedMenu.LOGGER.error("Couldn't load level list", e);
            minecraft.setScreen(new ErrorScreen(Component.translatable("selectWorld.unable_to_load"), e.getMessageComponent()));
            return CompletableFuture.completedFuture(List.of());
        }
        return minecraft.getLevelSource().loadLevelSummaries(candidates).exceptionally(throwable -> {
            minecraft.delayCrash(CrashReport.forThrowable(throwable, "Couldn't load level list"));
            return List.of();
        });
    }

    private void reload() {
        this.pending = this.loadLevels();
        this.levels = null;
        this.sizes.clear();
        this.closePictures();
        this.rebuildRows(true);
    }

    // Back from one of the game's screens (a world made, edited or deleted): the worlds are read again.
    private void returnHere() {
        this.reload();
        this.minecraft.setScreen(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.levels != null || !this.pending.isDone()) return;
        try {
            this.levels = this.pending.getNow(List.of());
        } catch (CompletionException | java.util.concurrent.CancellationException e) {
            this.levels = List.of();
        }
        // With no world yet, the game goes straight to making one.
        if (this.levels.isEmpty() && this.minecraft.screen == this) {
            CreateWorldScreen.openFresh(this.minecraft, () -> this.minecraft.setScreen(this.parent));
            return;
        }
        // The world played last is the one to hand.
        if (this.chosen == null && !this.levels.isEmpty()) {
            this.chosen = this.levels.stream().max(Comparator.comparingLong(LevelSummary::getLastPlayed)).map(LevelSummary::getLevelId).orElse(null);
        }
        this.rebuildRows(false);
    }

    private @Nullable LevelSummary chosenLevel() {
        if (this.levels == null || this.chosen == null) return null;
        for (LevelSummary level : this.levels) {
            if (level.getLevelId().equals(this.chosen)) return level;
        }
        return null;
    }

    @Override
    protected List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        if (this.levels == null) {
            rows.add(new Notice(ConfigIcons.GLOBE, Component.translatableWithFallback("glazedmenu.worlds.loading", "Reading the worlds…")));
            return rows;
        }
        String query = this.query.trim().toLowerCase(Locale.ROOT);
        Comparator<LevelSummary> order = sort == Sort.NAME
                ? Comparator.comparing(level -> level.getLevelName().toLowerCase(Locale.ROOT))
                : Comparator.comparingLong(LevelSummary::getLastPlayed).reversed();
        List<LevelSummary> found = this.levels.stream()
                .filter(level -> query.isEmpty() || level.getLevelName().toLowerCase(Locale.ROOT).contains(query) || level.getLevelId().toLowerCase(Locale.ROOT).contains(query))
                .sorted(order).toList();
        List<LevelSummary> favorites = found.stream().filter(level -> GameMarks.isFavoriteWorld(level.getLevelId())).toList();
        if (!favorites.isEmpty()) {
            rows.add(new Heading(Component.translatableWithFallback("glazedmenu.list.favorites", "Favorites")));
            for (LevelSummary level : favorites) rows.add(new WorldRow(level));
            if (favorites.size() < found.size()) rows.add(new Heading(Component.translatableWithFallback("glazedmenu.worlds.section.all", "Worlds")));
        }
        for (LevelSummary level : found) {
            if (!favorites.contains(level)) rows.add(new WorldRow(level));
        }
        if (found.isEmpty()) rows.add(new Notice(ConfigIcons.SEARCH, Component.translatableWithFallback("glazedmenu.worlds.none", "No worlds match")));
        return rows;
    }

    // A world's picture, read the first time it's asked for; null for a world without one.
    private @Nullable Identifier picture(LevelSummary level) {
        FaviconTexture picture = this.pictures.get(level.getLevelId());
        if (picture == null) {
            picture = FaviconTexture.forWorld(this.minecraft.getTextureManager(), level.getLevelId());
            this.pictures.put(level.getLevelId(), picture);
            Path file = level.getIcon();
            boolean read = false;
            if (file != null && Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                try (InputStream stream = Files.newInputStream(file)) {
                    picture.upload(NativeImage.read(stream));
                    read = true;
                } catch (Throwable t) {
                    GlazedMenu.LOGGER.error("Invalid icon for world {}", level.getLevelId(), t);
                }
            }
            if (!read) picture.clear();
        }
        Identifier texture = picture.textureLocation();
        return texture.getPath().startsWith("worlds/") ? texture : null;
    }

    private void closePictures() {
        for (FaviconTexture picture : this.pictures.values()) {
            if (!picture.isClosed()) picture.close();
        }
        this.pictures.clear();
    }

    @Override
    public void removed() {
        super.removed();
        this.closePictures();
    }

    // A world's size on disk: asked for once, added up off the main thread.
    private @Nullable Long size(LevelSummary level) {
        Path folder = this.minecraft.getLevelSource().getBaseDir().resolve(level.getLevelId());
        CompletableFuture<Long> size = this.sizes.computeIfAbsent(level.getLevelId(), id -> CompletableFuture.supplyAsync(() -> {
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
        }, Util.ioPool()));
        Long bytes = size.getNow(null);
        return bytes != null && bytes >= 0 ? bytes : null;
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1L << 30) return String.format(Locale.ROOT, "%.1f GB", bytes / (double) (1L << 30));
        if (bytes >= 1L << 20) return String.format(Locale.ROOT, "%.0f MB", bytes / (double) (1L << 20));
        return String.format(Locale.ROOT, "%.0f KB", Math.max(1, bytes / 1024.0));
    }

    private static @Nullable String lastPlayed(LevelSummary level) {
        long time = level.getLastPlayed();
        if (time == -1L) return null;
        return WorldSelectionList.DATE_FORMAT.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()));
    }

    // What stands in the way of playing a world, or asks for care first; null for a world that just opens.
    private static @Nullable Component caution(LevelSummary level) {
        if (level instanceof LevelSummary.SymlinkLevelSummary || level instanceof LevelSummary.CorruptedLevelSummary) return level.getInfo();
        if (level.isLocked()) return Component.translatable("selectWorld.locked");
        if (level.requiresManualConversion()) return Component.translatable("selectWorld.conversion.tooltip");
        if (!level.isCompatible()) return Component.translatable("selectWorld.incompatible.tooltip");
        if (level.shouldBackup()) {
            if (level.isDowngrade()) {
                return Component.translatable("selectWorld.tooltip.fromNewerVersion1").append(" ").append(Component.translatable("selectWorld.tooltip.fromNewerVersion2"));
            }
            if (level.requiresFileFixing()) return Component.translatable("selectWorld.requiresFileFixingTooltip.play");
            if (!SharedConstants.getCurrentVersion().stable()) {
                return Component.translatable("selectWorld.tooltip.snapshot1").append(" ").append(Component.translatable("selectWorld.tooltip.snapshot2"));
            }
        }
        return null;
    }

    // Whether the caution keeps the world from being played (red), or only warns (amber).
    private static boolean blocked(LevelSummary level) {
        return level.isDisabled() || level.isDowngrade() || level instanceof LevelSummary.SymlinkLevelSummary || level instanceof LevelSummary.CorruptedLevelSummary;
    }

    // --- Layout ---

    @Override
    protected Component searchName() {
        return Component.translatableWithFallback("glazedmenu.worlds.search", "Search worlds");
    }

    @Override
    protected Component searchHint() {
        return Component.translatableWithFallback("glazedmenu.worlds.search.hint", "Search worlds… (Ctrl+F)");
    }

    @Override
    protected void initButtons() {
        // The top bar: the order, left of the search.
        int sortWidth = Math.max(this.font.width(Sort.LAST_PLAYED.label()), this.font.width(Sort.NAME.label())) + 14;
        this.sortButton = this.addRenderableWidget(FlatButton.of(sort.label(), sortWidth, button -> {
            sort = sort == Sort.LAST_PLAYED ? Sort.NAME : Sort.LAST_PLAYED;
            button.setMessage(sort.label());
            this.rebuildRows(false);
        }).tooltip(Component.translatableWithFallback("glazedmenu.worlds.sort.tooltip", "The worlds played last first, or by name")));
        this.sortButton.setPosition(this.headerRight - sortWidth, this.topBarWidgetY(20));
        this.headerRight = this.sortButton.getX() - 8;
        this.sortButton.visible = this.sortButton.getX() > this.frameLeft() + 120;

        // The details pane: the star at its top, the world's actions along its bottom.
        int left = this.detailsLeft() + 8;
        int width = this.detailsWidth() - 16;
        int y = this.bodyBottom() - 8 - 20;
        this.favoriteButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.STAR_OUTLINE, CommonComponents.EMPTY, button -> {
            LevelSummary level = this.chosenLevel();
            if (level == null) return;
            GameMarks.toggleFavoriteWorld(level.getLevelId());
            this.rebuildRows(true);
        }));
        this.favoriteButton.setPosition(left + width - 20, this.bodyTop() + 6);
        this.deleteButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.TRASH, Component.translatable("selectWorld.delete"), button -> this.delete())
                .style(FlatButton.Style.DANGER));
        this.deleteButton.setPosition(left + width - 20, y);
        this.recreateButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.COPY, Component.translatable("selectWorld.recreate"), button -> this.recreate())
                .style(FlatButton.Style.NORMAL));
        this.recreateButton.setPosition(this.deleteButton.getX() - 4 - 20, y);
        this.editButton = this.addRenderableWidget(FlatButton.icon(ConfigIcons.EDIT, Component.translatable("selectWorld.edit"), button -> this.edit())
                .style(FlatButton.Style.NORMAL));
        this.editButton.setPosition(this.recreateButton.getX() - 4 - 20, y);
        this.playButton = this.addRenderableWidget(FlatButton.of(LevelSummary.PLAY_WORLD, this.editButton.getX() - 4 - left, button -> this.play())
                .style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.PLAY));
        this.playButton.setPosition(left, y);

        // The bottom bar: a new world and the worlds' folder, and the way back.
        int barY = this.barWidgetY(this.bottomBarY());
        Component create = Component.translatable("selectWorld.create");
        int createWidth = this.font.width(create) + 28;
        FlatButton createButton = this.addRenderableWidget(FlatButton.of(create, createWidth,
                button -> CreateWorldScreen.openFresh(this.minecraft, this::returnHere)).withIcon(ConfigIcons.PLUS));
        createButton.setPosition(this.frameLeft() + 4, barY);
        FlatButton folder = this.addRenderableWidget(FlatButton.icon(ConfigIcons.FOLDER,
                Component.translatableWithFallback("glazedmenu.worlds.folder.tooltip", "Open the worlds' folder"),
                button -> Util.getPlatform().openPath(this.minecraft.getLevelSource().getBaseDir())).style(FlatButton.Style.NORMAL));
        folder.setPosition(createButton.getX() + createWidth + 4, barY);
        FlatButton back = this.addRenderableWidget(FlatButton.of(CommonComponents.GUI_BACK, 72, button -> this.onClose()));
        back.setPosition(this.frameLeft() + this.frameWidth() - 4 - 72, barY);
    }

    @Override
    protected void onChosen() {
        if (this.playButton == null || this.editButton == null || this.recreateButton == null || this.deleteButton == null || this.favoriteButton == null) return;
        LevelSummary level = this.chosenLevel();
        this.playButton.visible = this.editButton.visible = this.recreateButton.visible = this.deleteButton.visible = this.favoriteButton.visible = level != null;
        if (level == null) return;
        // As the game's own screen enables them.
        // The game's own words for it where the button has room for them, else just Play (with them as its tooltip).
        Component action = level.primaryActionMessage();
        boolean fits = this.font.width(action) + 26 <= this.playButton.getWidth();
        this.playButton.setMessage(fits ? action : Component.translatableWithFallback("glazedmenu.worlds.play", "Play"));
        this.playButton.active = level.primaryActionActive();
        this.editButton.active = level.canEdit();
        this.recreateButton.active = level.canRecreate();
        this.deleteButton.active = level.canDelete();
        this.playButton.setTooltip(level.requiresFileFixing() ? Tooltip.create(Component.translatable("selectWorld.requiresFileFixingTooltip.play"))
                : fits ? null : Tooltip.create(action));
        boolean favorite = GameMarks.isFavoriteWorld(level.getLevelId());
        this.favoriteButton.withIcon(favorite ? ConfigIcons.STAR : ConfigIcons.STAR_OUTLINE);
        this.favoriteButton.iconColor(favorite ? 0xFFF2C94C : 0);
        this.favoriteButton.tooltip(favorite ? Component.translatableWithFallback("glazedmenu.list.favorite.remove", "Remove from favorites")
                : Component.translatableWithFallback("glazedmenu.list.favorite.add", "Add to favorites"));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (super.keyPressed(event)) return true;
        if (event.key() == KEY_F5) {
            this.reload();
            return true;
        }
        return false;
    }

    // --- The world's actions, as the game's own list does them ---

    private void play() {
        LevelSummary level = this.chosenLevel();
        if (level == null || !level.primaryActionActive()) return;
        if (level instanceof LevelSummary.SymlinkLevelSummary) {
            this.minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.setScreen(this)));
        } else {
            LoadingLook.expect(LoadingLook.worldKey(level.getLevelId()), level.getLevelName());
            this.minecraft.createWorldOpenFlows().openWorld(level.getLevelId(), this::returnHere);
        }
    }

    private void delete() {
        LevelSummary level = this.chosenLevel();
        if (level == null || !level.canDelete()) return;
        this.minecraft.setScreen(new ConfirmPopup(this, this.theme, Component.translatable("selectWorld.deleteQuestion"),
                Component.translatable("selectWorld.deleteWarning", level.getLevelName()), Component.translatable("selectWorld.deleteButton"), true, confirmed -> {
            if (!confirmed) {
                this.minecraft.setScreen(this);
                return;
            }
            this.minecraft.setScreen(new ProgressScreen(true));
            String levelId = level.getLevelId();
            try (LevelStorageSource.LevelStorageAccess access = this.minecraft.getLevelSource().createAccess(levelId)) {
                access.deleteLevel();
            } catch (IOException e) {
                SystemToast.onWorldDeleteFailure(this.minecraft, levelId);
                GlazedMenu.LOGGER.error("Failed to delete world {}", levelId, e);
            }
            this.returnHere();
        }));
    }

    private void edit() {
        LevelSummary level = this.chosenLevel();
        if (level == null || !level.canEdit()) return;
        this.minecraft.setScreenAndShow(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
        String levelId = level.getLevelId();
        LevelStorageSource.LevelStorageAccess access;
        try {
            access = this.minecraft.getLevelSource().validateAndCreateAccess(levelId);
        } catch (IOException e) {
            SystemToast.onWorldAccessFailure(this.minecraft, levelId);
            GlazedMenu.LOGGER.error("Failed to access level {}", levelId, e);
            this.returnHere();
            return;
        } catch (ContentValidationException e) {
            GlazedMenu.LOGGER.warn("{}", e.getMessage());
            this.minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.setScreen(this)));
            return;
        }
        EditWorldScreen editScreen;
        try {
            editScreen = EditWorldScreen.create(this.minecraft, access, result -> {
                access.safeClose();
                this.returnHere();
            });
        } catch (IOException | NbtException | ReportedNbtException e) {
            access.safeClose();
            SystemToast.onWorldAccessFailure(this.minecraft, levelId);
            GlazedMenu.LOGGER.error("Failed to load world data {}", levelId, e);
            this.returnHere();
            return;
        }
        // Its face (Glazed Menu's screen for editing a world) is told which world this is.
        GlazedEditWorldScreen.expect(level);
        this.minecraft.setScreen(editScreen);
    }

    private void recreate() {
        LevelSummary level = this.chosenLevel();
        if (level == null || !level.canRecreate()) return;
        this.minecraft.setScreenAndShow(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
        try (LevelStorageSource.LevelStorageAccess access = this.minecraft.getLevelSource().validateAndCreateAccess(level.getLevelId())) {
            Pair<LevelSettings, WorldCreationContext> recreated = this.minecraft.createWorldOpenFlows().recreateWorldData(access);
            LevelSettings settings = recreated.getFirst();
            WorldCreationContext context = recreated.getSecond();
            Path dataPacks = CreateWorldScreen.createTempDataPackDirFromExistingWorld(access.getLevelPath(LevelResource.DATAPACK_DIR), this.minecraft);
            context.validate();
            if (context.options().isOldCustomizedWorld()) {
                this.minecraft.setScreen(new ConfirmScreen(
                        result -> this.minecraft.setScreen(result ? CreateWorldScreen.createFromExisting(this.minecraft, this::returnHere, settings, context, dataPacks) : this),
                        Component.translatable("selectWorld.recreate.customized.title"), Component.translatable("selectWorld.recreate.customized.text"),
                        CommonComponents.GUI_PROCEED, CommonComponents.GUI_CANCEL));
            } else {
                this.minecraft.setScreen(CreateWorldScreen.createFromExisting(this.minecraft, this::returnHere, settings, context, dataPacks));
            }
        } catch (ContentValidationException e) {
            GlazedMenu.LOGGER.warn("{}", e.getMessage());
            this.minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.setScreen(this)));
        } catch (Exception e) {
            GlazedMenu.LOGGER.error("Unable to recreate world", e);
            this.minecraft.setScreen(new AlertScreen(() -> this.minecraft.setScreen(this),
                    Component.translatable("selectWorld.recreate.error.title"), Component.translatable("selectWorld.recreate.error.text")));
        }
    }

    // --- Drawing ---

    @Override
    protected Component subtitle() {
        if (this.levels == null) return Component.translatableWithFallback("glazedmenu.worlds.loading", "Reading the worlds…");
        LevelSummary last = this.levels.stream().max(Comparator.comparingLong(LevelSummary::getLastPlayed)).orElse(null);
        if (last == null) return Component.translatableWithFallback("glazedmenu.worlds.status", "%s worlds", this.levels.size());
        return this.levels.size() == 1 ? Component.translatableWithFallback("glazedmenu.worlds.status.one", "1 world · last played %s", last.getLevelName())
                : Component.translatableWithFallback("glazedmenu.worlds.status.last", "%s worlds · last played %s", this.levels.size(), last.getLevelName());
    }

    @Override
    protected void extractDetails(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        LevelSummary level = this.chosenLevel();
        if (level == null) {
            ConfigStyle.centeredText(graphics, this.font, Component.translatableWithFallback("glazedmenu.worlds.pick", "Pick a world"),
                    x + width / 2, y + height / 2 - 4, width, ConfigStyle.colors().textMuted());
            return;
        }
        int bottom = y + height - 26;
        this.extractPicture(graphics, this.picture(level), ConfigIcons.GLOBE, GlazedBrand.identity(0xFF5BC467), x, y, PICTURE);
        int textX = x + PICTURE + 7;
        // The name beside the picture (leaving the star its corner), the folder under it.
        int textWidth = width - PICTURE - 7 - 20;
        ConfigStyle.text(graphics, this.font, Component.literal(level.getLevelName()).withStyle(ChatFormatting.BOLD), textX, y + 4, textWidth, ConfigStyle.colors().text());
        ConfigStyle.text(graphics, this.font, Component.literal(level.getLevelId()), textX, y + 16, width - PICTURE - 7, ConfigStyle.colors().textMuted());
        String played = lastPlayed(level);
        if (played != null) ConfigStyle.text(graphics, this.font, Component.literal(played), textX, y + 27, width - PICTURE - 7, ConfigStyle.colors().textMuted());

        int lineY = y + PICTURE + 8;
        // The game mode, the difficulty and what else marks the world, as tags, wrapping.
        int tagX = x;
        for (Tag tag : tags(level)) {
            int tagWidth = this.font.width(tag.text) + 8;
            if (tagX > x && tagX + tagWidth > x + width) {
                tagX = x;
                lineY += 14;
            }
            if (lineY + 11 > bottom) break;
            tagX += ConfigStyle.badge(graphics, this.font, tag.text, tagX, lineY, tag.color) + 4;
        }
        lineY += 18;

        if (lineY + 11 <= bottom) {
            lineY = this.detailLine(graphics, Component.translatableWithFallback("glazedmenu.worlds.detail.version", "Version"), level.getWorldVersionName(), x, lineY, width,
                    level.isDowngrade() || !level.isCompatible() ? ConfigStyle.colors().error() : ConfigStyle.colors().text());
        }
        Long size = this.size(level);
        if (lineY + 11 <= bottom) {
            lineY = this.detailLine(graphics, Component.translatableWithFallback("glazedmenu.worlds.detail.size", "Size"),
                    size != null ? Component.literal(formatSize(size)) : Component.literal("…"), x, lineY, width, ConfigStyle.colors().text());
        }
        // How long it's been played, as Glazed Menu has counted it.
        String timesKey = LoadingLook.worldKey(level.getLevelId());
        if (PlayTimes.total(timesKey) > 0) {
            if (lineY + 11 <= bottom) {
                lineY = this.detailLine(graphics, Component.translatableWithFallback("glazedmenu.detail.last_session", "Last session"), PlayTimes.format(PlayTimes.lastSession(timesKey)), x, lineY, width,
                        ConfigStyle.colors().text());
            }
            if (lineY + 11 <= bottom) {
                lineY = this.detailLine(graphics, Component.translatableWithFallback("glazedmenu.detail.total_time", "Time played"), PlayTimes.format(PlayTimes.total(timesKey)), x, lineY, width,
                        ConfigStyle.colors().text());
            }
        }
        // What stands in the way, in the room that's left.
        Component caution = caution(level);
        if (caution != null) {
            int color = blocked(level) ? ConfigStyle.colors().error() : ConfigStyle.colors().warning();
            lineY += 4;
            List<FormattedCharSequence> lines = this.font.split(caution.copy().withStyle(style -> style.withColor(color & 0xFFFFFF)), width - 14);
            if (lineY + 10 <= bottom) ConfigIcons.WARNING.draw(graphics, x, lineY - 1, color);
            for (FormattedCharSequence line : lines) {
                if (lineY + 10 > bottom) break;
                graphics.text(this.font, line, x + 14, lineY, color, false);
                lineY += 10;
            }
        }
    }

    private record Tag(Component text, int color) {}

    private static List<Tag> tags(LevelSummary level) {
        List<Tag> tags = new ArrayList<>();
        if (level instanceof LevelSummary.SymlinkLevelSummary || level instanceof LevelSummary.CorruptedLevelSummary) return tags;
        tags.add(level.isHardcore() ? new Tag(Component.translatable("gameMode.hardcore"), ConfigStyle.colors().error())
                : new Tag(Component.translatable("gameMode." + level.getGameMode().getName()), ConfigStyle.colors().success()));
        tags.add(new Tag(level.getSettings().difficultySettings().difficulty().getDisplayName(), ConfigStyle.colors().warning()));
        if (level.hasCommands()) tags.add(new Tag(Component.translatable("selectWorld.commands"), ConfigStyle.colors().textDim()));
        if (level.isExperimental()) tags.add(new Tag(Component.translatable("selectWorld.experimental"), ConfigStyle.colors().modified()));
        return tags;
    }

    // A world: its picture, its name over what the game says of it, and the star of a favorite; a mark where something
    // stands in the way of playing it.
    private final class WorldRow extends EntryRow {
        private final LevelSummary level;

        WorldRow(LevelSummary level) {
            this.level = level;
        }

        @Override
        protected Object key() {
            return this.level.getLevelId();
        }

        @Override
        protected void open() {
            GlazedWorldsScreen.this.choose(this.level.getLevelId());
            GlazedWorldsScreen.this.play();
        }

        @Override
        protected void extractEntry(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean chosen, boolean hovered, int mouseX, int mouseY) {
            GlazedWorldsScreen screen = GlazedWorldsScreen.this;
            int pictureY = y + (height - ROW_ICON) / 2;
            screen.extractPicture(graphics, screen.picture(this.level), ConfigIcons.GLOBE, GlazedBrand.identity(0xFF5BC467), x + 3, pictureY, ROW_ICON);
            int right = x + width - 6;
            Component caution = caution(this.level);
            if (caution != null) {
                right -= 12;
                ConfigIcons.WARNING.draw(graphics, right, y + (height - ConfigIcons.SIZE) / 2, blocked(this.level) ? ConfigStyle.colors().error() : ConfigStyle.colors().warning());
                if (hovered && mouseX >= right - 2 && mouseX < right + 12) {
                    graphics.setTooltipForNextFrame(screen.font, screen.font.split(caution, 180), mouseX, mouseY);
                }
            }
            if (GameMarks.isFavoriteWorld(this.level.getLevelId())) {
                right -= 13;
                ConfigIcons.STAR.draw(graphics, right, y + (height - ConfigIcons.SIZE) / 2, 0xFFF2C94C);
            }
            int textX = x + 3 + ROW_ICON + 6;
            int textWidth = right - 4 - textX;
            ConfigStyle.text(graphics, screen.font, Component.literal(this.level.getLevelName()).withStyle(ChatFormatting.BOLD), textX, y + 5, textWidth, ConfigStyle.colors().text());
            ConfigStyle.text(graphics, screen.font, this.level.getInfo(), textX, y + 16, textWidth, ConfigStyle.colors().textDim());
        }
    }
}
