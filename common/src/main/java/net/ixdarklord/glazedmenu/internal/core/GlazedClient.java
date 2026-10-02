package net.ixdarklord.glazedmenu.internal.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

// Small client helpers: opening a screen once the chat (after a command) has closed, and toasts.
public final class GlazedClient {
    // The widest a toast's message line gets before it wraps.
    private static final int TOAST_LINE_WIDTH = 170;
    private static @Nullable Supplier<Screen> pendingScreen;

    private GlazedClient() {}

    /** Opens a screen next tick, once whatever is closing (the chat, after a command) has closed. */
    public static void openLater(Supplier<Screen> screen) {
        pendingScreen = screen;
    }

    /** Called every client tick by the loader module. */
    public static void tick(Minecraft minecraft) {
        if (pendingScreen != null) {
            Screen screen = pendingScreen.get();
            pendingScreen = null;
            if (screen != null) minecraft.setScreen(screen);
        }
    }

    /**
     * A notice in the corner: a short title (usually the config's name) over a message, which wraps so it never runs
     * past the toast. It replaces the previous one.
     */
    public static void toast(Component title, @Nullable Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        ToastComponent toasts = minecraft.getToasts();
        SystemToast.SystemToastId id = SystemToast.SystemToastId.PERIODIC_NOTIFICATION;
        SystemToast.forceHide(toasts, id);
        boolean fits = message == null || minecraft.font.width(message) <= TOAST_LINE_WIDTH;
        toasts.addToast(fits ? new SystemToast(id, title, message) : SystemToast.multiline(minecraft, id, title, message));
    }
}
