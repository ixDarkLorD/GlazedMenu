package net.ixdarklord.glazedmenu.api.config.type;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A namespaced id ({@code minecraft:stone}). With a registry, commands and the screen suggest its entries.
 * <p>
 * Ids aren't checked against the registry: configs load before other mods register their content, and a
 * data-driven registry only exists in a world. Check with the registry where the id is used.
 */
public final class IdentifierType implements ConfigType<ResourceLocation> {
    static final IdentifierType ANY = new IdentifierType(null);

    private final @Nullable ResourceKey<? extends Registry<?>> registry;

    IdentifierType(@Nullable ResourceKey<? extends Registry<?>> registry) {
        this.registry = registry;
    }

    /** The registry whose entries are suggested, if any. */
    public @Nullable ResourceKey<? extends Registry<?>> registry() {
        return this.registry;
    }

    @Override
    public Codec<ResourceLocation> codec() {
        return ResourceLocation.CODEC;
    }

    @Override
    public ValidationResult<ResourceLocation> validate(ResourceLocation value) {
        return ValidationResult.ok(value);
    }

    @Override
    public ValidationResult<ResourceLocation> parse(String text) {
        ResourceLocation id = ResourceLocation.tryParse(text.trim());
        return id != null ? ValidationResult.ok(id)
                : ValidationResult.error(Component.translatableWithFallback("glazedmenu.error.identifier", "Must be an id like minecraft:stone"));
    }

    @Override
    public String format(ResourceLocation value) {
        return value.toString();
    }

    @Override
    public List<String> suggestions() {
        if (this.registry == null) return List.of();
        Registry<?> registry = BuiltInRegistries.REGISTRY.get(this.registry.location());
        return registry == null ? List.of() : registry.keySet().stream().map(ResourceLocation::toString).sorted().toList();
    }
}
