package net.ixdarklord.glazedmenu.internal.source.external;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A config field, as Cloth Config, YACL and MidnightLib keep their values: read and written by reflection on the object
 * holding it (fetched again each time, as a system may replace it on loading), or on the class for a static one.
 */
public final class FieldBinding<T> implements ExternalValue.Binding<T> {
    private final Field field;
    private final Supplier<@Nullable Object> holder;
    private final Function<Object, T> read;
    private final Function<T, Object> write;

    private FieldBinding(Field field, Supplier<@Nullable Object> holder, Function<Object, T> read, Function<T, Object> write) {
        this.field = field;
        this.holder = holder;
        this.read = read;
        this.write = write;
        field.setAccessible(true);
    }

    /** The field as it is, on the object the supplier gives (null for a static field). */
    @SuppressWarnings("unchecked")
    public static <T> FieldBinding<T> of(Field field, Supplier<@Nullable Object> holder) {
        return new FieldBinding<>(field, holder, value -> (T) copyList(value), value -> copyList(value));
    }

    /** The field converted on the way: another class stores it than the screens edit (an AWT color as an int). */
    public static <T> FieldBinding<T> converted(Field field, Supplier<@Nullable Object> holder, Function<Object, T> read, Function<T, Object> write) {
        return new FieldBinding<>(field, holder, read, write);
    }

    public static boolean isStatic(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }

    /** Fields a config system would read: not static (unless {@code statics}), transient or synthetic. */
    public static List<Field> configFields(Class<?> type, boolean statics) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.isSynthetic() || Modifier.isTransient(modifiers) || Modifier.isStatic(modifiers) != statics) continue;
            fields.add(field);
        }
        return fields;
    }

    @Override
    public T get() {
        try {
            return this.read.apply(this.field.get(this.target()));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void set(T value) {
        try {
            this.field.set(this.target(), this.write.apply(value));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private @Nullable Object target() {
        return isStatic(this.field) ? null : this.holder.get();
    }

    // Lists are handed over as copies, so an edit never changes the list the mod is reading.
    private static Object copyList(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : value;
    }
}
