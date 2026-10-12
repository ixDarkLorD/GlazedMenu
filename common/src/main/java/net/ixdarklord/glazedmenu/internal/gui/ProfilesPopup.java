package net.ixdarklord.glazedmenu.internal.gui;

import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.config.ConfigProfiles;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.ConfirmPopup;
import net.ixdarklord.glazedmenu.internal.style.FlatButton;
import net.ixdarklord.glazedmenu.internal.style.StyledEditBox;
import net.ixdarklord.glazedmenu.internal.style.StyledPopup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * The profiles, in a popup over the list of every mod's configs: each profile's button puts its settings in as unsaved
 * changes (the list then shows them, to look over and save), and a bin deletes it. Below them, a name and Save make a
 * profile of the settings the list shows now (the changed ones, or what a search narrowed them to).
 */
final class ProfilesPopup extends StyledPopup {
    private static final int WIDTH = 250;
    // No more rows than fit most windows; the profiles past them are still used from the chat.
    private static final int MAX_ROWS = 7;

    private final List<ConfigValue<?>> listed;
    private final Consumer<String> use;
    private @Nullable StyledEditBox name;
    private @Nullable FlatButton saveButton;
    private String typed = "";

    ProfilesPopup(Screen parent, ConfigTheme theme, List<ConfigValue<?>> listed, Consumer<String> use) {
        super(parent, Component.translatableWithFallback("glazedmenu.profiles.title", "Profiles"), theme);
        this.listed = listed;
        this.use = use;
    }

    @Override
    protected ConfigIcons.Icon titleIcon() {
        return ConfigIcons.LIST;
    }

    private List<String> shownProfiles() {
        List<String> names = ConfigProfiles.names();
        return names.subList(0, Math.min(names.size(), MAX_ROWS));
    }

    @Override
    protected void initPopup() {
        List<String> names = this.shownProfiles();
        int rows = Math.max(1, names.size());
        this.setPanel(WIDTH, PADDING + TITLE_HEIGHT + rows * 24 + 8 + 14 + 22 + PADDING);
        int y = this.contentTop();
        for (String profile : names) {
            FlatButton button = this.addRenderableWidget(FlatButton.of(Component.literal(profile), this.contentWidth() - 24, pressed -> {
                this.minecraft.setScreen(this.parent);
                this.use.accept(profile);
            }).withIcon(ConfigIcons.CHEVRON).tooltip(Component.translatableWithFallback("glazedmenu.profiles.use.tooltip",
                    "Sets its %s settings, as unsaved changes to look over and save", ConfigProfiles.size(profile))));
            button.setPosition(this.contentLeft(), y);
            FlatButton delete = this.addRenderableWidget(FlatButton.icon(ConfigIcons.CLOSE,
                    Component.translatableWithFallback("glazedmenu.profiles.delete", "Delete this profile"), pressed -> this.confirmDelete(profile)));
            delete.setPosition(this.contentLeft() + this.contentWidth() - 20, y);
            y += 24;
        }

        // A name and Save: a profile of the settings the list shows.
        int saveY = this.contentTop() + rows * 24 + 8 + 14;
        Component save = Component.translatableWithFallback("glazedmenu.button.save_short", "Save");
        int saveWidth = this.font.width(save) + 16;
        this.name = this.addRenderableWidget(new StyledEditBox(this.font, this.contentWidth() - saveWidth - 4, 20,
                Component.translatableWithFallback("glazedmenu.profiles.name", "Profile name")));
        this.name.setPosition(this.contentLeft(), saveY);
        this.name.setMaxLength(40);
        this.name.setPlaceholder(Component.translatableWithFallback("glazedmenu.profiles.name", "Profile name"));
        this.name.setValue(this.typed);
        this.name.setResponder(text -> {
            this.typed = text;
            this.updateSave();
        });
        this.saveButton = this.addRenderableWidget(FlatButton.of(save, saveWidth, pressed -> this.save()).style(FlatButton.Style.PRIMARY));
        this.saveButton.setPosition(this.contentLeft() + this.contentWidth() - saveWidth, saveY);
        this.updateSave();
    }

    private void updateSave() {
        if (this.saveButton == null || this.name == null) return;
        boolean named = !this.typed.isBlank();
        this.name.setEditable(!this.listed.isEmpty());
        this.saveButton.active = named && !this.listed.isEmpty();
        this.saveButton.tooltip(this.listed.isEmpty()
                ? Component.translatableWithFallback("glazedmenu.profiles.save.none", "Turn on Changed (or search) first: a profile holds the settings the list shows")
                : ConfigProfiles.names().contains(this.typed.trim())
                ? Component.translatableWithFallback("glazedmenu.profiles.save.replace", "Replaces the profile of this name with the %s settings shown", this.listed.size())
                : Component.translatableWithFallback("glazedmenu.profiles.save.tooltip", "Makes a profile of the %s settings shown", this.listed.size()));
    }

    private void save() {
        String name = this.typed.trim();
        if (name.isEmpty() || this.listed.isEmpty()) return;
        ConfigProfiles.save(name, this.listed);
        this.typed = "";
        this.rebuildWidgets();
    }

    private void confirmDelete(String profile) {
        this.minecraft.setScreen(new ConfirmPopup(this, this.theme,
                Component.translatableWithFallback("glazedmenu.profiles.delete.title", "Delete %s?", profile),
                Component.translatableWithFallback("glazedmenu.profiles.delete.message", "The profile is removed; your settings stay as they are."),
                Component.translatableWithFallback("glazedmenu.profiles.delete.yes", "Delete"), true, delete -> {
                    if (delete) ConfigProfiles.delete(profile);
                    this.minecraft.setScreen(this);
                }));
    }

    @Override
    protected void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        List<String> names = this.shownProfiles();
        if (names.isEmpty()) {
            ConfigStyle.centeredText(graphics, this.font, Component.translatableWithFallback("glazedmenu.profiles.none", "No profiles yet"),
                    this.contentLeft() + this.contentWidth() / 2, this.contentTop() + 8, this.contentWidth(), ConfigStyle.colors().textMuted());
        }
        // A hairline, then what Save takes.
        int lineY = this.contentTop() + Math.max(1, names.size()) * 24 + 3;
        graphics.fill(this.contentLeft(), lineY, this.contentLeft() + this.contentWidth(), lineY + 1, ConfigStyle.colors().panelBorder());
        Component hint = this.listed.isEmpty()
                ? Component.translatableWithFallback("glazedmenu.profiles.hint.none", "Nothing listed to save")
                : Component.translatableWithFallback("glazedmenu.profiles.hint", "Save the %s settings shown as a profile", this.listed.size());
        ConfigStyle.text(graphics, this.font, hint, this.contentLeft(), lineY + 7, this.contentWidth(), ConfigStyle.colors().textDim());
    }
}
