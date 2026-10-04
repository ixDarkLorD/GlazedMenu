# Theming

Give your mod's config screens their own look. A resource pack can ship the same files to theme any mod.

## The theme file

Ship `assets/<modid>/glazedmenu/config_theme.json`. Every field is optional; colors are `"#RRGGBB"` or `"#AARRGGBB"`.

```json title="assets/yourmodid/glazedmenu/config_theme.json"
{
  "base": "tinted",
  "colors": { "accent": "#E86BA8" },
  "light_base": "tinted",
  "icon": "yourmodid:textures/gui/config_icon.png",
  "background": "yourmodid:textures/gui/config_background.png",
  "background_mode": "cover",
  "background_opacity": 0.6
}
```

| Field | Meaning |
|---|---|
| `base`, `light_base` | The starting palette for the dark and light modes: `tinted` (shaded with your accent), `dark` or `light`. |
| `colors`, `light_colors` | Individual colors over that base. See the list below. |
| `icon` | The icon shown for your mod's screens. |
| `background` | A texture behind the screen. |
| `background_mode` | `stretch`, `cover` or `tile` (with `tile_size`). |
| `background_opacity`, `texture_opacity` | How strongly the background and textures show. |
| `background_in_world` | Whether the background is drawn while a world is open. |
| `popup_sprite` | The sprite used for popups. |
| `effects` | Animated effects, by id. |

### Colors

`accent`, `backdrop`, `panel`, `panel_border`, `bar`, `popup`, `row_hover`, `field`, `field_border`, `button`, `button_hover`, `button_disabled`, `toggle_off`, `knob`, `text`, `text_dim`, `text_muted`, `modified`, `error`, `success`, `warning`.

## Config card artwork

Each of your configs gets a card in Glazed Menu's config screens. To use your own artwork instead of the built-in one for its kind (client, server and so on), ship:

```
assets/<modid>/textures/gui/config/cards/<config name>.png
```

The image is portrait, about 1:1.3. 512×672 works well.
