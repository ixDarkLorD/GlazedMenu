---
icon: material/palette-swatch-outline
description: Build your mod's theme in code
---

# Themes in Code

A theme is how a mod's config screens look: the colors for dark and light mode, an optional background texture, the icon beside the title, the panel popups use, and the [effects](effects.md) drawn.

The [theme file](../authors/theming.md) does the same from a resource pack. Use code when the theme depends on something only your mod knows, or when you want it without shipping a file.

## Set your mod's theme

```java
ConfigTheme.setForMod("mymod", ConfigTheme.builder()
        .colors(ConfigColorScheme.tinted(0xFFFF8A3D))
        .background(Identifier.fromNamespaceAndPath("mymod", "textures/gui/config_background.png"))
        .mode(ConfigTheme.BackgroundMode.COVER)
        .textureOpacity(0.8F)       // lets the panorama or world show through the texture a little
        .backgroundOpacity(0.3F)    // how strongly the backdrop color covers it
        .build());
```

`setForMod` covers every screen of the mod's configs: the cards, each config's screen, and the pickers. A mod that sets nothing gets `ConfigTheme.DEFAULT`, Glazed Menu's own look.

!!! note "What wins"

    A resource pack's `config_theme.json` is applied on top of the theme set here, field by field. Mods using CoolCatLib: Core's configs can set their theme there instead, and Glazed Menu uses it.

## The builder

| Method | Meaning |
|---|---|
| `colors(scheme)` | Every color in dark mode. |
| `lightColors(scheme)` | Every color in light mode. By default, the light scheme in the dark scheme's accent. |
| `accent(color)` | Just the accent of both modes, keeping the rest. |
| `icon(texture)` | An icon texture's full path for the top bar, instead of the mod's own icon. |
| `background(texture)` | A texture's full path, like `mymod:textures/gui/config_background.png`. Without one the screen is see-through. |
| `mode(mode)` | How the texture fills the screen: `COVER`, `STRETCH` or `TILE`. |
| `tiled(tileSize)` | Tiles the texture at this size in GUI pixels; `0` for the texture's own size. |
| `backgroundOpacity(opacity)` | The backdrop color over the background, from `0` (not drawn) to `1` (hides it). `0.35` by default. |
| `textureOpacity(opacity)` | The texture's own opacity, from `0` to `1` (the default). |
| `backgroundInWorld(boolean)` | Draws the texture while a world is open too, instead of the blurred world. |
| `popupSprite(sprite)` | A GUI sprite drawn as the panel of popups, instead of the flat panel. |
| `effects(ids...)` | The [effects](effects.md) drawn, in order, instead of the starfall. None for a still screen. |

Colors are ARGB integers. An opaque RGB like `0xFF8A3D` works for accents too.

`theme.toBuilder()` starts from an existing theme, to change one thing about it.

### Backgrounds

Without a texture the screens are see-through: the title panorama, or the world while playing, blurred behind the panels. With one, the blurred world still replaces it while a world is open, so players see where they are, unless you set `backgroundInWorld(true)`.

| `BackgroundMode` | The texture is |
|---|---|
| `COVER` | Scaled to cover the whole screen, keeping its proportions. Edges may be cut off. |
| `STRETCH` | Stretched to the screen's size. |
| `TILE` | Repeated, like the vanilla dirt background. |

Players can scale both opacities for every mod in Glazed Menu's settings.

### The popup sprite

`popupSprite` takes a GUI sprite id like `mymod:config/popup`: a texture in `textures/gui/sprites/`, usually with a nine-slice `.mcmeta`. The popup's title is drawn 12 pixels from the sprite's top-left corner and content starts 30 pixels down, so its top border can hold a title bar.

## Color schemes

`ConfigColorScheme` holds every color of the screens. Start from a preset and change what you like.

```java
ConfigColorScheme.tinted(0xFF7E57C2)                                    // the dark look, its greys shaded toward purple
ConfigColorScheme.LIGHT.toBuilder().accent(0xFF2E7D32).build()
ConfigColorScheme.DARK.toBuilder().panel(0xC0101820).text(0xFFF0F0F0).build()
```

| Preset | Look |
|---|---|
| `DARK` | Dark glassy panels, light text, a cyan accent. |
| `LIGHT` | Frosted white panels, dark text, a blue accent. |
| `tinted(accent)` | The dark scheme with its panels, buttons and fields shaded toward your accent. |
| `tintedLight(accent)` | The light scheme, faintly shaded toward your accent. |
| `tinted(base, accent, strength)` | Any scheme, shaded toward the accent by `strength`, from `0` to `1`. |

Translucent panel and bar colors let the background show through.

### The colors

Each is a builder method and a getter of the same name. They match the [theme file's color names](../authors/theming.md#colors), in camel case.

| Color | Used for |
|---|---|
| `accent` | Highlights, toggles, sliders, the main button. |
| `backdrop` | Laid over the panorama, world or texture behind the screen. |
| `panel`, `panelBorder` | The panels. |
| `bar` | The top and bottom bars. |
| `popup` | Popups: the color picker, presets, confirmations. |
| `rowHover` | The row under the mouse. |
| `field`, `fieldBorder` | Text fields. |
| `button`, `buttonHover`, `buttonDisabled` | Buttons. |
| `toggleOff` | A switch's track while off. |
| `knob` | Switch and slider knobs. |
| `text` | Text. |
| `textDim` | Descriptions and secondary text. |
| `textMuted` | Disabled and placeholder text. |
| `modified` | Unsaved changes. |
| `error`, `success` | Errors and confirmations. |
| `warning` | Restart notices and server-side edits. |

## Reading a theme

Useful inside an [effect](effects.md), or to draw your own screen in the same colors.

```java
ConfigTheme theme = ConfigTheme.forMod("mymod");                     // the mod's theme, or DEFAULT
ConfigColorScheme colors = theme.colors(ConfigTheme.Mode.DARK);      // or Mode.LIGHT
int accent = colors.accent();
```

`ConfigTheme.Mode` is each player's choice of dark or light, for every mod's config screens.
