package net.ixdarklord.glazedmenu.internal.gui.setup;

import net.ixdarklord.glazedmenu.internal.compat.Compat;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.game.GameLanguageScreen;
import net.ixdarklord.glazedmenu.internal.game.GameOptionsScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedServersScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedTitleScreen;
import net.ixdarklord.glazedmenu.internal.game.GlazedWorldsScreen;
import net.ixdarklord.glazedmenu.internal.keys.GameControlsScreen;
import net.ixdarklord.glazedmenu.internal.keys.GlazedKeysScreen;
import net.ixdarklord.glazedmenu.internal.modlist.GlazedModsScreen;
import net.ixdarklord.glazedmenu.internal.style.GuiFade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The setup's pictures of the game's screens, drawn live: the screen itself, made once and laid out at the setup's own
 * size, drawn scaled down into a box each frame it's in view, so it's exactly what the player will get (in the colors
 * chosen, at their window's size). Only screens that need no world or server can be drawn this way; the others keep
 * their drawn pictures (SetupPreview).
 * <p>
 * A screen made here is never opened: it isn't added, so it starts nothing (no LAN search, no pings), and the full-window
 * parts of drawing it (the panorama, the blur) are skipped while it's drawn (ScreenPreviewMixin, ConfigStyle), the setup
 * showing the real ones behind it.
 * <p>
 * The game's own screens with a scrolling list (the worlds, the key binds, the languages, the controls) keep their drawn
 * pictures too: 1.21 clips a list where it would be on the window, not where it's drawn scaled down.
 */
public final class LivePreview {
    private static boolean drawing;

    private final Screen setup;
    private final Map<String, @Nullable Entry> entries = new HashMap<>();
    private final Set<String> failed = new HashSet<>();

    /** A screen made for a preview, with the settings it's made and drawn under (a layout to show). */
    private record Entry(Screen screen, @Nullable Runnable enter, @Nullable Runnable exit) {}

    LivePreview(Screen setup) {
        this.setup = setup;
    }

    /** Whether a preview is being drawn now: the full-window parts of drawing a screen are skipped meanwhile. */
    public static boolean drawing() {
        return drawing;
    }

    /** Draws a screen's variant live into a box; false where it can't be (it needs a world), for a drawn picture instead. */
    boolean draw(GuiGraphicsExtractor graphics, String shot, int variant, int x, int y, int width, int height, float a) {
        Entry entry = this.entry(shot, variant);
        if (entry == null) return false;
        Screen screen = entry.screen;
        int screenWidth = this.setup.width;
        int screenHeight = this.setup.height;
        float fade = GuiFade.get();
        graphics.enableScissor(x, y, x + width, y + height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(width / (float) screenWidth, height / (float) screenHeight);
        if (entry.enter != null) entry.enter.run();
        drawing = true;
        // The screen sets and resets the fade as it draws: the setup's own (a page sliding in) stays under it.
        GuiFade.setBase(fade);
        try {
            if (screen.width != screenWidth || screen.height != screenHeight) Compat.init(screen, screenWidth, screenHeight);
            // (1.21's screens draw their background as they draw.)
            Compat.extractRenderState(screen, graphics, -1, -1, a);
        } catch (RuntimeException e) {
            // A screen that can't be drawn here is drawn as a picture from then on.
            GlazedMenu.LOGGER.warn("Couldn't draw the setup's preview of {}", shot, e);
            this.failed.add(shot + variant);
            this.entries.remove(shot + variant);
        } finally {
            drawing = false;
            GuiFade.setBase(1);
            GuiFade.set(fade);
            if (entry.exit != null) entry.exit.run();
            graphics.pose().popMatrix();
            graphics.disableScissor();
        }
        return !this.failed.contains(shot + variant);
    }

    /**
     * Ticks the previews that fill in as they tick (the title screen's last-played world, the list of worlds). None of
     * them acts on a tick unless it's the screen open (the worlds list going on to Create World with no world).
     */
    void tick() {
        for (Entry entry : this.entries.values()) {
            if (entry == null || !(entry.screen instanceof GlazedTitleScreen || entry.screen instanceof TitleScreen || entry.screen instanceof GlazedWorldsScreen)) continue;
            if (entry.enter != null) entry.enter.run();
            try {
                entry.screen.tick();
            } catch (RuntimeException e) {
                GlazedMenu.LOGGER.debug("A setup preview didn't tick", e);
            } finally {
                if (entry.exit != null) entry.exit.run();
            }
        }
    }

    /** Lets go of every preview (the setup closing). */
    void clear() {
        for (Entry entry : this.entries.values()) {
            if (entry != null && opens(entry.screen)) entry.screen.removed();
        }
        this.entries.clear();
    }

    private static boolean opens(Screen screen) {
        return screen instanceof GlazedTitleScreen;
    }

    private @Nullable Entry entry(String shot, int variant) {
        String key = shot + variant;
        if (this.failed.contains(key)) return null;
        if (this.entries.containsKey(key)) return this.entries.get(key);
        Entry entry = null;
        try {
            entry = this.make(shot, variant);
            if (entry != null) {
                if (entry.enter != null) entry.enter.run();
                try {
                    Compat.init(entry.screen, this.setup.width, this.setup.height);
                    // Glazed Menu's title screen reads the worlds, counts the servers and follows the Realms news (as the title
                    // screen behind the setup already does) as it's opened.
                    if (opens(entry.screen)) entry.screen.added();
                } finally {
                    if (entry.exit != null) entry.exit.run();
                }
            }
        } catch (RuntimeException e) {
            GlazedMenu.LOGGER.warn("Couldn't make the setup's preview of {}", shot, e);
            this.failed.add(key);
            entry = null;
        }
        this.entries.put(key, entry);
        return entry;
    }

    // The screen for a page's variant (0 the game's own, 1 and on Glazed Menu's layouts), or null for one needing a world.
    private @Nullable Entry make(String shot, int variant) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen setup = this.setup;
        return switch (shot) {
            case "title" -> switch (variant) {
                case 0 -> new Entry(new TitleScreen(), null, null);
                case 1 -> titleLayout(GlazedSettings.TitleLayout.SHELF);
                case 2 -> titleLayout(GlazedSettings.TitleLayout.SIDEBAR);
                default -> null;
            };
            case "mod_list" -> variant == 1 ? new Entry(new GlazedModsScreen(setup), null, null) : null;
            case "options" -> variant == 0 ? new Entry(new OptionsScreen(setup, minecraft.options), null, null)
                    : glazed("replaceOptions", () -> GameOptionsScreen.replace(new OptionsScreen(setup, minecraft.options)));
            case "worlds" -> variant == 0 ? null
                    : glazed("replaceWorlds", () -> GlazedWorldsScreen.replace(new SelectWorldScreen(setup)));
            // The game's own list starts its LAN search as it's laid out, so it's left to its picture.
            case "servers" -> variant == 1 ? glazed("replaceServers", () -> GlazedServersScreen.replace(new JoinMultiplayerScreen(setup))) : null;
            case "controls" -> variant == 0 ? null
                    : new Entry(new GameControlsScreen(setup, minecraft.options), null, null);
            case "key_binds" -> variant == 0 ? null
                    : new Entry(new GlazedKeysScreen(setup, minecraft.options), null, null);
            case "language" -> variant == 0 ? null
                    : new Entry(new GameLanguageScreen(setup, minecraft.options, minecraft.getLanguageManager()), null, null);
            default -> null;
        };
    }

    // Glazed Menu's screen, made through the same switch the game's screens are swapped by, turned on for the moment.
    private static @Nullable Entry glazed(String key, Supplier<@Nullable Screen> make) {
        GlazedSettings.Switch toggle = GlazedSettings.screenSwitch(key);
        boolean was = toggle.get().getAsBoolean();
        toggle.set().accept(true);
        try {
            Screen screen = make.get();
            return screen == null ? null : new Entry(screen, null, null);
        } finally {
            toggle.set().accept(was);
        }
    }

    // Glazed Menu's title screen in a layout, which it reads from the settings as it lays itself out and draws.
    private static @Nullable Entry titleLayout(GlazedSettings.TitleLayout layout) {
        Entry made = glazed("replaceTitle", () -> GlazedTitleScreen.replace(new TitleScreen()));
        if (made == null) return null;
        GlazedSettings.TitleLayout[] kept = new GlazedSettings.TitleLayout[1];
        return new Entry(made.screen, () -> {
            kept[0] = GlazedSettings.titleLayout();
            GlazedSettings.setTitleLayout(layout);
        }, () -> GlazedSettings.setTitleLayout(kept[0]));
    }
}
