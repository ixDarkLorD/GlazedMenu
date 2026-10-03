# Glazed Menu

**A frosted-glass mod list and one clean config screen for almost every mod.**
Client side only. It needs no other mod, and servers don't need it.

Glazed Menu replaces the mod list you already have (Mod Menu's, NeoForge's or Forge's) and reads the configs most mods
use, so every mod's settings look and work the same way.

| Minecraft | Fabric | NeoForge | Forge |
|---|:---:|:---:|:---:|
| 26.1 – 26.3 | ✅ | ✅ | – |
| 1.21 – 1.21.1 | ✅ | ✅ | ✅ |
| 1.20 – 1.20.1 | ✅ | – | ✅ |

---

## 📋 The mod list
- **Grid or list view**, with filters for **All**, **With Settings** and **Libraries**, search, and A–Z sorting
- **Every mod in its own colors**, taken from its logo
- **Groups**: mods that bundle others, like Fabric API and its modules, open and close as one entry
- **A details pane** with the description, authors, license, links, what a mod needs and what it bundles
- **Update notices** for mods that publish an update file
- **Remembers where you were**: the selected mod and scroll position stay when you open a mod's settings and come back
- **One click** to the mods folder, and Minecraft's own options as the game's settings

## ⚙️ Config screens
- **Mod Configs**: every mod's configs in one place, as cards in each mod's colors, with search across all of them
- **One editor for every mod**: a sidebar of categories, search, undo and redo, reset to default, presets, validation
  and restart notices
- **Proper editors for each value**: toggles, sliders, dropdowns, list editing and a color picker
- **Save** to keep editing, **Done** to save and close, **Cancel** to leave everything as it was
- **Server configs** are editable by the host in a world and read-only for other players

## 🎨 Make it yours
- **Dark and light** modes, switched from any screen
- **Color schemes**: Glazed, Classic, Ocean, Forest, Ember, Rose and Slate
- **Panel opacity**, the glass and sun ray effects, and fading page transitions. All of it holds still on Fast graphics
  or with transitions turned off

## 🧩 Works with
Glazed Menu's list opens in place of **Mod Menu's**, **NeoForge's** and **Forge's**. You can turn that off in its
settings and keep the original list.

It shows and edits **NeoForge** and **Forge** configs, and the configs of the widely used config libraries, each
saved through its own system, so nothing about your config files changes.

A mod with a config screen of its own keeps it: its Settings button opens that screen. Generic, auto-generated config
screens step aside for Glazed Menu's when it can read the configs behind them.

---

## 🛠️ For mod authors
Glazed Menu works with your mod as it is. These are optional extras, all read from files, with no code dependency on
Glazed Menu.

### List your mod under Libraries
**Fabric**, in `fabric.mod.json` (Mod Menu's own badge is read too):
```json
"custom": {
  "glazedmenu": { "library": true },
  "modmenu": { "badges": ["library"] }
}
```

**NeoForge and Forge**, in `neoforge.mods.toml` or `mods.toml`:
```toml
[modproperties.yourmodid]
library = true
```

### Update notices
**NeoForge and Forge** use your mod's usual `updateJSONURL`, so there is nothing to add.

**Fabric**: point Glazed Menu at an update file in `fabric.mod.json`:
```json
"custom": {
  "glazedmenu": { "update_json": "https://example.com/updates/yourmodid.json" }
}
```
The file uses the Forge and NeoForge update JSON format. Glazed Menu takes the `<minecraft>-recommended` version for
the running game (or `<minecraft>-latest`), and shows a notice when it's newer than the installed one:
```json
{
  "homepage": "https://modrinth.com/mod/yourmod",
  "promos": {
    "1.21.1-recommended": "1.2.0",
    "1.21.1-latest": "1.3.0-beta"
  }
}
```

### Your mod's look in the config screens
Ship `assets/<modid>/glazedmenu/config_theme.json` in your mod, or in a resource pack to theme any mod. Every field is
optional; colors are `"#RRGGBB"` or `"#AARRGGBB"`:
```json
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
- `base` / `light_base`: `tinted` (shaded with your accent), `dark` or `light`, for the dark and light modes.
- `colors` / `light_colors`: any of `accent`, `backdrop`, `panel`, `panel_border`, `bar`, `popup`, `row_hover`, `field`,
  `field_border`, `button`, `button_hover`, `button_disabled`, `toggle_off`, `knob`, `text`, `text_dim`, `text_muted`,
  `modified`, `error`, `success`, `warning`.
- `background_mode`: `stretch`, `cover` or `tile` (with `tile_size`). Also `texture_opacity`, `background_in_world`,
  `popup_sprite` and `effects`.

### Config card artwork
Each of your configs gets a card in Glazed Menu's config screens. To use your own artwork instead of the built-in one
for its kind (client, server, ...), ship `assets/<modid>/textures/gui/config/cards/<config name>.png`. The image is
portrait (about 1:1.3), for example 512×672.

### Your own config screen
If your mod has its own config screen (Mod Menu's `ModMenuApi`, NeoForge's `IConfigScreenFactory`, Forge's
`ConfigScreenFactory`), its Settings button opens it.

---

📦 Source and issues: [GitHub](https://github.com/ixDarkLorD/GlazedMenu) · License: MPL-2.0
