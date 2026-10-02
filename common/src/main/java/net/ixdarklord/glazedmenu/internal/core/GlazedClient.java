package net.ixdarklord.glazedmenu.internal.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

// Small client helpers: opening a screen once the chat (after a command) has closed, and toasts.
public final class GlazedClient {
    // The widest a toast's message line gets before it wraps.
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
            if (screen != null) minecraft.gui.setScreen(screen);
        }
    }

    /**
     * A notice in the corner: a short title (usually the config's name) over a message, which wraps so it never runs
     * past the toast. It replaces the previous one.
     */
    public static void toast(Component title, @Nullable Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        ToastManager toasts = minecraft.gui.toastManager();
        SystemToast.SystemToastId id = SystemToast.SystemToastId.PERIODIC_NOTIFICATION;
        SystemToast.forceHide(toasts, id);
        // A long message wraps on its own in 26.2's toasts.
        toasts.addToast(new SystemToast(id, title, message));
    }
}
