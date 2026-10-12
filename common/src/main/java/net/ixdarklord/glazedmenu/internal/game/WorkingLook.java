package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.internal.compat.TitleCompat;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.GuiFade;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.compat.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The look of every wait around a world or a server, from the first word of it to the world itself: the game's
 * one-line waits ("Reading world data", "Preparing for world creation", "Saving world"), the connecting to a server,
 * and the loading of the terrain. The world's icon inside a ring cut into one piece for each step of what's being done
 * (the done ones lit, the one under way breathing, or filling where the game says how far it is), the world's name and
 * the step under it, and under them the steps as a line of marks, each with its name and how long it took.
 * <p>
 * The game says each step only as it comes to it, on a screen of its own (GenericMessageScreenMixin,
 * ConnectScreenMixin and LevelLoadingScreenMixin draw this in their place), so the steps are gathered here across
 * those screens: one that follows another within a moment is the next step of the same wait, which is how the reading
 * of a world, the loading of its resources and the loading of its terrain are one line of marks. The steps still to
 * come are the ones such a wait is known to have; one the game says that wasn't expected is put into the line as it
 * comes.
 */
public final class WorkingLook {
    private static final ResourceLocation ICON = GlazedMenu.rl("working_icon");
    /** The step of the terrain's loading, which ends every wait into a world. */
    public static final String TERRAIN = "multiplayer.downloadingTerrain";
    /**
     * The step of a world of the player's own being made ready, before its terrain comes: on 1.20.1 a screen of its own
     * (the game's with the chunk map), where newer versions have one loading screen for both.
     */
    public static final String PREPARING = "menu.loadingLevel";
    private static final String SAVING = "menu.savingLevel";
    // The steps a wait is known to have, by the step it begins with.
    private static final Map<String, List<String>> FLOWS = Map.of(
            "selectWorld.data_read", List.of("selectWorld.data_read", PREPARING, TERRAIN),
            "createWorld.preparing", List.of("createWorld.preparing", PREPARING, TERRAIN),
            PREPARING, List.of(PREPARING, TERRAIN),
            SAVING, List.of(SAVING));
    // A step said this long after the last was drawn begins a wait of its own.
    private static final long SAME_WAIT_NANOS = 1_500_000_000L;
    private static final int RADIUS = 30;
    private static final int ICON_SIZE = 32;

    private static final class Step {
        final String key;
        Component label;
        long started;
        long ended;

        Step(String key, Component label) {
            this.key = key;
            this.label = label;
        }
    }

    private static final List<Step> steps = new ArrayList<>();
    private static int current = -1;
    private static long waitStarted;
    private static long lastDrawn;
    // The wait is over (the world was reached, or the player is back at a menu): its steps stay for the fading end of
    // it, and whatever is said next begins a new one.
    private static boolean finished;
    // The world the wait is about, as it was known when the wait began.
    private static @Nullable String name;
    private static @Nullable Path iconFile;
    private static @Nullable Path loadedIcon;
    private static @Nullable DynamicTexture icon;

    private WorkingLook() {}

    public static boolean active() {
        return GlazedSettings.replaceLoading();
    }

    /** The wait under way is over: the world was reached, or the player is back at a menu. */
    public static void finish() {
        finished = true;
    }

    private static String keyOf(Component title) {
        return title.getContents() instanceof TranslatableContents contents ? contents.getKey() : title.getString();
    }

    // The steps a wait beginning with this one is known to have: a server's connecting ends in the terrain too.
    private static List<String> plan(String key) {
        List<String> known = FLOWS.get(key);
        if (known != null) return known;
        return key.startsWith("connect.") ? List.of(key, TERRAIN) : List.of(key);
    }

    // The step the game says now: the next of the wait under way, or the first of a new one.
    private static void note(Component title) {
        long now = System.nanoTime();
        String key = keyOf(title);
        boolean under = !finished && current >= 0 && current < steps.size();
        if (under && steps.get(current).key.equals(key)) return;
        int index = -1;
        for (int i = current + 1; i < steps.size(); i++) {
            if (steps.get(i).key.equals(key)) {
                index = i;
                break;
            }
        }
        // A step the wait under way was coming to is its next step however long the game took to get there (it draws
        // nothing while it reads a world, so a long silence between two steps is the rule). Anything else begins a new
        // wait where none is under way, where this is the first step of a wait, or where the last one rested too long.
        if (!under || index < 0 && (FLOWS.containsKey(key) || now - lastDrawn > SAME_WAIT_NANOS)) {
            finished = false;
            steps.clear();
            for (String planned : plan(key)) steps.add(new Step(planned, Component.translatable(planned)));
            current = -1;
            index = 0;
            waitStarted = now;
            Minecraft minecraft = Minecraft.getInstance();
            name = LoadingLook.worldName(minecraft);
            iconFile = LoadingLook.worldIcon(minecraft);
        } else if (index < 0) {
            // Not a step this wait was known to have: it goes in after the one under way.
            index = current + 1;
            steps.add(index, new Step(key, title));
        } else {
            // The steps it was known to have and didn't come to are left out.
            for (int i = index - 1; i > current; i--) steps.remove(i);
            index = current + 1;
        }
        if (current >= 0) steps.get(current).ended = now;
        Step step = steps.get(index);
        step.label = title;
        step.started = now;
        current = index;
    }

    private static ConfigTheme theme() {
        return ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME);
    }

    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.1f s", nanos / 1_000_000_000.0);
    }

    // The world's own icon (the one its row in the list has), read once for each world.
    private static @Nullable DynamicTexture icon(Minecraft minecraft) {
        if (iconFile == null) return null;
        if (!iconFile.equals(loadedIcon)) {
            loadedIcon = iconFile;
            if (icon != null) {
                minecraft.getTextureManager().release(ICON);
                icon = null;
            }
            if (Files.isRegularFile(iconFile)) {
                try (InputStream stream = Files.newInputStream(iconFile)) {
                    icon = new DynamicTexture(NativeImage.read(stream));
                    minecraft.getTextureManager().register(ICON, icon);
                } catch (IOException | RuntimeException e) {
                    icon = null;
                }
            }
        }
        return icon;
    }

    /**
     * Behind it all: the game's panorama. It's the panorama from the first step of a wait to its last, also once the
     * world is there behind the screen (the terrain's loading, the saving of a world being left), so the backdrop is
     * one picture throughout. Newer versions blur it; 1.20.1 has no blur, so it's under a light tint of the shade
     * that stands in for the blur: the panorama is still plainly seen, and the ring and the steps read over it.
     */
    public static void background(GuiGraphicsExtractor graphics, int width, int height) {
        ConfigStyle.use(theme());
        TitleCompat.panorama(graphics, Minecraft.getInstance().getDeltaFrameTime());
        ConfigStyle.shade(graphics, width, height, 0.45F);
    }

    // A piece of the ring, from one angle to another (in turns, 0 at the top, clockwise): its shadow under it, then the
    // piece itself.
    private static void piece(GuiGraphicsExtractor graphics, float scale, int centerX, int centerY, float from, float to, int color, float shown) {
        raster(graphics, scale, centerX, centerY, from, to, color, shown, true);
        raster(graphics, scale, centerX, centerY, from, to, color, shown, false);
    }

    // The piece itself: a body inside an outline in a deeper shade of it, with a light along the body's outer side
    // where the light falls (the top left). Or its shadow: the same shape in a soft dark, a little down and right. It's
    // drawn in the window's own pixels, each one by how much of it the piece covers, so its curve and its ends are
    // smooth at any interface size; pixels of one color side by side are drawn as one.
    private static void raster(GuiGraphicsExtractor graphics, float scale, int centerX, int centerY, float from, float to, int color, float shown, boolean shadow) {
        float span = to - from;
        if (span <= 0) return;
        boolean whole = span >= 0.999F;
        float middle = RADIUS * scale;
        float half = 2 * scale;
        float edge = Math.max(1, scale * 0.75F);
        int outline = ConfigStyle.mix(color, 0xFF000000, 0.55F);
        float start = from - (float) Math.floor(from);
        int reach = (int) Math.ceil(middle + half + edge) + 1;
        float originX = centerX * scale + (shadow ? 0.75F * scale : 0);
        float originY = centerY * scale + (shadow ? 1.5F * scale : 0);
        for (int row = -reach; row <= reach; row++) {
            int runStart = 0;
            int runColor = 0;
            for (int column = -reach; column <= reach + 1; column++) {
                int pixel = 0;
                if (column <= reach) {
                    float dx = column + 0.5F;
                    float dy = row + 0.5F;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    float away = Math.abs(distance - middle);
                    if (away < half + edge + 0.5F) {
                        // How far inside the piece's two ends the pixel is, along the ring (in pixels).
                        float ends = Float.MAX_VALUE;
                        if (!whole) {
                            float turn = (float) (Math.atan2(dx, -dy) / (2 * Math.PI));
                            float along = turn - start;
                            along -= (float) Math.floor(along);
                            float around = (float) (2 * Math.PI) * distance;
                            ends = along <= span ? Math.min(along, span - along) * around : Math.max(span - along, along - 1) * around;
                        }
                        float inOutline = Mth.clamp(Math.min(half + edge - away, ends + edge) + 0.5F, 0, 1);
                        if (inOutline > 0) {
                            if (shadow) {
                                pixel = ConfigStyle.withAlpha(0xFF000000, Math.round(0x58 * shown * inOutline));
                            } else {
                                float inBody = Mth.clamp(Math.min(half - away, ends) + 0.5F, 0, 1);
                                // The light: a soft line along the body's outer side, strongest at the top left.
                                float line = Mth.clamp(1 - Math.abs(distance - (middle + half * 0.45F)) / (0.7F * scale), 0, 1);
                                float toward = Mth.clamp((-dx - dy) / distance * 0.6F + 0.3F, 0, 1);
                                int body = ConfigStyle.mix(color, 0xFFFFFFFF, line * toward * 0.7F);
                                pixel = ConfigStyle.withAlpha(ConfigStyle.mix(outline, body, inBody), Math.round(0xFF * shown * inOutline));
                            }
                        }
                    }
                }
                if (pixel != runColor) {
                    if (runColor >>> 24 != 0) {
                        graphics.fill(Math.round(originX) + runStart, Math.round(originY) + row, Math.round(originX) + column, Math.round(originY) + row + 1, runColor);
                    }
                    runStart = column;
                    runColor = pixel;
                }
            }
        }
    }

    // A mark of the line of steps, plain: a shadow under it, its body, and a light along its top.
    private static void node(GuiGraphicsExtractor graphics, int centerX, int y, int color) {
        int x = centerX - 4;
        ConfigStyle.rect(graphics, x + 1, y + 2, 9, 9, 0x50000000);
        ConfigStyle.rect(graphics, x, y + 1, 9, 9, color);
        graphics.fill(x + 1, y + 1, x + 8, y + 2, 0x70FFFFFF);
    }

    // The way from one mark to the next, plain: a line with a shadow under it and a light along its top.
    private static void way(GuiGraphicsExtractor graphics, int from, int to, int y, int color) {
        graphics.fill(from, y + 1, to, y + 3, 0x40000000);
        graphics.fill(from, y, to, y + 2, color);
        graphics.fill(from, y, to, y + 1, 0x38FFFFFF);
    }

    // The size a step's name is drawn at in its column: the font's own where the name fits on two lines, and the
    // largest smaller one (down to 60%) at which it does where it doesn't.
    private static float labelScale(Font font, Component label, int room) {
        for (float scale = 1; scale > 0.6F; scale -= 0.1F) {
            if (font.split(label, Math.round(room / scale)).size() <= 2) return scale;
        }
        return 0.6F;
    }

    // A line of text centered on a point, whole: smaller where it's too long for its room (cut short only below 60%).
    private static void fitted(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int room, int color) {
        int textWidth = font.width(text);
        if (textWidth <= room) {
            ConfigStyle.centeredText(graphics, font, text, centerX, y, room, color);
            return;
        }
        float scale = Math.max(0.6F, room / (float) textWidth);
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, y + 4 - 4 * scale);
        graphics.pose().scale(scale, scale);
        ConfigStyle.centeredText(graphics, font, text, 0, 0, Math.round(room / scale), color);
        graphics.pose().popMatrix();
    }

    /** The ring, the world and the steps, for the step the game's screen says. Gives back the bottom of what it drew. */
    public static int extract(GuiGraphicsExtractor graphics, Font font, int width, int height, Component title) {
        return extract(graphics, font, width, height, title, -1);
    }

    /**
     * As {@link #extract(GuiGraphicsExtractor, Font, int, int, Component)}, with how far the step is where the game says
     * (0 to 1; anything less for a step it doesn't measure).
     */
    public static int extract(GuiGraphicsExtractor graphics, Font font, int width, int height, Component title, float progress) {
        note(title);
        lastDrawn = System.nanoTime();
        return draw(graphics, font, width, height, progress, false);
    }

    /**
     * The wait under way as it stands, with no new step: for the blank screen 1.20.1 shows between two steps of a wait
     * (while it clears the world before, as one is opened). False, with nothing drawn, where no wait is under way.
     */
    public static boolean extractUnderWay(GuiGraphicsExtractor graphics, Font font, int width, int height) {
        if (finished || current < 0 || current >= steps.size()) return false;
        background(graphics, width, height);
        lastDrawn = System.nanoTime();
        draw(graphics, font, width, height, -1, false);
        return true;
    }

    /**
     * The wait as it ended, fading off the world it led to (LoadingLook, as the loading screen closes): every step
     * done, at an opacity.
     */
    static void extractDone(GuiGraphicsExtractor graphics, Font font, int width, int height, float opacity) {
        if (steps.isEmpty()) return;
        GuiFade.set(opacity);
        try {
            draw(graphics, font, width, height, -1, true);
        } finally {
            GuiFade.reset();
        }
    }

    private static int draw(GuiGraphicsExtractor graphics, Font font, int width, int height, float progress, boolean ended) {
        long now = System.nanoTime();
        Minecraft minecraft = Minecraft.getInstance();
        ConfigStyle.use(theme());
        int count = steps.size();
        int at = ended ? count : current;
        int centerX = width / 2;
        boolean line = count > 1;
        int accent = ConfigStyle.accent();
        float breath = 0.45F + 0.55F * (float) Math.abs(Math.sin(now / 1_000_000_000.0 * 2.6));
        int track = ConfigStyle.mix(ConfigStyle.colors().popup(), ConfigStyle.colors().textMuted(), 0.35F);

        // The line of steps is as tall as its longest name needs: a name goes onto two lines where it's too long for
        // its column, and is drawn smaller as well where two lines aren't enough.
        int column = line ? Mth.clamp((width - 24) / count, 56, 112) : 0;
        int rows = 1;
        if (line) for (Step each : steps) rows = Math.max(rows, Math.min(2, font.split(each.label, Math.round((column - 6) / labelScale(font, each.label, column - 6))).size()));
        int lineHeight = line ? 7 + 15 + rows * 9 + 11 + 5 : 0;
        int total = RADIUS * 2 + 10 + 12 + 12 + (line ? 12 + lineHeight : 0);
        int centerY = Math.max(RADIUS + 10, (height - total) / 2 + RADIUS);

        // The ring: a piece for each step; one step alone is a piece going round, or filling where it's measured.
        float scale = (float) minecraft.getWindow().getGuiScale();
        graphics.pose().pushMatrix();
        graphics.pose().scale(1 / scale, 1 / scale);
        if (count <= 1) {
            piece(graphics, scale, centerX, centerY, 0, 1, track, 0.85F);
            if (ended) {
                piece(graphics, scale, centerX, centerY, 0, 1, accent, 1);
            } else if (progress >= 0) {
                piece(graphics, scale, centerX, centerY, 0, Mth.clamp(progress, 0.02F, 1), accent, 1);
            } else {
                float turn = (float) (now / 1_600_000_000.0 % 1);
                piece(graphics, scale, centerX, centerY, turn, turn + 0.25F, accent, 1);
            }
        } else {
            float gap = 0.022F;
            for (int i = 0; i < count; i++) {
                float from = i / (float) count + gap;
                float to = (i + 1) / (float) count - gap;
                if (i < at) {
                    piece(graphics, scale, centerX, centerY, from, to, accent, 1);
                } else if (i > at) {
                    piece(graphics, scale, centerX, centerY, from, to, track, 0.85F);
                } else if (progress >= 0) {
                    // Measured: the piece fills as far as the step is.
                    piece(graphics, scale, centerX, centerY, from, to, track, 0.85F);
                    piece(graphics, scale, centerX, centerY, from, from + (to - from) * Mth.clamp(progress, 0.04F, 1), accent, 1);
                } else {
                    piece(graphics, scale, centerX, centerY, from, to, ConfigStyle.mix(track, accent, breath), 1);
                }
            }
        }
        graphics.pose().popMatrix();

        // The world's icon inside it, or a globe where it has none yet.
        int iconX = centerX - ICON_SIZE / 2;
        int iconY = centerY - ICON_SIZE / 2;
        if (icon(minecraft) != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, GuiFade.apply(0xFFFFFFFF));
            ConfigStyle.pictureFrame(graphics, iconX, iconY, ICON_SIZE, ICON_SIZE);
        } else {
            ConfigStyle.iconFrame(graphics, iconX, iconY, ICON_SIZE, ICON_SIZE, ConfigStyle.withAlpha(accent, 0xD0));
            ConfigIcons.GLOBE.draw(graphics, iconX + 8, iconY + 8, 16, ConfigStyle.readableOn(accent));
        }

        // The world's name, and the step under way with how long the wait has been (and how far it is, where it's
        // measured), on a plate of their own so they read over any picture.
        int y = centerY + RADIUS + 10;
        boolean named = name != null && !name.isBlank();
        Component label = ended ? Component.translatable("narrator.ready_to_play") : steps.get(current).label;
        String detail = (progress >= 0 && !ended ? Math.round(Mth.clamp(progress, 0, 1) * 100) + "%  ·  " : "") + seconds((ended ? lastDrawn : now) - waitStarted);
        Component status = Component.empty().append(label).append(Component.literal("  ·  " + detail).withStyle(ChatFormatting.GRAY));
        int plate = Math.min(width - 16, Math.max(font.width(status), named ? font.width(Component.literal(name).withStyle(ChatFormatting.BOLD)) : 0) + 20);
        ConfigStyle.rect(graphics, centerX - plate / 2, y - 4, plate, (named ? 24 : 12) + 7, ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xC0));
        if (named) {
            GlazedBrand.text(graphics, font, name, centerX - font.width(Component.literal(name).withStyle(ChatFormatting.BOLD)) / 2, y);
            y += 12;
        }
        fitted(graphics, font, status, centerX, y, plate - 12, ConfigStyle.colors().text());
        y += 12;

        // The steps as a line of marks on a strip of glass: done with its time, under way, and still to come.
        if (line) {
            y += 12;
            int left = centerX - column * count / 2;
            ConfigStyle.panel(graphics, left - 6, y - 7, column * count + 12, lineHeight);
            for (int i = 0; i < count; i++) {
                Step each = steps.get(i);
                int x = left + column * i + column / 2;
                boolean done = i < at;
                boolean under = i == at;
                if (i < count - 1) way(graphics, x + 8, x + column - 8, y + 5, done ? accent : track);
                node(graphics, x, y, done ? accent : under ? ConfigStyle.mix(track, accent, breath) : track);
                if (done) ConfigIcons.CHECK.draw(graphics, x - 3, y + 2, 7, ConfigStyle.readableOn(accent));
                float small = labelScale(font, each.label, column - 6);
                List<FormattedCharSequence> text = font.split(each.label, Math.round((column - 6) / small));
                int color = under ? ConfigStyle.colors().text() : ConfigStyle.colors().textDim();
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y + 15);
                graphics.pose().scale(small, small);
                for (int row = 0; row < Math.min(2, text.size()); row++) {
                    graphics.text(font, text.get(row), -font.width(text.get(row)) / 2, row * 9, color, true);
                }
                graphics.pose().popMatrix();
                long end = each.ended != 0 ? each.ended : lastDrawn;
                String time = each.started == 0 ? null : done ? seconds(end - each.started) : under ? seconds(now - each.started) : null;
                if (time != null) graphics.text(font, time, x - font.width(time) / 2, y + 15 + rows * 9 + 1, ConfigStyle.colors().textDim(), true);
            }
            y += lineHeight - 7;
        }
        if (!ended && current >= 0 && steps.get(current).key.equals(SAVING)) {
            ConfigStyle.centeredText(graphics, font, Component.translatableWithFallback("glazedmenu.working.keep_open", "Don't close the game"), centerX, height - 22, width - 24,
                    ConfigStyle.colors().textDim());
        }
        return y;
    }
}
