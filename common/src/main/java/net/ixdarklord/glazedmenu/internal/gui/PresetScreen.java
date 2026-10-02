package net.ixdarklord.glazedmenu.internal.gui;

import net.ixdarklord.glazedmenu.api.config.ConfigPreset;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

// Picks a preset, in a popup over the config screen; its values become pending edits (one undo step).
final class PresetScreen extends StyledPopup {
    private static final int WIDTH = 220;
    private static final int MAX_TOOLTIP_LINES = 12;

    private final ConfigEditSession session;

    PresetScreen(Screen parent, ConfigEditSession session) {
        super(parent, Component.translatableWithFallback("glazedmenu.presets.title", "Presets"), session.config().theme());
        this.session = session;
    }

    @Override
    protected void initPopup() {
        int count = this.session.config().presets().size();
        this.setPanel(WIDTH, PADDING + TITLE_HEIGHT + count * 24 + PADDING - 4);
        int y = this.contentTop();
        for (ConfigPreset preset : this.session.config().presets().values()) {
            FlatButton button = this.addRenderableWidget(FlatButton.of(preset.displayName(), this.contentWidth(), pressed -> {
                this.session.applyPreset(preset);
                this.onClose();
            }).withIcon(ConfigIcons.CHEVRON).tooltip(describe(preset)));
            button.setPosition(this.contentLeft(), y);
            y += 24;
        }
    }

    private static Component describe(ConfigPreset preset) {
        StringBuilder text = new StringBuilder();
        int lines = 0;
        for (Map.Entry<ConfigValue<?>, Object> entry : preset.values().entrySet()) {
            if (lines++ == MAX_TOOLTIP_LINES) {
                text.append("\n…");
                break;
            }
            if (!text.isEmpty()) text.append('\n');
            text.append(entry.getKey().displayName().getString()).append(": ").append(format(entry.getKey(), entry.getValue()));
        }
        return Component.literal(text.toString());
    }

    @SuppressWarnings("unchecked")
    private static <T> String format(ConfigValue<T> value, Object presetValue) {
        return value.type().format((T) presetValue);
    }
}
