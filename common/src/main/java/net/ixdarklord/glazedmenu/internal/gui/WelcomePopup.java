package net.ixdarklord.glazedmenu.internal.gui;

import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.gui.style.ThemeResources;
import net.ixdarklord.glazedmenu.internal.source.ConfigSources;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.minecraft.client.Minecraft;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A welcome over the title screen, the first time the game is run with Glazed Menu: it says which of the game's screens
 * open as Glazed Menu's, with a button to the settings that turn each back into the game's own. Continue turns the
 * welcome off in Glazed Menu's settings (so does going to the settings from it); turned back on there, it's shown again
 * the next time the game starts.
 */
public final class WelcomePopup extends StyledPopup {
    private static final int WIDTH = 270;
    // Looked for once each time the game runs, at the first title screen.
    private static boolean checked;

    private List<FormattedCharSequence> lines = List.of();

    private WelcomePopup(@Nullable Screen parent) {
        super(parent, Component.translatableWithFallback("glazedmenu.welcome.title", "Welcome to Glazed Menu"),
                ThemeResources.resolve(GlazedMenu.MOD_ID, GlazedBrand.THEME));
    }

    /** Every client tick: opens the welcome over the first title screen, while the player has it on. */
    public static void tick(Minecraft minecraft) {
        if (checked || !(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        checked = true;
        // Not in the dev client's smoke test, which starts from the title screen.
        if (GlazedSettings.welcomePopup() && !Boolean.getBoolean("glazedmenu.autotest")) minecraft.setScreen(new WelcomePopup(minecraft.screen));
    }

    @Override
    protected void initPopup() {
        Component message = Component.translatableWithFallback("glazedmenu.welcome.message",
                "The title screen, the pause menu, the lists of worlds and servers, the options and more of the game's screens now open in Glazed Menu's look.\n\nEach of them can be turned back into the game's own, one by one, in Glazed Menu's settings under Game Screens.");
        this.setPanel(WIDTH, 0);
        this.lines = this.font.split(message, this.contentWidth());
        this.setPanel(WIDTH, PADDING + TITLE_HEIGHT + this.lines.size() * 10 + 6 + FOOTER + PADDING);
        int y = this.footerButtonY();
        Component settings = Component.translatableWithFallback("glazedmenu.welcome.settings", "Screen Settings");
        int settingsWidth = Math.min(this.font.width(settings) + 28, this.contentWidth() - 4 - 84);
        FlatButton settingsButton = this.addRenderableWidget(FlatButton.of(settings, settingsWidth, button -> this.openSettings()).withIcon(ConfigIcons.GEAR)
                .tooltip(Component.translatableWithFallback("glazedmenu.welcome.settings.tooltip", "Choose which of the game's screens Glazed Menu replaces")));
        settingsButton.setPosition(this.contentLeft(), y);
        FlatButton continueButton = this.addRenderableWidget(FlatButton.of(Component.translatableWithFallback("glazedmenu.welcome.continue", "Continue"), 84,
                button -> {
                    GlazedSettings.dismissWelcome();
                    this.minecraft.setScreen(this.parent);
                }).style(FlatButton.Style.PRIMARY).withIcon(ConfigIcons.CHECK));
        continueButton.setPosition(this.contentLeft() + this.contentWidth() - 84, y);
    }

    // Glazed Menu's settings, at the switches for the game's screens; coming back from them leads to the title screen.
    private void openSettings() {
        GlazedSettings.dismissWelcome();
        List<Config> configs = ConfigSources.forMod(GlazedMenu.MOD_ID);
        this.minecraft.setScreen(configs.isEmpty() ? this.parent : ConfigScreen.createAt(this.parent, configs.getFirst(), "game", null, null));
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.STAR;
    }

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int y = this.contentTop();
        for (FormattedCharSequence line : this.lines) {
            graphics.text(this.font, line, this.contentLeft(), y, ConfigStyle.colors().textDim(), false);
            y += 10;
        }
    }
}
