package net.ixdarklord.glazedmenu.internal.source.spec;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.IConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// A Forge ForgeConfigSpec (Forge's own, or Forge Config API Port's copy on Fabric: the same classes).
public record ForgeSpecView(ForgeConfigSpec spec) implements SpecView {
    public static @Nullable SpecView of(IConfigSpec<?> spec) {
        return spec instanceof ForgeConfigSpec forgeSpec ? new ForgeSpecView(forgeSpec) : null;
    }

    @Override
    public UnmodifiableConfig values() {
        return this.spec.getValues();
    }

    @Override
    public @Nullable String groupComment(List<String> path) {
        return this.spec.getLevelComment(path);
    }

    @Override
    public @Nullable String groupTranslationKey(List<String> path) {
        return this.spec.getLevelTranslationKey(path);
    }

    @Override
    public @Nullable ValueView value(Object handle) {
        return handle instanceof ForgeConfigSpec.ConfigValue<?> value ? new Value<>(this.spec, value) : null;
    }

    @Override
    public boolean isLoaded() {
        return this.spec.isLoaded();
    }

    @Override
    public void save() {
        this.spec.save();
    }

    private record Value<T>(ForgeConfigSpec spec, ForgeConfigSpec.ConfigValue<T> handle) implements ValueView {
        private ForgeConfigSpec.ValueSpec valueSpec() {
            return this.spec.getSpec().get(this.handle.getPath());
        }

        @Override
        public List<String> path() {
            return this.handle.getPath();
        }

        @Override
        public Object get() {
            return this.handle.get();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void set(Object value) {
            this.handle.set((T) value);
        }

        @Override
        public Object defaultValue() {
            return this.handle.getDefault();
        }

        @Override
        public @Nullable Class<?> valueClass() {
            return this.valueSpec().getClazz();
        }

        @Override
        public @Nullable String comment() {
            return this.valueSpec().getComment();
        }

        @Override
        public @Nullable String translationKey() {
            return this.valueSpec().getTranslationKey();
        }

        @Override
        public @Nullable Number min() {
            return bound(this.valueSpec().getRange(), "getMin");
        }

        @Override
        public @Nullable Number max() {
            return bound(this.valueSpec().getRange(), "getMax");
        }

        // Forge 47 keeps its Range class private; its public getters are called through reflection.
        private static @Nullable Number bound(@Nullable Object range, String getter) {
            if (range == null) return null;
            try {
                java.lang.reflect.Method method = range.getClass().getMethod(getter);
                method.setAccessible(true);
                return method.invoke(range) instanceof Number number ? number : null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        @Override
        public RestartRequirement restart() {
            return this.valueSpec().needsWorldRestart() ? RestartRequirement.WORLD : RestartRequirement.NONE;
        }

        @Override
        public boolean test(Object value) {
            return this.valueSpec().test(value);
        }
    }
}
