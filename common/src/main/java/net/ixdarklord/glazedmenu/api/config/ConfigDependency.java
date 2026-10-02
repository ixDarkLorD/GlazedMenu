package net.ixdarklord.glazedmenu.api.config;

import java.util.function.Predicate;

/**
 * A value that only matters while another value meets a condition, such as "particle count" while "particles" is on.
 *
 * @param source    the value depended on
 * @param condition when the dependent value is active
 */
public record ConfigDependency<V>(ConfigValue<V> source, Predicate<? super V> condition) {
    /** Whether the condition holds for the source's current value. */
    public boolean isMet() {
        return this.condition.test(this.source.get());
    }

    /**
     * Whether the condition holds for a value of the source that isn't current yet, like one pending in the config
     * screen.
     */
    @SuppressWarnings("unchecked")
    public boolean isMetBy(Object sourceValue) {
        return this.condition.test((V) sourceValue);
    }
}
