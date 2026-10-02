package net.ixdarklord.glazedmenu.internal.source.external;

import net.ixdarklord.glazedmenu.internal.source.Access;
import net.ixdarklord.glazedmenu.api.config.Config;
import net.ixdarklord.glazedmenu.api.config.ConfigPreset;
import net.ixdarklord.glazedmenu.api.config.ConfigScope;
import net.ixdarklord.glazedmenu.api.config.ConfigTheme;
import net.ixdarklord.glazedmenu.api.config.ConfigValue;
import net.ixdarklord.glazedmenu.internal.core.GlazedMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Another config system's config, seen as a CoolCatLib {@link Config} so Glazed Menu's screens show it like their own:
 * its values read and write the system's, and saving saves through it. Made by {@link ExternalConfigBuilder}.
 */
public final class ExternalConfig implements Config {
    private final ResourceLocation id;
    private final ConfigScope scope;
    private final ExternalGroup root;
    private final Component title;
    private final String fileName;
    private final @Nullable Path filePath;
    private final Supplier<Access> access;
    private final Runnable saver;

    ExternalConfig(ResourceLocation id, ConfigScope scope, ExternalGroup root, Component title, String fileName, @Nullable Path filePath,
                   Supplier<Access> access, Runnable saver) {
        this.id = id;
        this.scope = scope;
        this.root = root;
        this.title = title;
        this.fileName = fileName;
        this.filePath = filePath;
        this.access = access;
        this.saver = saver;
        root.attach(this);
    }

    /** Where edits go from this client. */
    public Access access() {
        return this.access.get();
    }

    public String fileName() {
        return this.fileName;
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public ConfigScope scope() {
        return this.scope;
    }

    @Override
    public ExternalGroup root() {
        return this.root;
    }

    @Override
    public Component title() {
        return this.title;
    }

    @Override
    public ConfigTheme theme() {
        return ConfigTheme.forMod(this.modId());
    }

    @Override
    public List<String> comment() {
        return List.of();
    }

    @Override
    public @Nullable Path filePath() {
        return this.filePath;
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public void save() {
        try {
            this.saver.run();
        } catch (RuntimeException e) {
            GlazedMenu.LOGGER.error("Couldn't save config {}", this.id, e);
        }
    }

    // The other system reads its files itself.
    @Override
    public void reload() {}

    @Override
    public void resetAll() {
        this.values().forEach(ConfigValue::reset);
    }

    @Override
    public Optional<ConfigValue<?>> find(String path) {
        return this.values().filter(value -> value.path().equals(path)).findFirst();
    }

    @Override
    public Map<String, ConfigPreset> presets() {
        return Map.of();
    }

    @Override
    public void applyPreset(ConfigPreset preset) {}

    @Override
    public void addListener(ChangeListener listener) {}

    @Override
    public boolean removeListener(ChangeListener listener) {
        return false;
    }

    @Override
    public String toString() {
        return "ExternalConfig[" + this.id + ", " + this.scope + "]";
    }
}
