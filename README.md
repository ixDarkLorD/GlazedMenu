<p align="center"><img src="common/src/main/resources/mod_logo.png" width="160" alt="Glazed Menu"></p>
<h1 align="center">Glazed Menu</h1>
<p align="center"><b>A glassy mod list and config screens for every mod.</b><br>
Client side · Fabric &amp; NeoForge · Minecraft 26.1 – 26.1.2</p>
<hr>

Glazed Menu replaces Mod Menu's and NeoForge's mod lists with its own, and gives the configs of almost any mod a
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
- **Mark your mod as a library** so it's listed under Libraries:
  `"custom": { "glazedmenu": { "library": true } }` in `fabric.mod.json` (Mod Menu's `"badges": ["library"]` works too),
  or `library = true` under `[modproperties.<modid>]` in `neoforge.mods.toml`.
- **Update notices on Fabric**: `"custom": { "glazedmenu": { "update_json": "<url>" } }`, a file in NeoForge's update
  JSON format (on NeoForge, the usual `updateJSONURL` is used).
- **Themes**: CoolCatLib: Core's theme API, or a resource pack's `assets/<modid>/glazedmenu/config_theme.json`.

## Building
`./gradlew build`. CoolCatLib: Core and Canvas (optional integrations) are compiled against from Maven, including
Maven Local while developing.

## License
[MPL-2.0](https://www.mozilla.org/MPL/2.0/)
