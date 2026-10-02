package net.ixdarklord.glazedmenu.api.config.type;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A namespaced id ({@code minecraft:stone}). With a registry, commands and the screen suggest its entries.
 * <p>
 * Ids aren't checked against the registry: configs load before other mods register their content, and a
 * data-driven registry only exists in a world. Check with the registry where the id is used.
 */
public final class IdentifierType implements ConfigType<Identifier> {
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
    public Codec<Identifier> codec() {
        return Identifier.CODEC;
    }

    @Override
    public ValidationResult<Identifier> validate(Identifier value) {
        return ValidationResult.ok(value);
    }

    @Override
    public ValidationResult<Identifier> parse(String text) {
        Identifier id = Identifier.tryParse(text.trim());
        return id != null ? ValidationResult.ok(id)
                : ValidationResult.error(Component.translatableWithFallback("glazedmenu.error.identifier", "Must be an id like minecraft:stone"));
    }

    @Override
    public String format(Identifier value) {
        return value.toString();
    }

    @Override
    public List<String> suggestions() {
        if (this.registry == null) return List.of();
        Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(this.registry.identifier());
        return registry == null ? List.of() : registry.keySet().stream().map(Identifier::toString).sorted().toList();
    }
}
