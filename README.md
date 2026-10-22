<p align="center">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/GlazedMenu/26.3/common/src/main/resources/mod_logo.png" alt="Glazed Menu" width="160">
</p>

<h1 align="center">Glazed Menu</h1>

<p align="center">
  <b>A glassy mod list and config screens for every mod.</b><br>
  A client-side mod for Fabric, NeoForge and Forge.
</p>

<p align="center">
  <a href="https://modrinth.com/mod/glazedmenu"><img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/glazed-menu/modrinth.svg" alt="Modrinth" height="26"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/glazed-menu"><img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/glazed-menu/curseforge.svg" alt="CurseForge" height="26"></a>
  <a href="https://ixdarklord.github.io/GlazedMenu/"><img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/glazed-menu/docs.svg" alt="Docs" height="26"></a>
  <a href="https://discord.gg/ZapGEPqm2V"><img src="https://img.shields.io/discord/1173075408556654592?style=flat&logo=discord&logoColor=white&label=&labelColor=6f52dd&color=3A3F58" alt="Discord" width="110" height="26"></a>
</p>
<p align="center">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/glazed-menu/fabric.svg" alt="Fabric" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/glazed-menu/neoforge.svg" alt="NeoForge" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/glazed-menu/forge.svg" alt="Forge" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/glazed-menu/client-side.svg" alt="Client side" height="26">
</p>

<hr>

## ✨ About

Glazed Menu replaces Mod Menu's, NeoForge's and Forge's mod lists with its own, gives the configs of almost any mod a
clean, themed screen, and restyles the game's own menus to match. It needs no other mod, and every screen it replaces
can be turned back into the game's own in its settings.

## 🧩 Features

- **📋 The mod list.** Grid or list view, filters, search and sorting; favorites and hidden mods; mods that bundle
  others shown as one group; each mod in its logo's colors, with a details pane, and update notices.
- **⚙️ Config screens for every mod.** One look for every mod's settings, with categories, search, undo and redo,
  presets, validation and a color picker. Configs from CoolCatLib, NeoForge and Forge (also through Forge Config API
  Port), Cloth Config, YACL, MidnightLib, MezzConfig and FTB Library, plus a link to any mod's own screen.
- **🔎 Search, changes and profiles.** Search every mod's settings at once, list every setting you changed, and switch
  between saved sets of settings (`/glazedmenu search`, `changed` and `profile`).
- **🎛️ The game's options.** Options as a ribbon of tabs and cards, with a Quick tab for the ones most reached for; the
  language, packs and game rules screens in the same look, and Sodium and Iris's screens linked in.
- **🏠 The game's menus.** Title, pause, singleplayer, multiplayer, create and edit world, loading, advancements and
  statistics screens, each with layouts to pick from.
- **⌨️ Key binds.** Key binds with conflicts, search by name, key or mod, and a map of the keyboard and mouse to rebind
  on.
- **🎨 Looks.** Dark and light modes, color schemes (Glazed, Classic, Ocean, Forest, Ember, Rose, Slate), panel opacity,
  background effects and page transitions.

The [documentation](https://ixdarklord.github.io/GlazedMenu/) has a guide to every screen.

## 📦 Download

| CurseForge | Modrinth |
|---|---|
| [glazed-menu](https://www.curseforge.com/minecraft/mc-mods/glazed-menu) | [glazedmenu](https://modrinth.com/mod/glazedmenu) |

It needs no other mod. It's client side: servers don't need it.

## 🧭 Versions

Each Minecraft version lives on its own branch.

| Minecraft | Loaders | Branch | Status | Latest |
|---|---|---|:-:|---|
| **26.3** | Fabric, NeoForge | [`26.3`](https://github.com/ixDarkLorD/GlazedMenu/tree/26.3) | ✅ Supported | 26.3+1.2 |
| **26.2** | Fabric, NeoForge | [`26.2`](https://github.com/ixDarkLorD/GlazedMenu/tree/26.2) | ✅ Supported | 26.2+1.2 |
| **26.1 – 26.1.2** | Fabric, NeoForge | [`26.1.2`](https://github.com/ixDarkLorD/GlazedMenu/tree/26.1.2) | ✅ Supported | 26.1.2+1.2 |
| **1.21 – 1.21.1** | Fabric, NeoForge, Forge | [`1.21-1.21.1`](https://github.com/ixDarkLorD/GlazedMenu/tree/1.21-1.21.1) | ✅ Supported | 1.21.1+1.2 |
| **1.20 – 1.20.1** | Fabric, Forge | [`1.20-1.20.1`](https://github.com/ixDarkLorD/GlazedMenu/tree/1.20-1.20.1) | ✅ Supported | 1.20.1+1.2 |

## 🔌 For mod authors

Glazed Menu works with your mod as it is. These extras are all read from files, with no code dependency on Glazed Menu:

- **List your mod under Libraries:** `"custom": { "glazedmenu": { "library": true } }` in `fabric.mod.json`, or
  `library = true` under `[modproperties.yourmodid]` in `neoforge.mods.toml` / `mods.toml`.
- **Update notices:** NeoForge and Forge use your mod's `updateJSONURL`. On Fabric, point
  `"custom": { "glazedmenu": { "update_json": "<url>" } }` at a file in the same update JSON format.
- **Your look in the config screens:** `assets/<modid>/glazedmenu/config_theme.json` sets your accent and other colors,
  icon and background, for the dark and light modes.
- **Config card artwork:** `assets/<modid>/textures/gui/config/cards/<config name>.png`, portrait (about 1:1.3).
- **Your own config screen:** Mod Menu's `ModMenuApi`, NeoForge's `IConfigScreenFactory` and Forge's
  `ConfigScreenFactory` screens open from the Settings button.

The [documentation](https://ixdarklord.github.io/GlazedMenu/) has every field, with examples.

## 🛠️ Building

Check out the branch for your Minecraft version, then run:

```bash
./gradlew build
```

The jars end up in each loader's `build/libs`. CoolCatLib, for the optional integrations, comes from the
[ModResources Maven](https://ixdarklord.github.io/ModResources/), and the build scripts and dependency versions from
[ModResources](https://github.com/ixDarkLorD/ModResources).

## 🐞 Issues and ideas

Found a bug or have an idea? Open an [issue](https://github.com/ixDarkLorD/GlazedMenu/issues), or drop by the
[Discord](https://discord.gg/ZapGEPqm2V).

## 📄 License

Glazed Menu is under the [MPL-2.0](LICENSE).
