# Screens

`net.ixdarklord.glazedmenu.api.ConfigScreens` opens Glazed Menu's screens from your own code: a button in your GUI, a key binding, an item.

Every method works for configs from any [library Glazed Menu reads](../guide/config-screens.md#which-configs-it-reads), not just one.

## A mod's configs

```java
// Opens your mod's config screen over whatever screen is open.
ConfigScreens.open("mymod");
```

`open` does nothing when the mod has no config Glazed Menu can read. To build the screen yourself, for a screen factory or a button that needs the screen object:

```java
Screen screen = ConfigScreens.create(parent, "mymod");   // null when the mod has no config
if (screen != null) Minecraft.getInstance().setScreen(screen);
```

The screen shows a card for each of the mod's configs, even when there is only one.

| Method | Opens |
|---|---|
| `open(modId)` | The mod's configs, over the current screen. |
| `create(parent, modId)` | The same screen, returned. `null` when the mod has no config. |
| `create(parent, configId)` | One config by its id, `mymod:client`. `null` when there is no such config. |
| `create(parent, config)` | One config, from a [`Config`](editors.md#the-config-model) you already hold. |
| `createModList(parent)` | Every mod's configs. |
| `hasConfigs(modId)` | Whether the mod has any config Glazed Menu reads. |

`parent` is the screen to return to, or `null` to return to the game.

## A category as a popup

A category popup is a small window with one group of settings, with **Save** and **Cancel**. It floats over the screen it was opened from, or over the game.

![A category opened as a popup](../assets/shots/category-popup.jpg){ .gm-shot loading=lazy }

```java
// The "example_category" group of mymod's "client" config.
ConfigScreens.openCategory("mymod", "client/example_category");
```

The first part of the path names the config; the rest are the group's keys, joined by `/` or `.`.

| Method | Path |
|---|---|
| `openCategory(modId, path)` | `<config>/<group>/...`. Opens over the current screen; does nothing when there is no such category. |
| `categoryPopup(parent, modId, path)` | The same popup, returned. `null` when the mod has no such config or category. |
| `categoryPopup(parent, configId, path)` | The path inside the config: `rendering/particles`. `null` when there is no such config. |
| `categoryPopup(parent, config, path)` | The same, from a `Config`. Throws `IllegalArgumentException` when nothing is at the path. |
| `categoryPopup(parent, config, path, theme)` | Drawn with a [theme](themes.md) of your choice instead of the config's, to match the screen it opens from. |

A path to a single setting shows just that setting. An empty path shows the whole config.

## The color picker

The picker config screens use for colors, for any color your mod lets players choose.

![The color picker](../assets/shots/color-picker.jpg){ .gm-shot loading=lazy }

```java
Screen picker = ConfigScreens.colorPicker(
        parent,
        Component.translatable("mymod.pick_color"),
        0xFFFF8A3D,     // the starting color, ARGB
        false,          // true to let the player pick alpha too
        color -> MyMod.setTrailColor(color));
Minecraft.getInstance().setScreen(picker);
```

**Done** passes the picked color to your callback, as ARGB, then returns to `parent`. Without alpha the color is always opaque.

## The command

Players, and your own chat links, can open the same screens with a client command.

| Command | Opens |
|---|---|
| `/glazedmenu` | Every mod's configs. |
| `/glazedmenu mods` | The mod list. |
| `/glazedmenu <mod>` | One mod's configs. |
| `/glazedmenu <mod> <config>/<category>` | One category as a popup, like `/glazedmenu mymod client/example_category`. |
