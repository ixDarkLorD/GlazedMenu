package net.ixdarklord.glazedmenu.internal.mixin;

import net.ixdarklord.glazedmenu.internal.game.WorkingLook;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.glazedmenu.internal.core.GlazedSettings;
import net.ixdarklord.glazedmenu.internal.game.LoadingLook;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// The screen shown while a server is being connected to, in Glazed Menu's look: a picture of where the player last was
// there behind it, and a panel along the bottom saying how the connecting stands, with Cancel in it. The screen stays
// the game's own (it does the connecting), and Cancel presses its own button.
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {
    @Shadow
    private Component status;

    private ConnectScreenMixin(Component title) {
        super(title);
    }

    // A server being connected to, however the connecting began (the game's own list, Glazed Menu's, a shortcut, a
    // transfer from another server): the loading screens are told which, so its picture is there from their first frame.
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void glazedmenu$expect(Screen parent, Minecraft minecraft, ServerAddress hostAndPort, ServerData data, boolean isQuickPlay,
                                          TransferState transferState, CallbackInfo info) {
        LoadingLook.expect(LoadingLook.serverKey(data.ip), data.name);
        LoadingLook.connecting(data);
    }

    // The game's Cancel, taken off the screen and pressed by a button of Glazed Menu's in the panel.
    @Inject(method = "init", at = @At("TAIL"))
    private void glazedmenu$button(CallbackInfo info) {
        if (!GlazedSettings.replaceLoading()) return;
        for (GuiEventListener child : List.copyOf(this.children())) {
            if (!(child instanceof Button game) || child instanceof FlatButton) continue;
            this.removeWidget(game);
            FlatButton cancel = this.addRenderableWidget(FlatButton.of(game.getMessage(), 84, button -> game.onPress()));
            cancel.setPosition((this.width - 84) / 2, this.height - 30);
        }
    }

    // 1.21.1 draws a screen's background and the rest in one call: the game's own background (its panorama, blurred),
    // then the look of every wait into a world (WorkingLook), each word of the connecting a step of it, the terrain's
    // loading the last; and the widgets.
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void glazedmenu$content(GuiGraphics raw, int mouseX, int mouseY, float a, CallbackInfo info) {
        if (!GlazedSettings.replaceLoading()) return;
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        this.renderBackground(raw, mouseX, mouseY, a);
        WorkingLook.extract(graphics, this.font, this.width, this.height, this.status);
        for (Renderable renderable : ((ScreenInvoker) this).glazedmenu$renderables()) renderable.render(raw, mouseX, mouseY, a);
        info.cancel();
    }
}
