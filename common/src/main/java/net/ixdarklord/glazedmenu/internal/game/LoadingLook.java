package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.GuiFade;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.Locale;

/**
 * What the screens shown while a world or a server is joined and left need to know and do beyond their drawing
 * (WorkingLook draws them): what's being joined and what's being played (its name, its icon, how long), the fading of
 * the wait's look off the world it led to, and the map of the terrain's chunks. The game's own screens stay the game's
 * (they do the connecting and the loading); mixins draw them this way.
 */
public final class LoadingLook {
    private static final int MARGIN = 10;
    // What's being joined, told by Glazed Menu's own screens as they start it (before the game knows).
    private static @Nullable String expectedKey;
    private static @Nullable String expectedName;
    // The folder of the world being opened, and the name and icon of the world of the player's own last played: kept
    // past its closing, for the screen shown while it's saved (WorkingLook).
    private static @Nullable String expectedLevel;
    private static @Nullable String playedName;
    private static @Nullable Path playedIcon;
    // The map of the terrain's chunks: the loading it's of, and when its first chunk came (0 before one has).
    private static WeakReference<ChunkLoadStatusView> chunksView = new WeakReference<>(null);
    private static long chunksSince;
    private static final float CHUNKS_FADE_MILLIS = 180;
    // The server last connected to and when its world was first seen (0 before it is), for the screen shown when the
    // connection ends (GlazedDisconnectedScreen).
    private static @Nullable ServerData lastServer;
    private static long sessionStart;
    // When the loading ended and its look began to fade off the world (in nanoseconds); 0 while nothing fades.
    private static long fadeStart;
    private static final float FADE_MILLIS = 1100;
    // The loading screen as it last looked (a picture of the frame, taken a few times a second as the terrain nears
    // its end), for the fade to dissolve off the world from; and whether the fade under way is from it.
    private static final Identifier LAST_FRAME = GlazedMenu.rl("loading_end");
    private static final long KEEP_EVERY_NANOS = 120_000_000L;
    private static final long FRAME_FRESH_NANOS = 1_500_000_000L;
    private static @Nullable DynamicTexture lastFrame;
    private static int lastFrameWidth;
    private static int lastFrameHeight;
    private static long lastFrameAt;
    private static long lastFrameAsked;
    private static boolean fadeFromFrame;

    private LoadingLook() {}

    /** Keeps what the screen shows now (the loading screen, drawn the frame before), no more often than a few times a second. */
    public static void keepFrame() {
        if (!GlazedSettings.replaceLoading() || !GlazedSettings.transitions()) return;
        long now = System.nanoTime();
        if (now - lastFrameAsked < KEEP_EVERY_NANOS) return;
        lastFrameAsked = now;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            RenderTarget target = minecraft.getMainRenderTarget();
            Screenshot.takeScreenshot(target, Math.max(1, target.width / 1280), image -> minecraft.execute(() -> {
                // One that comes once the dissolve has begun is let go: the one on the screen is being drawn from, and
                // taking its place would close it under the frame that still holds it.
                if (fadeStart != 0) {
                    image.close();
                    return;
                }
                dropFrame();
                lastFrameWidth = Math.max(1, image.getWidth());
                lastFrameHeight = Math.max(1, image.getHeight());
                lastFrame = new DynamicTexture(() -> "Glazed Menu loading screen", image);
                minecraft.getTextureManager().register(LAST_FRAME, lastFrame);
                lastFrameAt = System.nanoTime();
            }));
        } catch (RuntimeException e) {
            GlazedMenu.LOGGER.debug("Couldn't keep the loading screen's frame: {}", e.toString());
        }
    }

    private static void dropFrame() {
        if (lastFrame != null) {
            Minecraft.getInstance().getTextureManager().release(LAST_FRAME);
            lastFrame = null;
        }
    }

    public static String worldKey(String levelId) {
        return "world:" + levelId;
    }

    public static String serverKey(String address) {
        return "server:" + address.toLowerCase(Locale.ROOT);
    }

    /** Says what's about to be joined, so its name is there from the first screen of the joining. */
    public static void expect(String key, @Nullable String name) {
        // A name told before (by the list the joining began from) is kept when the same thing is told of again without one.
        if (name == null && key.equals(expectedKey)) return;
        expectedKey = key;
        expectedName = name;
    }

    /** A server being connected to (ConnectScreenMixin): kept for the screen shown if the connection ends. */
    public static void connecting(ServerData server) {
        lastServer = server;
        sessionStart = 0;
    }

    /** The server last connected to; none once the player is back at a list or the title, or in a world of their own. */
    public static @Nullable ServerData lastServer() {
        return lastServer;
    }

    /** How long the world last joined has been played, in milliseconds; 0 when none was reached. */
    public static long playedMillis() {
        return sessionStart == 0 ? 0 : Util.getMillis() - sessionStart;
    }

    /** The name of the world or server being joined, or of the one being played; none where neither is known. */
    static @Nullable String worldName(Minecraft minecraft) {
        if (expectedName != null) return expectedName;
        String now = currentName(minecraft);
        if (now != null) return now;
        return expectedLevel != null ? expectedLevel : playedName;
    }

    /** The file of the icon of the world being opened, or of the one being played (or just left); none for a server. */
    static @Nullable Path worldIcon(Minecraft minecraft) {
        if (expectedLevel != null) return minecraft.getLevelSource().getBaseDir().resolve(expectedLevel).resolve("icon.png");
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) return minecraft.getSingleplayerServer().getWorldPath(LevelResource.ICON_FILE);
        return playedIcon;
    }

    /** A world being opened by its folder (WorldOpenFlowsMixin): however it's opened, its name and icon are there from the start. */
    public static void expectWorld(String levelId) {
        lastServer = null;
        sessionStart = 0;
        expectedLevel = levelId;
        expect(worldKey(levelId), null);
    }

    /** The key of the world or server being played ("world:<folder>" or "server:<address>"); none where neither is known. */
    static @Nullable String playingKey(Minecraft minecraft) {
        return currentKey(minecraft);
    }

    private static @Nullable String currentKey(Minecraft minecraft) {
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            Path root = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return root.getFileName() != null ? worldKey(root.getFileName().toString()) : null;
        }
        ServerData server = minecraft.getCurrentServer();
        return server != null ? serverKey(server.ip) : null;
    }

    private static @Nullable String currentName(Minecraft minecraft) {
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) return minecraft.getSingleplayerServer().getWorldData().getLevelName();
        ServerData server = minecraft.getCurrentServer();
        return server != null ? server.name : null;
    }

    /**
     * Every screen about to open passes through here (and is handed back as it is): the connecting screen closing onto
     * the world begins the fade, and a screen that isn't part of a joining forgets what was being joined.
     */
    public static @Nullable Screen watch(@Nullable Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        // The connecting screen closing straight onto the world (a server that has it ready at once): it fades off it,
        // as the terrain's loading screen does when that's the last of the joining (LevelLoadingScreenMixin).
        if (screen == null && minecraft.screen instanceof ConnectScreen && minecraft.level != null) fadeOut();
        if (screen instanceof TitleScreen || screen instanceof SelectWorldScreen || screen instanceof JoinMultiplayerScreen) {
            expectedKey = null;
            expectedName = null;
            expectedLevel = null;
            WorkingLook.finish();
            PlayTimes.flush();
            playedName = null;
            playedIcon = null;
            lastServer = null;
            sessionStart = 0;
        }
        return screen;
    }

    /** Called as each frame's world has been drawn (GameRendererMixin): counts the time played, and notes the world being played. */
    public static void afterLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (sessionStart == 0) sessionStart = Util.getMillis();
        PlayTimes.frame(currentKey(minecraft));
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            if (playedName == null) {
                playedName = minecraft.getSingleplayerServer().getWorldData().getLevelName();
                playedIcon = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ICON_FILE);
            }
        } else {
            playedName = null;
            playedIcon = null;
        }
    }

    /** The loading is over and its screen has closed: from now its look fades off the world, instead of a cut. */
    public static void fadeOut() {
        if (!GlazedSettings.replaceLoading() || !GlazedSettings.transitions()) return;
        fadeStart = System.nanoTime();
        fadeFromFrame = lastFrame != null && fadeStart - lastFrameAt < FRAME_FRESH_NANOS;
    }

    /**
     * The fading end of a loading, over the game and its HUD (GuiMixin). It's no screen: the game is the player's from
     * the moment it's ready, and this only draws over it for a moment. A screen opening over the game ends it.
     */
    public static void overWorld(GuiGraphicsExtractor graphics) {
        if (fadeStart == 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        float progress = (System.nanoTime() - fadeStart) / 1_000_000F / FADE_MILLIS;
        if (progress >= 1 || minecraft.screen != null || minecraft.level == null) {
            fadeStart = 0;
            dropFrame();
            return;
        }
        // Slow to start (it holds a beat before it goes) and slow to end, with no step at either.
        float left = 1 - progress * progress * progress * (progress * (progress * 6 - 15) + 10);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // The loading screen dissolves off the world: the screen as it last looked (its panorama, its ring and its
        // steps), growing clearer till only the world is there.
        if (fadeFromFrame && lastFrame != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, LAST_FRAME, 0, 0, 0, 0, width, height, lastFrameWidth, lastFrameHeight, lastFrameWidth, lastFrameHeight,
                    ConfigStyle.withAlpha(0xFFFFFFFF, Math.round(0xFF * left)));
            return;
        }
        // No picture of it (the terrain came before one could be kept): a shade in its place, with the ring and the
        // steps, all of them done.
        ConfigStyle.use(ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
        graphics.fill(0, 0, width, height, ConfigStyle.withAlpha(ConfigStyle.mix(0xFF000000, ConfigStyle.accent(), 0.12F), Math.round(0xC8 * left)));
        WorkingLook.extractDone(graphics, minecraft.font, width, height, left);
    }

    /** The map of the chunks being made, as the game draws it, in a small panel of its own with its bottom at {@code panelTop}, at the right. */
    public static void chunks(GuiGraphicsExtractor graphics, int width, int panelTop, ChunkLoadStatusView view) {
        // It's there once the terrain has begun to come (the first chunk it knows of), fading in fast; before that it
        // would be an empty box.
        long now = System.nanoTime();
        if (chunksView.get() != view) {
            chunksView = new WeakReference<>(view);
            chunksSince = 0;
        }
        if (chunksSince == 0) {
            int radius = view.radius();
            search:
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (view.get(x, z) != null) {
                        chunksSince = now;
                        break search;
                    }
                }
            }
            if (chunksSince == 0) return;
        }
        float shown = Math.clamp((now - chunksSince) / 1_000_000F / CHUNKS_FADE_MILLIS, 0, 1);
        GuiFade.set(shown);
        try {
            chunkMap(graphics, width, panelTop, view);
        } finally {
            GuiFade.reset();
        }
    }

    private static void chunkMap(GuiGraphicsExtractor graphics, int width, int panelTop, ChunkLoadStatusView view) {
        int size = (view.radius() * 2 + 1) * 2;
        int box = size + 10;
        int x = width - MARGIN - box;
        int y = panelTop - 6 - box;
        if (y < MARGIN) return;
        ConfigStyle.bar(graphics, x, y, box, box);
        LevelLoadingScreen.extractChunksForRendering(graphics, x + box / 2, y + box / 2, 2, 0, view);
    }
}
