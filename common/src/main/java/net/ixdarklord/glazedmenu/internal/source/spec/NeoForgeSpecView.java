package net.ixdarklord.glazedmenu.internal.source.spec;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.ixdarklord.glazedmenu.api.config.RestartRequirement;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// A NeoForge ModConfigSpec (NeoForge's own, or Forge Config API Port's copy on Fabric: the same classes).
record NeoForgeSpecView(ModConfigSpec spec) implements SpecView {
    static @Nullable SpecView of(IConfigSpec spec) {
        return spec instanceof ModConfigSpec modSpec ? new NeoForgeSpecView(modSpec) : null;
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
        return handle instanceof ModConfigSpec.ConfigValue<?> value ? new Value<>(value) : null;
    }

    @Override
    public boolean isLoaded() {
        return this.spec.isLoaded();
    }

    @Override
    public void save() {
        this.spec.save();
    }

    private record Value<T>(ModConfigSpec.ConfigValue<T> handle) implements ValueView {
        private ModConfigSpec.ValueSpec valueSpec() {
            return this.handle.getSpec();
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
        @SuppressWarnings("rawtypes")
        public @Nullable Number min() {
            ModConfigSpec.Range range = this.valueSpec().getRange();
            return range != null && range.getMin() instanceof Number min ? min : null;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public @Nullable Number max() {
            ModConfigSpec.Range range = this.valueSpec().getRange();
            return range != null && range.getMax() instanceof Number max ? max : null;
        }

        @Override
        public RestartRequirement restart() {
            return switch (this.valueSpec().restartType()) {
                case NONE -> RestartRequirement.NONE;
                case WORLD -> RestartRequirement.WORLD;
                case GAME -> RestartRequirement.GAME;
            };
        }

        @Override
        public boolean test(Object value) {
            return this.valueSpec().test(value);
        }
    }
}
