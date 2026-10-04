---
icon: material/form-textbox
description: The config model, types and editors
---

# Configs and Editors

Glazed Menu reads every config library into one shape, so one screen can edit them all. This page covers that shape, the value types in it, and how to change the widget that edits a type.

## The config model

Everything here is in `net.ixdarklord.glazedmenu.api.config`.

A `Config` is a tree: `ConfigGroup`s holding `ConfigValue`s and more groups. Glazed Menu builds it from the library your mod already uses. Your mod doesn't implement these interfaces, and this version has no lookup that hands you another mod's `Config`; you meet the model where Glazed Menu passes it to you, such as an [editor](#editors).

| Type | What it is |
|---|---|
| `Config` | One config: its id (`mymod:client`), scope, title, theme, file, presets and root group. |
| `ConfigGroup` | A named section, holding values and nested groups in declaration order. |
| `ConfigValue<T>` | One typed setting: its value, default, type, and what a change needs. |
| `ConfigNode` | What groups and values share: key, dotted path, comment, translation key, display name, hidden or not. |
| `ConfigPreset` | A named set of values applied together, like "Performance". |
| `ConfigDependency<V>` | A value that only matters while another meets a condition. The screen greys it out otherwise. |

### Values

```java
ConfigValue<Integer> count = ...;

int now = count.get();               // the current value; cheap and thread-safe
count.set(12);                       // in memory, and listeners are told; config.save() writes it
count.reset();                       // back to the default
count.addListener((oldValue, newValue) -> rebuild());
```

`set` stores a corrected value when the type can correct it, a number out of range for example, and throws `IllegalArgumentException` when it can't. `validate(value)` checks without setting.

Translations use the key `config.<modid>.<config>.<path>` for a node's name, and the same key with `.tooltip` for its tooltip, falling back to the comment.

### Scopes

| `ConfigScope` | Where it lives |
|---|---|
| `CLIENT` | Loaded only on clients. Never synced. |
| `COMMON` | Loaded on both sides. Each side keeps its own values. |
| `SERVER` | Loaded on both sides; the server's values are sent to every client. Operators can edit them in-game. |
| `WORLD` | Stored per world, and synced like `SERVER`. |
| `STARTUP` | Loaded before content is registered, then fixed for the session. Changes are saved and apply after a restart. |

For a `STARTUP` value, `get()` keeps the value the game started with and `getStored()` is the one in the file; `isRestartPending()` says whether they differ.

`RestartRequirement` (`NONE`, `WORLD`, `GAME`) is what must restart before a changed value takes effect. `StartupSync` (`REQUIRE_MATCH`, `USE_SERVER`) is how a `STARTUP` value is reconciled with the server's when a client joins.

## Value types

A `ConfigType<T>` in `api.config.type` says what a value is: how it's stored (a `Codec`), what it may be, and how a player types it. Types are immutable and carry their constraints, so a list's elements are checked the same way as a single value.

`ConfigTypes` has the built-in ones.

| Type | From `ConfigTypes` | Edited with |
|---|---|---|
| `BooleanType` | `BOOLEAN` | A switch. Typed as true/false, yes/no, on/off or 1/0. |
| `NumberType<N>` | `INT`, `LONG`, `FLOAT`, `DOUBLE`, `intRange(min, max)`, `longRange`, `floatRange`, `doubleRange` | A text box, or a slider with `withSlider(true)`. |
| `StringType` | `STRING`, `string(maxLength)`, `pattern(regex)` | A text box. |
| `EnumType<E>` | `enumOf(MyEnum.class)` | A list of the constants. |
| `ColorType` | `COLOR` (`#RRGGBB`), `COLOR_ALPHA` (`#AARRGGBB`) | The color picker. |
| `IdentifierType` | `IDENTIFIER`, `identifier(registry)` | A text box, suggesting the registry's entries. |
| `ListType<E>` | `listOf(elementType)`, `listOf(elementType, minSize, maxSize)` | The list editor. |
| `CodecType<T>` | `codec(codec)`, `codec(codec, validator, description...)` | A text box, as JSON. |

Ids aren't checked against their registry: configs load before other mods register their content. Check where the id is used.

### Naming enum constants

A constant named `FANCY_LEAVES` shows as "Fancy Leaves". Implement `EnumType.Displayable` to give constants their own name, and a description shown when choosing from the list.

```java
public enum Quality implements EnumType.Displayable {
    LOW, HIGH;

    @Override
    public Component displayName() {
        return Component.translatable("mymod.quality." + this.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public Component description() {
        return Component.translatable("mymod.quality." + this.name().toLowerCase(Locale.ROOT) + ".desc");
    }
}
```

### Validation

`ValidationResult<T>` is the outcome of checking a value.

| Result | Meaning |
|---|---|
| `ok(value)` | Fine as it is. |
| `corrected(value, message)` | Invalid, with the closest valid value: a number clamped into its range. |
| `error(message)` | Rejected. |

A file holding a correctable value loads the corrected one and is rewritten. The config screen and commands only accept values that are fine as they are.

`hasValue()` is true for the first two, `value()` returns the value or the corrected one, and `message()` says what was wrong. `map` and `then` chain checks: an earlier correction survives a later check that passes, and the first error wins.

## Editors

An editor is the widget that edits one value in the config screen. `ConfigEditors`, in `api.editor`, decides which widget edits which type. Types without an editor get a text box using the type's `parse` and `format`.

### Write one

A `ValueEditor` wraps a widget. The screen positions and draws it, and greys it out when the value can't be edited.

```java title="YesNoEditor.java"
public final class YesNoEditor implements ValueEditor {
    private final EditSlot<Boolean> slot;
    private final Button button;

    public YesNoEditor(EditSlot<Boolean> slot, int width, int height) {
        this.slot = slot;
        this.button = Button.builder(Component.empty(), pressed -> {
            slot.set(!slot.get());
            this.refresh();
        }).bounds(0, 0, width, height).build();
        this.refresh();
    }

    @Override
    public AbstractWidget widget() {
        return this.button;
    }

    /** Shows the slot's value again after it changed elsewhere: undo, reset, a preset, the server. */
    @Override
    public void refresh() {
        this.button.setMessage(Component.literal(this.slot.get() ? "Yes" : "No"));
    }
}
```

The `EditSlot<T>` is what the editor edits: a config value, or one element of a list being edited. Values set on it are pending until the player saves.

| `EditSlot` method | Meaning |
|---|---|
| `type()` | The value's `ConfigType`, with its constraints. |
| `get()` | The pending value. |
| `set(value)` | Sets a new pending value. It's checked, and the slot reports an `error()` when it isn't valid. |
| `setInvalid(error)` | Marks input that isn't a value at all, like text that doesn't parse. Saving is blocked until it's fixed. |
| `error()` | Why the current input can't be saved, if it can't. |
| `name()` | The name to narrate. |

### Register it

During client setup, for every value of a type class, or for one type instance:

```java
// Every boolean, in every mod's config screen.
ConfigEditors.register(BooleanType.class, (ValueEditor.Factory<Boolean>) YesNoEditor::new);

// Only values of this exact type: colors with alpha.
ConfigEditors.register(ConfigTypes.COLOR_ALPHA, MyColorEditor::new);
```

An editor for one instance is used ahead of its class's. An editor for a class also covers its subclasses, unless they have their own.

!!! warning "Editors are global"

    Both kinds of registration change the widget in every mod's config screen, not only yours: the built-in types are shared by all of them.
