package net.ixdarklord.glazedmenu.internal.core;

import net.ixdarklord.glazedmenu.api.ConfigScreens;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.internal.gui.ConfigSelectScreen;
import net.ixdarklord.glazedmenu.internal.modlist.GlazedModsScreen;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.function.Consumer;

// Glazed Menu's client smoke test, copied into a version's sources by tools/autotest/run.py (never committed there) and
// run with -Dglazedmenu.autotest=true. From the title screen it presses the Mods button and checks Glazed Menu's mod
// list opens, then opens the config list, a config and a popup, drawing each for a few frames, and quits. Each check
// logs "AUTOTEST PASS|FAIL <name>"; the last line is "AUTOTEST RESULT passed=<n> failed=<n>".
//
// One file serves every Minecraft version: the calls that differ between versions (where the open screen lives, how a
// button is pressed, how a screenshot is taken) go through reflection.
public final class DevAutotest {
    // Ticks between steps: enough for a screen to be laid out and drawn a few times.
    private static final int STEP_TICKS = 6;
    private static final Set<String> MODS_BUTTONS = Set.of("fml.menu.mods", "modmenu.title", "glazedmenu.mods.button");

    private static int ticks = -1;
    private static int step;
    private static int passed;
    private static int failed;

    private DevAutotest() {}

    public static void tick(Minecraft minecraft) {
        if (!Boolean.getBoolean("glazedmenu.autotest")) return;
        if (ticks < 0) {
            if (!(screen(minecraft) instanceof TitleScreen)) return;
            ticks = 0;
        }
        if (++ticks % STEP_TICKS != 0) return;
        try {
            switch (step++) {
                case 0 -> pressModsButton(minecraft);
                case 1 -> {
                    check("mods_button_opens_glazed_list", screen(minecraft) instanceof GlazedModsScreen, describe(screen(minecraft)));
                    shot(minecraft, "a_mods");
                }
                case 2 -> setScreen(minecraft, new ConfigSelectScreen(null, null));
                case 3 -> {
                    check("config_list_opens", screen(minecraft) instanceof ConfigSelectScreen, describe(screen(minecraft)));
                    shot(minecraft, "b_configs");
                }
                case 4 -> setScreen(minecraft, ConfigScreens.create(null, ownConfig()));
                case 5 -> {
                    check("config_screen_opens", describe(screen(minecraft)).endsWith(".ConfigScreen"), describe(screen(minecraft)));
                    shot(minecraft, "c_config");
                }
                case 6 -> setScreen(minecraft, ConfigScreens.categoryPopup(screen(minecraft), ownConfig(), ""));
                case 7 -> {
                    check("category_popup_opens", describe(screen(minecraft)).endsWith(".CategoryPopup"), describe(screen(minecraft)));
                    shot(minecraft, "d_popup");
                }
                default -> finish(minecraft);
            }
        } catch (Throwable e) {
            GlazedMenu.LOGGER.error("AUTOTEST FAIL step_{} threw", step - 1, e);
            failed++;
            finish(minecraft);
        }
    }

    // The title screen's Mods button (Glazed Menu's own, Mod Menu's or the loader's), pressed as a player would.
    private static void pressModsButton(Minecraft minecraft) throws ReflectiveOperationException {
        Screen title = screen(minecraft);
        AbstractWidget button = null;
        for (GuiEventListener child : title.children()) {
            if (!(child instanceof AbstractWidget widget)) continue;
            String key = widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
            if (key != null && MODS_BUTTONS.contains(key) || widget.getMessage().getString().startsWith("Mods")) button = widget;
        }
        check("title_screen_has_mods_button", button != null, describe(title));
        if (button == null) {
            // Still try the rest: open the list directly.
            setScreen(minecraft, new GlazedModsScreen(title));
            return;
        }
        GlazedMenu.LOGGER.info("AUTOTEST pressing \"{}\" ({})", button.getMessage().getString(), button.getClass().getName());
        press(button);
    }

    private static Config ownConfig() {
        return ConfigSources.forMod(GlazedMenu.MOD_ID).get(0);
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) passed++;
        else failed++;
        GlazedMenu.LOGGER.info("AUTOTEST {} {} ({})", ok ? "PASS" : "FAIL", name, detail);
    }

    private static void finish(Minecraft minecraft) {
        GlazedMenu.LOGGER.info("AUTOTEST RESULT passed={} failed={}", passed, failed);
        minecraft.stop();
        // Some dev clients (26.2 and later) don't exit after a stop like this: leftover worker threads keep them open
        // until the shutdown watchdog reports a crash, or for good. The test is over, so the process ends either way.
        Thread exit = new Thread(() -> {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "Glazed Menu autotest exit");
        exit.setDaemon(true);
        exit.start();
    }

    private static String describe(Object object) {
        return object == null ? "null" : object.getClass().getName();
    }

    // --- What differs between Minecraft versions ---

    // The open screen: Minecraft.gui.screen() since 26.2, the Minecraft.screen field before.
    private static Screen screen(Minecraft minecraft) {
        try {
            Object gui = Minecraft.class.getField("gui").get(minecraft);
            return (Screen) gui.getClass().getMethod("screen").invoke(gui);
        } catch (ReflectiveOperationException e) {
            try {
                return (Screen) Minecraft.class.getField("screen").get(minecraft);
            } catch (ReflectiveOperationException inner) {
                throw new IllegalStateException(inner);
            }
        }
    }

    private static void setScreen(Minecraft minecraft, Screen screen) throws ReflectiveOperationException {
        try {
            Object gui = Minecraft.class.getField("gui").get(minecraft);
            gui.getClass().getMethod("setScreen", Screen.class).invoke(gui, screen);
        } catch (NoSuchMethodException e) {
            Minecraft.class.getMethod("setScreen", Screen.class).invoke(minecraft, screen);
        }
    }

    // A button's press: onPress() before 26.1, onPress(input) since (given an Enter key press).
    private static void press(AbstractWidget button) throws ReflectiveOperationException {
        for (Method method : button.getClass().getMethods()) {
            if (!method.getName().equals("onPress")) continue;
            if (method.getParameterCount() == 0) {
                method.invoke(button);
                return;
            }
            if (method.getParameterCount() == 1) {
                Constructor<?> key = Class.forName("net.minecraft.client.input.KeyEvent").getConstructor(int.class, int.class, int.class);
                method.invoke(button, key.newInstance(257, 0, 0));
                return;
            }
        }
        throw new NoSuchMethodException("onPress on " + button.getClass().getName());
    }

    // Something of a type reachable from an object: what one of its no-argument getters returns or one of its fields
    // holds, or (a level further) one of those objects' own. Finds the main render target wherever a version keeps it.
    private static Object find(Object root, Class<?> type, int depth) {
        for (Method method : root.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers()) || !type.isAssignableFrom(method.getReturnType())) continue;
            try {
                Object value = method.invoke(root);
                if (value != null) return value;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        for (java.lang.reflect.Field field : root.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            try {
                Object value = field.get(root);
                if (value == null) continue;
                if (type.isInstance(value)) return value;
                if (depth > 1 && field.getType().getName().startsWith("net.minecraft.client.")) {
                    Object deeper = find(value, type, depth - 1);
                    if (deeper != null) return deeper;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    // Screenshot.grab, whose arguments changed over the versions; a failed screenshot fails nothing.
    private static void shot(Minecraft minecraft, String name) {
        try {
            Class<?> screenshots = Class.forName("net.minecraft.client.Screenshot");
            for (Method method : screenshots.getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!method.getName().equals("grab") || !Modifier.isStatic(method.getModifiers()) || types.length < 4
                        || types[0] != File.class || types[1] != String.class) continue;
                Object[] arguments = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    if (types[i] == File.class) arguments[i] = minecraft.gameDirectory;
                    else if (types[i] == String.class) arguments[i] = "autotest_" + name + ".png";
                    else if (types[i] == int.class) arguments[i] = 1;
                    else if (types[i] == Consumer.class) arguments[i] = (Consumer<Object>) message -> {};
                    else arguments[i] = find(minecraft, types[i], 2);
                }
                method.invoke(null, arguments);
                return;
            }
            GlazedMenu.LOGGER.warn("AUTOTEST no screenshot method for {}", name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            GlazedMenu.LOGGER.warn("AUTOTEST screenshot {} failed: {}", name, e.toString());
        }
    }
}
