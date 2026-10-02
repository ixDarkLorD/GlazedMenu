# Glazed Menu

**A glassy mod list and config screens for every mod.** Client side, for Fabric and NeoForge. It needs no other mod.

Glazed Menu takes the place of Mod Menu's and NeoForge's mod lists, and gives the settings of almost any mod one
clean, themed screen.

---

## 📋 The mod list
- **Grid or list view**, with filters for **All**, **With Settings** and **Libraries**, search, and A–Z sorting
- **Groups**: mods that bundle others, like Fabric API and its modules, open and close as one entry
- **Every mod in its own colors**, taken from its logo
- **A details pane** with the description, authors, license, links, what a mod needs and what it bundles
- **Update notices** for mods that publish an update file
- **Open the mods folder** in one click, and Minecraft's own options as the game's settings

## ⚙️ Config screens for every mod
One look for every mod's settings: a sidebar of categories, search, undo and redo, reset, presets, validation,
restart notices, list editing and a color picker. Find every mod's configs in one place through **Mod Configs**, with
search across all of them.

## 🎨 Make it yours
- **Dark and light** modes
- **Color schemes**: Glazed, Classic, Ocean, Forest, Ember, Rose and Slate
- **Panel opacity**, background effects and fading page transitions

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

### For mod authors
- **List your mod under Libraries**: `"custom": { "glazedmenu": { "library": true } }` in `fabric.mod.json`
  (Mod Menu's `"badges": ["library"]` works too), or `library = true` under `[modproperties.<modid>]` in
  `neoforge.mods.toml`.
- **Update notices on Fabric**: `"custom": { "glazedmenu": { "update_json": "<url>" } }`, a file in NeoForge's update
  JSON format. On NeoForge, the usual `updateJSONURL` is used.
- **Themes**: CoolCatLib: Core's theme API, or a resource pack's `assets/<modid>/glazedmenu/config_theme.json`.

📦 Source and issues: [GitHub](https://github.com/ixDarkLorD/GlazedMenu)
