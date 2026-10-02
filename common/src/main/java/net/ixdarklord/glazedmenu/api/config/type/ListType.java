package net.ixdarklord.glazedmenu.api.config.type;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A list whose elements are all of one type (itself possibly constrained), optionally limited in size. Values are
 * immutable lists. Reading a file drops invalid elements and corrects the rest; typed values must be valid as a whole.
 * Typed as JSON: {@code [1, 2, 3]}, {@code ["a", "b"]}.
 */
public final class ListType<E> implements ConfigType<List<E>> {
    private final ConfigType<E> elementType;
    private final int minSize;
    private final int maxSize;
    private final Codec<List<E>> codec;

    ListType(ConfigType<E> elementType, int minSize, int maxSize) {
        if (minSize < 0 || maxSize < minSize) throw new IllegalArgumentException("Invalid size range " + minSize + " ~ " + maxSize);
        this.elementType = elementType;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.codec = elementType.codec().listOf();
    }

    public ConfigType<E> elementType() {
        return this.elementType;
    }

    public int minSize() {
        return this.minSize;
    }

    public int maxSize() {
        return this.maxSize;
    }

    public ListType<E> withSize(int minSize, int maxSize) {
        return new ListType<>(this.elementType, minSize, maxSize);
    }

    @Override
    public Codec<List<E>> codec() {
        return this.codec;
    }

    @Override
    public ValidationResult<List<E>> validate(List<E> value) {
        List<E> elements = new ArrayList<>(value.size());
        Component problem = null;
        for (int i = 0; i < value.size(); i++) {
            E element = value.get(i);
            ValidationResult<E> result = element == null
                    ? ValidationResult.error(Component.translatableWithFallback("glazedmenu.error.null", "Must not be empty"))
                    : this.elementType.validate(element);
            if (result.hasValue()) elements.add(result.value());
            if (!result.isOk() && problem == null) {
                problem = Component.translatableWithFallback("glazedmenu.error.element", "Element %s: %s", i + 1, result.message().orElse(Component.empty()));
            }
        }
        if (elements.size() > this.maxSize) {
            elements = elements.subList(0, this.maxSize);
            if (problem == null) problem = Component.translatableWithFallback("glazedmenu.error.max_size", "Must have at most %s elements", this.maxSize);
        }
        if (elements.size() < this.minSize) {
            return ValidationResult.error(problem != null ? problem
                    : Component.translatableWithFallback("glazedmenu.error.min_size", "Must have at least %s elements", this.minSize));
        }
        List<E> list = List.copyOf(elements);
        return problem == null ? ValidationResult.ok(list) : ValidationResult.corrected(list, problem);
    }

    @Override
    public ValidationResult<List<E>> parse(String text) {
        ValidationResult<List<E>> result = ConfigType.super.parse(text);
        return result.isCorrected() ? ValidationResult.error(result.message().orElseThrow()) : result;
    }

    @Override
    public List<Component> describe() {
        List<Component> lines = new ArrayList<>();
        if (this.minSize > 0 || this.maxSize != Integer.MAX_VALUE) {
            lines.add(Component.translatableWithFallback("glazedmenu.info.size", "Size: %s ~ %s", this.minSize,
                    this.maxSize == Integer.MAX_VALUE ? "∞" : this.maxSize));
        }
        for (Component line : this.elementType.describe()) {
            lines.add(Component.translatableWithFallback("glazedmenu.info.elements", "Elements: %s", line));
        }
        return lines;
    }
}
