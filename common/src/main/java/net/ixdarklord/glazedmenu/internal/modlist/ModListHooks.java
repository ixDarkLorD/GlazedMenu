package net.ixdarklord.glazedmenu.internal.modlist;

import net.ixdarklord.glazedmenu.internal.core.GlazedPlatform;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

// Where Glazed Menu's mod list comes in: in place of Mod Menu's and NeoForge's as they open, and as a Mods button on the
// title and pause screens when nothing else put one there.
public final class ModListHooks {
    private static final Set<String> MOD_LISTS = Set.of("com.terraformersmc.modmenu.gui.ModsScreen", "net.neoforged.neoforge.client.gui.ModListScreen");
    private static final Set<String> MODS_BUTTONS = Set.of("fml.menu.mods", "modmenu.title", "glazedmenu.mods.button");

    private ModListHooks() {}

    /** The screen to open instead: Glazed Menu's mod list for another mod list, over the screen that opened it. */
    public static @Nullable Screen replace(@Nullable Screen screen) {
        if (screen == null || !GlazedSettings.replaceModList() || !MOD_LISTS.contains(screen.getClass().getName())) return screen;
        return new GlazedModsScreen(Minecraft.getInstance().screen);
    }

    /**
     * Adds a Mods button: on the title screen it shares the Realms button's row, on the pause screen it takes Report
     * Bugs' place (as Mod Menu's does). Nothing when a mod list's button is there already, or Mod Menu will add its own.
     */
    public static void addModsButton(Screen screen, List<? extends GuiEventListener> children, Consumer<Button> add, boolean titleScreen) {
        if (!GlazedSettings.modsButton() || GlazedPlatform.get().isModLoaded("modmenu")) return;
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget && (MODS_BUTTONS.contains(key(widget)) || widget.getMessage().getString().equalsIgnoreCase("Mods"))) return;
        }
        Component name = Component.translatableWithFallback("glazedmenu.mods.button", "Mods");
        for (GuiEventListener child : children) {
            if (!(child instanceof AbstractWidget widget)) continue;
            if (titleScreen && "menu.online".equals(key(widget))) {
                int half = (widget.getWidth() - 4) / 2;
                widget.setWidth(half);
                add.accept(Button.builder(name, button -> open(screen)).bounds(widget.getX() + half + 4, widget.getY(), half, widget.getHeight()).build());
                return;
            }
            if (!titleScreen && "menu.reportBugs".equals(key(widget))) {
                widget.visible = false;
                widget.active = false;
                add.accept(Button.builder(name, button -> open(screen)).bounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()).build());
                return;
            }
        }
    }

    private static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new GlazedModsScreen(parent));
    }

    private static @Nullable String key(AbstractWidget widget) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }
}
