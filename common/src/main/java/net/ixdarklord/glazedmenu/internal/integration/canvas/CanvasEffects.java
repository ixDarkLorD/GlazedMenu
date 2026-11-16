package net.ixdarklord.glazedmenu.internal.integration.canvas;

import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.theme.ConfigEffect;
import net.ixdarklord.glazedmenu.api.theme.ConfigEffects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Glazed Menu with CoolCatLib installed: the config screen effects mods register with CoolCatLib draw in Glazed Menu's
 * screens too, under the ids their themes name. Only loaded when CoolCatLib is.
 */
public final class CanvasEffects {
    private CanvasEffects() {}

    public static void init() {
        ConfigEffects.setFallback(CanvasEffects::find);
    }

    private static @Nullable ConfigEffect find(Identifier id) {
        net.ixdarklord.coolcatcore.api.config.client.ConfigEffect effect = net.ixdarklord.coolcatcore.api.config.client.ConfigEffects.get(id);
        return effect == null ? null : new Bridge(effect);
    }

    // CoolCatLib's effect, told about Glazed Menu's screen in CoolCatLib's terms: the CoolCatLib theme the screen's was made from (or
    // CoolCatLib's default), in the same mode.
    private record Bridge(net.ixdarklord.coolcatcore.api.config.client.ConfigEffect effect) implements ConfigEffect {
        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, Context context) {
            this.effect.extractBackground(graphics, convert(context));
        }

        @Override
        public void extractForeground(GuiGraphicsExtractor graphics, Context context) {
            this.effect.extractForeground(graphics, convert(context));
        }

        @Override
        public void extractWidget(GuiGraphicsExtractor graphics, WidgetContext context) {
            this.effect.extractWidget(graphics, new net.ixdarklord.coolcatcore.api.config.client.ConfigEffect.WidgetContext(
                    net.ixdarklord.coolcatcore.api.config.client.ConfigEffect.WidgetKind.valueOf(context.kind().name()),
                    context.x(), context.y(), context.width(), context.height(), context.hovered(), context.focused(), context.active(),
                    convert(context.screen())));
        }

        private static net.ixdarklord.coolcatcore.api.config.client.ConfigEffect.Context convert(Context context) {
            ConfigTheme theme = context.theme();
            net.ixdarklord.coolcatcore.api.config.ConfigTheme core = theme.source() instanceof net.ixdarklord.coolcatcore.api.config.ConfigTheme source
                    ? source : net.ixdarklord.coolcatcore.api.config.ConfigTheme.DEFAULT;
            net.ixdarklord.coolcatcore.api.config.ConfigTheme.Mode mode = net.ixdarklord.coolcatcore.api.config.ConfigTheme.Mode.valueOf(context.mode().name());
            return new net.ixdarklord.coolcatcore.api.config.client.ConfigEffect.Context(context.width(), context.height(), context.mouseX(),
                    context.mouseY(), context.time(), context.partialTick(), core, core.colors(mode), mode, context.inWorld());
        }
    }
}
