package net.ixdarklord.glazedmenu.internal.source.external;

import net.ixdarklord.glazedmenu.api.config.ConfigNode;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A group or value of another config system's config. Its name and description come from that system's translation
 * keys when the game has them, and otherwise from the key and the comment it gave.
 */
public abstract sealed class ExternalNode implements ConfigNode permits ExternalGroup, ExternalValue {
    private final String key;
    private final @Nullable ExternalGroup parent;
    private final @Nullable String translationKey;
    private final @Nullable String tooltipKey;
    private final String fallbackName;
    private final List<String> comment;
    private @Nullable ExternalConfig config;

    ExternalNode(String key, @Nullable ExternalGroup parent, @Nullable String translationKey, @Nullable String tooltipKey,
                 String fallbackName, List<String> comment) {
        this.key = key;
        this.parent = parent;
        this.translationKey = translationKey;
        this.tooltipKey = tooltipKey;
        this.fallbackName = fallbackName;
        this.comment = List.copyOf(comment);
    }

    void attach(ExternalConfig config) {
        this.config = config;
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public String path() {
        if (this.parent == null) return "";
        String parentPath = this.parent.path();
        return parentPath.isEmpty() ? this.key : parentPath + "." + this.key;
    }

    @Override
    public ExternalConfig config() {
        if (this.config == null) throw new IllegalStateException("The config holding " + this.key + " hasn't been built yet");
        return this.config;
    }

    @Override
    public @Nullable ExternalGroup parent() {
        return this.parent;
    }

    /** The translated description, if the config system has one for the node, or the comment it gave. */
    @Override
    public List<String> comment() {
        if (this.tooltipKey != null && Language.getInstance().has(this.tooltipKey)) {
            return Component.translatable(this.tooltipKey).getString().lines().toList();
        }
        return this.comment;
    }

    @Override
    public String translationKey() {
        if (this.translationKey != null) return this.translationKey;
        ExternalConfig config = this.config();
        String base = "config." + config.modId() + "." + config.name();
        return this.parent == null ? base : base + "." + this.path();
    }

    @Override
    public Component displayName() {
        return Component.translatableWithFallback(this.translationKey(), this.fallbackName);
    }

    @Override
    public boolean isHidden() {
        return false;
    }
}
