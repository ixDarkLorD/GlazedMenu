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
restart notices, list editing and a color picker. It reads the configs of:
- **CoolCatLib: Core**, with its themes
- **NeoForge** configs, and on Fabric through **Forge Config API Port** (Forge configs too)
- **Cloth Config**, **YACL** and **MidnightLib**
- **MezzConfig** (JEI) and **FTB Library** (FTB mods), each switchable in the settings
- Any other mod with a screen of its own gets a link to it

Find every mod's configs in one place through **Mod Configs**, with search across all of them.

## 🎨 Make it yours
- **Dark and light** modes
- **Color schemes**: Glazed, Classic, Ocean, Forest, Ember, Rose and Slate
- **Panel opacity**, background effects and fading page transitions

---

### For mod authors
- **List your mod under Libraries**: `"custom": { "glazedmenu": { "library": true } }` in `fabric.mod.json`
  (Mod Menu's `"badges": ["library"]` works too), or `library = true` under `[modproperties.<modid>]` in
  `neoforge.mods.toml`.
- **Update notices on Fabric**: `"custom": { "glazedmenu": { "update_json": "<url>" } }`, a file in NeoForge's update
  JSON format. On NeoForge, the usual `updateJSONURL` is used.
- **Themes**: CoolCatLib: Core's theme API, or a resource pack's `assets/<modid>/glazedmenu/config_theme.json`.

**Optional:** with [CoolCatLib](https://www.curseforge.com/minecraft/mc-mods/coolcatlib) installed, its configs and
screen effects show in Glazed Menu's screens.

📦 Source and issues: [GitHub](https://github.com/ixDarkLorD/GlazedMenu)
