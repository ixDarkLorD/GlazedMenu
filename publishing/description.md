# Glazed Menu

**A glassy mod list and config screens for every mod.** Client side, for Fabric and NeoForge. It needs no other mod.

Every screen is a pane of frosted glass: soft reflections, lit edges, a glaze of color running from top to bottom and
slow sun rays drifting across it.

Glazed Menu takes the place of Mod Menu's and NeoForge's mod lists, and gives the settings of almost any mod one
clean, themed screen.

---

## 📋 The mod list
- **Grid or list view**, with filters for **All**, **With Settings** and **Libraries**, smooth search, and A–Z sorting
- **Remembers where you were**: the selected mod and scroll position stay when you open a mod's settings and come back
- **Groups**: mods that bundle others, like Fabric API and its modules, open and close as one entry
- **Every mod in its own colors**, taken from its logo
- **A details pane** with the description, authors, license, links, what a mod needs and what it bundles
- **Update notices** for mods that publish an update file
- **Open the mods folder** in one click, and Minecraft's own options as the game's settings

## ⚙️ Config screens for every mod
One look for every mod's settings: a sidebar of categories, search, undo and redo, reset, presets, validation,
restart notices, list editing and a color picker, with **Save** to keep editing and **Done** to save and close.

- **Mod Configs**: every mod's configs in one place, as cards in each mod's colors, with search across all of them
- **Each mod's configs as cards** sized to fill the screen, tagged **CLIENT**, **SERVER**, **STARTUP** and so on

## 🎨 Make it yours
- **Dark and light** modes
- **Color schemes**: Glazed, Classic, Ocean, Forest, Ember, Rose and Slate
- **Panel opacity**, the frosted glass and sun ray effects, and fading page transitions (all of it holds still on Fast
  graphics or with transitions off)

## 🧩 Compatibility
Glazed Menu needs no other mod. It works with:
- **Mod Menu** (Fabric) and **NeoForge's mod list**: Glazed Menu's list opens in their place (can be turned off)
- **CoolCatLib** (Core and Canvas): their configs, themes and screen effects show in Glazed Menu's screens
- **NeoForge** configs, and on Fabric **Forge Config API Port** (NeoForge and Forge configs)
- **Cloth Config** (AutoConfig), **YACL** and **MidnightLib**
- **JEI** (through **MezzConfig**) and **FTB mods** (through **FTB Library**, like FTB Ultimine): their configs show in
  Glazed Menu's screens, and replacing their own screens is an option in its settings
- **Configured**: steps aside for Glazed Menu's screens
- Any other mod with a config screen of its own: its Settings button opens that screen

---

### 🛠️ For mod authors
Glazed Menu works with your mod as it is. These are optional extras, all read from files, with no code dependency on
Glazed Menu.

#### List your mod under Libraries
**Fabric**, in `fabric.mod.json` (Mod Menu's own badge is read too):
```json
"custom": {
  "glazedmenu": { "library": true },
  "modmenu": { "badges": ["library"] }
}
```

**NeoForge**, in `META-INF/neoforge.mods.toml`:
```toml
[modproperties.yourmodid]
library = true
```

#### Update notices
**NeoForge** uses your mod's usual `updateJSONURL`, so there is nothing to add.

**Fabric**: point Glazed Menu at an update file in `fabric.mod.json`:
```json
"custom": {
  "glazedmenu": { "update_json": "https://example.com/updates/yourmodid.json" }
}
```
The file uses NeoForge's update JSON format. Glazed Menu takes the `<minecraft>-recommended` version for the running
game (or `<minecraft>-latest`), and shows a notice when it's newer than the installed one:
```json
{
  "homepage": "https://modrinth.com/mod/yourmod",
  "promos": {
    "26.1.2-recommended": "1.2.0",
    "26.1.2-latest": "1.3.0-beta"
  }
}
```

#### Your mod's look in the config screens
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

With CoolCatLib: Core, its theme API (`ConfigTheme.builder()`, `ConfigTheme.setForMod(...)`) themes your screens too.

#### Config card artwork
Each of your configs gets a card in Glazed Menu's config screens. To use your own artwork instead of the built-in one
for its kind (client, server, ...), ship `assets/<modid>/textures/gui/config/cards/<config name>.png`. The image is
portrait (about 1:1.3), for example 512×672.

#### Your own config screen
If your mod has its own config screen (Mod Menu's `ModMenuApi`, NeoForge's `IConfigScreenFactory`), its Settings
button opens it. Generic screens made for any mod (NeoForge's, Configured's, Forge Config API Port's, MidnightLib's) are
replaced by Glazed Menu's when Glazed Menu can read the configs behind them.

📦 Source and issues: [GitHub](https://github.com/ixDarkLorD/GlazedMenu)
