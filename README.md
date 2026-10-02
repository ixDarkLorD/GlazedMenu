<p align="center"><img src="common/src/main/resources/mod_logo.png" width="160" alt="Glazed Menu"></p>
<h1 align="center">Glazed Menu</h1>
<p align="center"><b>A glassy mod list and config screens for every mod.</b><br>
Client side · Fabric &amp; Forge · Minecraft 1.20 – 1.20.1</p>
<hr>

Glazed Menu replaces Mod Menu's and Forge's mod lists with its own, and gives the configs of almost any mod a
clean, themed screen. It needs no other mod.

## The mod list
- **Grid or list view**, filters for **All**, **With Settings** and **Libraries**, search, and A–Z / Z–A sorting.
- **Groups**: mods that bundle others (like Fabric API and its modules) open and close as one group.
- **Each mod in its own colors**, taken from its logo; a details pane with its description, authors, links, what it
  needs and what it bundles.
- **Update notices**: mods that publish an update file get a mark, and the details pane shows the new version.
- A button to **open the mods folder**, and the game's own **options** as Minecraft's settings.

## The config screens
One look for every mod's settings: a sidebar of categories, search, undo / redo, reset, presets, validation, restart
notices, list editing and a color picker. Configs from:
- **CoolCatLib: Core** (with its themes),
- **NeoForge** (`ModConfigSpec`), and on Fabric through **Forge Config API Port** (also Forge's `ForgeConfigSpec`),
- **Cloth Config** (AutoConfig), **YACL**, **MidnightLib**,
- **MezzConfig** (JEI) and **FTB Library** (FTB mods), each optional in Glazed Menu's settings,
- and a link to the screen of any mod with its own.

## Looks
Dark and light modes, color schemes (Glazed, Classic, Ocean, Forest, Ember, Rose, Slate), panel opacity, background
effects and fading page transitions, all in Glazed Menu's settings.

## For mod authors
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

**NeoForge**, in `META-INF/neoforge.mods.toml`:
```toml
[modproperties.yourmodid]
library = true
```

### Update notices
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

With CoolCatLib: Core, its theme API (`ConfigTheme.builder()`, `ConfigTheme.setForMod(...)`) themes your screens too.

### Config card artwork
Each of your configs gets a card in Glazed Menu's config screens. To use your own artwork instead of the built-in one
for its kind (client, server, ...), ship `assets/<modid>/textures/gui/config/cards/<config name>.png`. The image is
portrait (about 1:1.3), for example 512×672.

### Your own config screen
If your mod has its own config screen (Mod Menu's `ModMenuApi`, NeoForge's `IConfigScreenFactory`), its Settings
button opens it. Generic screens made for any mod (NeoForge's, Configured's, Forge Config API Port's, MidnightLib's) are
replaced by Glazed Menu's when Glazed Menu can read the configs behind them.

## Building
`./gradlew build`. CoolCatLib: Core and Canvas (optional integrations) are compiled against from Maven, including
Maven Local while developing.

## License
[MPL-2.0](LICENSE)
